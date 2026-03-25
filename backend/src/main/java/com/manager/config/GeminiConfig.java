package com.manager.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GeminiConfig {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.model}")
    private String model;

    @Value("${gemini.embedding.model}")
    private String embeddingModel;

    /** Override in tests to point at MockWebServer. */
    @Value("${gemini.base.url:https://generativelanguage.googleapis.com}")
    private String baseUrl;

    public String getApiKey() { return apiKey; }
    public String getModel() { return model; }
    public String getEmbeddingModel() { return embeddingModel; }
    public String getBaseUrl() { return baseUrl; }
}
