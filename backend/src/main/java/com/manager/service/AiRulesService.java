package com.manager.service;

import com.manager.dto.AiRuleDto;
import com.manager.dto.CreateAiRuleRequest;
import com.manager.repository.AiRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiRulesService {

    private final AiRuleRepository aiRuleRepository;
    private final DocumentService documentService;

    public AiRulesService(AiRuleRepository aiRuleRepository, DocumentService documentService) {
        this.aiRuleRepository = aiRuleRepository;
        this.documentService = documentService;
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

    public AiRuleDto createRuleFromFile(MultipartFile file, String title, String category,
                                        Integer priority, String managerId, String departmentId) throws Exception {
        String content = documentService.extractText(file.getBytes(), file.getOriginalFilename());
        if (content == null || content.isBlank()) {
            throw new RuntimeException("Could not extract text from the uploaded file");
        }
        if (content.trim().length() < 20) {
            throw new RuntimeException(
                "The document appears to be a scanned image and does not contain readable text. " +
                "Please upload a document with actual text content, or enter the rule manually."
            );
        }
        AiRuleDto rule = AiRuleDto.builder()
                .departmentId(departmentId)
                .managerId(managerId)
                .title(title != null && !title.isBlank() ? title : file.getOriginalFilename())
                .content(content.trim())
                .category(category != null ? category : "GENERAL")
                .isActive(true)
                .priority(priority != null ? priority : 0)
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
