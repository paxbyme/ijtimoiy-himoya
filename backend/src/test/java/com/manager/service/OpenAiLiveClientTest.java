package com.manager.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiLiveClientTest {

    @Test
    void independentlyCapsRealtimeSessionInstructionFarBelowProviderLimit() {
        String instruction = OpenAiLiveClient.limitSystemInstruction("x".repeat(1_100_000));

        assertThat(instruction)
                .hasSize(OpenAiLiveClient.MAX_SESSION_INSTRUCTION_CHARS)
                .endsWith("…");
    }

    @Test
    void microphoneBecomesReadyOnlyAfterSessionUpdateIsAccepted() {
        assertThat(OpenAiLiveClient.isReadyEvent("session.created")).isFalse();
        assertThat(OpenAiLiveClient.isReadyEvent("session.updated")).isTrue();
    }
}
