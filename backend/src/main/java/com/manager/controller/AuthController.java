package com.manager.controller;

import com.manager.dto.ApiResponse;
import com.manager.dto.UserDto;
import com.manager.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<UserDto>> verifyToken(@RequestBody Map<String, String> request) {
        try {
            String idToken = request.get("idToken");
            if (idToken == null || idToken.isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("idToken is required"));
            }
            UserDto user = authService.verifyToken(idToken);
            return ResponseEntity.ok(ApiResponse.ok("Token verified", user));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(ApiResponse.error("Invalid token: " + e.getMessage()));
        }
    }
}
