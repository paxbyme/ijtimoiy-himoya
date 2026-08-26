package com.manager.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manager.config.OpenAiConfig;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAiServiceTest {

    private MockWebServer server;
    private OpenAiService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();

        OpenAiConfig config = mock(OpenAiConfig.class);
        when(config.getBaseUrl()).thenReturn(server.url("").toString().replaceAll("/$", ""));
        when(config.getApiKey()).thenReturn("test-key");
        when(config.getModel()).thenReturn("gpt-test");
        service = new OpenAiService(config);
        objectMapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void standardChatAllowsDetailedLegalAnswer() throws Exception {
        server.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody("{\"choices\":[{\"message\":{\"content\":\"answer\"}}]}"));

        assertThat(service.chat("system", List.of(), "question")).isEqualTo("answer");

        JsonNode request = requestBody(server.takeRequest());
        assertThat(request.path("max_tokens").asInt()).isEqualTo(2400);
        assertThat(request.has("stream")).isFalse();
    }

    @Test
    void streamingChatUsesSameDetailedAnswerLimitAndEmitsTokens() throws Exception {
        server.enqueue(new MockResponse()
                .addHeader("Content-Type", "text/event-stream")
                .setBody("""
                        data: {"choices":[{"delta":{"content":"hel"}}]}

                        data: {"choices":[{"delta":{"content":"lo"}}]}

                        data: [DONE]

                        """));

        List<String> tokens = new ArrayList<>();
        assertThat(service.chatStream("system", List.of(), "question", tokens::add))
                .isEqualTo("hello");
        assertThat(tokens).containsExactly("hel", "lo");

        JsonNode request = requestBody(server.takeRequest());
        assertThat(request.path("max_tokens").asInt()).isEqualTo(2400);
        assertThat(request.path("stream").asBoolean()).isTrue();
    }

    private JsonNode requestBody(RecordedRequest request) throws Exception {
        return objectMapper.readTree(request.getBody().readUtf8());
    }
}
