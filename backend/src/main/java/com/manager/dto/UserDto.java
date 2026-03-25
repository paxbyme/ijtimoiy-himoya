package com.manager.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private String id;
    private String phone;
    private String name;
    private String role;
    private String departmentId;
    private String managerId;
    private Boolean isActive;
    private String createdAt;
}
