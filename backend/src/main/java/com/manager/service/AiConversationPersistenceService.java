package com.manager.service;

import com.manager.repository.AiConversationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Keeps Firestore conversation writes outside the streaming response path. */
@Service
public class AiConversationPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(AiConversationPersistenceService.class);

    private final AiConversationRepository conversationRepository;

    public AiConversationPersistenceService(AiConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
    }

    public void persistTurn(String conversationId, String userMessage,
                            String assistantResponse, int recentMessageLimit) throws Exception {
        conversationRepository.appendMessages(
                conversationId,
                turnMessages(userMessage, assistantResponse),
                recentMessageLimit);
    }

    @Async("aiPersistenceExecutor")
    public void persistTurnAsync(String conversationId, String userMessage,
                                 String assistantResponse, int recentMessageLimit) {
        try {
            persistTurn(conversationId, userMessage, assistantResponse, recentMessageLimit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Conversation persistence interrupted: conversationId={}", conversationId);
        } catch (Exception e) {
            log.error("Conversation persistence failed: conversationId={}", conversationId, e);
        }
    }

    private List<Map<String, Object>> turnMessages(String userMessage, String assistantResponse) {
        return List.of(
                Map.of("role", "user", "parts", List.of(Map.of("text", userMessage))),
                Map.of("role", "model", "parts", List.of(Map.of("text", assistantResponse))));
    }
}
