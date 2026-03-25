package com.manager.controller;

import com.manager.dto.ApiResponse;
import com.manager.dto.DocumentDto;
import com.manager.service.DocumentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/upload")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ApiResponse<DocumentDto>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        try {
            String departmentId = (String) request.getAttribute("departmentId");
            String uploadedBy = (String) request.getAttribute("uid");

            // Read file bytes before async processing (stream closes after request)
            byte[] fileBytes = file.getBytes();
            String fileName = file.getOriginalFilename();

            // Create document record immediately
            DocumentDto document = documentService.createDocument(fileName, departmentId, uploadedBy);

            // Process in background (extract, chunk, embed)
            documentService.processDocumentAsync(document.getId(), fileBytes, fileName, departmentId);

            return ResponseEntity.ok(ApiResponse.ok("Document uploaded, processing in background", document));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to upload document: " + e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DocumentDto>>> listDocuments(HttpServletRequest request) {
        try {
            String departmentId = (String) request.getAttribute("departmentId");
            List<DocumentDto> documents = documentService.getDocuments(departmentId);
            return ResponseEntity.ok(ApiResponse.ok(documents));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to list documents: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(@PathVariable String id) {
        try {
            documentService.deleteDocument(id);
            return ResponseEntity.ok(ApiResponse.ok("Document deleted", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to delete document: " + e.getMessage()));
        }
    }
}
