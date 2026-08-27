package service.jobs;

import builder.*;
import org.acme.foodpackaging.domain.*;

import org.acme.foodpackaging.dto.bdvzpmc.JobRow;
import org.acme.foodpackaging.record.FactKey;
import org.acme.foodpackaging.record.FactProductionRow;
import org.acme.foodpackaging.repository.jobs.JobRepository;
import org.acme.foodpackaging.service.jobs.*;
import org.acme.foodpackaging.service.lines.LineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Map;

import static org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils.START_FACT_EVENT_TYPE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JobService business logic.
 * Tests are isolated with mocked dependencies.
 */
@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @InjectMocks
    JobService jobService;

    @Mock JobRepository jobRepository;
    @Mock JobListAssembler jobListAssembler;
    @Mock
    JobEnrichmentService jobEnrichmentService;
    @Mock JobRefreshService jobRefreshService;
    @Mock LineService lineService;

    private PackagingSchedule schedule;
    private Job job;

    @BeforeEach
    void setUp() {
        LocalDateTime lineStartDateTime = LocalDateTime.of(2025, 1, 15, 8, 0);
        job = JobTestBuilder.aJob().withId("J1").build();

        schedule = ScheduleTestBuilder.aSchedule()
                .withWorkCalendar(lineStartDateTime.toLocalDate(), lineStartDateTime)
                .withLines(LineTestBuilder.aLine("L1", lineStartDateTime).withJobs(job).build())
                .withSpeed("L1", "CLASSIC", 100)
                .withEmptyJobs()
                .withEmptyJobMap()
                .build();
    }

    @Test
    void buildJobsOnLines_delegatesToAssemblerAndSetsScheduleState() {
        List<Job> jobs = List.of(job);
        Map<Long, Job> allJobsById = Map.of(123L, job);
        List<JobRow> jobRows = List.of(JobRowBuilder.aRow().withSnpz(123L).withKmc("P1").withLineId("L1").build());

        JobListAssembler.JobAssemblyResult result =
                new JobListAssembler.JobAssemblyResult(jobs, allJobsById, jobRows);

        when(jobListAssembler.assemble(schedule)).thenReturn(result);
        when(jobRepository.getFactProductionRowMap(any(), any())).thenReturn(Map.of());

        List<JobRow> returned = jobService.buildJobsOnLines(schedule);

        assertEquals(jobRows, returned);
        assertEquals(jobs, schedule.getJobs());
        assertEquals(allJobsById, schedule.getAllJobsById());

        verify(jobEnrichmentService).enrichCameraFactsFromPmLog(schedule);
        verify(jobEnrichmentService).assignIdBatches(schedule);
        verify(jobRefreshService).refreshStaleCameraEndFromPmLog(schedule);
        verify(lineService).initLineStartEnd(schedule);
    }

    @Test
    void buildJobsOnLines_appliesFactProductionData() {
        job.setProduct(ProductTestBuilder.aProduct("P1").withType("CLASSIC").build());
        job.setNp(1);

        LocalDateTime dateTime = LocalDateTime.of(2026, Month.AUGUST, 27, 9, 0);
        JobListAssembler.JobAssemblyResult result =
                new JobListAssembler.JobAssemblyResult(List.of(job), Map.of(), List.of());
        when(jobListAssembler.assemble(schedule)).thenReturn(result);

        FactProductionRow startFact = new FactProductionRow("P1", "343355", dateTime,
                233, 10, dateTime, "13344");
        when(jobRepository.getFactProductionRowMap(any(), any()))
                .thenReturn(Map.of(new FactKey("P1", 1, START_FACT_EVENT_TYPE), startFact));

        jobService.buildJobsOnLines(schedule);

        assertEquals("P1", job.getIdBatch());
    }
}
