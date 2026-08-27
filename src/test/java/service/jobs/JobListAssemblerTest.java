package service.jobs;

import builder.*;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.dto.bdvzpmc.JobRow;
import org.acme.foodpackaging.dto.oeepev.DelayRow;
import org.acme.foodpackaging.dto.oeepev.MaintenanceRow;
import org.acme.foodpackaging.repository.jobs.JobRepository;
import org.acme.foodpackaging.service.jobs.JobFactory;
import org.acme.foodpackaging.service.jobs.JobListAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.smallrye.common.constraint.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobListAssemblerTest {

    @InjectMocks
    JobListAssembler jobListAssembler;

    @Mock
    JobFactory jobFactory;
    @Mock
    JobRepository jobRepository;

    private PackagingSchedule schedule;

    @BeforeEach
    void setUp() {
        LocalDateTime lineStartDateTime = LocalDateTime.of(2025, 1, 15, 8, 0);
        Job job = JobTestBuilder.aJob().withId("J1").build();

        schedule = ScheduleTestBuilder.aSchedule()
                .withWorkCalendar(lineStartDateTime.toLocalDate(), lineStartDateTime)
                .withLines(LineTestBuilder.aLine("L1", lineStartDateTime).withJobs(job).build())
                .withSpeed("L1", "CLASSIC", 100)
                .withEmptyJobs()
                .withEmptyJobMap()
                .build();

        schedule.getJobs().clear();
        schedule.getLines().getFirst().getJobs().clear();
    }

    @Test
    void assemble_buildsProductionJob() {
        JobRow dbRow = JobRowBuilder.aRow().withSnpz(123L).withKmc("P1").withLineId("L1").build();
        Job producedJob = JobTestBuilder.aJob().withId("123").build();

        when(jobRepository.getJobRowMap(any(), any())).thenReturn(Map.of(123L, dbRow));
        when(jobRepository.getMaintenanceData(any(), any())).thenReturn(Collections.emptyList());
        when(jobRepository.loadDelayDurationRows(any(), any()))
                .thenReturn(Map.of(123L, new DelayRow(2L, 123L, "Delay note", 22)));
        when(jobRepository.loadCleaningDelayDurationRows(any(), any()))
                .thenReturn(Map.of(123L, new DelayRow(2L, 123L, "Cleaning delay note", 12)));
        when(jobRepository.getCleaningData(any(), any())).thenReturn(Collections.emptyMap());
        when(jobFactory.createProductionJob(eq(dbRow), any())).thenReturn(producedJob);

        JobListAssembler.JobAssemblyResult result = jobListAssembler.assemble(schedule);

        assertEquals(1, result.jobs().size());
        assertEquals("123", result.jobs().getFirst().getId());
        assertEquals(22, result.jobs().getFirst().getDelayDuration().toMinutes());
        assertEquals(12, result.jobs().getFirst().getCleaningDelay().toMinutes());
        assertEquals("Delay note", result.jobs().getFirst().getDelayNote());
        assertEquals("Cleaning delay note", result.jobs().getFirst().getCleaningDelayNote());
        assertEquals(1, result.jobRows().size());
    }

    @Test
    void assemble_buildsMaintenanceJob() {
        MaintenanceRow maintenanceRow = MaintenanceRowBuilder.aRow()
                .withFId(111L).withEventTypeId(7).withDuration(60).withNote("Maintenance note").build();
        Job maintenanceJob = JobTestBuilder.aJob().withId("111").asMaintenance().build();

        when(jobRepository.getMaintenanceData(any(), any())).thenReturn(List.of(maintenanceRow));
        when(jobRepository.getJobRowMap(any(), any())).thenReturn(Collections.emptyMap());
        when(jobRepository.loadDelayDurationRows(any(), any())).thenReturn(Collections.emptyMap());
        when(jobRepository.loadCleaningDelayDurationRows(any(), any())).thenReturn(Collections.emptyMap());
        when(jobRepository.getCleaningData(any(), any())).thenReturn(Collections.emptyMap());
        when(jobFactory.createMaintenanceJob(eq(maintenanceRow), any())).thenReturn(maintenanceJob);

        JobListAssembler.JobAssemblyResult result = jobListAssembler.assemble(schedule);

        assertEquals(1, result.jobs().size());
        assertEquals("111", result.jobs().getFirst().getId());
        assertTrue(result.jobs().getFirst().isMaintenance());
    }
}
