package scheduleOperations;

import org.acme.foodpackaging.dto.MaintenanceRequest;
import org.acme.foodpackaging.dto.DbMaintenanceRow;
import org.acme.foodpackaging.scheduleOperations.MaintenanceJob;
import org.acme.foodpackaging.scheduleOperations.utils.SpeedCacheUtils;
import org.acme.foodpackaging.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.sql.Timestamp;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class MaintenanceJobTest {

    private MaintenanceJob maintenanceJob;
    private PackagingSchedule schedule;
    private Line line;

    @BeforeEach
    void setup() {
        maintenanceJob = new MaintenanceJob(null);

        // Line, schedule
        line = new Line("line1", "Line 1", "operator", LocalDateTime.now());
        schedule = new PackagingSchedule();
        schedule.setLines(List.of(line));

        // Products
        Product maintenanceProduct = new Product("MAINTENANCE", "Maintenance Product");
        Product normalProduct = new Product("NORMAL", "Normal Product");

        // Cleaning durations
        Map<Product, Duration> cleaningMaintenance = new HashMap<>();
        cleaningMaintenance.put(maintenanceProduct, Duration.ZERO);
        cleaningMaintenance.put(normalProduct, Duration.ofMinutes(10));
        maintenanceProduct.setCleaningDurations(cleaningMaintenance);

        Map<Product, Duration> cleaningNormal = new HashMap<>();
        cleaningNormal.put(maintenanceProduct, Duration.ofMinutes(5));
        cleaningNormal.put(normalProduct, Duration.ZERO);
        normalProduct.setCleaningDurations(cleaningNormal);

        // SpeedCacheUtils
        Map<String, Map<String, Integer>> speeds = new HashMap<>();
        Map<String, Integer> productSpeeds = new HashMap<>();
        productSpeeds.put("MAINTENANCE", 1);
        productSpeeds.put("NORMAL", 2);
        speeds.put("line1", productSpeeds);
        SpeedCacheUtils.init(speeds);

        schedule.setProducts(List.of(maintenanceProduct, normalProduct));
        schedule.setWorkCalendar(new WorkCalendar(LocalDate.now()));
        schedule.setJobs(new ArrayList<>());
    }

    @Test
    void maintenanceJobInEmptyLine() {
        MaintenanceRequest request = new MaintenanceRequest();
        request.setLineId("line1");
        request.setMaintenanceNote("Maintenance 1");
        request.setDurationMinutes(30);
        // emptyLineMode
        request.setStartProductionDateTime(LocalDateTime.now());

        PackagingSchedule result = maintenanceJob.addMaintenanceJob(schedule, request);

        assertEquals(1, result.getJobs().size());
        Job job = result.getJobs().getFirst();
        assertTrue(job.isMaintenance());
        assertEquals("Maintenance 1", job.getMaintenanceNote());
        assertEquals(30, job.getDuration().toMinutes());
        assertEquals(line, job.getLine());
    }

    @Test
    void addMaintenanceJob() {
        LocalDateTime startProductionDateTime = LocalDateTime.of(2025, 1, 15, 9, 0);
        LocalDateTime endDateTime = startProductionDateTime.plusMinutes(60);

        Product product = schedule.getProducts().getFirst();
        DbMaintenanceRow row = new DbMaintenanceRow(
                1L, (short)0, "line1", Timestamp.valueOf(startProductionDateTime),Timestamp.valueOf(endDateTime), 15,2212L, 4, "Maintenance 2"
        );

        Job existingJob = Job.fromDbMaintenanceRow(row,"Maintenance Name", product, startProductionDateTime);

        line.getJobs().add(existingJob);
        schedule.getJobs().add(existingJob);

        MaintenanceRequest request = new MaintenanceRequest();
        request.setLineId("line1");
        request.setMaintenanceTypeId(4);
        request.setMaintenanceNote("Maintenance 2");
        request.setDurationMinutes(15);
        request.setInsertIndex(0);

        PackagingSchedule result = maintenanceJob.addMaintenanceJob(schedule, request);

        assertEquals(2, result.getJobs().size());
        assertEquals(4,line.getJobs().getFirst().getMaintenanceTypeId());
        assertEquals("Maintenance 2", line.getJobs().getFirst().getMaintenanceNote());
        assertEquals("Maintenance Name", line.getJobs().get(1).getName());
    }

    @Test
    void removeMaintenanceJobByIndex() {
        Product product = schedule.getProducts().getFirst();
        DbMaintenanceRow row = new DbMaintenanceRow(
                1L, (short)0, "line1", null , null, 20,2212L, 4, "Maintenance 2"
        );

        Job job = Job.fromDbMaintenanceRow(row,"MaintenanceJob 1", product, null);
        job.setMinStartTime(schedule.getWorkCalendar().getMinStartDateTime());
        job.setMaintenance(true);
        job.setFId(100L);
        line.getJobs().add(job);
        schedule.getJobs().add(job);

        MaintenanceRequest request = new MaintenanceRequest();
        request.setLineId("line1");
        request.setRemoveIndex(0);

        PackagingSchedule result = maintenanceJob.removeMaintenanceJob(schedule, request);

        assertTrue(result.getJobs().isEmpty());
        assertTrue(line.getJobs().isEmpty());
    }
    
    @Test
    void updateDuration() {
        Product product = schedule.getProducts().getFirst();
        DbMaintenanceRow row = new DbMaintenanceRow(
                1L, (short)0, "line1", null , null, 20,2212L, 4, "Maintenance 2"
        );

        Job job = Job.fromDbMaintenanceRow(row,"MaintenanceJob 1", product, null);
        job.setMinStartTime(schedule.getWorkCalendar().getMinStartDateTime());

        job.setMaintenance(true);
        line.getJobs().add(job);
        schedule.getJobs().add(job);

        MaintenanceRequest request = new MaintenanceRequest();
        request.setLineId("line1");
        // updateLineMode
        request.setUpdateIndex(0);
        request.setDurationMinutes(45);

        PackagingSchedule result = maintenanceJob.updateDuration(schedule, request);

        assertEquals(45, result.getJobs().getFirst().getDuration().toMinutes());
    }

    @Test
    void marksMatchingRowsAsDeleted() {
        Map<Long, DbMaintenanceRow> jobs = new HashMap<>();

        DbMaintenanceRow match = new DbMaintenanceRow(
                100L, (short) 0, "L1",
                Timestamp.valueOf(LocalDateTime.of(2025, 1, 1, 8, 0)),
                Timestamp.valueOf(LocalDateTime.of(2025, 1, 1, 9, 0)),
                60, 123L, 1, "note"
        );
        DbMaintenanceRow other = new DbMaintenanceRow(
                200L, (short) 0, "L1",
                Timestamp.valueOf(LocalDateTime.of(2025, 1, 1, 10, 0)),
                Timestamp.valueOf(LocalDateTime.of(2025, 1, 1, 11, 0)),
                60, 124L, 1, "note2"
        );

        jobs.put(100L, match);
        jobs.put(200L, other);
        jobs.put(300L, null);

        maintenanceJob.markDeletedByFId(100L, jobs);

        assertEquals((short) 1, match.getFDel());
        assertEquals((short) 0, other.getFDel());
    }
}
