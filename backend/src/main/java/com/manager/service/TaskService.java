package com.manager.service;

import com.manager.dto.CreateTaskRequest;
import com.manager.dto.TaskDto;
import com.manager.dto.UpdateTaskRequest;
import com.manager.dto.UserDto;
import com.manager.repository.TaskRepository;
import com.manager.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final KpiService kpiService;

    public TaskService(TaskRepository taskRepository, UserRepository userRepository, KpiService kpiService) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.kpiService = kpiService;
    }

    public TaskDto createTask(CreateTaskRequest req, String assignedBy, String departmentId) throws Exception {
        // Look up assignee name
        String assigneeName = "";
        UserDto assignee = userRepository.findById(req.getAssignedTo());
        if (assignee != null) {
            assigneeName = assignee.getName();
        }

        TaskDto task = TaskDto.builder()
                .title(req.getTitle())
                .description(req.getDescription())
                .assignedTo(req.getAssignedTo())
                .assignedBy(assignedBy)
                .departmentId(departmentId)
                .status("PENDING")
                .priority(req.getPriority() != null ? req.getPriority() : "MEDIUM")
                .deadline(req.getDeadline())
                .createdAt(Instant.now().toString())
                .assigneeName(assigneeName)
                .build();

        return taskRepository.save(task);
    }

    public List<TaskDto> getTasksByDepartment(String departmentId) throws Exception {
        return taskRepository.findByDepartmentId(departmentId);
    }

    public List<TaskDto> getTasksByStaff(String staffId) throws Exception {
        return taskRepository.findByAssignedTo(staffId);
    }

    public TaskDto getTaskById(String id) throws Exception {
        return taskRepository.findById(id);
    }

    public TaskDto updateTask(String id, UpdateTaskRequest req) throws Exception {
        Map<String, Object> updates = new HashMap<>();
        if (req.getTitle() != null) updates.put("title", req.getTitle());
        if (req.getDescription() != null) updates.put("description", req.getDescription());
        if (req.getStatus() != null) updates.put("status", req.getStatus());
        if (req.getPriority() != null) updates.put("priority", req.getPriority());
        if (req.getDeadline() != null) updates.put("deadline", req.getDeadline());

        if (!updates.isEmpty()) {
            taskRepository.update(id, updates);
        }
        return taskRepository.findById(id);
    }

    public TaskDto completeTask(String id, String staffId) throws Exception {
        TaskDto task = taskRepository.findById(id);
        if (task == null) {
            throw new RuntimeException("Task not found");
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "COMPLETED");
        updates.put("completedAt", Instant.now().toString());
        taskRepository.update(id, updates);

        // Trigger KPI recalculation (best-effort – failure does not fail the task)
        try {
            kpiService.calculateKpi(staffId, task.getDepartmentId());
        } catch (Exception e) {
            log.warn("KPI recalculation failed for staffId={}: {}", staffId, e.getMessage());
        }

        return taskRepository.findById(id);
    }

    public void deleteTask(String id) throws Exception {
        taskRepository.delete(id);
    }
}
