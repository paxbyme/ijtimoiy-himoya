package com.manager.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.manager.config.GeminiConfig;
import com.manager.dto.AiRuleDto;
import com.manager.service.AiRulesService;
import com.manager.service.GeminiLiveClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * WebSocket bridge between the mobile client and the Gemini Live API.
 *
 * Wire protocol with the mobile:
 *   - First text frame: {"token": "<firebase_id_token>"}  (or pass ?token=... in query)
 *   - Subsequent binary frames: raw 16-bit PCM 16kHz mono audio
 *   - Optional text frame {"event": "endTurn"} when the user stops speaking
 *
 * Outbound to mobile:
 *   - Binary frames: raw 16-bit PCM 24kHz mono audio (Gemini's output rate)
 *   - Text frame {"event": "turnComplete"} after the model finishes a turn
 *   - Text frame {"event": "error", "message": "..."} on failure
 */
@Component
public class LiveAudioWebSocketHandler extends AbstractWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(LiveAudioWebSocketHandler.class);
    private static final String SESSION_KEY = "geminiLive";
    private static final String UID_KEY = "uid";

    private static final String GOLDEN_RULES = """
            You are a friendly voice assistant for employees in this organization.
            Speak naturally and conversationally. Keep replies short and to the point — this is a voice call, not a written chat.
            Always reply in the same language the user spoke. If unclear, default to Uzbek (O'zbek tili).
            Rules you MUST follow:
            1. Never reveal confidential salary, HR, or financial information.
            2. Always be professional and respectful.
            3. Do not provide legal or medical advice.
            4. If you don't know something, say so honestly.
            5. Follow all department-specific rules provided below.
            """;

    private final GeminiConfig geminiConfig;
    private final AiRulesService aiRulesService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LiveAudioWebSocketHandler(GeminiConfig geminiConfig, AiRulesService aiRulesService) {
        this.geminiConfig = geminiConfig;
        this.aiRulesService = aiRulesService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // Try query-string token first (?token=...), otherwise wait for the first text frame.
        String token = null;
        if (session.getUri() != null && session.getUri().getQuery() != null) {
            for (String pair : session.getUri().getQuery().split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2 && "token".equals(kv[0])) {
                    token = java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8);
                    break;
                }
            }
        }
        if (token != null) {
            startSession(session, token);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode node = objectMapper.readTree(message.getPayload());

        // Auth token sent as first frame
        if (node.has("token") && session.getAttributes().get(SESSION_KEY) == null) {
            startSession(session, node.get("token").asText());
            return;
        }

        if (node.has("event")) {
            String event = node.get("event").asText();
            GeminiLiveClient client = (GeminiLiveClient) session.getAttributes().get(SESSION_KEY);
            if (client == null) return;
            if ("endTurn".equals(event)) client.endTurn();
            else if ("startTurn".equals(event)) client.startTurn();
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        GeminiLiveClient client = (GeminiLiveClient) session.getAttributes().get(SESSION_KEY);
        if (client == null) {
            sendError(session, "Not authenticated");
            return;
        }
        ByteBuffer buf = message.getPayload();
        byte[] pcm = new byte[buf.remaining()];
        buf.get(pcm);
        client.sendAudio(pcm);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        GeminiLiveClient client = (GeminiLiveClient) session.getAttributes().get(SESSION_KEY);
        if (client != null) {
            client.close();
            session.getAttributes().remove(SESSION_KEY);
        }
        log.info("Live WS closed: uid={} status={}", session.getAttributes().get(UID_KEY), status);
    }

    private void startSession(WebSocketSession session, String token) {
        try {
            FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(token);
            String uid = decoded.getUid();
            String departmentId = decoded.getClaims().get("departmentId") != null
                    ? decoded.getClaims().get("departmentId").toString() : "";
            session.getAttributes().put(UID_KEY, uid);

            String systemInstruction = buildSystemInstruction(departmentId);

            GeminiLiveClient client = new GeminiLiveClient(
                    geminiConfig,
                    systemInstruction,
                    audio -> sendBinary(session, audio),
                    text -> sendText(session, Map.of("event", "text", "text", text)),
                    () -> sendText(session, Map.of("event", "turnComplete")),
                    err -> sendError(session, err.getMessage() != null ? err.getMessage() : "Gemini error"),
                    () -> closeSession(session),
                    // Tell the client to start streaming only once Gemini's session is
                    // registered (setupComplete). Sending 'ready' earlier made the client
                    // stream into a buffer that then burst-drained → Gemini 1007.
                    () -> sendText(session, Map.of("event", "ready")));
            session.getAttributes().put(SESSION_KEY, client);

            log.info("Live WS session started: uid={} dept={} (awaiting Gemini setupComplete before 'ready')", uid, departmentId);
        } catch (Exception e) {
            log.warn("Live WS auth failed", e);
            sendError(session, "Authentication failed");
            closeSession(session);
        }
    }

    private String buildSystemInstruction(String departmentId) {
        StringBuilder sb = new StringBuilder(GOLDEN_RULES);
        try {
            List<AiRuleDto> rules = aiRulesService.getActiveRulesForDepartment(departmentId);
            if (rules != null && !rules.isEmpty()) {
                sb.append("\n\nDepartment-specific rules:\n");
                for (AiRuleDto rule : rules) {
                    sb.append("- ").append(rule.getTitle()).append(": ").append(rule.getContent()).append("\n");
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load AI rules for dept={}", departmentId, e);
        }
        return sb.toString();
    }

    private synchronized void sendBinary(WebSocketSession session, byte[] data) {
        try {
            if (session.isOpen()) session.sendMessage(new BinaryMessage(data));
        } catch (Exception e) {
            log.debug("sendBinary failed", e);
        }
    }

    private synchronized void sendText(WebSocketSession session, Map<String, ?> payload) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
            }
        } catch (Exception e) {
            log.debug("sendText failed", e);
        }
    }

    private void sendError(WebSocketSession session, String message) {
        sendText(session, Map.of("event", "error", "message", Objects.toString(message, "Unknown error")));
    }

    private void closeSession(WebSocketSession session) {
        try {
            if (session.isOpen()) session.close(CloseStatus.NORMAL);
        } catch (Exception ignored) {}
    }
}
