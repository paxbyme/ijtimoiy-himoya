package com.manager.repository;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.manager.dto.DocumentDto;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Repository
public class DocumentRepository {

    private final Firestore firestore;
    private static final String COLLECTION = "documents";
    private static final String CHUNKS_COLLECTION = "document_chunks";

    public DocumentRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public DocumentDto save(DocumentDto document) throws ExecutionException, InterruptedException {
        DocumentReference docRef;
        if (document.getId() != null) {
            docRef = firestore.collection(COLLECTION).document(document.getId());
        } else {
            docRef = firestore.collection(COLLECTION).document();
            document.setId(docRef.getId());
        }
        docRef.set(toMap(document)).get();
        return document;
    }

    public DocumentDto findById(String id) throws ExecutionException, InterruptedException {
        DocumentSnapshot doc = firestore.collection(COLLECTION).document(id).get().get();
        if (!doc.exists()) return null;
        return fromDoc(doc);
    }

    public List<DocumentDto> findByDepartmentId(String departmentId) throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> future = firestore.collection(COLLECTION)
                .whereEqualTo("departmentId", departmentId)
                .get();
        return future.get().getDocuments().stream()
                .map(this::fromDoc)
                .collect(Collectors.toList());
    }

    public void saveChunk(Map<String, Object> chunk) throws ExecutionException, InterruptedException {
        DocumentReference docRef;
        if (chunk.containsKey("id") && chunk.get("id") != null) {
            docRef = firestore.collection(CHUNKS_COLLECTION).document(chunk.get("id").toString());
        } else {
            docRef = firestore.collection(CHUNKS_COLLECTION).document();
            chunk.put("id", docRef.getId());
        }
        docRef.set(chunk).get();
    }

    public List<Map<String, Object>> findChunksByDocumentId(String documentId) throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> future = firestore.collection(CHUNKS_COLLECTION)
                .whereEqualTo("documentId", documentId)
                .get();
        return future.get().getDocuments().stream()
                .map(doc -> {
                    Map<String, Object> data = new HashMap<>(doc.getData());
                    data.put("id", doc.getId());
                    return data;
                })
                .collect(Collectors.toList());
    }

    public Map<String, Object> findChunkByVectorId(String vectorId) throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> future = firestore.collection(CHUNKS_COLLECTION)
                .whereEqualTo("vectorId", vectorId)
                .limit(1)
                .get();
        List<QueryDocumentSnapshot> docs = future.get().getDocuments();
        if (docs.isEmpty()) return null;
        Map<String, Object> data = new HashMap<>(docs.get(0).getData());
        data.put("id", docs.get(0).getId());
        return data;
    }

    public void delete(String id) throws ExecutionException, InterruptedException {
        // Delete chunks first
        List<Map<String, Object>> chunks = findChunksByDocumentId(id);
        for (Map<String, Object> chunk : chunks) {
            firestore.collection(CHUNKS_COLLECTION).document(chunk.get("id").toString()).delete().get();
        }
        // Delete document
        firestore.collection(COLLECTION).document(id).delete().get();
    }

    private Map<String, Object> toMap(DocumentDto doc) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", doc.getId());
        map.put("departmentId", doc.getDepartmentId());
        map.put("uploadedBy", doc.getUploadedBy());
        map.put("title", doc.getTitle());
        map.put("fileName", doc.getFileName());
        map.put("storageUrl", doc.getStorageUrl());
        map.put("status", doc.getStatus());
        map.put("createdAt", doc.getCreatedAt());
        return map;
    }

    private DocumentDto fromDoc(DocumentSnapshot doc) {
        return DocumentDto.builder()
                .id(doc.getId())
                .departmentId(doc.getString("departmentId"))
                .uploadedBy(doc.getString("uploadedBy"))
                .title(doc.getString("title"))
                .fileName(doc.getString("fileName"))
                .storageUrl(doc.getString("storageUrl"))
                .status(doc.getString("status"))
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
