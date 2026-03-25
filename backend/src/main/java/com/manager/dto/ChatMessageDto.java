package com.manager.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDto {
    private String id;
    private String conversationId;
    private String senderId;
    private String receiverId;
    private String content;
    private String type;
    private String createdAt;
    private String readAt;
}
