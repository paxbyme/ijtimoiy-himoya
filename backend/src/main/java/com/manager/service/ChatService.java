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
        // Find or create conversation
        ConversationDto conversation = chatRepository.findConversationByParticipants(senderId, req.getReceiverId());
        if (conversation == null) {
            conversation = ConversationDto.builder()
                    .participants(Arrays.asList(senderId, req.getReceiverId()))
                    .lastMessage(req.getContent())
                    .lastMessageAt(Instant.now().toString())
                    .unreadCount(1)
                    .build();
            conversation = chatRepository.saveConversation(conversation);
        } else {
            Map<String, Object> updates = new HashMap<>();
            updates.put("lastMessage", req.getContent());
            updates.put("lastMessageAt", Instant.now().toString());
            updates.put("unreadCount", (conversation.getUnreadCount() != null ? conversation.getUnreadCount() : 0) + 1);
            chatRepository.updateConversation(conversation.getId(), updates);
        }

        // Save message
        ChatMessageDto message = ChatMessageDto.builder()
                .conversationId(conversation.getId())
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
