package com.manager.service;

import com.manager.dto.ChatMessageDto;
import com.manager.dto.ConversationDto;
import com.manager.dto.SendMessageRequest;
import com.manager.repository.ChatRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class ChatService {

    private final ChatRepository chatRepository;

    public ChatService(ChatRepository chatRepository) {
        this.chatRepository = chatRepository;
    }

    public ChatMessageDto sendMessage(SendMessageRequest req, String senderId) throws Exception {
        // Deterministic conversation ID: sorted participant IDs joined with "_"
        List<String> ids = Arrays.asList(senderId, req.getReceiverId());
        Collections.sort(ids);
        String conversationId = ids.get(0) + "_" + ids.get(1);

        // Find or create conversation using the deterministic ID
        ConversationDto conversation = chatRepository.findConversationById(conversationId);
        if (conversation == null) {
            conversation = ConversationDto.builder()
                    .id(conversationId)
                    .participants(ids)
                    .lastMessage(req.getContent())
                    .lastMessageAt(Instant.now().toString())
                    .unreadCount(Map.of(req.getReceiverId(), 1))
                    .build();
            chatRepository.saveConversation(conversation);
        } else {
            Map<String, Object> updates = new HashMap<>();
            updates.put("lastMessage", req.getContent());
            updates.put("lastMessageAt", Instant.now().toString());
            // Increment receiver's unread count
            @SuppressWarnings("unchecked")
            Map<String, Object> currentUnread = conversation.getUnreadCount() instanceof Map
                    ? new HashMap<>((Map<String, Object>) conversation.getUnreadCount())
                    : new HashMap<>();
            int prev = currentUnread.containsKey(req.getReceiverId())
                    ? ((Number) currentUnread.get(req.getReceiverId())).intValue() : 0;
            currentUnread.put(req.getReceiverId(), prev + 1);
            updates.put("unreadCount", currentUnread);
            chatRepository.updateConversation(conversationId, updates);
        }

        // Save message with the deterministic conversationId
        ChatMessageDto message = ChatMessageDto.builder()
                .conversationId(conversationId)
                .senderId(senderId)
                .receiverId(req.getReceiverId())
                .content(req.getContent())
                .type("TEXT")
                .createdAt(Instant.now().toString())
                .build();

        return chatRepository.saveMessage(message);
    }

    public List<ConversationDto> getConversations(String userId) throws Exception {
        return chatRepository.findConversationsByUserId(userId);
    }

    public List<ChatMessageDto> getMessages(String conversationId) throws Exception {
        return chatRepository.findMessagesByConversationId(conversationId);
    }
}
