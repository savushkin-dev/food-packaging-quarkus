package scheduleOperations.utils;

import org.acme.foodpackaging.domain.*;
import org.acme.foodpackaging.record.DbJobRow;
import org.acme.foodpackaging.scheduleOperations.utils.ScheduleUtils;
import org.acme.foodpackaging.scheduleOperations.utils.SpeedCacheUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ScheduleUtilsTest {

    private Line line;
    private Job job1, job2, job3;
    private PackagingSchedule schedule;

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
        Map<String, Map<String, Integer>> speeds = new HashMap<>();
        Map<String, Integer> productSpeeds = new HashMap<>();
        productSpeeds.put("MAINTENANCE", 1);
        productSpeeds.put("NORMAL", 2);
        speeds.put("line1", productSpeeds);
        SpeedCacheUtils.init(speeds);

        // Создание линии
        line = new Line("line1", "Line 1", "operator", LocalDateTime.now());

        // Создание задач
        job1 = new Job("1", "Job 1", normalProduct, null, null, null, null, 1, false, null, null);
        job2 = new Job("2", "Job 2", normalProduct, null, null, null, null, 1, false, null, null);
        job3 = new Job("3", "Job 3", maintenanceProduct, null, null, null, null, 1, false, null, null);

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

        assertThrows(IllegalArgumentException.class,
                () -> ScheduleUtils.findLineById(schedule, "not found"));
    }

    @Test
    void setLineStartDateTime() {
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
       
        Job jobWithLine = new Job("1", "Job 1", null, null, null, null, null, 1, false, null, null);
        Job jobWithoutLine = new Job("2", "Job 2", null, null, null, null, null, 1, false, null, null);
        Job anotherJobWithLine = new Job("3", "Job 3", null, null, null, null, null, 1, false, null, null);
        
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
        
        Job job1 = new Job("1", "Job 1", null, null, null, null, null, 1, false, null, null);
        Job job2 = new Job("2", "Job 2", null, null, null, null, null, 1, false, null, null);
        
        job1.setLine(line);
        job2.setLine(line);
        
        List<Job> jobs = new ArrayList<>(Arrays.asList(job1, job2));
        
        ScheduleUtils.removeJobsWithoutLine(jobs);
        
        assertEquals(2, jobs.size());
        assertTrue(jobs.contains(job1));
        assertTrue(jobs.contains(job2));
    }

    @Test
    void removeAllJobsWhenAllHaveNullLine() {
      
        Job job1 = new Job("1", "Job 1", null, null, null, null, null, 1, false, null, null);
        Job job2 = new Job("2", "Job 2", null, null, null, null, null, 1, false, null, null);
        
        List<Job> jobs = new ArrayList<>(Arrays.asList(job1, job2));
        
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
}
