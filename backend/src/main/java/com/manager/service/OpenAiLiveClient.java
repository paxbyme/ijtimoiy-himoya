package com.manager.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manager.config.OpenAiConfig;
import okhttp3.*;
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
 * Single bidirectional OpenAI Realtime API live-voice session.
 *
 * Protocol: connects to {@code wss://api.openai.com/v1/realtime?model=...} with
 * an {@code OpenAI-Beta: realtime=v1} header, sends a {@code session.update}
 * configuring server-VAD turn detection + pcm16 audio, then:
 *   - inbound mic audio (16 kHz PCM, from the mobile) is RESAMPLED to 24 kHz
 *     (OpenAI's pcm16 rate) and appended to the input audio buffer;
 *   - outbound model audio (24 kHz PCM) is forwarded to {@link #onAudio}, which
 *     already matches the mobile's expected 24 kHz output rate.
 *
 * NOTE: untested against the live OpenAI endpoint — requires OpenAI credits and
 * a real device streaming audio. Kept behind the {@code ai.provider} switch.
 */
public class OpenAiLiveClient implements LiveClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiLiveClient.class);

    private static final int MIC_RATE = 16000;   // mobile mic input rate
    private static final int OAI_RATE = 24000;    // OpenAI pcm16 rate
    private static final int MAX_PENDING_CHUNKS = 80;

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpenAiConfig config;
    private final String systemInstruction;
    private final Consumer<byte[]> onAudio;
    private final Consumer<String> onText;
    private final Runnable onTurnComplete;
    private final Consumer<Throwable> onError;
    private final Runnable onClose;
    private final Runnable onReady;

    private WebSocket webSocket;
    private volatile boolean closed;
    private volatile boolean ready;
    private final Deque<byte[]> pendingAudio = new ArrayDeque<>();

    public OpenAiLiveClient(OpenAiConfig config,
                            String systemInstruction,
                            Consumer<byte[]> onAudio,
                            Consumer<String> onText,
                            Runnable onTurnComplete,
                            Consumer<Throwable> onError,
                            Runnable onClose,
                            Runnable onReady) {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(20, TimeUnit.SECONDS)
                .build();
        this.config = config;
        this.systemInstruction = systemInstruction;
        this.onAudio = onAudio;
        this.onText = onText;
        this.onTurnComplete = onTurnComplete;
        this.onError = onError;
        this.onClose = onClose;
        this.onReady = onReady;
        connect();
    }

    private void connect() {
        String url = config.getBaseUrl().replaceFirst("^http", "ws")
                + "/v1/realtime?model=" + config.getRealtimeModel();
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + config.getApiKey())
                .header("OpenAI-Beta", "realtime=v1")
                .build();
        this.webSocket = httpClient.newWebSocket(request, new Listener());
    }

    @Override
    public void sendAudio(byte[] pcm16kHzMono) {
        if (closed || webSocket == null) return;
        if (!ready) {
            synchronized (pendingAudio) {
                if (pendingAudio.size() >= MAX_PENDING_CHUNKS) pendingAudio.pollFirst();
                pendingAudio.addLast(pcm16kHzMono);
            }
            return;
        }
        appendAudio(pcm16kHzMono);
    }

    private void appendAudio(byte[] pcm16kHzMono) {
        try {
            byte[] resampled = resample(pcm16kHzMono, MIC_RATE, OAI_RATE);
            String b64 = Base64.getEncoder().encodeToString(resampled);
            String json = objectMapper.writeValueAsString(Map.of(
                    "type", "input_audio_buffer.append",
                    "audio", b64));
            webSocket.send(json);
        } catch (Exception e) {
            onError.accept(e);
        }
    }

    private void drainPending() {
        Deque<byte[]> snapshot;
        synchronized (pendingAudio) {
            snapshot = new ArrayDeque<>(pendingAudio);
            pendingAudio.clear();
        }
        for (byte[] chunk : snapshot) appendAudio(chunk);
    }

    /** Server VAD owns turn boundaries — manual signals are no-ops. */
    @Override public void endTurn() { }
    @Override public void startTurn() { }

    @Override
    public void close() {
        closed = true;
        if (webSocket != null) {
            webSocket.close(1000, "client closed");
            webSocket = null;
        }
    }

    /**
     * Linear-interpolation resampler for 16-bit little-endian mono PCM.
     * Adequate for speech; not a high-fidelity filter.
     */
    static byte[] resample(byte[] pcm, int inRate, int outRate) {
        if (inRate == outRate || pcm.length < 4) return pcm;
        int inSamples = pcm.length / 2;
        int outSamples = (int) ((long) inSamples * outRate / inRate);
        byte[] out = new byte[outSamples * 2];
        for (int j = 0; j < outSamples; j++) {
            double srcPos = (double) j * (inSamples - 1) / (outSamples - 1);
            int i0 = (int) Math.floor(srcPos);
            int i1 = Math.min(i0 + 1, inSamples - 1);
            double frac = srcPos - i0;
            int s0 = sampleAt(pcm, i0);
            int s1 = sampleAt(pcm, i1);
            int s = (int) Math.round(s0 + (s1 - s0) * frac);
            out[j * 2] = (byte) (s & 0xff);
            out[j * 2 + 1] = (byte) ((s >> 8) & 0xff);
        }
        return out;
    }

    private static int sampleAt(byte[] pcm, int sampleIndex) {
        int lo = pcm[sampleIndex * 2] & 0xff;
        int hi = pcm[sampleIndex * 2 + 1];
        int s = (hi << 8) | lo;
        if (s > 32767) s -= 65536;
        return s;
    }

    private final class Listener extends WebSocketListener {
        @Override
        public void onOpen(WebSocket ws, Response response) {
            try {
                Map<String, Object> session = Map.of(
                        "modalities", List.of("audio", "text"),
                        "instructions", systemInstruction,
                        "voice", config.getRealtimeVoice(),
                        "input_audio_format", "pcm16",
                        "output_audio_format", "pcm16",
                        "turn_detection", Map.of("type", "server_vad"),
                        "input_audio_transcription", Map.of("model", "whisper-1"));
                String json = objectMapper.writeValueAsString(Map.of(
                        "type", "session.update",
                        "session", session));
                ws.send(json);
            } catch (Exception e) {
                onError.accept(e);
            }
        }

        @Override
        public void onMessage(WebSocket ws, String text) {
            try {
                JsonNode root = objectMapper.readTree(text);
                String type = root.path("type").asText("");
                switch (type) {
                    case "session.created", "session.updated" -> {
                        if (!ready) {
                            ready = true;
                            try { onReady.run(); } catch (Exception e) { log.warn("onReady failed", e); }
                            drainPending();
                        }
                    }
                    case "response.audio.delta" -> {
                        String b64 = root.path("delta").asText("");
                        if (!b64.isEmpty()) onAudio.accept(Base64.getDecoder().decode(b64));
                    }
                    case "response.audio_transcript.delta" -> {
                        String t = root.path("delta").asText("");
                        if (!t.isEmpty()) onText.accept(t);
                    }
                    case "response.done", "response.audio.done" -> onTurnComplete.run();
                    case "error" -> {
                        String msg = root.path("error").path("message").asText(root.toString());
                        log.warn("OpenAI Realtime error: {}", msg);
                        onError.accept(new IOException("OpenAI Realtime error: " + msg));
                    }
                    default -> { /* ignore other event types */ }
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
            log.error("OpenAI Realtime WS failure (status={}, body={})",
                    response != null ? response.code() : -1, body, t);
            closed = true;
            onError.accept(t);
            onClose.run();
        }

        @Override
        public void onClosed(WebSocket ws, int code, String reason) {
            log.warn("OpenAI Realtime WS closed: code={} reason={}", code, reason);
            closed = true;
            if (code != 1000 && reason != null && !reason.isEmpty()) {
                onError.accept(new IOException("OpenAI closed: " + code + " " + reason));
            }
            onClose.run();
        }
    }
}
