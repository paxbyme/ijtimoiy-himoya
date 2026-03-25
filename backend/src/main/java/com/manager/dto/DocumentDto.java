package com.manager.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentDto {
    private String id;
    private String departmentId;
    private String uploadedBy;
    private String title;
    private String fileName;
    private String storageUrl;
    private String status;
    private String createdAt;
}
