package scheduleoperations.utils;

import org.acme.foodpackaging.domain.*;
import org.acme.foodpackaging.record.DbJobRow;
import org.acme.foodpackaging.record.DowntimeData;
import org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils;
import org.acme.foodpackaging.scheduleoperations.utils.SpeedCacheUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import org.apache.commons.lang3.tuple.Pair;

import static org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils.*;
import static org.junit.jupiter.api.Assertions.*;

class ScheduleUtilsTest {

    private Line line;
    private Job job1, job2, job3;
    private PackagingSchedule schedule;
    private Product product;

    @BeforeEach
    void setup() {
        // Создание продуктов
        Product maintenanceProduct = new Product("MAINTENANCE", "Maintenance Product");
        Product normalProduct = new Product("NORMAL", "Normal Product");
        product = new Product("1", "Vanilla");

        // Инициализация карт cleaningDurations
        Map<Product, Duration> cleaningForMaintenance = new HashMap<>();
        cleaningForMaintenance.put(maintenanceProduct, Duration.ZERO);
        cleaningForMaintenance.put(normalProduct, Duration.ofMinutes(10));
        maintenanceProduct.setCleaningDurations(cleaningForMaintenance);

        Map<Product, Duration> cleaningForNormal = new HashMap<>();
        cleaningForNormal.put(maintenanceProduct, Duration.ofMinutes(5));
        cleaningForNormal.put(normalProduct, Duration.ZERO);
        normalProduct.setCleaningDurations(cleaningForNormal);

        // Инициализация SpeedCacheUtils
        Map<String, Map<String, Pair<Integer, Integer>>> speeds = new HashMap<>();
        Map<String, Pair<Integer, Integer>> productSpeeds = new HashMap<>();
        productSpeeds.put("MAINTENANCE", Pair.of(1, 0));
        productSpeeds.put("NORMAL", Pair.of(2, 1));
        speeds.put("line1", productSpeeds);
        SpeedCacheUtils.init(speeds);

        // Создание линии
        line = new Line("line1", "Line 1", "operator", LocalDateTime.now());

        // Создание задач
        job1 = new Job("1", "Job 1", normalProduct, null, 1, false, null);
        job2 = new Job("2", "Job 2", normalProduct, null, 1, false, null);
        job3 = new Job("3", "Job 3", maintenanceProduct, null, 1, false, null);

        line.setJobs(new ArrayList<>(Arrays.asList(job1, job2, job3)));

        // Создание schedule
        schedule = new PackagingSchedule();
        schedule.setProducts(List.of(maintenanceProduct, normalProduct));
        schedule.setWorkCalendar(new WorkCalendar(LocalDate.now()));
        schedule.setJobs(new ArrayList<>());
        schedule.setLines(new ArrayList<>(List.of(line)));
    }

    @Test
    void fixLineJobs() {
        ScheduleUtils.fixLineJobs(line);

        assertEquals(line, job1.getLine());
        assertEquals(line, job2.getLine());
        assertEquals(line, job3.getLine());

        assertNull(job1.getPreviousJob());
        assertEquals(job2, job1.getNextJob());

        assertEquals(job1, job2.getPreviousJob());
        assertEquals(job3, job2.getNextJob());

        assertEquals(job2, job3.getPreviousJob());
        assertNull(job3.getNextJob());
    }

    @Test
    void fixPinnedJobs() {
        job1.setMaintenance(true);
        job2.setMaintenance(false);
        job3.setMaintenance(true);

        ScheduleUtils.fixPinnedJobs(line);

        // последний pinned индекс = 2 (job3)
        assertEquals(3, line.getFirstUnpinnedIndex());
    }

    @Test
    void fixPinnedJobs_newIndexLessThenCurrent() {
        line.setFirstUnpinnedIndex(3);
        job1.setMaintenance(true);

        ScheduleUtils.fixPinnedJobs(line);
        assertEquals(3, line.getFirstUnpinnedIndex());
    }

    @Test
    void fixPinnedJobs_newIndexMoreThenCurrent() {
        job1.setMaintenance(true);

        ScheduleUtils.fixPinnedJobs(line);
        assertEquals(1, line.getFirstUnpinnedIndex());
    }

    @Test
    void fixEndDateTime() {
        LocalDateTime maxTime = LocalDateTime.now().plusHours(5);

        ScheduleUtils.fixEndDateTime(line.getJobs(), maxTime);

        assertEquals(maxTime, job1.getMaxEndTime());
        assertEquals(maxTime, job2.getMaxEndTime());
        assertEquals(maxTime, job3.getMaxEndTime());
    }

