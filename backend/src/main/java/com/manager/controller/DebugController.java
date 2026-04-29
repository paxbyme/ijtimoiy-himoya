package com.manager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manager.config.GeminiConfig;
import com.manager.service.GeminiLiveClient;
import okhttp3.*;
import okio.ByteString;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/debug")
public class DebugController {

    private final GeminiConfig geminiConfig;
    private final ObjectMapper mapper = new ObjectMapper();

    public DebugController(GeminiConfig geminiConfig) {
        this.geminiConfig = geminiConfig;
    }

    @GetMapping("/gemini-models")
    public ResponseEntity<String> listGeminiModels() {
        try {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build();
            Request req = new Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models?key=" + geminiConfig.getApiKey() + "&pageSize=200")
                    .get()
                    .build();
            try (Response resp = client.newCall(req).execute()) {
                String body = resp.body() != null ? resp.body().string() : "";
                return ResponseEntity.status(resp.code()).body(body);
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("error: " + e.getMessage());
        }
    }

    /**
     * Reproduces our Live API session standalone: connects, sends our exact setup,
     * streams 3 s of synthetic 16 kHz PCM audio, captures every frame Gemini sends,
     * returns the trace. Lets us see the actual reason behind the 1007 close
     * without needing Railway log access.
     *
     * Query: ?model=gemini-2.5-flash-native-audio-preview-09-2025
     *        &durationMs=3000
     *        &include=audio (default omits audio bytes from the trace)
     */
    @GetMapping("/live-test")
    public ResponseEntity<Map<String, Object>> liveTest(
            @RequestParam(defaultValue = "gemini-2.5-flash-native-audio-preview-09-2025") String model,
            @RequestParam(defaultValue = "3000") int durationMs,
            @RequestParam(defaultValue = "false") boolean include) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("model", model);
        result.put("durationMs", durationMs);
        List<Map<String, Object>> events = new ArrayList<>();
        result.put("events", events);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(20, TimeUnit.SECONDS)
                .build();

        String url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key="
                + geminiConfig.getApiKey();
        Request request = new Request.Builder().url(url).build();

        CountDownLatch done = new CountDownLatch(1);
        AtomicInteger setupComplete = new AtomicInteger(0);

        WebSocket ws = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                logEvent(events, "open", Map.of(
                        "httpStatus", response.code(),
                        "headers", response.headers().toMultimap()));
                try {
                    Map<String, Object> setup = Map.of(
                            "setup", Map.of(
                                    "model", "models/" + model,
                                    "generationConfig", Map.of(
                                            "responseModalities", List.of("AUDIO")),
                                    "systemInstruction", Map.of(
                                            "parts", List.of(Map.of("text",
                                                    "You are a concise voice assistant. Reply briefly in Uzbek.")))));
                    String json = mapper.writeValueAsString(setup);
                    logEvent(events, "send_setup", Map.of("json", json));
                    ws.send(json);
                } catch (Exception e) {
                    logEvent(events, "send_setup_error", Map.of("error", e.toString()));
                }
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                logEvent(events, "recv_text", Map.of("len", text.length(), "json", truncate(text, 4000)));
                if (text.contains("setupComplete")) setupComplete.set(1);
            }

            @Override
            public void onMessage(WebSocket ws, ByteString bytes) {
                String s = bytes.utf8();
                logEvent(events, "recv_binary", Map.of(
                        "len", bytes.size(),
                        "utf8Preview", truncate(s, 4000)));
                if (s.contains("setupComplete")) setupComplete.set(1);
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                String body = "";
                int code = -1;
                try {
                    if (response != null) {
                        code = response.code();
                        if (response.body() != null) body = response.body().string();
                    }
                } catch (Exception ignored) {}
                logEvent(events, "ws_failure", Map.of(
                        "throwable", t.toString(),
                        "httpStatus", code,
                        "body", truncate(body, 4000)));
                done.countDown();
            }

