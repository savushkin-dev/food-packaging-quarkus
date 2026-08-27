package service.jobs;

import builder.JobTestBuilder;
import builder.LineTestBuilder;
import builder.ScheduleTestBuilder;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.dto.DelayNoteRequest;
import org.acme.foodpackaging.service.jobs.JobNoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(MockitoExtension.class)
class JobNoteServiceTest {

    @InjectMocks
    JobNoteService jobNoteService;

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
    void writeDelayNote_success() {
        DelayNoteRequest request = new DelayNoteRequest();
        request.setLineId("L1");
        request.setIndex(0);
        request.setDelayNote("Note");

        jobNoteService.writeDelayNote(request, schedule);
        assertEquals("Note", job.getDelayNote());
    }

    @Test
    void writeCleaningDelayNote_success() {
        DelayNoteRequest request = new DelayNoteRequest();
        request.setLineId("L1");
        request.setIndex(0);
        request.setDelayNote("Cleaning");

        jobNoteService.writeCleaningDelayNote(request, schedule);
        assertEquals("Cleaning", job.getCleaningDelayNote());
    }

    @Test
    void writeDelayNote_whenLineNotFound() {
        schedule.getLines().getFirst().setId("L2");
        DelayNoteRequest request = new DelayNoteRequest();
        request.setLineId("L1");
        request.setDelayNote("Note");

        jobNoteService.writeDelayNote(request, schedule);

        assertNull(job.getDelayNote());
    }

    @Test
    void writeDelayNote_whenLineJobsNull() {
        schedule.getLines().getFirst().setJobs(null);
        DelayNoteRequest request = new DelayNoteRequest();
        request.setLineId("L1");
        request.setIndex(0);
        request.setDelayNote("Note");

        jobNoteService.writeDelayNote(request, schedule);

        assertNull(schedule.getLines().getFirst().getJobs());
    }
}

