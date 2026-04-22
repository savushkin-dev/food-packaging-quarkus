package service.jobs;

import org.acme.foodpackaging.domain.*;
import org.acme.foodpackaging.dto.DelayNoteRequest;
import org.acme.foodpackaging.exception.service.ProductNotFoundException;
import org.acme.foodpackaging.persistence.load.LoadDataService;
import org.acme.foodpackaging.persistence.upload.UploadDataService;
import org.acme.foodpackaging.repository.jobs.JobRepository;
import org.acme.foodpackaging.record.FactKey;
import org.acme.foodpackaging.record.FactProductionRow;
import org.acme.foodpackaging.record.DbJobRow;
import org.acme.foodpackaging.dto.DbMaintenanceRow;
import org.acme.foodpackaging.scheduleoperations.utils.SpeedCacheUtils;
import org.acme.foodpackaging.service.jobs.JobInfoService;
import org.acme.foodpackaging.service.jobs.JobService;
import org.acme.foodpackaging.record.CameraValue;
import org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils;
import org.apache.commons.lang3.tuple.Pair;
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
    @Mock
    JobInfoService jobInfoService;

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

        Map<String, Map<String, Pair<Integer, Integer>>> speeds = new HashMap<>();

        Map<String, Pair<Integer, Integer>> productSpeeds = new HashMap<>();
        productSpeeds.put("CLASSIC", Pair.of(100, 100));

        speeds.put("L1", productSpeeds);

        SpeedCacheUtils.init(speeds);

        schedule.setLines(List.of(line));
    }

    private DbJobRow createDbJobRow() {
        Timestamp start = Timestamp.valueOf(
                LocalDateTime.of(2025, 1, 15, 9, 0)
        );

        Timestamp end = Timestamp.valueOf(
                LocalDateTime.of(2025, 1, 15, 9, 34)
        );
        return new DbJobRow(
                start, "KMC1", 10, 3000, 2.0,
                start, end, 30, 123L, 1,
                "L1", "Test Job", 18, 100, 1
        );
    }

    private DbJobRow createDbJobRowWithNullId(){

            Timestamp start = Timestamp.valueOf(
                    LocalDateTime.of(2025, 1, 15, 9, 0)
            );

            Timestamp end = Timestamp.valueOf(
                    LocalDateTime.of(2025, 1, 15, 9, 34)
            );
            return new DbJobRow(
                    start, "KMC1",
                    10, 3000, 2.0,
                    start, end, 34, null, 1,
                    "L1", "Test Job", 18, 100, 1
            );
        }

    private DbMaintenanceRow createDbMaintenanceRow() {
        Timestamp now = Timestamp.valueOf(
                LocalDateTime.of(2025, 1, 15, 10, 0)
        );
    
        return new DbMaintenanceRow(
                1L, (short) 0, "L1",
                now, now, 30, 123L, 1, "Note"
        );
    }

    private DbMaintenanceRow createDelayRow() {
        Timestamp planEnd= Timestamp.valueOf(
                LocalDateTime.of(2025, 1, 15, 9, 34)
        );

        Timestamp end = Timestamp.valueOf(
                LocalDateTime.of(2025, 1, 15, 9, 40)
        );

        return new DbMaintenanceRow(
                2L, (short) 0, "L1",
                planEnd, end, 6, 123L,
                10, "Delay Note"
        );
    }

    private Product getTestProduct(){
        return new Product(
                "Product1", "KMC1", "KRKMC1",
                "CLASSIC", "Glaze1", "100", "Filling1"
        );
    }

    @Test
    void initSolutionJobList_shouldLoadJobsAndMaintenance() {

        DbJobRow jobRow = createDbJobRow();
        DbMaintenanceRow maintenanceRow = createDbMaintenanceRow();
    
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
        assertTrue(schedule.getJobs().stream()
                .anyMatch(j -> !j.isMaintenance() && j.getSnpz() == 123L));

        assertTrue(schedule.getJobs().stream()
                .anyMatch(Job::isMaintenance));
        Line line = schedule.getLines().getFirst();
        assertEquals(2, line.getJobs().size());
    }

    @Test
