package com.manager.controller;

import com.manager.dto.*;
import com.manager.service.DepartmentService;
import com.manager.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('DEVELOPER')")
public class AdminController {

    private final UserService userService;
    private final DepartmentService departmentService;

    public AdminController(UserService userService, DepartmentService departmentService) {
        this.userService = userService;
        this.departmentService = departmentService;
    }

    // ── Managers ──

    @PostMapping("/managers")
    public ResponseEntity<ApiResponse<UserDto>> createManager(@Valid @RequestBody CreateUserRequest req) {
        try {
            UserDto m = userService.createManager(req);
            return ResponseEntity.ok(ApiResponse.ok("Manager created", m));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to create manager: " + e.getMessage()));
        }
    }

    @GetMapping("/managers")
    public ResponseEntity<ApiResponse<PageResponse<UserDto>>> listManagers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            List<UserDto> all = userService.listAllManagers();
            return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(all, page, size)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to list managers: " + e.getMessage()));
        }
    }

    @GetMapping("/managers/{id}")
    public ResponseEntity<ApiResponse<UserDto>> getManager(@PathVariable String id) {
        try {
            UserDto m = userService.getStaffById(id);
            if (m == null) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(ApiResponse.ok(m));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to get manager: " + e.getMessage()));
        }
    }

    @PutMapping("/managers/{id}")
    public ResponseEntity<ApiResponse<UserDto>> updateManager(
            @PathVariable String id,
            @RequestBody UpdateUserRequest req) {
        try {
            UserDto m = userService.updateManager(id, req);
            return ResponseEntity.ok(ApiResponse.ok("Manager updated", m));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to update manager: " + e.getMessage()));
        }
    }

    @DeleteMapping("/managers/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteManager(@PathVariable String id) {
        try {
            userService.softDeleteManager(id);
            return ResponseEntity.ok(ApiResponse.ok("Manager deactivated", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to deactivate manager: " + e.getMessage()));
        }
    }

    // ── Departments ──

    @PostMapping("/departments")
    public ResponseEntity<ApiResponse<DepartmentDto>> createDepartment(@Valid @RequestBody CreateDepartmentRequest req) {
        try {
            DepartmentDto d = departmentService.create(req);
            return ResponseEntity.ok(ApiResponse.ok("Department created", d));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to create department: " + e.getMessage()));
        }
    }

    @GetMapping("/departments")
    public ResponseEntity<ApiResponse<PageResponse<DepartmentDto>>> listDepartments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            List<DepartmentDto> all = departmentService.listAll();
            return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(all, page, size)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to list departments: " + e.getMessage()));
        }
    }

    @GetMapping("/departments/{id}")
    public ResponseEntity<ApiResponse<DepartmentDto>> getDepartment(@PathVariable String id) {
        try {
            DepartmentDto d = departmentService.getById(id);
            if (d == null) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(ApiResponse.ok(d));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to get department: " + e.getMessage()));
        }
    }

    @PutMapping("/departments/{id}")
    public ResponseEntity<ApiResponse<DepartmentDto>> updateDepartment(
            @PathVariable String id,
            @RequestBody UpdateDepartmentRequest req) {
        try {
            DepartmentDto d = departmentService.update(id, req);
            return ResponseEntity.ok(ApiResponse.ok("Department updated", d));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to update department: " + e.getMessage()));
        }
    }

    @DeleteMapping("/departments/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDepartment(@PathVariable String id) {
        try {
            departmentService.delete(id);
            return ResponseEntity.ok(ApiResponse.ok("Department deleted", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to delete department: " + e.getMessage()));
        }
    }
}
