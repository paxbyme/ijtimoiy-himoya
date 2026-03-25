package com.manager.service;

import com.manager.dto.KpiDto;
import com.manager.dto.TaskDto;
import com.manager.dto.UserDto;
import com.manager.repository.KpiRepository;
import com.manager.repository.TaskRepository;
import com.manager.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KpiServiceTest {

    @Mock KpiRepository kpiRepository;
    @Mock TaskRepository taskRepository;
    @Mock UserRepository userRepository;

    @InjectMocks KpiService kpiService;

    private final String STAFF_ID = "staff-001";
    private final String DEPT_ID  = "dept-001";
    private final String PERIOD   = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));

    @BeforeEach
    void setUp() throws Exception {
        UserDto staff = UserDto.builder().id(STAFF_ID).name("Alice").build();
        when(userRepository.findById(STAFF_ID)).thenReturn(staff);
        when(kpiRepository.findByStaffIdAndPeriod(eq(STAFF_ID), eq(PERIOD))).thenReturn(null);
    }

    // ---- calculateKpi ----

    @Test
    void calculateKpi_noTasks_returnsZeroScore() throws Exception {
        when(taskRepository.findByAssignedToAndPeriod(eq(STAFF_ID), any(), any()))
                .thenReturn(Collections.emptyList());
        KpiDto saved = KpiDto.builder().staffId(STAFF_ID).score(0).build();
        when(kpiRepository.save(any())).thenReturn(saved);

        KpiDto result = kpiService.calculateKpi(STAFF_ID, DEPT_ID);

        assertThat(result.getScore()).isEqualTo(0.0);
        verify(kpiRepository).save(argThat(k -> k.getScore() == 0.0));
    }

    @Test
    void calculateKpi_allTasksOnTime_returnsPerfectScore() throws Exception {
        String now   = Instant.now().toString();
        String later = Instant.now().plusSeconds(86400).toString(); // deadline tomorrow

        TaskDto task = TaskDto.builder()
                .id("t1")
                .assignedTo(STAFF_ID)
                .status("COMPLETED")
                .createdAt(Instant.now().minusSeconds(7200).toString())  // created 2h ago
                .deadline(later)
                .completedAt(now)
                .build();

        when(taskRepository.findByAssignedToAndPeriod(eq(STAFF_ID), any(), any()))
                .thenReturn(List.of(task));

        KpiDto saved = KpiDto.builder().staffId(STAFF_ID).score(100.0).build();
        when(kpiRepository.save(any())).thenReturn(saved);

        KpiDto result = kpiService.calculateKpi(STAFF_ID, DEPT_ID);

        // score = timeliness(40) + completion(30) + efficiency(up to 30) = 100
        assertThat(result.getScore()).isEqualTo(100.0);
    }

    @Test
    void calculateKpi_taskCompletedLate_penalisesTimeliness() throws Exception {
        String created   = Instant.now().minusSeconds(7200).toString();
        String deadline  = Instant.now().minusSeconds(3600).toString(); // deadline 1h ago (past)
        String completed = Instant.now().toString();                    // completed now (late)

        TaskDto lateTask = TaskDto.builder()
                .id("t2")
                .assignedTo(STAFF_ID)
                .status("COMPLETED")
                .createdAt(created)
                .deadline(deadline)
                .completedAt(completed)
                .build();

        when(taskRepository.findByAssignedToAndPeriod(eq(STAFF_ID), any(), any()))
                .thenReturn(List.of(lateTask));

        when(kpiRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        KpiDto result = kpiService.calculateKpi(STAFF_ID, DEPT_ID);

        // timeliness = 0 (late), completion = 30 (100% completed), efficiency = 0 (ratio > 1 capped)
        assertThat(result.getScore()).isLessThan(40.0);
    }

    @Test
    void calculateKpi_mixedTasks_partialCompletion() throws Exception {
        String base = Instant.now().minusSeconds(7200).toString();
        String dl   = Instant.now().plusSeconds(3600).toString();
        String comp = Instant.now().toString();

        TaskDto pending   = TaskDto.builder().id("t1").status("PENDING").createdAt(base).build();
        TaskDto completed = TaskDto.builder().id("t2").status("COMPLETED")
                .createdAt(base).deadline(dl).completedAt(comp).build();

        when(taskRepository.findByAssignedToAndPeriod(eq(STAFF_ID), any(), any()))
                .thenReturn(List.of(pending, completed));
        when(kpiRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        KpiDto result = kpiService.calculateKpi(STAFF_ID, DEPT_ID);

        // completion = 1/2 * 30 = 15
        assertThat(result.getBreakdown().get("completion")).isEqualTo(15.0);
    }

    @Test
    void calculateKpi_existingKpiForPeriod_updatesInsteadOfCreating() throws Exception {
        KpiDto existing = KpiDto.builder().id("kpi-1").staffId(STAFF_ID).score(50.0).build();
        when(kpiRepository.findByStaffIdAndPeriod(eq(STAFF_ID), eq(PERIOD))).thenReturn(existing);
        when(taskRepository.findByAssignedToAndPeriod(eq(STAFF_ID), any(), any()))
                .thenReturn(Collections.emptyList());

        kpiService.calculateKpi(STAFF_ID, DEPT_ID);

        verify(kpiRepository).update(eq("kpi-1"), any());
        verify(kpiRepository, never()).save(any());
    }

    // ---- getKpiByStaff ----

    @Test
    void getKpiByStaff_noRecords_returnsNull() throws Exception {
        when(kpiRepository.findByStaffId(STAFF_ID)).thenReturn(Collections.emptyList());
        assertThat(kpiService.getKpiByStaff(STAFF_ID)).isNull();
    }

    @Test
    void getKpiByStaff_multipleRecords_returnsLatest() throws Exception {
        KpiDto old  = KpiDto.builder().staffId(STAFF_ID).period("2025-10").score(60.0).build();
        KpiDto latest = KpiDto.builder().staffId(STAFF_ID).period("2026-03").score(80.0).build();
        when(kpiRepository.findByStaffId(STAFF_ID)).thenReturn(List.of(old, latest));

        KpiDto result = kpiService.getKpiByStaff(STAFF_ID);

        assertThat(result.getPeriod()).isEqualTo("2026-03");
        assertThat(result.getScore()).isEqualTo(80.0);
    }

    // ---- getKpiRankings ----

    @Test
    void getKpiRankings_assignsRanksInDescendingOrder() throws Exception {
        KpiDto a = KpiDto.builder().staffId("s1").score(70.0).build();
        KpiDto b = KpiDto.builder().staffId("s2").score(90.0).build();
        KpiDto c = KpiDto.builder().staffId("s3").score(80.0).build();

        when(kpiRepository.findByDepartmentIdAndPeriod(DEPT_ID, PERIOD))
                .thenReturn(List.of(a, b, c));

        List<KpiDto> rankings = kpiService.getKpiRankings(DEPT_ID, PERIOD);

        assertThat(rankings).hasSize(3);
        assertThat(rankings.get(0).getScore()).isEqualTo(90.0);
        assertThat(rankings.get(0).getRank()).isEqualTo(1);
        assertThat(rankings.get(2).getScore()).isEqualTo(70.0);
        assertThat(rankings.get(2).getRank()).isEqualTo(3);
    }
}
