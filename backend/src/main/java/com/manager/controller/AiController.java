package com.manager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manager.dto.*;
import com.manager.exception.GeminiRateLimitException;
import com.manager.repository.AiConversationRepository;
import com.manager.repository.AiFeedbackRepository;
import com.manager.service.AiConversationPersistenceService;
import com.manager.service.AiRulesService;
import com.manager.service.AiService;
import com.manager.service.ConversationContext;
import com.manager.service.ConversationEvidenceCache;
import com.manager.service.LegalAssistantPrompt;
import com.manager.service.LegalQueryPlanner;
import com.manager.service.LexUzService;
import com.manager.service.RagService;
import com.manager.service.RateLimiterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    private static final int MAX_HISTORY_MESSAGES = 6;
    private static final int MAX_REQUESTS_PER_MINUTE = 20;
    private static final int MAX_RULE_CONTENT_CHARS = 1_000;
    private static final int MAX_TOTAL_RULE_CHARS = 6_000;
    private static final int RAG_TOP_K = 10;

    private final AiService aiService;
    private final AiRulesService aiRulesService;
    private final LexUzService lexUzService;
    private final LegalQueryPlanner legalQueryPlanner;
    private final RagService ragService;
    private final AiConversationRepository aiConversationRepository;
    private final AiFeedbackRepository aiFeedbackRepository;
    private final RateLimiterService rateLimiterService;
    private final AiConversationPersistenceService conversationPersistenceService;
    private final ConversationEvidenceCache conversationEvidenceCache;
    private final Executor aiPipelineExecutor;
    private final Executor aiStreamExecutor;
    private final ObjectMapper objectMapper;

    public AiController(AiService aiService,
                        AiRulesService aiRulesService,
                        LexUzService lexUzService, LegalQueryPlanner legalQueryPlanner,
                        RagService ragService,
                        AiConversationRepository aiConversationRepository,
                        AiFeedbackRepository aiFeedbackRepository,
                        RateLimiterService rateLimiterService,
                        AiConversationPersistenceService conversationPersistenceService,
                        ConversationEvidenceCache conversationEvidenceCache,
                        @Qualifier("aiPipelineExecutor") Executor aiPipelineExecutor,
                        @Qualifier("aiStreamExecutor") Executor aiStreamExecutor) {
        this.aiService = aiService;
        this.aiRulesService = aiRulesService;
        this.lexUzService = lexUzService;
        this.legalQueryPlanner = legalQueryPlanner;
        this.ragService = ragService;
        this.aiConversationRepository = aiConversationRepository;
        this.aiFeedbackRepository = aiFeedbackRepository;
        this.rateLimiterService = rateLimiterService;
        this.conversationPersistenceService = conversationPersistenceService;
        this.conversationEvidenceCache = conversationEvidenceCache;
        this.aiPipelineExecutor = aiPipelineExecutor;
        this.aiStreamExecutor = aiStreamExecutor;
        this.objectMapper = new ObjectMapper();
    }

    // ---- Chat (non-streaming, backward compatible) ----

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<AiChatResponse>> chat(
            @RequestBody AiChatRequest request,
            HttpServletRequest httpRequest) {
        try {
            String uid = (String) httpRequest.getAttribute("uid");
            String departmentId = (String) httpRequest.getAttribute("departmentId");

            if (isRateLimited(uid)) {
                return ResponseEntity.status(429)
                        .body(ApiResponse.error("Rate limit exceeded. Please wait before sending more messages."));
            }

            ChatContext ctx = buildChatContext(uid, departmentId, request.getMessage(), request.getConversationId());

            String aiResponse;
            if (ctx.evidence.isEmpty()) {
                aiResponse = LegalAssistantPrompt.noBasisAnswer();
            } else {
                String draft = aiService.chat(ctx.systemPrompt, ctx.history, request.getMessage());
                aiResponse = repairIfMalformed(ctx, request.getMessage(), draft);
            }

            // Save conversation
            String conversationId = saveConversation(ctx, request.getMessage(), aiResponse);

            AiChatResponse response = AiChatResponse.builder()
                    .response(aiResponse)
                    .conversationId(conversationId)
                    .sources(ctx.citations)
                    .build();

            return ResponseEntity.ok(ApiResponse.ok(response));

        } catch (GeminiRateLimitException e) {
            log.warn("AI chat rate-limited: {}", e.getMessage());
            return ResponseEntity.status(429)
                    .body(ApiResponse.error("AI is busy right now. Please try again in a moment."));
        } catch (Exception e) {
            log.error("AI chat failed", e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("AI chat failed: " + e.getMessage()));
        }
    }

    // ---- Chat (streaming via SSE) ----

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(
            @RequestBody AiChatRequest request,
            HttpServletRequest httpRequest) {

        SseEmitter emitter = new SseEmitter(120_000L); // 2 minute timeout

        String uid = (String) httpRequest.getAttribute("uid");
        String departmentId = (String) httpRequest.getAttribute("departmentId");

        if (isRateLimited(uid)) {
            CompletableFuture.runAsync(() -> {
                try {
                    emitter.send(SseEmitter.event().data(
                            objectMapper.writeValueAsString(Map.of(
                                    "type", "error",
                                    "message", "Rate limit exceeded. Please wait."))));
                    emitter.complete();
                } catch (IOException e) {
                    emitter.completeWithError(e);
                }
            }, aiStreamExecutor);
            return emitter;
        }

        CompletableFuture.runAsync(() -> {
            try {
                emitter.send(SseEmitter.event().data(
                        objectMapper.writeValueAsString(Map.of(
                                "type", "status",
                                "message", "Lex.uz'dan amaldagi normativ hujjatlar qidirilmoqda..."))));

                ChatContext ctx = buildChatContext(uid, departmentId, request.getMessage(), request.getConversationId());

                // Send meta with conversationId
                emitter.send(SseEmitter.event().data(
                        objectMapper.writeValueAsString(Map.of(
                                "type", "meta",
                                "conversationId", ctx.conversationId))));

                Consumer<String> tokenSink = token -> {
                    try {
                        emitter.send(SseEmitter.event().data(
                                objectMapper.writeValueAsString(Map.of(
                                        "type", "token",
                                        "text", token))));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                };

                String fullResponse;
                if (ctx.evidence.isEmpty()) {
                    fullResponse = LegalAssistantPrompt.noBasisAnswer();
                    tokenSink.accept(fullResponse);
                } else {
                    emitter.send(SseEmitter.event().data(
                            objectMapper.writeValueAsString(Map.of(
                                    "type", "status",
                                    "message", "Asoslangan javob tayyorlanmoqda..."))));
                    fullResponse = aiService.chatStream(
                            ctx.systemPrompt, ctx.history, request.getMessage(), tokenSink);

                    // A streamed draft is already on screen, so a contract
                    // violation is repaired and pushed as a full replacement
                    // rather than as more tokens.
                    if (!LegalAssistantPrompt.isWellFormed(fullResponse, ctx.evidence)) {
                        emitter.send(SseEmitter.event().data(
                                objectMapper.writeValueAsString(Map.of(
                                        "type", "status",
                                        "message", "Javob tuzilmasi tekshirilmoqda..."))));
                        String repaired = repairIfMalformed(ctx, request.getMessage(), fullResponse);
                        if (!repaired.equals(fullResponse)) {
                            fullResponse = repaired;
                            emitter.send(SseEmitter.event().data(
                                    objectMapper.writeValueAsString(Map.of(
                                            "type", "replace",
                                            "text", fullResponse))));
                        }
                    }
                }

                // Send done
                emitter.send(SseEmitter.event().data(
                        objectMapper.writeValueAsString(Map.of(
                                "type", "done",
                                "sources", ctx.citations != null ? ctx.citations : List.of()))));
                emitter.complete();

                // Queue persistence only after the completion event was sent. A
                // disconnected client can retry without duplicating a saved turn.
                try {
                    conversationPersistenceService.persistTurnAsync(
                            ctx.conversationId, request.getMessage(), fullResponse, MAX_HISTORY_MESSAGES);
                } catch (TaskRejectedException e) {
                    log.error("Conversation persistence queue is full: conversationId={}",
                            ctx.conversationId, e);
                }

            } catch (Exception e) {
                String errorMessage = (e instanceof GeminiRateLimitException)
                        ? "AI is busy right now. Please try again in a moment."
                        : (e.getMessage() != null ? e.getMessage() : "Unknown error");
                if (e instanceof GeminiRateLimitException) {
                    log.warn("AI chat (stream) rate-limited: {}", e.getMessage());
                }
                try {
                    emitter.send(SseEmitter.event().data(
                            objectMapper.writeValueAsString(Map.of(
                                    "type", "error",
                                    "message", errorMessage))));
                    emitter.complete();
                } catch (IOException ex) {
                    emitter.completeWithError(ex);
                }
            }
        }, aiStreamExecutor);

        return emitter;
    }

    // ---- Voice transcription ----

    @PostMapping(value = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, String>>> transcribe(
            @RequestParam("audio") MultipartFile audio,
            HttpServletRequest httpRequest) {
        try {
            String uid = (String) httpRequest.getAttribute("uid");

            if (isRateLimited(uid)) {
                return ResponseEntity.status(429)
                        .body(ApiResponse.error("Rate limit exceeded. Please wait before sending more messages."));
            }

            if (audio == null || audio.isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Audio file is empty"));
            }

            // Cap at ~10MB to keep transcription latency reasonable
            if (audio.getSize() > 10 * 1024 * 1024) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Audio file too large (max 10MB)"));
            }

            String rawMime = audio.getContentType();
            String mimeType = normalizeAudioMime(rawMime, audio.getOriginalFilename());

            log.info("Transcribe request: uid={} size={}B rawMime={} normalized={} filename={}",
                    uid, audio.getSize(), rawMime, mimeType, audio.getOriginalFilename());

            String transcript = aiService.transcribeAudio(audio.getBytes(), mimeType);
            return ResponseEntity.ok(ApiResponse.ok(Map.of("transcript", transcript)));

        } catch (Exception e) {
            log.error("Transcription failed", e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Transcription failed: " + e.getMessage()));
        }
    }

    private static String normalizeAudioMime(String raw, String filename) {
        // Gemini Files API accepts: audio/wav, audio/mp3, audio/mpeg, audio/aiff,
        // audio/aac, audio/ogg, audio/flac, audio/mp4
        String name = filename != null ? filename.toLowerCase() : "";
        if (name.endsWith(".m4a") || name.endsWith(".mp4") || name.endsWith(".aac")) {
            return "audio/mp4";
        }
        if (name.endsWith(".ogg") || name.endsWith(".opus")) return "audio/ogg";
        if (name.endsWith(".wav")) return "audio/wav";
        if (name.endsWith(".mp3")) return "audio/mp3";
        if (name.endsWith(".flac")) return "audio/flac";

        if (raw == null) return "audio/mp4";
        // Map non-standard m4a label to mp4 container mime
        if ("audio/m4a".equalsIgnoreCase(raw) || "audio/x-m4a".equalsIgnoreCase(raw)) {
            return "audio/mp4";
        }
        if (raw.startsWith("audio/")) return raw;
        return "audio/mp4";
    }

    // ---- Conversation management ----

    @GetMapping("/conversations")
    public ResponseEntity<ApiResponse<List<AiConversationDto>>> listConversations(HttpServletRequest httpRequest) {
        try {
            String uid = (String) httpRequest.getAttribute("uid");
            List<AiConversationDto> conversations = aiConversationRepository.findSummariesByStaffId(uid);
            return ResponseEntity.ok(ApiResponse.ok(conversations));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to list conversations: " + e.getMessage()));
        }
    }

    @GetMapping("/conversations/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getConversation(
            @PathVariable String id,
            HttpServletRequest httpRequest) {
        try {
            String uid = (String) httpRequest.getAttribute("uid");
            Map<String, Object> conversation = aiConversationRepository.findById(id);

            if (conversation == null) {
                return ResponseEntity.notFound().build();
            }

            // Verify ownership
            if (!uid.equals(conversation.get("staffId"))) {
                return ResponseEntity.status(403)
                        .body(ApiResponse.error("Access denied"));
            }

            return ResponseEntity.ok(ApiResponse.ok(conversation));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to get conversation: " + e.getMessage()));
        }
    }

    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteConversation(
            @PathVariable String id,
            HttpServletRequest httpRequest) {
        try {
            String uid = (String) httpRequest.getAttribute("uid");
            Map<String, Object> conversation = aiConversationRepository.findById(id);

            if (conversation == null) {
                return ResponseEntity.notFound().build();
            }

            if (!uid.equals(conversation.get("staffId"))) {
                return ResponseEntity.status(403)
                        .body(ApiResponse.error("Access denied"));
            }

            aiConversationRepository.delete(id);
            return ResponseEntity.ok(ApiResponse.ok("Conversation deleted", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to delete conversation: " + e.getMessage()));
        }
    }

    // ---- Feedback ----

    @PostMapping("/feedback")
    public ResponseEntity<ApiResponse<Map<String, Object>>> submitFeedback(
            @RequestBody AiFeedbackRequest request,
            HttpServletRequest httpRequest) {
        try {
            String uid = (String) httpRequest.getAttribute("uid");
            String departmentId = (String) httpRequest.getAttribute("departmentId");

            Map<String, Object> feedback = new HashMap<>();
            feedback.put("staffId", uid);
            feedback.put("departmentId", departmentId);
            feedback.put("conversationId", request.getConversationId());
            feedback.put("messageIndex", request.getMessageIndex());
            feedback.put("rating", request.getRating());
            feedback.put("comment", request.getComment());
            feedback.put("createdAt", Instant.now().toString());

            Map<String, Object> saved = aiFeedbackRepository.save(feedback);
            return ResponseEntity.ok(ApiResponse.ok("Feedback submitted", saved));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to submit feedback: " + e.getMessage()));
        }
    }

    // ---- Private helpers ----

    private ChatContext buildChatContext(String uid, String departmentId,
                                         String message, String conversationId) throws Exception {
        ChatContext ctx = new ChatContext();

        // Department rules do not steer retrieval, so they are read alongside
        // everything else and never add to the critical path.
        CompletableFuture<List<AiRuleDto>> rulesFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return aiRulesService.getActiveRulesForDepartment(departmentId);
            } catch (Exception e) {
                log.warn("AI rules unavailable for dept={}: {}", departmentId, e.getMessage());
                return List.<AiRuleDto>of();
            }
        }, aiPipelineExecutor).completeOnTimeout(List.of(), 1500, TimeUnit.MILLISECONDS);

        final String requestedConversationId = conversationId;
        CompletableFuture<ConversationState> conversationFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return loadOrCreateConversation(
                        uid, departmentId, message, requestedConversationId);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, aiPipelineExecutor);

        // Retrieval is chained onto the conversation read rather than run beside
        // it: a follow-up turn ("ha, malakam bor") only names its legal subject
        // through the turns before it, and searching it literally finds nothing.
        // The conversation is a single Firestore read, so the added wait is
        // small next to the search it steers.
        //
        // The LLM planner turns the resolved question into focused Lex.uz
        // queries. A planner timeout or failure yields an empty plan, and the
        // search then falls back to keyword extraction over the same resolved
        // text — retrieval never blocks on the planner.
        CompletableFuture<List<String>> plannerFuture = conversationFuture
                .thenApplyAsync(conversation -> legalQueryPlanner.plan(message, conversation.history()),
                        aiPipelineExecutor)
                .completeOnTimeout(List.of(), 5_000, TimeUnit.MILLISECONDS)
                .exceptionally(e -> {
                    log.warn("Legal query planner unavailable: {}", e.getMessage());
                    return List.of();
                });

        CompletableFuture<List<RagSource>> ragFuture = plannerFuture
                .thenCombineAsync(conversationFuture.exceptionally(e -> null),
                        (plannedQueries, conversation) -> {
                            String searchText = conversation == null
                                    ? message
                                    : ConversationContext.retrievalQuestion(message, conversation.history());
                            try {
                                return lexUzService.query(searchText, plannedQueries, RAG_TOP_K);
                            } catch (Exception e) {
                                log.warn("Lex.uz search unavailable: {}", e.getMessage());
                                return List.<RagSource>of();
                            }
                        }, aiPipelineExecutor)
                .completeOnTimeout(List.of(), 18_000, TimeUnit.MILLISECONDS);

        CompletableFuture.allOf(rulesFuture, ragFuture, conversationFuture)
                .get(19, TimeUnit.SECONDS);

        ConversationState conversation = conversationFuture.join();
        List<AiRuleDto> deptRules = rulesFuture.join();
        List<RagSource> evidence = ragFuture.join();

        String retrievalQuestion = ConversationContext.retrievalQuestion(message, conversation.history());

        // Live Lex.uz scraping is the primary source but can come back empty on
        // a slow/timed-out fetch even when the law exists. Fall back to the
        // pre-indexed department RAG so a transient Lex.uz failure no longer
        // forces a blanket "no normative basis" reply with the model unused.
        if (evidence.isEmpty()) {
            evidence = fallbackRagSearch(retrievalQuestion, departmentId);
        }

        // Last resort inside an ongoing conversation: answer from the documents
        // the previous turn was grounded in. A user who answers the assistant's
        // own clarifying question must never be told the basis just cited to
        // them no longer exists.
        if (evidence.isEmpty()) {
            List<RagSource> carried = conversationEvidenceCache.get(conversation.id());
            if (!carried.isEmpty()) {
                log.info("Retrieval returned nothing; reusing previous turn's evidence: conversationId={} sources={}",
                        conversation.id(), carried.size());
                evidence = carried;
            }
        }

        conversationEvidenceCache.put(conversation.id(), evidence);

        String deptRulesText = buildDepartmentRulesPrompt(deptRules, departmentId);

        List<Map<String, Object>> history = new ArrayList<>(conversation.history());
        if (history.size() > MAX_HISTORY_MESSAGES) {
            history = new ArrayList<>(history.subList(history.size() - MAX_HISTORY_MESSAGES, history.size()));
        }

        ctx.conversationId = conversation.id();
        ctx.isNew = conversation.isNew();
        ctx.history = history;
        ctx.uid = uid;
        ctx.departmentId = departmentId;
        ctx.evidence = evidence;
        ctx.citations = evidence.stream()
                .map(RagSource::citationReference)
                .distinct()
                .toList();
        ctx.departmentRules = deptRulesText;
        ctx.systemPrompt = LegalAssistantPrompt.buildGroundedPrompt(evidence, deptRulesText);

        return ctx;
    }

    /**
     * Every answer must reach the user in the same shape. When a draft breaks
     * the response contract — a missing section, no [Asos N] marker, or a
     * lex.uz link that is not in the retrieved context — one repair pass
     * rewrites it against the same evidence. A failed repair serves the draft:
     * a shape violation must never cost the user their answer.
     */
    private String repairIfMalformed(ChatContext ctx, String message, String draft) {
        if (LegalAssistantPrompt.isWellFormed(draft, ctx.evidence)) {
            return draft;
        }
        log.warn("AI answer broke the response contract; repairing: conversationId={}", ctx.conversationId);
        try {
            String repaired = aiService.chat(
                    LegalAssistantPrompt.buildRepairPrompt(ctx.evidence, ctx.departmentRules),
                    List.of(),
                    LegalAssistantPrompt.buildRepairRequest(message, draft));
            if (repaired != null && !repaired.isBlank()) {
                return repaired;
            }
        } catch (Exception e) {
            log.warn("Answer repair pass failed, serving original draft: {}", e.getMessage());
        }
        return draft;
    }

    private List<RagSource> fallbackRagSearch(String message, String departmentId) {
        try {
            List<RagSource> fallback = ragService.query(message, departmentId, RAG_TOP_K);
            if (!fallback.isEmpty()) {
                log.info("Lex.uz returned no evidence; served {} indexed RAG source(s) as fallback for dept={}",
                        fallback.size(), departmentId);
            }
            return fallback;
        } catch (Exception e) {
            log.warn("RAG fallback search failed for dept={}: {}", departmentId, e.getMessage());
            return List.of();
        }
    }

    private ConversationState loadOrCreateConversation(String uid, String departmentId,
                                                        String message, String conversationId) throws Exception {
        if (conversationId != null && !conversationId.isBlank()) {
            Map<String, Object> conversation = aiConversationRepository.findRecentById(
                    conversationId, MAX_HISTORY_MESSAGES);
            if (conversation == null) {
                throw new IllegalArgumentException("Conversation not found");
            }
            if (!uid.equals(Objects.toString(conversation.get("staffId"), ""))) {
                throw new IllegalArgumentException("Access denied");
            }

            List<Map<String, Object>> history = new ArrayList<>();
            Object stored = conversation.get("messages");
            if (stored instanceof List<?> messages) {
                for (Object item : messages) {
                    if (item instanceof Map<?, ?> raw) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> typed = (Map<String, Object>) raw;
                        history.add(typed);
                    }
                }
            }
            return new ConversationState(conversationId, history, false);
        }

        Map<String, Object> conversation = new HashMap<>();
        conversation.put("staffId", uid);
        conversation.put("departmentId", departmentId);
        conversation.put("title", truncateTitle(message));
        conversation.put("messageCount", 0);
        conversation.put("messages", new ArrayList<>());
        conversation.put("recentMessages", new ArrayList<>());
        conversation.put("createdAt", Instant.now().toString());
        conversation.put("updatedAt", Instant.now().toString());
        Map<String, Object> saved = aiConversationRepository.save(conversation);
        return new ConversationState(saved.get("id").toString(), new ArrayList<>(), true);
    }

    private String buildDepartmentRulesPrompt(List<AiRuleDto> rules, String departmentId) {
        StringBuilder text = new StringBuilder();
        int truncated = 0;
        int skipped = 0;

        for (AiRuleDto rule : rules) {
            String rawContent = rule.getContent() != null ? rule.getContent().trim() : "";
            if (rawContent.isBlank()) {
                continue;
            }

            String title = rule.getTitle() != null && !rule.getTitle().isBlank()
                    ? rule.getTitle().trim()
                    : "Untitled rule";
            if (title.length() > 120) {
                title = title.substring(0, 117) + "...";
            }

            String content = rawContent;
            if (content.length() > MAX_RULE_CONTENT_CHARS) {
                content = content.substring(0, MAX_RULE_CONTENT_CHARS).trim()
                        + " ... [truncated; long policy documents belong in Documents/RAG]";
                truncated++;
            }

            String prefix = "- " + title + ": ";
            int remaining = MAX_TOTAL_RULE_CHARS - text.length() - prefix.length() - 1;
            if (remaining < 200) {
                skipped++;
                continue;
            }
            if (content.length() > remaining) {
                content = content.substring(0, remaining).trim()
                        + " ... [truncated by prompt budget]";
                truncated++;
            }

            text.append(prefix).append(content).append("\n");
        }

        if (truncated > 0 || skipped > 0) {
            log.warn("AI rules prompt capped: dept={} activeRules={} includedChars={} truncated={} skipped={}",
                    departmentId, rules.size(), text.length(), truncated, skipped);
        }

        return text.toString();
    }

    private String saveConversation(ChatContext ctx, String userMessage, String aiResponse) throws Exception {
        conversationPersistenceService.persistTurn(
                ctx.conversationId, userMessage, aiResponse, MAX_HISTORY_MESSAGES);

        return ctx.conversationId;
    }

    private String truncateTitle(String message) {
        if (message == null || message.isEmpty()) return "New conversation";
        String cleaned = message.replaceAll("\\s+", " ").trim();
        return cleaned.length() > 60 ? cleaned.substring(0, 57) + "..." : cleaned;
    }

    private boolean isRateLimited(String uid) {
        return rateLimiterService.isRateLimited(uid, MAX_REQUESTS_PER_MINUTE);
    }

    private static class ChatContext {
        String uid;
        String departmentId;
        String conversationId;
        String systemPrompt;
        String departmentRules;
        List<Map<String, Object>> history;
        List<RagSource> evidence;
        List<String> citations;
        boolean isNew;
    }

    private record ConversationState(
            String id,
            List<Map<String, Object>> history,
            boolean isNew
    ) {}
}
