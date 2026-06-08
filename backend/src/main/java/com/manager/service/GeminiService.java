package com.manager.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manager.config.GeminiConfig;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    private final GeminiConfig geminiConfig;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GeminiService(GeminiConfig geminiConfig) {
        this.geminiConfig = geminiConfig;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Retryable(retryFor = IOException.class, maxAttempts = 3,
               backoff = @Backoff(delay = 2000, multiplier = 2, maxDelay = 10000))
    public String chat(String systemInstruction, List<Map<String, Object>> history, String userMessage) throws IOException {
        String url = String.format(
                "%s/v1beta/models/%s:generateContent?key=%s",
                geminiConfig.getBaseUrl(), geminiConfig.getModel(), geminiConfig.getApiKey()
        );

        Map<String, Object> requestBody = buildRequestBody(systemInstruction, history, userMessage);
        String json = objectMapper.writeValueAsString(requestBody);

        RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                log.error("Gemini API error: status={} body={}", response.code(), errorBody);
                throw new IOException("Gemini API error: " + response.code() + " - " + errorBody);
            }

            String responseBody = response.body().string();
            log.debug("Gemini chat response received");
            return extractTextFromResponse(responseBody);
        }
    }

    /**
     * Stream chat response from Gemini. Calls onToken for each text chunk.
     * Returns the full assembled response.
     */
    @Retryable(retryFor = IOException.class, maxAttempts = 3,
               backoff = @Backoff(delay = 2000, multiplier = 2, maxDelay = 10000))
    public String chatStream(String systemInstruction, List<Map<String, Object>> history,
                             String userMessage, Consumer<String> onToken) throws IOException {
        String url = String.format(
                "%s/v1beta/models/%s:streamGenerateContent?key=%s&alt=sse",
                geminiConfig.getBaseUrl(), geminiConfig.getModel(), geminiConfig.getApiKey()
        );

        Map<String, Object> requestBody = buildRequestBody(systemInstruction, history, userMessage);
        String json = objectMapper.writeValueAsString(requestBody);

        RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                throw new IOException("Gemini streaming error: " + response.code() + " - " + errorBody);
            }

            StringBuilder fullResponse = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body().byteStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6).trim();
                        if (data.equals("[DONE]")) break;
                        try {
                            String text = extractTextFromResponse(data);
                            if (text != null && !text.isEmpty() &&
                                    !text.equals("I'm sorry, I couldn't generate a response.")) {
                                fullResponse.append(text);
                                onToken.accept(text);
                            }
                        } catch (Exception ignored) {
                            // Skip malformed chunks
                        }
                    }
                }
            }

            String result = fullResponse.toString();
            return result.isEmpty() ? "I'm sorry, I couldn't generate a response." : result;
        }
    }

    /**
     * OCR: sends images to Gemini Vision and returns extracted text.
     * Processes up to 10 images per call to stay within inline-data limits.
     * For documents with more images, call this method in batches and combine.
     */
    public String ocrImages(List<byte[]> images) throws IOException {
        if (images.isEmpty()) return "";

        String url = String.format(
                "%s/v1beta/models/%s:generateContent?key=%s",
                geminiConfig.getBaseUrl(), geminiConfig.getOcrModel(), geminiConfig.getApiKey()
        );

        List<Map<String, Object>> parts = new ArrayList<>();
        int limit = Math.min(images.size(), 10);
        for (int i = 0; i < limit; i++) {
            byte[] imgBytes = images.get(i);
            Map<String, Object> inlineData = new HashMap<>();
            inlineData.put("mimeType", detectMimeType(imgBytes));
            inlineData.put("data", Base64.getEncoder().encodeToString(imgBytes));
            parts.add(Map.of("inlineData", inlineData));
        }
        parts.add(Map.of("text",
                "You are performing OCR on a scanned official document. " +
                "Extract ALL visible text exactly as it appears, including text in any language (Uzbek, Russian, etc.). " +
                "Preserve all numbers, dates, names, document numbers, section headings, and paragraph structure. " +
                "Return only the extracted text — no explanations, commentary, or translations."));

        Map<String, Object> content = Map.of("role", "user", "parts", parts);
        Map<String, Object> requestBody = Map.of("contents", List.of(content));

        String json = objectMapper.writeValueAsString(requestBody);
        RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
        Request request = new Request.Builder().url(url).post(body).build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                log.error("Gemini OCR error: status={} body={}", response.code(), errorBody);
                throw new IOException("Gemini OCR error: " + response.code() + " - " + errorBody);
            }
            String result = extractTextFromResponse(response.body().string());
            log.info("Gemini OCR extracted {} chars from {} images", result.length(), limit);
            return result;
        }
    }

    /**
     * Uploads a file to the Gemini Files API using multipart/related upload.
     * Returns the file URI (e.g. "https://generativelanguage.googleapis.com/v1beta/files/abc123").
     * Uploaded files are automatically deleted by Google after 48 hours.
     */
    public String uploadToFilesApi(byte[] bytes, String mimeType) throws IOException {
        String url = String.format("%s/upload/v1beta/files?key=%s",
                geminiConfig.getBaseUrl(), geminiConfig.getApiKey());

        String boundary = "bound_" + System.currentTimeMillis();
        String CRLF = "\r\n";
        String metadataJson = "{\"file\":{\"display_name\":\"document\"}}";

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(("--" + boundary + CRLF).getBytes(StandardCharsets.UTF_8));
        baos.write(("Content-Type: application/json; charset=UTF-8" + CRLF + CRLF).getBytes(StandardCharsets.UTF_8));
        baos.write((metadataJson + CRLF).getBytes(StandardCharsets.UTF_8));
        baos.write(("--" + boundary + CRLF).getBytes(StandardCharsets.UTF_8));
        baos.write(("Content-Type: " + mimeType + CRLF + CRLF).getBytes(StandardCharsets.UTF_8));
        baos.write(bytes);
        baos.write((CRLF + "--" + boundary + "--" + CRLF).getBytes(StandardCharsets.UTF_8));

        RequestBody body = RequestBody.create(baos.toByteArray(),
                MediaType.parse("multipart/related; boundary=" + boundary));
        Request request = new Request.Builder()
                .url(url)
                .header("X-Goog-Upload-Protocol", "multipart")
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String err = response.body() != null ? response.body().string() : "";
                throw new IOException("Files API upload failed: " + response.code() + " - " + err);
            }
            JsonNode root = objectMapper.readTree(response.body().string());
            String fileUri = root.path("file").path("uri").asText(null);
            if (fileUri == null || fileUri.isEmpty()) {
                throw new IOException("Files API did not return a file URI");
            }
            log.debug("Uploaded to Files API: uri={} size={}B mimeType={}", fileUri, bytes.length, mimeType);
            return fileUri;
        }
    }

    /**
     * OCR a single document (PDF or image) via Files API — one upload + one inference call.
     * Preferred over inline-data for large or multi-page documents.
     */
    public String ocrDocument(byte[] bytes, String mimeType) throws IOException {
        String fileUri = uploadToFilesApi(bytes, mimeType);
        return ocrFileUris(List.of(fileUri), List.of(mimeType));
    }

    /**
     * OCR multiple images via Files API — upload each image then one single inference call.
     * Eliminates the need for batching: all pages are processed together.
     */
    public String ocrImagesViaFilesApi(List<byte[]> images) throws IOException {
        if (images.isEmpty()) return "";
        List<String> uris = new ArrayList<>(images.size());
        List<String> mimeTypes = new ArrayList<>(images.size());
        for (byte[] img : images) {
            String mime = detectMimeType(img);
            uris.add(uploadToFilesApi(img, mime));
            mimeTypes.add(mime);
        }
        return ocrFileUris(uris, mimeTypes);
    }

    private String ocrFileUris(List<String> fileUris, List<String> mimeTypes) throws IOException {
        String url = String.format("%s/v1beta/models/%s:generateContent?key=%s",
                geminiConfig.getBaseUrl(), geminiConfig.getOcrModel(), geminiConfig.getApiKey());

        List<Map<String, Object>> parts = new ArrayList<>();
        for (int i = 0; i < fileUris.size(); i++) {
            parts.add(Map.of("file_data", Map.of(
                    "mime_type", mimeTypes.get(i),
                    "file_uri", fileUris.get(i))));
        }
        parts.add(Map.of("text",
                "You are performing OCR on a scanned official document. " +
                "Extract ALL visible text exactly as it appears, including text in any language (Uzbek, Russian, etc.). " +
                "Preserve all numbers, dates, names, document numbers, section headings, and paragraph structure. " +
                "Return only the extracted text — no explanations, commentary, or translations."));

        Map<String, Object> content = Map.of("role", "user", "parts", parts);
        Map<String, Object> requestBody = Map.of("contents", List.of(content));

        String json = objectMapper.writeValueAsString(requestBody);
        RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
        Request request = new Request.Builder().url(url).post(body).build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String err = response.body() != null ? response.body().string() : "";
                log.error("Gemini OCR (Files API) error: status={} body={}", response.code(), err);
                throw new IOException("Gemini OCR error: " + response.code() + " - " + err);
            }
            String result = extractTextFromResponse(response.body().string());
            log.info("Files API OCR extracted {} chars from {} file(s)", result.length(), fileUris.size());
            return result;
        }
    }

    /**
     * Transcribe audio (voice message) via Gemini Files API.
     * Optimized for Uzbek-language input but handles mixed Uzbek/Russian as well.
     * Returns the transcript text only — no commentary.
     */
    @Retryable(retryFor = IOException.class, maxAttempts = 2,
               backoff = @Backoff(delay = 1500, multiplier = 2, maxDelay = 6000))
    public String transcribeAudio(byte[] bytes, String mimeType) throws IOException {
        String fileUri = uploadToFilesApi(bytes, mimeType);

        String url = String.format("%s/v1beta/models/%s:generateContent?key=%s",
                geminiConfig.getBaseUrl(), geminiConfig.getModel(), geminiConfig.getApiKey());

        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("file_data", Map.of(
                "mime_type", mimeType,
                "file_uri", fileUri)));
        parts.add(Map.of("text",
                "Transcribe this voice message verbatim. " +
                "The speaker is most likely speaking Uzbek (may include Russian or English words). " +
                "Return ONLY the transcript text, no translations, no commentary, no quotes, no labels. " +
                "Preserve punctuation naturally. If the audio is silent or unintelligible, return an empty string."));

        Map<String, Object> content = Map.of("role", "user", "parts", parts);
        Map<String, Object> requestBody = Map.of("contents", List.of(content));

        String json = objectMapper.writeValueAsString(requestBody);
        RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
        Request request = new Request.Builder().url(url).post(body).build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String err = response.body() != null ? response.body().string() : "";
                log.error("Gemini transcribe error: status={} body={}", response.code(), err);
                throw new IOException("Gemini transcribe error: " + response.code() + " - " + err);
            }
            String result = extractTextFromResponse(response.body().string());
            if ("I'm sorry, I couldn't generate a response.".equals(result)) {
                return "";
            }
            log.info("Audio transcription: {} chars from {}B {}", result.length(), bytes.length, mimeType);
            return result.trim();
        }
    }

    private String detectMimeType(byte[] bytes) {
        if (bytes.length >= 2 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8) return "image/jpeg";
        if (bytes.length >= 4 && bytes[0] == (byte) 0x89 && bytes[1] == 'P'
                && bytes[2] == 'N' && bytes[3] == 'G') return "image/png";
        return "image/jpeg";
    }

    public float[] embed(String text) throws IOException {
        String url = String.format(
                "%s/v1beta/models/%s:embedContent?key=%s",
                geminiConfig.getBaseUrl(), geminiConfig.getEmbeddingModel(), geminiConfig.getApiKey()
        );

        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> content = new HashMap<>();
        Map<String, String> part = new HashMap<>();
        part.put("text", text);
        content.put("parts", List.of(part));
        requestBody.put("content", content);
        requestBody.put("outputDimensionality", geminiConfig.getEmbeddingDimension());

        String json = objectMapper.writeValueAsString(requestBody);

        RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                throw new IOException("Gemini Embedding API error: " + response.code() + " - " + errorBody);
            }

            String responseBody = response.body().string();
            JsonNode rootNode = objectMapper.readTree(responseBody);
            JsonNode values = rootNode.path("embedding").path("values");

            if (values.isArray()) {
                float[] embeddings = new float[values.size()];
                for (int i = 0; i < values.size(); i++) {
                    embeddings[i] = (float) values.get(i).asDouble();
                }
                return embeddings;
            }
            throw new IOException("Failed to parse embedding response");
        }
    }

    /**
     * Batch embed multiple texts in a single API call.
     */
    @Retryable(retryFor = IOException.class, maxAttempts = 3,
               backoff = @Backoff(delay = 2000, multiplier = 2, maxDelay = 10000))
    public List<float[]> batchEmbed(List<String> texts) throws IOException {
        String url = String.format(
                "%s/v1beta/models/%s:batchEmbedContents?key=%s",
                geminiConfig.getBaseUrl(), geminiConfig.getEmbeddingModel(), geminiConfig.getApiKey()
        );

        List<Map<String, Object>> requests = new ArrayList<>();
        for (String text : texts) {
            Map<String, Object> req = new HashMap<>();
            Map<String, Object> content = new HashMap<>();
            content.put("parts", List.of(Map.of("text", text)));
            req.put("model", "models/" + geminiConfig.getEmbeddingModel());
            req.put("content", content);
            req.put("outputDimensionality", geminiConfig.getEmbeddingDimension());
            requests.add(req);
        }

        Map<String, Object> requestBody = Map.of("requests", requests);
        String json = objectMapper.writeValueAsString(requestBody);

        RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                throw new IOException("Gemini Batch Embedding error: " + response.code() + " - " + errorBody);
            }

            String responseBody = response.body().string();
            JsonNode rootNode = objectMapper.readTree(responseBody);
            JsonNode embeddings = rootNode.path("embeddings");

            List<float[]> results = new ArrayList<>();
            if (embeddings.isArray()) {
                for (JsonNode embedding : embeddings) {
                    JsonNode values = embedding.path("values");
                    if (values.isArray()) {
                        float[] vec = new float[values.size()];
                        for (int i = 0; i < values.size(); i++) {
                            vec[i] = (float) values.get(i).asDouble();
                        }
                        results.add(vec);
                    }
                }
            }
            if (results.size() != texts.size()) {
                throw new IOException("Gemini batch embedding returned " + results.size()
                        + " embeddings for " + texts.size() + " texts");
            }
            return results;
        }
    }

    private Map<String, Object> buildRequestBody(String systemInstruction,
                                                   List<Map<String, Object>> history,
                                                   String userMessage) {
        Map<String, Object> requestBody = new HashMap<>();

        // System instruction
        if (systemInstruction != null && !systemInstruction.isEmpty()) {
            Map<String, Object> systemInstr = new HashMap<>();
            Map<String, String> textPart = new HashMap<>();
            textPart.put("text", systemInstruction);
            systemInstr.put("parts", List.of(textPart));
            requestBody.put("system_instruction", systemInstr);
        }

        // Build contents array
        List<Map<String, Object>> contents = new ArrayList<>();

        // Add history
        if (history != null) {
            contents.addAll(history);
        }

        // Add new user message
        Map<String, Object> userContent = new HashMap<>();
        userContent.put("role", "user");
        Map<String, String> userPart = new HashMap<>();
        userPart.put("text", userMessage);
        userContent.put("parts", List.of(userPart));
        contents.add(userContent);

        requestBody.put("contents", contents);
        return requestBody;
    }

    private String extractTextFromResponse(String responseBody) throws IOException {
        JsonNode rootNode = objectMapper.readTree(responseBody);
        JsonNode candidates = rootNode.path("candidates");
        if (candidates.isArray() && !candidates.isEmpty()) {
            JsonNode content = candidates.get(0).path("content");
            JsonNode parts = content.path("parts");
            if (parts.isArray() && !parts.isEmpty()) {
                return parts.get(0).path("text").asText();
            }
        }
        return "I'm sorry, I couldn't generate a response.";
    }
}
