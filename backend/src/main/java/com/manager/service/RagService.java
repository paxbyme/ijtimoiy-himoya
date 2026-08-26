package com.manager.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manager.config.PineconeConfig;
import com.manager.dto.RagSource;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private static final int MAX_TOP_K = 10;

    private final AiService aiService;
    private final PineconeConfig pineconeConfig;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${rag.min-score:0.45}")
    private double minScore = 0.45;

    public RagService(AiService aiService, PineconeConfig pineconeConfig) {
        this.aiService = aiService;
        this.pineconeConfig = pineconeConfig;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(4, TimeUnit.SECONDS)
                .writeTimeout(4, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Searches Pinecone and returns ranked, source-aware evidence directly from
     * match metadata. This keeps Firestore completely off the RAG request path.
     * Vectors without embedded content metadata are ignored and must be
     * re-indexed by the document ingestion pipeline.
     */
    public List<RagSource> query(String queryText, String departmentId, int requestedTopK) throws Exception {
        if (queryText == null || queryText.isBlank()
                || departmentId == null || departmentId.isBlank()) {
            return List.of();
        }

        float[] queryVector = aiService.embed(queryText.trim());
        int topK = Math.max(1, Math.min(requestedTopK > 0 ? requestedTopK : 6, MAX_TOP_K));

        Map<String, Object> requestBody = new HashMap<>();
        List<Float> vector = new ArrayList<>(queryVector.length);
        for (float value : queryVector) vector.add(value);
        requestBody.put("vector", vector);
        requestBody.put("topK", topK);
        requestBody.put("includeMetadata", true);
        requestBody.put("filter", Map.of("departmentId", Map.of("$eq", departmentId)));

        Request request = new Request.Builder()
                .url(pineconeConfig.getIndexUrl() + "/query")
                .post(RequestBody.create(
                        objectMapper.writeValueAsString(requestBody),
                        MediaType.parse("application/json")))
                .addHeader("Api-Key", pineconeConfig.getApiKey())
                .addHeader("Content-Type", "application/json")
                .build();

        List<RawMatch> matches;
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("Pinecone query failed: status={}", response.code());
                return List.of();
            }
            String responseBody = response.body() != null ? response.body().string() : "{}";
            matches = parseMatches(objectMapper.readTree(responseBody).path("matches"));
        }

        if (matches.isEmpty()) return List.of();

        List<RagSource> results = new ArrayList<>();
        Set<String> seenContent = new LinkedHashSet<>();
        int missingContentCount = 0;
        for (RawMatch match : matches) {
            String content = match.content.trim();
            if (content.isBlank()) {
                missingContentCount++;
                continue;
            }

            String documentId = match.documentId;
            String title = firstNonBlank(
                    match.documentTitle,
                    "Noma'lum hujjat");

            String dedupeKey = content.replaceAll("\\s+", " ")
                    .trim().toLowerCase(Locale.ROOT);
            if (dedupeKey.length() > 180) dedupeKey = dedupeKey.substring(0, 180);
            if (!seenContent.add(dedupeKey)) continue;

            results.add(new RagSource(
                    match.vectorId, documentId, title, match.chunkIndex, match.score, content));
        }
        if (missingContentCount > 0) {
            log.warn("Ignored {} Pinecone match(es) without content metadata; re-index those vectors",
                    missingContentCount);
        }
        return results;
    }

    private List<RawMatch> parseMatches(JsonNode matchesNode) {
        if (!matchesNode.isArray()) return List.of();

        List<RawMatch> matches = new ArrayList<>();
        for (JsonNode match : matchesNode) {
            double score = match.path("score").asDouble(0.0);
            if (score < minScore) continue;

            JsonNode metadata = match.path("metadata");
            String vectorId = match.path("id").asText("");
            if (vectorId.isBlank()) continue;

            matches.add(new RawMatch(
                    vectorId,
                    metadata.path("documentId").asText(""),
                    firstNonBlank(
                            metadata.path("documentTitle").asText(""),
                            metadata.path("fileName").asText("")),
                    metadata.path("chunkIndex").asInt(-1),
                    score,
                    metadata.path("content").asText("")));
        }
        return matches;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }

    private record RawMatch(
            String vectorId,
            String documentId,
            String documentTitle,
            int chunkIndex,
            double score,
            String content
    ) {}
}
