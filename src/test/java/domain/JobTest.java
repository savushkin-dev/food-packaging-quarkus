package domain;

import builder.JobTestBuilder;
import builder.LineTestBuilder;
import builder.ProductTestBuilder;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.dto.DbMaintenanceRow;
import org.acme.foodpackaging.record.CleaningResult;
import org.acme.foodpackaging.record.DbJobRow;
import org.acme.foodpackaging.scheduleoperations.utils.CleaningDurationUtils;
import org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils;
import org.acme.foodpackaging.scheduleoperations.utils.SpeedCacheUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.acme.foodpackaging.scheduleoperations.MaintenanceJob.createMaintenanceProduct;
import static org.junit.jupiter.api.Assertions.*;

class JobTest {

    @BeforeEach
    void initSpeedCache() {
        SpeedCacheUtils.init(Map.of(
                "line1", Map.of("TYPE_A", Pair.of(100, 50), "TYPE_B", Pair.of(200, 80))
        ));
    }

    private Product createProductWithType(String type) {
        Product p = new Product("id", "name");
        p.setType(type);
        return p;
    }

    @Test
    void setMaintenanceFields() {
        LocalDateTime startProductionDateTime = LocalDateTime.of(2025, 1, 15, 9, 0);
        LocalDateTime endDateTime = startProductionDateTime.plusMinutes(60);

        Product product = createMaintenanceProduct();
        Duration duration = Duration.ofMinutes(60);

        DbMaintenanceRow row = new DbMaintenanceRow(
                1L, (short) 0, "1600", startProductionDateTime, endDateTime, 60, 2212L, 4, "Note"
        );
        Job job = Job.fromDbMaintenanceRow(row, "Maintenance Name", product, startProductionDateTime);

        assertEquals("1", job.getId());
        assertEquals("1600", job.getLineId());
        assertEquals("Maintenance Name", job.getName());
        assertEquals(product, job.getProduct());
        // getDuration() returns the duration field only for maintenance jobs
        // For non-maintenance jobs, it calculates from speed/quantity
        job.setMaintenance(true);
        assertEquals(duration, job.getDuration());
        assertEquals(1, job.getPriority());
        assertTrue(job.isPinned());
        assertEquals(startProductionDateTime, job.getStartProductionDateTime());
        assertEquals(startProductionDateTime.plus(duration), job.getEndDateTime());
    }

    @Test
    void setProductionFields() {
        LocalDateTime dti = LocalDateTime.of(2025, 1, 1, 8, 30);
        LocalDateTime startProductionDateTime = LocalDateTime.of(2025, 1, 15, 9, 0);
        LocalDateTime endDateTime = startProductionDateTime.plusMinutes(20);

        Product product = new Product("12", "Vanilla");
        Duration duration = Duration.ofMinutes(20);

        DbJobRow row = new DbJobRow(
                dti, "1623", 34, 5600, 1600.23,
                startProductionDateTime, endDateTime,
                20, 3L, 0, "17000234", "Strawberry", 18, 100, 1);
        Job job = Job.fromDbJobRow(row, product, startProductionDateTime, ScheduleUtils::nameCleaner);

        assertEquals("3", job.getId());
        assertEquals("17000234", job.getLineId());
        assertEquals(product, job.getProduct());
        assertEquals("Strawberry", job.getName());
        assertEquals(34, job.getNp());
        assertEquals(1600.23, job.getMass());
        assertEquals(1, job.getPriority());
        assertEquals(startProductionDateTime, job.getStartProductionDateTime());
        assertEquals(startProductionDateTime.plus(duration), job.getEndDateTime());
        assertEquals(row.emk(), job.getEmk());
        assertEquals(row.placePlan(), job.getPlacePlan());

        assertTrue(job.isHandPackaging());
    }

    //==================================================================================================================
   // fromDbJobRow
    @Test
    void fromDbJobRow_WithNullDuration() {
        LocalDateTime dti = LocalDateTime.of(2025, 1, 1, 8, 30);
        LocalDateTime startProductionDateTime = LocalDateTime.of(2025, 1, 15, 9, 0);
        LocalDateTime endDateTime = startProductionDateTime.plusMinutes(20);
        Product product = new Product("12", "Vanilla");
        DbJobRow row = new DbJobRow(
                dti, "1623", 34, 5600, 1600.23,
                startProductionDateTime, endDateTime,
                null,
                3L, 0, "17000234", "Strawberry", 18, 100, 0
        );
        Job job = Job.fromDbJobRow(row, product, startProductionDateTime, ScheduleUtils::nameCleaner);
        assertEquals(Duration.ZERO, job.getDuration());
        assertFalse(job.isHandPackaging());
    }

