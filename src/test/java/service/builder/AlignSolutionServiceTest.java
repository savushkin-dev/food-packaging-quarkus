package service.builder;

import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.domain.WorkCalendar;
import org.acme.foodpackaging.dto.MaintenanceRequest;
import org.acme.foodpackaging.scheduleoperations.MaintenanceJob;
import org.acme.foodpackaging.service.builder.AlignSolutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlignSolutionServiceTest {

    @InjectMocks
    AlignSolutionService alignSolutionService;

    @Mock
    MaintenanceJob maintenanceJob;

    private PackagingSchedule schedule;
    private Line line;
    private WorkCalendar workCalendar;

    @BeforeEach
    void setUp() {
        schedule = new PackagingSchedule();
        workCalendar = new WorkCalendar(LocalDate.of(2025, 1, 15));
        workCalendar.setMinStartDateTime(LocalDateTime.of(2025, 1, 15, 8, 0));
        schedule.setWorkCalendar(workCalendar);

        line = new Line("line1", "Line 1");
        line.setJobs(new ArrayList<>());
        schedule.setLines(List.of(line));
        schedule.setJobs(new ArrayList<>());
    }

    @Test
    void alignByFactDuration_whenNoJobs_shouldNotAddMaintenance() {

        line.setJobs(new ArrayList<>());

        alignSolutionService.alignByFactDuration(schedule);
        verify(maintenanceJob, never()).addMaintenanceJob(any(), any());
    }

    @Test
    void alignByFactDuration_whenJobsNull_shouldNotAddMaintenance() {

        line.setJobs(null);

        alignSolutionService.alignByFactDuration(schedule);
        verify(maintenanceJob, never()).addMaintenanceJob(any(), any());
    }

    @Test
    void alignByFactDuration_whenFactEqualsPlan_shouldNotAddMaintenance() {
        // Given - fact duration equals plan duration
        LocalDateTime start = LocalDateTime.of(2025, 1, 15, 10, 0);
        LocalDateTime end = LocalDateTime.of(2025, 1, 15, 11, 0); // 60 minutes

        Job job = createJob("J1", start, end);
        job.setCameraStart(start);
        job.setCameraEnd(end); // Same as plan: 60 minutes
        line.getJobs().add(job);
        schedule.getJobs().add(job);

        alignSolutionService.alignByFactDuration(schedule);

        verify(maintenanceJob, never()).addMaintenanceJob(any(), any());
    }

    @Test
    void alignByFactDuration_whenFactLessThanPlan_shouldNotAddMaintenance() {
        // Given - fact duration less than plan duration
        LocalDateTime start = LocalDateTime.of(2025, 1, 15, 10, 0);
        LocalDateTime end = LocalDateTime.of(2025, 1, 15, 11, 0); // Plan: 60 minutes

        Job job = createJob("J1", start, end);
        job.setCameraStart(start);
        job.setCameraEnd(start.plusMinutes(30)); // Fact: 30 minutes < 60
        line.getJobs().add(job);
        schedule.getJobs().add(job);

        alignSolutionService.alignByFactDuration(schedule);

        verify(maintenanceJob, never()).addMaintenanceJob(any(), any());
    }

    @Test
    void alignByFactDuration_whenFactGreaterThanPlan_shouldAddMaintenance() {
        // Given - fact duration greater than plan duration
        LocalDateTime start = LocalDateTime.of(2025, 1, 15, 10, 0);
        LocalDateTime end = LocalDateTime.of(2025, 1, 15, 11, 0); // Plan: 60 minutes

        Job job = createJob("J1", start, end);
        job.setCameraStart(start);
        job.setCameraEnd(start.plusMinutes(90)); // Fact: 90 minutes > 60
        line.getJobs().add(job);
        schedule.getJobs().add(job);

        alignSolutionService.alignByFactDuration(schedule);

        // Then - should add maintenance job with diff = 30 minutes
        ArgumentCaptor<MaintenanceRequest> requestCaptor = ArgumentCaptor.forClass(MaintenanceRequest.class);
        verify(maintenanceJob).addMaintenanceJob(eq(schedule), requestCaptor.capture());

        MaintenanceRequest request = requestCaptor.getValue();
        assertEquals("line1", request.getLineId());
        assertEquals(1, request.getInsertIndex()); // After job at index 0
        assertEquals(30, request.getDurationMinutes()); // 90 - 60 = 30
        assertEquals(7, request.getMaintenanceTypeId());
        assertTrue(request.getMaintenanceNote().contains("Отклонение факт > план"));
        assertTrue(request.getMaintenanceNote().contains("J1"));
    }

    @Test
    void alignByFactDuration_whenMultipleJobsWithFactGreaterThanPlan_shouldAddMultipleMaintenance() {
        // Given - multiple jobs with fact > plan
        LocalDateTime start1 = LocalDateTime.of(2025, 1, 15, 10, 0);
        LocalDateTime end1 = LocalDateTime.of(2025, 1, 15, 11, 0); // Plan: 60 minutes

        Job job1 = createJob("J1", start1, end1);
        job1.setCameraStart(start1);
        job1.setCameraEnd(start1.plusMinutes(75)); // Fact: 75 > 60, diff = 15

        LocalDateTime start2 = LocalDateTime.of(2025, 1, 15, 11, 0);
        LocalDateTime end2 = LocalDateTime.of(2025, 1, 15, 12, 30); // Plan: 90 minutes

        Job job2 = createJob("J2", start2, end2);
        job2.setCameraStart(start2);
        job2.setCameraEnd(start2.plusMinutes(120)); // Fact: 120 > 90, diff = 30

        line.getJobs().add(job1);
        line.getJobs().add(job2);
        schedule.getJobs().add(job1);
        schedule.getJobs().add(job2);

        alignSolutionService.alignByFactDuration(schedule);

        // Then - should add maintenance jobs in reverse order (from last to first)
        ArgumentCaptor<MaintenanceRequest> requestCaptor = ArgumentCaptor.forClass(MaintenanceRequest.class);
        verify(maintenanceJob, times(2)).addMaintenanceJob(eq(schedule), requestCaptor.capture());

        List<MaintenanceRequest> requests = requestCaptor.getAllValues();
        // First call (for job2, processed last)
        assertEquals(2, requests.get(0).getInsertIndex()); // After job2 at index 1
        assertEquals(30, requests.get(0).getDurationMinutes());
        assertTrue(requests.get(0).getMaintenanceNote().contains("J2"));

        // Second call (for job1, processed first)
        assertEquals(1, requests.get(1).getInsertIndex()); // After job1 at index 0
        assertEquals(15, requests.get(1).getDurationMinutes());
        assertTrue(requests.get(1).getMaintenanceNote().contains("J1"));
    }

    @Test
    void alignByFactDuration_whenCameraTimesNull_shouldSkipJob() {
        // Given - job without camera times
        LocalDateTime start = LocalDateTime.of(2025, 1, 15, 10, 0);
        LocalDateTime end = LocalDateTime.of(2025, 1, 15, 11, 0);

        Job job = createJob("J1", start, end);
        // cameraStart and cameraEnd are null
        line.getJobs().add(job);
        schedule.getJobs().add(job);

        alignSolutionService.alignByFactDuration(schedule);
        verify(maintenanceJob, never()).addMaintenanceJob(any(), any());
    }

    @Test
    void alignByFactDuration_whenPlanTimesNull_shouldReturnZeroPlanMinutes() {
        // Given - job with camera times but null plan times
        LocalDateTime start = LocalDateTime.of(2025, 1, 15, 10, 0);

        Job job = createJob("J1", null, null); // No plan times
        job.setCameraStart(start);
        job.setCameraEnd(start.plusMinutes(60)); // Fact: 60 minutes
        line.getJobs().add(job);
        schedule.getJobs().add(job);

        alignSolutionService.alignByFactDuration(schedule);

        // Then - plan is 0, fact is 60, so diff = 60, should add maintenance
        ArgumentCaptor<MaintenanceRequest> requestCaptor = ArgumentCaptor.forClass(MaintenanceRequest.class);
        verify(maintenanceJob).addMaintenanceJob(eq(schedule), requestCaptor.capture());
        assertEquals(60, requestCaptor.getValue().getDurationMinutes());
    }

    @Test
    void alignByFactDuration_whenCeilMinutesRoundsUp_shouldUseRoundedValue() {
        // Given - durations that need rounding up (e.g., 90.5 seconds = 2 minutes)
        LocalDateTime start = LocalDateTime.of(2025, 1, 15, 10, 0);
        LocalDateTime end = LocalDateTime.of(2025, 1, 15, 10, 1); // Plan: 1 minute (60 seconds)

        Job job = createJob("J1", start, end);
        job.setCameraStart(start);
        job.setCameraEnd(start.plusSeconds(90)); // Fact: 90 seconds = 1.5 minutes, rounded up to 2 minutes
        line.getJobs().add(job);
        schedule.getJobs().add(job);

        alignSolutionService.alignByFactDuration(schedule);

        // Then - diff = 2 - 1 = 1 minute (rounded)
        ArgumentCaptor<MaintenanceRequest> requestCaptor = ArgumentCaptor.forClass(MaintenanceRequest.class);
        verify(maintenanceJob).addMaintenanceJob(eq(schedule), requestCaptor.capture());
        assertEquals(1, requestCaptor.getValue().getDurationMinutes());
    }

    @Test
    void alignByFactDuration_whenJobNotFoundInLine_shouldSkip() {
        // Given - job exists but not found in line.getJobs() (edge case)
        LocalDateTime start = LocalDateTime.of(2025, 1, 15, 10, 0);
        LocalDateTime end = LocalDateTime.of(2025, 1, 15, 11, 0);

        Job job = createJob("J1", start, end);
        job.setCameraStart(start);
        job.setCameraEnd(start.plusMinutes(90));
        // Job not added to line.getJobs() but exists in schedule.getJobs()
        schedule.getJobs().add(job);

        alignSolutionService.alignByFactDuration(schedule);
        // Then - should skip because indexOf returns -1
        verify(maintenanceJob, never()).addMaintenanceJob(any(), any());
    }

    @Test
    void alignLineStartByFact_whenNoJobs_shouldNotAddMaintenance() {
        // Given - empty line
        line.setJobs(new ArrayList<>());

        alignSolutionService.alignLineStartByFact(schedule);
        verify(maintenanceJob, never()).addMaintenanceJob(any(), any());
    }

    @Test
    void alignLineStartByFact_whenJobsNull_shouldNotAddMaintenance() {
        // Given - null jobs list
        line.setJobs(null);

        alignSolutionService.alignLineStartByFact(schedule);
        verify(maintenanceJob, never()).addMaintenanceJob(any(), any());
    }

    @Test
    void alignLineStartByFact_whenNoFactJobs_shouldNotAddMaintenance() {
        // Given - jobs without camera times
        LocalDateTime start = LocalDateTime.of(2025, 1, 15, 10, 0);
        LocalDateTime end = LocalDateTime.of(2025, 1, 15, 11, 0);

        Job job = createJob("J1", start, end);
        // No camera times
        line.getJobs().add(job);
        schedule.getJobs().add(job);

        alignSolutionService.alignLineStartByFact(schedule);
        verify(maintenanceJob, never()).addMaintenanceJob(any(), any());
    }

    @Test
    void alignLineStartByFact_whenFactBeforePlan_shouldNotAddMaintenance() {
        // Given - fact start is before plan start → Duration.between(ref, fact) is negative → ceilMinutes returns 0
        LocalDateTime planStart = LocalDateTime.of(2025, 1, 15, 10, 0);
        LocalDateTime factStart = LocalDateTime.of(2025, 1, 15, 9, 30); // 30 minutes earlier
        LocalDateTime end = LocalDateTime.of(2025, 1, 15, 11, 0);

        Job job = createJob("J1", planStart, end);
        job.setCameraStart(factStart);
        job.setCameraEnd(end);
        line.getJobs().add(job);
        schedule.getJobs().add(job);

        alignSolutionService.alignLineStartByFact(schedule);
        // Then - negative diff yields 0, should not add
        verify(maintenanceJob, never()).addMaintenanceJob(any(), any());
    }

    @Test
    void alignLineStartByFact_whenFactAfterPlan_shouldAddMaintenance() {
        // Given - fact start is after plan start → positive diff → add maintenance to fill gap
        LocalDateTime planStart = LocalDateTime.of(2025, 1, 15, 10, 0);
        LocalDateTime factStart = LocalDateTime.of(2025, 1, 15, 10, 30); // 30 minutes later
        LocalDateTime end = LocalDateTime.of(2025, 1, 15, 11, 0);

        Job job = createJob("J1", planStart, end);
        job.setCameraStart(factStart);
        job.setCameraEnd(end);
        line.getJobs().add(job);
        schedule.getJobs().add(job);

        alignSolutionService.alignLineStartByFact(schedule);
        // Then - diff = 30 min, should add maintenance
        ArgumentCaptor<MaintenanceRequest> requestCaptor = ArgumentCaptor.forClass(MaintenanceRequest.class);
        verify(maintenanceJob).addMaintenanceJob(eq(schedule), requestCaptor.capture());

        MaintenanceRequest request = requestCaptor.getValue();
        assertEquals("line1", request.getLineId());
        assertEquals(0, request.getInsertIndex());
        assertEquals(30, request.getDurationMinutes());
        assertEquals(8, request.getMaintenanceTypeId());
        assertTrue(request.getMaintenanceNote().contains("Сдвиг старта линии по факту"));
        assertTrue(request.getMaintenanceNote().contains("J1"));
    }

    @Test
    void alignLineStartByFact_whenFactEqualsPlan_shouldNotAddMaintenance() {
        // Given - fact start equals plan start
        LocalDateTime start = LocalDateTime.of(2025, 1, 15, 10, 0);
        LocalDateTime end = LocalDateTime.of(2025, 1, 15, 11, 0);

        Job job = createJob("J1", start, end);
        job.setCameraStart(start);
        job.setCameraEnd(end);
        line.getJobs().add(job);
        schedule.getJobs().add(job);

        alignSolutionService.alignLineStartByFact(schedule);
        // Then - diff is 0, should not add
        verify(maintenanceJob, never()).addMaintenanceJob(any(), any());
    }

    @Test
    void alignLineStartByFact_whenPreviousJobExists_shouldUsePreviousEndAsReference() {
        // Given - J0 plan 10:30-11:00, J1 plan 10:00-10:30 so earliest plan = J1. Previous = J0, reference = J0 end = 11:00.
        // Fact: J0 camera 11:30, J1 camera 11:30 so earliest fact = 11:30. diff = 30 min → add.
        LocalDateTime j0Start = LocalDateTime.of(2025, 1, 15, 10, 30);
        LocalDateTime j0End = LocalDateTime.of(2025, 1, 15, 11, 0);

        Job previousJob = createJob("J0", j0Start, j0End);
        previousJob.setCameraStart(LocalDateTime.of(2025, 1, 15, 11, 30));
        previousJob.setCameraEnd(LocalDateTime.of(2025, 1, 15, 12, 0));

        LocalDateTime j1Start = LocalDateTime.of(2025, 1, 15, 10, 0);
        LocalDateTime j1End = LocalDateTime.of(2025, 1, 15, 10, 30);

        Job job = createJob("J1", j1Start, j1End);
        job.setCameraStart(LocalDateTime.of(2025, 1, 15, 11, 30));
        job.setCameraEnd(LocalDateTime.of(2025, 1, 15, 12, 0));
        job.setPreviousJob(previousJob);

        line.getJobs().add(previousJob);
        line.getJobs().add(job);
        schedule.getJobs().add(previousJob);
        schedule.getJobs().add(job);

        alignSolutionService.alignLineStartByFact(schedule);

        ArgumentCaptor<MaintenanceRequest> requestCaptor = ArgumentCaptor.forClass(MaintenanceRequest.class);
        verify(maintenanceJob).addMaintenanceJob(eq(schedule), requestCaptor.capture());

        MaintenanceRequest request = requestCaptor.getValue();
        assertEquals(1, request.getInsertIndex());
        assertEquals(30, request.getDurationMinutes()); // 11:30 - 11:00 = 30
    }

    @Test
    void alignLineStartByFact_whenPreviousJobHasNullEnd_shouldUsePlanStartAsReference() {
        // J0 plan 10:30, null end → J1 plan 10:00 so earliest plan = J1. previous = J0, reference = J1 start = 10:00 (J0 end null).
        // Fact: J0 camera 10:20, J1 camera 10:20 → earliest fact = 10:20. diff = 20 min.
        LocalDateTime j0Start = LocalDateTime.of(2025, 1, 15, 10, 30);

        Job previousJob = createJob("J0", j0Start, null);
        previousJob.setCameraStart(LocalDateTime.of(2025, 1, 15, 10, 20));
        previousJob.setCameraEnd(LocalDateTime.of(2025, 1, 15, 11, 0));

        LocalDateTime planStart = LocalDateTime.of(2025, 1, 15, 10, 0);
        LocalDateTime end = LocalDateTime.of(2025, 1, 15, 10, 30);

        Job job = createJob("J1", planStart, end);
        job.setCameraStart(LocalDateTime.of(2025, 1, 15, 10, 20));
        job.setCameraEnd(LocalDateTime.of(2025, 1, 15, 11, 0));
        job.setPreviousJob(previousJob);

        line.getJobs().add(previousJob);
        line.getJobs().add(job);
        schedule.getJobs().add(previousJob);
        schedule.getJobs().add(job);

        alignSolutionService.alignLineStartByFact(schedule);

        ArgumentCaptor<MaintenanceRequest> requestCaptor = ArgumentCaptor.forClass(MaintenanceRequest.class);
        verify(maintenanceJob).addMaintenanceJob(eq(schedule), requestCaptor.capture());

        MaintenanceRequest request = requestCaptor.getValue();
        assertEquals(20, request.getDurationMinutes()); // 10:20 - 10:00 = 20 minutes
    }

    @Test
    void alignLineStartByFact_whenNoPreviousJob_shouldUsePlanStartAsReference() {
        LocalDateTime planStart = LocalDateTime.of(2025, 1, 15, 10, 0);
        LocalDateTime factStart = LocalDateTime.of(2025, 1, 15, 10, 15); // 15 minutes after plan start → positive diff
        LocalDateTime end = LocalDateTime.of(2025, 1, 15, 11, 0);

        Job job = createJob("J1", planStart, end);
        job.setCameraStart(factStart);
        job.setCameraEnd(end);
        line.getJobs().add(job);
        schedule.getJobs().add(job);

        alignSolutionService.alignLineStartByFact(schedule);

        ArgumentCaptor<MaintenanceRequest> requestCaptor = ArgumentCaptor.forClass(MaintenanceRequest.class);
        verify(maintenanceJob).addMaintenanceJob(eq(schedule), requestCaptor.capture());

        MaintenanceRequest request = requestCaptor.getValue();
        assertEquals(15, request.getDurationMinutes()); // 10:15 - 10:00 = 15 minutes
    }

    @Test
    void alignLineStartByFact_whenMultipleFactJobs_shouldUseEarliest() {
        // Earliest plan = J1 (10:00), reference = 10:00. Earliest fact = min(10:15, 10:30) = 10:15. diff = 15 min.
        LocalDateTime planStart1 = LocalDateTime.of(2025, 1, 15, 10, 0);
        LocalDateTime factStart1 = LocalDateTime.of(2025, 1, 15, 10, 15);
        LocalDateTime end1 = LocalDateTime.of(2025, 1, 15, 11, 0);

        Job job1 = createJob("J1", planStart1, end1);
        job1.setCameraStart(factStart1);
        job1.setCameraEnd(end1);

        LocalDateTime planStart2 = LocalDateTime.of(2025, 1, 15, 11, 0);
        LocalDateTime factStart2 = LocalDateTime.of(2025, 1, 15, 10, 30);
        LocalDateTime end2 = LocalDateTime.of(2025, 1, 15, 12, 0);

        Job job2 = createJob("J2", planStart2, end2);
        job2.setCameraStart(factStart2);
        job2.setCameraEnd(end2);

        line.getJobs().add(job1);
        line.getJobs().add(job2);
        schedule.getJobs().add(job1);
        schedule.getJobs().add(job2);

        alignSolutionService.alignLineStartByFact(schedule);

        ArgumentCaptor<MaintenanceRequest> requestCaptor = ArgumentCaptor.forClass(MaintenanceRequest.class);
        verify(maintenanceJob).addMaintenanceJob(eq(schedule), requestCaptor.capture());

        MaintenanceRequest request = requestCaptor.getValue();
        assertEquals(0, request.getInsertIndex());
        assertEquals(15, request.getDurationMinutes()); // 10:15 - 10:00 = 15
        assertTrue(request.getMaintenanceNote().contains("J1"));
    }

    @Test
    void alignLineStartByFact_whenJobNotFoundInLine_shouldSkip() {
        // Given - job exists but not found in line.getJobs()
        LocalDateTime planStart = LocalDateTime.of(2025, 1, 15, 10, 0);
        LocalDateTime factStart = LocalDateTime.of(2025, 1, 15, 9, 30);
        LocalDateTime end = LocalDateTime.of(2025, 1, 15, 11, 0);

        Job job = createJob("J1", planStart, end);
        job.setCameraStart(factStart);
        job.setCameraEnd(end);
        // Job not added to line.getJobs() but exists in schedule.getJobs()
        schedule.getJobs().add(job);

        alignSolutionService.alignLineStartByFact(schedule);
        // Then - should skip because indexOf returns -1
        verify(maintenanceJob, never()).addMaintenanceJob(any(), any());
    }

    @Test
    void alignLineStartByFact_whenFactJobMissingStartProductionDateTime_shouldSkip() {
        // Given - job with camera times but no startProductionDateTime
        LocalDateTime factStart = LocalDateTime.of(2025, 1, 15, 9, 30);
        LocalDateTime end = LocalDateTime.of(2025, 1, 15, 11, 0);

        Job job = createJob("J1", null, end); // No startProductionDateTime
        job.setCameraStart(factStart);
        job.setCameraEnd(end);
        line.getJobs().add(job);
        schedule.getJobs().add(job);

        alignSolutionService.alignLineStartByFact(schedule);
        // Then - should skip because startProductionDateTime is null
        verify(maintenanceJob, never()).addMaintenanceJob(any(), any());
    }

    @Test
    void alignLineStartByFact_whenCeilMinutesRoundsUp_shouldUseRoundedValue() {
        // Fact 30 seconds after plan → Duration 30 sec → ceilMinutes rounds up to 1 minute
        LocalDateTime planStart = LocalDateTime.of(2025, 1, 15, 10, 0);
        LocalDateTime factStart = LocalDateTime.of(2025, 1, 15, 10, 0, 30);
        LocalDateTime end = LocalDateTime.of(2025, 1, 15, 11, 0);

        Job job = createJob("J1", planStart, end);
        job.setCameraStart(factStart);
        job.setCameraEnd(end);
        line.getJobs().add(job);
        schedule.getJobs().add(job);

        alignSolutionService.alignLineStartByFact(schedule);

        ArgumentCaptor<MaintenanceRequest> requestCaptor = ArgumentCaptor.forClass(MaintenanceRequest.class);
        verify(maintenanceJob).addMaintenanceJob(eq(schedule), requestCaptor.capture());

        MaintenanceRequest request = requestCaptor.getValue();
        assertEquals(1, request.getDurationMinutes()); // 30 seconds rounded up to 1 minute
    }

    @Test
    void alignLineStartByFact_whenMultipleLines_shouldProcessAllLines() {
        Line line2 = new Line("line2", "Line 2");
        line2.setJobs(new ArrayList<>());
        schedule.setLines(List.of(line, line2));

        LocalDateTime planStart = LocalDateTime.of(2025, 1, 15, 10, 0);
        LocalDateTime factStart = LocalDateTime.of(2025, 1, 15, 10, 15); // after plan → positive diff
        LocalDateTime end = LocalDateTime.of(2025, 1, 15, 11, 0);

        Job job1 = createJob("J1", planStart, end);
        job1.setCameraStart(factStart);
        job1.setCameraEnd(end);
        line.getJobs().add(job1);
        schedule.getJobs().add(job1);

        Job job2 = createJob("J2", planStart, end);
        job2.setCameraStart(factStart);
        job2.setCameraEnd(end);
        line2.getJobs().add(job2);
        schedule.getJobs().add(job2);

        alignSolutionService.alignLineStartByFact(schedule);

        verify(maintenanceJob, times(2)).addMaintenanceJob(eq(schedule), any());
    }

    private Job createJob(String id, LocalDateTime startProductionDateTime, LocalDateTime endDateTime) {
        Job job = new Job();
        job.setId(id);
        job.setStartProductionDateTime(startProductionDateTime);
        job.setEndDateTime(endDateTime);
        job.setDuration(Duration.ofMinutes(60));
        return job;
    }
}
