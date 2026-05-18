package domain;

import builder.*;
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

import static org.junit.jupiter.api.Assertions.*;

class JobTest {

    @BeforeEach
    void initSpeedCache() {
        Map<String, Pair<Integer, Integer>> line1Speeds = new HashMap<>();
        line1Speeds.put("TYPE_A", Pair.of(100, 50));
        line1Speeds.put("TYPE_B", Pair.of(200, 80));

        Map<String, Map<String, Pair<Integer, Integer>>> speeds = new HashMap<>();
        speeds.put("line1", line1Speeds);

        SpeedCacheUtils.init(speeds);

        CleaningDurationUtils.init(Map.of("line1", 45));
    }
    // ============================================================
    // fromDbMaintenanceRow
    // ============================================================

    @Test
    void fromDbMaintenanceRow_success() {
        DbMaintenanceRow row = DbMaintenanceRowBuilder.aRow().build();
        Product mProduct = new Product();
        Job mJob = Job.fromDbMaintenanceRow(row, "MJob",
                mProduct, row.getStartProductionDateTime());

        assertTrue(mJob.isMaintenance());

        assertEquals(String.valueOf(row.getFId()), mJob.getId());
        assertEquals(row.getMaintenanceTypeId(), mJob.getMaintenanceTypeId());
        assertEquals(Duration.ofMinutes(row.getDuration()), mJob.getDuration());
        assertEquals(row.getLineId(), mJob.getLineId());
        assertEquals(row.getFId(), mJob.getMaintenanceFId());
        assertEquals(row.getMaintenanceNote(), mJob.getMaintenanceNote());
    }

    @Test
    void fromDbMaintenanceRow_whenDurationIsNull() {
        DbMaintenanceRow row = DbMaintenanceRowBuilder.aRow()
                .withDuration(null).build();
        Product mProduct = new Product();
        Job mJob = Job.fromDbMaintenanceRow(row, "MJob",
                mProduct, row.getStartProductionDateTime());

        assertTrue(mJob.isMaintenance());
        assertEquals(Duration.ZERO, mJob.getDuration());
    }

    // ============================================================
    // fromDbJobRow
    // ============================================================
    @Test
    void fromDbJobRow_success() {
        DbJobRow row = DbJobRowBuilder.aRow().build();

        Product p1 = new Product();
        Job job = Job.fromDbJobRow(row, p1, row.startProductionDateTime(),
                ScheduleUtils::nameCleaner);

        assertEquals("123", job.getId());
        assertEquals(row.shortName(), job.getName());
        assertEquals(row.emk(), job.getEmk());
        assertEquals(row.placePlan(), job.getPlacePlan());
        assertEquals(row.np(), job.getNp());
        assertEquals(row.mass(), job.getMass());
        assertEquals(row.lineId(), job.getLineId());
        assertEquals(row.startProductionDateTime(), job.getStartProductionDateTime());
        assertEquals(row.snpz(), job.getSnpz());
    }

    @Test
    void fromDbJobRow_whenValuesAreNull() {
        DbJobRow row = DbJobRowBuilder.aRow()
                .withNp(null)
                .withEmk(null)
                .withDuration(null)
                .withPlacePlan(null)
                .withQuantity(null)
                .withPriority(null).build();

        Product p1 = new Product();
        Job job = Job.fromDbJobRow(row, p1, row.startProductionDateTime(),
                ScheduleUtils::nameCleaner);

        assertEquals(0, job.getNp());
        assertEquals(0, job.getEmk());
        assertEquals(0, job.getQuantity());
        assertEquals(0, job.getPlacePlan());
        assertEquals(1, job.getPriority());
    }

    // ============================================================
    // getDuration
    // ============================================================
    @Test
    void getDuration_isHandPackaging() {
        Pair<Job, Job> jobs = buildTestJobsWithCleanings();
        jobs.getLeft().setHandPackaging(true);

        assertEquals(Duration.ofMinutes(56), jobs.getLeft().getDuration());
    }

    @Test
    void getDuration_whenDelayIsNotNull() {
        Pair<Job, Job> jobs = buildTestJobsWithCleanings();
        jobs.getLeft().setDelayDuration(Duration.ofMinutes(20));

        assertEquals(Duration.ofMinutes(50), jobs.getLeft().getDuration());
    }

