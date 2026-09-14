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

            Kimlarga beriladi:
            - 17-bandda belgilangan toifadagi shaxslarga.

            Qanday olinadi:
            1. Toifani tasdiqlovchi hujjat bilan murojaat qiling.

            Manba: Dori ta'minoti to'g'risida. Vazirlar Mahkamasining 2025-yil 10-yanvardagi 123-son qarori, 17-band. Lex.uz: https://lex.uz/uz/docs/-123#-17

            Kim uchun so'rayapsiz? Shunga qarab aniqroq yo'l ko'rsataman.""";

    @Test
    void promptAsksForADirectAnswerPracticalMeaningAndSources() {
        String prompt = LegalAssistantPrompt.buildGroundedPrompt(
                List.of(new RagSource(
                        "v1", "d1", "Dori ta'minoti to'g'risida. Vazirlar Mahkamasining 2025-yil 10-yanvardagi 123-son qarori", 7, 0.82,
                        "17-band. Belgilangan toifadagi shaxslarga dori bepul beriladi.",
                        "https://lex.uz/uz/docs/-123#-17")),
                "Javobni o'zbek tilida yozing");

        assertThat(prompt)
                .contains("Siz Ijtimoiy himoya milliy agentligining Bosh AI yordamchisisiz")
                .contains("JAVOB USLUBI")
                .contains("Javobni doimo \"Qisqa javob:\" bilan boshlang")
                .contains("Har bir javobga bir xil bo‘limlar shablonini qo‘ymang")
                .contains("Javob oxirida \"Manba:\" bo‘limi SHART")
                .contains("**, #, jadval va kod bloklarini ishlatmang")
                .contains("Kim uchun so‘rayapsiz (yoshi, nogironlik guruhi, tashxisi)?")
                .contains("Normani quruq ko‘chirmang")
                .contains("AMALIY IZOH")
                .contains("18 yoshgacha bo‘lganlarga esa \"nogironligi bo‘lgan bola\" maqomi beriladi")
                .contains("unga Lex.uz manbasini biriktirmang")
                .contains("summa, foiz, BHM ulushi, muddat, hujjat nomi yoki raqami, band")
                .contains("mos keladigan, mos kelmaydigan va aniqlashtirilishi kerak bo‘lgan shartlarni")
                .contains("Qoidada ko‘rsatilmagan ariza tartibi, hujjatlar ro‘yxati")
                .contains("Amaldagi tahrir manbasi")
                .contains("uni asosiy hujjat o‘rniga yozmang")
                .contains("PTPK (Psixologik-tibbiy-pedagogik komissiya)ni IPTK")
                .contains("2025-yil 27-fevraldagi 126-son qarorini")
                .contains("271-son qarorni 126-son qarorning o‘rniga")
                .contains("Normada yosh chegarasi belgilanmagan")
                .contains("SUHBAT TARIXI")
                .contains("birinchi savolim nima edi?")
                .contains("aniq raqam, foiz, BHM ulushi, toifa, hudud, sana, muddat")
                .contains("javobni sunʼiy qisqartirmang")
                .contains("NAMUNA")
                .contains("Hujjat: Dori ta'minoti to'g'risida. Vazirlar Mahkamasining 2025-yil 10-yanvardagi 123-son qarori")
                .contains("Lex.uz: https://lex.uz/uz/docs/-123#-17")
                .contains("17-band. Belgilangan toifadagi shaxslarga dori bepul beriladi.")
                .contains("Ushbu holat boʻyicha Lex.uz bazasidan aniq amaldagi normativ hujjat topilmadi")
                .doesNotContain("Holat va qoida tahlili:", "Huquqiy asoslar:", "[Asos N]");
    }

    @Test
    void noBasisAnswerIsShortAndPassesTheContract() {
        String answer = LegalAssistantPrompt.noBasisAnswer();

        assertThat(answer)
                .startsWith("Qisqa javob:")
                .contains("normativ hujjat topilmadi")
                .contains("Qaysi masala va kim uchun soʻrayapsiz?")
                .doesNotContain("Huquqiy asoslar:", "**");
        assertThat(LegalAssistantPrompt.isWellFormed(answer, SOURCES)).isTrue();
    }

    @Test
    void wellFormedAnswerPassesTheContract() {
        assertThat(LegalAssistantPrompt.isWellFormed(WELL_FORMED_ANSWER, SOURCES)).isTrue();
    }

    @Test
    void severalSourcesUnderManbalarPass() {
        String answer = WELL_FORMED_ANSWER.replace("Manba: ", "Manbalar:\n[1] ");

        assertThat(LegalAssistantPrompt.isWellFormed(answer, SOURCES)).isTrue();
    }

    @Test
    void answerWithoutShortAnswerIsRejected() {
        String noDirectAnswer = WELL_FORMED_ANSWER.replace("Qisqa javob:", "Javob:");

        assertThat(LegalAssistantPrompt.isWellFormed(noDirectAnswer, SOURCES)).isFalse();
    }

    @Test
    void answerWithoutSourcesIsRejected() {
        String unsourced = """
                Qisqa javob: belgilangan toifadagi shaxslarga dori bepul beriladi.

                Qanday olinadi:
                1. Toifani tasdiqlovchi hujjat bilan murojaat qiling.""";

        assertThat(LegalAssistantPrompt.isWellFormed(unsourced, SOURCES)).isFalse();
    }

    @Test
    void sourcesBeforeTheDirectAnswerAreRejected() {
        String reordered = """
                Manba: Dori ta'minoti to'g'risida, 17-band.

                Qisqa javob: dori bepul beriladi.""";

        assertThat(LegalAssistantPrompt.isWellFormed(reordered, SOURCES)).isFalse();
    }

    @Test
    void noBasisReplyThatStillCitesALinkNeedsSources() {
        String mixed = """
                Qisqa javob: Ushbu savol boʻyicha aniq amaldagi normativ hujjat topilmadi.
                Lex.uz: https://lex.uz/uz/docs/-123#-17""";

        assertThat(LegalAssistantPrompt.isWellFormed(mixed, SOURCES)).isFalse();
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
                .contains("JAVOB USLUBI")
                .contains("QAYTA FORMATLASH VAZIFASI")
                .contains("\"Manba:\"")
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
