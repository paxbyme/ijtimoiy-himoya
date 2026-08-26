package com.manager.service;

import com.manager.dto.AiRuleDto;
import com.manager.dto.CreateAiRuleRequest;
import com.manager.repository.AiRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AiRulesService {

    private static final int MAX_RULE_CONTENT_CHARS = 8_000;
    private static final long ACTIVE_RULES_CACHE_MILLIS = 60_000;

    private final AiRuleRepository aiRuleRepository;
    private final DocumentService documentService;
    private final Map<String, CachedRules> activeRulesCache = new ConcurrentHashMap<>();

    public AiRulesService(AiRuleRepository aiRuleRepository, DocumentService documentService) {
        this.aiRuleRepository = aiRuleRepository;
        this.documentService = documentService;
    }

    public AiRuleDto createRule(CreateAiRuleRequest req, String managerId, String departmentId) throws Exception {
        validateRuleContent(req.getContent());

        AiRuleDto rule = AiRuleDto.builder()
                .departmentId(departmentId)
                .managerId(managerId)
                .title(req.getTitle())
                .content(req.getContent())
                .category(req.getCategory())
                .isActive(true)
                .priority(req.getPriority() != null ? req.getPriority() : 0)
                .build();

        AiRuleDto saved = aiRuleRepository.save(rule);
        activeRulesCache.remove(departmentId);
        return saved;
    }

    public AiRuleDto createRuleFromFile(MultipartFile file, String title, String category,
                                        Integer priority, String managerId, String departmentId) throws Exception {
        String content = documentService.extractText(file.getBytes(), file.getOriginalFilename());
        if (content == null || content.isBlank()) {
            throw new RuntimeException("Could not extract text from the uploaded file");
        }
        if (content.trim().length() < 20) {
            throw new RuntimeException(
                "Could not extract readable text from the document (scanned image or unsupported format). " +
                "Please upload a document with selectable text, or enter the rule manually."
            );
        }
        validateRuleContent(content);
        AiRuleDto rule = AiRuleDto.builder()
                .departmentId(departmentId)
                .managerId(managerId)
                .title(title != null && !title.isBlank() ? title : file.getOriginalFilename())
                .content(content.trim())
                .category(category != null ? category : "GENERAL")
                .isActive(true)
                .priority(priority != null ? priority : 0)
                .build();
        AiRuleDto saved = aiRuleRepository.save(rule);
        activeRulesCache.remove(departmentId);
        return saved;
    }

    public List<AiRuleDto> getRulesByDepartment(String departmentId) throws Exception {
        return aiRuleRepository.findByDepartmentId(departmentId);
    }

    public List<AiRuleDto> getActiveRulesForDepartment(String departmentId) throws Exception {
        CachedRules cached = activeRulesCache.get(departmentId);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.loadedAtMillis < ACTIVE_RULES_CACHE_MILLIS) {
            return cached.rules;
        }

        List<AiRuleDto> rules = List.copyOf(aiRuleRepository.findActiveByDepartmentId(departmentId));
        activeRulesCache.put(departmentId, new CachedRules(rules, now));
        return rules;
    }

    public AiRuleDto updateRule(String id, CreateAiRuleRequest req) throws Exception {
        Map<String, Object> updates = new HashMap<>();
        if (req.getTitle() != null) updates.put("title", req.getTitle());
        if (req.getContent() != null) {
            validateRuleContent(req.getContent());
            updates.put("content", req.getContent());
        }
        if (req.getCategory() != null) updates.put("category", req.getCategory());
        if (req.getPriority() != null) updates.put("priority", req.getPriority());
        if (req.getIsActive() != null) updates.put("isActive", req.getIsActive());

        if (!updates.isEmpty()) {
            aiRuleRepository.update(id, updates);
            activeRulesCache.clear();
        }
        return aiRuleRepository.findById(id);
    }

    public void deleteRule(String id) throws Exception {
        aiRuleRepository.delete(id);
        activeRulesCache.clear();
    }

    private void validateRuleContent(String content) {
        if (content == null) {
            return;
        }
        int length = content.trim().length();
        if (length > MAX_RULE_CONTENT_CHARS) {
            throw new RuntimeException("AI rules must be short instructions (max "
                    + MAX_RULE_CONTENT_CHARS
                    + " characters). Upload long policy documents in Documents so RAG can index them.");
        }
    }

    private record CachedRules(List<AiRuleDto> rules, long loadedAtMillis) {}
}
