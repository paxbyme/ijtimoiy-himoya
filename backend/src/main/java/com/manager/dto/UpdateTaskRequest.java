package com.manager.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTaskRequest {

    @Size(min = 2, max = 200, message = "Title must be between 2 and 200 characters")
    private String title;

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;

    @Pattern(regexp = "^(PENDING|IN_PROGRESS|COMPLETED|CANCELLED)$",
             message = "Status must be PENDING, IN_PROGRESS, COMPLETED, or CANCELLED")
    private String status;

    @Pattern(regexp = "^(LOW|MEDIUM|HIGH|URGENT)$",
             message = "Priority must be LOW, MEDIUM, HIGH, or URGENT")
    private String priority;

    private String deadline;
}
