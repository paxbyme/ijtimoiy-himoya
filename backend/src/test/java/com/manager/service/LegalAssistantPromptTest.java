package com.manager.service;

import com.manager.dto.RagSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LegalAssistantPromptTest {

    private static final List<RagSource> SOURCES = List.of(new RagSource(
            "v1", "d1",
            "Dori ta'minoti to'g'risida. Vazirlar Mahkamasining 2025-yil 10-yanvardagi 123-son qarori",
            7, 0.82,
            "17-band. Belgilangan toifadagi shaxslarga dori bepul beriladi.",
            "https://lex.uz/uz/docs/-123#-17"));

    private static final String WELL_FORMED_ANSWER = """
            Qisqa javob: belgilangan toifadagi shaxslarga dori bepul beriladi.

            Holat va qoida tahlili:
            1. [Bepul dori olish huquqi]: qoida belgilangan toifaga tegishli. [Asos 1]

            Amaliy yechim:
            1. [Murojaat qiling]: toifani tasdiqlovchi hujjat bilan murojaat qiling. [Asos 1]

            Huquqiy asoslar:
            [Asos 1]
            - Hujjat: Dori ta'minoti to'g'risida. Vazirlar Mahkamasining 2025-yil 10-yanvardagi 123-son qarori
            - Norma joylashuvi: 17-band
            - Norma mazmuni: belgilangan toifadagi shaxslarga dori bepul beriladi.
            - Holatga qo'llanishi: foydalanuvchi shu toifaga kirsa, dori bepul beriladi.
            - Lex.uz: https://lex.uz/uz/docs/-123#-17""";

    @Test
    void promptRequiresDocumentAndExactClauseForEveryRecommendation() {
        String prompt = LegalAssistantPrompt.buildGroundedPrompt(
                List.of(new RagSource(
                        "v1", "d1", "Dori ta'minoti to'g'risida. Vazirlar Mahkamasining 2025-yil 10-yanvardagi 123-son qarori", 7, 0.82,
                        "17-band. Belgilangan toifadagi shaxslarga dori bepul beriladi.",
                        "https://lex.uz/uz/docs/-123#-17")),
                "Javobni o'zbek tilida yozing");

        assertThat(prompt)
                .contains("Siz Ijtimoiy himoya milliy agentligining Bosh AI yordamchisisiz")
                .contains("qaysi qaror yoki boshqa normativ hujjatga asoslanayotganingizni")
                .contains("Vazifangiz faqat qoidani ko‘chirish yoki qayta aytish emas")
                .contains("aynan shu qoidadan kelib chiqib holatga mos javob hamda amaliy yechim bering")
                .contains("Qisqa javob:")
                .contains("Holat va qoida tahlili:")
                .contains("Amaliy yechim:")
                .contains("mos keladigan, mos kelmaydigan va yetishmayotgan shartlarni")
                .contains("Qoidada ko‘rsatilmagan ariza tartibi, hujjatlar ro‘yxati")
                .contains("Har bir xulosa va amaliy qadamdan keyin unga tegishli [Asos N] belgisini yozing")
                .contains("Huquqiy asoslar:")
                .contains("- Hujjat:")
                .contains("- Norma joylashuvi:")
                .contains("- Norma mazmuni:")
                .contains("- Holatga qo‘llanishi:")
                .contains("Amaldagi tahrir manbasi")
                .contains("uni asosiy hujjat o‘rniga yozmang")
                .contains("PTPK (Psixologik-tibbiy-pedagogik komissiya)ni IPTK")
                .contains("2025-yil 27-fevraldagi 126-son qarorini")
                .contains("271-son qarorni 126-son qarorning o‘rniga")
                .contains("Aniqlashtirish uchun:")
                .contains("aniq raqam, foiz, BHM ulushi, toifa, hudud, sana, muddat")
                .contains("javobni sunʼiy qisqartirmang")
                .contains("Hujjat: Dori ta'minoti to'g'risida. Vazirlar Mahkamasining 2025-yil 10-yanvardagi 123-son qarori")
                .contains("Lex.uz: https://lex.uz/uz/docs/-123#-17")
                .contains("17-band. Belgilangan toifadagi shaxslarga dori bepul beriladi.")
                .contains("Ushbu holat boʻyicha Lex.uz bazasidan aniq amaldagi normativ hujjat topilmadi")
                .contains("MAJBURIY JAVOB FORMATI")
                .contains("Bu format buzilgan javob yaroqsiz hisoblanadi.");
    }

    @Test
    void noBasisAnswerKeepsTheMandatoryStructure() {
        String answer = LegalAssistantPrompt.noBasisAnswer();

        assertThat(answer).startsWith("Qisqa javob:");
        assertThat(answer)
                .contains("Holat va qoida tahlili:")
                .contains("Amaliy yechim:")
                .contains("Huquqiy asoslar:")
                .contains("Aniqlashtirish uchun:");
    }

    @Test
    void wellFormedAnswerPassesTheContract() {
        assertThat(LegalAssistantPrompt.isWellFormed(WELL_FORMED_ANSWER, SOURCES)).isTrue();
    }

    @Test
    void answerMissingASectionIsRejected() {
        String withoutSteps = WELL_FORMED_ANSWER.replace("Amaliy yechim:", "Tavsiyalar:");

        assertThat(LegalAssistantPrompt.isWellFormed(withoutSteps, SOURCES)).isFalse();
    }

    @Test
    void answerWithSectionsOutOfOrderIsRejected() {
        String reordered = """
                Qisqa javob: javob.

                Huquqiy asoslar:
                [Asos 1]
                - Hujjat: Qaror

                Holat va qoida tahlili:
                1. [Masala]: tahlil. [Asos 1]

                Amaliy yechim:
                1. [Qadam]: bajaring. [Asos 1]""";

        assertThat(LegalAssistantPrompt.isWellFormed(reordered, SOURCES)).isFalse();
    }

    @Test
    void answerWithoutEvidenceMarkersIsRejected() {
        String unmarked = WELL_FORMED_ANSWER.replace("[Asos 1]", "");

        assertThat(LegalAssistantPrompt.isWellFormed(unmarked, SOURCES)).isFalse();
    }

    @Test
    void inventedLexUzLinkIsRejected() {
        String invented = WELL_FORMED_ANSWER.replace(
                "https://lex.uz/uz/docs/-123#-17", "https://lex.uz/uz/docs/-999#-4");

        assertThat(LegalAssistantPrompt.isWellFormed(invented, SOURCES)).isFalse();
    }

    @Test
    void deeperAnchorOnAContextLinkIsAccepted() {
        String deeper = WELL_FORMED_ANSWER.replace(
                "https://lex.uz/uz/docs/-123#-17", "https://lex.uz/uz/docs/-123#-17-2");

        assertThat(LegalAssistantPrompt.isWellFormed(deeper, SOURCES)).isTrue();
    }

    @Test
    void repairPromptCarriesEvidenceAndTheDraft() {
        String prompt = LegalAssistantPrompt.buildRepairPrompt(SOURCES, "");

        assertThat(prompt)
                .contains("MAJBURIY JAVOB FORMATI")
                .contains("QAYTA FORMATLASH VAZIFASI")
                .contains("Hujjat: Dori ta'minoti to'g'risida.");
        assertThat(LegalAssistantPrompt.buildRepairRequest("savol", "qoralama"))
                .contains("Foydalanuvchi savoli:")
                .contains("savol")
                .contains("Qoralama javob:")
                .contains("qoralama");
    }

    @Test
    void chunkIndexIsNotExposedAsLegalClause() {
        String prompt = LegalAssistantPrompt.buildGroundedPrompt(
                List.of(new RagSource("v1", "d1", "Qaror", 42, 0.75, "Hujjat matni")),
                "");

        assertThat(prompt).doesNotContain("42-band", "Chunk: 42", "chunkIndex");
    }
}