    @Test
    void getDuration_whenSpeedIsNull() {
        Job j1 = new Job();

        assertEquals(Duration.ZERO, j1.getDuration());
    }

    @Test
    void getDuration_whenSpeedLessZero() {
        Pair<Job, Job> jobs = buildTestJobsWithCleanings();

        SpeedCacheUtils.getLineSpeeds().put("line1", Map.of("TYPE_A", Pair.of(-100, 50)));
        assertEquals(Duration.ZERO, jobs.getLeft().getDuration());
    }

    // ============================================================
    // getSpeed
    // ============================================================
    @Test
    void getSpeed_whenProductIsNull() {
        Job j1 = new Job();
        j1.setLine(new Line());
        assertNull(j1.getSpeed());
    }

    @Test
    void getSpeed_whenProductTypeIsNull() {
        Job j1 = new Job();
        j1.setLine(new Line());
        j1.setProduct(new Product());
        assertNull(j1.getSpeed());
    }

    // ============================================================
    //  getHandPackagingSpeed
    // ============================================================
    @Test
    void getHandPackagingSpeed_success() {
        Pair<Job, Job> jobs = buildTestJobsWithCleanings();
        jobs.getLeft().setHandPackaging(true);
        assertEquals(50, jobs.getLeft().getHandPackagingSpeed());
    }

    @Test
    void getHandPackagingSpeed_whenLineIsNull() {
        Job j1 = new Job();
        assertNull(j1.getHandPackagingSpeed());
    }

    @Test
    void getHandPackagingSpeed_whenProductIsNull() {
        Job j1 = new Job();
        j1.setLine(new Line());
        assertNull(j1.getHandPackagingSpeed());
    }

    @Test
    void getHandPackagingSpeed_whenProductTypeIsNull() {
        Job j1 = new Job();
        j1.setLine(new Line());
        j1.setProduct(new Product());
        assertNull(j1.getHandPackagingSpeed());
    }

    // ============================================================
    // updateStartCleaningDateTime
    // ============================================================

    @Test
    void updateStartCleaningDateTime_success() {
        Pair<Job, Job> jobs = buildTestJobsWithCleanings();

        jobs.getLeft().updateStartCleaningDateTime();
        jobs.getRight().updateStartCleaningDateTime();

        assertEquals(LocalDateTime.of(2026, 5, 9, 10, 0), jobs.getLeft().getStartCleaningDateTime());
        assertEquals(LocalDateTime.of(2026, 5, 9, 10, 0), jobs.getLeft().getStartProductionDateTime());
        assertEquals(LocalDateTime.of(2026, 5, 9, 10, 30), jobs.getLeft().getEndDateTime());

        assertEquals(LocalDateTime.of(2026, 5, 9, 10, 30), jobs.getRight().getStartCleaningDateTime());
        assertEquals(LocalDateTime.of(2026, 5, 9, 10, 50), jobs.getRight().getStartProductionDateTime());
        assertEquals(LocalDateTime.of(2026, 5, 9, 11, 7), jobs.getRight().getEndDateTime());

    }

    @Test
    void updateStartCleaningDateTime_whenPreviousIsMaintenance() {
        Pair<Job, Job> jobs = buildTestJobsWithCleanings();
        jobs.getLeft().setDuration(Duration.ofMinutes(60));
        jobs.getLeft().setMaintenance(true);

        jobs.getLeft().updateStartCleaningDateTime();
        jobs.getRight().updateStartCleaningDateTime();

        assertEquals(LocalDateTime.of(2026, 5, 9, 10, 0), jobs.getLeft().getStartCleaningDateTime());
        assertEquals(LocalDateTime.of(2026, 5, 9, 10, 0), jobs.getLeft().getStartProductionDateTime());
        assertEquals(LocalDateTime.of(2026, 5, 9, 11, 0), jobs.getLeft().getEndDateTime());

        assertEquals(LocalDateTime.of(2026, 5, 9, 11, 0), jobs.getRight().getStartCleaningDateTime());
        assertEquals(LocalDateTime.of(2026, 5, 9, 11, 0), jobs.getRight().getStartProductionDateTime());
        assertEquals(LocalDateTime.of(2026, 5, 9, 11, 17), jobs.getRight().getEndDateTime());
    }

