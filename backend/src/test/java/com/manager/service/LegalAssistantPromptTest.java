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
            Belgilangan toifadagi shaxslarga dori bepul beriladi [1].

            Kimlarga beriladi:
            - 17-bandda belgilangan toifadagi shaxslarga [1].

            Qanday olinadi:
            1. Toifani tasdiqlovchi hujjat bilan murojaat qiling [1].

            Manba:
            [1] Dori ta'minoti to'g'risida. Vazirlar Mahkamasining 2025-yil 10-yanvardagi 123-son qarori, 17-band. Lex.uz: https://lex.uz/uz/docs/-123#-17

            Kim uchun so'rayapsiz? Shunga qarab aniqroq yo'l ko'rsataman.""";

    @Test
    void promptAsksForADeepFullyGroundedAnswer() {
        String prompt = LegalAssistantPrompt.buildGroundedPrompt(
                List.of(new RagSource(
                        "v1", "d1", "Dori ta'minoti to'g'risida. Vazirlar Mahkamasining 2025-yil 10-yanvardagi 123-son qarori", 7, 0.82,
                        "17-band. Belgilangan toifadagi shaxslarga dori bepul beriladi.",
                        "https://lex.uz/uz/docs/-123#-17")),
                "Javobni o'zbek tilida yozing");

        assertThat(prompt)
                .contains("Siz Ijtimoiy himoya milliy agentligining Bosh AI yordamchisisiz")
                .contains("chuqur, to‘liq va amaliy javob")
                .contains("KONTEKST SAVOLGA ALOQADORMI")
                .contains("kontekst esa davlat xaridlari haqida")
                .contains("JAVOB USLUBI")
                .contains("\"Qisqa javob\" kabi yorliq yozmang")
                .contains("tashxislar (kodlari bilan)")
                .contains("istisnolar, qarshi ko‘rsatmalar va rad etish asoslari")
                .contains("javobni sunʼiy qisqartirmang")
                .contains("\"va boshqalar\" deb qisqartirmang")
                .contains("Javob oxirida \"Manbalar:\"")
                .contains("**, #, jadval va kod bloklarini ishlatmang")
                .contains("AMALIY IZOH")
                .contains("18 yoshgacha bo‘lganlarga esa \"nogironligi bo‘lgan bola\" maqomi beriladi")
                .contains("kontekst yo‘q yoki savolga aloqasiz bo‘lsa, amaliy izoh yozmang")
                .contains("unga Lex.uz manbasini biriktirmang")
                .contains("summa, foiz, BHM ulushi, muddat, hujjat nomi yoki raqami, band")
                .contains("mos keladigan, mos kelmaydigan va aniqlashtirilishi kerak bo‘lgan shartlarni")
                .contains("Qoidada ko‘rsatilmagan ariza tartibi, hujjatlar ro‘yxati")
                .contains("Amaldagi tahrir manbasi")
                .contains("uni asosiy hujjat o‘rniga yozmang")
                .contains("PTPK (Psixologik-tibbiy-pedagogik komissiya)ni IPTK")
                .contains("Kontekstda PTPK ko‘rsatilmagan bo‘lsa, uni yozmang")
                .contains("2025-yil 27-fevraldagi 126-son qarorini")
                .contains("271-son qarorni 126-son qarorning o‘rniga")
                .contains("Normada yosh chegarasi belgilanmagan")
                .contains("SUHBAT TARIXI")
                .contains("birinchi savolim nima edi?")
                .contains("aniq raqam, foiz, BHM ulushi, toifa, hudud, sana, muddat")
                .contains("NAMUNA TUZILMA")
                .contains("Hujjat: Dori ta'minoti to'g'risida. Vazirlar Mahkamasining 2025-yil 10-yanvardagi 123-son qarori")
                .contains("Lex.uz: https://lex.uz/uz/docs/-123#-17")
                .contains("17-band. Belgilangan toifadagi shaxslarga dori bepul beriladi.")
                .contains("Ushbu holat boʻyicha Lex.uz bazasidan aniq amaldagi normativ hujjat topilmadi")
                .doesNotContain("Qisqa javob:", "Holat va qoida tahlili:", "Huquqiy asoslar:", "[Asos N]",
                        "X xizmati uchun");
    }

    @Test
    void noBasisAnswerPassesTheContract() {
        String answer = LegalAssistantPrompt.noBasisAnswer();

        assertThat(answer)
                .contains("normativ hujjat topilmadi")
                .contains("Qaysi masala va kim uchun soʻrayapsiz?")
                .doesNotContain("Qisqa javob", "Huquqiy asoslar:", "**");
        assertThat(LegalAssistantPrompt.isWellFormed(answer, SOURCES)).isTrue();
    }

    @Test
    void wellFormedAnswerPassesTheContract() {
        assertThat(LegalAssistantPrompt.isWellFormed(WELL_FORMED_ANSWER, SOURCES)).isTrue();
    }

    @Test
    void severalSourcesUnderManbalarPass() {
        String answer = WELL_FORMED_ANSWER.replace("Manba:", "Manbalar:");

        assertThat(LegalAssistantPrompt.isWellFormed(answer, SOURCES)).isTrue();
    }

    @Test
    void answerWithoutSourcesIsRejected() {
        String unsourced = """
                Belgilangan toifadagi shaxslarga dori bepul beriladi.

                Qanday olinadi:
                1. Toifani tasdiqlovchi hujjat bilan murojaat qiling.""";

        assertThat(LegalAssistantPrompt.isWellFormed(unsourced, SOURCES)).isFalse();
    }

    @Test
    void sourcesWithoutAnAnswerAreRejected() {
        String onlySources = """
                Manba:
                [1] Dori ta'minoti to'g'risida, 17-band. Lex.uz: https://lex.uz/uz/docs/-123#-17""";

        assertThat(LegalAssistantPrompt.isWellFormed(onlySources, SOURCES)).isFalse();
    }

    @Test
    void fullAnswerOverSourcesThatAdmitNothingWasFoundIsRejected() {
        String ungrounded = """
                Normada yosh chegarasi belgilanmagan.

                Qanday murojaat qilinadi:
                1. Komissiyaga murojaat qilib, xulosa oling.

                Manba: Ushbu holat boʻyicha Lex.uz bazasidan aniq amaldagi normativ hujjat topilmadi.""";

        assertThat(LegalAssistantPrompt.isWellFormed(ungrounded, SOURCES)).isFalse();
    }

    @Test
    void noBasisReplyThatStillCitesALinkNeedsSources() {
        String mixed = """
                Ushbu savol boʻyicha aniq amaldagi normativ hujjat topilmadi.
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
                .contains("Avval kontekst savolga aloqadorligini tekshiring")
                .contains("\"Manbalar:\"")
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

    @Test
    void earlierQuestionsAreListedFromTheFirstOne() {
        String prompt = LegalAssistantPrompt.buildGroundedPrompt(SOURCES, "",
                List.of("Yangi kun xizmatiga kimlar qabul qilinadi?", "Hujjatlar qanday?"));

        assertThat(prompt)
                .contains("SUHBATDAGI OLDINGI SAVOLLAR (birinchisidan")
                .contains("1. Yangi kun xizmatiga kimlar qabul qilinadi?")
                .contains("2. Hujjatlar qanday?")
                .contains("hech qachon oldingi savolni eslay olmayman demang");
    }

    @Test
    void firstTurnHasNoQuestionList() {
        assertThat(LegalAssistantPrompt.buildGroundedPrompt(SOURCES, "", List.of()))
                .doesNotContain("SUHBATDAGI OLDINGI SAVOLLAR (birinchisidan");
    }
}
