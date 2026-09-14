package com.manager.service;

import com.manager.repository.AiConversationRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Appends completed chat turns to their Firestore conversation. */
@Service
public class AiConversationPersistenceService {

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

    private List<Map<String, Object>> turnMessages(String userMessage, String assistantResponse) {
        return List.of(
                Map.of("role", "user", "parts", List.of(Map.of("text", userMessage))),
                Map.of("role", "model", "parts", List.of(Map.of("text", assistantResponse))));
    }
}
