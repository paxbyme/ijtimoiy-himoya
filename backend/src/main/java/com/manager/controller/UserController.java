package com.manager.controller;

import com.manager.dto.*;
import com.manager.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/staff")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ApiResponse<UserDto>> createStaff(
            @Valid @RequestBody CreateUserRequest request,
            HttpServletRequest httpRequest) {
        try {
            String managerId = (String) httpRequest.getAttribute("uid");
            String departmentId = (String) httpRequest.getAttribute("departmentId");
            UserDto user = userService.createStaff(request, managerId, departmentId);
            return ResponseEntity.ok(ApiResponse.ok("Staff created", user));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to create staff: " + e.getMessage()));
        }
    }

    /**
     * GET /api/users/staff?page=0&size=20
     * Returns PageResponse<UserDto>.
     * NOTE: clients must read `.content` instead of the raw array.
     */
    @GetMapping("/staff")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ApiResponse<PageResponse<UserDto>>> listStaff(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        try {
            String managerId = (String) request.getAttribute("uid");
            String departmentId = (String) request.getAttribute("departmentId");
            List<UserDto> staff = userService.getStaffByDepartment(managerId, departmentId);
            return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(staff, page, size)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to list staff: " + e.getMessage()));
        }
    }

    @GetMapping("/staff/{id}")
    public ResponseEntity<ApiResponse<UserDto>> getStaff(@PathVariable String id) {
        try {
            UserDto user = userService.getStaffById(id);
            if (user == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(ApiResponse.ok(user));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to get staff: " + e.getMessage()));
        }
    }

    @PutMapping("/staff/{id}")
    public ResponseEntity<ApiResponse<UserDto>> updateStaff(
            @PathVariable String id,
            @RequestBody UpdateUserRequest request) {
        try {
            UserDto user = userService.updateStaff(id, request);
            return ResponseEntity.ok(ApiResponse.ok("Staff updated", user));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to update staff: " + e.getMessage()));
        }
    }

    @DeleteMapping("/staff/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ApiResponse<Void>> deleteStaff(@PathVariable String id) {
        try {
            userService.deleteStaff(id);
            return ResponseEntity.ok(ApiResponse.ok("Staff deactivated", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to deactivate staff: " + e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserDto>> getCurrentUser(HttpServletRequest request) {
        try {
            String uid = (String) request.getAttribute("uid");
            UserDto user = userService.getStaffById(uid);
            if (user == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(ApiResponse.ok(user));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to get user: " + e.getMessage()));
        }
    }
}
