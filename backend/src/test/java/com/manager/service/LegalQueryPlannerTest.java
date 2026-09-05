package com.manager.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LegalQueryPlannerTest {

    private static Map<String, Object> message(String role, String text) {
        return Map.of("role", role, "parts", List.of(Map.of("text", text)));
    }

    @Test
    void followUpIsPlannedWithTheTopicOfThePreviousTurns() throws Exception {
        AiService aiService = mock(AiService.class);
        when(aiService.chat(anyString(), any(), anyString()))
                .thenReturn("[\"ijtimoiy xodim maqomi\"]");
        LegalQueryPlanner planner = new LegalQueryPlanner(aiService);

        List<String> queries = planner.plan("ha, menda malaka bor", List.of(
                message("user", "Ijtimoiy xodim bo'lish uchun nima kerak?"),
                message("model", "Qisqa javob: ijtimoiy xodim maqomi qonun bilan belgilangan.")));

        ArgumentCaptor<String> request = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(aiService).chat(anyString(), any(), request.capture());
        assertThat(request.getValue())
                .contains("Ijtimoiy xodim bo'lish uchun nima kerak?")
                .contains("ha, menda malaka bor");
        assertThat(queries).containsExactly("ijtimoiy xodim maqomi");
    }

    @Test
    void firstTurnIsPlannedFromTheQuestionAlone() throws Exception {
        AiService aiService = mock(AiService.class);
        when(aiService.chat(anyString(), any(), anyString()))
                .thenReturn("[\"ishsizlik nafaqasi tayinlash\"]");
        LegalQueryPlanner planner = new LegalQueryPlanner(aiService);

        List<String> queries = planner.plan("Ishsizlik nafaqasi qanday tayinlanadi?", List.of());

        ArgumentCaptor<String> request = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(aiService).chat(anyString(), any(), request.capture());
        assertThat(request.getValue()).isEqualTo("Ishsizlik nafaqasi qanday tayinlanadi?");
        assertThat(queries).containsExactly("ishsizlik nafaqasi tayinlash");
    }

    @Test
    void plannerFailureFallsBackToKeywordSearch() throws Exception {
        AiService aiService = mock(AiService.class);
        when(aiService.chat(anyString(), any(), anyString()))
                .thenThrow(new java.io.IOException("rate limited"));
        LegalQueryPlanner planner = new LegalQueryPlanner(aiService);

        assertThat(planner.plan("savol", List.of())).isEmpty();
    }
}
