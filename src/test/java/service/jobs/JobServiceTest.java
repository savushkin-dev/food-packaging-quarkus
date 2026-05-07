package service.jobs;

import builder.*;
import org.acme.foodpackaging.domain.*;
import org.acme.foodpackaging.dto.DelayNoteRequest;
import org.acme.foodpackaging.exception.service.ProductNotFoundException;
import org.acme.foodpackaging.persistence.load.LoadDataService;
import org.acme.foodpackaging.repository.jobs.JobRepository;
import org.acme.foodpackaging.record.DbJobRow;
import org.acme.foodpackaging.service.jobs.JobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    private PackagingSchedule schedule;

    @BeforeEach
    void setUp() {

        schedule = ScheduleTestBuilder.aSchedule()
                .withWorkCalendar(
                        LocalDate.of(2025, 1, 15),
                        LocalDateTime.of(2025, 1, 15, 8, 0)
                )
                .withLines(
                        LineTestBuilder
                                .aLine("L1", LocalDateTime.of(2025, 1, 15, 8, 0))
                                .build()
                )
                .withSpeed("L1", "CLASSIC", 100)
                .withEmptyJobs()
                .withEmptyJobMap()
                .build();
    }

    @Test
    void buildJobsOnLines_shouldThrowException_whenProductNotFound() {

        DbJobRow jobRow = DbJobRowBuilder.aRow()
                .withKmc("UNKNOWN")
                .build();

        Product product = ProductTestBuilder
                .aProduct("KMC1").withType("CLASSIC")
                .build();

        when(loadDataService.getProducts())
                .thenReturn(Map.of("KMC1", product));

        when(jobRepository.getDbJobRowMap(any(), any()))
                .thenReturn(Map.of(123L, jobRow));

        assertThrows(ProductNotFoundException.class,
                () -> jobService.buildJobsOnLines(schedule));
    }

    @Test
    void writeDelayNote_success() {

        Job job = JobTestBuilder.aJob()
                .withId("1")
                .build();

        PackagingSchedule solution = ScheduleTestBuilder.aSchedule()
                .withWorkCalendar(
                        LocalDate.of(2025, 1, 15),
                        LocalDateTime.of(2025, 1, 15, 8, 0)
                )
                .withLines(
                        LineTestBuilder.aLine("L1", LocalDateTime.now())
                                .withJobs(job)
                                .build()
                )
                .withEmptyJobs()
                .withEmptyJobMap()
                .build();

        DelayNoteRequest request = new DelayNoteRequest();
        request.setLineId("L1");
        request.setIndex(0);
        request.setDelayNote("Note");

        jobService.writeDelayNote(request, solution);

        assertEquals("Note", job.getDelayNote());
    }

    @Test
    void writeCleaningDelayNote_success() {

        Job job = JobTestBuilder.aJob()
                .withId("1")
                .build();

        PackagingSchedule solution = ScheduleTestBuilder.aSchedule()
                .withWorkCalendar(
                        LocalDate.of(2025, 1, 15),
                        LocalDateTime.of(2025, 1, 15, 8, 0)
                )
                .withLines(
                        LineTestBuilder.aLine("L1", LocalDateTime.now())
                                .withJobs(job)
                                .build()
                )
                .withEmptyJobs()
                .withEmptyJobMap()
                .build();

        DelayNoteRequest request = new DelayNoteRequest();
        request.setLineId("L1");
        request.setIndex(0);
        request.setDelayNote("Cleaning");

        jobService.writeCleaningDelayNote(request, solution);

        assertEquals("Cleaning", job.getCleaningDelayNote());
    }

    @Test
    void writeDelayNote_whenLineNotFound() {

        Job job = JobTestBuilder.aJob().build();

        PackagingSchedule solution = ScheduleTestBuilder.aSchedule()
                .withWorkCalendar(
                        LocalDate.of(2025, 1, 15),
                        LocalDateTime.of(2025, 1, 15, 8, 0)
                )
                .withLines(
                        LineTestBuilder.aLine("L2", LocalDateTime.now())
                                .withJobs(job)
                                .build()
                )
                .withEmptyJobs()
                .withEmptyJobMap()
                .build();

        DelayNoteRequest request = new DelayNoteRequest();
        request.setLineId("L1");
        request.setDelayNote("Note");

        jobService.writeDelayNote(request, solution);

        assertNull(job.getDelayNote());
    }

    @Test
    void writeDelayNote_whenLineJobsNull() {

        Line line = LineTestBuilder.aLine("L1", LocalDateTime.now())
                .build();

        line.setJobs(null);

        PackagingSchedule solution = ScheduleTestBuilder.aSchedule()
                .withWorkCalendar(
                        LocalDate.of(2025, 1, 15),
                        LocalDateTime.of(2025, 1, 15, 8, 0)
                )
                .withLines(line)
                .withEmptyJobs()
                .withEmptyJobMap()
                .build();

        DelayNoteRequest request = new DelayNoteRequest();
        request.setLineId("L1");
        request.setIndex(0);
        request.setDelayNote("Note");

        jobService.writeDelayNote(request, solution);

        assertTrue(line.getJobs() == null || line.getJobs().isEmpty());
    }
}