            @Override
            public void onClosing(WebSocket ws, int code, String reason) {
                logEvent(events, "closing", Map.of("code", code, "reason", reason));
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                logEvent(events, "closed", Map.of("code", code, "reason", reason));
                done.countDown();
            }
        });

        try {
            // Wait up to 8 s for setupComplete.
            long deadline = System.currentTimeMillis() + 8000;
            while (setupComplete.get() == 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
                if (done.getCount() == 0) break;
            }
            if (setupComplete.get() == 0) {
                logEvent(events, "no_setup_complete", Map.of(
                        "waitedMs", 8000,
                        "note", "Gemini did not return setupComplete; closing"));
            } else {
                // Stream synthetic 1 kHz sine, 16 kHz mono 16-bit, in 200 ms chunks.
                int sampleRate = 16000;
                int chunkMs = 200;
                int samplesPerChunk = sampleRate * chunkMs / 1000;
                int totalChunks = Math.max(1, durationMs / chunkMs);
                long sampleIndex = 0;
                for (int c = 0; c < totalChunks && done.getCount() > 0; c++) {
                    byte[] pcm = new byte[samplesPerChunk * 2];
                    for (int i = 0; i < samplesPerChunk; i++) {
                        double t = (double) sampleIndex / sampleRate;
                        int s = (int) (Math.sin(2 * Math.PI * 1000.0 * t) * 9830);
                        pcm[i * 2] = (byte) (s & 0xff);
                        pcm[i * 2 + 1] = (byte) ((s >> 8) & 0xff);
                        sampleIndex++;
                    }
                    String b64 = Base64.getEncoder().encodeToString(pcm);
                    Map<String, Object> frame = Map.of(
                            "realtimeInput", Map.of(
                                    "audio", Map.of(
                                            "mimeType", "audio/pcm;rate=16000",
                                            "data", b64)));
                    String json = mapper.writeValueAsString(frame);
                    logEvent(events, "send_audio", Map.of(
                            "chunk", c + 1,
                            "bytes", pcm.length,
                            "samples", samplesPerChunk));
                    ws.send(json);
                    Thread.sleep(chunkMs);
                }
                // Then wait up to 5 s for any model response or close.
                done.await(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logEvent(events, "test_error", Map.of("error", e.toString()));
        } finally {
            try { ws.close(1000, "test done"); } catch (Exception ignored) {}
        }

        // Strip recv_binary and audio inlineData from events when include=false to keep payload small
        if (!include) {
            for (Map<String, Object> ev : events) {
                Object json = ev.get("json");
                if (json instanceof String s && s.length() > 1500) {
                    ev.put("json", truncate(s, 1500));
                }
            }
        }

        result.put("setupCompleteSeen", setupComplete.get() == 1);
        result.put("eventCount", events.size());
        return ResponseEntity.ok(result);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "...[+" + (s.length() - max) + "]";
    }

    /**
     * Returns analysis of the most recent live session's mic audio: bytes/sec
     * (revealing actual sample rate vs the declared 16 kHz), per-chunk min/max/
     * avgAbs (revealing endianness/silence/clipping issues), and a hex preview
     * of the very first chunk. Also reports the close code/reason if the
     * session 1007'd.
     */
    @GetMapping("/last-mic-capture")
    public ResponseEntity<Map<String, Object>> lastMicCapture() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<byte[]> chunks = GeminiLiveClient.LAST_CAPTURE.get();
        long[] times = GeminiLiveClient.LAST_CAPTURE_TIMES.get();
        result.put("chunkCount", chunks.size());
        result.put("closeCode", GeminiLiveClient.LAST_CAPTURE_CLOSE_CODE);
        result.put("closeReason", GeminiLiveClient.LAST_CAPTURE_CLOSE_REASON);
        if (chunks.isEmpty()) {
            result.put("note", "no capture yet — open the live screen and let it 1007");
            return ResponseEntity.ok(result);
        }
        long totalBytes = 0;
        List<Map<String, Object>> chunkInfo = new ArrayList<>();
        for (int c = 0; c < chunks.size(); c++) {
            byte[] pcm = chunks.get(c);
            totalBytes += pcm.length;
            int sampleCount = pcm.length / 2;
            // Try BOTH endianness interpretations and report which gives more
            // typical audio statistics. Real speech LE should have moderate
            // avgAbs and modest dynamic range; if BE values are dramatically
            // different, the source is BE-swapped (or vice-versa).
            int[] leMin = {Integer.MAX_VALUE}, leMax = {Integer.MIN_VALUE};
            int[] beMin = {Integer.MAX_VALUE}, beMax = {Integer.MIN_VALUE};
            long leSum = 0, beSum = 0;
            for (int i = 0; i + 1 < pcm.length; i += 2) {
                int lo = pcm[i] & 0xff;
                int hi = pcm[i + 1] & 0xff;
                int le = (hi << 8) | lo; if (le > 32767) le -= 65536;
                int be = (lo << 8) | hi; if (be > 32767) be -= 65536;
                if (le < leMin[0]) leMin[0] = le; if (le > leMax[0]) leMax[0] = le;
                if (be < beMin[0]) beMin[0] = be; if (be > beMax[0]) beMax[0] = be;
                leSum += Math.abs(le);
                beSum += Math.abs(be);
            }
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("idx", c);
            info.put("bytes", pcm.length);
            info.put("samples", sampleCount);
            info.put("leMin", leMin[0]);
            info.put("leMax", leMax[0]);
            info.put("leAvgAbs", sampleCount > 0 ? leSum / sampleCount : 0);
            info.put("beMin", beMin[0]);
            info.put("beMax", beMax[0]);
            info.put("beAvgAbs", sampleCount > 0 ? beSum / sampleCount : 0);
            if (c == 0) {
                int previewLen = Math.min(pcm.length, 32);
                StringBuilder hex = new StringBuilder();
                for (int i = 0; i < previewLen; i++) hex.append(String.format("%02x ", pcm[i] & 0xff));
                info.put("hexPrefix32", hex.toString().trim());
            }
            chunkInfo.add(info);
        }
        long elapsedMs = (chunks.size() >= 2 && times.length >= chunks.size())
                ? times[chunks.size() - 1] - times[0]
                : 0;
        double bytesPerSec = elapsedMs > 0 ? totalBytes * 1000.0 / elapsedMs : 0;
        // 16 kHz mono 16-bit = 32_000 bytes/sec. Anything materially different
        // means the mic plugin is NOT honoring the requested sample rate.
        result.put("totalBytes", totalBytes);
        result.put("elapsedMs", elapsedMs);
        result.put("bytesPerSec", bytesPerSec);
        result.put("expected16kHzMonoBytesPerSec", 32000);
        result.put("inferredSampleRateIfMono16Bit", bytesPerSec / 2);
        result.put("chunks", chunkInfo);
        return ResponseEntity.ok(result);
    }

    private static synchronized void logEvent(List<Map<String, Object>> events, String type, Map<String, Object> payload) {
        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("t", System.currentTimeMillis());
        ev.put("type", type);
        ev.putAll(payload);
        events.add(ev);
    }
}
