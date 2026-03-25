package com.manager.service;

import com.manager.dto.AiRuleDto;
import com.manager.dto.CreateAiRuleRequest;
import com.manager.repository.AiRuleRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiRulesService {

    private final AiRuleRepository aiRuleRepository;

    public AiRulesService(AiRuleRepository aiRuleRepository) {
        this.aiRuleRepository = aiRuleRepository;
    }

    public AiRuleDto createRule(CreateAiRuleRequest req, String managerId, String departmentId) throws Exception {
        AiRuleDto rule = AiRuleDto.builder()
                .departmentId(departmentId)
                .managerId(managerId)
                .title(req.getTitle())
                .content(req.getContent())
                .category(req.getCategory())
                .isActive(true)
                .priority(req.getPriority() != null ? req.getPriority() : 0)
                .build();

        return aiRuleRepository.save(rule);
    }

    public List<AiRuleDto> getRulesByDepartment(String departmentId) throws Exception {
        return aiRuleRepository.findByDepartmentId(departmentId);
    }

    public List<AiRuleDto> getActiveRulesForDepartment(String departmentId) throws Exception {
        return aiRuleRepository.findActiveByDepartmentId(departmentId);
    }

    public AiRuleDto updateRule(String id, CreateAiRuleRequest req) throws Exception {
        Map<String, Object> updates = new HashMap<>();
        if (req.getTitle() != null) updates.put("title", req.getTitle());
        if (req.getContent() != null) updates.put("content", req.getContent());
        if (req.getCategory() != null) updates.put("category", req.getCategory());
        if (req.getPriority() != null) updates.put("priority", req.getPriority());
        if (req.getIsActive() != null) updates.put("isActive", req.getIsActive());

        if (!updates.isEmpty()) {
            aiRuleRepository.update(id, updates);
        }
        return aiRuleRepository.findById(id);
    }

    public void deleteRule(String id) throws Exception {
        aiRuleRepository.delete(id);
    }
}
