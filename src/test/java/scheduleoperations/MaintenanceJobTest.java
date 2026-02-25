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
    void addMaintenanceAddsExtraWhenDurationAtLeastSixHours() {
        CleaningDurationUtils.init(Map.of("line1", 40));

        // Seed one job to make line non-empty
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
    void addMaintenanceAddsExtraWhenDurationAtLeastSixHours_EmptyLineReusesStart() {
        CleaningDurationUtils.init(Map.of("line1", 30));

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
    
    @Test
    void dilyCleaningEmptyLine() {
        CleaningDurationUtils.init(Map.of("line1", 30));
        line.setJobs(new ArrayList<>());

        maintenanceJob.addDailyFullCleaning(schedule);

        assertTrue(line.getJobs().isEmpty());
    }

    @Test
    void dailyCleaningNullJobs() {
        CleaningDurationUtils.init(Map.of("line1", 30));
        line.setJobs(null);

        maintenanceJob.addDailyFullCleaning(schedule);

        assertNull(line.getJobs());
    }

    @Test
    void dailyCleaning_WhenNoCleaningConfig() {
        CleaningDurationUtils.init(Map.of("otherLine", 30));
        Product product = schedule.getProducts().get(1);
        Job job = Job.fromDbMaintenanceRow(
                new DbMaintenanceRow(1L, (short) 0, "line1",
                        Timestamp.valueOf(LocalDateTime.of(2025, 1, 15, 8, 0)),
                        Timestamp.valueOf(LocalDateTime.of(2025, 1, 15, 9, 0)),
                        60, 2212L, 1, "note"),
                "Job", product, LocalDateTime.of(2025, 1, 15, 8, 0));
        job.setMaintenance(true);
        line.getJobs().add(job);
        schedule.getJobs().add(job);
        ScheduleUtils.fixLineJobs(line);

        maintenanceJob.addDailyFullCleaning(schedule);

        assertEquals(1, line.getJobs().size());
    }

    @Test
    void dailyCleaning_WhenNoCleaningDuration() {
        CleaningDurationUtils.init(Map.of("line1", 25));
        ConcurrentMap<Integer, String> maintenanceTypes = new ConcurrentHashMap<>();
        maintenanceTypes.put(2, "Мойка");
        when(loadDataService.getMaintenanceTypes()).thenReturn(maintenanceTypes);

        Product maintenanceProduct = schedule.getProducts().get(0);
        Product product = schedule.getProducts().get(1);
        product.setCleaningResults(Map.of(maintenanceProduct, new CleaningResult(10, false)));
        LocalDateTime start = LocalDateTime.of(2025, 1, 15, 8, 0);
        line.setStartDateTime(start.minusMinutes(60));
        // Preceding job so the main job gets a positive wash duration (anchor)
        Job preceding = Job.fromDbMaintenanceRow(
                new DbMaintenanceRow(0L, (short) 0, "line1",
                        Timestamp.valueOf(start.minusMinutes(60)),
                        Timestamp.valueOf(start.minusMinutes(30)),
                        30, 2211L, 1, "preceding"),
                "Preceding", maintenanceProduct, start.minusMinutes(60));
        preceding.setMaintenance(true);
        preceding.setMaintenanceTypeId(1);
        line.getJobs().add(preceding);
        schedule.getJobs().add(preceding);

        Job job = Job.fromDbMaintenanceRow(
                new DbMaintenanceRow(1L, (short) 0, "line1",
                        Timestamp.valueOf(start),
                        Timestamp.valueOf(start.plusMinutes(60)),
                        60, 2212L, 1, "note"),
                "Job", product, start);
        job.setMaintenance(true);
        job.setMaintenanceTypeId(2);
        line.getJobs().add(job);
        schedule.getJobs().add(job);
        ScheduleUtils.fixLineJobs(line);

        maintenanceJob.addDailyFullCleaning(schedule);

        assertEquals(3, line.getJobs().size());
        Job added = line.getJobs().get(2);
        assertTrue(added.isMaintenance());
        assertEquals(2, added.getMaintenanceTypeId());
        assertEquals(25, added.getDuration().toMinutes());
    }

    @Test
    void dailyCleaning_WithStartTimeWhenHasBaseJob() {
        CleaningDurationUtils.init(Map.of("line1", 40));
        ConcurrentMap<Integer, String> maintenanceTypes = new ConcurrentHashMap<>();
        maintenanceTypes.put(2, "Мойка");
        when(loadDataService.getMaintenanceTypes()).thenReturn(maintenanceTypes);

        Product maintenanceProduct = schedule.getProducts().get(0);
        Product normalProduct = schedule.getProducts().get(1);
        normalProduct.setCleaningResults(Map.of(maintenanceProduct, new CleaningResult(15, false)));

        LocalDateTime lineStart = LocalDateTime.of(2025, 1, 15, 7, 30);
        LocalDateTime prodStart = LocalDateTime.of(2025, 1, 15, 8, 15);
        line.setStartDateTime(lineStart);

        Job maintenanceJob1 = Job.fromDbMaintenanceRow(
                new DbMaintenanceRow(1L, (short) 0, "line1",
                        Timestamp.valueOf(lineStart),
                        Timestamp.valueOf(lineStart.plusMinutes(45)),
                        45, 2211L, 1, "note"),
                "Maintenance", maintenanceProduct, lineStart);
        line.getJobs().add(maintenanceJob1);
        schedule.getJobs().add(maintenanceJob1);

        Job job = Job.fromDbJobRow(
                new DbJobRow(null, "", 0, 0, 0.0,
                        Timestamp.valueOf(prodStart),
                        Timestamp.valueOf(prodStart.plusMinutes(60)),
                        60, 2212L, 0, "line1", "Job", 0),
                normalProduct, prodStart, null);
        line.getJobs().add(job);
        schedule.getJobs().add(job);
        ScheduleUtils.fixLineJobs(line);

        maintenanceJob.addDailyFullCleaning(schedule);

        assertEquals(3, line.getJobs().size());
        Job added = line.getJobs().get(2);
        assertTrue(added.isMaintenance());
        assertEquals(2, added.getMaintenanceTypeId());
        assertEquals(40, added.getDuration().toMinutes());
    }

    @Test
    void extendLineMaxEndTime_skipsWhenLastJobEndDateTimeNull() {
        CleaningDurationUtils.init(Map.of("line1", 30));

        Line lineNoStart = new Line("line1", "Line 1");
        lineNoStart.setStartDateTime(null);
        schedule.setLines(List.of(lineNoStart));

        Product product = schedule.getProducts().get(1);
        Job job = Job.fromDbJobRow(
                new DbJobRow(null, "", 0, 0, 0.0,
                        null, null, 60, 2212L, 0, "line1", "Job", 0),
                product, null, null);
        lineNoStart.getJobs().add(job);
        schedule.getJobs().add(job);
        ScheduleUtils.fixLineJobs(lineNoStart);

        maintenanceJob.addDailyFullCleaning(schedule);

        // Single job has null endDateTime, so no anchor → daily cleaning not added
        assertEquals(1, lineNoStart.getJobs().size());
        assertNull(lineNoStart.getMaxEndTime());
    }

    @Test
    void dailyCleaning_WhenCleaningUtilsNull() {
        CleaningDurationUtils.init(null);
        Product product = schedule.getProducts().get(1);
        Job job = Job.fromDbMaintenanceRow(
                new DbMaintenanceRow(1L, (short) 0, "line1",
                        Timestamp.valueOf(LocalDateTime.of(2025, 1, 15, 8, 0)),
                        Timestamp.valueOf(LocalDateTime.of(2025, 1, 15, 9, 0)),
                        60, 2212L, 1, "note"),
                "Job", product, LocalDateTime.of(2025, 1, 15, 8, 0));
        job.setMaintenance(true);
        line.getJobs().add(job);
        schedule.getJobs().add(job);
        ScheduleUtils.fixLineJobs(line);

        maintenanceJob.addDailyFullCleaning(schedule);

        assertEquals(1, line.getJobs().size());
    }

    @Test
    void dailyCleaning_MultipleLines() {
        CleaningDurationUtils.init(Map.of("line1", 30, "line2", 25));
        ConcurrentMap<Integer, String> maintenanceTypes = new ConcurrentHashMap<>();
        maintenanceTypes.put(2, "Мойка");
        when(loadDataService.getMaintenanceTypes()).thenReturn(maintenanceTypes);

        Line line2 = new Line("line2", "Line 2", "op2", LocalDateTime.now());
        schedule.setLines(List.of(line, line2));

        Product maintenanceProduct = schedule.getProducts().get(0);
        Product product = schedule.getProducts().get(1);
        product.setCleaningResults(Map.of(maintenanceProduct, new CleaningResult(10, false)));
        LocalDateTime start = LocalDateTime.of(2025, 1, 15, 8, 0);
        line.setStartDateTime(start.minusMinutes(60));
        line2.setStartDateTime(start.minusMinutes(60));

        Job prec1 = Job.fromDbMaintenanceRow(
                new DbMaintenanceRow(0L, (short) 0, "line1",
                        Timestamp.valueOf(start.minusMinutes(60)), Timestamp.valueOf(start.minusMinutes(30)),
                        30, 2211L, 1, "p1"),
                "P1", maintenanceProduct, start.minusMinutes(60));
        prec1.setMaintenance(true);
        prec1.setMaintenanceTypeId(1);
        line.getJobs().add(prec1);
        schedule.getJobs().add(prec1);
        Job job1 = Job.fromDbMaintenanceRow(
                new DbMaintenanceRow(1L, (short) 0, "line1",
                        Timestamp.valueOf(start), Timestamp.valueOf(start.plusMinutes(60)),
                        60, 2212L, 1, "note"),
                "Job", product, start);
        job1.setMaintenance(true);
        job1.setMaintenanceTypeId(2);
        line.getJobs().add(job1);
        schedule.getJobs().add(job1);
        ScheduleUtils.fixLineJobs(line);

        Job prec2 = Job.fromDbMaintenanceRow(
                new DbMaintenanceRow(0L, (short) 0, "line2",
                        Timestamp.valueOf(start.minusMinutes(60)), Timestamp.valueOf(start.minusMinutes(30)),
                        30, 2211L, 1, "p2"),
                "P2", maintenanceProduct, start.minusMinutes(60));
        prec2.setMaintenance(true);
        prec2.setMaintenanceTypeId(1);
        line2.getJobs().add(prec2);
        schedule.getJobs().add(prec2);
        Job job2 = Job.fromDbMaintenanceRow(
                new DbMaintenanceRow(2L, (short) 0, "line2",
                        Timestamp.valueOf(start), Timestamp.valueOf(start.plusMinutes(60)),
                        60, 2213L, 1, "note2"),
                "Job2", product, start);
        job2.setMaintenance(true);
        job2.setMaintenanceTypeId(1);
        line2.getJobs().add(job2);
        schedule.getJobs().add(job2);
        ScheduleUtils.fixLineJobs(line2);

        maintenanceJob.addDailyFullCleaning(schedule);

        assertEquals(3, line.getJobs().size());
        assertEquals(3, line2.getJobs().size());
        assertEquals(30, line.getJobs().get(2).getDuration().toMinutes());
        assertEquals(25, line2.getJobs().get(2).getDuration().toMinutes());
    }

    @Test
    void dailyCleaning_Maintenance2WhenLonger() {
        CleaningDurationUtils.init(Map.of("line1", 35));
        ConcurrentMap<Integer, String> maintenanceTypes = new ConcurrentHashMap<>();
        maintenanceTypes.put(2, "Мойка");
        when(loadDataService.getMaintenanceTypes()).thenReturn(maintenanceTypes);

        Product maintenanceProduct = schedule.getProducts().get(0);
        Product product = schedule.getProducts().get(1);
        product.setCleaningResults(Map.of(maintenanceProduct, new CleaningResult(10, false)));
        LocalDateTime prodStart = LocalDateTime.of(2025, 1, 15, 9, 0);
        LocalDateTime lineStart = LocalDateTime.of(2025, 1, 15, 7, 0);
        line.setStartDateTime(lineStart);
        Job preceding = Job.fromDbMaintenanceRow(
                new DbMaintenanceRow(0L, (short) 0, "line1",
                        Timestamp.valueOf(lineStart),
                        Timestamp.valueOf(LocalDateTime.of(2025, 1, 15, 8, 0)),
                        60, 2211L, 1, "preceding"),
                "Preceding", maintenanceProduct, lineStart);
        preceding.setMaintenance(true);
        preceding.setMaintenanceTypeId(1);
        line.getJobs().add(preceding);
        schedule.getJobs().add(preceding);

        Job maint2 = Job.fromDbMaintenanceRow(
                new DbMaintenanceRow(1L, (short) 0, "line1",
                        Timestamp.valueOf(LocalDateTime.of(2025, 1, 15, 8, 0)),
                        Timestamp.valueOf(prodStart.plusMinutes(60)),
                        60, 2212L, 2, "Мойка"),
                "Мойка", product, prodStart);
        maint2.setMaintenance(true);
        maint2.setMaintenanceTypeId(2);
        maint2.setEndDateTime(prodStart.plusMinutes(60));
        line.getJobs().add(maint2);
        schedule.getJobs().add(maint2);
        ScheduleUtils.fixLineJobs(line);

        maintenanceJob.addDailyFullCleaning(schedule);

        assertEquals(3, line.getJobs().size());
        Job added = line.getJobs().get(2);
        assertTrue(added.isMaintenance());
        assertEquals(2, added.getMaintenanceTypeId());
        assertEquals(35, added.getDuration().toMinutes());
    }
}
