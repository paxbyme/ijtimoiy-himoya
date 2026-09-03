package com.manager.service;

import com.manager.dto.RagSource;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Retrieves current legal evidence directly from Lex.uz.
 *
 * <p>The public national-legislation search is restricted to active normative
 * documents ({@code status=Y&nature=1}). Document pages are requested with the
 * same query so Lex.uz marks matching provisions with {@code show_context}.
 * Matching provisions are expanded to their surrounding legal section and
 * ranked by both subject relevance and factual completeness before entering
 * the AI prompt.</p>
 */
@Service
public class LexUzService {

    private static final Logger log = LoggerFactory.getLogger(LexUzService.class);
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "[\\p{L}\\p{N}][\\p{L}\\p{N}'ʻʼ’‘-]*");
    private static final Pattern NUMBERED_PROVISION_PATTERN = Pattern.compile(
            "^\\d+(?:\\.\\d+)*(?:\\.|-band\\.)\\s+.*", Pattern.CASE_INSENSITIVE);
    private static final int MAX_QUERY_CHARS = 100;
    private static final int MAX_RESULTS = 12;
    private static final int MAX_SEARCH_BYTES = 2 * 1024 * 1024;
    private static final int MAX_DOCUMENT_BYTES = 6 * 1024 * 1024;
    private static final int MAX_SNIPPET_CHARS = 6_000;
    private static final int MAX_CACHE_ENTRIES = 200;
    private static final int MAX_QUERY_TERMS = 16;

    /**
     * Wall-clock ceiling for one whole {@link #query} call. It is kept below the
     * caller's own Lex.uz timeout (18s in AiController), leaving headroom for the
     * upstream LLM query planner, so this method returns whatever evidence has
     * already been extracted instead of being force-killed and yielding nothing.
     * Search time counts against it, shrinking the budget left for document
     * fetches.
     */
    private static final long QUERY_BUDGET_MILLIS = 14_000;

    /**
     * Legal concepts must outrank names and addresses in long case narratives.
     * Equal-priority terms retain the order in which the user wrote them.
     */
    private static final Map<String, Integer> QUERY_TERM_PRIORITY = Map.ofEntries(
            Map.entry("ptpk", 130),
            Map.entry("nogironligi", 120),
            Map.entry("kunduzgi", 120),
            Map.entry("bola", 115),
            Map.entry("farzandi", 115),
            Map.entry("parvarish", 115),
            Map.entry("ijtimoiy", 110),
            Map.entry("subsidiya", 110),
            Map.entry("xizmat", 105),
            Map.entry("nafaqa", 100),
            Map.entry("reabilitatsiya", 100),
            Map.entry("bandlik", 95),
            Map.entry("ishsiz", 90),
            Map.entry("tayinlash", 90),
            Map.entry("aqliy", 85),
            Map.entry("zaiflik", 80),
            Map.entry("pensiya", 80),
            Map.entry("kompensatsiya", 80),
            Map.entry("kompleks", 75),
            Map.entry("daraja", 70),
            Map.entry("yosh", 70),
            Map.entry("ariza", 65),
            Map.entry("transport", 65)
    );

    private static final Set<String> STOP_WORDS = Set.of(
            "men", "menda", "meni", "mening", "biz", "bizda", "bizning",
            "u", "uning", "ular", "bu", "shu", "o'sha", "ushbu",
            "qanday", "qanaqa", "qaysi", "nima", "nega", "nechta", "qancha",
            "kim", "kimga", "kimning", "kimni", "kimdan",
            "bor", "yo'q", "kerak", "mumkin", "olish", "olinadi", "olaman",
            "ber", "berish", "bering", "ko'rsating", "yozing",
            "ayting", "tushuntiring", "haqida", "bo'yicha", "uchun", "bilan",
            "ham", "yoki", "va", "agar", "lekin", "qilib", "qilish",
            "bo'lgan", "bo'lsa", "bo'ladi", "ekan", "edi", "hozir", "holatda",
            "ma'lumot", "savol", "javob", "iltimos", "batafsil", "to'liq",
            "to'la", "aniq"
    );

    private final boolean enabled;
    private final HttpUrl baseUrl;
    private final int maxDocuments;
    private final int maxSnippetsPerDocument;
    private final long cacheTtlSeconds;
    private final Executor executor;
    private final OkHttpClient httpClient;
    private final Map<String, CacheEntry> cache = new java.util.concurrent.ConcurrentHashMap<>();

    public LexUzService(
            @Value("${lexuz.enabled:true}") boolean enabled,
            @Value("${lexuz.base-url:https://lex.uz}") String baseUrl,
            @Value("${lexuz.max-documents:4}") int maxDocuments,
            @Value("${lexuz.max-snippets-per-document:6}") int maxSnippetsPerDocument,
            @Value("${lexuz.cache-ttl-seconds:3600}") long cacheTtlSeconds,
            @Qualifier("lexUzExecutor") Executor executor) {
        this.enabled = enabled;
        HttpUrl parsed = HttpUrl.parse(baseUrl);
        if (parsed == null) {
            throw new IllegalArgumentException("Invalid lexuz.base-url: " + baseUrl);
        }
        this.baseUrl = parsed;
        this.maxDocuments = Math.max(1, Math.min(maxDocuments, MAX_RESULTS));
        this.maxSnippetsPerDocument = Math.max(1, Math.min(maxSnippetsPerDocument, 6));
        this.cacheTtlSeconds = Math.max(60, cacheTtlSeconds);
        this.executor = executor;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                // Lex.uz document pages are large (2+ MB); a legitimate download
                // can take ~5s, so the read/call ceilings must not cut it off
                // prematurely. The global query budget caps total wall time.
                .readTimeout(9, TimeUnit.SECONDS)
                .writeTimeout(3, TimeUnit.SECONDS)
                .callTimeout(11, TimeUnit.SECONDS)
                .build();
    }

    public List<RagSource> query(String question, int requestedTopK) {
        return query(question, List.of(), requestedTopK);
    }

    /**
     * Retrieves evidence using explicit search queries produced by the LLM
     * query planner. Each planned query becomes its own search group, so a
     * multi-issue question fans out to the right documents. When
     * {@code plannedQueries} is empty (or yields no usable terms) the method
     * falls back to keyword extraction from the raw question.
     */
    public List<RagSource> query(String question, List<String> plannedQueries, int requestedTopK) {
        if (!enabled || question == null || question.isBlank()) return List.of();
        if (plannedQueries == null) plannedQueries = List.of();

        int topK = Math.max(1, Math.min(requestedTopK > 0 ? requestedTopK : 6, MAX_RESULTS));
        String planKey = plannedQueries.isEmpty() ? "kw" : String.join(",", plannedQueries);
        String cacheKey = normalizeWhitespace(question).toLowerCase(Locale.ROOT)
                + "|" + topK + "|" + planKey.toLowerCase(Locale.ROOT);
        CacheEntry cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAt.isAfter(Instant.now())) {
            return cached.sources;
        }

        Instant deadline = Instant.now().plusMillis(QUERY_BUDGET_MILLIS);

        // Prefer the LLM planner's focused queries, but never let a weak plan
        // suppress retrieval: if the planned queries match no active documents,
        // fall back to keyword extraction (which also carries the domain-specific
        // query expansions) before giving up.
        List<SearchSelection> selections = List.of();
        if (!plannedQueries.isEmpty()) {
            List<List<String>> plannedGroups = buildPlannedGroups(plannedQueries);
            if (!plannedGroups.isEmpty()) selections = runSearches(plannedGroups);
        }
        if (selections.isEmpty()) {
            List<String> terms = extractQueryTerms(question);
            if (!terms.isEmpty()) selections = runSearches(buildQueryGroups(terms));
        }
        if (selections.isEmpty()) {
            return List.of();
        }

        // Round-robin across needs (benefits, services, employment) so one
        // broad search cannot consume the entire document budget.
        List<EvidenceRequest> requests = new ArrayList<>();
        Set<String> selectedDocumentIds = new HashSet<>();
        for (int documentIndex = 0; requests.size() < maxDocuments; documentIndex++) {
            boolean foundAtThisIndex = false;
            for (SearchSelection selection : selections) {
                if (documentIndex >= selection.documents.size()) continue;
                foundAtThisIndex = true;
                SearchDocument document = selection.documents.get(documentIndex);
                if (selectedDocumentIds.add(document.documentId)) {
                    requests.add(new EvidenceRequest(document, selection.query));
                    if (requests.size() >= maxDocuments) break;
                }
            }
            if (!foundAtThisIndex) break;
        }

        List<CompletableFuture<List<RagSource>>> futures = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            EvidenceRequest request = requests.get(i);
            int rank = i;
            futures.add(CompletableFuture.supplyAsync(
                            () -> fetchEvidence(
                                    request.document, request.query, question, rank), executor)
                    .completeOnTimeout(List.of(), 9, TimeUnit.SECONDS)
                    .exceptionally(error -> {
                        log.warn("Lex.uz document fetch failed: documentId={} error={}",
                                request.document.documentId, rootMessage(error));
                        return List.of();
                    }));
        }

        // Collect against the global deadline instead of blocking on the slowest
        // fetch. When the budget runs out we keep the evidence already gathered
        // and cancel the rest, so a single slow document no longer forces an
        // empty result (which would suppress the AI answer entirely).
        List<RagSource> allSources = new ArrayList<>();
        boolean budgetExhausted = false;
        for (CompletableFuture<List<RagSource>> future : futures) {
            if (budgetExhausted) {
                future.cancel(true);
                continue;
            }
            long remainingMs = Duration.between(Instant.now(), deadline).toMillis();
            if (remainingMs <= 0) {
                future.cancel(true);
                budgetExhausted = true;
                continue;
            }
            try {
                allSources.addAll(future.get(remainingMs, TimeUnit.MILLISECONDS));
            } catch (TimeoutException e) {
                future.cancel(true);
                budgetExhausted = true;
                log.warn("Lex.uz fetch budget exhausted after {} source(s); returning partial evidence",
                        allSources.size());
            } catch (Exception e) {
                log.warn("Lex.uz fetch join failed: {}", rootMessage(e));
            }
        }

        // Lex.uz search ordering is not a relevance guarantee (newer broad
        // documents can precede an older exact regulation). Rank every
        // extracted provision globally before applying the prompt budget.
        List<RagSource> sources = allSources.stream()
                .sorted(Comparator.comparingDouble(RagSource::score).reversed())
                .limit(topK)
                .toList();

        List<RagSource> result = List.copyOf(sources);
        if (!result.isEmpty()) putCache(cacheKey, result);
        return result;
    }

    /**
     * Runs each query group as its own Lex.uz search concurrently and returns
     * only the selections that actually matched documents. A per-group timeout
     * and exception guard keep one slow or failing search from starving the
     * others or aborting the whole retrieval.
     */
    private List<SearchSelection> runSearches(List<List<String>> queryGroups) {
        List<CompletableFuture<SearchSelection>> searchFutures = queryGroups.stream()
                .map(group -> CompletableFuture.supplyAsync(() -> findDocuments(group), executor)
                        .completeOnTimeout(new SearchSelection("", List.of()), 8, TimeUnit.SECONDS)
                        .exceptionally(error -> {
                            log.warn("Lex.uz grouped search failed: terms={} error={}",
                                    group, rootMessage(error));
                            return new SearchSelection("", List.of());
                        }))
                .toList();

        return searchFutures.stream()
                .map(CompletableFuture::join)
                .filter(selection -> !selection.documents.isEmpty())
                .toList();
    }

    private SearchSelection findDocuments(List<String> terms) {
        int firstSize = Math.min(4, terms.size());
        for (int size = firstSize; size >= 1; size--) {
            String query = String.join(" ", terms.subList(0, size));
            if (query.length() > MAX_QUERY_CHARS) {
                query = query.substring(0, MAX_QUERY_CHARS).trim();
            }
            try {
                List<SearchDocument> documents = search(query);
                if (!documents.isEmpty()) return new SearchSelection(query, documents);
            } catch (Exception e) {
                log.warn("Lex.uz search failed: query='{}' error={}", query, rootMessage(e));
                return new SearchSelection(query, List.of());
            }
        }
        return new SearchSelection("", List.of());
    }

    private List<List<String>> buildQueryGroups(List<String> terms) {
        Set<String> available = new LinkedHashSet<>(terms);
        List<List<String>> groups = new ArrayList<>();

        if (hasAny(available, "nogironligi")
                && hasAny(available, "bola", "farzandi")
                && hasAny(available,
                        "ijtimoiy", "xizmat", "reabilitatsiya", "aqliy", "zaiflik",
                        "kunduzgi", "parvarish", "ptpk")) {
            // A child-disability case can imply a day-care need even when the
            // family describes it only as "complex social services". These
            // formal terms reliably surface VMQ-126 and its PTPK provisions.
            groups.add(List.of("kunduzgi", "parvarish", "nogironligi", "bola"));
        }

        if (hasAny(available, "nafaqa", "pensiya", "tayinlash")
                && hasAny(available, "nogironligi", "bola", "farzandi")) {
            addGroup(groups, available,
                    "nogironligi", "bola", "farzandi", "nafaqa", "pensiya", "tayinlash");
        }

        if (hasAny(available, "ijtimoiy", "xizmat", "reabilitatsiya")) {
            addGroup(groups, available,
                    "ijtimoiy", "xizmat", "nogironligi", "bola", "farzandi", "reabilitatsiya");
        }

        if (hasAny(available, "ishsiz", "bandlik")) {
            List<String> employment = new ArrayList<>();
            if (available.contains("ishsiz")) employment.add("ishsiz");
            // "bandlik" is a safe legal-search expansion for an unemployment
            // need even when the user did not use the formal statutory term.
            employment.add("bandlik");
            groups.add(List.copyOf(employment));
        }

        if (groups.isEmpty()) groups.add(terms);
        return groups.stream().distinct().toList();
    }

    private List<List<String>> buildPlannedGroups(List<String> plannedQueries) {
        List<List<String>> groups = new ArrayList<>();
        Set<List<String>> seen = new LinkedHashSet<>();
        for (String plannedQuery : plannedQueries) {
            if (plannedQuery == null || plannedQuery.isBlank()) continue;
            List<String> tokens = tokenizePlannedQuery(plannedQuery);
            if (!tokens.isEmpty() && seen.add(tokens)) groups.add(tokens);
            if (groups.size() >= MAX_RESULTS) break;
        }
        return groups;
    }

    private List<String> tokenizePlannedQuery(String plannedQuery) {
        String normalized = plannedQuery
                .replace('’', '\'').replace('‘', '\'')
                .replace('ʻ', '\'').replace('ʼ', '\'')
                .toLowerCase(Locale.ROOT);
        Matcher matcher = TOKEN_PATTERN.matcher(normalized);
        List<String> tokens = new ArrayList<>();
        while (matcher.find()) {
            String token = canonicalLegalTerm(matcher.group());
            if (token.length() >= 3 && !STOP_WORDS.contains(token) && !tokens.contains(token)) {
                tokens.add(token);
            }
            if (tokens.size() >= 6) break;
        }
        return tokens;
    }

    private void addGroup(List<List<String>> groups, Set<String> available, String... orderedTerms) {
        List<String> group = new ArrayList<>();
        for (String term : orderedTerms) {
            if (available.contains(term) && !group.contains(term)) group.add(term);
        }
        if (!group.isEmpty()) groups.add(List.copyOf(group));
    }

    private boolean hasAny(Set<String> values, String... candidates) {
        for (String candidate : candidates) {
            if (values.contains(candidate)) return true;
        }
        return false;
    }

    private List<SearchDocument> search(String query) throws Exception {
        HttpUrl url = baseUrl.newBuilder()
                .addPathSegments("uz/search/nat")
                .addQueryParameter("query", query)
                .addQueryParameter("status", "Y")
                .addQueryParameter("nature", "1")
                .build();

        Request request = request(url).build();
        String html;
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IllegalStateException("HTTP " + response.code());
            }
            html = readLimited(response.body(), MAX_SEARCH_BYTES);
        }

        Document page = Jsoup.parse(html, baseUrl.toString());
        List<SearchDocument> documents = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (Element row : page.select("tr.dd-table__main-item")) {
            if (row.selectFirst(".status_code_y") == null) continue;
            Element link = row.selectFirst(".dd-table__main-left-desc a.lx_link[href*=\"/docs/\"]");
            if (link == null) continue;

            String href = link.absUrl("href");
            HttpUrl resultUrl = HttpUrl.parse(href);
            if (resultUrl == null) continue;
            String documentId = extractDocumentId(resultUrl.encodedPath());
            if (documentId.isBlank() || !seenIds.add(documentId)) continue;

            HttpUrl canonicalUrl = baseUrl.newBuilder()
                    .addPathSegments("uz/docs")
                    .addPathSegment(documentId)
                    .build();
            Element metadata = row.selectFirst(".badge-nine");
            documents.add(new SearchDocument(
                    documentId,
                    normalizeWhitespace(link.text()),
                    metadata != null ? normalizeWhitespace(metadata.text()) : "",
                    canonicalUrl));
            if (documents.size() >= maxDocuments) break;
        }
        return documents;
    }

    private List<RagSource> fetchEvidence(
            SearchDocument result, String query, String question, int rank) {
        try {
            HttpUrl requestUrl = result.canonicalUrl.newBuilder()
                    .addQueryParameter("query", query)
                    .build();
            String html;
            try (Response response = httpClient.newCall(request(requestUrl).build()).execute()) {
                if (!response.isSuccessful()) {
                    throw new IllegalStateException("HTTP " + response.code());
                }
                html = readLimited(response.body(), MAX_DOCUMENT_BYTES);
            }

            Document page = Jsoup.parse(html, result.canonicalUrl.toString());
            String pageTitle = officialTitle(page);
            String title = !pageTitle.isBlank() ? pageTitle : result.title;
            if (!result.metadata.isBlank()) title = title + ". " + result.metadata;

            LinkedHashMap<String, Element> matchedElements = new LinkedHashMap<>();
            for (Element highlight : page.select("span.show_context")) {
                Element legalElement = highlight.closest(".lx_elem");
                if (legalElement == null || isDocumentHeading(legalElement)) continue;
                Element contentElement = officialContentElement(legalElement);
                if (contentElement == null || contentElement.text().isBlank()) continue;
                String id = contentElement.id();
                if (id.isBlank()) id = "match-" + matchedElements.size();
                matchedElements.putIfAbsent(id, legalElement);
                if (matchedElements.size() >= 150) break;
            }

            // Lex.uz highlights only a small selection of search hits. The
            // decisive amount, eligibility or procedure clause can therefore
            // be present in the official document without show_context. Scan
            // every official provision that contains a subject term, then let
            // the factual relevance ranker choose the useful legal sections.
            for (Element legalElement : page.select(".lx_elem")) {
                if (isDocumentHeading(legalElement)) continue;
                Element contentElement = officialContentElement(legalElement);
                if (contentElement == null || contentElement.text().isBlank()) continue;
                if (queryTermCoverage(legalElement, query) == 0) continue;
                String id = contentElement.id();
                if (id.isBlank()) id = "provision-" + matchedElements.size();
                matchedElements.putIfAbsent(id, legalElement);
                if (matchedElements.size() >= 2_000) break;
            }

            List<EvidenceMatch> rankedMatches = new ArrayList<>();
            for (Map.Entry<String, Element> match : matchedElements.entrySet()) {
                String content = surroundingLegalContext(match.getValue());
                if (content.isBlank()) continue;
                Element official = officialContentElement(match.getValue());
                String matchedProvision = official != null ? official.text() : content;
                int relevance = evidenceRelevance(
                        matchedProvision, content, title, query, question, rank);
                rankedMatches.add(new EvidenceMatch(
                        match.getKey(), match.getValue(), content, relevance));
            }
            rankedMatches.sort(Comparator
                    .comparingInt(EvidenceMatch::relevance).reversed()
                    .thenComparingInt(match -> documentOrder(match.element)));

            List<RagSource> sources = new ArrayList<>();
            List<String> selectedContexts = new ArrayList<>();
            int chunkIndex = 0;
            for (EvidenceMatch match : rankedMatches) {
                if (isOverlappingContext(match.element, match.content, selectedContexts)) continue;
                String sourceUrl = result.canonicalUrl.newBuilder()
                        .fragment(match.id.startsWith("match-") ? null : match.id)
                        .build()
                        .toString();
                sources.add(new RagSource(
                        "lexuz:" + result.documentId + ":" + match.id,
                        result.documentId,
                        title,
                        chunkIndex++,
                        match.relevance,
                        match.content,
                        sourceUrl));
                selectedContexts.add(normalizeForComparison(match.content));
                if (sources.size() >= maxSnippetsPerDocument) break;
            }
            return sources;
        } catch (Exception e) {
            log.warn("Unable to extract Lex.uz evidence: documentId={} error={}",
                    result.documentId, rootMessage(e));
            return List.of();
        }
    }

    private String surroundingLegalContext(Element matched) {
        List<String> blocks = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();

        // If the match is a subparagraph, include its nearest numbered parent
        // provision. A numbered match already identifies its own legal clause.
        if (!isNumberedProvision(matched)) {
            List<Element> preceding = new ArrayList<>();
            Element cursor = matched.previousElementSibling();
            for (int i = 0; cursor != null && i < 4; i++, cursor = cursor.previousElementSibling()) {
                if (!cursor.hasClass("lx_elem")) continue;
                preceding.add(0, cursor);
                if (isNumberedProvision(cursor)) break;
            }
            for (Element element : preceding) addOfficialText(blocks, seen, element);
        }
        addOfficialText(blocks, seen, matched);

        // Collect the whole provision, including lettered subparagraphs and
        // amendments, until the next numbered legal provision begins.
        Element cursor = matched.nextElementSibling();
        for (int scanned = 0; cursor != null && scanned < 24;
             scanned++, cursor = cursor.nextElementSibling()) {
            if (!cursor.hasClass("lx_elem")) continue;
            if (isNumberedProvision(cursor)) break;
            addOfficialText(blocks, seen, cursor);
        }

        String text = String.join("\n", blocks).trim();
        if (text.length() > MAX_SNIPPET_CHARS) {
            text = text.substring(0, MAX_SNIPPET_CHARS).trim() + " …";
        }
        return text;
    }

    private void addOfficialText(List<String> blocks, Set<String> seen, Element legalElement) {
        if (isDocumentHeading(legalElement)) return;
        Element content = officialContentElement(legalElement);
        if (content == null) return;
        String text = normalizeWhitespace(content.text());
        if (!text.isBlank() && seen.add(text)) blocks.add(text);
    }

    private Element officialContentElement(Element legalElement) {
        for (Element child : legalElement.children()) {
            if (child.hasAttr("name") && child.hasAttr("id")) return child;
        }
        return null;
    }

    private boolean isDocumentHeading(Element legalElement) {
        String classes = legalElement.className().toUpperCase(Locale.ROOT);
        return classes.contains("ACT_TITLE")
                || classes.contains("SUBTITLE")
                || classes.contains("CHAPTER")
                || classes.contains("ACT_FORM")
                || classes.contains("ACCEPTING_BODY")
                || classes.contains("BANNER");
    }

    private boolean isNumberedProvision(Element legalElement) {
        Element content = officialContentElement(legalElement);
        return content != null
                && NUMBERED_PROVISION_PATTERN.matcher(normalizeWhitespace(content.text())).matches();
    }

    private int queryTermCoverage(Element legalElement, String query) {
        Element content = officialContentElement(legalElement);
        if (content == null) return 0;
        return termCoverage(normalizeForComparison(content.text()), query);
    }

    private int evidenceRelevance(String matchedProvision, String content, String title, String query,
                                  String question, int resultRank) {
        String provision = normalizeForComparison(matchedProvision);
        String text = normalizeForComparison(content);
        String normalizedTitle = normalizeForComparison(title);
        String normalizedQuestion = normalizeForComparison(question);

        int score = termCoverage(provision, query) * 20;
        score += termCoverage(normalizedTitle, query) * 10;
        score += factualDetailScore(text);

        if (containsAny(normalizedQuestion, "batafsil", "to'liq", "to'la", "hammasi")) {
            score += factualDetailScore(text);
        }
        if (containsAny(normalizedQuestion,
                "qancha", "miqdor", "summa", "foiz", "to'lov", "subsidiya", "nafaqa")) {
            if (containsAny(text,
                    "foiz", "baravar", "bhm", "bazaviy hisoblash", "so'm", "summa")) {
                score += 42;
            } else if (containsAny(text, "subsidiya", "nafaqa", "kompensatsiya", "to'lov")) {
                score += 12;
            }
        }
        if (containsAny(normalizedQuestion, "kim", "kimga", "toifa", "shart", "talab")) {
            score += containsAny(text,
                    "huquq", "toifa", "shart", "talab", "fuqaro", "bola", "shaxs") ? 18 : 0;
        }
        if (containsAny(normalizedQuestion, "qachon", "muddat", "sana", "necha kun", "necha oy")) {
            score += containsAny(text, "muddat", "kun", "oy", "yil", "sanadan") ? 18 : 0;
        }
        if (containsAny(normalizedQuestion, "qayer", "hudud", "viloyat", "tuman", "shahar")) {
            score += containsAny(text, "hudud", "viloyat", "tuman", "shahar", "respublika") ? 18 : 0;
        }
        if (containsAny(normalizedQuestion,
                "qanday", "tartib", "ariza", "hujjat", "murojaat", "yo'nalish")) {
            score += containsAny(text,
                    "tartib", "ariza", "hujjat", "murojaat", "xizmat", "yo'nalish",
                    "transport", "amalga oshiriladi") ? 18 : 0;
        }

        boolean childDayCareQuestion = containsAny(normalizeForComparison(query),
                "kunduzgi", "parvarish")
                && containsAny(normalizedQuestion, "bola", "farzand", "yosh");
        if (childDayCareQuestion) {
            if (containsAny(normalizedTitle,
                    "nogironligi bo'lgan bolalar uchun kunduzgi parvarish")) {
                score += 60;
            }
            if (containsAny(text,
                    "psixologik-tibbiy-pedagogik komissiya", "ptpk")) {
                score += 50;
            }
            if (containsAny(normalizedQuestion, "aqliy", "zaiflik", "f71")
                    && containsAny(text, "mo'tadil aqliy zaiflik", "f71")) {
                score += 45;
            }
        }

        if (text.matches("^\\d+[.-]?.*")) score += 6;
        if (containsAny(text, "tasdiqlansin", "xulosa berildi", "maqsadga muvofiq")) score -= 8;

        // Rank and document position only break otherwise similar matches; a
        // later exact provision must still beat an early generic paragraph.
        score -= resultRank * 2;
        return score;
    }

    private int factualDetailScore(String text) {
        int score = 0;
        if (text.matches(".*\\d.*")) score += 5;
        if (containsAny(text, "foiz", "baravar", "bhm", "bazaviy hisoblash", "so'm")) score += 10;
        if (containsAny(text, "muddat", "kun", "oy", "yil", "sanadan")) score += 4;
        if (containsAny(text, "ariza", "hujjat", "murojaat", "tartib")) score += 4;
        if (containsAny(text, "shart", "talab", "huquq", "istisno", "bundan tashqari")) score += 4;
        if (containsAny(text, "hudud", "viloyat", "tuman", "shahar", "respublika")) score += 4;
        if (containsAny(text, "subsidiya", "nafaqa", "kompensatsiya", "to'lov")) score += 6;
        return score;
    }

    private int termCoverage(String normalizedText, String query) {
        int score = 0;
        for (String term : normalizeForComparison(query).split("\\s+")) {
            if (!term.isBlank() && legalTermMatches(normalizedText, term)) score++;
        }
        return score;
    }

    private boolean legalTermMatches(String text, String term) {
        if (text.contains(term)) return true;
        if (term.startsWith("ajrat")) return text.contains("ajrat");
        if (term.startsWith("to'la") || term.startsWith("to'lov")) return text.contains("to'lo");
        if (term.startsWith("murojaat")) return text.contains("murojaat");
        return false;
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) return true;
        }
        return false;
    }

    private boolean isOverlappingContext(
            Element element, String content, List<String> selectedContexts) {
        Element official = officialContentElement(element);
        String center = normalizeForComparison(official != null ? official.text() : "");
        String normalizedContent = normalizeForComparison(content);
        for (String selected : selectedContexts) {
            if (!center.isBlank() && selected.contains(center)) return true;
            if (normalizedContent.equals(selected)) return true;
        }
        return false;
    }

    private int documentOrder(Element element) {
        int order = 0;
        Element cursor = element.previousElementSibling();
        while (cursor != null && order < 1_000) {
            if (cursor.hasClass("lx_elem")) order++;
            cursor = cursor.previousElementSibling();
        }
        return order;
    }

    private String officialTitle(Document page) {
        Element title = page.selectFirst(".ACT_TITLE.lx_elem");
        Element content = title != null ? officialContentElement(title) : null;
        return content != null ? normalizeWhitespace(content.text()) : "";
    }

    private List<String> extractQueryTerms(String question) {
        String normalized = question
                .replace('’', '\'')
                .replace('‘', '\'')
                .replace('ʻ', '\'')
                .replace('ʼ', '\'')
                .toLowerCase(Locale.ROOT);
        Matcher matcher = TOKEN_PATTERN.matcher(normalized);
        LinkedHashMap<String, Integer> terms = new LinkedHashMap<>();
        int position = 0;
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() < 3 || STOP_WORDS.contains(token)) continue;
            token = canonicalLegalTerm(token);
            if (token.length() >= 3 && !STOP_WORDS.contains(token)) {
                terms.putIfAbsent(token, position++);
            }
            if (terms.size() >= 64) break;
        }
        return terms.entrySet().stream()
                .sorted(Comparator
                        .<Map.Entry<String, Integer>>comparingInt(
                                entry -> QUERY_TERM_PRIORITY.getOrDefault(entry.getKey(), 0))
                        .reversed()
                        .thenComparingInt(Map.Entry::getValue))
                .limit(MAX_QUERY_TERMS)
                .map(Map.Entry::getKey)
                .toList();
    }

    private String canonicalLegalTerm(String token) {
        if (token.startsWith("nogiron")) return "nogironligi";
        if (token.startsWith("farzand")) return "farzandi";
        if (token.startsWith("bola")) return "bola";
        if (token.startsWith("nafaqa")) return "nafaqa";
        if (token.startsWith("imtiyoz")) return "imtiyoz";
        if (token.startsWith("pensiya")) return "pensiya";
        if (token.startsWith("ijtimoiy")) return "ijtimoiy";
        if (token.startsWith("xizmat") || token.startsWith("hizmat")) return "xizmat";
        if (token.startsWith("ishsiz")) return "ishsiz";
        if (token.startsWith("bandlik")) return "bandlik";
        if (token.startsWith("tayinlan") || token.startsWith("tayinlash")) return "tayinlash";
        if (token.startsWith("parvarish")) return "parvarish";
        if (token.startsWith("reabilit")) return "reabilitatsiya";
        if (token.startsWith("subsidiya")) return "subsidiya";
        if (token.startsWith("kompensatsiya")) return "kompensatsiya";
        if (token.startsWith("zaif")) return "zaiflik";
        if (token.startsWith("daraja")) return "daraja";
        if (token.startsWith("yosh")) return "yosh";
        if (token.equals("ptpk")) return "ptpk";

        String[] suffixes = {"laringiz", "larining", "larning", "lardan", "larga",
                "larini", "lari", "larni", "ning", "ingiz", "imiz", "lar"};
        for (String suffix : suffixes) {
            if (token.length() > suffix.length() + 3 && token.endsWith(suffix)) {
                return token.substring(0, token.length() - suffix.length());
            }
        }
        if (token.length() > 6 && token.endsWith("im")) {
            return token.substring(0, token.length() - 2);
        }
        return token;
    }

    private Request.Builder request(HttpUrl url) {
        return new Request.Builder()
                .url(url)
                .get()
                .addHeader("Accept", "text/html,application/xhtml+xml")
                .addHeader("Accept-Language", "uz-UZ,uz;q=0.9")
                .addHeader("User-Agent", "Manager-Legal-Assistant/1.0 (Lex.uz evidence lookup)");
    }

    private String readLimited(ResponseBody body, int limit) throws Exception {
        if (body == null) return "";
        try (InputStream input = body.byteStream()) {
            byte[] bytes = input.readNBytes(limit);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private void putCache(String key, List<RagSource> sources) {
        Instant now = Instant.now();
        if (cache.size() >= MAX_CACHE_ENTRIES) {
            cache.entrySet().removeIf(entry -> entry.getValue().expiresAt.isBefore(now));
            if (cache.size() >= MAX_CACHE_ENTRIES) {
                cache.keySet().stream().findFirst().ifPresent(cache::remove);
            }
        }
        cache.put(key, new CacheEntry(now.plusSeconds(cacheTtlSeconds), List.copyOf(sources)));
    }

    private String extractDocumentId(String path) {
        int marker = path.indexOf("/docs/");
        if (marker < 0) return "";
        String id = path.substring(marker + "/docs/".length());
        int slash = id.indexOf('/');
        return slash >= 0 ? id.substring(0, slash) : id;
    }

    private static String normalizeWhitespace(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    private static String normalizeForComparison(String text) {
        return normalizeWhitespace(text)
                .replace('’', '\'')
                .replace('‘', '\'')
                .replace('ʻ', '\'')
                .replace('ʼ', '\'')
                .toLowerCase(Locale.ROOT);
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() != null ? current.getMessage() : current.getClass().getSimpleName();
    }

    private record SearchDocument(
            String documentId,
            String title,
            String metadata,
            HttpUrl canonicalUrl
    ) {}

    private record SearchSelection(String query, List<SearchDocument> documents) {}

    private record EvidenceRequest(SearchDocument document, String query) {}

    private record EvidenceMatch(String id, Element element, String content, int relevance) {}

    private record CacheEntry(Instant expiresAt, List<RagSource> sources) {}
}
