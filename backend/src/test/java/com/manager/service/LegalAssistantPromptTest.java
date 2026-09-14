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

    private static final String GENERAL_ANALYSIS_ANSWER = """
            Kunduzgi parvarish — oila ishlayotgan vaqtda bolani kun davomida parvarish qiladigan ijtimoiy xizmat.

            %s

            Bu masala qaysi yo'nalishga tegishli:
            - ijtimoiy xizmatlar; odatda bolaning yoshi va sog'lig'i holati hal qiluvchi bo'ladi.

            Nima qilish mumkin:
            1. Yashash joyidagi "Inson" ijtimoiy xizmatlar markaziga murojaat qiling.

            Farzandingizning yoshi va tashxisi qanday?""".formatted(LegalAssistantPrompt.GENERAL_ANALYSIS_NOTICE);

    @Test
    void promptAnalysesEveryMessageAndGroundsLegalFacts() {
        String prompt = LegalAssistantPrompt.buildGroundedPrompt(
                List.of(new RagSource(
                        "v1", "d1", "Dori ta'minoti to'g'risida. Vazirlar Mahkamasining 2025-yil 10-yanvardagi 123-son qarori", 7, 0.82,
                        "17-band. Belgilangan toifadagi shaxslarga dori bepul beriladi.",
                        "https://lex.uz/uz/docs/-123#-17")),
                "Javobni o'zbek tilida yozing");

        assertThat(prompt)
                .contains("Siz Ijtimoiy himoya milliy agentligining Bosh AI yordamchisisiz")
                .contains("har qanday xabarni — savol, bitta so‘z yoki xizmat nomi, holat bayoni, shikoyat")
                .contains("XABARNI TAHLIL QILISH")
                .contains("Faqat mavzu yoki xizmat nomi yozilgan bo‘lsa")
                .contains("Hech qachon faqat \"savolni aniqroq bering\" deb javob bermang")
                .contains("KONTEKSTDAN FOYDALANISH")
                .contains("kontekst esa davlat xaridlari haqida")
                .contains("lekin javob berishdan bosh tortmang")
                .contains("UMUMIY TAHLIL")
                .contains(LegalAssistantPrompt.GENERAL_ANALYSIS_NOTICE)
                .contains("summa, foiz, BHM ulushi, muddat, hujjat nomi yoki raqami, band raqami va Lex.uz havolasini hech qachon umumiy bilimdan yozmang")
                .contains("JAVOB USLUBI")
                .contains("\"Qisqa javob\" kabi yorliq yozmang")
                .contains("tashxislar (kodlari bilan)")
                .contains("istisnolar, qarshi ko‘rsatmalar va rad etish asoslari")
                .contains("javobni sunʼiy qisqartirmang")
                .contains("\"va boshqalar\" deb qisqartirmang")
                .contains("javob oxirida \"Manbalar:\"")
                .contains("**, #, jadval va kod bloklarini ishlatmang")
                .contains("AMALIY IZOH")
                .contains("18 yoshgacha bo‘lganlarga esa \"nogironligi bo‘lgan bola\" maqomi beriladi")
                .contains("unga Lex.uz manbasini biriktirmang")
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
                .doesNotContain("Qisqa javob:", "Holat va qoida tahlili:", "Huquqiy asoslar:", "[Asos N]",
                        "X xizmati uchun", "%s", "faqat fallback jumlasi");
    }

    @Test
    void emptyContextStillAsksForAnAnalysis() {
        assertThat(LegalAssistantPrompt.buildGroundedPrompt(List.of(), ""))
                .contains("[Kontekst topilmadi — xabarni UMUMIY TAHLIL qoidalari bo‘yicha, eslatma bilan tahlil qiling]");
    }

    @Test
    void wellFormedAnswerPassesTheContract() {
        assertThat(LegalAssistantPrompt.isWellFormed(WELL_FORMED_ANSWER, SOURCES)).isTrue();
    }

    @Test
    void labelledGeneralAnalysisWithoutSourcesPasses() {
        assertThat(LegalAssistantPrompt.isWellFormed(GENERAL_ANALYSIS_ANSWER, SOURCES)).isTrue();
        assertThat(LegalAssistantPrompt.isWellFormed(GENERAL_ANALYSIS_ANSWER, List.of())).isTrue();
    }

    @Test
    void labelledGeneralAnalysisOverSourcesThatAdmitNothingWasFoundPasses() {
        String answer = GENERAL_ANALYSIS_ANSWER
                + "\n\nManba: Ushbu holat boʻyicha Lex.uz bazasidan aniq amaldagi normativ hujjat topilmadi.";

        assertThat(LegalAssistantPrompt.isWellFormed(answer, SOURCES)).isTrue();
    }

    @Test
    void severalSourcesUnderManbalarPass() {
        String answer = WELL_FORMED_ANSWER.replace("Manba:", "Manbalar:");

        assertThat(LegalAssistantPrompt.isWellFormed(answer, SOURCES)).isTrue();
    }

    @Test
    void unlabelledAnswerWithoutSourcesIsRejected() {
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
    void unlabelledAnswerOverSourcesThatAdmitNothingWasFoundIsRejected() {
        String ungrounded = """
                Normada yosh chegarasi belgilanmagan.

                Qanday murojaat qilinadi:
                1. Komissiyaga murojaat qilib, xulosa oling.

                Manba: Ushbu holat boʻyicha Lex.uz bazasidan aniq amaldagi normativ hujjat topilmadi.""";

        assertThat(LegalAssistantPrompt.isWellFormed(ungrounded, SOURCES)).isFalse();
    }

    @Test
    void generalAnalysisThatStillCitesALinkNeedsSources() {
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
    void repairPromptKeepsTheAnalysisInsteadOfFallingBack() {
        String prompt = LegalAssistantPrompt.buildRepairPrompt(SOURCES, "");

        assertThat(prompt)
                .contains("JAVOB USLUBI")
                .contains("QAYTA FORMATLASH VAZIFASI")
                .contains("Avval kontekst savolga aloqadorligini tekshiring")
                .contains("xabarni UMUMIY TAHLIL qoidalari bo‘yicha, eslatma bilan to‘liq tahlil qiling")
                .contains("kontekstda yo‘q qismni o‘chirib tashlamang")
                .contains("\"Manbalar:\"")
                .contains("Hujjat: Dori ta'minoti to'g'risida.");
        assertThat(LegalAssistantPrompt.buildRepairRequest("savol", "qoralama"))
                .contains("Foydalanuvchi xabari:")
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
