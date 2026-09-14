package com.manager.repository;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiConversationRepositoryTest {

    private static Map<String, Object> message(String role, String text) {
        return Map.of("role", role, "parts", List.of(Map.of("text", text)));
    }

    @Test
    void questionListKeepsEveryUserQuestionInOrder() {
        List<Map<String, Object>> log = List.of(
                message("user", "Birinchi savol"),
                message("model", "Birinchi javob"),
                message("user", "  Ikkinchi savol  "),
                message("model", "Ikkinchi javob"),
                message("user", ""));

        assertThat(AiConversationRepository.userQuestionsOf(log))
                .containsExactly("Birinchi savol", "Ikkinchi savol");
    }

    @Test
    void malformedEntriesAreSkipped() {
        List<Object> log = List.of("not a message", Map.of("role", "user"), message("user", "Savol"));

        assertThat(AiConversationRepository.userQuestionsOf(log)).containsExactly("Savol");
    }
}