    @Test
    void updateStartCleaningDateTime_whenProductIsNull() {
        Pair<Job, Job> jobs = buildTestJobsWithCleanings();

        jobs.getRight().setProduct(null);

        jobs.getLeft().updateStartCleaningDateTime();
        jobs.getRight().updateStartCleaningDateTime();

        assertEquals(LocalDateTime.of(2026, 5, 9, 10, 0), jobs.getLeft().getStartCleaningDateTime());
        assertEquals(LocalDateTime.of(2026, 5, 9, 10, 0), jobs.getLeft().getStartProductionDateTime());
        assertEquals(LocalDateTime.of(2026, 5, 9, 10, 30), jobs.getLeft().getEndDateTime());

        assertEquals(LocalDateTime.of(2026, 5, 9, 10, 30), jobs.getRight().getStartCleaningDateTime());
        assertEquals(LocalDateTime.of(2026, 5, 9, 10, 30), jobs.getRight().getStartProductionDateTime());
        assertEquals(LocalDateTime.of(2026, 5, 9, 10, 30), jobs.getRight().getEndDateTime());
    }

    @Test
    void updateStartCleaningDateTime_whenPreviousProductIsNull() {
        Pair<Job, Job> jobs = buildTestJobsWithCleanings();

        jobs.getLeft().setProduct(null);

        jobs.getLeft().updateStartCleaningDateTime();
        jobs.getRight().updateStartCleaningDateTime();

        System.out.println(jobs.getRight().getDuration());
        assertEquals(LocalDateTime.of(2026, 5, 9, 10, 0), jobs.getLeft().getStartCleaningDateTime());
        assertEquals(LocalDateTime.of(2026, 5, 9, 10, 0), jobs.getLeft().getStartProductionDateTime());
        assertEquals(LocalDateTime.of(2026, 5, 9, 10, 0), jobs.getLeft().getEndDateTime());

        assertEquals(LocalDateTime.of(2026, 5, 9, 10, 0), jobs.getRight().getStartCleaningDateTime());
        assertEquals(LocalDateTime.of(2026, 5, 9, 10, 0), jobs.getRight().getStartProductionDateTime());
        assertEquals(LocalDateTime.of(2026, 5, 9, 10, 17), jobs.getRight().getEndDateTime());
    }

    @Test
    void updateStartCleaningDateTime_whenPreviousIsPLRC() {
        Pair<Job, Job> jobs = buildTestJobsWithCleanings();

        jobs.getRight().getProduct().getCleaningResults()
                .put(jobs.getLeft().getProduct(), new CleaningResult(60, true));

        jobs.getLeft().updateStartCleaningDateTime();
        jobs.getRight().updateStartCleaningDateTime();

        assertEquals(LocalDateTime.of(2026, 5, 9, 10, 0), jobs.getLeft().getStartCleaningDateTime());
        assertEquals(LocalDateTime.of(2026, 5, 9, 10, 0), jobs.getLeft().getStartProductionDateTime());
        assertEquals(LocalDateTime.of(2026, 5, 9, 10, 30), jobs.getLeft().getEndDateTime());

        assertEquals(LocalDateTime.of(2026, 5, 9, 10, 30), jobs.getRight().getStartCleaningDateTime());
        assertEquals(LocalDateTime.of(2026, 5, 9, 11, 15), jobs.getRight().getStartProductionDateTime());
        assertEquals(LocalDateTime.of(2026, 5, 9, 11, 32), jobs.getRight().getEndDateTime());
    }

    @Test
    void updateStartCleaningDateTime_whenCleaningDelayIsNotNull() {
        Pair<Job, Job> jobs = buildTestJobsWithCleanings();

        jobs.getRight().setCleaningDelay(Duration.ofMinutes(30));

        jobs.getLeft().updateStartCleaningDateTime();
        jobs.getRight().updateStartCleaningDateTime();

        assertEquals(LocalDateTime.of(2026, 5, 9, 10, 0), jobs.getLeft().getStartCleaningDateTime());
        assertEquals(LocalDateTime.of(2026, 5, 9, 10, 0), jobs.getLeft().getStartProductionDateTime());
        assertEquals(LocalDateTime.of(2026, 5, 9, 10, 30), jobs.getLeft().getEndDateTime());

        assertEquals(LocalDateTime.of(2026, 5, 9, 10, 30), jobs.getRight().getStartCleaningDateTime());
        assertEquals(LocalDateTime.of(2026, 5, 9, 11, 20), jobs.getRight().getStartProductionDateTime());
        assertEquals(LocalDateTime.of(2026, 5, 9, 11, 37), jobs.getRight().getEndDateTime());
    }

