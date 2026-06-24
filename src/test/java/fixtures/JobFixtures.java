package fixtures;

import builder.JobTestBuilder;
import builder.ProductTestBuilder;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.record.CleaningResult;
import org.apache.commons.lang3.tuple.Pair;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.HashMap;
import java.util.Map;

public final class JobFixtures {

    private JobFixtures() {
    }

    public static Pair<Job, Job> jobsWithCleanings() {

        LocalDateTime start = LocalDateTime.of(2026, Month.MAY, 9, 10, 0);
        LocalDateTime cameraStartLeft = LocalDateTime.of(2026, Month.MAY, 9, 11, 0);
        LocalDateTime cameraStartRight = LocalDateTime.of(2026, Month.MAY, 9, 12, 0);

        Job j1 = JobTestBuilder.aJob()
                .withId("J1")
                .withIdBatch("12345678")
                .withCamera(cameraStartLeft, cameraStartLeft.plusHours(1))
                .withQuantity(2600)
                .build();

        Job j2 = JobTestBuilder.aJob()
                .withId("J2")
                .withIdBatch("87654321")
                .withCamera(cameraStartRight, cameraStartRight.plusHours(1))
                .withQuantity(2600)
                .build();

        Product p1 = ProductTestBuilder.aProduct("P1")
                .withType("TYPE_A")
                .build();

        Product p2 = ProductTestBuilder.aProduct("P2")
                .withType("TYPE_B")
                .build();

        Map<Product, Duration> cleaningDurationsP1 =
                Map.of(
                        p1, Duration.ZERO,
                        p2, Duration.ofMinutes(20)
                );

        Map<Product, Duration> cleaningDurationsP2 =
                Map.of(
                        p2, Duration.ZERO,
                        p1, Duration.ofMinutes(20)
                );

        Map<Product, CleaningResult> results =
                Map.of(
                        p1, new CleaningResult(30, false),
                        p2, new CleaningResult(30, false)
                );

        p1.setCleaningDurations(cleaningDurationsP1);
        p1.setCleaningResults(new HashMap<>(results));

        p2.setCleaningDurations(cleaningDurationsP2);
        p2.setCleaningResults(new HashMap<>(results));

        Line line = new Line("L007", "line007");
        line.setStartDateTime(start);

        j1.setProduct(p1);
        j2.setProduct(p2);

        j1.setLine(line);
        j2.setLine(line);

        j2.setPreviousJob(j1);

        return Pair.of(j1, j2);
    }
}
