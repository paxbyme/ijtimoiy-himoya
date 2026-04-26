package com.manager.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manager.config.GeminiConfig;
import okhttp3.*;
import okio.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Base64;
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

    // Native-audio Live model — can speak Uzbek with natural prosody.
    private static final String LIVE_MODEL = "models/gemini-2.5-flash-preview-native-audio-dialog";
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
        String url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key="
                + config.getApiKey();
        Request request = new Request.Builder().url(url).build();
        this.webSocket = httpClient.newWebSocket(request, new Listener());
    }

    /** Send raw 16-bit PCM 16kHz mono audio frame from the user's microphone. */
    public void sendAudio(byte[] pcm16kHzMono) {
        if (closed || webSocket == null) return;
        try {
            String b64 = Base64.getEncoder().encodeToString(pcm16kHzMono);
            Map<String, Object> realtimeInput = Map.of(
                    "realtime_input", Map.of(
                            "media_chunks", List.of(Map.of(
                                    "mime_type", "audio/pcm;rate=16000",
                                    "data", b64))));
            webSocket.send(objectMapper.writeValueAsString(realtimeInput));
        } catch (Exception e) {
            onError.accept(e);
        }
    }

    /** Tell the model the user has stopped speaking so it can respond. */
    public void endTurn() {
        if (closed || webSocket == null) return;
        try {
            Map<String, Object> msg = Map.of("realtime_input", Map.of("turn_complete", true));
            webSocket.send(objectMapper.writeValueAsString(msg));
        } catch (Exception e) {
            onError.accept(e);
        }
    }

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
                Map<String, Object> setup = Map.of(
                        "setup", Map.of(
                                "model", LIVE_MODEL,
                                "generation_config", Map.of(
                                        "response_modalities", List.of("AUDIO"),
                                        "speech_config", Map.of(
                                                "voice_config", Map.of(
                                                        "prebuilt_voice_config", Map.of(
                                                                "voice_name", VOICE_NAME)))),
                                "system_instruction", Map.of(
                                        "parts", List.of(Map.of("text", systemInstruction)))));
                ws.send(objectMapper.writeValueAsString(setup));
                log.info("Gemini Live session opened");
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

        private void handleMessage(String json) {
            try {
                JsonNode root = objectMapper.readTree(json);

                JsonNode setupComplete = root.path("setupComplete");
                if (!setupComplete.isMissingNode()) {
                    log.debug("Gemini Live setup complete");
                    return;
                }

                JsonNode serverContent = root.path("serverContent");
                if (!serverContent.isMissingNode()) {
                    JsonNode modelTurn = serverContent.path("modelTurn");
                    JsonNode parts = modelTurn.path("parts");
                    if (parts.isArray()) {
                        for (JsonNode part : parts) {
                            JsonNode inlineData = part.path("inlineData");
                            if (!inlineData.isMissingNode()) {
                                String b64 = inlineData.path("data").asText("");
                                if (!b64.isEmpty()) {
                                    onAudio.accept(Base64.getDecoder().decode(b64));
                                }
                            }
                            JsonNode txt = part.path("text");
                            if (!txt.isMissingNode() && txt.isTextual()) {
                                onText.accept(txt.asText());
                            }
                        }
                    }

                    if (serverContent.path("turnComplete").asBoolean(false)) {
                        onTurnComplete.run();
                    }
                    return;
                }

                JsonNode error = root.path("error");
                if (!error.isMissingNode()) {
                    log.warn("Gemini Live server error: {}", error.toString());
                    onError.accept(new IOException("Gemini error: " + error.toString()));
                }
            } catch (Exception e) {
                onError.accept(e);
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
            ws.close(1000, null);
        }

        @Override
        public void onClosed(WebSocket ws, int code, String reason) {
            log.info("Gemini Live WS closed: {} {}", code, reason);
            closed = true;
            onClose.run();
        }
    }
}
