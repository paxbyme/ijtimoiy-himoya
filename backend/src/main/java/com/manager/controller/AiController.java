package com.manager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manager.dto.*;
import com.manager.repository.AiConversationRepository;
import com.manager.repository.AiFeedbackRepository;
import com.manager.service.AiRulesService;
import com.manager.service.GeminiService;
import com.manager.service.RagService;
import com.manager.service.RateLimiterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    private static final String GOLDEN_RULES = """
            You are an AI assistant for employees in this organization.
            Rules you MUST follow:
            1. Never reveal confidential salary, HR, or financial information.
            2. Always be professional and respectful.
            3. Do not provide legal or medical advice.
            4. If you don't know something, say so honestly.
            5. Follow all department-specific rules provided below.
            6. Base your answers on the provided context documents when available.
            7. Do not make up information that isn't in your context.
            8. Keep responses concise and actionable.
            """;

    private static final int MAX_HISTORY_MESSAGES = 20;
    private static final int MAX_REQUESTS_PER_MINUTE = 20;

    private final GeminiService geminiService;
    private final AiRulesService aiRulesService;
    private final RagService ragService;
    private final AiConversationRepository aiConversationRepository;
    private final AiFeedbackRepository aiFeedbackRepository;
    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;

    public AiController(GeminiService geminiService, AiRulesService aiRulesService,
                        RagService ragService, AiConversationRepository aiConversationRepository,
                        AiFeedbackRepository aiFeedbackRepository,
                        RateLimiterService rateLimiterService) {
        this.geminiService = geminiService;
        this.aiRulesService = aiRulesService;
        this.ragService = ragService;
        this.aiConversationRepository = aiConversationRepository;
        this.aiFeedbackRepository = aiFeedbackRepository;
        this.rateLimiterService = rateLimiterService;
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

            // Call Gemini
            String aiResponse = geminiService.chat(ctx.systemPrompt, ctx.history, request.getMessage());

            // Save conversation
            String conversationId = saveConversation(ctx, request.getMessage(), aiResponse);

            AiChatResponse response = AiChatResponse.builder()
                    .response(aiResponse)
                    .conversationId(conversationId)
                    .sources(ctx.ragSources)
                    .build();

            return ResponseEntity.ok(ApiResponse.ok(response));

        } catch (Exception e) {
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
            });
            return emitter;
        }

        CompletableFuture.runAsync(() -> {
            try {
                ChatContext ctx = buildChatContext(uid, departmentId, request.getMessage(), request.getConversationId());

                // Send meta with conversationId
                emitter.send(SseEmitter.event().data(
                        objectMapper.writeValueAsString(Map.of(
                                "type", "meta",
                                "conversationId", ctx.conversationId))));

                // Stream response
                String fullResponse = geminiService.chatStream(
                        ctx.systemPrompt, ctx.history, request.getMessage(),
                        token -> {
                            try {
                                emitter.send(SseEmitter.event().data(
                                        objectMapper.writeValueAsString(Map.of(
                                                "type", "token",
                                                "text", token))));
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });

                // Save conversation
                saveConversation(ctx, request.getMessage(), fullResponse);

                // Send done
                emitter.send(SseEmitter.event().data(
                        objectMapper.writeValueAsString(Map.of(
                                "type", "done",
                                "sources", ctx.ragSources != null ? ctx.ragSources : List.of()))));
                emitter.complete();

            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().data(
                            objectMapper.writeValueAsString(Map.of(
                                    "type", "error",
                                    "message", e.getMessage() != null ? e.getMessage() : "Unknown error"))));
                    emitter.complete();
                } catch (IOException ex) {
                    emitter.completeWithError(ex);
                }
            }
        });

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

            String transcript = geminiService.transcribeAudio(audio.getBytes(), mimeType);
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

        // Load department rules and query RAG in parallel
        CompletableFuture<List<AiRuleDto>> rulesFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return aiRulesService.getActiveRulesForDepartment(departmentId);
            } catch (Exception e) {
                return List.<AiRuleDto>of();
            }
        });

        CompletableFuture<List<String>> ragFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return ragService.query(message, departmentId, 5);
            } catch (Exception e) {
                return List.<String>of();
            }
        });

        List<AiRuleDto> deptRules = rulesFuture.get(10, TimeUnit.SECONDS);
        StringBuilder deptRulesText = new StringBuilder();
        for (AiRuleDto rule : deptRules) {
            deptRulesText.append("- ").append(rule.getTitle()).append(": ").append(rule.getContent()).append("\n");
        }

        ctx.ragSources = new ArrayList<>();
        List<String> ragContext;
        try {
            ragContext = ragFuture.get(5, TimeUnit.SECONDS);
            ctx.ragSources.addAll(ragContext);
        } catch (Exception e) {
            ragContext = new ArrayList<>();
        }

        // Build system prompt
        StringBuilder systemPrompt = new StringBuilder();
        systemPrompt.append(GOLDEN_RULES).append("\n\n");

        if (!deptRulesText.isEmpty()) {
            systemPrompt.append("Department-specific rules:\n").append(deptRulesText).append("\n\n");
        }

        if (!ragContext.isEmpty()) {
            systemPrompt.append("Relevant context from documents:\n");
            for (String context : ragContext) {
                systemPrompt.append("---\n").append(context).append("\n");
            }
            systemPrompt.append("---\n\n");
        }

        ctx.systemPrompt = systemPrompt.toString();

        // Load or create conversation
        List<Map<String, Object>> history = new ArrayList<>();

        if (conversationId != null && !conversationId.isEmpty()) {
            Map<String, Object> conversation = aiConversationRepository.findById(conversationId);
            if (conversation != null && conversation.containsKey("messages")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> storedMessages = (List<Map<String, Object>>) conversation.get("messages");
                if (storedMessages != null) {
                    history.addAll(storedMessages);
                }
            }
            ctx.conversationId = conversationId;
            ctx.isNew = false;
        } else {
            Map<String, Object> conversation = new HashMap<>();
            conversation.put("staffId", uid);
            conversation.put("departmentId", departmentId);
            conversation.put("title", truncateTitle(message));
            conversation.put("messageCount", 0);
            conversation.put("messages", new ArrayList<>());
            conversation.put("createdAt", Instant.now().toString());
            conversation.put("updatedAt", Instant.now().toString());
            conversation = aiConversationRepository.save(conversation);
            conversationId = conversation.get("id").toString();
            ctx.conversationId = conversationId;
            ctx.isNew = true;
        }

        // Truncate history to last N messages to stay within token limits
        if (history.size() > MAX_HISTORY_MESSAGES) {
            history = new ArrayList<>(history.subList(history.size() - MAX_HISTORY_MESSAGES, history.size()));
        }

        ctx.history = history;
        ctx.uid = uid;
        ctx.departmentId = departmentId;

        return ctx;
    }

    private String saveConversation(ChatContext ctx, String userMessage, String aiResponse) throws Exception {
        // Add new messages to history
        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("parts", List.of(Map.of("text", userMessage)));
        ctx.history.add(userMsg);

        Map<String, Object> assistantMsg = new HashMap<>();
        assistantMsg.put("role", "model");
        assistantMsg.put("parts", List.of(Map.of("text", aiResponse)));
        ctx.history.add(assistantMsg);

        Map<String, Object> updates = new HashMap<>();
        updates.put("messages", ctx.history);
        updates.put("messageCount", ctx.history.size());
        updates.put("updatedAt", Instant.now().toString());
        aiConversationRepository.update(ctx.conversationId, updates);

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
        List<Map<String, Object>> history;
        List<String> ragSources;
        boolean isNew;
    }
}
