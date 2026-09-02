package com.manager.service;

import com.manager.dto.RagSource;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LexUzServiceTest {

    private MockWebServer server;
    private LexUzService service;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        service = new LexUzService(
                true,
                server.url("/").toString(),
                2,
                6,
                3600,
                Runnable::run);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void returnsOnlyHighlightedOfficialProvisionWithLexUzLink() throws Exception {
        server.enqueue(htmlResponse("""
                <html><body><table>
                  <tr class="dd-table__main-item">
                    <td><span class="lx_act_state"><i class="status_code_y"></i></span></td>
                    <td>
                      <div class="dd-table__main-left-desc">
                        <a class="lx_link" href="/uz/docs/-123?query=nogironligi#sr-1">Nafaqa tayinlash tartibi</a>
                      </div>
                      <span class="badge-nine">Vazirlar Mahkamasining 123-son qarori</span>
                    </td>
                  </tr>
                </table></body></html>
                """));
        server.enqueue(htmlResponse("""
                <html><body><div id="divCont">
                  <div class="ACT_TITLE lx_elem">
                    <div class="lx_elem2">Sahifa boshqaruvlari</div>
                    <div name="-10" id="-10">Nafaqa tayinlash tartibi to'g'risida</div>
                  </div>
                  <div class="ACT_TEXT lx_elem">
                    <div class="lx_elem2">Hujjatga taklif yuborish</div>
                    <div name="-11" id="-11">17-band. <span class="show_context" id="sr-1">Nogironligi</span> bo'lgan farzandi uchun nafaqa tayinlanadi.</div>
                  </div>
                  <div class="ACT_TEXT lx_elem">
                    <div class="lx_elem2">Hujjatga taklif yuborish</div>
                    <div name="-12" id="-12">Ariza yashash joyidagi vakolatli organga beriladi.</div>
                  </div>
                </div></body></html>
                """));

        List<RagSource> result = service.query(
                "Nogiron farzandim uchun nafaqa qanday olinadi?", 8);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).documentId()).isEqualTo("-123");
        assertThat(result.get(0).documentTitle())
                .contains("Nafaqa tayinlash tartibi to'g'risida")
                .contains("123-son qarori");
        assertThat(result.get(0).content())
                .contains("17-band")
                .contains("Ariza yashash joyidagi vakolatli organga beriladi")
                .doesNotContain("Hujjatga taklif yuborish");
        assertThat(result.get(0).sourceUrl()).isEqualTo(server.url("/uz/docs/-123#-11").toString());

        RecordedRequest search = server.takeRequest();
        assertThat(search.getRequestUrl().encodedPath()).isEqualTo("/uz/search/nat");
        assertThat(search.getRequestUrl().queryParameter("status")).isEqualTo("Y");
        assertThat(search.getRequestUrl().queryParameter("nature")).isEqualTo("1");
        assertThat(search.getRequestUrl().queryParameter("query"))
                .isEqualTo("nogironligi farzandi nafaqa");

        RecordedRequest document = server.takeRequest();
        assertThat(document.getRequestUrl().encodedPath()).isEqualTo("/uz/docs/-123");
        assertThat(document.getRequestUrl().queryParameter("query"))
                .isEqualTo("nogironligi farzandi nafaqa");
    }

    @Test
    void noOfficialMatchReturnsNoEvidenceAndDoesNotInventFallbackContent() {
        server.enqueue(htmlResponse("<html><body><div>Hujjat topilmadi</div></body></html>"));
        server.enqueue(htmlResponse("<html><body><div>Hujjat topilmadi</div></body></html>"));
        server.enqueue(htmlResponse("<html><body><div>Hujjat topilmadi</div></body></html>"));

        List<RagSource> result = service.query("noma'lum masala", 8);

        assertThat(result).isEmpty();
    }

    @Test
    void detailedQuestionPrioritizesAndExpandsFactRichLegalSection() throws Exception {
        server.enqueue(htmlResponse("""
                <html><body><table>
                  <tr class="dd-table__main-item">
                    <td><span class="lx_act_state"><i class="status_code_y"></i></span></td>
                    <td>
                      <div class="dd-table__main-left-desc">
                        <a class="lx_link" href="/uz/docs/-7410361">Kunduzgi parvarish xizmatini tashkil etish</a>
                      </div>
                      <span class="badge-nine">Vazirlar Mahkamasining 126-son qarori</span>
                    </td>
                  </tr>
                </table></body></html>
                """));
        server.enqueue(htmlResponse("""
                <html><body><div id="divCont">
                  <div class="ACT_TITLE lx_elem"><div name="-1" id="-1">Nogironligi bo'lgan bolalar uchun kunduzgi parvarish xizmatini tashkil etish chora-tadbirlari to'g'risida</div></div>
                  <div class="ACT_TEXT lx_elem"><div name="-2" id="-2"><span class="show_context">Kunduzgi parvarish</span> xizmati tashkil etiladi.</div></div>
                  <div class="ACT_TEXT lx_elem"><div name="-3" id="-3">Umumiy tashkiliy qoida.</div></div>
                  <div class="ACT_TEXT lx_elem"><div name="-4" id="-4">Hamkorlik masalasi.</div></div>
                  <div class="ACT_TEXT lx_elem"><div name="-5" id="-5">Ro'yxatni shakllantirish tartibi.</div></div>
                  <div class="ACT_TEXT lx_elem"><div name="-6" id="-6">Axborot tizimidan foydalanish qoidasi.</div></div>
                  <div class="ACT_TEXT lx_elem"><div name="-7" id="-7">Xizmat ko'rsatish bo'yicha umumiy talab.</div></div>
                  <div class="ACT_TEXT lx_elem"><div name="-8" id="-8">Hisobot yuritish qoidasi.</div></div>
                  <div class="ACT_TEXT lx_elem"><div name="-9" id="-9">5-band. Xususiy sherikka kunduzgi parvarish xizmatini ko'rsatgan har bir kun uchun subsidiya ajratiladi:</div></div>
                  <div class="ACT_TEXT lx_elem"><div name="-10" id="-10">3 yoshdan 7 yoshgacha bo'lgan har bir bola uchun BHMning 25 foizi.</div></div>
                  <div class="ACT_TEXT lx_elem"><div name="-11" id="-11">7 yoshdan 18 yoshgacha bo'lgan har bir bola uchun BHMning 27 foizi.</div></div>
                  <div class="ACT_TEXT lx_elem"><div name="-12" id="-12">6-band. Toshkent va Nukus shaharlarida kunduzgi parvarish subsidiyasiga 1,1 ko'paytiruvchi koeffitsiyent qo'llanadi.</div></div>
                  <div class="ACT_TEXT lx_elem"><div name="-13" id="-13">8-band. Bola kunduzgi parvarish xizmatidan uch kilometrdan uzoqda yashasa transport tashkil etilishi mumkin.</div></div>
                  <div class="ACT_TEXT lx_elem"><div name="-14" id="-14">Transport uchun oyiga BHMning 0,75 baravarigacha kompensatsiya to'lanadi.</div></div>
                </div></body></html>
                """));

        List<RagSource> result = service.query(
                "kunduzgi parvarish haqida batafsil ma'lumot ber, qancha subsidiya ajratilgan va qaysi yo'nalishlarga",
                8);

        assertThat(result).isNotEmpty();
        String allContent = result.stream()
                .map(RagSource::content)
                .reduce("", (left, right) -> left + "\n" + right);
        assertThat(allContent)
                .contains("BHMning 25 foizi")
                .contains("BHMning 27 foizi")
                .contains("1,1 ko'paytiruvchi koeffitsiyent")
                .contains("BHMning 0,75 baravarigacha kompensatsiya");

        RecordedRequest search = server.takeRequest();
        assertThat(search.getRequestUrl().queryParameter("query"))
                .isEqualTo("kunduzgi parvarish subsidiya ajratilgan");
    }

    @Test
    void complexFamilyCaseSearchesLegalNeedsInsteadOfNamesAndAddress() throws Exception {
        server.enqueue(searchResult("-101", "Nogironligi bo'lgan bolaga nafaqa",
                "Vazirlar Mahkamasining 101-son qarori"));
        server.enqueue(searchResult("-202", "Kompleks ijtimoiy xizmatlar",
                "Vazirlar Mahkamasining 202-son qarori"));
        server.enqueue(searchResult("-303", "Ishsiz shaxslar bandligi",
                "Vazirlar Mahkamasining 303-son qarori"));

        server.enqueue(htmlResponse("""
                <html><body><div id="divCont">
                  <div class="ACT_TITLE lx_elem"><div name="-1" id="-1">Nogironligi bo'lgan bolaga nafaqa tayinlash tartibi</div></div>
                  <div class="ACT_TEXT lx_elem"><div name="-11" id="-11">17-band. <span class="show_context">Nogironligi</span> bo'lgan bolaga belgilangan shartlarda nafaqa tayinlanadi.</div></div>
                </div></body></html>
                """));
        server.enqueue(htmlResponse("""
                <html><body><div id="divCont">
                  <div class="ACT_TITLE lx_elem"><div name="-1" id="-1">Kompleks ijtimoiy xizmatlarni ko'rsatish tartibi</div></div>
                  <div class="ACT_TEXT lx_elem"><div name="-21" id="-21">5-band. Nogironligi bo'lgan bola uchun <span class="show_context">ijtimoiy xizmat</span>lar individual ehtiyoj asosida belgilanadi.</div></div>
                </div></body></html>
                """));

        List<RagSource> result = service.query("""
                Uchqo'rg'on tumani Yorqin Hayot mahallasidagi Sotvoldiyev Jahongirning
                aqliy zaiflikning o'rta darajasi bo'lgan 7 yoshli o'g'li bor. Ayoli bola
                mashg'uloti bilan mashg'ul, Jahongir esa ishsiz. Bolaga nogironlik nafaqasi
                tayinlanmagan. Qarorlar asosida kompleks ijtimoiy hizmatlar ishlab chiq.
                """, 10);

        assertThat(result)
                .extracting(RagSource::documentId)
                .containsExactlyInAnyOrder("-101", "-202");

        RecordedRequest benefitSearch = server.takeRequest();
        RecordedRequest serviceSearch = server.takeRequest();
        RecordedRequest employmentSearch = server.takeRequest();
        assertThat(benefitSearch.getRequestUrl().queryParameter("query"))
                .isEqualTo("nogironligi bola nafaqa tayinlash");
        assertThat(serviceSearch.getRequestUrl().queryParameter("query"))
                .isEqualTo("ijtimoiy xizmat nogironligi bola");
        assertThat(employmentSearch.getRequestUrl().queryParameter("query"))
                .isEqualTo("ishsiz bandlik");
        assertThat(List.of(
                benefitSearch.getRequestUrl().queryParameter("query"),
                serviceSearch.getRequestUrl().queryParameter("query"),
                employmentSearch.getRequestUrl().queryParameter("query")))
                .allSatisfy(query -> assertThat(query)
                        .doesNotContainIgnoringCase("Uchqo'rg'on", "Yorqin", "Sotvoldiyev", "Jahongir"));
    }

    private MockResponse searchResult(String documentId, String title, String metadata) {
        return htmlResponse("""
                <html><body><table>
                  <tr class="dd-table__main-item">
                    <td><span class="lx_act_state"><i class="status_code_y"></i></span></td>
                    <td>
                      <div class="dd-table__main-left-desc">
                        <a class="lx_link" href="/uz/docs/%s">%s</a>
                      </div>
                      <span class="badge-nine">%s</span>
                    </td>
                  </tr>
                </table></body></html>
                """.formatted(documentId, title, metadata));
    }

    private MockResponse htmlResponse(String body) {
        return new MockResponse()
                .addHeader("Content-Type", "text/html; charset=utf-8")
                .setBody(body);
    }
}