    @Test
    void updateStartCleaningDateTime_whenCleanupDurationIsNegative() {
        Pair<Job, Job> jobs = buildTestJobsWithCleanings();

        jobs.getRight().setCleaningDelay(Duration.ofMinutes(-30));

        jobs.getLeft().updateStartCleaningDateTime();
        jobs.getRight().updateStartCleaningDateTime();

        assertEquals(LocalDateTime.of(2026, 5, 9, 10, 0), jobs.getLeft().getStartCleaningDateTime());
        assertEquals(LocalDateTime.of(2026, 5, 9, 10, 0), jobs.getLeft().getStartProductionDateTime());
        assertEquals(LocalDateTime.of(2026, 5, 9, 10, 30), jobs.getLeft().getEndDateTime());

        assertEquals(LocalDateTime.of(2026, 5, 9, 10, 30), jobs.getRight().getStartCleaningDateTime());
        assertEquals(LocalDateTime.of(2026, 5, 9, 10, 40), jobs.getRight().getStartProductionDateTime());
        assertEquals(LocalDateTime.of(2026, 5, 9, 10, 57), jobs.getRight().getEndDateTime());
    }

    @Test
    void updateStartCleaningDateTime_whenNPE() {
        Pair<Job, Job> jobs = buildTestJobsWithCleanings();

        jobs.getRight().getProduct().getCleaningResults().remove(jobs.getRight().getPreviousJob().getProduct());

        jobs.getLeft().updateStartCleaningDateTime();
        jobs.getRight().updateStartCleaningDateTime();

        assertEquals(LocalDateTime.of(2026, 5, 9, 10, 0), jobs.getLeft().getStartCleaningDateTime());
        assertEquals(LocalDateTime.of(2026, 5, 9, 10, 0), jobs.getLeft().getStartProductionDateTime());
        assertEquals(LocalDateTime.of(2026, 5, 9, 10, 30), jobs.getLeft().getEndDateTime());

        assertEquals(LocalDateTime.of(2026, 5, 9, 10, 30), jobs.getRight().getStartCleaningDateTime());
        assertEquals(LocalDateTime.of(2026, 5, 9, 10, 30), jobs.getRight().getStartProductionDateTime());
        assertEquals(LocalDateTime.of(2026, 5, 9, 10, 47), jobs.getRight().getEndDateTime());
    }

    @Test
    void updateStartCleaningDateTime_whenLineIsNull() {
        Pair<Job, Job> jobs = buildTestJobsWithCleanings();
        LocalDateTime endDateTime = LocalDateTime.of(2026, 6, 9, 10, 30);
        jobs.getLeft().setEndDateTime(endDateTime);
        jobs.getLeft().setLine(null);
        jobs.getLeft().updateStartCleaningDateTime();

        assertEquals(endDateTime, jobs.getLeft().getEndDateTime());
    }

    @Test
    void updateStartCleaningDateTime_whenLineAndCleaningAreNull() {
        Pair<Job, Job> jobs = buildTestJobsWithCleanings();

        jobs.getLeft().setStartCleaningDateTime(LocalDateTime.of(2026, 6, 9, 10, 0));
        jobs.getLeft().setLine(null);
        jobs.getLeft().updateStartCleaningDateTime();

        assertNull(jobs.getLeft().getStartCleaningDateTime());
    }

    // ============================================================
    // areEqualsPlanAndFactLines
    // ============================================================
    @Test
    void areEqualsPlanAndFactLines_success() {
        Line line = new Line("L1", "line");
        Job j1 = new Job();

        j1.setLine(line);
        j1.setLineIdFact(line.getId());

        assertTrue(j1.areEqualsPlanAndFactLines());
    }

    @Test
    void areEqualsPlanAndFactLines_WhenLinesAreNotTheSame() {
        Line line = new Line("L1", "line");
        Job j1 = new Job();

        j1.setLine(line);
        j1.setLineIdFact("L2");

        assertFalse(j1.areEqualsPlanAndFactLines());
    }

    @Test
    void areEqualsPlanAndFactLines_WhenLineIdFactIsNull() {
        Job j1 = new Job();
        assertFalse(j1.areEqualsPlanAndFactLines());
    }