void initSolutionJobList_shouldSkipJobsWithoutLineId() {

    DbJobRow jobRow = createDbJobRow();
    jobRow = new DbJobRow(
            jobRow.dti(), jobRow.kmc(), jobRow.np(), jobRow.quantity(),
            jobRow.mass(), jobRow.startProductionDateTime(),
            jobRow.endDateTime(), jobRow.duration(),
            jobRow.snpz(), jobRow.priority(),
            null, jobRow.shortName(), 18, 100, 1
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
    assertEquals(0, schedule.getJobs().size());

    Line line = schedule.getLines().getFirst();
    assertTrue(line.getJobs().isEmpty());
}

    @Test
    void initSolutionJobList_shouldThrowException() {

        DbJobRow jobRow = createDbJobRow();
        jobRow = new DbJobRow(
                jobRow.dti(), "Unknown kmc", jobRow.np(), jobRow.quantity(),
                jobRow.mass(), jobRow.startProductionDateTime(),
                jobRow.endDateTime(), jobRow.duration(),
                jobRow.snpz(), jobRow.priority(),
                null, jobRow.shortName(), 18, 100, 1
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

        assertThrows(ProductNotFoundException.class, () -> jobService.initSolutionJobList(schedule));
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

    @Test
    void  initDelayDuration(){
        DbJobRow jobRow = createDbJobRow();

        DbMaintenanceRow delayData = createDelayRow();

        when(loadDataService.getProducts())
                .thenReturn(Map.of("KMC1", getTestProduct()));
        when(jobRepository.getDbJobRowMap(any(), any()))
                .thenReturn(Map.of(123L, jobRow));
        when(jobRepository.getMaintenanceData(any(), any()))
                .thenReturn(List.of());
        when(jobRepository.getDelayData(any(), any()))
                .thenReturn(Map.of(123L, delayData));

        jobService.initSolutionJobList(schedule);

        assertEquals(1, schedule.getJobs().size());
        assertEquals(delayData.getStartProductionDateTime().toLocalDateTime(), schedule.getJobs().getFirst().getPlanEndDateTime());
        assertEquals(delayData.getEndDateTime().toLocalDateTime(), schedule.getJobs().getFirst().getEndDateTime());
        assertEquals(delayData.getMaintenanceNote(), schedule.getJobs().getFirst().getDelayNote());

        assertEquals(6, schedule.getJobs().getFirst().getDelayDuration().toMinutes());
        assertEquals(34, schedule.getJobs().getFirst().getDuration().toMinutes());
    }

    @Test
    void  initDelayDuration_WhenJobIdIsNull(){
        DbJobRow jobRow = createDbJobRowWithNullId();

        DbMaintenanceRow delayData = createDelayRow();

        when(loadDataService.getProducts())
                .thenReturn(Map.of("KMC1", getTestProduct()));
        when(jobRepository.getDbJobRowMap(any(), any()))
                .thenReturn(Map.of(123L, jobRow));
        when(jobRepository.getMaintenanceData(any(), any()))
                .thenReturn(List.of());
        when(jobRepository.getDelayData(any(), any()))
                .thenReturn(Map.of(123L, delayData));

        jobService.initSolutionJobList(schedule);

        assertEquals(1, schedule.getJobs().size());
        assertEquals(jobRow.endDateTime().toLocalDateTime(), schedule.getJobs().getFirst().getEndDateTime());
        assertEquals(34, schedule.getJobs().getFirst().getDuration().toMinutes());
        assertEquals("null", schedule.getJobs().getFirst().getId());

        assertNull(schedule.getJobs().getFirst().getDelayNote());
        assertNull( schedule.getJobs().getFirst().getDelayDuration());
    }

    @Test
    void initIdBatch(){
        Job j1 = new Job();
        Job j2 = new Job();
        Job j3 = new Job();

        j1.setId("247811");
        j1.setDti(LocalDateTime.of(2026, 3, 20, 0, 0));
        j1.setProduct(new Product("P1", "vanilla"));
        j1.getProduct().setEan13("4810268050671");
        j1.setNp(346);

        j2.setIdBatch("79079078908908");
        j3.setMaintenance(true);

        schedule.setJobs(List.of(j1, j2, j3));
        schedule.setAllJobsById(Map.of(Long.parseLong(j1.getId()), j1));

        when(jobInfoService.generateIdBatch(any(), anyLong()))
                .thenCallRealMethod();

        jobService.initIdBatch(schedule);

        assertEquals("481026805067020260320000000346", schedule.getJobs().getFirst().getIdBatch());
        assertEquals("247811", schedule.getJobs().getFirst().getId());
    }
}
