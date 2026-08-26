package com.manager.service;

import com.manager.config.PineconeConfig;
import com.manager.dto.RagSource;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagServiceTest {

    private MockWebServer server;
    private AiService aiService;
    private RagService ragService;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();

        aiService = mock(AiService.class);
        PineconeConfig config = mock(PineconeConfig.class);
        when(config.getIndexUrl()).thenReturn(server.url("").toString().replaceAll("/$", ""));
        when(config.getApiKey()).thenReturn("test-key");
        when(aiService.embed("dori olish")).thenReturn(new float[]{0.1f, 0.2f});

        ragService = new RagService(aiService, config);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void metadataBackedMatchPreservesCitation() throws Exception {
        server.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {"matches":[{
                          "id":"doc-1_chunk_0",
                          "score":0.86,
                          "metadata":{
                            "documentId":"doc-1",
                            "documentTitle":"VMQ-123-son qarori",
                            "chunkIndex":0,
                            "content":"17-band. Dori bepul beriladi."
                          }
                        }]}
                        """));

        List<RagSource> result = ragService.query("dori olish", "dept-1", 8);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).documentTitle()).isEqualTo("VMQ-123-son qarori");
        assertThat(result.get(0).content()).contains("17-band");

        String requestJson = server.takeRequest().getBody().readUtf8();
        assertThat(requestJson).contains("\"includeMetadata\":true");
    }

    @Test
    void lowScoreMatchIsDiscarded() throws Exception {
        server.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {"matches":[{
                          "id":"irrelevant",
                          "score":0.2,
                          "metadata":{"content":"Aloqasiz matn"}
                        }]}
                        """));

        assertThat(ragService.query("dori olish", "dept-1", 8)).isEmpty();
    }

    @Test
    void matchWithoutContentMetadataIsIgnoredWithoutSecondaryLookup() throws Exception {
        server.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {"matches":[{
                          "id":"legacy-vector",
                          "score":0.9,
                          "metadata":{"documentId":"doc-1","documentTitle":"Old document"}
                        }]}
                        """));

        assertThat(ragService.query("dori olish", "dept-1", 8)).isEmpty();
    }
}
