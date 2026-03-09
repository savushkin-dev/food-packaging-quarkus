package service.jobs;

import org.acme.foodpackaging.domain.*;
import org.acme.foodpackaging.dto.DelayNoteRequest;
import org.acme.foodpackaging.persistence.load.LoadDataService;
import org.acme.foodpackaging.persistence.upload.UploadDataService;
import org.acme.foodpackaging.repository.jobs.JobRepository;
import org.acme.foodpackaging.record.FactKey;
import org.acme.foodpackaging.record.FactProductionRow;
import org.acme.foodpackaging.record.DbJobRow;
import org.acme.foodpackaging.dto.DbMaintenanceRow;
import org.acme.foodpackaging.service.jobs.JobService;
import org.acme.foodpackaging.record.CameraValue;
import org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
        schedule.setAllJobsById(new HashMap<>());
        schedule.setJobs(new ArrayList<>());

        Line line = new Line();
        line.setId("L1");
        line.setJobs(new ArrayList<>());

        schedule.setLines(List.of(line));
    }

    private DbJobRow createDbJobRow() {
        Timestamp now = Timestamp.valueOf(
                LocalDateTime.of(2025, 1, 15, 9, 0)
        );
    
        return new DbJobRow(
                now,              // dti
                "KMC1",              // kmc
                10,               // np
                5,                // quantity
                2.0,              // mass
                now,              // startProductionDateTime
                now,              // endDateTime
                60,               // duration (minutes)
                123L,             // snpz
                1,                // priority
                "L1",           // lineId
                "Test Job",       // shortName
                18,                // emk
                100                //placePlan
        );
    }
    
    private DbMaintenanceRow createDbMaintenanceRow(String lineId) {
        Timestamp now = Timestamp.valueOf(
                LocalDateTime.of(2025, 1, 15, 10, 0)
        );
    
        return new DbMaintenanceRow(
                1L,          // fId
                (short) 0,   // fDel
                lineId,      // lineId
                now,         // start
                now,         // end
                30,          // duration
                123L,        // snpz
                1,           // maintenanceTypeId
                "Note"       // maintenanceNote
        );
    }
    
    @Test
    void initSolutionJobList_shouldLoadJobsAndMaintenance() {
    
        // --- given ---
    
        DbJobRow jobRow = createDbJobRow();
    
        DbMaintenanceRow maintenanceRow = createDbMaintenanceRow("L1");
    
        Product product = new Product(
                "Product1", "KMC1", "KRKMC1",
                "Type1", "Glaze1", "100", "Filling1"
        );
    
        
        when(loadDataService.getProducts())
                .thenReturn(Map.of("KMC1", product));
    
        when(jobRepository.getDbJobRowMap(any(), any()))
                .thenReturn(Map.of(123L, jobRow));
    
        when(jobRepository.getMaintenanceData(any(), any()))
                .thenReturn(List.of(maintenanceRow));
    
        when(loadDataService.getMaintenanceTypes())
                .thenReturn(new ConcurrentHashMap<>(Map.of(1, "Обслуживание")));
    
        schedule.setMaintenanceProduct(product);
    
        jobService.initSolutionJobList(schedule);
    
    
        assertEquals(2, schedule.getJobs().size());
        // production job
        assertTrue(schedule.getJobs().stream()
                .anyMatch(j -> !j.isMaintenance() && j.getSnpz() == 123L));
        // maintenance job
        assertTrue(schedule.getJobs().stream()
                .anyMatch(Job::isMaintenance));
        // attached to line
        Line line = schedule.getLines().getFirst();
        assertEquals(2, line.getJobs().size());
    }
    @Test
void initSolutionJobList_shouldSkipJobsWithoutLineId() {

    DbJobRow jobRow = createDbJobRow();

    // принудительно делаем lineId = null
    jobRow = new DbJobRow(
            jobRow.dti(), jobRow.kmc(), jobRow.np(), jobRow.quantity(),
            jobRow.mass(), jobRow.startProductionDateTime(),
            jobRow.endDateTime(), jobRow.duration(),
            jobRow.snpz(), jobRow.priority(),
            null, jobRow.shortName(), 18, 100
    );

    Product product = new Product(
            "Product1", "KMC1", "KRKMC1",
            "Type1", "Glaze1", "100", "Filling1"
    );

    when(loadDataService.getProducts())
            .thenReturn(Map.of("KMC1", product));

    when(jobRepository.getDbJobRowMap(any(), any()))
            .thenReturn(Map.of(123L, jobRow));

    when(jobRepository.getMaintenanceData(any(), any()))
            .thenReturn(Collections.emptyList());

    jobService.initSolutionJobList(schedule);

    // production job создан, но к линии не прикреплён
    assertEquals(1, schedule.getJobs().size());

    Line line = schedule.getLines().getFirst();
    assertTrue(line.getJobs().isEmpty());
}

