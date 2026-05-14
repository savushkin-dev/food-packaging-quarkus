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
import java.util.*;

import static org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils.fixLineJobs;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AlignCleaningServiceTest {

    @InjectMocks
    AlignCleaningService cleaningService;

    private PackagingSchedule solution;

    @BeforeEach
    void setUp() {
        initSpeedCache();

        LocalDateTime lineStart = LocalDateTime.of(2026, 4, 24, 10, 0);

        LocalDateTime j1CameraStart = LocalDateTime.of(2026, 4, 24, 12, 0);
        LocalDateTime j1CameraEnd = LocalDateTime.of(2026, 4, 24, 13, 0);

        LocalDateTime j2CameraStart = LocalDateTime.of(2026, 4, 24, 14, 0);
        LocalDateTime j2CameraEnd = LocalDateTime.of(2026, 4, 24, 15, 0);

        Job j1 = buildTestJob("J1", j1CameraStart, j1CameraEnd);
        Job j2 = buildTestJob("J2", j2CameraStart, j2CameraEnd);

        Line line = LineTestBuilder.aLine("line1", lineStart)
                .withJobs(j1, j2).build();

        solution = ScheduleTestBuilder.aSchedule()
                .withLines(line)
                .withJobs(j1, j2).build();

        solution.setProducts(buildProductsList());
        solution.getJobs().getFirst().setProduct(solution.getProducts().getFirst());
        solution.getJobs().getLast().setProduct(solution.getProducts().get(1));
        fixLineJobs(line);
    }

    // ============================================================
    // alignCleanings
    // ============================================================

    @Test
    void alignCleanings_shouldCalculateCleaningDelay() {
        cleaningService.alignCleanings(solution);

        assertEquals(Duration.ofMinutes(30), solution.getJobs().getLast().getCleaningDelay());
        assertNull(solution.getJobs().getFirst().getCleaningDelay());
    }

    @Test
    void alignCleanings_shouldSetStartForLineWithoutFact() {
        Line emptyLine = LineTestBuilder.aLine("line2", solution.getLines().getFirst().getStartDateTime())
                .withEmptyJobs().build();

        solution.getLines().add(emptyLine);
        cleaningService.alignCleanings(solution);

        assertEquals(LocalDateTime.of(2026, 4, 24, 12, 0),
                solution.getLines().getFirst().getStartDateTime());
        assertEquals(LocalDateTime.of(2026, 4, 24, 12, 0),
                solution.getLines().getLast().getStartDateTime());
    }

    @Test
    void alignCleanings_whenAlreadyHasCleaningDelay() {
        cleaningService.alignCleanings(solution);

        solution.getJobs().getLast().setCleaningDelay(Duration.ZERO);
        assertEquals(Duration.ZERO, solution.getJobs().getLast().getCleaningDelay());
    }

    @Test
    void alignCleanings_whenPreviousIsMaintenance() {

        Product mProduct = solution.getMaintenanceProduct();
        Job j1 = solution.getJobs().getFirst();
        Job j2 = solution.getJobs().getLast();
        Job m1 = buildMaintenanceTestJob("M1", 1, Duration.ofMinutes(60), mProduct);
        buildSolutionWithNewJobs(j1, m1, j2);

        cleaningService.alignCleanings(solution);
        assertEquals(2, solution.getJobs().size());
        assertEquals(1, solution.getDeletedMaintenance().size());
        assertEquals(Duration.ofMinutes(30), solution.getJobs().getLast().getCleaningDelay());

    }

    @Test
    void alignCleanings_whenPreviousIsMaintenanceWithCleaning() {
        Product mProduct = solution.getMaintenanceProduct();
        Job j1 = solution.getJobs().getFirst();
        Job j2 = solution.getJobs().getLast();
        Job m1 = buildMaintenanceTestJob("M1", 8, Duration.ofMinutes(60), mProduct);
        Job m2 = buildMaintenanceTestJob("M2", 2, Duration.ofMinutes(60), mProduct);
        buildSolutionWithNewJobs(j1, m1, m2, j2);

        cleaningService.alignCleanings(solution);
        assertEquals(2, solution.getJobs().size());
        assertEquals(2, solution.getDeletedMaintenance().size());
        assertEquals(Duration.ofMinutes(30), solution.getJobs().getLast().getCleaningDelay());
    }

    @Test
    void alignCleanings_whenPreviousWithoutFact() {
        Job j1 = solution.getJobs().getFirst();
        Job j2 = solution.getJobs().getLast();
        Job j3 = buildTestJob("J3", null, null);
        Product p3 = solution.getProducts().stream()
                .filter(p -> p.getId().equals("P3"))
                .findFirst().orElse(null);

        j3.setProduct(p3);
        buildSolutionWithNewJobs(j1, j3, j2);
        cleaningService.alignCleanings(solution);

        assertEquals(LocalDateTime.of(2026, 4, 24, 14, 0),
                solution.getJobs().getLast().getStartProductionDateTime());

        assertEquals(Duration.ofMinutes(20), solution.getJobs().getLast().getCleaningDelay());
    }

    @Test
    void alignCleanings_whenSolutionIsNull() {
        assertDoesNotThrow(() -> cleaningService.alignCleanings(null));
    }

    @Test
    void alignCleanings_whenLineJobListIsNull() {
        solution.getLines().getFirst().setJobs(null);
        cleaningService.alignCleanings(solution);
        assertNull( solution.getJobs().getLast().getCleaningDelay());
    }

    @Test
    void alignCleanings_whenLinesListIsNull() {
        solution.setLines(null);
        cleaningService.alignCleanings(solution);
        assertDoesNotThrow(() -> cleaningService.alignCleanings(solution));
    }

    @Test
    void alignCleanings_whenWhenFactJobsIsEmpty() {
        solution.getJobs().getFirst().setCameraStart(null);
        solution.getJobs().getFirst().setCameraEnd(null);
        solution.getJobs().getLast().setCameraStart(null);
        solution.getJobs().getLast().setCameraEnd(null);

        cleaningService.alignCleanings(solution);
       assertEquals(LocalDateTime.of(2026,4, 24, 10,0),
               solution.getLines().getFirst().getStartDateTime());
    }

    @Test
    void alignCleanings_whenJobSizeLessTwo() {
        solution.getJobs().removeLast();
        solution.getLines().getFirst().getJobs().removeLast();

        cleaningService.alignCleanings(solution);
        assertNull(solution.getJobs().getLast().getCleaningDelay());
        assertEquals(solution.getJobs().getFirst().getCameraStart(),
                solution.getLines().getFirst().getStartDateTime());
    }

    @Test
    void alignCleanings_whenDeletedMaintenanceIsNull() {
        solution.setDeletedMaintenance(null);

        cleaningService.alignCleanings(solution);
        assertNull(solution.getJobs().getLast().getCleaningDelay());
        assertEquals(solution.getJobs().getFirst().getCameraStart(),
                solution.getLines().getFirst().getStartDateTime());
    }

    @Test
    void alignCleanings_theSameProduct() {
       solution.getJobs().getLast().setProduct(solution.getJobs().getFirst().getProduct());

        cleaningService.alignCleanings(solution);
        assertNull(solution.getJobs().getLast().getCleaningDelay());
        assertEquals(solution.getJobs().getFirst().getCameraStart(),
                solution.getLines().getFirst().getStartDateTime());
    }

    @Test
    void alignCleanings_firstWithoutFact() {
        Job j2 = solution.getJobs().getLast();
        Job j3 = buildTestJob("J3", null, null);
        Product p3 = solution.getProducts().stream()
                .filter(p -> p.getId().equals("P3"))
                .findFirst().orElse(null);

        j3.setProduct(p3);
        buildSolutionWithNewJobs(j3, j2);
        cleaningService.alignCleanings(solution);

        assertEquals(solution.getJobs().getLast().getCameraStart(),
                solution.getJobs().getLast().getStartProductionDateTime());

        assertEquals(solution.getJobs().getFirst().getStartProductionDateTime(),
                solution.getLines().getFirst().getStartDateTime());
    }

    // ============================================================
    // addition methods
    // ============================================================

    void initSpeedCache() {
        SpeedCacheUtils.init(Map.of(
                "line1", Map.of("TYPE_A", Pair.of(100, 50), "10003",
                        Pair.of(100, 50), "TYPE_B", Pair.of(100, 80))
        ));
    }

    private void setCleaningResults(List<Product> products) {
        Map<Product, CleaningResult> results = new HashMap<>();
        for (Product product : products) {
            results.put(product, new CleaningResult(0, false));
        }
        products.forEach(p -> p.setCleaningResults(results));
    }

    private Job buildTestJob(String id, LocalDateTime cameraStart, LocalDateTime cameraEnd) {
        return JobTestBuilder.aJob()
                .withId(id)
                .withLineIdFact("line1")
                .withQuantity(2600)
                .withCamera(cameraStart, cameraEnd).build();
    }

    private Job buildMaintenanceTestJob(String id, int maintenanceTypeId, Duration duration, Product product) {
        return JobTestBuilder.aJob()
                .withId(id)
                .withMaintenanceTypeId(maintenanceTypeId)
                .asMaintenance()
                .withDurationMinutes(duration.toMinutes())
                .withLineIdFact("line1")
                .withProduct(product).build();
    }

    private Product buildTestProduct(String id, String type) {
        return ProductTestBuilder.aProduct(id)
                .withType(type)
                .build();
    }

    private void buildSolutionWithNewJobs(Job... jobs) {
        solution.setJobs(new ArrayList<>(List.of(jobs)));
        Line line = solution.getLines().getFirst();
        line.setJobs(new ArrayList<>(List.of(jobs)));

        for (Job job : solution.getJobs()) {
            job.setLine(line);
        }
        fixLineJobs(line);
    }

    private List<Product> buildProductsList() {
        Product p1 = buildTestProduct("P1", "TYPE_A");
        Product p2 = buildTestProduct("P2", "TYPE_B");
        Product p3 = buildTestProduct("P3", "10003");
        Product mProduct = solution.getMaintenanceProduct();

        Map<Product, Duration> cleaningDurationsP1 = Map.of(
                p1, Duration.ZERO,
                p3, Duration.ofMinutes(20),
                mProduct, Duration.ZERO,
                p2, Duration.ofMinutes(30)
        );

        Map<Product, Duration> cleaningDurationsP2 = Map.of(
                p2, Duration.ZERO,
                p3, Duration.ofMinutes(20),
                mProduct, Duration.ZERO,
                p1, Duration.ofMinutes(30)

        );

        Map<Product, Duration> cleaningDurationsMp = Map.of(
                p2, Duration.ZERO,
                mProduct, Duration.ZERO,
                p1, Duration.ZERO
        );

        Map<Product, Duration> cleaningDurationsP3 = Map.of(
                p1, Duration.ofMinutes(20),
                p2, Duration.ofMinutes(20),
                mProduct, Duration.ZERO
        );


        p1.setCleaningDurations(cleaningDurationsP1);
        p2.setCleaningDurations(cleaningDurationsP2);
        p3.setCleaningDurations(cleaningDurationsP3);
        mProduct.setCleaningDurations(cleaningDurationsMp);

        List<Product> products = List.of(p1, p2, p3, mProduct);
        setCleaningResults(products);
        return products;
    }
}
