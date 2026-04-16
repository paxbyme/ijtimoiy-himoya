package com.manager.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.manager.dto.CreateUserRequest;
import com.manager.dto.UpdateUserRequest;
import com.manager.dto.UserDto;
import com.manager.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserDto createStaff(CreateUserRequest req, String managerId, String departmentId) throws Exception {
        // If JWT token departmentId is stale/empty, fall back to manager's Firestore doc
        String resolvedDepartmentId = departmentId;
        if (resolvedDepartmentId == null || resolvedDepartmentId.isEmpty()) {
            UserDto manager = userRepository.findById(managerId);
            if (manager != null && manager.getDepartmentId() != null && !manager.getDepartmentId().isEmpty()) {
                resolvedDepartmentId = manager.getDepartmentId();
            }
        }

        String email = req.getPhone() + "@manager.local";
        String uid;

        try {
            // Try to create a new Firebase Auth user
            UserRecord.CreateRequest createRequest = new UserRecord.CreateRequest()
                    .setEmail(email)
                    .setPassword(req.getPassword())
                    .setDisplayName(req.getDisplayName());
            UserRecord userRecord = FirebaseAuth.getInstance().createUser(createRequest);
            uid = userRecord.getUid();
        } catch (FirebaseAuthException e) {
            if (!"EMAIL_EXISTS".equals(e.getAuthErrorCode().name())) {
                throw e;
            }
            // User already exists in Firebase Auth (previously soft-deleted) — reuse their UID
            UserRecord existing = FirebaseAuth.getInstance().getUserByEmail(email);
            uid = existing.getUid();
            // Update password and display name in case they changed
            FirebaseAuth.getInstance().updateUser(
                    new UserRecord.UpdateRequest(uid)
                            .setPassword(req.getPassword())
                            .setDisplayName(req.getDisplayName())
                            .setDisabled(false)
            );
        }

        // Set custom claims — role is clamped to STAFF regardless of request value
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "STAFF");
        claims.put("departmentId", resolvedDepartmentId);
        FirebaseAuth.getInstance().setCustomUserClaims(uid, claims);

        // Save (or overwrite) Firestore document
        UserDto user = UserDto.builder()
                .id(uid)
                .phone(req.getPhone())
                .name(req.getDisplayName())
                .role("STAFF")
                .departmentId(resolvedDepartmentId)
                .managerId(managerId)
                .isActive(true)
                .createdAt(Instant.now().toString())
                .build();

        return userRepository.save(user);
    }

    public List<UserDto> getStaffByDepartment(String managerId, String departmentId) throws Exception {
        String resolvedDepartmentId = departmentId;
        if (resolvedDepartmentId == null || resolvedDepartmentId.isEmpty()) {
            UserDto manager = userRepository.findById(managerId);
            if (manager != null && manager.getDepartmentId() != null && !manager.getDepartmentId().isEmpty()) {
                resolvedDepartmentId = manager.getDepartmentId();
            }
        }
        return userRepository.findByDepartmentId(resolvedDepartmentId);
    }

    public UserDto getStaffById(String id) throws Exception {
        return userRepository.findById(id);
    }

    public UserDto updateStaff(String id, UpdateUserRequest req) throws Exception {
        Map<String, Object> updates = new HashMap<>();
        if (req.getDisplayName() != null) {
            updates.put("name", req.getDisplayName());
        }
        if (req.getIsActive() != null) {
            updates.put("isActive", req.getIsActive());
        }
        if (!updates.isEmpty()) {
            userRepository.update(id, updates);
        }
        return userRepository.findById(id);
    }

    public void deleteStaff(String id) throws Exception {
        Map<String, Object> updates = new HashMap<>();
        updates.put("isActive", false);
        userRepository.update(id, updates);
    }

    // ── DEVELOPER / admin operations ──

    public UserDto createManager(CreateUserRequest req) throws Exception {
        String email = req.getPhone() + "@manager.local";
        UserRecord.CreateRequest createRequest = new UserRecord.CreateRequest()
                .setEmail(email)
                .setPassword(req.getPassword())
                .setDisplayName(req.getDisplayName());

        UserRecord userRecord = FirebaseAuth.getInstance().createUser(createRequest);
        String uid = userRecord.getUid();
        String deptId = req.getDepartmentId() != null ? req.getDepartmentId() : "";

        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "MANAGER");
        claims.put("departmentId", deptId);
        FirebaseAuth.getInstance().setCustomUserClaims(uid, claims);

        UserDto user = UserDto.builder()
                .id(uid)
                .phone(req.getPhone())
                .name(req.getDisplayName())
                .role("MANAGER")
                .departmentId(deptId)
                .managerId(null)
                .isActive(true)
                .createdAt(Instant.now().toString())
                .build();

        return userRepository.save(user);
    }

    public List<UserDto> listAllManagers() throws Exception {
        return userRepository.findByRole("MANAGER");
    }

    public UserDto updateManager(String id, UpdateUserRequest req) throws Exception {
        Map<String, Object> updates = new HashMap<>();
        if (req.getDisplayName() != null) updates.put("name", req.getDisplayName());
        if (req.getIsActive() != null)     updates.put("isActive", req.getIsActive());
        if (!updates.isEmpty()) userRepository.update(id, updates);
        return userRepository.findById(id);
    }

    public void softDeleteManager(String id) throws Exception {
        userRepository.update(id, Map.of("isActive", false));
        FirebaseAuth.getInstance().updateUser(new UserRecord.UpdateRequest(id).setDisabled(true));
    }

    public void hardDeleteManager(String id) throws Exception {
        userRepository.delete(id);
        FirebaseAuth.getInstance().deleteUser(id);
    }
}
