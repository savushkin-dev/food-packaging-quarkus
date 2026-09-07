
package service.jobs;

import org.acme.foodpackaging.domain.*;
import org.acme.foodpackaging.persistence.upload.UploadDataService;
import org.acme.foodpackaging.record.CameraValue;
import org.acme.foodpackaging.repository.jobs.JobRepository;
import org.acme.foodpackaging.service.jobs.JobEnrichmentService;
import org.acme.foodpackaging.service.jobs.JobInfoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;
import builder.JobTestBuilder;

import org.acme.foodpackaging.dto.MsLogInsertRow;
import org.acme.foodpackaging.exception.service.CameraDataReadException;

@ExtendWith(MockitoExtension.class)
class JobEnrichmentServiceTest {

    @InjectMocks
    JobEnrichmentService jobEnrichmentService;

    @Mock
    JobRepository jobRepository;
    @Mock
    UploadDataService uploadDataService;
    @Mock
    JobInfoService jobInfoService;

    private PackagingSchedule schedule;

    @BeforeEach
    void setUp() {
        schedule = new PackagingSchedule();
    }

    // ===== enrichCameraFactsFromPmLog =====

    @Test
    void enrichCameraFacts_doesNothing_whenNoJobsNeedCamera() {
        Job jobWithCamera = JobTestBuilder.aJob().withId("1")
                .withIdBatch("B1")
                .withCameraStart(LocalDateTime.of(2025, Month.JANUARY, 1, 8, 0))
                .withCameraEnd(LocalDateTime.of(2025, Month.JANUARY, 1, 9, 0))
                .build();

        schedule.setJobs(List.of(jobWithCamera));

        jobEnrichmentService.enrichCameraFactsFromPmLog(schedule);

        verifyNoInteractions(jobRepository, uploadDataService);
    }

    @Test
    void enrichCameraFacts_doesNothing_whenIdBatchIsNull() {
        Job jobWithoutBatch = JobTestBuilder.aJob().withId("1").withIdBatch(null).build();

        schedule.setJobs(List.of(jobWithoutBatch));

        jobEnrichmentService.enrichCameraFactsFromPmLog(schedule);

        verifyNoInteractions(jobRepository, uploadDataService);
    }

    @Test
    void enrichCameraFacts_fillsStartAndEnd_andLogsBoth() throws CameraDataReadException {
        Job job = JobTestBuilder.aJob().withId("1")
                .withProduct(new Product("1", "Chocolate"))
                .withIdBatch("B1").build();
        schedule.setJobs(List.of(job));

        LocalDateTime start = LocalDateTime.of(2025, Month.JANUARY, 1, 8, 0);
        LocalDateTime end = LocalDateTime.of(2025, Month.JANUARY, 1, 9, 0);
        CameraValue cameraValue = new CameraValue(start, end);

        when(jobRepository.getCameraFactRowMap(List.of(job))).thenReturn(Map.of("B1", cameraValue));

        jobEnrichmentService.enrichCameraFactsFromPmLog(schedule);

        assertEquals(start, job.getCameraStart());
        assertEquals(end, job.getCameraEnd());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MsLogInsertRow>> captor = ArgumentCaptor.forClass(List.class);
        verify(uploadDataService).fillMsLogTable(captor.capture());
        assertEquals(2, captor.getValue().size());
    }

