package com.manager.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manager.config.PineconeConfig;
import com.manager.repository.DocumentRepository;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class RagService {

    private final GeminiService geminiService;
    private final PineconeConfig pineconeConfig;
    private final DocumentRepository documentRepository;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public RagService(GeminiService geminiService, PineconeConfig pineconeConfig,
                      DocumentRepository documentRepository) {
        this.geminiService = geminiService;
        this.pineconeConfig = pineconeConfig;
        this.documentRepository = documentRepository;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public List<String> query(String queryText, String departmentId, int topK) throws Exception {
        // Embed query text
        float[] queryVector = geminiService.embed(queryText);

        // Query Pinecone
        String url = pineconeConfig.getIndexUrl() + "/query";

        Map<String, Object> requestBody = new HashMap<>();
        // Convert float[] to List<Float>
        List<Float> vectorList = new ArrayList<>();
        for (float f : queryVector) {
            vectorList.add(f);
        }
        requestBody.put("vector", vectorList);
        requestBody.put("topK", topK > 0 ? topK : 5);
        requestBody.put("includeMetadata", true);

        // Filter by departmentId
        Map<String, Object> filter = new HashMap<>();
        filter.put("departmentId", Map.of("$eq", departmentId));
        requestBody.put("filter", filter);

        String json = objectMapper.writeValueAsString(requestBody);

        RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Api-Key", pineconeConfig.getApiKey())
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return Collections.emptyList();
            }

            String responseBody = response.body().string();
            JsonNode rootNode = objectMapper.readTree(responseBody);
            JsonNode matches = rootNode.path("matches");

            List<String> results = new ArrayList<>();
            if (matches.isArray()) {
                for (JsonNode match : matches) {
                    String vectorId = match.path("id").asText();
                    // Fetch chunk content from Firestore
                    Map<String, Object> chunk = documentRepository.findChunkByVectorId(vectorId);
                    if (chunk != null && chunk.containsKey("content")) {
                        results.add(chunk.get("content").toString());
                    }
                }
            }
            return results;
        }
    }
}
