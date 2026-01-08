package service.jobs;

import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.domain.WorkCalendar;
import org.acme.foodpackaging.dto.DbMaintenanceRow;
import org.acme.foodpackaging.persistence.load.LoadDataService;
import org.acme.foodpackaging.record.DbJobRow;
import org.acme.foodpackaging.service.jobs.JobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JobService business logic.
 * Tests are isolated with mocked dependencies.
 */
@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @InjectMocks
    JobService jobService;

    @Mock
    LoadDataService loadDataService;

    private PackagingSchedule schedule;

    @BeforeEach
    void setUp() {
        WorkCalendar workCalendar = new WorkCalendar(LocalDate.of(2025, 1, 15));
        workCalendar.setMinStartDateTime(LocalDateTime.of(2025, 1, 15, 8, 0));
        
        schedule = new PackagingSchedule();
        schedule.setWorkCalendar(workCalendar);
        schedule.setDbJobRowMap(new HashMap<>());
        schedule.setDbMaintenanceRowMap(new HashMap<>());
        schedule.setJobIdMap(new HashMap<>());
        schedule.setJobs(new ArrayList<>());
    }

    @Test
    void initSolutionJobList() {
        DbJobRow jobRow1 = createDbJobRow("KMC1", 123L);
        jobRow1 = new DbJobRow(
                jobRow1.dti(), jobRow1.kmc(), jobRow1.np(), jobRow1.quantity(),
                jobRow1.mass(), jobRow1.startProductionDateTime(), jobRow1.endDateTime(),
                jobRow1.duration(), jobRow1.snpz(), jobRow1.priority(), "L1", jobRow1.shortName()
        );
        
        DbJobRow jobRow2 = createDbJobRow("KMC2", 124L);
        jobRow2 = new DbJobRow(
                jobRow2.dti(), jobRow2.kmc(), jobRow2.np(), jobRow2.quantity(),
                jobRow2.mass(), jobRow2.startProductionDateTime(), jobRow2.endDateTime(),
                jobRow2.duration(), jobRow2.snpz(), jobRow2.priority(), null, jobRow2.shortName()
        );

        DbMaintenanceRow maintenanceRow = createDbMaintenanceRow();

        schedule.setDbJobRowMap(Map.of(123L, jobRow1, 124L, jobRow2));
        schedule.setDbMaintenanceRowMap(Map.of(1L, maintenanceRow));

        Product product1 = new Product("Product1", "KMC1", "KRKMC1", "Type1", "Glaze1", "100", "Filling1");
        Product product2 = new Product("Product2", "KMC2", "KRKMC2", "Type2", "Glaze2", "200", "Filling2");
        when(loadDataService.getProducts()).thenReturn(Map.of("KMC1", product1, "KMC2", product2));

        jobService.initSolutionJobList(schedule);

        assertNotNull(schedule.getJobs());
        assertEquals(2, schedule.getJobs().size(), "Only jobs with lineId should be included");
        assertTrue(schedule.getJobs().stream().anyMatch(j -> j.getSnpz() == 123L));
        assertTrue(schedule.getJobs().stream().anyMatch(Job::isMaintenance));
    }

    @Test
    void createJobByIdForRegularJob() {
        DbJobRow jobRow = createDbJobRow("KMC1", 123L);
        jobRow = new DbJobRow(
                jobRow.dti(), jobRow.kmc(), jobRow.np(), jobRow.quantity(),
                jobRow.mass(), jobRow.startProductionDateTime(), jobRow.endDateTime(),
                jobRow.duration(), jobRow.snpz(), jobRow.priority(), "L1", jobRow.shortName()
        );
        schedule.setDbJobRowMap(Map.of(123L, jobRow));

        Product product = new Product("Product1", "KMC1", "KRKMC1", "Type1", "Glaze1", "100", "Filling1");
        when(loadDataService.getProducts()).thenReturn(Map.of("KMC1", product));

        Job job = jobService.createJobById(123L, false, schedule);

        assertNotNull(job);
        assertEquals("123", job.getId());
        assertEquals(123L, job.getSnpz());
        assertEquals("L1", job.getLineId());
        assertEquals(product, job.getProduct());
        assertFalse(job.isMaintenance());
        assertTrue(schedule.getJobIdMap().containsKey(123L), "Job should be cached in jobIdMap");
    }

    @Test
    void createJobByIdForMaintenanceJob() {
        DbMaintenanceRow maintenanceRow = createDbMaintenanceRow(1L, "L1");
        schedule.setDbMaintenanceRowMap(Map.of(1L, maintenanceRow));

        Job job = jobService.createJobById(1L, true, schedule);

        assertNotNull(job);
        assertEquals("1", job.getId());
        assertEquals(1L, job.getFId());
        assertEquals("L1", job.getLineId());
        assertTrue(job.isMaintenance());
        assertNotNull(job.getProduct());
        assertEquals("MAINTENANCE", job.getProduct().getId());
    }

    @Test
    void createJobByIdThrowsWhenJobNotFound() {
        schedule.setDbJobRowMap(Map.of());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> jobService.createJobById(999L, false, schedule));
        
        assertTrue(exception.getMessage().contains("Unknown SNPZ=999"));
    }

    @Test
    void createJobByIdThrowsWhenMaintenanceJobNotFound() {
        schedule.setDbMaintenanceRowMap(Map.of());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> jobService.createJobById(999L, true, schedule));
        
        assertTrue(exception.getMessage().contains("Unknown maintenance job FId=999"));
    }

    @Test
    void createJobByIdThrowsWhenProductNotFound() {
        DbJobRow jobRow = createDbJobRow("UNKNOWN_KMC", 123L);
        jobRow = new DbJobRow(
                jobRow.dti(), jobRow.kmc(), jobRow.np(), jobRow.quantity(),
                jobRow.mass(), jobRow.startProductionDateTime(), jobRow.endDateTime(),
                jobRow.duration(), jobRow.snpz(), jobRow.priority(), "L1", jobRow.shortName()
        );
        schedule.setDbJobRowMap(Map.of(123L, jobRow));

        when(loadDataService.getProducts()).thenReturn(Map.of());

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> jobService.createJobById(123L, false, schedule));
        
        assertTrue(exception.getMessage().contains("Unknown product KMC=UNKNOWN_KMC"));
    }

    @Test
    void createJobByIdReturnsExistingJob() {
        Job existingJob = new Job();
        existingJob.setId("123");
        existingJob.setSnpz(123L);
        schedule.getJobIdMap().put(123L, existingJob);

        Job result = jobService.createJobById(123L, false, schedule);

        assertSame(existingJob, result, "Should return existing job from cache");
        verify(loadDataService, never()).getProducts();
    }

    @Test
    void getStartProductionDateTime() {
        Timestamp timestamp = Timestamp.valueOf(LocalDateTime.of(2025, 1, 15, 10, 30));

        LocalDateTime result = jobService.getStartProductionDateTime(timestamp);

        assertEquals(LocalDateTime.of(2025, 1, 15, 10, 30), result);
    }

    @Test
    void getStartProductionDateTimeWithNull() {
        LocalDateTime result = jobService.getStartProductionDateTime(null);

        assertNull(result);
    }

    @Test
    void initSolutionJobListFiltersNullLineIds() {
        DbJobRow jobRow1 = createDbJobRow("KMC1", 123L);
        jobRow1 = new DbJobRow(
                jobRow1.dti(), jobRow1.kmc(), jobRow1.np(), jobRow1.quantity(),
                jobRow1.mass(), jobRow1.startProductionDateTime(), jobRow1.endDateTime(),
                jobRow1.duration(), jobRow1.snpz(), jobRow1.priority(), "L1", jobRow1.shortName()
        );
        
        DbJobRow jobRow2 = createDbJobRow("KMC2", 124L);
        jobRow2 = new DbJobRow(
                jobRow2.dti(), jobRow2.kmc(), jobRow2.np(), jobRow2.quantity(),
                jobRow2.mass(), jobRow2.startProductionDateTime(), jobRow2.endDateTime(),
                jobRow2.duration(), jobRow2.snpz(), jobRow2.priority(), null, jobRow2.shortName()
        );

        schedule.setDbJobRowMap(Map.of(123L, jobRow1, 124L, jobRow2));
        schedule.setDbMaintenanceRowMap(Map.of());

        Product product1 = new Product("Product1", "KMC1", "KRKMC1", "Type1", "Glaze1", "100", "Filling1");
        when(loadDataService.getProducts()).thenReturn(Map.of("KMC1", product1));

        jobService.initSolutionJobList(schedule);

        assertEquals(1, schedule.getJobs().size(), "Should filter out jobs with null lineId");
        assertEquals(123L, schedule.getJobs().get(0).getSnpz());
    }

    private DbJobRow createDbJobRow(String kmc, Long snpz) {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        return new DbJobRow(
                now, kmc, 10, 5, 2.0,
                now, now, 5, snpz, 1, "L1", "Product"
        );
    }

    private DbMaintenanceRow createDbMaintenanceRow() {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        return new DbMaintenanceRow(
                1L, (short) 0, "L1", now, now, 30, 123L, "Maintenance"
        );
    }
}
