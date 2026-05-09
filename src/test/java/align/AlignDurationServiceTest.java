package align;

import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.scheduleoperations.MaintenanceJob;
import org.acme.foodpackaging.scheduleoperations.utils.SpeedCacheUtils;
import org.acme.foodpackaging.service.align.AlignDurationService;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import builder.JobTestBuilder;
import builder.LineTestBuilder;
import builder.ProductTestBuilder;
import builder.ScheduleTestBuilder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AlignDurationServiceTest {

    @InjectMocks
    AlignDurationService alignDuration;

    @Mock
    MaintenanceJob maintenanceJob;

    private PackagingSchedule solution;
    private Line line;
    private LocalDateTime lineSDateTime;

    @BeforeEach
    void setUp() {

        lineSDateTime = LocalDateTime.of(2026, 3, 6, 10, 0);
        line = LineTestBuilder.aLine("line1", lineSDateTime).build();

        solution = ScheduleTestBuilder.aSchedule()
                .withJobs(getFirstTestJob(), getSecondTestJob())
                .withLines(line)
                .build();

        line.setJobs(solution.getJobs());
        // Init speeds
        Map<String, Map<String, Pair<Integer, Integer>>> speeds = new HashMap<>();

        Map<String, Pair<Integer, Integer>> productSpeeds = new HashMap<>();
        productSpeeds.put("CLASSIC", Pair.of(100, 100));

        speeds.put("line1", productSpeeds);

        SpeedCacheUtils.init(speeds);
    }

    private Job getFirstTestJob() {

        Product product = ProductTestBuilder.aProduct("P1").withType("CLASSIC").build();
        product.setCleaningDurations(Map.of(product, Duration.ZERO));

        // 10:00
        // 10:00
        // 10:30
        // 10:00-11:30

        return JobTestBuilder.aJob()
                .withId("J1")
                .withStartCleaningDateTime(lineSDateTime) // 10:00
                .withStartProductionDateTime(lineSDateTime) // 10:00
                .withEndDateTime(lineSDateTime.plusMinutes(30)) // 10:30
                .withCamera(lineSDateTime, lineSDateTime.plusMinutes(90)) // 10:00-11:30
                .withLine(line)
                .withLineIdFact("line1")
                .withQuantity(2600)
                .withProduct(product)
                .build();
    }

    private Job getSolutionFirstJob() {
        return solution.getJobs().getFirst();
    }

    private Job getSolutionSecondJob() {
        return solution.getJobs().getLast();
    }

    private Job getSecondTestJob() {
        Job j2 = getFirstTestJob();
        j2.setStartCleaningDateTime(j2.getEndDateTime()); // 10:30
        j2.setStartProductionDateTime(j2.getEndDateTime()); // 10:30
        j2.setEndDateTime(j2.getStartProductionDateTime().plusMinutes(30)); // 11:00
        j2.setCameraStart(LocalDateTime.of(2026, 3, 6, 11, 30)); // 11:30-12:30
        j2.setCameraEnd(j2.getCameraStart().plusMinutes(60));

        return j2;
    }

    // ============================================================
    // alignByFactDuration
    // ============================================================

    @Test
    void alignByFactDuration_sucсes() {

        alignDuration.alignByFactDuration(solution);
        Job firstJob = getSolutionFirstJob();
        Job secondJob = getSolutionSecondJob();

        assertEquals(60, firstJob.getDelayDuration().toMinutes());
        assertEquals(30, secondJob.getDelayDuration().toMinutes());

        assertEquals(90, firstJob.getDuration().toMinutes());
        assertEquals(60, secondJob.getDuration().toMinutes());
    }

    @Test
    void alignByFactDuration_NullLines() {
        solution.setLines(null);
        alignDuration.alignByFactDuration(solution);
        assertNull(solution.getLines());
    }

    @Test
    void alignByFactDuration_NullJobs() {
        solution.setJobs(null);
        alignDuration.alignByFactDuration(solution);
        assertNull(solution.getJobs());
    }

    @Test
    void alignByFactDuration_emptyJobs() {
        solution.setJobs(new ArrayList<>());
        alignDuration.alignByFactDuration(solution);
        assertTrue(solution.getJobs().isEmpty());
    }

    @Test
    void alignByFactDuration_whenLineJobsListIsNull() {

        solution.getLines().getFirst().setJobs(null);
        alignDuration.alignByFactDuration(solution);
        assertNull(solution.getJobs().getFirst().getDelayDuration());
    }

    @Test
    void alignByFactDuration_whenLineJobsListIsEmpty() {

        solution.getLines().getFirst().setJobs(new ArrayList<>());
        alignDuration.alignByFactDuration(solution);
        assertNull(solution.getJobs().getFirst().getDelayDuration());
    }

    @Test
    void alignByFactDuration_delayDurationIsNotNull() {
        solution.getJobs().getFirst().setDelayDuration(Duration.ofMinutes(20));
        ;
        alignDuration.alignByFactDuration(solution);
        assertEquals(Duration.ofMinutes(20), solution.getJobs().getFirst().getDelayDuration());
    }

    @Test
    void alignByFactDuration_NullCameraData() {
        Job firstJob = getSolutionFirstJob();
        firstJob.setCameraStart(null);
        firstJob.setCameraEnd(null);

        alignDuration.alignByFactDuration(solution);

        assertEquals(LocalDateTime.of(2026, 3, 6, 10, 30), firstJob.getEndDateTime());
        assertEquals(LocalDateTime.of(2026, 3, 6, 10, 30), firstJob.getPlanEndDateTime());
        assertNull(solution.getJobs().getFirst().getDelayDuration());
    }

    @Test
    void alignByFactDuration_whenLineIdFactIsNull() {
        solution.getJobs().getFirst().setLineIdFact(null);
        solution.getJobs().getLast().setLineIdFact(null);

        alignDuration.alignByFactDuration(solution);

        assertEquals(30, solution.getJobs().getFirst().getDuration().toMinutes());
        assertNull(solution.getJobs().getFirst().getDelayDuration());
    }

    // ============================================================
    // findTimeIntersections
    // ============================================================

    @Test
    void findTimeIntersections_shouldCountFullInsideOverlap() {

        Job firstJob = getSolutionFirstJob();
        Job secondJob = getSolutionSecondJob();

        LocalDateTime cameraStart1 = LocalDateTime.of(2026, 3, 6, 10, 10);
        LocalDateTime cameraEnd1 = LocalDateTime.of(2026, 3, 6, 10, 40);

        secondJob.setCameraStart(cameraStart1);
        secondJob.setCameraEnd(cameraEnd1);

        alignDuration.alignByFactDuration(solution);

        assertEquals(Duration.ofMinutes(30), firstJob.getDelayDuration());
        assertNull(secondJob.getDelayDuration());

        assertEquals(Duration.ofMinutes(60), firstJob.getDuration());
        assertEquals(Duration.ofMinutes(30), secondJob.getDuration());
    }

    @Test
    void alignByFactDuration_shouldTrimIntersectionByParentBounds() {

        Job firstJob = getSolutionFirstJob();
        Job secondJob = getSolutionSecondJob();

        secondJob.setCameraStart(
                LocalDateTime.of(2026, 3, 6, 10, 30));

        secondJob.setCameraEnd(
                LocalDateTime.of(2026, 3, 6, 12, 0));

        alignDuration.alignByFactDuration(solution);

        assertEquals(Duration.ofMinutes(30),
                firstJob.getDuration());

        assertNull(firstJob.getDelayDuration());
    }
}
