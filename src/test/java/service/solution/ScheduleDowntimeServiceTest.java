package service.solution;

import org.acme.foodpackaging.domain.*;
import org.acme.foodpackaging.service.solution.ScheduleDowntimeService;
import org.acme.foodpackaging.service.solution.value.DowntimeDataValue;
import org.acme.foodpackaging.utils.SpeedCacheUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.*;
import org.apache.commons.lang3.tuple.Pair;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты на {@link ScheduleDowntimeService} — простой по уже посчитанному
 * солвером расписанию (в памяти), а не по логам БД конкретной партии
 * (см. BatchDowntimeService).
 */
class ScheduleDowntimeServiceTest {

    private final ScheduleDowntimeService service = new ScheduleDowntimeService();

    private Line line;
    private Job job1, job2, job3;
    private PackagingSchedule schedule;
    private LocalDateTime now;

    @BeforeEach
    void setup() {
        // Создание продуктов
        Product maintenanceProduct = new Product("MAINTENANCE", "Maintenance Product");
        Product normalProduct = new Product("NORMAL", "Normal Product");

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
        now = LocalDateTime.now();

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
    void DowntimeDataValue_shouldReturnCorrectDuration_onlyForOverloadedJobs() {

        schedule.setWorkCalendar(new WorkCalendar(LocalDate.of(2026, Month.APRIL, 6)));
        schedule.setOverloadedIds(Set.of("1", "2"));

        job1.setLine(line);
        job1.setStartProductionDateTime(LocalDateTime.of(2026, Month.APRIL, 6, 10, 0));
        job1.setStartCleaningDateTime(LocalDateTime.of(2026, Month.APRIL, 6, 9, 30));

        job2.setLine(line);
        job2.setStartProductionDateTime(LocalDateTime.of(2026, Month.APRIL, 6, 12, 0));
        job2.setStartCleaningDateTime(LocalDateTime.of(2026, Month.APRIL, 6, 11, 30));

        DowntimeDataValue result = service.calculate(schedule);

        assertEquals(60, result.downtime());
        assertEquals(60, result.lines().get(line.getId()));
    }

    @Test
    void DowntimeDataValue_shouldIgnoreNullIdAndJob() {

        schedule.setWorkCalendar(new WorkCalendar(LocalDate.of(2026, Month.APRIL, 6)));
        schedule.setOverloadedIds(Set.of("1", "2"));

        job1.setLine(line);
        job1.setStartProductionDateTime(LocalDateTime.of(2026, Month.APRIL, 6, 10, 0));
        job1.setStartCleaningDateTime(LocalDateTime.of(2026, Month.APRIL, 6, 9, 30));

        job2.setLine(line);
        job2.setStartProductionDateTime(LocalDateTime.of(2026, Month.APRIL, 6, 12, 0));
        job2.setStartCleaningDateTime(LocalDateTime.of(2026, Month.APRIL, 6, 11, 30));

        job2.setId(null);
        line.setJobs(Arrays.asList(job1, job2, null));

        DowntimeDataValue result = service.calculate(schedule);

        assertEquals(30, result.downtime());
        assertEquals(30, result.lines().get(line.getId()));
    }

    @Test
    void DowntimeDataValue_shouldIgnoreJobsNotInOverloadedIds() {

        schedule.setWorkCalendar(new WorkCalendar(LocalDate.of(2026, Month.APRIL, 6)));
        schedule.setOverloadedIds(Set.of("1"));

        job1.setStartCleaningDateTime(LocalDateTime.of(2026, Month.APRIL, 6, 9, 0));
        job1.setStartProductionDateTime(LocalDateTime.of(2026, Month.APRIL, 6, 10, 0));

        job2.setStartCleaningDateTime(LocalDateTime.of(2026, Month.APRIL, 6, 11, 0));
        job2.setStartProductionDateTime(LocalDateTime.of(2026, Month.APRIL, 6, 12, 0));

        DowntimeDataValue result = service.calculate(schedule);

        assertEquals(60, result.downtime());
    }

    @Test
    void DowntimeDataValue_isNegativeCleaning() {

        schedule.setWorkCalendar(new WorkCalendar(LocalDate.of(2026, Month.APRIL, 6)));
        schedule.setOverloadedIds(Set.of("1", "2"));

        job1.setStartCleaningDateTime(LocalDateTime.of(2026, Month.APRIL, 6, 9, 0));
        job1.setStartProductionDateTime(LocalDateTime.of(2026, Month.APRIL, 6, 10, 0));

        job2.setStartCleaningDateTime(LocalDateTime.of(2026, Month.APRIL, 6, 12, 0));
        job2.setStartProductionDateTime(LocalDateTime.of(2026, Month.APRIL, 6, 11, 0));

        DowntimeDataValue result = service.calculate(schedule);

        assertEquals(60, result.downtime());
    }

    @Test
    void DowntimeDataValue_startProductionIsNull_shouldReturnZero() {

        schedule.setWorkCalendar(new WorkCalendar(LocalDate.of(2026, Month.APRIL, 6)));
        schedule.setOverloadedIds(Set.of("1"));

        job1.setStartCleaningDateTime(null);
        job1.setStartProductionDateTime(LocalDateTime.of(2026, Month.APRIL, 6, 10, 0));

        job2.setStartCleaningDateTime(LocalDateTime.of(2026, Month.APRIL, 6, 11, 0));
        job2.setStartProductionDateTime(null);

        DowntimeDataValue result = service.calculate(schedule);

        assertEquals(0, result.downtime());
    }

    @Test
    void DowntimeDataValue_emptyOverloadedIds_shouldReturnZero() {

        schedule.setWorkCalendar(new WorkCalendar(LocalDate.of(2026, Month.APRIL, 6)));
        schedule.setOverloadedIds(Set.of());

        DowntimeDataValue result = service.calculate(schedule);
        assertEquals(0, result.downtime());
        assertTrue(result.lines().isEmpty());
    }

    @Test
    void DowntimeDataValue_solutionIsnNull_shouldReturnZero() {

        schedule = null;
        DowntimeDataValue result = service.calculate(schedule);
        assertEquals(0, result.downtime());
        assertTrue(result.lines().isEmpty());
    }

    @Test
    void DowntimeDataValue_WorkCalendarIsNull_shouldReturnZero() {

        DowntimeDataValue result = service.calculate(schedule);
        assertEquals(0, result.downtime());
        assertTrue(result.lines().isEmpty());
    }

    @Test
    void DowntimeDataValue_LinesIsNull_shouldReturnZero() {

        schedule.setLines(null);
        DowntimeDataValue  result = service.calculate(schedule);
        assertEquals(0, result.downtime());
        assertTrue(result.lines().isEmpty());
    }

    @Test
    void DowntimeDataValue_lineIsnull_shouldReturnZero() {

        schedule.setWorkCalendar(new WorkCalendar(LocalDate.of(2026, Month.APRIL, 6)));
        schedule.setOverloadedIds(Set.of("1"));
        schedule.setLines(Arrays.asList(line, null));

        DowntimeDataValue result = service.calculate(schedule);
        assertEquals(0, result.downtime());
        assertEquals(1, result.lines().size());
    }

    @Test
    void DowntimeDataValue_PlanningDateIsNull_shouldReturnZero() {

        schedule.setWorkCalendar(new WorkCalendar(LocalDate.of(2026, Month.APRIL, 6)));
        schedule.setOverloadedIds(Set.of("1"));

        schedule.getWorkCalendar().setPlanningDate(null);
        DowntimeDataValue result = service.calculate(schedule);
        assertEquals(0, result.downtime());
        assertTrue(result.lines().isEmpty());
    }

    @Test
    void DowntimeDataValue_lineJobsIsNull_shouldReturnZero() {

        schedule.setWorkCalendar(new WorkCalendar(LocalDate.of(2026, Month.APRIL, 6)));
        schedule.setOverloadedIds(Set.of("1"));
        Line line2 = new Line("line2", "Line 2");
        schedule.setLines(Arrays.asList(line, line2));

        DowntimeDataValue result = service.calculate(schedule);
        assertEquals(0, result.downtime());
        assertEquals(2, result.lines().size());
    }
}
