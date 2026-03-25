package com.manager.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRuleDto {
    private String id;
    private String departmentId;
    private String managerId;
    private String title;
    private String content;
    private String category;
    private Boolean isActive;
    private Integer priority;
}
