package com.manager.repository;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.manager.dto.ChatMessageDto;
import com.manager.dto.ConversationDto;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Repository
public class ChatRepository {

    private final Firestore firestore;
    private static final String MESSAGES_COLLECTION = "messages";
    private static final String CONVERSATIONS_COLLECTION = "conversations";

    public ChatRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public ChatMessageDto saveMessage(ChatMessageDto message) throws ExecutionException, InterruptedException {
        DocumentReference docRef;
        if (message.getId() != null) {
            docRef = firestore.collection(MESSAGES_COLLECTION).document(message.getId());
        } else {
            docRef = firestore.collection(MESSAGES_COLLECTION).document();
            message.setId(docRef.getId());
        }
        docRef.set(messageToMap(message)).get();
        return message;
    }

    public List<ChatMessageDto> findMessagesByConversationId(String conversationId) throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> future = firestore.collection(MESSAGES_COLLECTION)
                .whereEqualTo("conversationId", conversationId)
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .get();
        return future.get().getDocuments().stream()
                .map(this::messageFromDoc)
                .collect(Collectors.toList());
    }

    public ConversationDto saveConversation(ConversationDto conversation) throws ExecutionException, InterruptedException {
        DocumentReference docRef;
        if (conversation.getId() != null) {
            docRef = firestore.collection(CONVERSATIONS_COLLECTION).document(conversation.getId());
        } else {
            docRef = firestore.collection(CONVERSATIONS_COLLECTION).document();
            conversation.setId(docRef.getId());
        }
        docRef.set(conversationToMap(conversation)).get();
        return conversation;
    }

    @SuppressWarnings("unchecked")
    public List<ConversationDto> findConversationsByUserId(String userId) throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> future = firestore.collection(CONVERSATIONS_COLLECTION)
                .whereArrayContains("participants", userId)
                .get();
        return future.get().getDocuments().stream()
                .map(this::conversationFromDoc)
                .collect(Collectors.toList());
    }

    public void updateConversation(String id, Map<String, Object> updates) throws ExecutionException, InterruptedException {
        firestore.collection(CONVERSATIONS_COLLECTION).document(id).update(updates).get();
    }

    public ConversationDto findConversationByParticipants(String userId1, String userId2) throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> future = firestore.collection(CONVERSATIONS_COLLECTION)
                .whereArrayContains("participants", userId1)
                .get();
        List<QueryDocumentSnapshot> docs = future.get().getDocuments();
        for (QueryDocumentSnapshot doc : docs) {
            @SuppressWarnings("unchecked")
            List<String> participants = (List<String>) doc.get("participants");
            if (participants != null && participants.contains(userId2)) {
                return conversationFromDoc(doc);
            }
        }
        return null;
    }

    private Map<String, Object> messageToMap(ChatMessageDto msg) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", msg.getId());
        map.put("conversationId", msg.getConversationId());
        map.put("senderId", msg.getSenderId());
        map.put("receiverId", msg.getReceiverId());
        map.put("content", msg.getContent());
        map.put("type", msg.getType());
        map.put("createdAt", msg.getCreatedAt());
        map.put("readAt", msg.getReadAt());
        return map;
    }

    private ChatMessageDto messageFromDoc(DocumentSnapshot doc) {
        return ChatMessageDto.builder()
                .id(doc.getId())
                .conversationId(doc.getString("conversationId"))
                .senderId(doc.getString("senderId"))
                .receiverId(doc.getString("receiverId"))
                .content(doc.getString("content"))
                .type(doc.getString("type"))
                .createdAt(doc.getString("createdAt"))
                .readAt(doc.getString("readAt"))
                .build();
    }

    private Map<String, Object> conversationToMap(ConversationDto conv) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", conv.getId());
        map.put("participants", conv.getParticipants());
        map.put("lastMessage", conv.getLastMessage());
        map.put("lastMessageAt", conv.getLastMessageAt());
        map.put("unreadCount", conv.getUnreadCount());
        return map;
    }

    @SuppressWarnings("unchecked")
    private ConversationDto conversationFromDoc(DocumentSnapshot doc) {
        return ConversationDto.builder()
                .id(doc.getId())
                .participants((List<String>) doc.get("participants"))
                .lastMessage(doc.getString("lastMessage"))
                .lastMessageAt(doc.getString("lastMessageAt"))
                .unreadCount(doc.getLong("unreadCount") != null ? doc.getLong("unreadCount").intValue() : 0)
                .build();
    }
}
