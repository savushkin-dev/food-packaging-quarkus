package service.lines;

import org.acme.foodpackaging.domain.*;
import org.acme.foodpackaging.service.lines.LineSchedulingService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LineSchedulingServiceTest {

    LineSchedulingService schedulingService = new LineSchedulingService();

    @Test
    void initLineTimesWithEmptyJobs() {
        PackagingSchedule solution = new PackagingSchedule();
        solution.setJobs(new ArrayList<>());

        Line line1 = new Line("L1", "Line 1");
        line1.setJobs(new ArrayList<>());
        Line line2 = new Line("L2", "Line 2");
        line2.setJobs(new ArrayList<>());

        solution.setLines(List.of(line1, line2));
        solution.setWorkCalendar(new WorkCalendar(LocalDate.of(2025,12,27)));

        schedulingService.initJobListOnLine(solution);

        solution.getLines().forEach(line -> {
            assertNotNull(line.getStartDateTime());
            assertNotNull(line.getMaxEndTime());
        });
    }

    @Test
    void assignJobsWithLineId() {
        Line line = new Line("L1", "Line 1");
        line.setJobs(new ArrayList<>());

        Product product = new Product("VAN", "Vanilla");
        // инициализация cleaningDurations для product
        Map<Product, Duration> cleaning = new HashMap<>();
        cleaning.put(product, Duration.ZERO);
        product.setCleaningDurations(cleaning);

        Job job = new Job();
        job.setLineId("L1");
        job.setProduct(product);
        job.setStartProductionDateTime(LocalDateTime.of(2025,12,27,10,0));
        job.setEndDateTime(LocalDateTime.of(2025,12,27,12,0));

        solutionSetUp(line, job);
    }

    @Test
    void fallbackStartTimeForEmptyLines() {
        Line emptyLine = new Line("L1", "Line 1");
        emptyLine.setJobs(new ArrayList<>());
        Line busyLine = new Line("L2", "Line 2");
        busyLine.setJobs(new ArrayList<>());

        Product product = new Product("VAN", "Vanilla");
        product.setCleaningDurations(Map.of(product, Duration.ZERO));

        Job job = new Job();
        job.setLineId("L2");
        job.setProduct(product);
        LocalDateTime fixedTime = LocalDateTime.of(2025, 12, 27, 15, 35);
        job.setStartProductionDateTime(fixedTime);
        job.setEndDateTime(fixedTime.plusHours(2));

        PackagingSchedule solution = new PackagingSchedule();
        solution.setJobs(List.of(job));
        solution.setLines(List.of(emptyLine, busyLine));
        solution.setWorkCalendar(new WorkCalendar(LocalDate.of(2025,12,27)));

        schedulingService.initJobListOnLine(solution);

        assertNotNull(emptyLine.getStartDateTime());
        // maxEndTime у busyLine должно быть >= endTime последнего job
        assertEquals(job.getEndDateTime().plusHours(20), busyLine.getMaxEndTime());
    }

    @Test
    void jobsAreSortedAndMaxEndTimeIsSet() {
        Line line = new Line("L1", "Line 1");
        line.setJobs(new ArrayList<>());

        Product product = new Product("VAN", "Vanilla");
        product.setCleaningDurations(Map.of(product, Duration.ZERO));

        Job job1 = new Job();
        job1.setLineId("L1");
        job1.setProduct(product);
        job1.setStartProductionDateTime(LocalDateTime.of(2025,12,27,15,35));
        job1.setEndDateTime(LocalDateTime.of(2025,12,27,17,35));

        Job job2 = new Job();
        job2.setLineId("L1");
        job2.setProduct(product);
        job2.setStartProductionDateTime(LocalDateTime.of(2025,12,27,14,35));
        job2.setEndDateTime(LocalDateTime.of(2025,12,27,16,35));

        PackagingSchedule solution = new PackagingSchedule();
        solution.setJobs(List.of(job1, job2));
        solution.setLines(List.of(line));
        solution.setWorkCalendar(new WorkCalendar(LocalDate.of(2025,12,27)));

        schedulingService.initJobListOnLine(solution);

        assertEquals(2, line.getJobs().size());
        assertSame(line, job1.getLine());
        assertSame(line, job2.getLine());

        Job lastJob = line.getJobs().get(1);
        assertEquals(lastJob.getEndDateTime().plusHours(20), line.getMaxEndTime());
    }

    private void solutionSetUp(Line line, Job job) {
        PackagingSchedule solution = new PackagingSchedule();
        solution.setJobs(List.of(job));
        solution.setLines(List.of(line));
        solution.setWorkCalendar(new WorkCalendar(LocalDate.of(2025,12,27)));

        schedulingService.initJobListOnLine(solution);

        assertEquals(1, line.getJobs().size());
        assertSame(line, job.getLine());
    }
}

