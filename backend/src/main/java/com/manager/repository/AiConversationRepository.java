package com.manager.repository;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.manager.dto.AiConversationDto;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Repository
public class AiConversationRepository {

    private final Firestore firestore;
    private static final String COLLECTION = "ai_conversations";

    public AiConversationRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public Map<String, Object> save(Map<String, Object> conversation) throws ExecutionException, InterruptedException {
        DocumentReference docRef;
        if (conversation.containsKey("id") && conversation.get("id") != null) {
            docRef = firestore.collection(COLLECTION).document(conversation.get("id").toString());
        } else {
            docRef = firestore.collection(COLLECTION).document();
            conversation.put("id", docRef.getId());
        }
        docRef.set(conversation).get();
        return conversation;
    }

    public Map<String, Object> findById(String id) throws ExecutionException, InterruptedException {
        DocumentSnapshot doc = firestore.collection(COLLECTION).document(id).get().get();
        if (!doc.exists()) return null;
        Map<String, Object> data = new HashMap<>(doc.getData());
        data.put("id", doc.getId());
        return data;
    }

    public List<Map<String, Object>> findByStaffId(String staffId) throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> future = firestore.collection(COLLECTION)
                .whereEqualTo("staffId", staffId)
                .get();
        return future.get().getDocuments().stream()
                .map(doc -> {
                    Map<String, Object> data = new HashMap<>(doc.getData());
                    data.put("id", doc.getId());
                    return data;
                })
                .collect(Collectors.toList());
    }

    /**
     * Get conversation summaries (without messages) ordered by most recent.
     */
    public List<AiConversationDto> findSummariesByStaffId(String staffId) throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> future = firestore.collection(COLLECTION)
                .whereEqualTo("staffId", staffId)
                .get();

        return future.get().getDocuments().stream()
                .map(doc -> {
                    @SuppressWarnings("unchecked")
                    List<?> messages = (List<?>) doc.get("messages");
                    int messageCount = messages != null ? messages.size() : 0;

                    return AiConversationDto.builder()
                            .id(doc.getId())
                            .staffId(doc.getString("staffId"))
                            .departmentId(doc.getString("departmentId"))
                            .title(doc.getString("title"))
                            .messageCount(messageCount)
                            .createdAt(doc.getString("createdAt"))
                            .updatedAt(doc.getString("updatedAt"))
                            .build();
                })
                .sorted((a, b) -> {
                    String aDate = a.getUpdatedAt() != null ? a.getUpdatedAt() : a.getCreatedAt();
                    String bDate = b.getUpdatedAt() != null ? b.getUpdatedAt() : b.getCreatedAt();
                    if (aDate == null || bDate == null) return 0;
                    return bDate.compareTo(aDate); // Descending
                })
                .collect(Collectors.toList());
    }

    public void update(String id, Map<String, Object> updates) throws ExecutionException, InterruptedException {
        firestore.collection(COLLECTION).document(id).update(updates).get();
    }

    public void delete(String id) throws ExecutionException, InterruptedException {
        firestore.collection(COLLECTION).document(id).delete().get();
    }
}
