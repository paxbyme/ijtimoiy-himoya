package com.manager.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationContextTest {

    private static Map<String, Object> message(String role, String text) {
        return Map.of("role", role, "parts", List.of(Map.of("text", text)));
    }

    private static final List<Map<String, Object>> HISTORY = List.of(
            message("user", "Ijtimoiy xodim bo'lish uchun qanday talablar bor?"),
            message("model", "Qisqa javob: ijtimoiy xodim maqomi qonun bilan belgilangan. [Asos 1]"));

    @Test
    void shortReplyToClarifyingQuestionIsTreatedAsFollowUp() {
        assertThat(ConversationContext.isFollowUp("ha, menda malaka bor")).isTrue();
        assertThat(ConversationContext.isFollowUp("ha")).isTrue();
        assertThat(ConversationContext.isFollowUp("batafsil tushuntiring")).isTrue();
    }

    @Test
    void questionCarryingItsOwnSubjectIsNotAFollowUp() {
        assertThat(ConversationContext.isFollowUp(
                "Nogironligi bo'lgan bolaga kunduzgi parvarish xizmati qanday tartibda beriladi?"))
                .isFalse();
    }

    @Test
    void followUpIsSearchedTogetherWithTheQuestionItAnswers() {
        String retrieval = ConversationContext.retrievalQuestion("ha, menda malaka bor", HISTORY);

        assertThat(retrieval)
                .contains("Ijtimoiy xodim bo'lish uchun qanday talablar bor?")
                .contains("ha, menda malaka bor");
    }

    @Test
    void selfContainedQuestionIsSearchedAsWritten() {
        String question = "Ishsizlik nafaqasi qanday tartibda tayinlanadi va qancha muddatga beriladi?";

        assertThat(ConversationContext.retrievalQuestion(question, HISTORY)).isEqualTo(question);
    }

    @Test
    void followUpOnAnEmptyHistoryIsSearchedAsWritten() {
        assertThat(ConversationContext.retrievalQuestion("ha", List.of())).isEqualTo("ha");
    }

    @Test
    void lastUserQuestionSkipsAssistantTurns() {
        assertThat(ConversationContext.lastUserQuestion(HISTORY))
                .isEqualTo("Ijtimoiy xodim bo'lish uchun qanday talablar bor?");
        assertThat(ConversationContext.lastUserQuestion(List.of())).isEmpty();
    }

    @Test
    void transcriptLabelsBothSpeakers() {
        String transcript = ConversationContext.transcript(HISTORY);

        assertThat(transcript)
                .contains("Fuqaro: Ijtimoiy xodim bo'lish uchun qanday talablar bor?")
                .contains("Yordamchi: Qisqa javob:");
    }

    @Test
    void assistantTurnIsDetected() {
        assertThat(ConversationContext.hasAssistantTurn(HISTORY)).isTrue();
        assertThat(ConversationContext.hasAssistantTurn(
                List.of(message("user", "salom")))).isFalse();
    }
}
