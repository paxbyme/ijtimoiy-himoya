package com.manager.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class StartupValidationConfig {

    private static final Logger log = LoggerFactory.getLogger(StartupValidationConfig.class);

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    @Value("${pinecone.api.key:}")
    private String pineconeApiKey;

    @Value("${pinecone.index.url:}")
    private String pineconeIndexUrl;

    @Value("${firebase.credentials.path:}")
    private String firebaseCredentialsPath;

    @PostConstruct
    public void validateRequiredProperties() {
        List<String> missing = new ArrayList<>();

        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            missing.add("GEMINI_API_KEY (gemini.api.key)");
        }
        if (pineconeApiKey == null || pineconeApiKey.isBlank()) {
            missing.add("PINECONE_API_KEY (pinecone.api.key)");
        }
        if (pineconeIndexUrl == null || pineconeIndexUrl.isBlank()) {
            missing.add("PINECONE_INDEX_URL (pinecone.index.url)");
        }
        if (firebaseCredentialsPath == null || firebaseCredentialsPath.isBlank()) {
            missing.add("FIREBASE_CREDENTIALS_PATH (firebase.credentials.path)");
        }

        if (!missing.isEmpty()) {
            String msg = "Application startup failed — missing required environment variables:\n  - " +
                         String.join("\n  - ", missing);
            log.error(msg);
            throw new IllegalStateException(msg);
        }

        log.info("All required environment variables are present. AI and RAG pipeline ready.");
    }
}