    @Test
    void fromDbJobRow_WithNullEmk() {
        LocalDateTime dti = LocalDateTime.of(2025, 1, 1, 8, 30);
        LocalDateTime startProductionDateTime = LocalDateTime.of(2025, 1, 15, 9, 0);
        LocalDateTime endDateTime = startProductionDateTime.plusMinutes(20);
        Product product = new Product("12", "Vanilla");
        DbJobRow row = new DbJobRow(
                dti, "1623", 34, 5600, 1600.23,
                startProductionDateTime, endDateTime,
                20, 3L, 0, "17000234", "Strawberry",
                null,  // emk = null
                100, 0
        );
        Job job = Job.fromDbJobRow(row, product, startProductionDateTime, ScheduleUtils::nameCleaner);
        assertEquals(0, job.getEmk());
    }

    @Test
    void fromDbJobRow_WithNullPlacePlan() {
        LocalDateTime dti = LocalDateTime.of(2025, 1, 1, 8, 30);
        LocalDateTime startProductionDateTime = LocalDateTime.of(2025, 1, 15, 9, 0);
        LocalDateTime endDateTime = startProductionDateTime.plusMinutes(20);
        Product product = new Product("12", "Vanilla");
        DbJobRow row = new DbJobRow(
                dti, "1623", 34, 5600, 1600.23,
                startProductionDateTime, endDateTime,
                20, 3L, 0, "17000234", "Strawberry",
                10,
                null, 1
        );
        Job job = Job.fromDbJobRow(row, product, startProductionDateTime, ScheduleUtils::nameCleaner);
        assertEquals(0, job.getPlacePlan());
    }

    //==================================================================================================================
   // getDuration

    @Test
    void getDuration_maintenanceReturnsDuration() {
        Job job = new Job();
        job.setMaintenance(true);
        job.setDuration(Duration.ofMinutes(30));

        assertEquals(Duration.ofMinutes(30), job.getDuration());
    }

    @Test
    void getDuration_usesSpeedWhenNotHandPackaging() {
        Job job = new Job();
        job.setQuantity(200);
        job.setHandPackaging(false);
        job.setLine(new Line("line1", "Line 1"));
        job.setProduct(createProductWithType("TYPE_A"));

        assertEquals(Duration.ofMinutes(6), job.getDuration()); // ceil(200/100) + 4 = 2 + 4 = 6
    }

    @Test
    void getDuration_usesHandPackagingSpeedWhenHandPackaging() {
        Job job = new Job();
        job.setQuantity(200);
        job.setHandPackaging(true);
        job.setLine(new Line("line1", "Line 1"));
        job.setProduct(createProductWithType("TYPE_A"));

        assertEquals(Duration.ofMinutes(8), job.getDuration()); // ceil(200/50) + 4 = 4 + 4 = 8
    }

    @Test
    void getDuration_returnsZeroWhenSpeedNull() {
        Job job = new Job();
        job.setQuantity(100);
        job.setLine(new Line("line1", "Line 1"));
        job.setProduct(createProductWithType("UNKNOWN_TYPE"));

        assertEquals(Duration.ZERO, job.getDuration());
    }

    @Test
    void getDuration_returnsZeroWhenSpeedZero() {
        SpeedCacheUtils.init(Map.of("line1", Map.of("TYPE_ZERO", Pair.of(0, 0))));
        Job job = new Job();
        job.setQuantity(100);
        job.setLine(new Line("line1", "Line 1"));
        job.setProduct(createProductWithType("TYPE_ZERO"));

        assertEquals(Duration.ZERO, job.getDuration());
    }

    //==================================================================================================================
   // getSpeed
    @Test
    void getSpeed_returnsNullWhenLineNull() {
        Job job = new Job();
        job.setProduct(createProductWithType("TYPE_A"));

        assertNull(job.getSpeed());
    }