    @Test
    void findLineById() {
        Line found = ScheduleUtils.findLineById(schedule, "line1");
        assertEquals(line, found);

        assertNull(ScheduleUtils.findLineById(schedule, "not found"));
    }

    @Test
    void setLinestartDateTime() {
        LocalDateTime newStart = LocalDateTime.now().plusDays(1);
        ScheduleUtils.setLineStartDateTime(line, newStart);

        assertEquals(newStart, line.getStartDateTime());
    }

    @Test
    void setLineMaxEndDateTime_shouldUpdateMaxEndTime() {
        LocalDateTime newEnd = LocalDateTime.now().plusDays(1);
        ScheduleUtils.setLineMaxEndDateTime(line, newEnd);

        assertEquals(newEnd, line.getMaxEndTime());
    }

    @Test
    void pinnAllLines() {
        job3.setEndDateTime(LocalDateTime.now().plusHours(3));
        ScheduleUtils.pinnAllLines(List.of(line));

        assertEquals(line.getJobs().size(), line.getFirstUnpinnedIndex());
        assertEquals(job3.getEndDateTime(), line.getStartDateTime());
    }

    @Test
    void unPinnAllLines() {
        line.setFirstUnpinnedIndex(5);
        ScheduleUtils.unPinnAllLines(List.of(line));

        assertEquals(0, line.getFirstUnpinnedIndex());
    }

    @Test
    void removesJobsWithNullLine() {
        Job jobWithLine = new Job("1", "Job 1", product, null, 1, false, null);
        Job jobWithoutLine = new Job("2", "Job 2", product, null, 1, false, null);
        Job anotherJobWithLine = new Job("3", "Job 3", product, null, 1, false, null);

        jobWithLine.setLine(line);

        anotherJobWithLine.setLine(line);

        List<Job> jobs = new ArrayList<>(Arrays.asList(jobWithLine, jobWithoutLine, anotherJobWithLine));

        ScheduleUtils.removeJobsWithoutLine(jobs);

        assertEquals(2, jobs.size());
        assertTrue(jobs.contains(jobWithLine));
        assertTrue(jobs.contains(anotherJobWithLine));
        assertFalse(jobs.contains(jobWithoutLine));
    }

    @Test
    void keepAllJobsWhenAllHaveLines() {
        Job jobWithLine1 = new Job("1", "Job 1", product, null, 1, false, null);
        Job jobWithLine2 = new Job("2", "Job 2", product, null, 1, false, null);

        jobWithLine1.setLine(line);
        jobWithLine2.setLine(line);

        List<Job> jobs = new ArrayList<>(Arrays.asList(jobWithLine1, jobWithLine2));

        ScheduleUtils.removeJobsWithoutLine(jobs);

        assertEquals(2, jobs.size());
        assertTrue(jobs.contains(jobWithLine1));
        assertTrue(jobs.contains(jobWithLine2));
    }

    @Test
    void removeAllJobsWhenAllHaveNullLine() {

        Job jobWithNullLine1 = new Job("1", "Job 1", product, null, 1, false, null);
        Job jobWithNullLine2 = new Job("2", "Job 2", product, null, 1, false, null);

        List<Job> jobs = new ArrayList<>(Arrays.asList(jobWithNullLine1, jobWithNullLine2));

        ScheduleUtils.removeJobsWithoutLine(jobs);

        assertTrue(jobs.isEmpty());
    }

    @Test
    void handlesNullList() {

        assertDoesNotThrow(() -> ScheduleUtils.removeJobsWithoutLine(null));
    }

    @Test
    void handlesEmptyList() {

        List<Job> jobs = new ArrayList<>();

        ScheduleUtils.removeJobsWithoutLine(jobs);

        assertTrue(jobs.isEmpty());
    }

    @Test
    void convertsMapToList() {
        Timestamp timestamp = Timestamp.valueOf(LocalDateTime.now());
        DbJobRow row1 = new DbJobRow(timestamp, "KMC1", 1, 10, 100.0, timestamp, timestamp, 60, 1L, 1, "L1",
                "Product 1", 18, 100);
        DbJobRow row2 = new DbJobRow(timestamp, "KMC2", 2, 20, 200.0, timestamp, timestamp, 120, 2L, 2, "L2",
                "Product 2", 19, 100);
        DbJobRow row3 = new DbJobRow(timestamp, "KMC3", 3, 30, 300.0, timestamp, timestamp, 180, 3L, 3, "L3",
                "Product 3", 20, 100);

        Map<Long, DbJobRow> rows = new HashMap<>();
        rows.put(1L, row1);
        rows.put(2L, row2);
        rows.put(3L, row3);

        List<DbJobRow> result = ScheduleUtils.getDbJobRowList(rows);

        assertNotNull(result);
        assertEquals(3, result.size());
        assertTrue(result.contains(row1));
        assertTrue(result.contains(row2));
        assertTrue(result.contains(row3));
    }

