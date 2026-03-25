package com.manager.repository;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Repository
public class AiFeedbackRepository {

    private final Firestore firestore;
    private static final String COLLECTION = "ai_feedback";

    public AiFeedbackRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public Map<String, Object> save(Map<String, Object> feedback) throws ExecutionException, InterruptedException {
        DocumentReference docRef = firestore.collection(COLLECTION).document();
        feedback.put("id", docRef.getId());
        docRef.set(feedback).get();
        return feedback;
    }

    public List<Map<String, Object>> findByConversationId(String conversationId) throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> future = firestore.collection(COLLECTION)
                .whereEqualTo("conversationId", conversationId)
                .get();
        return future.get().getDocuments().stream()
                .map(doc -> {
                    Map<String, Object> data = new HashMap<>(doc.getData());
                    data.put("id", doc.getId());
                    return data;
                })
                .collect(Collectors.toList());
    }

    public long countByDepartmentId(String departmentId) throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> future = firestore.collection(COLLECTION)
                .whereEqualTo("departmentId", departmentId)
                .get();
        return future.get().size();
    }
}
