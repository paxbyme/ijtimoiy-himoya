package com.manager.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManagerStatsDto {
    private String id;
    private String name;
    private String phone;
    private String departmentId;
    private String departmentName;
    private Boolean isActive;
    private String createdAt;

    // Staff
    private int staffTotal;
    private int staffActive;

    // Tasks (department-wide)
    private int taskTotal;
    private int taskCompleted;
    private int taskPending;
    private int taskInProgress;
    private int taskCancelled;

    // KPI (current month average across staff)
    private Double avgKpiScore;
    private String currentPeriod;
}
