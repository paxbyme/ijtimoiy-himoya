package com.manager.controller;

import com.manager.dto.ApiResponse;
import com.manager.dto.KpiDto;
import com.manager.service.KpiService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/kpi")
public class KpiController {

    private final KpiService kpiService;

    public KpiController(KpiService kpiService) {
        this.kpiService = kpiService;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<KpiDto>> getMyKpi(HttpServletRequest request) {
        try {
            String uid = (String) request.getAttribute("uid");
            KpiDto kpi = kpiService.getKpiByStaff(uid);
            return ResponseEntity.ok(ApiResponse.ok(kpi));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to get KPI: " + e.getMessage()));
        }
    }

    @GetMapping("/{staffId}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ApiResponse<KpiDto>> getStaffKpi(@PathVariable String staffId) {
        try {
            KpiDto kpi = kpiService.getKpiByStaff(staffId);
            return ResponseEntity.ok(ApiResponse.ok(kpi));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to get KPI: " + e.getMessage()));
        }
    }

    @GetMapping("/rankings")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ApiResponse<List<KpiDto>>> getRankings(
            @RequestParam(required = false) String period,
            HttpServletRequest request) {
        try {
            String departmentId = (String) request.getAttribute("departmentId");
            if (period == null || period.isEmpty()) {
                period = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            }
            List<KpiDto> rankings = kpiService.getKpiRankings(departmentId, period);
            return ResponseEntity.ok(ApiResponse.ok(rankings));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to get rankings: " + e.getMessage()));
        }
    }

    @PostMapping("/calculate/{staffId}")
    public ResponseEntity<ApiResponse<KpiDto>> calculateKpi(
            @PathVariable String staffId,
            HttpServletRequest request) {
        try {
            String departmentId = (String) request.getAttribute("departmentId");
            KpiDto kpi = kpiService.calculateKpi(staffId, departmentId);
            return ResponseEntity.ok(ApiResponse.ok("KPI calculated", kpi));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to calculate KPI: " + e.getMessage()));
        }
    }
}
