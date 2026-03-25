package com.manager.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manager.config.PineconeConfig;
import com.manager.repository.DocumentRepository;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class EmbeddingService {

    private static final int BATCH_SIZE = 20;

    private final GeminiService geminiService;
    private final PineconeConfig pineconeConfig;
    private final DocumentRepository documentRepository;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public EmbeddingService(GeminiService geminiService, PineconeConfig pineconeConfig,
                            DocumentRepository documentRepository) {
        this.geminiService = geminiService;
        this.pineconeConfig = pineconeConfig;
        this.documentRepository = documentRepository;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public void embedAndStore(String documentId, String departmentId, List<String> chunks) throws Exception {
        // Process in batches
        for (int batchStart = 0; batchStart < chunks.size(); batchStart += BATCH_SIZE) {
            int batchEnd = Math.min(batchStart + BATCH_SIZE, chunks.size());
            List<String> batch = chunks.subList(batchStart, batchEnd);

            // Batch embed
            List<float[]> embeddings = geminiService.batchEmbed(batch);

            List<Map<String, Object>> vectors = new ArrayList<>();

            for (int i = 0; i < batch.size(); i++) {
                int globalIndex = batchStart + i;
                String chunk = batch.get(i);
                float[] embedding = embeddings.get(i);

                String vectorId = documentId + "_chunk_" + globalIndex;

                // Prepare Pinecone vector
                Map<String, Object> vector = new HashMap<>();
                vector.put("id", vectorId);

                List<Float> values = new ArrayList<>();
                for (float f : embedding) {
                    values.add(f);
                }
                vector.put("values", values);

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("documentId", documentId);
                metadata.put("departmentId", departmentId);
                metadata.put("chunkIndex", globalIndex);
                vector.put("metadata", metadata);

                vectors.add(vector);

                // Save chunk to Firestore
                Map<String, Object> chunkData = new HashMap<>();
                chunkData.put("documentId", documentId);
                chunkData.put("departmentId", departmentId);
                chunkData.put("chunkIndex", globalIndex);
                chunkData.put("content", chunk);
                chunkData.put("vectorId", vectorId);
                documentRepository.saveChunk(chunkData);
            }

            // Upsert batch to Pinecone
            if (!vectors.isEmpty()) {
                upsertToPinecone(vectors);
            }
        }
    }

    private void upsertToPinecone(List<Map<String, Object>> vectors) throws IOException {
        String url = pineconeConfig.getIndexUrl() + "/vectors/upsert";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("vectors", vectors);

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
                String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                throw new IOException("Pinecone upsert error: " + response.code() + " - " + errorBody);
            }
        }
    }

    public void deleteVectors(String documentId, int chunkCount) throws IOException {
        String url = pineconeConfig.getIndexUrl() + "/vectors/delete";

        List<String> ids = new ArrayList<>();
        for (int i = 0; i < chunkCount; i++) {
            ids.add(documentId + "_chunk_" + i);
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("ids", ids);

        String json = objectMapper.writeValueAsString(requestBody);

        RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Api-Key", pineconeConfig.getApiKey())
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            // Best effort deletion
        }
    }
}
