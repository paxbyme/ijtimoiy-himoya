package com.manager.service;

import com.manager.config.GeminiConfig;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeminiServiceTest {

    MockWebServer server;
    GeminiService geminiService;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        GeminiConfig config = mock(GeminiConfig.class);
        when(config.getBaseUrl()).thenReturn(server.url("").toString().replaceAll("/$", ""));
        when(config.getModel()).thenReturn("gemini-2.0-flash");
        when(config.getEmbeddingModel()).thenReturn("text-embedding-004");
        when(config.getApiKey()).thenReturn("test-key");

        geminiService = new GeminiService(config);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    // ---- chat() ----

    @Test
    void chat_successResponse_returnsExtractedText() throws Exception {
        String mockBody = """
                {
                  "candidates": [{
                    "content": {
                      "parts": [{"text": "Hello, I am your AI assistant."}],
                      "role": "model"
                    }
                  }]
                }
                """;
        server.enqueue(new MockResponse()
                .setBody(mockBody)
                .addHeader("Content-Type", "application/json"));

        String result = geminiService.chat("You are helpful.", List.of(), "Hi");

        assertThat(result).isEqualTo("Hello, I am your AI assistant.");

        RecordedRequest req = server.takeRequest();
        assertThat(req.getMethod()).isEqualTo("POST");
        assertThat(req.getPath()).contains("generateContent");
        assertThat(req.getPath()).contains("test-key");
    }

    @Test
    void chat_emptyParts_returnsDefaultMessage() throws Exception {
        String mockBody = """
                {
                  "candidates": [{
                    "content": { "parts": [], "role": "model" }
                  }]
                }
                """;
        server.enqueue(new MockResponse()
                .setBody(mockBody)
                .addHeader("Content-Type", "application/json"));

        String result = geminiService.chat(null, null, "test");

        assertThat(result).isEqualTo("I'm sorry, I couldn't generate a response.");
    }

    @Test
    void chat_apiError_throwsIOException() {
        server.enqueue(new MockResponse().setResponseCode(429).setBody("Too Many Requests"));

        assertThatThrownBy(() -> geminiService.chat("sys", List.of(), "msg"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("429");
    }

    @Test
    void chat_sendsSystemInstruction() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("""
                        {"candidates":[{"content":{"parts":[{"text":"ok"}]}}]}
                        """)
                .addHeader("Content-Type", "application/json"));

        geminiService.chat("Be concise.", List.of(), "Hello");

        String requestBody = server.takeRequest().getBody().readUtf8();
        assertThat(requestBody).contains("system_instruction");
        assertThat(requestBody).contains("Be concise.");
    }

    @Test
    void chat_includesHistoryInContents() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("""
                        {"candidates":[{"content":{"parts":[{"text":"reply"}]}}]}
                        """)
                .addHeader("Content-Type", "application/json"));

        List<Map<String, Object>> history = List.of(
                Map.of("role", "user",  "parts", List.of(Map.of("text", "prev msg"))),
                Map.of("role", "model", "parts", List.of(Map.of("text", "prev reply")))
        );

        geminiService.chat(null, history, "follow-up");

        String body = server.takeRequest().getBody().readUtf8();
        assertThat(body).contains("prev msg");
        assertThat(body).contains("prev reply");
        assertThat(body).contains("follow-up");
    }

    // ---- embed() ----

    @Test
    void embed_successResponse_returnsFloatArray() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("""
                        {"embedding":{"values":[0.1,0.2,0.3]}}
                        """)
                .addHeader("Content-Type", "application/json"));

        float[] result = geminiService.embed("some text");

        assertThat(result).hasSize(3);
        assertThat(result[0]).isCloseTo(0.1f, org.assertj.core.data.Offset.offset(0.001f));
        assertThat(result[1]).isCloseTo(0.2f, org.assertj.core.data.Offset.offset(0.001f));
    }

    @Test
    void embed_apiError_throwsIOException() {
        server.enqueue(new MockResponse().setResponseCode(503).setBody("Service Unavailable"));

        assertThatThrownBy(() -> geminiService.embed("text"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("503");
    }

    // ---- batchEmbed() ----

    @Test
    void batchEmbed_twoTexts_returnsTwoVectors() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("""
                        {"embeddings":[
                          {"values":[0.1,0.2]},
                          {"values":[0.3,0.4]}
                        ]}
                        """)
                .addHeader("Content-Type", "application/json"));

        List<float[]> result = geminiService.batchEmbed(List.of("text1", "text2"));

        assertThat(result).hasSize(2);
        assertThat(result.get(0)[0]).isCloseTo(0.1f, org.assertj.core.data.Offset.offset(0.001f));
        assertThat(result.get(1)[0]).isCloseTo(0.3f, org.assertj.core.data.Offset.offset(0.001f));
    }
}
