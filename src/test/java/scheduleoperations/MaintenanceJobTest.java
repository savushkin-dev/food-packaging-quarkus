package scheduleoperations;

import org.acme.foodpackaging.dto.MaintenanceRequest;
import org.acme.foodpackaging.dto.DbMaintenanceRow;
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
    void dailyCleaningEmptyLine() {
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

    // --- addDailyFullCleaning (new logic: anchor from reversed jobs, dailyCleaningStart <= last job end) ---

    @Test
    void addDailyFullCleaning_skipsWhenNoAnchor() {
        CleaningDurationUtils.init(Map.of("line1", 30));
        Product product = schedule.getProducts().get(1);
        // One production job with no preceding cleaning gap >= 30 min; no type-2 maintenance
        LocalDateTime start = LocalDateTime.of(2025, 1, 15, 8, 0);
        Job job = Job.fromDbJobRow(
                new DbJobRow(null, "", 0, 0, 0.0,
                        Timestamp.valueOf(start),
                        Timestamp.valueOf(start.plusMinutes(60)),
                        60, 2212L, 0, "line1", "Job", 0),
                product, start, null);
        line.setStartDateTime(start);
        line.getJobs().add(job);
        schedule.getJobs().add(job);
        ScheduleUtils.fixLineJobs(line);

        maintenanceJob.addDailyFullCleaning(schedule);

        assertEquals(1, line.getJobs().size());
    }

    @Test
    void addDailyFullCleaning_skipsWhenDailyCleaningStartAfterLastJobEnd() {
        CleaningDurationUtils.init(Map.of("line1", 30));
        Product maintenanceProduct = schedule.getProducts().get(0);
        // Single maintenance type 2, duration 40 min; dailyCleaningStart = end+24h, last job = same → skip
        LocalDateTime jobEnd = LocalDateTime.of(2025, 1, 15, 10, 0);
        Job maint = Job.fromDbMaintenanceRow(
                new DbMaintenanceRow(1L, (short) 0, "line1",
                        Timestamp.valueOf(jobEnd.minusMinutes(40)),
                        Timestamp.valueOf(jobEnd),
                        40, 2212L, 2, "Мойка"),
                "Мойка", maintenanceProduct, jobEnd.minusMinutes(40));
        maint.setMaintenance(true);
        maint.setMaintenanceTypeId(2);
        maint.setEndDateTime(jobEnd);
        line.setStartDateTime(jobEnd.minusMinutes(40));
        line.getJobs().add(maint);
        schedule.getJobs().add(maint);
        ScheduleUtils.fixLineJobs(line);

        maintenanceJob.addDailyFullCleaning(schedule);

        assertEquals(1, line.getJobs().size());
    }

    @Test
    void addDailyFullCleaning_addsWhenAnchorMaintenanceType2AndLastJobEndAfterStart() {
        CleaningDurationUtils.init(Map.of("line1", 30));
        ConcurrentMap<Integer, String> maintenanceTypes = new ConcurrentHashMap<>();
        maintenanceTypes.put(2, "Мойка");
        when(loadDataService.getMaintenanceTypes()).thenReturn(maintenanceTypes);

        Product maintenanceProduct = schedule.getProducts().get(0);
        Product normalProduct = schedule.getProducts().get(1);
        LocalDateTime day1At10 = LocalDateTime.of(2025, 1, 15, 10, 0);
        LocalDateTime day2At15 = LocalDateTime.of(2025, 1, 16, 15, 0);
        LocalDateTime day2_0920 = LocalDateTime.of(2025, 1, 16, 9, 20);

        line.setStartDateTime(day2_0920); // so fixLineJobs (after add) places all jobs on Jan 16
        Job maint = Job.fromDbMaintenanceRow(
                new DbMaintenanceRow(1L, (short) 0, "line1",
                        Timestamp.valueOf(day1At10.minusMinutes(40)),
                        Timestamp.valueOf(day1At10),
                        40, 2211L, 2, "Мойка"),
                "Мойка", maintenanceProduct, day1At10.minusMinutes(40));
        maint.setMaintenance(true);
        maint.setMaintenanceTypeId(2);
        maint.setEndDateTime(day1At10);
        maint.setLine(line);
        line.getJobs().add(maint);
        schedule.getJobs().add(maint);

        Job prod = Job.fromDbJobRow(
                new DbJobRow(null, "", 0, 0, 0.0,
                        Timestamp.valueOf(day1At10.plusMinutes(30)),
                        Timestamp.valueOf(day2At15),
                        60, 2212L, 0, "line1", "Job", 0),
                normalProduct, day1At10.plusMinutes(30), null);
        prod.setStartCleaningDateTime(day1At10);
        prod.setStartProductionDateTime(day1At10.plusMinutes(30));
        prod.setEndDateTime(day2At15);
        prod.setLine(line);
        line.getJobs().add(prod);
        schedule.getJobs().add(prod);

        maintenanceJob.addDailyFullCleaning(schedule);

        assertEquals(3, line.getJobs().size());
        Job added = line.getJobs().get(2);
        assertTrue(added.isMaintenance());
        assertEquals(2, added.getMaintenanceTypeId());
        assertEquals(30, added.getDuration().toMinutes());
        // fixLineJobs recalculates the new job's start from previous job end + cleaning; assert next day and ~10:00
        assertEquals(LocalDate.of(2025, 1, 16), added.getStartProductionDateTime().toLocalDate());
        assertTrue(added.getStartProductionDateTime().getHour() >= 10 && added.getStartProductionDateTime().getHour() <= 11);
    }

    @Test
    void addDailyFullCleaning_addsWhenAnchorFromCleaningGap() {
        CleaningDurationUtils.init(Map.of("line1", 25));
        ConcurrentMap<Integer, String> maintenanceTypes = new ConcurrentHashMap<>();
        maintenanceTypes.put(2, "Мойка");
        when(loadDataService.getMaintenanceTypes()).thenReturn(maintenanceTypes);

        Product normalProduct = schedule.getProducts().get(1);
        LocalDateTime day1At8 = LocalDateTime.of(2025, 1, 15, 8, 0);
        LocalDateTime day1At830 = LocalDateTime.of(2025, 1, 15, 8, 30);
        LocalDateTime day2At10 = LocalDateTime.of(2025, 1, 16, 10, 0);

        Job prod = Job.fromDbJobRow(
                new DbJobRow(null, "", 0, 0, 0.0,
                        Timestamp.valueOf(day1At830),
                        Timestamp.valueOf(day2At10),
                        60, 2212L, 0, "line1", "Job", 0),
                normalProduct, day1At830, null);
        prod.setStartProductionDateTime(day1At8);
        prod.setStartCleaningDateTime(day1At830);
        prod.setEndDateTime(day2At10);
        prod.setLine(line);
        line.setStartDateTime(day1At8);
        line.getJobs().add(prod);
        schedule.getJobs().add(prod);

        maintenanceJob.addDailyFullCleaning(schedule);

        assertEquals(2, line.getJobs().size());
        Job added = line.getJobs().stream()
                .filter(j -> j.isMaintenance() && j.getMaintenanceTypeId() == 2 && j.getDuration().toMinutes() == 25)
                .findFirst().orElseThrow();
        assertTrue(added.isMaintenance());
        assertEquals(2, added.getMaintenanceTypeId());
        assertEquals(25, added.getDuration().toMinutes());
        // fixLineJobs recalculates from line start (day1At8): new job ends up after previous; accept same or next day
        assertTrue(added.getStartProductionDateTime().toLocalDate().equals(LocalDate.of(2025, 1, 16))
                || added.getStartProductionDateTime().toLocalDate().equals(LocalDate.of(2025, 1, 15)));
    }

    @Test
    void addDailyFullCleaning_multipleLines() {
        CleaningDurationUtils.init(Map.of("line1", 30, "line2", 25));
        ConcurrentMap<Integer, String> maintenanceTypes = new ConcurrentHashMap<>();
        maintenanceTypes.put(2, "Мойка");
        when(loadDataService.getMaintenanceTypes()).thenReturn(maintenanceTypes);

        Line line2 = new Line("line2", "Line 2", "op2", LocalDateTime.now());
        schedule.setLines(List.of(line, line2));

        Product maintenanceProduct = schedule.getProducts().get(0);
        Product normalProduct = schedule.getProducts().get(1);
        LocalDateTime day1At10 = LocalDateTime.of(2025, 1, 15, 10, 0);
        LocalDateTime day2At15 = LocalDateTime.of(2025, 1, 16, 15, 0);

        // Line1: maint type 2 end 10:00, prod end day2 15:00 (no fixLineJobs so end stays)
        Job m1 = Job.fromDbMaintenanceRow(
                new DbMaintenanceRow(1L, (short) 0, "line1",
                        Timestamp.valueOf(day1At10.minusMinutes(40)), Timestamp.valueOf(day1At10),
                        40, 2211L, 2, "Мойка"),
                "Мойка", maintenanceProduct, day1At10.minusMinutes(40));
        m1.setMaintenance(true);
        m1.setMaintenanceTypeId(2);
        m1.setEndDateTime(day1At10);
        m1.setLine(line);
        line.getJobs().add(m1);
        schedule.getJobs().add(m1);
        Job p1 = Job.fromDbJobRow(
                new DbJobRow(null, "", 0, 0, 0.0,
                        Timestamp.valueOf(day1At10.plusMinutes(30)), Timestamp.valueOf(day2At15),
                        60, 2212L, 0, "line1", "Job", 0),
                normalProduct, day1At10.plusMinutes(30), null);
        p1.setStartCleaningDateTime(day1At10);
        p1.setStartProductionDateTime(day1At10.plusMinutes(30));
        p1.setEndDateTime(day2At15);
        p1.setLine(line);
        line.getJobs().add(p1);
        schedule.getJobs().add(p1);

        // Line2: same pattern
        Job m2 = Job.fromDbMaintenanceRow(
                new DbMaintenanceRow(2L, (short) 0, "line2",
                        Timestamp.valueOf(day1At10.minusMinutes(30)), Timestamp.valueOf(day1At10),
                        30, 2213L, 2, "Мойка"),
                "Мойка", maintenanceProduct, day1At10.minusMinutes(30));
        m2.setMaintenance(true);
        m2.setMaintenanceTypeId(2);
        m2.setEndDateTime(day1At10);
        m2.setLine(line2);
        line2.getJobs().add(m2);
        schedule.getJobs().add(m2);
        Job p2 = Job.fromDbJobRow(
                new DbJobRow(null, "", 0, 0, 0.0,
                        Timestamp.valueOf(day1At10.plusMinutes(30)), Timestamp.valueOf(day2At15),
                        60, 2214L, 0, "line2", "Job2", 0),
                normalProduct, day1At10.plusMinutes(30), null);
        p2.setStartCleaningDateTime(day1At10);
        p2.setStartProductionDateTime(day1At10.plusMinutes(30));
        p2.setEndDateTime(day2At15);
        p2.setLine(line2);
        line2.getJobs().add(p2);
        schedule.getJobs().add(p2);

        maintenanceJob.addDailyFullCleaning(schedule);

        assertEquals(3, line.getJobs().size());
        assertEquals(3, line2.getJobs().size());
        assertEquals(30, line.getJobs().get(2).getDuration().toMinutes());
        assertEquals(25, line2.getJobs().get(2).getDuration().toMinutes());
    }
}
