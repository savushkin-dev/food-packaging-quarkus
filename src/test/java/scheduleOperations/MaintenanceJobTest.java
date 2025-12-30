package scheduleOperations;

import org.acme.foodpackaging.dto.MaintenanceRequestDTO;
import org.acme.foodpackaging.scheduleOperations.MaintenanceJob;
import org.acme.foodpackaging.scheduleOperations.utils.SpeedCacheUtils;
import org.acme.foodpackaging.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class MaintenanceJobTest {

    private MaintenanceJob maintenanceJob;
    private PackagingSchedule schedule;
    private Line line;

    @BeforeEach
    void setup() {
        maintenanceJob = new MaintenanceJob();

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
    void MaintenanceJobInEmptyLine() {
        MaintenanceRequestDTO request = new MaintenanceRequestDTO();
        request.setLineId("line1");
        request.setName("Maintenance 1");
        request.setDurationMinutes(30);
        // emptyLineMode
        request.setStartProductionDateTime(LocalDateTime.now());

        PackagingSchedule result = maintenanceJob.addMaintenanceJob(schedule, request);

        assertEquals(1, result.getJobs().size());
        Job job = result.getJobs().getFirst();
        assertTrue(job.isMaintenance());
        assertEquals("Maintenance 1", job.getName());
        assertEquals(30, job.getDuration().toMinutes());
        assertEquals(line, job.getLine());
    }

    @Test
    void addMaintenanceJob() {
        Job existingJob = new Job("1", "Job 1", schedule.getProducts().get(1), Duration.ofMinutes(20),
                schedule.getWorkCalendar().getMinStartDateTime(), null, null,
                1, false, null, null);

        line.getJobs().add(existingJob);
        schedule.getJobs().add(existingJob);

        MaintenanceRequestDTO request = new MaintenanceRequestDTO();
        request.setLineId("line1");
        request.setName("Maintenance 2");
        request.setDurationMinutes(15);
        request.setInsertIndex(0);

        PackagingSchedule result = maintenanceJob.addMaintenanceJob(schedule, request);

        assertEquals(2, result.getJobs().size());
        assertEquals("Maintenance 2", line.getJobs().getFirst().getName());
        assertEquals("Job 1", line.getJobs().get(1).getName());
    }

    @Test
    void removeMaintenanceJobByIndex() {
        Job job = new Job("1", "MaintenanceJob 1", schedule.getProducts().getFirst(), Duration.ofMinutes(20),
                schedule.getWorkCalendar().getMinStartDateTime(),
                null, null, 1, true, null, null);

        job.setMaintenance(true);
        job.setFId(100L);
        line.getJobs().add(job);
        schedule.getJobs().add(job);

        MaintenanceRequestDTO request = new MaintenanceRequestDTO();
        request.setLineId("line1");
        request.setRemoveIndex(0);

        PackagingSchedule result = maintenanceJob.removeMaintenanceJob(schedule, request);

        assertTrue(result.getJobs().isEmpty());
        assertTrue(line.getJobs().isEmpty());
    }
    
    @Test
    void updateDuration() {
        Job job = new Job("1", "MaintenanceJob 1", schedule.getProducts().getFirst(), Duration.ofMinutes(20),
                schedule.getWorkCalendar().getMinStartDateTime(), null, null,
                1, true, null, null);

        job.setMaintenance(true);
        line.getJobs().add(job);
        schedule.getJobs().add(job);

        MaintenanceRequestDTO request = new MaintenanceRequestDTO();
        request.setLineId("line1");
        // updateLineMode
        request.setUpdateIndex(0);
        request.setDurationMinutes(45);

        PackagingSchedule result = maintenanceJob.updateDuration(schedule, request);

        assertEquals(45, result.getJobs().getFirst().getDuration().toMinutes());
    }
}