@Test
void initSolutionJobList_shouldSetMinStartTime() {

    DbJobRow jobRow = createDbJobRow();

    Product product = new Product(
            "Product1", "KMC1", "KRKMC1",
            "Type1", "Glaze1", "100", "Filling1"
    );

    when(loadDataService.getProducts())
            .thenReturn(Map.of("KMC1", product));

    when(jobRepository.getDbJobRowMap(any(), any()))
            .thenReturn(Map.of(123L, jobRow));

    when(jobRepository.getMaintenanceData(any(), any()))
            .thenReturn(Collections.emptyList());

    jobService.initSolutionJobList(schedule);

    Job job = schedule.getJobs().getFirst();

    assertEquals(
            schedule.getWorkCalendar().getMinStartDateTime(),
            job.getMinStartTime()
    );
}
    // --- initFactProductionData tests ---

    @Test
    void initFactProductionData_setsStartFactAndCameraData() {
        Job job = new Job();
        job.setProduct(new Product("KMC1", "Vanilla"));
        job.setNp(10);

        FactProductionRow startFact = new FactProductionRow(
                "BATCH-1", "KMC1",
                Timestamp.valueOf(LocalDateTime.of(2025, 1, 1, 8, 0)),
                10, ScheduleUtils.START_FACT_EVENT_TYPE,
                Timestamp.valueOf(LocalDateTime.of(2025, 1, 1, 8, 0)),
                "LINE_1"
        );
        FactProductionRow startCamera = new FactProductionRow(
                "BATCH-1", "KMC1", null, 10, ScheduleUtils.START_CAMERA_EVENT_TYPE,
                Timestamp.valueOf(LocalDateTime.of(2025, 1, 1, 9, 0)),
                "LINE_1"
        );
        FactProductionRow endCamera = new FactProductionRow(
                "BATCH-1", "KMC1", null, 10, ScheduleUtils.END_CAMERA_EVENT_TYPE,
                Timestamp.valueOf(LocalDateTime.of(2025, 1, 1, 10, 0)),
                "LINE_1"
        );

        Map<FactKey, FactProductionRow> factMap = Map.of(
                new FactKey("KMC1", 10, ScheduleUtils.START_FACT_EVENT_TYPE), startFact,
                new FactKey("KMC1", 10, ScheduleUtils.START_CAMERA_EVENT_TYPE), startCamera,
                new FactKey("KMC1", 10, ScheduleUtils.END_CAMERA_EVENT_TYPE), endCamera
        );

        PackagingSchedule solution = new PackagingSchedule();
        solution.setJobs(List.of(job));

        jobService.initFactProductionData(solution, factMap);

        assertEquals("BATCH-1", job.getIdBatch());
        assertEquals("LINE_1", job.getLineIdFact());
        assertEquals(LocalDateTime.of(2025, 1, 1, 8, 0), job.getDtv());
        assertEquals(LocalDateTime.of(2025, 1, 1, 8, 0), job.getStartProductionDateTimeFact());
        assertEquals(LocalDateTime.of(2025, 1, 1, 9, 0), job.getCameraStart());
        assertEquals(LocalDateTime.of(2025, 1, 1, 10, 0), job.getCameraEnd());
    }

    @Test
    void initFactProductionData_skipsJobWithNullProduct() {
        Job jobWithProduct = new Job();
        jobWithProduct.setProduct(new Product("KMC1", "Vanilla"));
        jobWithProduct.setNp(10);

        Job jobWithoutProduct = new Job();
        jobWithoutProduct.setNp(20);

        Map<FactKey, FactProductionRow> factMap = Map.of(
                new FactKey("KMC1", 10, ScheduleUtils.START_FACT_EVENT_TYPE),
                new FactProductionRow("BATCH-1", "KMC1",
                        Timestamp.valueOf(LocalDateTime.of(2025, 1, 1, 8, 0)),
                        10, 1, Timestamp.valueOf(LocalDateTime.of(2025, 1, 1, 8, 0)), "LINE_1")
        );

        PackagingSchedule solution = new PackagingSchedule();
        solution.setJobs(List.of(jobWithProduct, jobWithoutProduct));

        jobService.initFactProductionData(solution, factMap);

        assertEquals("BATCH-1", jobWithProduct.getIdBatch());
        assertNull(jobWithoutProduct.getIdBatch());
    }

    @Test
    void initFactProductionData_ignoresWhenFactNotFound() {
        Job job = new Job();
        job.setProduct(new Product("KMC1", "Vanilla"));
        job.setNp(99);

        PackagingSchedule solution = new PackagingSchedule();
        solution.setJobs(List.of(job));

        jobService.initFactProductionData(solution, Map.of());

        assertNull(job.getIdBatch());
        assertNull(job.getLineIdFact());
        assertNull(job.getStartProductionDateTimeFact());
        assertNull(job.getCameraStart());
        assertNull(job.getCameraEnd());
    }

    // --- enrichCameraFactsFromPmLog tests ---

    @Test
    void enrichCameraFactsFromPmLog_setsStartAndEndAndCallsFillMsLogTable() {
        Job job = new Job();
        job.setIdBatch("BATCH-1");
        job.setProduct(new Product("KMC1", "Product1"));
        job.setDtv(LocalDateTime.of(2025, 1, 1, 8, 0));

        PackagingSchedule solution = new PackagingSchedule();
        solution.setJobs(List.of(job));

        LocalDateTime start = LocalDateTime.of(2025, 1, 1, 9, 0);
        LocalDateTime end = LocalDateTime.of(2025, 1, 1, 10, 0);
        Map<String, CameraValue> cameraMap = Map.of("BATCH-1", new CameraValue(start, end));

        when(jobRepository.getCameraFactRowMap(any())).thenReturn(cameraMap);

        jobService.enrichCameraFactsFromPmLog(solution);

        assertEquals(start, job.getCameraStart());
        assertEquals(end, job.getCameraEnd());
        verify(uploadDataService).fillMsLogTable(argThat(list -> list.size() == 2));
    }

    @Test
    void enrichCameraFactsFromPmLog_ignoresWhenCameraMapEmpty() {
        Job job = new Job();
        job.setIdBatch("BATCH-1");

        PackagingSchedule solution = new PackagingSchedule();
        solution.setJobs(List.of(job));

        when(jobRepository.getCameraFactRowMap(any())).thenReturn(Map.of());

        jobService.enrichCameraFactsFromPmLog(solution);

        assertNull(job.getCameraStart());
        assertNull(job.getCameraEnd());
        verify(uploadDataService, never()).fillMsLogTable(any());
    }

    @Test
    void enrichCameraFactsFromPmLog_returnsEarlyWhenNoJobsNeedCamera() {
        Job jobWithFullCamera = new Job();
        jobWithFullCamera.setIdBatch("BATCH-1");
        jobWithFullCamera.setCameraStart(LocalDateTime.of(2025, 1, 1, 9, 0));
        jobWithFullCamera.setCameraEnd(LocalDateTime.of(2025, 1, 1, 10, 0));

        Job jobWithNullBatch = new Job();
        jobWithNullBatch.setIdBatch(null);

        PackagingSchedule solution = new PackagingSchedule();
        solution.setJobs(List.of(jobWithFullCamera, jobWithNullBatch));

        jobService.enrichCameraFactsFromPmLog(solution);

        verify(jobRepository, never()).getCameraFactRowMap(any());
        verify(uploadDataService, never()).fillMsLogTable(any());
    }

    @Test
    void enrichCameraFactsFromPmLog_fillsOnlyMissingCameraStart() {
        Job job = new Job();
        job.setIdBatch("BATCH-1");
        job.setProduct(new Product("KMC1", "Product1"));
        job.setDtv(LocalDateTime.of(2025, 1, 1, 8, 0));
        job.setCameraEnd(LocalDateTime.of(2025, 1, 1, 10, 0));

        PackagingSchedule solution = new PackagingSchedule();
        solution.setJobs(List.of(job));

        LocalDateTime start = LocalDateTime.of(2025, 1, 1, 9, 0);
        Map<String, CameraValue> cameraMap = Map.of("BATCH-1", new CameraValue(start, null));

        when(jobRepository.getCameraFactRowMap(any())).thenReturn(cameraMap);

        jobService.enrichCameraFactsFromPmLog(solution);

        assertEquals(start, job.getCameraStart());
        assertEquals(LocalDateTime.of(2025, 1, 1, 10, 0), job.getCameraEnd());
        verify(uploadDataService).fillMsLogTable(argThat(list -> list.size() == 1));
    }

    @Test
    void enrichCameraFactsFromPmLog_fillsOnlyMissingCameraEnd() {
        Job job = new Job();
        job.setIdBatch("BATCH-1");
        job.setProduct(new Product("KMC1", "Product1"));
        job.setDtv(LocalDateTime.of(2025, 1, 1, 8, 0));
        job.setCameraStart(LocalDateTime.of(2025, 1, 1, 9, 0));

        PackagingSchedule solution = new PackagingSchedule();
        solution.setJobs(List.of(job));

        LocalDateTime end = LocalDateTime.of(2025, 1, 1, 10, 0);
        Map<String, CameraValue> cameraMap = Map.of("BATCH-1", new CameraValue(null, end));

        when(jobRepository.getCameraFactRowMap(any())).thenReturn(cameraMap);

        jobService.enrichCameraFactsFromPmLog(solution);

        assertEquals(LocalDateTime.of(2025, 1, 1, 9, 0), job.getCameraStart());
        assertEquals(end, job.getCameraEnd());
        verify(uploadDataService).fillMsLogTable(argThat(list -> list.size() == 1));
    }

    @Test
    void writeDelayNote(){
        PackagingSchedule solution = new PackagingSchedule();
        Job j1 = new Job();
        Line line1 = new Line("L1", "line1");
        j1.setLine(line1);
        line1.setJobs(List.of(j1));
        solution.setLines(List.of(line1));
        solution.setJobs(List.of(j1));

        DelayNoteRequest request = new DelayNoteRequest();
        request.setLineId("L1");
        request.setIndex(0);
        request.setDelayNote("Note for testing");

        jobService.writeDelayNote(request, solution);

        assertEquals(request.getDelayNote(), solution.getJobs().getFirst().getDelayNote());
    }

    @Test
    void writeDelayNote_whenLineNotFound(){
        PackagingSchedule solution = new PackagingSchedule();
        Line line2 = new Line("L2", "line1");
        solution.setLines(List.of(line2));
        solution.setJobs(List.of(new Job()));

        DelayNoteRequest request = new DelayNoteRequest();
        request.setLineId("L1");
        request.setIndex(0);
        request.setDelayNote("Note for testing");

        jobService.writeDelayNote(request, solution);

        assertNull(solution.getJobs().getFirst().getDelayNote());
    }

    @Test
    void writeDelayNote_whenLineJobsListNull(){
        PackagingSchedule solution = new PackagingSchedule();

        Job j1 = new Job();
        Line line1 = new Line("L1", "line1");
        line1.setJobs(null);
        solution.setLines(List.of(line1));
        solution.setJobs(List.of(j1));

        DelayNoteRequest request = new DelayNoteRequest();
        request.setLineId("L1");
        request.setIndex(0);
        request.setDelayNote("Note for testing");

        jobService.writeDelayNote(request, solution);

        assertNull(solution.getJobs().getFirst().getDelayNote());
    }

    @Test
    void writeDelayNote_whenLineJobsListIsEmpty(){
        PackagingSchedule solution = new PackagingSchedule();

        Job j1 = new Job();
        Line line1 = new Line("L1", "line1");
        line1.setJobs(new ArrayList<>());
        solution.setLines(List.of(line1));
        solution.setJobs(List.of(j1));

        DelayNoteRequest request = new DelayNoteRequest();
        request.setLineId("L1");
        request.setIndex(0);
        request.setDelayNote("Note for testing");

        jobService.writeDelayNote(request, solution);

        assertNull(solution.getJobs().getFirst().getDelayNote());
    }
}
