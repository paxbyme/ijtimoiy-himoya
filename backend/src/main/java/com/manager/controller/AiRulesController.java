package com.manager.controller;

import com.manager.dto.AiRuleDto;
import com.manager.dto.ApiResponse;
import com.manager.dto.CreateAiRuleRequest;
import com.manager.service.AiRulesService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai-rules")
public class AiRulesController {

    private final AiRulesService aiRulesService;

    public AiRulesController(AiRulesService aiRulesService) {
        this.aiRulesService = aiRulesService;
    }

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ApiResponse<AiRuleDto>> createRule(
            @RequestBody CreateAiRuleRequest request,
            HttpServletRequest httpRequest) {
        try {
            String managerId = (String) httpRequest.getAttribute("uid");
            String departmentId = (String) httpRequest.getAttribute("departmentId");
            AiRuleDto rule = aiRulesService.createRule(request, managerId, departmentId);
            return ResponseEntity.ok(ApiResponse.ok("Rule created", rule));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to create rule: " + e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AiRuleDto>>> listRules(HttpServletRequest request) {
        try {
            String departmentId = (String) request.getAttribute("departmentId");
            List<AiRuleDto> rules = aiRulesService.getRulesByDepartment(departmentId);
            return ResponseEntity.ok(ApiResponse.ok(rules));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to list rules: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ApiResponse<AiRuleDto>> updateRule(
            @PathVariable String id,
            @RequestBody CreateAiRuleRequest request) {
        try {
            AiRuleDto rule = aiRulesService.updateRule(id, request);
            return ResponseEntity.ok(ApiResponse.ok("Rule updated", rule));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to update rule: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ApiResponse<Void>> deleteRule(@PathVariable String id) {
        try {
            aiRulesService.deleteRule(id);
            return ResponseEntity.ok(ApiResponse.ok("Rule deleted", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to delete rule: " + e.getMessage()));
        }
    }
}
