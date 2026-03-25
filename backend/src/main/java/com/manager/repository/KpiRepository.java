package com.manager.repository;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.manager.dto.KpiDto;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Repository
public class KpiRepository {

    private final Firestore firestore;
    private static final String COLLECTION = "kpi_scores";

    public KpiRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public KpiDto save(KpiDto kpi) throws ExecutionException, InterruptedException {
        DocumentReference docRef;
        if (kpi.getId() != null) {
            docRef = firestore.collection(COLLECTION).document(kpi.getId());
        } else {
            docRef = firestore.collection(COLLECTION).document();
            kpi.setId(docRef.getId());
        }
        docRef.set(toMap(kpi)).get();
        return kpi;
    }

    public List<KpiDto> findByStaffId(String staffId) throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> future = firestore.collection(COLLECTION)
                .whereEqualTo("staffId", staffId)
                .get();
        return future.get().getDocuments().stream()
                .map(this::fromDoc)
                .collect(Collectors.toList());
    }

    public List<KpiDto> findByDepartmentIdAndPeriod(String departmentId, String period) throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> future = firestore.collection(COLLECTION)
                .whereEqualTo("departmentId", departmentId)
                .whereEqualTo("period", period)
                .get();
        return future.get().getDocuments().stream()
                .map(this::fromDoc)
                .collect(Collectors.toList());
    }

    public KpiDto findByStaffIdAndPeriod(String staffId, String period) throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> future = firestore.collection(COLLECTION)
                .whereEqualTo("staffId", staffId)
                .whereEqualTo("period", period)
                .limit(1)
                .get();
        List<QueryDocumentSnapshot> docs = future.get().getDocuments();
        if (docs.isEmpty()) return null;
        return fromDoc(docs.get(0));
    }

    public void update(String id, Map<String, Object> updates) throws ExecutionException, InterruptedException {
        firestore.collection(COLLECTION).document(id).update(updates).get();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(KpiDto kpi) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", kpi.getId());
        map.put("staffId", kpi.getStaffId());
        map.put("staffName", kpi.getStaffName());
        map.put("departmentId", kpi.getDepartmentId());
        map.put("period", kpi.getPeriod());
        map.put("score", kpi.getScore());
        map.put("rank", kpi.getRank());
        map.put("breakdown", kpi.getBreakdown());
        return map;
    }

    @SuppressWarnings("unchecked")
    private KpiDto fromDoc(DocumentSnapshot doc) {
        Map<String, Double> breakdown = new HashMap<>();
        Object breakdownObj = doc.get("breakdown");
        if (breakdownObj instanceof Map) {
            Map<String, Object> rawBreakdown = (Map<String, Object>) breakdownObj;
            rawBreakdown.forEach((k, v) -> {
                if (v instanceof Number) {
                    breakdown.put(k, ((Number) v).doubleValue());
                }
            });
        }

        return KpiDto.builder()
                .id(doc.getId())
                .staffId(doc.getString("staffId"))
                .staffName(doc.getString("staffName"))
                .departmentId(doc.getString("departmentId"))
                .period(doc.getString("period"))
                .score(doc.getDouble("score"))
                .rank(doc.getLong("rank") != null ? doc.getLong("rank").intValue() : null)
                .breakdown(breakdown)
                .build();
    }
}
