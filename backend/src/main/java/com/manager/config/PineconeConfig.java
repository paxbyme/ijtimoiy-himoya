package com.manager.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PineconeConfig {

    @Value("${pinecone.api.key}")
    private String apiKey;

    @Value("${pinecone.index.url}")
    private String indexUrl;

    public String getApiKey() {
        return apiKey;
    }

    public String getIndexUrl() {
        return indexUrl;
    }
}