    @Test
    void areEqualsPlanAndFactLines_WhenLineIsNull() {
        Job j1 = new Job();
        j1.setLineIdFact("L2");

        assertFalse(j1.areEqualsPlanAndFactLines());
    }

    @Test
    void areEqualsPlanAndFactLines_WhenLineIdIsNull() {
        Line line = new Line();
        Job j1 = new Job();

        j1.setLine(line);
        j1.setLineIdFact("L1");

        assertFalse(j1.areEqualsPlanAndFactLines());
    }

    // ============================================================
    // getFactDuration
    // ============================================================
    @Test
    void getFactDuration_WhenCameraDataIsNotNull() {
        LocalDateTime cameraStart = LocalDateTime.of(2026, 5, 5, 8, 0);
        LocalDateTime cameraEnd = LocalDateTime.of(2026, 5, 5, 8, 30);

        Job j1 = new Job();
        j1.setCameraStart(cameraStart);
        j1.setCameraEnd(cameraEnd);
        assertEquals(30, j1.getFactDuration());
    }

    @Test
    void getFactDuration_WhenCameraStartIsNull() {
        LocalDateTime cameraEnd = LocalDateTime.of(2026, 5, 5, 8, 30);

        Job j1 = new Job();
        j1.setCameraEnd(cameraEnd);
        assertEquals(0, j1.getFactDuration());
    }

    @Test
    void getFactDuration_WhenCameraEndIsNull() {
        LocalDateTime cameraStart = LocalDateTime.of(2026, 5, 5, 8, 30);

        Job j1 = new Job();
        j1.setCameraStart(cameraStart);
        assertEquals(0, j1.getFactDuration());
    }

    @Test
    void getFactDuration_WhenCameraStartIsNotBeforeEnd() {
        LocalDateTime cameraStart = LocalDateTime.of(2026, 5, 5, 8, 30);
        LocalDateTime cameraEnd = LocalDateTime.of(2026, 5, 5, 8, 0);

        Job j1 = new Job();
        j1.setCameraStart(cameraStart);
        j1.setCameraEnd(cameraEnd);

        assertEquals(0, j1.getFactDuration());
    }

    // ============================================================
    // getCleaningDurationPlan
    // ============================================================

    @Test
    void getCleaningDurationPlan_WhenCleaningDataIsNotNull() {
        LocalDateTime cleaningStart = LocalDateTime.of(2026, 5, 5, 8, 0);
        LocalDateTime productionStart = LocalDateTime.of(2026, 5, 5, 8, 30);

        Job j1 = new Job();
        j1.setStartCleaningDateTime(cleaningStart);
        j1.setStartProductionDateTime(productionStart);

        assertEquals(30, j1.getCleaningDurationPlan());
    }

    @Test
    void getCleaningDurationPlan_WhenCleaningIsNull() {
        LocalDateTime productionStart = LocalDateTime.of(2026, 5, 5, 8, 30);

        Job j1 = new Job();
        j1.setStartProductionDateTime(productionStart);

        assertEquals(0, j1.getCleaningDurationPlan());
    }

    @Test
    void getCleaningDurationPlan_WhenProductionStartIsNull() {
        LocalDateTime cleaningStart = LocalDateTime.of(2026, 5, 5, 8, 30);

        Job j1 = new Job();
        j1.setStartCleaningDateTime(cleaningStart);
        assertEquals(0, j1.getCleaningDurationPlan());
    }

    @Test
    void getCleaningDurationPlan_WhenStartIsNotBeforeCleaning() {
        LocalDateTime cleaningStart = LocalDateTime.of(2026, 5, 5, 8, 30);
        LocalDateTime productionStart = LocalDateTime.of(2026, 5, 5, 8, 0);

        Job j1 = new Job();
        j1.setStartCleaningDateTime(cleaningStart);
        j1.setStartProductionDateTime(productionStart);

        assertEquals(0, j1.getCleaningDurationPlan());
    }

    // ============================================================
    // getCleaningDurationPlan
    // ============================================================
    @Test
    void getCleaningDurationWithDelay_whenCleaningDelayIsNull() {
        Job job = new Job();

        job.setStartCleaningDateTime(LocalDateTime.now().minusMinutes(10));
        job.setStartProductionDateTime(LocalDateTime.now());
        job.setCleaningDelay(null);

        assertEquals(0, job.getCleaningDurationWithDelay());
    }

