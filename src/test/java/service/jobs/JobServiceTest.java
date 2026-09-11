package service.jobs;

import builder.*;
import org.acme.foodpackaging.domain.*;

import org.acme.foodpackaging.dto.row.jobs.JobRow;
import org.acme.foodpackaging.domain.value.FactKey;
import org.acme.foodpackaging.dto.row.jobs.FactProductionRow;
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

import static org.acme.foodpackaging.domain.value.FactKey.EventType.START_FACT;
import static org.acme.foodpackaging.domain.value.FactKey.EventType.START_CAMERA;
import static org.acme.foodpackaging.domain.value.FactKey.EventType.END_CAMERA;
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
                LocalDateTime lineStartDateTime = LocalDateTime.of(2025, Month.JANUARY, 15, 8, 0);
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
                job.setIdBatch("BATCH1");

                LocalDateTime dateTime = LocalDateTime.of(2026, Month.AUGUST, 27, 9, 0);
                JobListAssembler.JobAssemblyResult result =
                        new JobListAssembler.JobAssemblyResult(List.of(job), Map.of(), List.of());
                when(jobListAssembler.assemble(schedule)).thenReturn(result);

                FactProductionRow startFact = new FactProductionRow("BATCH1", "343355", dateTime,
                        233, START_FACT.code(), dateTime, "13344");
                when(jobRepository.getFactProductionRowMap(any(), any()))
                        .thenReturn(Map.of(new FactKey("BATCH1", START_FACT), startFact));

                jobService.buildJobsOnLines(schedule);

                assertEquals("BATCH1", job.getIdBatch());
                assertEquals("13344", job.getLineIdFact());
                assertEquals(dateTime, job.getDtv());
                assertEquals(dateTime, job.getStartProductionDateTimeFact());
        }

        @Test
        void buildJobsOnLines_appliesCameraFacts() {
                job.setProduct(ProductTestBuilder.aProduct("P1").withType("CLASSIC").build());
                job.setNp(1);
                job.setIdBatch("BATCH1");

                LocalDateTime cameraStart = LocalDateTime.of(2026, Month.AUGUST, 27, 9, 0);
                LocalDateTime cameraEnd = LocalDateTime.of(2026, Month.AUGUST, 27, 9, 30);

                JobListAssembler.JobAssemblyResult result =
                        new JobListAssembler.JobAssemblyResult(List.of(job), Map.of(), List.of());
                when(jobListAssembler.assemble(schedule)).thenReturn(result);

                FactProductionRow startCameraFact = new FactProductionRow(
                        "BATCH1", "P1", null, 1, START_CAMERA.code(), cameraStart, null);
                FactProductionRow endCameraFact = new FactProductionRow(
                        "BATCH1", "P1", null, 1, END_CAMERA.code(), cameraEnd, null);

                when(jobRepository.getFactProductionRowMap(any(), any()))
                        .thenReturn(Map.of(
                                new FactKey("BATCH1", START_CAMERA), startCameraFact,
                                new FactKey("BATCH1", END_CAMERA), endCameraFact
                        ));

                jobService.buildJobsOnLines(schedule);

                assertEquals(cameraStart, job.getCameraStart());
                assertEquals(cameraEnd, job.getCameraEnd());
        }

        @Test
        void buildJobsOnLines_skipsJobsWithoutProduct() {
                job.setProduct(null);

                JobListAssembler.JobAssemblyResult result =
                        new JobListAssembler.JobAssemblyResult(List.of(job), Map.of(), List.of());
                when(jobListAssembler.assemble(schedule)).thenReturn(result);
                when(jobRepository.getFactProductionRowMap(any(), any())).thenReturn(Map.of());

                assertDoesNotThrow(() -> jobService.buildJobsOnLines(schedule));
                assertNull(job.getIdBatch());
        }

        @Test
        void buildJobsOnLines_noMatchingFactRows_leavesJobUnchanged() {
                job.setProduct(ProductTestBuilder.aProduct("P1").withType("CLASSIC").build());
                job.setNp(1);
                job.setIdBatch("BATCH1");

                JobListAssembler.JobAssemblyResult result =
                        new JobListAssembler.JobAssemblyResult(List.of(job), Map.of(), List.of());
                when(jobListAssembler.assemble(schedule)).thenReturn(result);
                when(jobRepository.getFactProductionRowMap(any(), any())).thenReturn(Map.of());

                jobService.buildJobsOnLines(schedule);

                assertEquals("BATCH1", job.getIdBatch());
                assertNull(job.getCameraStart());
                assertNull(job.getCameraEnd());
        }
}