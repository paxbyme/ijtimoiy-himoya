package com.manager.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiDto {
    private String id;
    private String staffId;
    private String staffName;
    private String departmentId;
    private String period;
    private Double score;
    private Integer rank;
    private Map<String, Double> breakdown;
}
