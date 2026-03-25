package com.manager.service;

import com.manager.dto.KpiDto;
import com.manager.dto.TaskDto;
import com.manager.dto.UserDto;
import com.manager.repository.KpiRepository;
import com.manager.repository.TaskRepository;
import com.manager.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class KpiService {

    private static final Logger log = LoggerFactory.getLogger(KpiService.class);

    private final KpiRepository kpiRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    public KpiService(KpiRepository kpiRepository, TaskRepository taskRepository, UserRepository userRepository) {
        this.kpiRepository = kpiRepository;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
    }

    public KpiDto calculateKpi(String staffId, String departmentId) throws Exception {
        // Current month period
        YearMonth currentMonth = YearMonth.now();
        String period = currentMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        String periodStart = currentMonth.atDay(1).toString();
        String periodEnd = currentMonth.atEndOfMonth().toString() + "T23:59:59Z";

        // Get all tasks assigned to staff in current period
        List<TaskDto> allTasks = taskRepository.findByAssignedToAndPeriod(staffId, periodStart, periodEnd);
        List<TaskDto> completedTasks = allTasks.stream()
                .filter(t -> "COMPLETED".equals(t.getStatus()))
                .collect(Collectors.toList());

        double timeliness = 0;
        double completion = 0;
        double efficiency = 0;

        if (!allTasks.isEmpty()) {
            // Completion: completed / total * 30
            completion = ((double) completedTasks.size() / allTasks.size()) * 30;
        }

        if (!completedTasks.isEmpty()) {
            // Timeliness: tasks completed on or before deadline / total completed * 40
            long onTime = completedTasks.stream().filter(t -> {
                if (t.getDeadline() == null || t.getCompletedAt() == null) return true;
                try {
                    LocalDate deadline = LocalDate.parse(t.getDeadline().substring(0, 10));
                    LocalDate completed = LocalDate.parse(t.getCompletedAt().substring(0, 10));
                    return !completed.isAfter(deadline);
                } catch (Exception e) {
                    return true;
                }
            }).count();
            timeliness = ((double) onTime / completedTasks.size()) * 40;

            // Efficiency: avg(min(deadline_duration / actual_duration, 1.0)) * 30
            double totalEfficiency = 0;
            int efficiencyCount = 0;
            for (TaskDto task : completedTasks) {
                if (task.getDeadline() != null && task.getCompletedAt() != null && task.getCreatedAt() != null) {
                    try {
                        Instant created = Instant.parse(task.getCreatedAt());
                        Instant deadlineInstant = Instant.parse(task.getDeadline().length() > 10 ?
                                task.getDeadline() : task.getDeadline() + "T23:59:59Z");
                        Instant completed = Instant.parse(task.getCompletedAt());

                        long deadlineDuration = ChronoUnit.HOURS.between(created, deadlineInstant);
                        long actualDuration = ChronoUnit.HOURS.between(created, completed);

                        if (actualDuration > 0 && deadlineDuration > 0) {
                            double ratio = Math.min((double) deadlineDuration / actualDuration, 1.0);
                            totalEfficiency += ratio;
                            efficiencyCount++;
                        }
                    } catch (Exception e) {
                        // Skip tasks with unparseable dates
                    }
                }
            }
            if (efficiencyCount > 0) {
                efficiency = (totalEfficiency / efficiencyCount) * 30;
            }
        }

        double totalScore = timeliness + completion + efficiency;
        log.debug("KPI calculated for staffId={} period={}: timeliness={} completion={} efficiency={} total={}",
                staffId, period, timeliness, completion, efficiency, totalScore);

        // Look up staff name
        UserDto staff = userRepository.findById(staffId);
        String staffName = staff != null ? staff.getName() : "";

        Map<String, Double> breakdown = new HashMap<>();
        breakdown.put("timeliness", Math.round(timeliness * 100.0) / 100.0);
        breakdown.put("completion", Math.round(completion * 100.0) / 100.0);
        breakdown.put("efficiency", Math.round(efficiency * 100.0) / 100.0);

        // Check if KPI already exists for this period
        KpiDto existing = kpiRepository.findByStaffIdAndPeriod(staffId, period);
        if (existing != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("score", Math.round(totalScore * 100.0) / 100.0);
            updates.put("breakdown", breakdown);
            updates.put("staffName", staffName);
            kpiRepository.update(existing.getId(), updates);
            existing.setScore(Math.round(totalScore * 100.0) / 100.0);
            existing.setBreakdown(breakdown);
            existing.setStaffName(staffName);
            return existing;
        }

        KpiDto kpi = KpiDto.builder()
                .staffId(staffId)
                .staffName(staffName)
                .departmentId(departmentId)
                .period(period)
                .score(Math.round(totalScore * 100.0) / 100.0)
                .breakdown(breakdown)
                .build();

        return kpiRepository.save(kpi);
    }

    public KpiDto getKpiByStaff(String staffId) throws Exception {
        List<KpiDto> kpis = kpiRepository.findByStaffId(staffId);
        if (kpis.isEmpty()) return null;
        // Return latest
        return kpis.stream()
                .max(Comparator.comparing(KpiDto::getPeriod))
                .orElse(null);
    }

    public List<KpiDto> getKpiRankings(String departmentId, String period) throws Exception {
        List<KpiDto> kpis = kpiRepository.findByDepartmentIdAndPeriod(departmentId, period);
        // Sort by score descending
        kpis.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        // Assign ranks
        for (int i = 0; i < kpis.size(); i++) {
            kpis.get(i).setRank(i + 1);
        }
        return kpis;
    }
}
