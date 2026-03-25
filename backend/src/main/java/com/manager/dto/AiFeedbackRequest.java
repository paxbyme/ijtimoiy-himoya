package com.manager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiFeedbackRequest {
    private String conversationId;
    private int messageIndex;
    private String rating; // "up" or "down"
    private String comment;
}
