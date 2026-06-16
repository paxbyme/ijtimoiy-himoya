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
