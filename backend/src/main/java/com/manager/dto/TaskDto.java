package com.manager.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDto {
    private String id;
    private String title;
    private String description;
    private String assignedTo;
    private String assignedBy;
    private String departmentId;
    private String status;
    private String priority;
    private String deadline;
    private String completedAt;
    private String createdAt;
    private String assigneeName;
    private String attachmentUrl;
    private String attachmentName;
    private Boolean managerAccepted;
    private List<Map<String, String>> attachments;
}
