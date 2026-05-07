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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(MockitoExtension.class)
class AlignCleaningServiceTest {

    @InjectMocks
    AlignCleaningService cleaningService;

    private PackagingSchedule solution;

    void initSpeedCache() {
        SpeedCacheUtils.init(Map.of(
                "line1", Map.of("TYPE_A", Pair.of(100, 50), "TYPE_B", Pair.of(200, 80))
        ));
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

    private Job getFirstTestJob(){
        LocalDateTime startProduction = LocalDateTime.of(2026, 4, 24, 10,0);
        LocalDateTime startCleaning = LocalDateTime.of(2026, 4, 24, 10,0);

        LocalDateTime cameraStart = LocalDateTime.of(2026,4, 24, 12,0);
        LocalDateTime cameraEnd = LocalDateTime.of(2026,4,24,13,0);

        return JobTestBuilder.aJob()
                .withId("J1")
                .withLineIdFact("line1")
                .withProduct(getFirstTestProduct())
                .withQuantity(5000)
                .withStartProductionDateTime(startProduction)
                .withStartCleaningDateTime(startCleaning)
                .withCamera(cameraStart, cameraEnd).build();
    }

    private Job getSecondTestJob(){
        LocalDateTime startProduction = LocalDateTime.of(2026, 4, 24, 14,0);
        LocalDateTime startCleaning = LocalDateTime.of(2026, 4,24, 13, 30);

        LocalDateTime cameraStart = LocalDateTime.of(2026,4, 24, 14,0);
        LocalDateTime cameraEnd = LocalDateTime.of(2026,4,24,15,0);

        return JobTestBuilder.aJob()
                .withId("J2")
                .withLineIdFact("line1")
                .withProduct(getSecondTestProduct())
                .withQuantity(6000)
                .withStartProductionDateTime(startProduction)
                .withStartCleaningDateTime(startCleaning)
                .withCamera(cameraStart, cameraEnd).build();
    }

    private Product getFirstTestProduct(){
        return ProductTestBuilder.aProduct("P1")
                .withType("TYPE_A").build();
    }

    private Product getSecondTestProduct(){
        return ProductTestBuilder.aProduct("P2")
                .withType("TYPE_B")
                .withCleaningResult(getFirstTestProduct(),
                        new CleaningResult(30, false))
                .build();
    }
}
