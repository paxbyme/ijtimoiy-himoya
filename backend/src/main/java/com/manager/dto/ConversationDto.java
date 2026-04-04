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
public class ConversationDto {
    private String id;
    private List<String> participants;
    private String lastMessage;
    private String lastMessageAt;
    private Object unreadCount; // Map<String, Integer> per-user counts
}
