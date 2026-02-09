package scheduleoperations;

import org.acme.foodpackaging.dto.MaintenanceRequest;
import org.acme.foodpackaging.dto.DbMaintenanceRow;
import org.acme.foodpackaging.record.CleaningResult;
import org.acme.foodpackaging.record.DbJobRow;
import org.acme.foodpackaging.persistence.load.LoadDataService;
import org.acme.foodpackaging.scheduleoperations.MaintenanceJob;
import org.acme.foodpackaging.scheduleoperations.utils.CleaningDurationUtils;
import org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils;
import org.acme.foodpackaging.scheduleoperations.utils.SpeedCacheUtils;
import org.acme.foodpackaging.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.sql.Timestamp;
import java.util.*;
import org.apache.commons.lang3.tuple.Pair;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceJobTest {

    @Mock
    LoadDataService loadDataService;
    private MaintenanceJob maintenanceJob;
    private PackagingSchedule schedule;
    private Line line;

    @BeforeEach
    void setup() {
        maintenanceJob = new MaintenanceJob(loadDataService);

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
        Map<String, Map<String, Pair<Integer, Integer>>> speeds = new HashMap<>();
        Map<String, Pair<Integer, Integer>> productSpeeds = new HashMap<>();
        productSpeeds.put("MAINTENANCE", Pair.of(1, 0));
        productSpeeds.put("NORMAL", Pair.of(2, 1));
        speeds.put("line1", productSpeeds);
        SpeedCacheUtils.init(speeds);

        schedule.setProducts(List.of(maintenanceProduct, normalProduct));
        schedule.setWorkCalendar(new WorkCalendar(LocalDate.now()));
        schedule.setJobs(new ArrayList<>());
    }

    @Test
    void maintenanceJobInEmptyLineTest() {
        MaintenanceRequest request = new MaintenanceRequest();
        request.setLineId("line1");
        request.setMaintenanceNote("Maintenance 1");
        request.setDurationMinutes(30);

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
    void addMaintenanceJobTest() {
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
    void removeMaintenanceJobByIndexTest() {
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
    void updateDurationTest() {
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

        request.setUpdateIndex(0);
        request.setDurationMinutes(45);

        PackagingSchedule result = maintenanceJob.updateDuration(schedule, request);

        assertEquals(45, result.getJobs().getFirst().getDuration().toMinutes());
    }

    @Test
    void updateMaintenanceTypeTest() {
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

        request.setUpdateIndex(0);
        request.setMaintenanceTypeId(2);
        request.setMaintenanceNote("Updated note");
        request.setDurationMinutes(45);

        ConcurrentMap<Integer, String> maintenanceTypes = new ConcurrentHashMap<>();
        maintenanceTypes.put(1, "Обслуживание");
        maintenanceTypes.put(2, "Мойка");

        when(loadDataService.getMaintenanceTypes()).thenReturn(maintenanceTypes);

        PackagingSchedule result = maintenanceJob.updateMaintenanceType(schedule, request);

        assertEquals(45, result.getJobs().getFirst().getDuration().toMinutes());
        assertEquals(2, result.getJobs().getFirst().getMaintenanceTypeId());
        assertEquals("Updated note", result.getJobs().getFirst().getMaintenanceNote());
        assertEquals("Мойка", result.getJobs().getFirst().getName());
    }

    @Test
    void addMaintenanceExtra_WhenDurationAtLeastSixHours() {
        Map<String, Integer> lc = Map.of("line1", 40);
        CleaningDurationUtils.init(lc);
        MaintenanceRequest seed = new MaintenanceRequest();
        seed.setLineId("line1");
        seed.setDurationMinutes(20);
        seed.setInsertIndex(0);
        maintenanceJob.addMaintenanceJob(schedule, seed);

        MaintenanceRequest req = new MaintenanceRequest();
        req.setLineId("line1");
        req.setDurationMinutes(360);
        req.setInsertIndex(1); // insert after seed

        PackagingSchedule result = maintenanceJob.addMaintenanceJob(schedule, req);

        // Expect two jobs at indices 1 (original) and 2 (extra)
        assertEquals(3, result.getJobs().size());
        Job inserted = line.getJobs().get(1);
        Job extra = line.getJobs().get(2);
        assertTrue(inserted.isMaintenance());
        assertTrue(extra.isMaintenance());
        assertEquals(40, extra.getDuration().toMinutes());
        assertEquals(2, extra.getMaintenanceTypeId()); 
        assertEquals("Мойка", extra.getName());
    }

    @Test
    void addmaintenanceExtra_EmptyLineReusesStart() {
        Map<String, Integer> lc = Map.of("line1", 30);
        CleaningDurationUtils.init(lc);

        LocalDateTime start = LocalDateTime.now();
        MaintenanceRequest req = new MaintenanceRequest();
        req.setLineId("line1");
        req.setDurationMinutes(400);
        req.setStartProductionDateTime(start);

        PackagingSchedule result = maintenanceJob.addMaintenanceJob(schedule, req);

        assertEquals(2, result.getJobs().size());
        Job first = line.getJobs().get(0);
        Job extra = line.getJobs().get(1);
        assertEquals(start, first.getStartProductionDateTime());
        assertNotNull(extra.getStartProductionDateTime(), "Extra job should have a start time");
        assertFalse(extra.getStartProductionDateTime().isBefore(first.getStartProductionDateTime()),
                "Extra job should not start before the first maintenance");
        assertEquals(30, extra.getDuration().toMinutes());
        assertEquals(2, extra.getMaintenanceTypeId());
        assertEquals("Мойка", extra.getName());
    }

}
