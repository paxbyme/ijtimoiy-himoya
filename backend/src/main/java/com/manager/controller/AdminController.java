package com.manager.controller;

import com.manager.dto.*;
import com.manager.repository.KpiRepository;
import com.manager.repository.TaskRepository;
import com.manager.repository.UserRepository;
import com.manager.service.DepartmentService;
import com.manager.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.List;
import java.util.OptionalDouble;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('DEVELOPER')")
public class AdminController {

    private final UserService userService;
    private final DepartmentService departmentService;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final KpiRepository kpiRepository;

    public AdminController(UserService userService,
                           DepartmentService departmentService,
                           UserRepository userRepository,
                           TaskRepository taskRepository,
                           KpiRepository kpiRepository) {
        this.userService = userService;
        this.departmentService = departmentService;
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
        this.kpiRepository = kpiRepository;
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

    @GetMapping("/managers/{id}/stats")
    public ResponseEntity<ApiResponse<ManagerStatsDto>> getManagerStats(@PathVariable String id) {
        try {
            UserDto manager = userService.getStaffById(id);
            if (manager == null) return ResponseEntity.notFound().build();

            String deptId = manager.getDepartmentId();
            String deptName = null;
            int staffTotal = 0, staffActive = 0;
            int taskTotal = 0, taskCompleted = 0, taskPending = 0, taskInProgress = 0, taskCancelled = 0;
            Double avgKpi = null;
            String period = YearMonth.now().toString();

            if (deptId != null && !deptId.isEmpty()) {
                DepartmentDto dept = departmentService.getById(deptId);
                if (dept != null) deptName = dept.getName();

                List<UserDto> staff = userRepository.findByDepartmentId(deptId);
                staffTotal = staff.size();
                staffActive = (int) staff.stream().filter(s -> Boolean.TRUE.equals(s.getIsActive())).count();

                List<TaskDto> tasks = taskRepository.findByDepartmentId(deptId);
                taskTotal = tasks.size();
                for (TaskDto t : tasks) {
                    switch (t.getStatus() != null ? t.getStatus() : "") {
                        case "COMPLETED"   -> taskCompleted++;
                        case "PENDING", "NEW" -> taskPending++;
                        case "IN_PROGRESS" -> taskInProgress++;
                        case "CANCELLED"   -> taskCancelled++;
                    }
                }

                List<KpiDto> kpis = kpiRepository.findByDepartmentIdAndPeriod(deptId, period);
                OptionalDouble avg = kpis.stream()
                        .filter(k -> k.getScore() != null)
                        .mapToDouble(KpiDto::getScore)
                        .average();
                if (avg.isPresent()) avgKpi = Math.round(avg.getAsDouble() * 10.0) / 10.0;
            }

            ManagerStatsDto stats = ManagerStatsDto.builder()
                    .id(manager.getId())
                    .name(manager.getName())
                    .phone(manager.getPhone())
                    .departmentId(deptId)
                    .departmentName(deptName)
                    .isActive(manager.getIsActive())
                    .createdAt(manager.getCreatedAt())
                    .staffTotal(staffTotal)
                    .staffActive(staffActive)
                    .taskTotal(taskTotal)
                    .taskCompleted(taskCompleted)
                    .taskPending(taskPending)
                    .taskInProgress(taskInProgress)
                    .taskCancelled(taskCancelled)
                    .avgKpiScore(avgKpi)
                    .currentPeriod(period)
                    .build();

            return ResponseEntity.ok(ApiResponse.ok(stats));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to get manager stats: " + e.getMessage()));
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
    public ResponseEntity<ApiResponse<Void>> deactivateManager(@PathVariable String id) {
        try {
            userService.softDeleteManager(id);
            return ResponseEntity.ok(ApiResponse.ok("Manager deactivated", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to deactivate manager: " + e.getMessage()));
        }
    }

    @DeleteMapping("/managers/{id}/hard")
    public ResponseEntity<ApiResponse<Void>> hardDeleteManager(@PathVariable String id) {
        try {
            departmentService.removeManagerFromAllDepartments(id);
            userService.hardDeleteManager(id);
            return ResponseEntity.ok(ApiResponse.ok("Manager permanently deleted", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to delete manager: " + e.getMessage()));
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
