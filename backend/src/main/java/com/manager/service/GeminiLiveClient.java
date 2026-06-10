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

    // Switched off gemini-2.5-flash-native-audio-preview-09-2025: it reliably
    // closes real (VAD-triggered) turns with 1007 "invalid argument" — a known
    // native-audio-preview quirk. gemini-3.1-flash-live-preview is a half-cascade
    // Live model and tolerates our minimal setup. Overridable via GEMINI_LIVE_MODEL.
    private static final String LIVE_MODEL = resolveLiveModel();
    private static final String VOICE_NAME = "Aoede";

    private static String resolveLiveModel() {
        String env = System.getenv("GEMINI_LIVE_MODEL");
        if (env != null && !env.isBlank()) {
            return env.startsWith("models/") ? env : "models/" + env;
        }
        return "models/gemini-3.1-flash-live-preview";
    }

    // Diagnostic capture: the most recent live session copies its first N audio
    // chunks here so /api/debug/last-mic-capture can return them. Lets us inspect
    // exactly what the mobile mic is sending when 1007 reproduces.
    public static final java.util.concurrent.atomic.AtomicReference<List<byte[]>> LAST_CAPTURE =
            new java.util.concurrent.atomic.AtomicReference<>(new java.util.ArrayList<>());
    public static final java.util.concurrent.atomic.AtomicReference<long[]> LAST_CAPTURE_TIMES =
            new java.util.concurrent.atomic.AtomicReference<>(new long[0]);
    public static volatile String LAST_CAPTURE_CLOSE_REASON = "";
    public static volatile int LAST_CAPTURE_CLOSE_CODE = 0;
    private static final int CAPTURE_CHUNKS = 20;
    private final List<byte[]> captureBuf = new java.util.ArrayList<>();
    private final long[] captureTimes = new long[CAPTURE_CHUNKS];
    private int captureIdx = 0;

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String systemInstruction;
    private final Consumer<byte[]> onAudio;
    private final Consumer<String> onText;
    private final Runnable onTurnComplete;
    private final Consumer<Throwable> onError;
    private final Runnable onClose;
    // Fired once Gemini confirms setupComplete. The bridge waits for this before
    // telling the mobile client it may start streaming, so the client never sends
    // audio that has to be buffered and then burst-drained (which triggers 1007).
    private final Runnable onReady;

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
                            Runnable onClose,
                            Runnable onReady) {
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
        this.onReady = onReady;
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

    /** Send raw 16-bit PCM 16kHz mono audio frame from the user's microphone. */
    public void sendAudio(byte[] pcm16kHzMono) {
        if (closed || webSocket == null) return;
        // Diagnostic tap: copy the first N chunks unmodified, with arrival
        // wall-clock so we can compute the actual sample rate.
        synchronized (captureBuf) {
            if (captureIdx < CAPTURE_CHUNKS) {
                captureBuf.add(pcm16kHzMono.clone());
                captureTimes[captureIdx] = System.currentTimeMillis();
                captureIdx++;
                if (captureIdx == CAPTURE_CHUNKS) {
                    LAST_CAPTURE.set(new java.util.ArrayList<>(captureBuf));
                    LAST_CAPTURE_TIMES.set(captureTimes.clone());
                }
            }
        }
        // Hold audio until Gemini confirms setup; otherwise the model rejects
        // the stream with 1007 invalid-argument before the session is registered.
        if (!setupComplete) {
            synchronized (pendingAudio) {
                if (pendingAudio.size() >= MAX_PENDING_CHUNKS) {
                    pendingAudio.pollFirst();
                    bufferedDropped++;
                    if (bufferedDropped == 1 || bufferedDropped % 20 == 0) {
                        log.warn("Gemini Live: dropped {} buffered chunks (waiting for setup)", bufferedDropped);
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
            // Per-chunk stats so we can correlate the exact frame Gemini chokes on.
            int sampleCount = pcm16kHzMono.length / 2;
            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
            long sum = 0;
            for (int i = 0; i + 1 < pcm16kHzMono.length; i += 2) {
                int lo = pcm16kHzMono[i] & 0xff;
                int hi = pcm16kHzMono[i + 1];
                int s = (hi << 8) | lo; // little-endian assumption
                if (s > 32767) s -= 65536;
                if (s < min) min = s;
                if (s > max) max = s;
                sum += Math.abs(s);
            }
            long avg = sampleCount > 0 ? sum / sampleCount : 0;
            int seq = forwardedChunks + 1;
            if (!firstAudioLogged || seq <= 5 || seq % 10 == 0) {
                firstAudioLogged = true;
                int previewLen = Math.min(pcm16kHzMono.length, 32);
                StringBuilder hex = new StringBuilder(previewLen * 3);
                for (int i = 0; i < previewLen; i++) {
                    hex.append(String.format("%02x ", pcm16kHzMono[i] & 0xff));
                }
                log.info("Gemini Live --> audio #{} ({} bytes, {} samples, min={} max={} avgAbs={}) hex0..31={}",
                        seq, pcm16kHzMono.length, sampleCount, min, max, avg, hex.toString().trim());
            }
            webSocket.send(json);
        } catch (Exception e) {
            onError.accept(e);
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
            // Pace the drain: Gemini rejects a tight-loop burst of realtimeInput
            // frames with 1007. A small gap keeps it under the realtime input rate.
            for (byte[] chunk : snapshot) {
                sendAudioFrame(chunk);
                try {
                    Thread.sleep(20);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
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
                                        "parts", List.of(Map.of("text", systemInstruction)))));
                // outputAudioTranscription removed: empirically triggers 1007
                // invalid-argument on native-audio-preview-09-2025 (re-tested
                // bd18620). Same applies to inputAudioTranscription/speechConfig.
                String json = objectMapper.writeValueAsString(setup);
                log.info("Gemini Live setup: {}", json);
                ws.send(json);
            } catch (Exception e) {
                onError.accept(e);
            }
        }

        @Override
        public void onMessage(WebSocket ws, String text) {
            log.info("Gemini Live <-- text frame ({} bytes): {}",
                    text.length(),
                    text.length() > 2000 ? text.substring(0, 2000) + "...[truncated]" : text);
            handleMessage(text);
        }

        @Override
        public void onMessage(WebSocket ws, ByteString bytes) {
            String s = bytes.utf8();
            log.info("Gemini Live <-- binary frame ({} bytes, decoded {} chars): {}",
                    bytes.size(),
                    s.length(),
                    s.length() > 2000 ? s.substring(0, 2000) + "...[truncated]" : s);
            handleMessage(s);
        }

        private void handleMessage(String json) {
            try {
                JsonNode root = objectMapper.readTree(json);

                JsonNode setupCompleteNode = root.path("setupComplete");
                if (!setupCompleteNode.isMissingNode()) {
                    log.info("Gemini Live setup complete (model={})", LIVE_MODEL);
                    setupComplete = true;
                    // Only now is the session registered with Gemini. Tell the bridge
                    // (which tells the mobile client) it may start streaming. Doing this
                    // before setupComplete made the client stream early; those frames
                    // buffered and then burst-drained, which Gemini rejects with 1007.
                    try {
                        onReady.run();
                    } catch (Exception e) {
                        log.warn("Gemini Live onReady callback failed", e);
                    }
                    // Drain anything that still slipped in before the client got 'ready'.
                    // Greeting trigger removed — clientContent text turn followed by
                    // realtimeInput.audio reproducibly triggers 1007 on native-audio
                    // models (see commit 6ca986f). User speaks first.
                    drainPendingAudio();
                    return;
                }

                JsonNode serverContent = root.path("serverContent");
                if (!serverContent.isMissingNode()) {
                    extractParts(serverContent.path("modelTurn").path("parts"));
                    extractParts(serverContent.path("model_turn").path("parts"));
                    extractParts(serverContent.path("parts"));

                    // Surface the model's output transcription as text events so the
                    // mobile UI can show what the AI said even if audio playback fails.
                    JsonNode outTr = serverContent.path("outputTranscription");
                    if (outTr.isMissingNode()) outTr = serverContent.path("output_transcription");
                    if (!outTr.isMissingNode()) {
                        String t = outTr.path("text").asText("");
                        if (!t.isEmpty()) {
                            log.info("Gemini said: {}", t);
                            onText.accept(t);
                        }
                    }

                    if (serverContent.path("turnComplete").asBoolean(false)
                            || serverContent.path("turn_complete").asBoolean(false)) {
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
            log.warn("Gemini Live WS closing: code={} reason={} (after {} chunks forwarded, {} buffered-dropped, setupComplete={})",
                    code, reason, forwardedChunks, bufferedDropped, setupComplete);
            ws.close(1000, null);
        }

        @Override
        public void onClosed(WebSocket ws, int code, String reason) {
            log.warn("Gemini Live WS closed: code={} reason={} (after {} chunks forwarded, setupComplete={})",
                    code, reason, forwardedChunks, setupComplete);
            closed = true;
            LAST_CAPTURE_CLOSE_CODE = code;
            LAST_CAPTURE_CLOSE_REASON = reason == null ? "" : reason;
            // Even if we didn't reach CAPTURE_CHUNKS, publish what we have so we
            // can inspect captures from sessions that 1007'd before chunk 20.
            synchronized (captureBuf) {
                if (!captureBuf.isEmpty()) {
                    LAST_CAPTURE.set(new java.util.ArrayList<>(captureBuf));
                    long[] copy = new long[captureIdx];
                    System.arraycopy(captureTimes, 0, copy, 0, captureIdx);
                    LAST_CAPTURE_TIMES.set(copy);
                }
            }
            if (code != 1000 && reason != null && !reason.isEmpty()) {
                onError.accept(new IOException("Gemini closed: " + code + " " + reason));
            }
            onClose.run();
        }
    }
}
