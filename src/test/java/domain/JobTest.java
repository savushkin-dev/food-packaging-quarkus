package domain;

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

import java.sql.Timestamp;
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


    @Test
    void setMaintenanceFields() {
        LocalDateTime startProductionDateTime = LocalDateTime.of(2025, 1, 15, 9, 0);
        LocalDateTime endDateTime = startProductionDateTime.plusMinutes(60);
        
        Product product =  createMaintenanceProduct();
        Duration duration = Duration.ofMinutes(60);

        DbMaintenanceRow row = new DbMaintenanceRow(
                1L, (short)0, "1600", Timestamp.valueOf(startProductionDateTime),Timestamp.valueOf(endDateTime), 60,2212L, 4, "Note"
                );
        Job job = Job.fromDbMaintenanceRow(row,"Maintenance Name", product, startProductionDateTime);
        
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

        Product product =  new Product("12", "Vanilla");
        Duration duration = Duration.ofMinutes(20);

        DbJobRow row = new DbJobRow(
                Timestamp.valueOf(dti),"1623", 34,5600,1600.23,
                Timestamp.valueOf(startProductionDateTime),Timestamp.valueOf(endDateTime),
                20,3L, 0, "17000234", "Strawberry");
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
    }

    // --- getDuration, getSpeed, getHandPackagingSpeed tests ---

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

    @Test
    void getHandPackagingSpeed_returnsValueFromCache() {
        Job job = new Job();
        job.setLine(new Line("line1", "Line 1"));
        job.setProduct(createProductWithType("TYPE_A"));

        assertEquals(50, job.getHandPackagingSpeed());
    }

    // --- updateStartCleaningDateTime tests ---

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
    }

    @Test
    void updateStartCleaningDateTimeWhenNotPLRLC() {
        LocalDateTime lineStart = LocalDateTime.of(2025, 1, 15, 8, 0);
        Line line = new Line("line1", "Line 1", "op", lineStart);

        Product prodA = new Product("A", "Prod A");
        Product prodB = new Product("B", "Prod B");
        Map<Product, Duration> cleaningDurations = new HashMap<>();
        cleaningDurations.put(prodA, Duration.ofMinutes(25));
        prodB.setCleaningDurations(cleaningDurations);
        Map<Product, CleaningResult> cleaningResults = new HashMap<>();
        cleaningResults.put(prodA, new CleaningResult(25, false));
        prodB.setCleaningResults(cleaningResults);

        Job job1 = new Job();
        job1.setProduct(prodA);
        job1.setMaintenance(true);
        job1.setDuration(Duration.ofMinutes(60));
        job1.setStartCleaningDateTime(lineStart);
        job1.setStartProductionDateTime(lineStart);
        job1.setEndDateTime(lineStart.plusMinutes(60));

        Job job2 = new Job();
        job2.setProduct(prodB);
        job2.setMaintenance(true);
        job2.setDuration(Duration.ofMinutes(30));

        line.setJobs(java.util.List.of(job1, job2));
        ScheduleUtils.fixLineJobs(line);

        LocalDateTime expectedStartProduction = lineStart.plusMinutes(60).plusMinutes(25);
        assertEquals(expectedStartProduction, job2.getStartProductionDateTime());
        assertEquals(expectedStartProduction.plusMinutes(30), job2.getEndDateTime());
    }

    @Test
    void updateStartCleaningDateTime_whenPLRLC() {
        CleaningDurationUtils.init(Map.of("line1", 40));

        LocalDateTime lineStart = LocalDateTime.of(2025, 1, 15, 8, 0);
        Line line = new Line("line1", "Line 1", "op", lineStart);

        Product prodA = new Product("A", "Prod A");
        Product prodB = new Product("B", "Prod B");
        Map<Product, Duration> cleaningDurations = new HashMap<>();
        cleaningDurations.put(prodA, Duration.ofMinutes(10)); // ignored when PLRLC
        prodB.setCleaningDurations(cleaningDurations);
        Map<Product, CleaningResult> cleaningResults = new HashMap<>();
        cleaningResults.put(prodA, new CleaningResult(0, true)); // isPLRLC=true -> use linesCleaning
        prodB.setCleaningResults(cleaningResults);

        Job job1 = new Job();
        job1.setProduct(prodA);
        job1.setMaintenance(true);
        job1.setDuration(Duration.ofMinutes(60));
        job1.setStartCleaningDateTime(lineStart);
        job1.setStartProductionDateTime(lineStart);
        job1.setEndDateTime(lineStart.plusMinutes(60));

        Job job2 = new Job();
        job2.setProduct(prodB);
        job2.setMaintenance(true);
        job2.setDuration(Duration.ofMinutes(30));

        line.setJobs(java.util.List.of(job1, job2));
        ScheduleUtils.fixLineJobs(line);

        LocalDateTime expectedStartProduction = lineStart.plusMinutes(60).plusMinutes(40);
        assertEquals(expectedStartProduction, job2.getStartProductionDateTime());
    }

    @Test
    void updateStartCleaningDateTime_whenCleaningResultMissing() {
        LocalDateTime lineStart = LocalDateTime.of(2025, 1, 15, 8, 0);
        Line line = new Line("line1", "Line 1", "op", lineStart);

        Product prodA = new Product("A", "Prod A");
        Product prodB = new Product("B", "Prod B");
        prodB.setCleaningDurations(null);
        prodB.setCleaningResults(null);

        Job job1 = new Job();
        job1.setProduct(prodA);
        job1.setMaintenance(true);
        job1.setDuration(Duration.ofMinutes(60));
        job1.setStartCleaningDateTime(lineStart);
        job1.setStartProductionDateTime(lineStart);
        job1.setEndDateTime(lineStart.plusMinutes(60));

        Job job2 = new Job();
        job2.setProduct(prodB);
        job2.setMaintenance(true);
        job2.setDuration(Duration.ofMinutes(30));

        line.setJobs(java.util.List.of(job1, job2));
        ScheduleUtils.fixLineJobs(line);

        assertEquals(lineStart.plusMinutes(60), job2.getStartProductionDateTime());
    }

    private Product createProductWithType(String type) {
        Product p = new Product("id", "name");
        p.setType(type);
        return p;
    }
}
