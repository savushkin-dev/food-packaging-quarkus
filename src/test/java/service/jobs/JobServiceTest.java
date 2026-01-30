package service.jobs;

import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.domain.WorkCalendar;
import org.acme.foodpackaging.dto.DbMaintenanceRow;
import org.acme.foodpackaging.persistence.load.LoadDataService;
import org.acme.foodpackaging.repository.jobs.JobRepository;
import org.acme.foodpackaging.persistence.upload.UploadDataService;
import org.acme.foodpackaging.record.CameraEventRow;
import org.acme.foodpackaging.record.DbJobRow;
import org.acme.foodpackaging.record.FactKey;
import org.acme.foodpackaging.record.FactProductionRow;
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
import java.util.List;
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
    @Mock
    JobRepository jobRepository;
    @Mock
    UploadDataService uploadDataService;

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
        DbMaintenanceRow maintenanceRow = createDbMaintenanceRow();
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
        assertEquals(123L, schedule.getJobs().getFirst().getSnpz());
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
               1L, (short) 0, "L1", now, now, 30, 123L, 1, "Maintenance Note"
        );
    }

    @Test
    void initJobFromFactProductionRow() {
        Job job = new Job();
        job.setProduct(new Product("KMC1", "VANILLA"));
        job.setNp(10);

        FactProductionRow fact = new FactProductionRow(
                "IdBatch", "KMC1",
                Timestamp.valueOf(
                        LocalDateTime.of(2025, 1, 1, 8, 0)),
                10,
                1,
                Timestamp.valueOf(
                        LocalDateTime.of(2025, 1, 1, 8, 0)),
                "LINE_1"
        );

        Map<FactKey, FactProductionRow> factMap = Map.of(
                new FactKey("KMC1", 10), fact
        );

        PackagingSchedule solution = new PackagingSchedule();
        solution.setJobs(List.of(job));

        jobService.initFactProductionData(solution, factMap);

        assertEquals("IdBatch", job.getIdBatch());
        assertEquals("LINE_1", job.getLineIdFact());
        assertEquals(
                LocalDateTime.of(2025, 1, 1, 8, 0),
                job.getStartProductionDateTimeFact()
        );
    }
    @Test
    void IgnoreJobWhenFactNotFound() {
        Job job = new Job();
        job.setProduct(new Product("KMC1", "VANILLA"));
        job.setNp(99);

        PackagingSchedule solution = new PackagingSchedule();
        solution.setJobs(List.of(job));

        jobService.initFactProductionData(solution, Map.of());

        assertNull(job.getLineIdFact());
        assertNull(job.getStartProductionDateTimeFact());
    }

    @Test
    void initCameraFromEvents_setsStartAndEndFromEvents() {
        Job job = new Job();
        job.setIdBatch("B1");
        schedule.setJobs(List.of(job));

        LocalDateTime start = LocalDateTime.of(2025, 1, 1, 9, 0);
        LocalDateTime end = LocalDateTime.of(2025, 1, 1, 10, 0);

        Map<String, CameraEventRow> startEvents = Map.of(
                "B1", new CameraEventRow("B1", 2, Timestamp.valueOf(start))
        );
        Map<String, CameraEventRow> endEvents = Map.of(
                "B1", new CameraEventRow("B1", 3, Timestamp.valueOf(end))
        );

        jobService.initCameraFromEvents(schedule, startEvents, endEvents);

        assertEquals(start, job.getCameraStart());
        assertEquals(end, job.getCameraEnd());
    }

    @Test
    void initCameraFromEvents() {
        Job job = new Job();
        job.setIdBatch("B2");
        schedule.setJobs(List.of(job));

        LocalDateTime fallbackStart = LocalDateTime.of(2025, 1, 2, 9, 0);
        LocalDateTime fallbackEnd = LocalDateTime.of(2025, 1, 2, 10, 0);

        when(jobRepository.getCameraValueByBatch("B2"))
                .thenReturn(new org.acme.foodpackaging.record.CameraValue(fallbackStart, fallbackEnd));

        jobService.initCameraFromEvents(schedule, Map.of(), Map.of());

        assertEquals(fallbackStart, job.getCameraStart());
        assertEquals(fallbackEnd, job.getCameraEnd());
    }

    @Test
    void persistMissingCameraEvents() {
        Job j1 = new Job();
        j1.setIdBatch("B1");
        j1.setCameraEnd(LocalDateTime.of(2025, 1, 1, 10, 0));

        Job j2 = new Job();
        j2.setIdBatch("B2");
        j2.setCameraStart(LocalDateTime.of(2025, 1, 1, 9, 30));
        schedule.setJobs(List.of(j1, j2));

        // Only B1 has a start event; no end events present.
        Map<String, CameraEventRow> startEvents = Map.of(
                "B1", new CameraEventRow("B1", 2, Timestamp.valueOf(LocalDateTime.of(2025,1,1,9,0)))
        );
        Map<String, CameraEventRow> endEvents = Map.of();

        jobService.persistMissingCameraEvents(schedule, startEvents, endEvents);

        // Capture batch maps
        @SuppressWarnings("unchecked")
        var startCaptor = (org.mockito.ArgumentCaptor<Map<String, LocalDateTime>>) (org.mockito.ArgumentCaptor<?>) org.mockito.ArgumentCaptor.forClass(Map.class);
        @SuppressWarnings("unchecked")
        var endCaptor = (org.mockito.ArgumentCaptor<Map<String, LocalDateTime>>) (org.mockito.ArgumentCaptor<?>) org.mockito.ArgumentCaptor.forClass(Map.class);

        verify(uploadDataService).writeCameraEventsBatch(startCaptor.capture(), endCaptor.capture());

        Map<String, LocalDateTime> writtenStart = startCaptor.getValue();
        Map<String, LocalDateTime> writtenEnd = endCaptor.getValue();

        // Expect start for B2 (since not in startEvents but has cameraStart)
        assertEquals(1, writtenStart.size());
        assertEquals(LocalDateTime.of(2025, 1, 1, 9, 30), writtenStart.get("B2"));

        // Expect end for B1 (since not in endEvents but has cameraEnd)
        assertEquals(1, writtenEnd.size());
        assertEquals(LocalDateTime.of(2025, 1, 1, 10, 0), writtenEnd.get("B1"));
    }

    @Test
    void initCameraFactData_setsStartAndEnd() {
        Job job = new Job();
        job.setIdBatch("BATCH-1");

        PackagingSchedule solution = new PackagingSchedule();
        solution.setJobs(List.of(job));

        LocalDateTime start = LocalDateTime.of(2025, 1, 1, 9, 0);
        LocalDateTime end = LocalDateTime.of(2025, 1, 1, 10, 0);
        Map<String, org.acme.foodpackaging.record.CameraValue> cameraMap = Map.of("BATCH-1", new org.acme.foodpackaging.record.CameraValue(start, end));

        jobService.initCameraFactData(solution, cameraMap);

        assertEquals(start, job.getCameraStart());
        assertEquals(end, job.getCameraEnd());
    }

    @Test
    void initCameraFactData_ignoresWhenMissing() {
        Job jobWithNullBatch = new Job();
        jobWithNullBatch.setIdBatch(null);

        Job jobWithoutEntry = new Job();
        jobWithoutEntry.setIdBatch("BATCH-2");

        PackagingSchedule solution = new PackagingSchedule();
        solution.setJobs(List.of(jobWithNullBatch, jobWithoutEntry));

        jobService.initCameraFactData(solution, Map.of());

        assertNull(jobWithNullBatch.getCameraStart());
        assertNull(jobWithNullBatch.getCameraEnd());
        assertNull(jobWithoutEntry.getCameraStart());
        assertNull(jobWithoutEntry.getCameraEnd());
    }

    @Test
    void initFromMsLogEvents_initializesFactsAndCamera_andPersistsMissing() {
        // Prepare schedule with two jobs: B1 (has events), B2 (will fallback and persist)
        Job job1 = new Job();
        job1.setProduct(new Product("KMC1", "Vanilla"));
        job1.setNp(10);

        Job job2 = new Job();
        job2.setProduct(new Product("KMC2", "Chocolate"));
        job2.setNp(20);

        schedule.setJobs(List.of(job1, job2));

        // MS_LOG events:
        // - Fact for job1 and job2 (event 1)
        // - Two start events for B1 (pick earliest)
        // - One end event for B1 (pick latest)
        LocalDateTime dtStart1 = LocalDateTime.of(2025, 1, 5, 8, 0);
        LocalDateTime dtStart1Later = LocalDateTime.of(2025, 1, 5, 8, 5);
        LocalDateTime dtEnd1 = LocalDateTime.of(2025, 1, 5, 10, 0);

        LocalDateTime fact1 = LocalDateTime.of(2025, 1, 5, 7, 55);
        LocalDateTime fact2 = LocalDateTime.of(2025, 1, 6, 9, 0);

        var events = List.of(
                // facts
                new FactProductionRow("B1", "KMC1", Timestamp.valueOf(fact1), 10, 1, Timestamp.valueOf(fact1), "LINE_1"),
                new FactProductionRow("B2", "KMC2", Timestamp.valueOf(fact2), 20, 1, Timestamp.valueOf(fact2), "LINE_2"),
                // camera start (two for B1 -> earliest kept)
                new FactProductionRow("B1", "KMC1", Timestamp.valueOf(dtStart1Later), 10, 2, null, "LINE_1"),
                new FactProductionRow("B1", "KMC1", Timestamp.valueOf(dtStart1), 10, 2, null, "LINE_1"),
                // camera end (latest kept but only one provided)
                new FactProductionRow("B1", "KMC1", Timestamp.valueOf(dtEnd1), 10, 3, null, "LINE_1")
        );

        // Fallback for B2: when missing camera events, use PM_LOG values and persist
        LocalDateTime fallbackStartB2 = LocalDateTime.of(2025, 1, 6, 9, 5);
        LocalDateTime fallbackEndB2 = LocalDateTime.of(2025, 1, 6, 11, 30);
        when(jobRepository.getCameraValueByBatch("B2"))
                .thenReturn(new org.acme.foodpackaging.record.CameraValue(fallbackStartB2, fallbackEndB2));

        // Execute
        jobService.initFromMsLogEvents(schedule, events);

        // Validate job1 facts populated
        assertEquals("B1", job1.getIdBatch());
        assertEquals("LINE_1", job1.getLineIdFact());
        assertEquals(fact1, job1.getStartProductionDateTimeFact());
        // Camera from events (earliest start, end present)
        assertEquals(dtStart1, job1.getCameraStart());
        assertEquals(dtEnd1, job1.getCameraEnd());

        // Validate job2 facts populated
        assertEquals("B2", job2.getIdBatch());
        assertEquals("LINE_2", job2.getLineIdFact());
        assertEquals(fact2, job2.getStartProductionDateTimeFact());
        // Camera from fallback
        assertEquals(fallbackStartB2, job2.getCameraStart());
        assertEquals(fallbackEndB2, job2.getCameraEnd());

        // Persist called with B2 entries (start/end), not for B1 (has events)
        @SuppressWarnings("unchecked")
        var startCaptor = (org.mockito.ArgumentCaptor<Map<String, LocalDateTime>>) (org.mockito.ArgumentCaptor<?>) org.mockito.ArgumentCaptor.forClass(Map.class);
        @SuppressWarnings("unchecked")
        var endCaptor = (org.mockito.ArgumentCaptor<Map<String, LocalDateTime>>) (org.mockito.ArgumentCaptor<?>) org.mockito.ArgumentCaptor.forClass(Map.class);

        verify(uploadDataService).writeCameraEventsBatch(startCaptor.capture(), endCaptor.capture());

        Map<String, LocalDateTime> writtenStart = startCaptor.getValue();
        Map<String, LocalDateTime> writtenEnd = endCaptor.getValue();

        assertEquals(1, writtenStart.size());
        assertEquals(fallbackStartB2, writtenStart.get("B2"));
        assertEquals(1, writtenEnd.size());
        assertEquals(fallbackEndB2, writtenEnd.get("B2"));
    }
}
