package com.manager.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDepartmentRequest {

    @Size(min = 2, max = 100, message = "Department name must be 2–100 characters")
    private String name;

    private String managerId;
}
