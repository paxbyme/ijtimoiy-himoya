package com.manager.repository;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.manager.dto.DepartmentDto;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Repository
public class DepartmentRepository {

    private final Firestore firestore;
    private static final String COLLECTION = "departments";

    public DepartmentRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public DepartmentDto save(DepartmentDto dept) throws ExecutionException, InterruptedException {
        DocumentReference docRef;
        if (dept.getId() == null || dept.getId().isEmpty()) {
            docRef = firestore.collection(COLLECTION).document();
            dept.setId(docRef.getId());
        } else {
            docRef = firestore.collection(COLLECTION).document(dept.getId());
        }
        docRef.set(toMap(dept)).get();
        return dept;
    }

    public DepartmentDto findById(String id) throws ExecutionException, InterruptedException {
        DocumentSnapshot doc = firestore.collection(COLLECTION).document(id).get().get();
        if (!doc.exists()) return null;
        return fromDoc(doc);
    }

    public List<DepartmentDto> findAll() throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> future = firestore.collection(COLLECTION).get();
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

    private Map<String, Object> toMap(DepartmentDto d) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", d.getId());
        map.put("name", d.getName());
        map.put("managerId", d.getManagerId());
        map.put("createdAt", d.getCreatedAt());
        map.put("updatedAt", d.getUpdatedAt());
        return map;
    }

    private DepartmentDto fromDoc(DocumentSnapshot doc) {
        return DepartmentDto.builder()
                .id(doc.getId())
                .name(doc.getString("name"))
                .managerId(doc.getString("managerId"))
                .createdAt(timestampToString(doc, "createdAt"))
                .updatedAt(timestampToString(doc, "updatedAt"))
                .build();
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
