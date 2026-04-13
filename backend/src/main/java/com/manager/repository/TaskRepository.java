package com.manager.repository;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.manager.dto.TaskDto;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Repository
public class TaskRepository {

    private final Firestore firestore;
    private static final String COLLECTION = "tasks";

    public TaskRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public TaskDto save(TaskDto task) throws ExecutionException, InterruptedException {
        DocumentReference docRef;
        if (task.getId() != null) {
            docRef = firestore.collection(COLLECTION).document(task.getId());
        } else {
            docRef = firestore.collection(COLLECTION).document();
            task.setId(docRef.getId());
        }
        docRef.set(toMap(task)).get();
        return task;
    }

    public TaskDto findById(String id) throws ExecutionException, InterruptedException {
        DocumentSnapshot doc = firestore.collection(COLLECTION).document(id).get().get();
        if (!doc.exists()) return null;
        return fromDoc(doc);
    }

    public List<TaskDto> findByDepartmentId(String departmentId) throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> future = firestore.collection(COLLECTION)
                .whereEqualTo("departmentId", departmentId)
                .get();
        return future.get().getDocuments().stream()
                .map(this::fromDoc)
                .collect(Collectors.toList());
    }

    public List<TaskDto> findByAssignedTo(String staffId) throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> future = firestore.collection(COLLECTION)
                .whereEqualTo("assignedTo", staffId)
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

    public List<TaskDto> findCompletedByStaffAndPeriod(String staffId, String periodStart, String periodEnd)
            throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> future = firestore.collection(COLLECTION)
                .whereEqualTo("assignedTo", staffId)
                .whereEqualTo("status", "COMPLETED")
                .whereGreaterThanOrEqualTo("createdAt", periodStart)
                .whereLessThanOrEqualTo("createdAt", periodEnd)
                .get();
        return future.get().getDocuments().stream()
                .map(this::fromDoc)
                .collect(Collectors.toList());
    }

    public List<TaskDto> findByAssignedToAndPeriod(String staffId, String periodStart, String periodEnd)
            throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> future = firestore.collection(COLLECTION)
                .whereEqualTo("assignedTo", staffId)
                .whereGreaterThanOrEqualTo("createdAt", periodStart)
                .whereLessThanOrEqualTo("createdAt", periodEnd)
                .get();
        return future.get().getDocuments().stream()
                .map(this::fromDoc)
                .collect(Collectors.toList());
    }

    private Map<String, Object> toMap(TaskDto task) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", task.getId());
        map.put("title", task.getTitle());
        map.put("description", task.getDescription());
        map.put("assignedTo", task.getAssignedTo());
        map.put("assignedBy", task.getAssignedBy());
        map.put("departmentId", task.getDepartmentId());
        map.put("status", task.getStatus());
        map.put("priority", task.getPriority());
        map.put("deadline", task.getDeadline());
        map.put("completedAt", task.getCompletedAt());
        map.put("createdAt", task.getCreatedAt());
        map.put("assigneeName", task.getAssigneeName());
        map.put("attachmentUrl", task.getAttachmentUrl());
        map.put("attachmentName", task.getAttachmentName());
        map.put("managerAccepted", task.getManagerAccepted() != null ? task.getManagerAccepted() : false);
        map.put("attachments", task.getAttachments() != null ? task.getAttachments() : new ArrayList<>());
        return map;
    }

    @SuppressWarnings("unchecked")
    private TaskDto fromDoc(DocumentSnapshot doc) {
        // Parse attachments list (new format)
        List<Map<String, String>> attachments = new ArrayList<>();
        Object attList = doc.get("attachments");
        if (attList instanceof List<?> rawList) {
            for (Object item : rawList) {
                if (item instanceof Map<?, ?> rawMap) {
                    Map<String, String> att = new HashMap<>();
                    if (rawMap.get("url") != null) att.put("url", rawMap.get("url").toString());
                    if (rawMap.get("name") != null) att.put("name", rawMap.get("name").toString());
                    if (att.containsKey("url")) attachments.add(att);
                }
            }
        }
        // Backward compat: single attachment fields
        String legacyUrl = doc.getString("attachmentUrl");
        if (attachments.isEmpty() && legacyUrl != null) {
            Map<String, String> att = new HashMap<>();
            att.put("url", legacyUrl);
            String legacyName = doc.getString("attachmentName");
            att.put("name", legacyName != null ? legacyName : "Fayl");
            attachments.add(att);
        }

        return TaskDto.builder()
                .id(doc.getId())
                .title(doc.getString("title"))
                .description(doc.getString("description"))
                .assignedTo(doc.getString("assignedTo"))
                .assignedBy(doc.getString("assignedBy"))
                .departmentId(doc.getString("departmentId"))
                .status(doc.getString("status"))
                .priority(doc.getString("priority"))
                .deadline(timestampToString(doc, "deadline"))
                .completedAt(timestampToString(doc, "completedAt"))
                .createdAt(timestampToString(doc, "createdAt"))
                .assigneeName(doc.getString("assigneeName"))
                .attachmentUrl(attachments.isEmpty() ? null : attachments.get(0).get("url"))
                .attachmentName(attachments.isEmpty() ? null : attachments.get(0).get("name"))
                .managerAccepted(doc.getBoolean("managerAccepted"))
                .attachments(attachments)
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