    @Test
    void returnEmptyListForNullMap() {
        List<DbJobRow> result = ScheduleUtils.getDbJobRowList(null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void returnEmptyListForEmptyMap() {
        Map<Long, DbJobRow> emptyMap = new HashMap<>();

        List<DbJobRow> result = ScheduleUtils.getDbJobRowList(emptyMap);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void returnListWithAllValues() {
        Timestamp timestamp = Timestamp.valueOf(LocalDateTime.now());
        DbJobRow row1 = new DbJobRow(timestamp, "KMC1", 1, 10, 100.0, timestamp, timestamp, 60, 1L, 1, "L1",
                "Product 1", 18, 100);
        DbJobRow row2 = new DbJobRow(timestamp, "KMC2", 2, 20, 200.0, timestamp, timestamp, 120, 2L, 2, "L2",
                "Product 2", 19, 100);

        Map<Long, DbJobRow> rows = Map.of(1L, row1, 2L, row2);

        List<DbJobRow> result = ScheduleUtils.getDbJobRowList(rows);

        assertEquals(2, result.size());
        // Verify all values from map are in the list
        assertEquals(2, result.stream().filter(r -> r.equals(row1) || r.equals(row2)).count());
    }

    @Test
    void initLinesJobList_WhenLinesListNotNull() {

        Job j = new Job();
        j.setLine(line);

        schedule.setJobs(List.of(j));

        initLinesJobList(schedule);
        assertEquals(1, schedule.getLines().getFirst().getJobs().size());
        assertEquals("line1", line.getJobs().getFirst().getLine().getId());
    }

    @Test
    void initLinesJobList_WhenLinesListIsNull() {

        Job j = new Job();
        j.setLine(line);

        schedule.setJobs(List.of(j));
        schedule.setLines(null);

        initLinesJobList(schedule);
        assertNull(schedule.getLines());
    }

    @Test
    void downtimeData_shouldReturnCorrectDuration_onlyForOverloadedJobs() {

        schedule.setWorkCalendar(new WorkCalendar(LocalDate.of(2026, 4, 6)));
        schedule.setOverloadedIds(Set.of("1", "2"));

        job1.setLine(line);
        job1.setStartProductionDateTime(LocalDateTime.of(2026, 4, 6, 10, 0));
        job1.setStartCleaningDateTime(LocalDateTime.of(2026, 4, 6, 9, 30));

        job2.setLine(line);
        job2.setStartProductionDateTime(LocalDateTime.of(2026, 4, 6, 12, 0));
        job2.setStartCleaningDateTime(LocalDateTime.of(2026, 4, 6, 11, 30));

        DowntimeData result = getDowntimeData(schedule);

        assertEquals(60, result.downtime());
        assertEquals(60, result.lines().get(line.getId()));
    }

    @Test
    void downtimeData_shouldIgnoreNullIdAndJob() {

        schedule.setWorkCalendar(new WorkCalendar(LocalDate.of(2026, 4, 6)));
        schedule.setOverloadedIds(Set.of("1", "2"));

        job1.setLine(line);
        job1.setStartProductionDateTime(LocalDateTime.of(2026, 4, 6, 10, 0));
        job1.setStartCleaningDateTime(LocalDateTime.of(2026, 4, 6, 9, 30));

        job2.setLine(line);
        job2.setStartProductionDateTime(LocalDateTime.of(2026, 4, 6, 12, 0));
        job2.setStartCleaningDateTime(LocalDateTime.of(2026, 4, 6, 11, 30));

        job2.setId(null);
        line.setJobs(Arrays.asList(job1, job2, null));

        DowntimeData result = getDowntimeData(schedule);

        assertEquals(30, result.downtime());
        assertEquals(30, result.lines().get(line.getId()));
    }

    @Test
    void downtimeData_shouldIgnoreJobsNotInOverloadedIds() {

        schedule.setWorkCalendar(new WorkCalendar(LocalDate.of(2026, 4, 6)));
        schedule.setOverloadedIds(Set.of("1"));

        job1.setStartCleaningDateTime(LocalDateTime.of(2026, 4, 6, 9, 0));
        job1.setStartProductionDateTime(LocalDateTime.of(2026, 4, 6, 10, 0));

        job2.setStartCleaningDateTime(LocalDateTime.of(2026, 4, 6, 11, 0));
        job2.setStartProductionDateTime(LocalDateTime.of(2026, 4, 6, 12, 0));

        DowntimeData result = getDowntimeData(schedule);

        assertEquals(60, result.downtime());
    }

    @Test
    void downtimeData_isNegativeCleaning() {

        schedule.setWorkCalendar(new WorkCalendar(LocalDate.of(2026, 4, 6)));
        schedule.setOverloadedIds(Set.of("1", "2"));

        job1.setStartCleaningDateTime(LocalDateTime.of(2026, 4, 6, 9, 0));
        job1.setStartProductionDateTime(LocalDateTime.of(2026, 4, 6, 10, 0));

        job2.setStartCleaningDateTime(LocalDateTime.of(2026, 4, 6, 12, 0));
        job2.setStartProductionDateTime(LocalDateTime.of(2026, 4, 6, 11, 0));

        DowntimeData result = getDowntimeData(schedule);

        assertEquals(60, result.downtime());
    }

    @Test
    void downtimeData_startProductionIsNull_shouldReturnZero() {

        schedule.setWorkCalendar(new WorkCalendar(LocalDate.of(2026, 4, 6)));
        schedule.setOverloadedIds(Set.of("1"));

        job1.setStartCleaningDateTime(null);
        job1.setStartProductionDateTime(LocalDateTime.of(2026, 4, 6, 10, 0));

        job2.setStartCleaningDateTime(LocalDateTime.of(2026, 4, 6, 11, 0));
        job2.setStartProductionDateTime(null);

        DowntimeData result = getDowntimeData(schedule);

        assertEquals(0, result.downtime());
    }

    @Test
    void downtimeData_emptyOverloadedIds_shouldReturnZero() {

        schedule.setWorkCalendar(new WorkCalendar(LocalDate.of(2026, 4, 6)));
        schedule.setOverloadedIds(Set.of());

        DowntimeData result = getDowntimeData(schedule);
        assertEquals(0, result.downtime());
        assertTrue(result.lines().isEmpty());
    }

    @Test
    void downtimeData_solutionIsnNull_shouldReturnZero() {

        schedule = null;
        DowntimeData result = getDowntimeData(schedule);
        assertEquals(0, result.downtime());
        assertTrue(result.lines().isEmpty());
    }

    @Test
    void downtimeData_WorkCalendarIsNull_shouldReturnZero() {

        DowntimeData result = getDowntimeData(schedule);
        assertEquals(0, result.downtime());
        assertTrue(result.lines().isEmpty());
    }

    @Test
    void downtimeData_LinesIsNull_shouldReturnZero() {

        schedule.setLines(null);
        DowntimeData result = getDowntimeData(schedule);
        assertEquals(0, result.downtime());
        assertTrue(result.lines().isEmpty());
    }

    @Test
    void downtimeData_lineIsnull_shouldReturnZero() {

        schedule.setWorkCalendar(new WorkCalendar(LocalDate.of(2026, 4, 6)));
        schedule.setOverloadedIds(Set.of("1"));
        schedule.setLines(Arrays.asList(line, null));

        DowntimeData result = getDowntimeData(schedule);
        assertEquals(0, result.downtime());
        assertEquals(1, result.lines().size());
    }

    @Test
    void downtimeData_PlanningDateIsNull_shouldReturnZero() {

        schedule.setWorkCalendar(new WorkCalendar(LocalDate.of(2026, 4, 6)));
        schedule.setOverloadedIds(Set.of("1"));
       
        schedule.getWorkCalendar().setPlanningDate(null);
        DowntimeData result = getDowntimeData(schedule);
        assertEquals(0, result.downtime());
        assertTrue(result.lines().isEmpty());
    }

    @Test
    void downtimeData_lineJobsIsNull_shouldReturnZero() {

        schedule.setWorkCalendar(new WorkCalendar(LocalDate.of(2026, 4, 6)));
        schedule.setOverloadedIds(Set.of("1"));
        Line line2 = new Line("line2", "Line 2");
        schedule.setLines(Arrays.asList(line, line2));

        DowntimeData result = getDowntimeData(schedule);
        assertEquals(0, result.downtime());
        assertEquals(2, result.lines().size());
    }
}