    @Test
    void getCleaningDurationWithDelay_whenStartProductionIsNull() {
        Job job = new Job();

        job.setStartCleaningDateTime(LocalDateTime.now().minusMinutes(10));
        job.setCleaningDelay(Duration.ofMinutes(5));
        job.setStartProductionDateTime(null);

        assertEquals(0, job.getCleaningDurationWithDelay());
    }

    @Test
    void getCleaningDurationWithDelay_whenStartCleaningIsNull() {
        Job job = new Job();

        job.setStartProductionDateTime(LocalDateTime.now());
        job.setCleaningDelay(Duration.ofMinutes(5));
        job.setStartCleaningDateTime(null);

        assertEquals(0, job.getCleaningDurationWithDelay());
    }

    @Test
    void getCleaningDurationWithDelay_whenProductionNotAfterCleaning() {
        Job job = new Job();

        LocalDateTime now = LocalDateTime.now();

        job.setStartCleaningDateTime(now);
        job.setStartProductionDateTime(now.minusMinutes(5));
        job.setCleaningDelay(Duration.ofMinutes(5));

        assertEquals(0, job.getCleaningDurationWithDelay());
    }

    @Test
    void getCleaningDurationWithDelay_whenValidData_returnsSumOfDurationAndDelay() {
        Job job = new Job();

        LocalDateTime cleaning = LocalDateTime.now().minusMinutes(20);
        LocalDateTime production = LocalDateTime.now();

        job.setStartCleaningDateTime(cleaning);
        job.setStartProductionDateTime(production);
        job.setCleaningDelay(Duration.ofMinutes(5));

        assertEquals(25, job.getCleaningDurationWithDelay());
    }

    // ============================================================
    // getPlanDuration
    // ============================================================
    @Test
    void getPlanDuration_whenIsMaintenance() {
        Job job = new Job();
        job.setMaintenance(true);
        assertNull(job.getPlanDuration());
    }
    // ============================================================
    // getPlanEndDateTime
    // ============================================================
    @Test
    void getPlanEndDateTime_whenIsMaintenance() {
        Job job = new Job();
        assertNull(job.getPlanEndDateTime());
    }
    // ============================================================
    // toString
    // ============================================================
    @Test
    void toString_success() {
        Job job = new Job();
        job.setId("J1");
        job.setProduct(new Product("P1", "product"));
        String expected = job.getId() + "(" + job.getProduct().getName() + ")";
        assertEquals(expected, job.toString());
    }
    @Test
    void toString_whenProductIsNUll() {
        Job job = new Job();
        job.setId("J1");
        String expected = job.getId() + "(" + "null" + ")";
        assertEquals(expected, job.toString());
    }
    // ============================================================
    // helper methods
    // ============================================================
    private Pair<Job, Job> buildTestJobsWithCleanings() {
        LocalDateTime start = LocalDateTime.of(2026, 5, 9, 10, 0);
        Job j1 = JobTestBuilder.aJob().withId("J1").withQuantity(2600).build();
        Job j2 = JobTestBuilder.aJob().withId("J2").withQuantity(2600).build();

        Product p1 = ProductTestBuilder.aProduct("P1").withType("TYPE_A").build();
        Product p2 = ProductTestBuilder.aProduct("P2").withType("TYPE_B").build();

        Map<Product, Duration> cleaningDurationsP1 = Map.of(p1, Duration.ZERO, p2, Duration.ofMinutes(20));
        Map<Product, Duration> cleaningDurationsP2 = Map.of(p2, Duration.ZERO, p1, Duration.ofMinutes(20));
        Map<Product, CleaningResult> results1 = new HashMap<>(Map.of(p1, new CleaningResult(30, false),
                p2, new CleaningResult(30, false)));

        p1.setCleaningDurations(cleaningDurationsP1);
        p1.setCleaningResults(results1);
        p2.setCleaningDurations(cleaningDurationsP2);
        p2.setCleaningResults(results1);

        Line line = new Line("line1", "line1");
        line.setStartDateTime(start);
        j1.setProduct(p1);
        j2.setProduct(p2);
        j1.setLine(line);
        j2.setLine(line);

        j2.setPreviousJob(j1);
        return Pair.of(j1, j2);
    }
}
