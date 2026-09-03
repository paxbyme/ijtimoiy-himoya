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
