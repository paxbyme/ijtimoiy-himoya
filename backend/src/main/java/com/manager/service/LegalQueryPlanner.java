package com.manager.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Analyses a citizen's question with the LLM before retrieval and turns it into
 * a small set of focused Lex.uz search queries.
 *
 * <p>Keyword extraction alone cannot separate the distinct legal issues hidden
 * in a long, informal narrative ("my child has a disability and I also lost my
 * job"). Letting the model decompose the question first produces sharper,
 * domain-general searches, which is what ultimately lets the final answer be
 * precise. The planner never invents legal facts — it only proposes search
 * phrases; the strict grounded answer is still produced from the retrieved
 * Lex.uz context. Any failure (including a rate limit) degrades silently to
 * keyword search.</p>
 */
@Service
public class LegalQueryPlanner {

    private static final Logger log = LoggerFactory.getLogger(LegalQueryPlanner.class);

    private static final int MAX_QUERIES = 5;
    private static final int MAX_QUERY_CHARS = 80;

    private static final String PLANNER_INSTRUCTION = """
            Siz O'zbekiston qonunchiligi bo'yicha Lex.uz huquqiy qidiruv rejalashtiruvchisisiz.
            Sizga fuqaroning savoli beriladi. Vazifangiz — savolga javob berish EMAS, balki
            unda yashiringan alohida huquqiy masalalarni aniqlab, har biri uchun Lex.uz'da
            amaldagi normativ hujjatlarni topadigan qisqa qidiruv so'rovlarini tuzish.

            Qoidalar:
            - Har bir so'rov 2-5 ta so'zdan iborat, o'zbek tilida, rasmiy huquqiy atamalarda bo'lsin.
            - Savolda bir nechta masala bo'lsa (masalan nafaqa, ijtimoiy xizmat, bandlik), har biriga alohida so'rov yozing.
            - Fuqaro so'zlashuv tilida yozgan bo'lsa ham, rasmiy huquqiy atamaga aylantiring
              (masalan "bolamga pul" -> "bolalar nafaqasi tayinlash").
            - Ism, manzil, sana kabi shaxsiy tafsilotlarni so'rovga qo'shmang.
            - Eng ko'pi 5 ta so'rov. Ortiqcha izoh yozmang.

            Javobni FAQAT JSON massiv ko'rinishida bering, boshqa hech narsa yozmang:
            ["birinchi so'rov", "ikkinchi so'rov"]
            """;

    private final AiService aiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LegalQueryPlanner(AiService aiService) {
        this.aiService = aiService;
    }

    /**
     * Returns focused search queries for the question, or an empty list when
     * planning is unavailable so the caller can fall back to keyword search.
     */
    public List<String> plan(String question) {
        if (question == null || question.isBlank()) return List.of();
        try {
            String raw = aiService.chat(PLANNER_INSTRUCTION, List.of(), question.trim());
            List<String> queries = parseQueries(raw);
            if (!queries.isEmpty()) {
                log.info("Legal query planner produced {} search quer(ies)", queries.size());
            }
            return queries;
        } catch (Exception e) {
            log.warn("Legal query planning failed; falling back to keyword search: {}", e.getMessage());
            return List.of();
        }
    }

    private List<String> parseQueries(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        String json = extractJsonArray(raw);
        if (json == null) return List.of();

        List<String> queries = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isArray()) return List.of();
            for (JsonNode element : node) {
                if (!element.isTextual()) continue;
                String query = element.asText().replaceAll("\\s+", " ").trim();
                if (query.length() > MAX_QUERY_CHARS) {
                    query = query.substring(0, MAX_QUERY_CHARS).trim();
                }
                if (query.length() < 3) continue;
                if (seen.add(query.toLowerCase(Locale.ROOT))) queries.add(query);
                if (queries.size() >= MAX_QUERIES) break;
            }
        } catch (Exception e) {
            log.warn("Could not parse planner output as JSON: {}", e.getMessage());
            return List.of();
        }
        return queries;
    }

    /** Tolerates markdown code fences or surrounding prose around the JSON array. */
    private String extractJsonArray(String raw) {
        int start = raw.indexOf('[');
        int end = raw.lastIndexOf(']');
        if (start < 0 || end <= start) return null;
        return raw.substring(start, end + 1);
    }
}
