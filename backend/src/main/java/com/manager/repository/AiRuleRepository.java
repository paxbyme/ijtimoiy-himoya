package com.manager.repository;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.manager.dto.AiRuleDto;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Repository
public class AiRuleRepository {

    private final Firestore firestore;
    private static final String COLLECTION = "ai_rules";

    public AiRuleRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public AiRuleDto save(AiRuleDto rule) throws ExecutionException, InterruptedException {
        DocumentReference docRef;
        if (rule.getId() != null) {
            docRef = firestore.collection(COLLECTION).document(rule.getId());
        } else {
            docRef = firestore.collection(COLLECTION).document();
            rule.setId(docRef.getId());
        }
        docRef.set(toMap(rule)).get();
        return rule;
    }

    public AiRuleDto findById(String id) throws ExecutionException, InterruptedException {
        DocumentSnapshot doc = firestore.collection(COLLECTION).document(id).get().get();
        if (!doc.exists()) return null;
        return fromDoc(doc);
    }

    public List<AiRuleDto> findByDepartmentId(String departmentId) throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> future = firestore.collection(COLLECTION)
                .whereEqualTo("departmentId", departmentId)
                .get();
        return future.get().getDocuments().stream()
                .map(this::fromDoc)
                .collect(Collectors.toList());
    }

    public List<AiRuleDto> findActiveByDepartmentId(String departmentId) throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> future = firestore.collection(COLLECTION)
                .whereEqualTo("departmentId", departmentId)
                .whereEqualTo("isActive", true)
                .get();
        return future.get().getDocuments().stream()
                .map(this::fromDoc)
                .collect(Collectors.toList());
    }

    public void update(String id, Map<String, Object> updates) throws ExecutionException, InterruptedException {
        firestore.collection(COLLECTION).document(id).update(updates).get();
    }

    public void delete(String id) throws ExecutionException, InterruptedException {
        firestore.collection(COLLECTION).document(id).delete().get();
    }

    private Map<String, Object> toMap(AiRuleDto rule) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", rule.getId());
        map.put("departmentId", rule.getDepartmentId());
        map.put("managerId", rule.getManagerId());
        map.put("title", rule.getTitle());
        map.put("content", rule.getContent());
        map.put("category", rule.getCategory());
        map.put("isActive", rule.getIsActive());
        map.put("priority", rule.getPriority());
        return map;
    }

    private AiRuleDto fromDoc(DocumentSnapshot doc) {
        return AiRuleDto.builder()
                .id(doc.getId())
                .departmentId(doc.getString("departmentId"))
                .managerId(doc.getString("managerId"))
                .title(doc.getString("title"))
                .content(doc.getString("content"))
                .category(doc.getString("category"))
                .isActive(doc.getBoolean("isActive"))
                .priority(doc.getLong("priority") != null ? doc.getLong("priority").intValue() : 0)
                .build();
    }
}