    @Test
    void getSpeed_returnsNullWhenProductNull() {
        Job job = new Job();
        job.setLine(new Line("line1", "Line 1"));

        assertNull(job.getSpeed());
    }

    @Test
    void getSpeed_returnsValueFromCache() {
        Job job = new Job();
        job.setLine(new Line("line1", "Line 1"));
        job.setProduct(createProductWithType("TYPE_A"));

        assertEquals(100, job.getSpeed());
    }

    //==================================================================================================================
    // getHandPackagingSpeed
    @Test
    void getHandPackagingSpeed_returnsValueFromCache() {
        Job job = new Job();
        job.setLine(new Line("line1", "Line 1"));
        job.setProduct(createProductWithType("TYPE_A"));

        assertEquals(50, job.getHandPackagingSpeed());
    }


    //==================================================================================================================
   // updateStartCleaningDateTime
    @Test
    void updateStartCleaningDateTime_clearsDatesWhenLineNull() {
        Job job = new Job();
        job.setStartCleaningDateTime(LocalDateTime.of(2025, 1, 15, 8, 0));
        job.setStartProductionDateTime(LocalDateTime.of(2025, 1, 15, 9, 0));
        job.setEndDateTime(LocalDateTime.of(2025, 1, 15, 10, 0));

        job.updateStartCleaningDateTime();

        assertNull(job.getStartCleaningDateTime());
        assertNull(job.getStartProductionDateTime());
        assertNull(job.getEndDateTime());
    }

    @Test
    void updateStartCleaningDateTime_noChangeWhenLineNullAndDatesAlreadyNull() {
        Job job = new Job();
        job.setLine(null);

        job.updateStartCleaningDateTime();

        assertNull(job.getStartCleaningDateTime());
        assertNull(job.getStartProductionDateTime());
        assertNull(job.getEndDateTime());
        assertNull(job.getPlanEndDateTime());
    }

    @Test
    void updateStartCleaningDateTimeWhenNotPLRLC() {

        LocalDateTime start = LocalDateTime.of(2025, 1, 15, 8, 0);

        Product prodA = ProductTestBuilder.aProduct("A").withType("TYPE_A").build();
        Product prodB = ProductTestBuilder.aProduct("B").withType("TYPE_B").build();
        prodB.setCleaningDurations(new HashMap<>(Map.of(prodA, Duration.ofMinutes(25))));
        prodB.setCleaningResults(new HashMap<>(Map.of(prodA, new org.acme.foodpackaging.record.CleaningResult(0, false))));

        Job job1 = JobTestBuilder.aJob()
                .withProduct(prodA)
                .withDurationMinutes(60)
                .asMaintenance()
                .startingAt(start)
                .build();

        Job job2 = JobTestBuilder.aJob()
                .withProduct(prodB)
                .withDurationMinutes(30)
                .asMaintenance()
                .build();

        Line line = LineTestBuilder.aLine("line1", start)
                .withJobs(job1, job2)
                .build();

        ScheduleUtils.fixLineJobs(line);

        LocalDateTime expected = start.plusMinutes(60 + 25);

        assertEquals(expected, job2.getStartProductionDateTime());
    }

    @Test
    void updateStartCleaningDateTime_whenPLRLC() {

        CleaningDurationUtils.init(Map.of("line1", 40));

        LocalDateTime start = LocalDateTime.of(2025, 1, 15, 8, 0);

        Product prodA = ProductTestBuilder.aProduct("A").withType("TYPE_A").build();
        Product prodB = ProductTestBuilder.aProduct("B").withType("TYPE_B")
                .withPLRLC(prodA).build();

        Job job1 = JobTestBuilder.aJob()
                .withProduct(prodA)
                .withDurationMinutes(60)
                .asMaintenance()
                .startingAt(start)
                .build();

        Job job2 = JobTestBuilder.aJob()
                .withProduct(prodB)
                .withDurationMinutes(30)
                .asMaintenance()
                .build();

        Line line = LineTestBuilder.aLine("line1", start)
                .withJobs(job1, job2)
                .build();

        ScheduleUtils.fixLineJobs(line);

        LocalDateTime expected = start.plusMinutes(60 + 40);

        assertEquals(expected, job2.getStartProductionDateTime());
    }

