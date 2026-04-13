package com.manager.service;

import com.manager.dto.BulkCreateTaskRequest;
import com.manager.dto.CreateTaskRequest;
import com.manager.dto.TaskDto;
import com.manager.dto.UpdateTaskRequest;
import com.manager.dto.UserDto;
import com.manager.repository.TaskRepository;
import com.manager.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final KpiService kpiService;
    private final StorageService storageService;

    public TaskService(TaskRepository taskRepository, UserRepository userRepository,
                       KpiService kpiService, StorageService storageService) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.kpiService = kpiService;
        this.storageService = storageService;
    }

    public TaskDto createTask(CreateTaskRequest req, String assignedBy, String departmentId) throws Exception {
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
                .status("NEW")
                .priority(req.getPriority() != null ? req.getPriority() : "MEDIUM")
                .deadline(req.getDeadline())
                .createdAt(Instant.now().toString())
                .assigneeName(assigneeName)
                .build();

        return taskRepository.save(task);
    }

    public List<TaskDto> createBulkTasks(BulkCreateTaskRequest req, String assignedBy, String departmentId) throws Exception {
        List<TaskDto> created = new ArrayList<>();
        for (String assignedTo : req.getAssignedToList()) {
            String assigneeName = "";
            UserDto assignee = userRepository.findById(assignedTo);
            if (assignee != null) {
                assigneeName = assignee.getName();
            }
            TaskDto task = TaskDto.builder()
                    .title(req.getTitle())
                    .description(req.getDescription())
                    .assignedTo(assignedTo)
                    .assignedBy(assignedBy)
                    .departmentId(departmentId)
                    .status("NEW")
                    .priority(req.getPriority() != null ? req.getPriority() : "MEDIUM")
                    .deadline(req.getDeadline())
                    .createdAt(Instant.now().toString())
                    .assigneeName(assigneeName)
                    .build();
            created.add(taskRepository.save(task));
        }
        return created;
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

        try {
            kpiService.calculateKpi(staffId, task.getDepartmentId());
        } catch (Exception e) {
            log.warn("KPI recalculation failed for staffId={}: {}", staffId, e.getMessage());
        }

        return taskRepository.findById(id);
    }

    public TaskDto uploadAttachment(String taskId, MultipartFile file) throws Exception {
        TaskDto task = taskRepository.findById(taskId);
        if (task == null) {
            throw new RuntimeException("Task not found");
        }
        String path = "task-attachments/" + taskId + "/" + file.getOriginalFilename();
        String url = storageService.uploadFile(file.getBytes(), path, file.getContentType());
        Map<String, Object> updates = new HashMap<>();
        updates.put("attachmentUrl", url);
        updates.put("attachmentName", file.getOriginalFilename());
        taskRepository.update(taskId, updates);
        return taskRepository.findById(taskId);
    }

    public TaskDto acceptTask(String taskId) throws Exception {
        TaskDto task = taskRepository.findById(taskId);
        if (task == null) {
            throw new RuntimeException("Task not found");
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("managerAccepted", true);
        taskRepository.update(taskId, updates);
        return taskRepository.findById(taskId);
    }

    public void deleteTask(String id) throws Exception {
        taskRepository.delete(id);
    }
}
