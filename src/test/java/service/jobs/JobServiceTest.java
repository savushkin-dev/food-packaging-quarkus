package service.jobs;

import builder.*;
import org.acme.foodpackaging.domain.*;
import org.acme.foodpackaging.dto.DbMaintenanceRow;
import org.acme.foodpackaging.dto.DelayNoteRequest;
import org.acme.foodpackaging.exception.service.ProductNotFoundException;
import org.acme.foodpackaging.persistence.load.LoadDataService;
import org.acme.foodpackaging.repository.jobs.JobRepository;
import org.acme.foodpackaging.record.DbJobRow;
import org.acme.foodpackaging.service.jobs.JobInfoService;
import org.acme.foodpackaging.service.jobs.JobRefreshService;
import org.acme.foodpackaging.service.jobs.JobService;
import org.acme.foodpackaging.service.lines.LineService;
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

    @Mock
    LoadDataService loadDataService;
    @Mock
    JobRepository jobRepository;
    @Mock
    JobInfoService jobInfoService;
    @Mock
    JobRefreshService jobRefreshService;
    @Mock
    LineService lineService;

    private PackagingSchedule schedule;
    private Job job;

    @BeforeEach
    void setUp() {

        LocalDateTime lineStartDateTime = LocalDateTime.of(2025, 1, 15, 8, 0);
        job = JobTestBuilder.aJob()
                .withId("J1")
                .build();

        schedule = ScheduleTestBuilder.aSchedule()
                .withWorkCalendar(
                        lineStartDateTime.toLocalDate(),
                        lineStartDateTime
                )
                .withLines(
                        LineTestBuilder
                                .aLine("L1", lineStartDateTime)
                                .withJobs(job)
                                .build()
                )
                .withSpeed("L1", "CLASSIC", 100)
                .withEmptyJobs()
                .withEmptyJobMap()
                .build();

    }

    private Product getTestProduct() {
        return ProductTestBuilder.aProduct("P1").withType("CLASSIC").build();
    }

    private DbJobRow getTestDbJobRow() {
        return DbJobRowBuilder.aRow()
                .withSnpz(123L)
                .withKmc("P1")
                .withLineId("L1").build();
    }

    private DbMaintenanceRow getTestDbMaintenanceRow(int duration, String note) {
        return DbMaintenanceRowBuilder.aRow()
                .withId(123L)
                .withDuration(duration)
                .withNote(note).build();
    }

    @Test
    void buildJobsOnLines_createJobById() {
        when(loadDataService.getProducts())
                .thenReturn(Map.of("P1", getTestProduct()));

        when(jobRepository.getDbJobRowMap(any(), any()))
                .thenReturn(Map.of(123L, getTestDbJobRow()));

        when(jobRepository.getMaintenanceData(any(), any()))
                .thenReturn(Collections.emptyList());

        when(jobRepository.getDelayData(any(), any()))
                .thenReturn(Map.of(123L, getTestDbMaintenanceRow(22, "Delay note")));

        when(jobRepository.getCleaningDelayData(any(), any()))
                .thenReturn(Map.of(123L, getTestDbMaintenanceRow(12, "Cleaning delay note")));

        when(jobRepository.getDbJobRowMap(any(), any()))
                .thenReturn(Map.of(123L, getTestDbJobRow()));

        when(jobInfoService.generateIdBatch(any(), anyLong())).thenReturn("1212");
        doNothing().when(jobRefreshService).refreshStaleCameraEndFromPmLog(any());
        doNothing().when(lineService).initLineStartEnd(any());

        schedule.getJobs().clear();
        schedule.getLines().getFirst().getJobs().clear();

        jobService.buildJobsOnLines(schedule);
        assertEquals(1, schedule.getJobs().size());
        assertEquals(1, schedule.getAllJobsById().size());
        assertEquals(1, schedule.getLines().getFirst().getJobs().size());
        assertEquals("123", schedule.getJobs().getFirst().getId());

        assertEquals(22, schedule.getJobs().getFirst().getDelayDuration().toMinutes());
        assertEquals(12, schedule.getJobs().getFirst().getCleaningDelay().toMinutes());

        assertEquals("Delay note", schedule.getJobs().getFirst().getDelayNote());
        assertEquals("Cleaning delay note", schedule.getJobs().getFirst().getCleaningDelayNote());
    }


    @Test
    void buildJobsOnLines_shouldThrowException_whenProductNotFound() {

        DbJobRow jobRow = DbJobRowBuilder.aRow()
                .withKmc("UNKNOWN")
                .build();

        when(loadDataService.getProducts())
                .thenReturn(Map.of("P1", getTestProduct()));

        when(jobRepository.getDbJobRowMap(any(), any()))
                .thenReturn(Map.of(123L, jobRow));

        assertThrows(ProductNotFoundException.class,
                () -> jobService.buildJobsOnLines(schedule));
    }

    @Test
    void writeDelayNote_success() {

        DelayNoteRequest request = new DelayNoteRequest();
        request.setLineId("L1");
        request.setIndex(0);
        request.setDelayNote("Note");

        jobService.writeDelayNote(request, schedule);
        assertEquals("Note", job.getDelayNote());
    }

    @Test
    void writeCleaningDelayNote_success() {

        schedule.setJobs(List.of());
        schedule.setAllJobsById(Map.of());
        DelayNoteRequest request = new DelayNoteRequest();
        request.setLineId("L1");
        request.setIndex(0);
        request.setDelayNote("Cleaning");

        jobService.writeCleaningDelayNote(request, schedule);
        assertEquals("Cleaning", job.getCleaningDelayNote());
    }

    @Test
    void writeDelayNote_whenLineNotFound() {

        Job job = JobTestBuilder.aJob().build();

        schedule.getLines().getFirst().setId("L2");
        DelayNoteRequest request = new DelayNoteRequest();
        request.setLineId("L1");
        request.setDelayNote("Note");

        jobService.writeDelayNote(request, schedule);

        assertNull(job.getDelayNote());
    }

    @Test
    void writeDelayNote_whenLineJobsNull() {

        schedule.setAllJobsById(Map.of());
        schedule.setJobs(List.of());
        schedule.getLines().getFirst().setJobs(null);
        DelayNoteRequest request = new DelayNoteRequest();
        request.setLineId("L1");
        request.setIndex(0);
        request.setDelayNote("Note");

        jobService.writeDelayNote(request, schedule);

        assertNull(schedule.getLines().getFirst().getJobs());
    }
}
