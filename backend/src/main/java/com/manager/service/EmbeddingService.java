package com.manager.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manager.config.PineconeConfig;
import com.manager.dto.DocumentDto;
import com.manager.repository.DocumentRepository;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class EmbeddingService {

    private static final int BATCH_SIZE = 20;

    private final AiService aiService;
    private final PineconeConfig pineconeConfig;
    private final DocumentRepository documentRepository;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public EmbeddingService(AiService aiService, PineconeConfig pineconeConfig,
                            DocumentRepository documentRepository) {
        this.aiService = aiService;
        this.pineconeConfig = pineconeConfig;
        this.documentRepository = documentRepository;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public void embedAndStore(String documentId, String departmentId, List<String> chunks) throws Exception {
        List<String> cleanChunks = new ArrayList<>();
        if (chunks != null) {
            for (String chunk : chunks) {
                if (chunk != null && !chunk.trim().isEmpty()) {
                    cleanChunks.add(chunk.trim());
                }
            }
        }

        String documentTitle = "";
        try {
            DocumentDto document = documentRepository.findById(documentId);
            if (document != null) {
                documentTitle = document.getTitle() != null && !document.getTitle().isBlank()
                        ? document.getTitle().trim()
                        : Objects.toString(document.getFileName(), "").trim();
            }
        } catch (Exception ignored) {
            // Title is useful retrieval context, but indexing must not fail without it.
        }

        // Process in batches
        for (int batchStart = 0; batchStart < cleanChunks.size(); batchStart += BATCH_SIZE) {
            int batchEnd = Math.min(batchStart + BATCH_SIZE, cleanChunks.size());
            List<String> batch = cleanChunks.subList(batchStart, batchEnd);

            // Include the source title in the semantic input. The stored chunk
            // remains unchanged, while searches for a decree/document name get
            // substantially better recall.
            List<String> embeddingInputs = new ArrayList<>(batch.size());
            for (String chunk : batch) {
                embeddingInputs.add(documentTitle.isBlank()
                        ? chunk
                        : "Hujjat: " + documentTitle + "\n" + chunk);
            }
            List<float[]> embeddings = aiService.batchEmbed(embeddingInputs);

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
                metadata.put("content", chunk);
                if (!documentTitle.isBlank()) metadata.put("documentTitle", documentTitle);
                vector.put("metadata", metadata);

                vectors.add(vector);

                // Save chunk to Firestore
                Map<String, Object> chunkData = new HashMap<>();
                chunkData.put("documentId", documentId);
                chunkData.put("departmentId", departmentId);
                chunkData.put("chunkIndex", globalIndex);
                chunkData.put("content", chunk);
                chunkData.put("vectorId", vectorId);
                if (!documentTitle.isBlank()) chunkData.put("documentTitle", documentTitle);
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
