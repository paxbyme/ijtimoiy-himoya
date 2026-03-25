package com.manager.controller;

import com.manager.dto.*;
import com.manager.service.ChatService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/send")
    public ResponseEntity<ApiResponse<ChatMessageDto>> sendMessage(
            @Valid @RequestBody SendMessageRequest request,
            HttpServletRequest httpRequest) {
        try {
            String senderId = (String) httpRequest.getAttribute("uid");
            ChatMessageDto message = chatService.sendMessage(request, senderId);
            return ResponseEntity.ok(ApiResponse.ok("Message sent", message));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to send message: " + e.getMessage()));
        }
    }

    @GetMapping("/conversations")
    public ResponseEntity<ApiResponse<List<ConversationDto>>> getConversations(HttpServletRequest request) {
        try {
            String userId = (String) request.getAttribute("uid");
            List<ConversationDto> conversations = chatService.getConversations(userId);
            return ResponseEntity.ok(ApiResponse.ok(conversations));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to get conversations: " + e.getMessage()));
        }
    }

    /**
     * GET /api/chat/messages/{conversationId}?page=0&size=50
     * Returns a page of messages ordered ascending (oldest first within the page).
     * NOTE: clients must read `.content` from the response.
     */
    @GetMapping("/messages/{conversationId}")
    public ResponseEntity<ApiResponse<PageResponse<ChatMessageDto>>> getMessages(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            List<ChatMessageDto> messages = chatService.getMessages(conversationId);
            return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(messages, page, size)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to get messages: " + e.getMessage()));
        }
    }
}