    @Test
    void enrichCameraFacts_fillsOnlyMissingField_whenStartAlreadyPresent() throws CameraDataReadException {
        LocalDateTime existingStart = LocalDateTime.of(2025, Month.JANUARY, 1, 7, 0);

        Job job = JobTestBuilder.aJob().withId("1").withIdBatch("B1")
                .withCameraStart(existingStart)
                .withProduct(new Product("1", "Vanilla"))
                .build();
        schedule.setJobs(List.of(job));

        LocalDateTime end = LocalDateTime.of(2025, Month.JANUARY, 1, 9, 0);
        CameraValue cameraValue = new CameraValue(LocalDateTime.of(2025, Month.JANUARY, 1, 8, 0), end);

        when(jobRepository.getCameraFactRowMap(List.of(job))).thenReturn(Map.of("B1", cameraValue));

        jobEnrichmentService.enrichCameraFactsFromPmLog(schedule);

        assertEquals(existingStart, job.getCameraStart()); // не перезаписалось
        assertEquals(end, job.getCameraEnd());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MsLogInsertRow>> captor = ArgumentCaptor.forClass(List.class);
        verify(uploadDataService).fillMsLogTable(captor.capture());
        assertEquals(1, captor.getValue().size()); // только end-событие
    }

    @Test
    void enrichCameraFacts_skipsJob_whenCameraValueNotFound() throws CameraDataReadException {
        Job job = JobTestBuilder.aJob().withId("1").withIdBatch("B1").build();
        schedule.setJobs(List.of(job));

        when(jobRepository.getCameraFactRowMap(List.of(job))).thenReturn(Map.of());

        jobEnrichmentService.enrichCameraFactsFromPmLog(schedule);

        assertNull(job.getCameraStart());
        assertNull(job.getCameraEnd());
        verifyNoInteractions(uploadDataService);
    }

    @Test
    void enrichCameraFacts_doesNotCallUpload_whenNoRowsCollected() throws CameraDataReadException {
        Job job = JobTestBuilder.aJob().withId("1").withIdBatch("B1").build();
        schedule.setJobs(List.of(job));

        when(jobRepository.getCameraFactRowMap(List.of(job))).thenReturn(Map.of());

        jobEnrichmentService.enrichCameraFactsFromPmLog(schedule);

        verify(uploadDataService, never()).fillMsLogTable(any());
    }

    @Test
    void enrichCameraFacts_wrapsCheckedException_asRuntimeException() throws CameraDataReadException {
        Job job = JobTestBuilder.aJob().withId("1").withIdBatch("B1").build();
        schedule.setJobs(List.of(job));

        when(jobRepository.getCameraFactRowMap(List.of(job)))
                .thenThrow(new CameraDataReadException("read failed", new RuntimeException()));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> jobEnrichmentService.enrichCameraFactsFromPmLog(schedule));

        assertInstanceOf(CameraDataReadException.class, ex.getCause());
    }

    // ===== assignIdBatches =====

    @Test
    void assignIdBatches_skipsMaintenanceJobs() {
        Job maintenanceJob = JobTestBuilder.aJob().withId("1").asMaintenance().build();
        schedule.setJobs(List.of(maintenanceJob));

        jobEnrichmentService.assignIdBatches(schedule);

        verifyNoInteractions(jobInfoService);
        assertNull(maintenanceJob.getIdBatch());
    }

    @Test
    void assignIdBatches_skipsJobsWithExistingIdBatch() {
        Job job = JobTestBuilder.aJob().withId("1").withIdBatch("EXISTING").build();
        schedule.setJobs(List.of(job));

        jobEnrichmentService.assignIdBatches(schedule);

        verifyNoInteractions(jobInfoService);
        assertEquals("EXISTING", job.getIdBatch());
    }

    @Test
    void assignIdBatches_generatesIdBatch_forValidNumericId() {
        Job job = JobTestBuilder.aJob().withId("123").build();
        schedule.setJobs(List.of(job));

        when(jobInfoService.generateIdBatch(schedule, 123L)).thenReturn("GENERATED");

        jobEnrichmentService.assignIdBatches(schedule);

        assertEquals("GENERATED", job.getIdBatch());
    }

    @Test
    void assignIdBatches_skipsJob_whenIdIsNotNumeric() {
        Job job = JobTestBuilder.aJob().withId("not-a-number").build();
        schedule.setJobs(List.of(job));

        jobEnrichmentService.assignIdBatches(schedule);

        verifyNoInteractions(jobInfoService);
        assertNull(job.getIdBatch());
    }
}