package com.manager.service;

import com.manager.dto.RagSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LegalAssistantPromptTest {

    @Test
    void promptRequiresDocumentAndExactClauseForEveryRecommendation() {
        String prompt = LegalAssistantPrompt.buildGroundedPrompt(
                List.of(new RagSource(
                        "v1", "d1", "VMQ-123-son qarori", 7, 0.82,
                        "17-band. Belgilangan toifadagi shaxslarga dori bepul beriladi.",
                        "https://lex.uz/uz/docs/-123")),
                "Javobni o'zbek tilida yozing");

        assertThat(prompt)
                .contains("Siz Ijtimoiy himoya milliy agentligining Bosh AI yordamchisisiz")
                .contains("Har bir imtiyoz va tavsiya uchun Hujjat nomi, aniq bandi va Lex.uz havolasini ko‘rsating")
                .contains("Qisqa javob:")
                .contains("Batafsil:")
                .contains("aniq raqam, foiz, BHM ulushi, toifa, hudud, sana, muddat")
                .contains("javobni sunʼiy qisqartirmang")
                .contains("Hujjat: VMQ-123-son qarori")
                .contains("Lex.uz: https://lex.uz/uz/docs/-123")
                .contains("17-band. Belgilangan toifadagi shaxslarga dori bepul beriladi.")
                .contains(LegalAssistantPrompt.NO_NORMATIVE_BASIS);
    }

    @Test
    void chunkIndexIsNotExposedAsLegalClause() {
        String prompt = LegalAssistantPrompt.buildGroundedPrompt(
                List.of(new RagSource("v1", "d1", "Qaror", 42, 0.75, "Hujjat matni")),
                "");

        assertThat(prompt).doesNotContain("42-band", "Chunk: 42", "chunkIndex");
    }
}
