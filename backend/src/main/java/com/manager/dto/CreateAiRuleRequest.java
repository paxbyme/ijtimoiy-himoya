










package com.manager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateAiRuleRequest {
    private String title;
    private String content;
    private String category;
    private Integer priority;
    private Boolean isActive;
}