    @Test
    void updateStartCleaningDateTime_whenCleaningResultMissing() {

        LocalDateTime start = LocalDateTime.of(2025, 1, 15, 8, 0);

        Product prodA = ProductTestBuilder.aProduct("A").withType("TYPE_A").build();
        Product prodB = ProductTestBuilder.aProduct("B").withType("TYPE_B")
                .withoutCleaning()
                .build();

        Job job1 = JobTestBuilder.aJob()
                .withProduct(prodA)
                .withDurationMinutes(60)
                .asMaintenance()
                .startingAt(start)
                .build();

        Job job2 = JobTestBuilder.aJob()
                .withProduct(prodB)
                .withDurationMinutes(30)
                .asMaintenance()
                .build();

        Line line = LineTestBuilder.aLine("line1", start)
                .withJobs(job1, job2)
                .build();

        ScheduleUtils.fixLineJobs(line);

        assertEquals(start.plusMinutes(60), job2.getStartProductionDateTime());
    }

    @Test
    void updateStartCleaningDateTime_whenCleaningDelayExists_shouldAddToDuration() {

        LocalDateTime lineStart = LocalDateTime.of(2025, 1, 15, 8, 0);

        Product prodA = ProductTestBuilder.aProduct("A").withType("TYPE_A").build();

        Product prodB = ProductTestBuilder.aProduct("B").withType( "TYPE_B").build();
        prodB.setCleaningDurations(new HashMap<>(Map.of(prodA, Duration.ofMinutes(20))));
        prodB.setCleaningResults(new HashMap<>(Map.of(prodA, new CleaningResult(0, false))));

        Job job1 = JobTestBuilder.aJob()
                .withProduct(prodA)
                .withQuantity(5000)
                .startingAt(lineStart)
                .build();

        Job job2 = JobTestBuilder.aJob()
                .withProduct(prodB)
                .withQuantity(6000)
                .withCleaningDelay(Duration.ofMinutes(10))
                .build();

        Line line = LineTestBuilder.aLine("line1", lineStart)
                .withJobs(job1, job2)
                .build();

        ScheduleUtils.fixLineJobs(line);

        LocalDateTime expected = LocalDateTime.of(2025, 1, 15, 9, 24);

        assertEquals(expected, job2.getStartProductionDateTime());
        assertEquals(10, job2.getCleaningDelay().toMinutes());
    }

    //==================================================================================================================
    // areEqualsPlanAndFactLines
    @Test
    void areEqualsPlanAndFactLines_WhenLineTheSame() {
        LocalDateTime lineStart = LocalDateTime.of(2026, 5, 5, 8, 0);

        Job job1 = JobTestBuilder.aJob()
                .withLineIdFact("line1")
                .startingAt(lineStart)
                .build();

        Line line = LineTestBuilder.aLine("line1", lineStart)
                .withJobs(job1)
                .build();

        assertTrue(job1.areEqualsPlanAndFactLines());
    }

    @Test
    void areEqualsPlanAndFactLines_WhenLineIsNotTheSame() {
        LocalDateTime lineStart = LocalDateTime.of(2026, 5, 5, 8, 0);

        Job job1 = JobTestBuilder.aJob()
                .withLineIdFact("line2")
                .startingAt(lineStart)
                .build();

        Line line = LineTestBuilder.aLine("line1", lineStart)
                .withJobs(job1)
                .build();

        assertFalse(job1.areEqualsPlanAndFactLines());
    }

    @Test
    void areEqualsPlanAndFactLines_WhenLineIsNull() {
        LocalDateTime lineStart = LocalDateTime.of(2026, 5, 5, 8, 0);

        Job job1 = JobTestBuilder.aJob()
                .withLineIdFact("line1")
                .startingAt(lineStart)
                .build();

        assertFalse(job1.areEqualsPlanAndFactLines());
    }

    @Test
    void areEqualsPlanAndFactLines_WhenLineIdFactIsNull() {
        LocalDateTime lineStart = LocalDateTime.of(2026, 5, 5, 8, 0);

        Job job1 = JobTestBuilder.aJob()
                .startingAt(lineStart)
                .build();

        Line line = LineTestBuilder.aLine("line1", lineStart)
                .withJobs(job1)
                .build();

        assertFalse(job1.areEqualsPlanAndFactLines());
    }

