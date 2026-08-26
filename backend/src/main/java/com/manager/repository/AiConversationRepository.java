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

    /**
     * Reads only ownership and the bounded prompt-history projection. Existing
     * conversations created before that projection was introduced fall back to
     * one full read until their next completed turn populates it.
     */
    public Map<String, Object> findRecentById(String id, int maxMessages)
            throws ExecutionException, InterruptedException {
        DocumentReference docRef = firestore.collection(COLLECTION).document(id);
        List<DocumentSnapshot> selected = firestore.getAll(
                new DocumentReference[]{docRef},
                FieldMask.of("staffId", "recentMessages")).get();
        if (selected.isEmpty() || !selected.get(0).exists()) return null;

        DocumentSnapshot snapshot = selected.get(0);
        Map<String, Object> data = new HashMap<>(snapshot.getData());
        data.put("id", snapshot.getId());
        Object recent = data.get("recentMessages");
        if (recent instanceof List<?>) {
            data.put("messages", recent);
            return data;
        }

        Map<String, Object> legacy = findById(id);
        if (legacy == null) return null;
        Object stored = legacy.get("messages");
        if (stored instanceof List<?> messages && messages.size() > maxMessages) {
            legacy.put("messages", new ArrayList<>(
                    messages.subList(messages.size() - maxMessages, messages.size())));
        }
        return legacy;
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
                            .createdAt(timestampToString(doc, "createdAt"))
                            .updatedAt(timestampToString(doc, "updatedAt"))
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

    /**
     * Atomically appends a completed chat turn. The transaction prevents
     * simultaneous requests for the same conversation from overwriting one
     * another with stale history snapshots.
     */
    public void appendMessages(String id, List<Map<String, Object>> newMessages,
                               int recentMessageLimit)
            throws ExecutionException, InterruptedException {
        DocumentReference docRef = firestore.collection(COLLECTION).document(id);
        firestore.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(docRef).get();
            if (!snapshot.exists()) {
                throw new IllegalArgumentException("Conversation not found");
            }

            List<Map<String, Object>> messages = new ArrayList<>();
            Object stored = snapshot.get("messages");
            if (stored instanceof List<?> storedMessages) {
                for (Object item : storedMessages) {
                    if (item instanceof Map<?, ?> raw) {
                        Map<String, Object> message = new HashMap<>();
                        raw.forEach((key, value) -> message.put(String.valueOf(key), value));
                        messages.add(message);
                    }
                }
            }
            for (Map<String, Object> message : newMessages) {
                messages.add(new HashMap<>(message));
            }

            int keepFrom = Math.max(0, messages.size() - Math.max(1, recentMessageLimit));
            List<Map<String, Object>> recentMessages = new ArrayList<>(
                    messages.subList(keepFrom, messages.size()));
            transaction.update(docRef, Map.of(
                    "messages", messages,
                    "recentMessages", recentMessages,
                    "messageCount", messages.size(),
                    "updatedAt", java.time.Instant.now().toString()));
            return null;
        }).get();
    }

    public void delete(String id) throws ExecutionException, InterruptedException {
        firestore.collection(COLLECTION).document(id).delete().get();
    }

    private String timestampToString(DocumentSnapshot doc, String field) {
        Object value = doc.get(field);
        if (value == null) return null;
        if (value instanceof com.google.cloud.Timestamp ts) {
            return ts.toDate().toInstant().toString();
        }
        return value.toString();
    }
}
