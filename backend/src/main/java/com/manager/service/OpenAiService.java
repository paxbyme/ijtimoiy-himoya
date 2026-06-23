package com.manager.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manager.config.OpenAiConfig;
import com.manager.exception.GeminiRateLimitException;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * OpenAI Chat Completions backend for AI chat. Mirrors {@link GeminiService}'s
 * chat / chatStream contract (same Gemini-shaped history input) so the two are
 * interchangeable behind the {@code ai.chat.provider} switch and stored
 * conversation history stays in one canonical (Gemini) format.
 *
 * On HTTP 429 it throws {@link GeminiRateLimitException} (a provider-neutral
 * rate-limit signal, despite the legacy name) so AiController's existing 429
 * handling applies. Genuine network IOExceptions are retried by @Retryable.
 */
@Service
public class OpenAiService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiService.class);
    private static final String FALLBACK_TEXT = "I'm sorry, I couldn't generate a response.";

    private final OpenAiConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAiService(OpenAiConfig config) {
        this.config = config;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Retryable(retryFor = IOException.class, maxAttempts = 3,
               backoff = @Backoff(delay = 2000, multiplier = 2, maxDelay = 10000))
    public String chat(String systemInstruction, List<Map<String, Object>> history, String userMessage)
            throws IOException {
        Map<String, Object> requestBody = buildRequestBody(systemInstruction, history, userMessage, false);
        String json = objectMapper.writeValueAsString(requestBody);

        try (Response response = execute(json)) {
            String responseBody = response.body() != null ? response.body().string() : "";
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            String text = content.asText("");
            return text.isBlank() ? FALLBACK_TEXT : text;
        }
    }

    @Retryable(retryFor = IOException.class, maxAttempts = 3,
               backoff = @Backoff(delay = 2000, multiplier = 2, maxDelay = 10000))
    public String chatStream(String systemInstruction, List<Map<String, Object>> history,
                             String userMessage, Consumer<String> onToken) throws IOException {
        Map<String, Object> requestBody = buildRequestBody(systemInstruction, history, userMessage, true);
        String json = objectMapper.writeValueAsString(requestBody);

        try (Response response = execute(json)) {
            StringBuilder full = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body().byteStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) continue;
                    String data = line.substring(5).trim();
                    if (data.equals("[DONE]")) break;
                    if (data.isEmpty()) continue;
                    try {
                        JsonNode root = objectMapper.readTree(data);
                        String token = root.path("choices").path(0).path("delta").path("content").asText("");
                        if (!token.isEmpty()) {
                            full.append(token);
                            onToken.accept(token);
                        }
                    } catch (Exception ignored) {
                        // Skip malformed chunks
                    }
                }
            }
            String result = full.toString();
            return result.isEmpty() ? FALLBACK_TEXT : result;
        }
    }

    // ---- Embeddings ----

    public float[] embed(String text) throws IOException {
        List<float[]> result = batchEmbed(List.of(text));
        if (result.isEmpty()) throw new IOException("OpenAI returned no embedding");
        return result.get(0);
    }

    @Retryable(retryFor = IOException.class, maxAttempts = 3,
               backoff = @Backoff(delay = 2000, multiplier = 2, maxDelay = 10000))
    public List<float[]> batchEmbed(List<String> texts) throws IOException {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getEmbeddingModel());
        requestBody.put("input", texts);
        requestBody.put("dimensions", config.getEmbeddingDimension());

        String json = objectMapper.writeValueAsString(requestBody);
        RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(config.getBaseUrl() + "/v1/embeddings")
                .header("Authorization", "Bearer " + config.getApiKey())
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String err = response.body() != null ? response.body().string() : "Unknown error";
                if (response.code() == 429) {
                    throw new GeminiRateLimitException("OpenAI embedding quota exhausted");
                }
                throw new IOException("OpenAI embedding error: " + response.code() + " - " + err);
            }
            JsonNode data = objectMapper.readTree(response.body().string()).path("data");
            List<float[]> results = new ArrayList<>();
            if (data.isArray()) {
                for (JsonNode item : data) {
                    JsonNode values = item.path("embedding");
                    float[] vec = new float[values.size()];
                    for (int i = 0; i < values.size(); i++) {
                        vec[i] = (float) values.get(i).asDouble();
                    }
                    results.add(vec);
                }
            }
            if (results.size() != texts.size()) {
                throw new IOException("OpenAI returned " + results.size()
                        + " embeddings for " + texts.size() + " texts");
            }
            return results;
        }
    }

    // ---- Transcription (voice message -> text) ----

    @Retryable(retryFor = IOException.class, maxAttempts = 2,
               backoff = @Backoff(delay = 1500, multiplier = 2, maxDelay = 6000))
    public String transcribeAudio(byte[] bytes, String mimeType) throws IOException {
        String filename = "audio" + extensionForMime(mimeType);
        RequestBody fileBody = RequestBody.create(bytes, MediaType.parse(mimeType));

        MultipartBody multipart = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", filename, fileBody)
                .addFormDataPart("model", config.getTranscribeModel())
                .addFormDataPart("response_format", "json")
                .build();

        Request request = new Request.Builder()
                .url(config.getBaseUrl() + "/v1/audio/transcriptions")
                .header("Authorization", "Bearer " + config.getApiKey())
                .post(multipart)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String err = response.body() != null ? response.body().string() : "Unknown error";
                log.error("OpenAI transcribe error: status={} body={}", response.code(), err);
                if (response.code() == 429) {
                    throw new GeminiRateLimitException("OpenAI transcription quota exhausted");
                }
                throw new IOException("OpenAI transcribe error: " + response.code() + " - " + err);
            }
            String text = objectMapper.readTree(response.body().string()).path("text").asText("");
            return text.trim();
        }
    }

    private static String extensionForMime(String mime) {
        if (mime == null) return ".m4a";
        return switch (mime) {
            case "audio/mpeg", "audio/mp3" -> ".mp3";
            case "audio/wav", "audio/x-wav" -> ".wav";
            case "audio/ogg", "audio/opus" -> ".ogg";
            case "audio/flac" -> ".flac";
            case "audio/webm" -> ".webm";
            default -> ".m4a";
        };
    }

    // ---- OCR (image -> text) via gpt-4o vision ----

    /**
     * OCR a batch of images in one vision call. For OpenAI this also serves the
     * "files API" path — images are sent inline as data URLs, no upload step.
     */
    public String ocrImages(List<byte[]> images) throws IOException {
        if (images == null || images.isEmpty()) return "";

        List<Map<String, Object>> content = new ArrayList<>();
        for (byte[] img : images) {
            String mime = detectImageMime(img);
            String dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(img);
            content.add(Map.of("type", "image_url", "image_url", Map.of("url", dataUrl)));
        }
        content.add(Map.of("type", "text", "text",
                "You are performing OCR on a scanned official document. " +
                "Extract ALL visible text exactly as it appears, in any language (Uzbek, Russian, etc.). " +
                "Preserve numbers, dates, names, document numbers, headings and paragraph structure. " +
                "Return only the extracted text — no explanations or translations."));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getOcrModel());
        requestBody.put("messages", List.of(Map.of("role", "user", "content", content)));

        String json = objectMapper.writeValueAsString(requestBody);
        try (Response response = execute(json)) {
            JsonNode root = objectMapper.readTree(response.body() != null ? response.body().string() : "");
            String text = root.path("choices").path(0).path("message").path("content").asText("");
            log.info("OpenAI OCR extracted {} chars from {} image(s)", text.length(), images.size());
            return text;
        }
    }

    /** OpenAI has no separate Files API for OCR — inline images cover it. */
    public String ocrImagesViaFilesApi(List<byte[]> images) throws IOException {
        return ocrImages(images);
    }

    /**
     * OpenAI vision does not accept a raw multi-page PDF the way Gemini's Files API
     * does. Signal "unsupported" so DocumentService falls back to rendering PDF
     * pages to images and calling {@link #ocrImagesViaFilesApi}.
     */
    public String ocrDocument(byte[] bytes, String mimeType) throws IOException {
        throw new IOException("OpenAI OCR requires page images; render PDF to images first");
    }

    private String detectImageMime(byte[] bytes) {
        if (bytes.length >= 2 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8) return "image/jpeg";
        if (bytes.length >= 4 && bytes[0] == (byte) 0x89 && bytes[1] == 'P'
                && bytes[2] == 'N' && bytes[3] == 'G') return "image/png";
        if (bytes.length >= 3 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') return "image/gif";
        return "image/jpeg";
    }

    /** Sends the request and validates the HTTP status. Caller must close the Response. */
    private Response execute(String json) throws IOException {
        String url = config.getBaseUrl() + "/v1/chat/completions";
        RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + config.getApiKey())
                .post(body)
                .build();

        Response response = httpClient.newCall(request).execute();
        if (!response.isSuccessful()) {
            String errorBody = response.body() != null ? response.body().string() : "Unknown error";
            int code = response.code();
            response.close();
            log.error("OpenAI API error: status={} model={} body={}", code, config.getModel(), errorBody);
            if (code == 429) {
                throw new GeminiRateLimitException("OpenAI quota/rate limit exhausted for model " + config.getModel());
            }
            throw new IOException("OpenAI API error: " + code + " - " + errorBody);
        }
        return response;
    }

    /**
     * Builds an OpenAI Chat Completions body from the canonical Gemini-shaped
     * inputs. Gemini history items look like {@code {role: "user"|"model",
     * parts: [{text: "..."}]}}; OpenAI wants {@code {role: "user"|"assistant",
     * content: "..."}}.
     */
    private Map<String, Object> buildRequestBody(String systemInstruction,
                                                 List<Map<String, Object>> history,
                                                 String userMessage,
                                                 boolean stream) {
        List<Map<String, Object>> messages = new ArrayList<>();

        if (systemInstruction != null && !systemInstruction.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemInstruction));
        }

        if (history != null) {
            for (Map<String, Object> item : history) {
                String role = "model".equals(item.get("role")) ? "assistant" : "user";
                String text = extractText(item);
                if (!text.isBlank()) {
                    messages.add(Map.of("role", role, "content", text));
                }
            }
        }

        messages.add(Map.of("role", "user", "content", userMessage));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getModel());
        requestBody.put("messages", messages);
        if (stream) {
            requestBody.put("stream", true);
        }
        return requestBody;
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> geminiMessage) {
        Object parts = geminiMessage.get("parts");
        if (parts instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first) {
            Object text = ((Map<String, Object>) first).get("text");
            return text != null ? text.toString() : "";
        }
        // Fallback: some stored shapes may carry a plain "content"/"text" field.
        Object content = geminiMessage.getOrDefault("content", geminiMessage.get("text"));
        return content != null ? content.toString() : "";
    }
}