    @Test
    void areEqualsPlanAndFactLines_WhenLineIdIsNull() {
        LocalDateTime lineStart = LocalDateTime.of(2026, 5, 5, 8, 0);

        Job job1 = JobTestBuilder.aJob()
                .withLineIdFact("line1")
                .startingAt(lineStart)
                .build();

        Line line = LineTestBuilder.aLine("line1", lineStart)
                .withJobs(job1)
                .build();

        line.setId(null);
        assertFalse(job1.areEqualsPlanAndFactLines());
    }

    //==================================================================================================================
    // getFactDuration
    @Test
    void getFactDuration_WhenCameraDataIsNotNull() {
        LocalDateTime cameraStart = LocalDateTime.of(2026, 5, 5, 8, 0);
        LocalDateTime cameraEnd = LocalDateTime.of(2026, 5, 5, 8, 30);

        Job job1 = JobTestBuilder.aJob()
                .withCamera(cameraStart, cameraEnd)
                .build();

        assertEquals(30, job1.getFactDuration());
    }

    @Test
    void getFactDuration_WhenCameraStartIsNull() {
        LocalDateTime cameraEnd = LocalDateTime.of(2026, 5, 5, 8, 30);

        Job job1 = JobTestBuilder.aJob()
                .withCamera(null, cameraEnd)
                .build();

        assertEquals(0, job1.getFactDuration());
    }

    @Test
    void getFactDuration_WhenCameraEndIsNull() {
        LocalDateTime cameraStart = LocalDateTime.of(2026, 5, 5, 8, 30);

        Job job1 = JobTestBuilder.aJob()
                .withCamera(cameraStart, null)
                .build();

        assertEquals(0, job1.getFactDuration());
    }

    @Test
    void getFactDuration_WhenCameraStartIsNotBeforeEnd() {
        LocalDateTime cameraStart = LocalDateTime.of(2026, 5, 5, 8, 30);
        LocalDateTime cameraEnd = LocalDateTime.of(2026, 5, 5, 8, 0);

        Job job1 = JobTestBuilder.aJob()
                .withCamera(cameraStart, cameraEnd)
                .build();

        assertEquals(0, job1.getFactDuration());
    }

    //==================================================================================================================
    // getCleaningDurationPlan
    @Test
    void getCleaningDurationPlan_WhenCleaningDataIsNotNull() {
        LocalDateTime cleaningStart = LocalDateTime.of(2026, 5, 5, 8, 0);
        LocalDateTime startProduction = LocalDateTime.of(2026, 5, 5, 8, 30);

        Job job1 = JobTestBuilder.aJob()
                .withStartProductionDateTime(startProduction)
                .withStartCleaningDateTime(cleaningStart)
                .build();
        assertEquals(30, job1.getCleaningDurationPlan());
    }

    @Test
    void getCleaningDurationPlan_WhenCameraCleaningIsNull() {
        LocalDateTime startProduction = LocalDateTime.of(2026, 5, 5, 8, 30);

        Job job1 = JobTestBuilder.aJob()
                .withStartProductionDateTime(startProduction)
                .build();

        assertEquals(0, job1.getCleaningDurationPlan());
    }

    @Test
    void getCleaningDurationPlan_WhenStartIsNull() {
        LocalDateTime cleaningStart = LocalDateTime.of(2026, 5, 5, 8, 0);

        Job job1 = JobTestBuilder.aJob()
                .withStartCleaningDateTime(cleaningStart)
                .build();

        assertEquals(0, job1.getCleaningDurationPlan());
    }

    @Test
    void getCleaningDurationPlan_WhenStartIsNotBeforeCleaning() {
        LocalDateTime cleaningStart = LocalDateTime.of(2026, 5, 5, 8, 30);
        LocalDateTime startProduction = LocalDateTime.of(2026, 5, 5, 8, 0);

        Job job1 = JobTestBuilder.aJob()
                .withStartProductionDateTime(startProduction)
                .withStartCleaningDateTime(cleaningStart)
                .build();

        assertEquals(0, job1.getCleaningDurationPlan());
    }
}
