package com.manager.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GeminiConfig {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.model}")
    private String model;

    /**
     * Model used for chat when the primary model returns 429 (per-model quota
     * exhausted). Gemini token quotas are per-model-per-minute, so a different
     * model id has its own separate bucket. Empty disables fallback.
     */
    @Value("${gemini.fallback.model:gemini-2.5-flash-lite}")
    private String fallbackModel;

    @Value("${gemini.ocr.model:gemini-2.5-flash-lite}")
    private String ocrModel;

    @Value("${gemini.embedding.model}")
    private String embeddingModel;

    @Value("${gemini.embedding.dimension:512}")
    private int embeddingDimension;

    @Value("${ai.chat.max-output-tokens:2400}")
    private int chatMaxOutputTokens;

    /** Override in tests to point at MockWebServer. */
    @Value("${gemini.base.url:https://generativelanguage.googleapis.com}")
    private String baseUrl;

    public String getApiKey() { return apiKey; }
    public String getModel() { return model; }
    public String getFallbackModel() { return fallbackModel; }
    public String getOcrModel() { return ocrModel; }
    public String getEmbeddingModel() { return embeddingModel; }
    public int getEmbeddingDimension() { return embeddingDimension; }
    public int getChatMaxOutputTokens() { return chatMaxOutputTokens; }
    public String getBaseUrl() { return baseUrl; }
}
