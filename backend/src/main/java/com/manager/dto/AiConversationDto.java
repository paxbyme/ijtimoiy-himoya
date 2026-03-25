package com.manager.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiConversationDto {
    private String id;
    private String staffId;
    private String departmentId;
    private String title;
    private int messageCount;
    private String createdAt;
    private String updatedAt;
}
