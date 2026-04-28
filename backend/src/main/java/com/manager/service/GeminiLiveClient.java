package com.manager.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manager.config.GeminiConfig;
import okhttp3.*;
import okio.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Single bidirectional Gemini Live API session.
 *
 * Wraps a WebSocket to {@code wss://generativelanguage.googleapis.com/...BidiGenerateContent}.
 * Sends a setup message with the system instruction on connect, then forwards
 * 16-bit PCM 16kHz mono audio chunks from the caller and emits 24kHz PCM audio
 * chunks from the model.
 */
public class GeminiLiveClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiLiveClient.class);

    private static final String LIVE_MODEL = "models/gemini-2.5-flash-native-audio-preview-12-2025";
    private static final String VOICE_NAME = "Aoede";

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String systemInstruction;
    private final Consumer<byte[]> onAudio;
    private final Consumer<String> onText;
    private final Runnable onTurnComplete;
    private final Consumer<Throwable> onError;
    private final Runnable onClose;

    private WebSocket webSocket;
    private volatile boolean closed;
    private volatile boolean setupComplete;
    // Buffer audio chunks until Gemini confirms setup; otherwise the model rejects
    // the stream with "Invalid argument" before it has registered the session.
    private final Deque<byte[]> pendingAudio = new ArrayDeque<>();
    private static final int MAX_PENDING_CHUNKS = 80; // ~1.6s at 20ms frames

    public GeminiLiveClient(GeminiConfig config,
                            String systemInstruction,
                            Consumer<byte[]> onAudio,
                            Consumer<String> onText,
                            Runnable onTurnComplete,
                            Consumer<Throwable> onError,
                            Runnable onClose) {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS) // no read timeout for streaming WS
                .pingInterval(20, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
        this.systemInstruction = systemInstruction;
        this.onAudio = onAudio;
        this.onText = onText;
        this.onTurnComplete = onTurnComplete;
        this.onError = onError;
        this.onClose = onClose;
        connect(config);
    }

    private void connect(GeminiConfig config) {
        // Native audio Live models are stable on v1beta.
        String url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key="
                + config.getApiKey();
        Request request = new Request.Builder().url(url).build();
        this.webSocket = httpClient.newWebSocket(request, new Listener());
    }

    private volatile boolean firstAudioLogged = false;
    private volatile int forwardedChunks = 0;
    private volatile int bufferedDropped = 0;
    private volatile boolean greetingSent = false;
    private volatile boolean greetingTurnDone = false;

    /** Send raw 16-bit PCM 16kHz mono audio frame from the user's microphone. */
    public void sendAudio(byte[] pcm16kHzMono) {
        if (closed || webSocket == null) return;
        // Hold audio until setup is complete AND any initial greeting turn has
        // finished — sending realtimeInput.audio while the model is generating
        // its greeting reply triggers 1007 invalid-argument.
        if (!setupComplete || (greetingSent && !greetingTurnDone)) {
            synchronized (pendingAudio) {
                if (pendingAudio.size() >= MAX_PENDING_CHUNKS) {
                    pendingAudio.pollFirst();
                    bufferedDropped++;
                    if (bufferedDropped == 1 || bufferedDropped % 20 == 0) {
                        log.warn("Gemini Live: dropped {} buffered chunks (waiting for setup/greeting)", bufferedDropped);
                    }
                }
                pendingAudio.addLast(pcm16kHzMono);
            }
            return;
        }
        sendAudioFrame(pcm16kHzMono);
        forwardedChunks++;
        if (forwardedChunks == 1 || forwardedChunks % 25 == 0) {
            log.info("Gemini Live: forwarded {} audio chunks to model", forwardedChunks);
        }
    }

    private void sendAudioFrame(byte[] pcm16kHzMono) {
        try {
            String b64 = Base64.getEncoder().encodeToString(pcm16kHzMono);
            Map<String, Object> realtimeInput = Map.of(
                    "realtimeInput", Map.of(
                            "audio", Map.of(
                                    "mimeType", "audio/pcm;rate=16000",
                                    "data", b64)));
            String json = objectMapper.writeValueAsString(realtimeInput);
            if (!firstAudioLogged) {
                firstAudioLogged = true;
                int previewLen = Math.min(pcm16kHzMono.length, 64);
                StringBuilder hex = new StringBuilder(previewLen * 3);
                for (int i = 0; i < previewLen; i++) {
                    hex.append(String.format("%02x ", pcm16kHzMono[i] & 0xff));
                }
                int sampleCount = pcm16kHzMono.length / 2;
                int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
                long sum = 0;
                for (int i = 0; i + 1 < pcm16kHzMono.length; i += 2) {
                    int lo = pcm16kHzMono[i] & 0xff;
                    int hi = pcm16kHzMono[i + 1];
                    int s = (hi << 8) | lo; // little-endian
                    if (s > 32767) s -= 65536;
                    if (s < min) min = s;
                    if (s > max) max = s;
                    sum += Math.abs(s);
                }
                long avg = sampleCount > 0 ? sum / sampleCount : 0;
                log.info("Gemini Live: first audio chunk sent ({} bytes, {} samples, min={} max={} avgAbs={}) hex0..63={}",
                        pcm16kHzMono.length, sampleCount, min, max, avg, hex.toString().trim());
            }
            webSocket.send(json);
        } catch (Exception e) {
            onError.accept(e);
        }
    }

    /**
     * Right after setupComplete, send a single user turn that asks Gemini to
     * greet the user. We do NOT drain mic audio yet — incoming
     * realtimeInput.audio frames during the greeting response cause 1007.
     * Audio is drained after we observe turnComplete for the greeting.
     */
    private void sendInitialGreetingTrigger() {
        try {
            Map<String, Object> trigger = Map.of(
                    "clientContent", Map.of(
                            "turns", List.of(Map.of(
                                    "role", "user",
                                    "parts", List.of(Map.of(
                                            "text", "Suhbat boshlandi. O'zbek tilida foydalanuvchini qisqa va do'stona salomlash bilan kutib ol va qanday yordam bera olishingni so'ra. Faqat bitta qisqa jumla ayt.")))),
                            "turnComplete", true));
            String json = objectMapper.writeValueAsString(trigger);
            log.info("Gemini Live: sending initial greeting trigger");
            webSocket.send(json);
            greetingSent = true;
        } catch (Exception e) {
            log.warn("Failed to send greeting trigger; draining audio anyway", e);
            greetingSent = false;
            greetingTurnDone = true;
            drainPendingAudio();
        }
    }

    private void drainPendingAudio() {
        Deque<byte[]> snapshot;
        synchronized (pendingAudio) {
            snapshot = new ArrayDeque<>(pendingAudio);
            pendingAudio.clear();
        }
        if (!snapshot.isEmpty()) {
            log.info("Gemini Live: draining {} buffered audio chunks", snapshot.size());
            for (byte[] chunk : snapshot) sendAudioFrame(chunk);
        }
    }

    /** No-op: server VAD owns turn boundaries. */
    public void endTurn() { }

    /** No-op: server VAD owns turn boundaries. */
    public void startTurn() { }

    public void close() {
        closed = true;
        if (webSocket != null) {
            webSocket.close(1000, "client closed");
            webSocket = null;
        }
    }

    private final class Listener extends WebSocketListener {
        @Override
        public void onOpen(WebSocket ws, Response response) {
            try {
                // Audio output + input transcription. Server-side VAD owns
                // turn boundaries — manual activity signals were rejected with
                // 1007 when sent standalone and short-circuited model replies
                // when sent back-to-back.
                // Native-audio model picks its own voice and emits its own
                // output transcription — adding speechConfig/outputAudioTranscription
                // here triggers 1007 with these models.
                Map<String, Object> setup = Map.of(
                        "setup", Map.of(
                                "model", LIVE_MODEL,
                                "generationConfig", Map.of(
                                        "responseModalities", List.of("AUDIO")),
                                "systemInstruction", Map.of(
                                        "parts", List.of(Map.of("text", systemInstruction))),
                                "inputAudioTranscription", Map.of()));
                String json = objectMapper.writeValueAsString(setup);
                log.info("Gemini Live setup: {}", json);
                ws.send(json);
            } catch (Exception e) {
                onError.accept(e);
            }
        }

        @Override
        public void onMessage(WebSocket ws, String text) {
            handleMessage(text);
        }

        @Override
        public void onMessage(WebSocket ws, ByteString bytes) {
            handleMessage(bytes.utf8());
        }

        private boolean firstResponseLogged = false;

        private void handleMessage(String json) {
            try {
                JsonNode root = objectMapper.readTree(json);

                JsonNode setupCompleteNode = root.path("setupComplete");
                if (!setupCompleteNode.isMissingNode()) {
                    log.info("Gemini Live setup complete (model={})", LIVE_MODEL);
                    setupComplete = true;
                    sendInitialGreetingTrigger();
                    return;
                }

                JsonNode serverContent = root.path("serverContent");
                if (!serverContent.isMissingNode()) {
                    if (!firstResponseLogged) {
                        firstResponseLogged = true;
                        String preview = json.length() > 1200 ? json.substring(0, 1200) + "..." : json;
                        log.info("Gemini Live first serverContent: {}", preview);
                    }
                    // Diagnostic: surface what Gemini transcribed from the user's audio
                    JsonNode inputTr = serverContent.path("inputTranscription");
                    if (inputTr.isMissingNode()) inputTr = serverContent.path("input_transcription");
                    if (!inputTr.isMissingNode()) {
                        String t = inputTr.path("text").asText("");
                        if (!t.isEmpty()) {
                            log.info("Gemini heard user: {}", t);
                            onText.accept("[user] " + t);
                        }
                    }

                    extractParts(serverContent.path("modelTurn").path("parts"));
                    extractParts(serverContent.path("model_turn").path("parts"));
                    extractParts(serverContent.path("parts"));

                    if (serverContent.path("turnComplete").asBoolean(false)
                            || serverContent.path("turn_complete").asBoolean(false)) {
                        if (greetingSent && !greetingTurnDone) {
                            greetingTurnDone = true;
                            log.info("Gemini Live: greeting turn complete, draining buffered mic audio");
                            drainPendingAudio();
                        }
                        onTurnComplete.run();
                    }
                    return;
                }

                // Some response shapes put audio at top-level
                JsonNode topAudio = root.path("audio");
                if (!topAudio.isMissingNode()) {
                    String b64 = topAudio.path("data").asText("");
                    if (!b64.isEmpty()) onAudio.accept(Base64.getDecoder().decode(b64));
                    return;
                }

                JsonNode error = root.path("error");
                if (!error.isMissingNode()) {
                    log.warn("Gemini Live server error: {}", error.toString());
                    onError.accept(new IOException("Gemini error: " + error.toString()));
                    return;
                }

                String preview = json.length() > 800 ? json.substring(0, 800) + "..." : json;
                log.info("Gemini Live unhandled message (keys={}): {}",
                        root.fieldNames().hasNext() ? iterToString(root.fieldNames()) : "[]", preview);
            } catch (Exception e) {
                onError.accept(e);
            }
        }

        private String iterToString(java.util.Iterator<String> it) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            while (it.hasNext()) {
                if (!first) sb.append(",");
                sb.append(it.next());
                first = false;
            }
            return sb.append("]").toString();
        }

        private void extractParts(JsonNode parts) {
            if (!parts.isArray()) return;
            for (JsonNode part : parts) {
                for (String key : new String[]{"inlineData", "inline_data"}) {
                    JsonNode inline = part.path(key);
                    if (!inline.isMissingNode()) {
                        String b64 = inline.path("data").asText("");
                        if (!b64.isEmpty()) {
                            onAudio.accept(Base64.getDecoder().decode(b64));
                        }
                    }
                }
                JsonNode txt = part.path("text");
                if (!txt.isMissingNode() && txt.isTextual()) {
                    onText.accept(txt.asText());
                }
            }
        }

        @Override
        public void onFailure(WebSocket ws, Throwable t, Response response) {
            String body = "";
            try {
                if (response != null && response.body() != null) body = response.body().string();
            } catch (IOException ignored) {}
            log.error("Gemini Live WS failure (status={}, body={})",
                    response != null ? response.code() : -1, body, t);
            closed = true;
            onError.accept(t);
            onClose.run();
        }

        @Override
        public void onClosing(WebSocket ws, int code, String reason) {
            log.warn("Gemini Live WS closing: code={} reason={}", code, reason);
            ws.close(1000, null);
        }

        @Override
        public void onClosed(WebSocket ws, int code, String reason) {
            log.warn("Gemini Live WS closed: code={} reason={}", code, reason);
            closed = true;
            if (code != 1000 && reason != null && !reason.isEmpty()) {
                onError.accept(new IOException("Gemini closed: " + code + " " + reason));
            }
            onClose.run();
        }
    }
}
