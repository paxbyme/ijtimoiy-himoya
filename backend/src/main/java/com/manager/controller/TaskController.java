package com.manager.controller;

import com.manager.dto.*;
import com.manager.service.TaskService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ApiResponse<TaskDto>> createTask(
            @Valid @RequestBody CreateTaskRequest request,
            HttpServletRequest httpRequest) {
        try {
            String assignedBy = (String) httpRequest.getAttribute("uid");
            String departmentId = (String) httpRequest.getAttribute("departmentId");
            TaskDto task = taskService.createTask(request, assignedBy, departmentId);
            return ResponseEntity.ok(ApiResponse.ok("Task created", task));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to create task: " + e.getMessage()));
        }
    }

    /**
     * GET /api/tasks?page=0&size=20&status=PENDING
     * Returns PageResponse<TaskDto> with content, totalElements, totalPages, etc.
     * NOTE: web/mobile clients must now read `.content` instead of the raw array.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<TaskDto>>> listTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            HttpServletRequest request) {
        try {
            String role = (String) request.getAttribute("role");
            String uid = (String) request.getAttribute("uid");
            String departmentId = (String) request.getAttribute("departmentId");

            List<TaskDto> tasks;
            if ("MANAGER".equalsIgnoreCase(role)) {
                tasks = taskService.getTasksByDepartment(departmentId);
            } else {
                tasks = taskService.getTasksByStaff(uid);
            }

            if (status != null && !status.isEmpty()) {
                tasks = tasks.stream()
                        .filter(t -> status.equalsIgnoreCase(t.getStatus()))
                        .collect(Collectors.toList());
            }

            return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(tasks, page, size)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to list tasks: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskDto>> getTask(@PathVariable String id) {
        try {
            TaskDto task = taskService.getTaskById(id);
            if (task == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(ApiResponse.ok(task));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to get task: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskDto>> updateTask(
            @PathVariable String id,
            @RequestBody UpdateTaskRequest request) {
        try {
            TaskDto task = taskService.updateTask(id, request);
            return ResponseEntity.ok(ApiResponse.ok("Task updated", task));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to update task: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}/complete")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<ApiResponse<TaskDto>> completeTask(
            @PathVariable String id,
            HttpServletRequest request) {
        try {
            String uid = (String) request.getAttribute("uid");
            TaskDto task = taskService.completeTask(id, uid);
            return ResponseEntity.ok(ApiResponse.ok("Task completed", task));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to complete task: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ApiResponse<Void>> deleteTask(@PathVariable String id) {
        try {
            taskService.deleteTask(id);
            return ResponseEntity.ok(ApiResponse.ok("Task deleted", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to delete task: " + e.getMessage()));
        }
    }
}
