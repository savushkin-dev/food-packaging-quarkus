package align;

import builder.JobTestBuilder;
import builder.LineTestBuilder;
import builder.ProductTestBuilder;
import builder.ScheduleTestBuilder;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.record.CleaningResult;
import org.acme.foodpackaging.scheduleoperations.utils.SpeedCacheUtils;
import org.acme.foodpackaging.service.align.AlignCleaningService;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Map;

import static org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils.fixLineJobs;
import static org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils.fixPinnedJobs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(MockitoExtension.class)
class AlignCleaningServiceTest {

    @InjectMocks
    AlignCleaningService cleaningService;

    private PackagingSchedule solution;

    void initSpeedCache() {
        SpeedCacheUtils.init(Map.of(
                "line1", Map.of("TYPE_A", Pair.of(100, 50), "10003",
                        Pair.of(100, 50),"TYPE_B", Pair.of(200, 80))
        ));
    }

    private Job getFirstTestJob(){
        LocalDateTime startProduction = LocalDateTime.of(2026, 4, 24, 10,0);
        LocalDateTime startCleaning = LocalDateTime.of(2026, 4, 24, 10,0);

        LocalDateTime cameraStart = LocalDateTime.of(2026,4, 24, 12,0);
        LocalDateTime cameraEnd = LocalDateTime.of(2026,4,24,13,0);

        return buildTestJob("J2", buildTestProduct("P1", "TYPE_A", null, null),
                4600, startProduction, startCleaning, cameraStart, cameraEnd);
    }

    private Job getSecondTestJob(){
        LocalDateTime startProduction = LocalDateTime.of(2026, 4, 24, 14,0);
        LocalDateTime startCleaning = LocalDateTime.of(2026, 4,24, 13, 30);

        LocalDateTime cameraStart = LocalDateTime.of(2026,4, 24, 14,0);
        LocalDateTime cameraEnd = LocalDateTime.of(2026,4,24,15,0);

        return buildTestJob("J1", buildTestProduct("P2", "TYPE_B", getFirstTestJob().getProduct(),
                new CleaningResult(30, false)), 5600, startProduction, startCleaning, cameraStart, cameraEnd);
    }

    private Job getThirdTestJob(){
        LocalDateTime startProduction = LocalDateTime.of(2026, 4, 24, 11,0);
        LocalDateTime startCleaning = LocalDateTime.of(2026, 4,24, 10, 50);

        return buildTestJob("J1", buildTestProduct("P3", "10003", getFirstTestJob().getProduct(),
                new CleaningResult(10, false)), 600, startProduction, startCleaning, null, null);
    }

    private Job buildTestJob(String id, Product product, int quantity,
                                  LocalDateTime startProductionDateTime, LocalDateTime startCleaningDateTime,
                                  LocalDateTime cameraStart, LocalDateTime cameraEnd){
        return JobTestBuilder.aJob()
                .withId(id)
                .withLineIdFact("line1")
                .withProduct(product)
                .withQuantity(quantity)
                .withStartProductionDateTime(startProductionDateTime)
                .withStartCleaningDateTime(startCleaningDateTime)
                .withCamera(cameraStart, cameraEnd).build();
    }

    private Job buildMaintenanceTestJob(String id, Duration duration, Product product,
                                  LocalDateTime start){
        return JobTestBuilder.aJob()
                .withId(id)
                .asMaintenance()
                .withDurationMinutes(duration.toMinutes())
                .startingAt(start)
                .withLineIdFact("line1")
                .withProduct(product).build();
    }

    private Product buildTestProduct(String id, String type, Product previous, CleaningResult result){
        return ProductTestBuilder.aProduct(id)
                .withType(type)
                .withCleaningResult(previous, result)
                .build();
    }

    @BeforeEach
    void setUp(){
        initSpeedCache();
        LocalDateTime lineStart = LocalDateTime.of(2026, 4, 24, 10,0);
        Job j1 = getFirstTestJob();
        Job j2 = getSecondTestJob();

        Line line = LineTestBuilder.aLine("line1", lineStart)
                .withJobs(j1, j2).build();

        solution = ScheduleTestBuilder.aSchedule()
                .withLines(line)
                .withJobs(j1,j2).build();
    }

    @Test
    void alignCleanings_shouldCalculateCleaningDelay(){
        cleaningService.alignCleanings(solution);

        assertEquals(Duration.ofMinutes(30), solution.getJobs().getLast().getCleaningDelay());
        assertNull(solution.getJobs().getFirst().getCleaningDelay());
    }

    @Test
    void alignCleanings_whenPreviousIsMaintenance(){
        Product maintenanceProduct = solution.getMaintenanceProduct();
        Job maintenanceJob = buildMaintenanceTestJob("M1", Duration.ofMinutes(60),
                maintenanceProduct, LocalDateTime.of(2026,4, 24, 12,0));
        solution.getJobs().add(maintenanceJob);
        solution.setJobs(solution.getJobs().stream().sorted(Comparator.comparing(Job::getStartProductionDateTime)).toList());
        fixLineJobs(solution.getLines().getFirst());
        cleaningService.alignCleanings(solution);

        assertEquals(Duration.ofMinutes(30), solution.getJobs().getLast().getCleaningDelay());
        assertNull(solution.getJobs().getFirst().getCleaningDelay());
    }

    @Test
    void alignCleanings_whenPreviousIsMaintenanceWithCleaning(){
        Product maintenanceProduct = solution.getMaintenanceProduct();
        Job maintenanceJob = buildMaintenanceTestJob("M1", Duration.ofMinutes(60),
                maintenanceProduct, LocalDateTime.of(2026,4, 24, 12,0));
        maintenanceJob.setMaintenanceTypeId(8);

        Job maintenanceJobCleaning = buildMaintenanceTestJob("M2", Duration.ofMinutes(60),
                maintenanceProduct, LocalDateTime.of(2026,4, 24, 12,30));
        maintenanceJobCleaning.setMaintenanceTypeId(2);

        solution.getJobs().add(maintenanceJob);
        solution.getJobs().add(maintenanceJobCleaning);
        solution.setJobs(solution.getJobs().stream().sorted(Comparator.comparing(Job::getStartProductionDateTime)).toList());
        cleaningService.alignCleanings(solution);

        assertEquals(Duration.ofMinutes(30), solution.getJobs().getLast().getCleaningDelay());
        assertNull(solution.getJobs().getFirst().getCleaningDelay());
    }
}
