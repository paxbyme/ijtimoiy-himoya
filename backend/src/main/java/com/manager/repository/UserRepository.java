package com.manager.repository;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.manager.dto.UserDto;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Repository
public class UserRepository {

    private final Firestore firestore;
    private static final String COLLECTION = "users";

    public UserRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public UserDto save(UserDto user) throws ExecutionException, InterruptedException {
        DocumentReference docRef = firestore.collection(COLLECTION).document(user.getId());
        docRef.set(toMap(user)).get();
        return user;
    }

    public UserDto findById(String id) throws ExecutionException, InterruptedException {
        DocumentSnapshot doc = firestore.collection(COLLECTION).document(id).get().get();
        if (!doc.exists()) return null;
        return fromDoc(doc);
    }

    public List<UserDto> findByDepartmentId(String departmentId) throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> future = firestore.collection(COLLECTION)
                .whereEqualTo("departmentId", departmentId)
                .whereEqualTo("role", "STAFF")
                .get();
        return future.get().getDocuments().stream()
                .map(this::fromDoc)
                .collect(Collectors.toList());
    }

    public UserDto findByPhone(String phone) throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> future = firestore.collection(COLLECTION)
                .whereEqualTo("phone", phone)
                .limit(1)
                .get();
        List<QueryDocumentSnapshot> docs = future.get().getDocuments();
        if (docs.isEmpty()) return null;
        return fromDoc(docs.get(0));
    }

    public void update(String id, Map<String, Object> updates) throws ExecutionException, InterruptedException {
        firestore.collection(COLLECTION).document(id).update(updates).get();
    }

    public void delete(String id) throws ExecutionException, InterruptedException {
        firestore.collection(COLLECTION).document(id).delete().get();
    }

    private Map<String, Object> toMap(UserDto user) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("phone", user.getPhone());
        map.put("name", user.getName());
        map.put("role", user.getRole());
        map.put("departmentId", user.getDepartmentId());
        map.put("managerId", user.getManagerId());
        map.put("isActive", user.getIsActive());
        map.put("createdAt", user.getCreatedAt());
        return map;
    }

    private UserDto fromDoc(DocumentSnapshot doc) {
        return UserDto.builder()
                .id(doc.getId())
                .phone(doc.getString("phone"))
                .name(doc.getString("name"))
                .role(doc.getString("role"))
                .departmentId(doc.getString("departmentId"))
                .managerId(doc.getString("managerId"))
                .isActive(doc.getBoolean("isActive"))
                .createdAt(timestampToString(doc, "createdAt"))
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
