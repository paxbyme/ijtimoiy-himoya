package com.manager.service;

import com.google.firebase.auth.FirebaseAuth;
import com.manager.dto.CreateDepartmentRequest;
import com.manager.dto.DepartmentDto;
import com.manager.dto.UpdateDepartmentRequest;
import com.manager.repository.DepartmentRepository;
import com.manager.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;

    public DepartmentService(DepartmentRepository departmentRepository, UserRepository userRepository) {
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
    }

    public DepartmentDto create(CreateDepartmentRequest req) throws Exception {
        String now = Instant.now().toString();
        DepartmentDto dept = DepartmentDto.builder()
                .name(req.getName())
                .managerId(req.getManagerId())
                .createdAt(now)
                .updatedAt(now)
                .build();
        DepartmentDto saved = departmentRepository.save(dept);
        if (req.getManagerId() != null && !req.getManagerId().isEmpty()) {
            assignManager(saved.getId(), req.getManagerId());
        }
        return saved;
    }

    public List<DepartmentDto> listAll() throws Exception {
        return departmentRepository.findAll();
    }

    public DepartmentDto getById(String id) throws Exception {
        return departmentRepository.findById(id);
    }

    public DepartmentDto update(String id, UpdateDepartmentRequest req) throws Exception {
        Map<String, Object> updates = new HashMap<>();
        if (req.getName() != null)      updates.put("name", req.getName());
        if (req.getManagerId() != null) updates.put("managerId", req.getManagerId());
        updates.put("updatedAt", Instant.now().toString());
        if (!updates.isEmpty()) departmentRepository.update(id, updates);
        if (req.getManagerId() != null && !req.getManagerId().isEmpty()) {
            assignManager(id, req.getManagerId());
        }
        return departmentRepository.findById(id);
    }

    public void delete(String id) throws Exception {
        if (!userRepository.findByDepartmentId(id).isEmpty()) {
            throw new IllegalStateException("Cannot delete department with active staff");
        }
        boolean hasManager = userRepository.findByRole("MANAGER").stream()
                .anyMatch(m -> id.equals(m.getDepartmentId()));
        if (hasManager) {
            throw new IllegalStateException("Cannot delete department that still has a manager");
        }
        departmentRepository.delete(id);
    }

    public void removeManagerFromAllDepartments(String managerId) throws Exception {
        List<DepartmentDto> all = departmentRepository.findAll();
        for (DepartmentDto dept : all) {
            if (managerId.equals(dept.getManagerId())) {
                departmentRepository.update(dept.getId(), Map.of("managerId", ""));
            }
        }
    }

    private void assignManager(String departmentId, String managerId) throws Exception {
        Map<String, Object> userUpdates = new HashMap<>();
        userUpdates.put("departmentId", departmentId);
        userRepository.update(managerId, userUpdates);

        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "MANAGER");
        claims.put("departmentId", departmentId);
        FirebaseAuth.getInstance().setCustomUserClaims(managerId, claims);
    }
}
