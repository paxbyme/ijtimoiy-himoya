package com.manager.service;

import com.google.firebase.auth.FirebaseAuth;
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
        // Create Firebase Auth user with email = phone@manager.local
        String email = req.getPhone() + "@manager.local";
        UserRecord.CreateRequest createRequest = new UserRecord.CreateRequest()
                .setEmail(email)
                .setPassword(req.getPassword())
                .setDisplayName(req.getDisplayName());

        UserRecord userRecord = FirebaseAuth.getInstance().createUser(createRequest);
        String uid = userRecord.getUid();

        // Set custom claims
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", req.getRole() != null ? req.getRole() : "STAFF");
        claims.put("departmentId", departmentId);
        FirebaseAuth.getInstance().setCustomUserClaims(uid, claims);

        // Save to Firestore
        UserDto user = UserDto.builder()
                .id(uid)
                .phone(req.getPhone())
                .name(req.getDisplayName())
                .role(req.getRole() != null ? req.getRole() : "STAFF")
                .departmentId(departmentId)
                .managerId(managerId)
                .isActive(true)
                .createdAt(Instant.now().toString())
                .build();

        return userRepository.save(user);
    }

    public List<UserDto> getStaffByDepartment(String departmentId) throws Exception {
        return userRepository.findByDepartmentId(departmentId);
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
}
