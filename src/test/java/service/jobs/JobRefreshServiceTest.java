package service.jobs;

import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.persistence.upload.UploadDataService;
import org.acme.foodpackaging.record.CameraValue;
import org.acme.foodpackaging.record.DbJobRow;
import org.acme.foodpackaging.record.SelectionValue;
import org.acme.foodpackaging.repository.jobs.JobRepository;
import org.acme.foodpackaging.service.products.ProductService;
import org.acme.foodpackaging.service.jobs.JobRefreshService;
import org.acme.foodpackaging.service.jobs.JobService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobRefreshServiceTest {

    @InjectMocks
    JobRefreshService service;

    @Mock
    JobRepository jobRepository;
    @Mock
    JobService jobService;
    @Mock
    ProductService productService;
    @Mock
    UploadDataService uploadDataService;

    @Test
    void enabledJobNotPresent() {
        PackagingSchedule solution = new PackagingSchedule();
        solution.setJobs(new ArrayList<>());
        solution.setJobIdMap(new HashMap<>());

        DbJobRow row = mock(DbJobRow.class);
        when(row.snpz()).thenReturn(Long.valueOf(1));
        solution.setDbJobRowMap(Map.of(1L, row));

        Job job = new Job();
        job.setSnpz(1L);

        when(jobService.createJobById(1L, false, solution))
                .thenReturn(job);
        when(productService.getProductList(solution))
                .thenReturn(List.of());

        Map<Long, SelectionValue> selection = Map.of(1L, new SelectionValue(true, true));

        service.applySelection(selection, solution);

        assertEquals(1, solution.getJobs().size());
        assertSame(job, solution.getJobs().getFirst());
        assertTrue( solution.getJobs().getFirst().isHandPackaging());
        assertEquals(job, solution.getJobIdMap().get(1L));

        verify(jobService).createJobById(1L, false, solution);
        verify(productService).getProductList(solution);
    }

    @Test
    void enabledJobsAlreadyPresent() {
        Job job = new Job();
        job.setSnpz(1L);

        PackagingSchedule solution = new PackagingSchedule();
        solution.setJobs(new ArrayList<>(List.of(job)));
        solution.setJobIdMap(new HashMap<>(Map.of(1L, job)));

        when(productService.getProductList(solution))
                .thenReturn(List.of());

        service.applySelection(Map.of(1L, new SelectionValue(true, true)), solution);

        assertEquals(1, solution.getJobs().size());
        verify(jobService, never()).createJobById(anyLong(), anyBoolean(), any());
    }

    @Test
    void removeDisabledJobs() {
        Line line = new Line();
        line.setJobs(new ArrayList<>());
        line.setFirstUnpinnedIndex(10);

        Job job = new Job();
        job.setSnpz(1L);
        job.setLine(line);

        line.getJobs().add(job);

        PackagingSchedule solution = new PackagingSchedule();
        solution.setJobs(new ArrayList<>(List.of(job)));
        solution.setJobIdMap(new HashMap<>(Map.of(1L, job)));

        when(productService.getProductList(solution))
                .thenReturn(List.of());

        service.applySelection(Map.of(1L, new SelectionValue(false, false)), solution);

        assertTrue(solution.getJobs().isEmpty());
        assertTrue(line.getJobs().isEmpty());
        assertNull(job.getLine());
        assertEquals(0, line.getFirstUnpinnedIndex());
    }

    @Test
    void rebuildJobIdMap() {
        Job job1 = new Job(); job1.setSnpz(1L);
        Job job2 = new Job(); job2.setSnpz(2L);

        PackagingSchedule solution = new PackagingSchedule();
        solution.setJobs(new ArrayList<>(List.of(job1, job2)));
        solution.setJobIdMap(new HashMap<>());

        when(productService.getProductList(solution))
                .thenReturn(List.of());

        service.applySelection(Map.of(), solution);

        assertEquals(2, solution.getJobIdMap().size());
        assertSame(job1, solution.getJobIdMap().get(1L));
        assertSame(job2, solution.getJobIdMap().get(2L));
    }

    @Test
    void enabledButDbJobRowMissing() {
        PackagingSchedule solution = new PackagingSchedule();
        solution.setJobs(new ArrayList<>());
        solution.setJobIdMap(new HashMap<>());
        solution.setDbJobRowMap(new HashMap<>());
        when(productService.getProductList(solution)).thenReturn(List.of());

        service.applySelection(Map.of(1L, new SelectionValue(true, true)), solution);

        assertEquals(0, solution.getJobs().size());
        assertNull(solution.getJobIdMap().get(1L));
        verify(jobService, never()).createJobById(anyLong(), anyBoolean(), any());
        verify(productService).getProductList(solution);
    }

    @Test
    void disabledJobWithoutLine() {
        Job job = new Job();
        job.setSnpz(1L);
        job.setLine(null);

        PackagingSchedule solution = new PackagingSchedule();
        solution.setJobs(new ArrayList<>(List.of(job)));
        solution.setJobIdMap(new HashMap<>(Map.of(1L, job)));

        when(productService.getProductList(solution)).thenReturn(List.of());

        service.applySelection(Map.of(1L, new SelectionValue(false, false)), solution);

        assertTrue(solution.getJobs().isEmpty());
        assertNull(solution.getJobIdMap().get(1L));
        verify(productService).getProductList(solution);
    }

    @Test
    void rebuildIdExcludesMaintenanceJobs() {
        Job regularJob = new Job();
        regularJob.setSnpz(1L);
        regularJob.setMaintenance(false);
        Job maintenanceJob = new Job();
        maintenanceJob.setSnpz(2L);
        maintenanceJob.setMaintenance(true);

        PackagingSchedule solution = new PackagingSchedule();
        solution.setJobs(new ArrayList<>(List.of(regularJob, maintenanceJob)));
        solution.setJobIdMap(new HashMap<>(Map.of(1L, regularJob, 2L, maintenanceJob)));

        when(productService.getProductList(solution)).thenReturn(List.of());

        service.applySelection(Map.of(), solution);

        assertEquals(1, solution.getJobIdMap().size());
        assertSame(regularJob, solution.getJobIdMap().get(1L));
        assertNull(solution.getJobIdMap().get(2L));
    }

    @Test
    void selection_addOneRemoveAnother() {
        DbJobRow row1 = mock(DbJobRow.class);
        when(row1.snpz()).thenReturn(1L);

        Job job1 = new Job();
        job1.setSnpz(1L);
        Job job2 = new Job();
        job2.setSnpz(2L);

        PackagingSchedule solution = new PackagingSchedule();
        solution.setJobs(new ArrayList<>(List.of(job2)));
        solution.setJobIdMap(new HashMap<>(Map.of(2L, job2)));
        solution.setDbJobRowMap(new HashMap<>(Map.of(1L, row1)));

        when(jobService.createJobById(1L, false, solution)).thenReturn(job1);
        when(productService.getProductList(solution)).thenReturn(List.of());

        Map<Long, SelectionValue> selection = Map.of(1L, new SelectionValue(true, true), 2L, new SelectionValue(false, false));
        PackagingSchedule result = service.applySelection(selection, solution);

        assertSame(solution, result);
        assertEquals(1, solution.getJobs().size());
        assertSame(job1, solution.getJobs().getFirst());
        assertSame(job1, solution.getJobIdMap().get(1L));
        assertNull(solution.getJobIdMap().get(2L));
        verify(jobService).createJobById(1L, false, solution);
        verify(productService).getProductList(solution);
    }

    @Test
    void removeDisabledJob() {
        Line line = new Line();
        line.setJobs(new ArrayList<>());
        line.setFirstUnpinnedIndex(5);

        Job job = new Job();
        job.setSnpz(1L);
        job.setLine(line);
        line.getJobs().add(job);

        PackagingSchedule solution = new PackagingSchedule();
        solution.setJobs(new ArrayList<>(List.of(job)));
        solution.setJobIdMap(new HashMap<>(Map.of(1L, job)));

        when(productService.getProductList(solution)).thenReturn(List.of());

        service.applySelection(Map.of(1L, new SelectionValue(false, false)), solution);

        assertTrue(solution.getJobs().isEmpty());
        assertTrue(line.getJobs().isEmpty());
        assertEquals(0, line.getFirstUnpinnedIndex());
    }

    // --- refreshStaleCameraEndFromPmLog tests ---

    @Test
    void refreshStaleCameraEndFromPmLog_updatesWhenEndDiffersMoreThanOneMinute() {
        Job job = new Job();
        job.setIdBatch("BATCH-1");
        job.setProduct(new Product("KMC1", "Product1"));
        job.setDtv(LocalDateTime.now().minusHours(2));
        job.setCameraStart(LocalDateTime.now().minusHours(1));
        job.setCameraEnd(LocalDateTime.now().minusHours(1).plusMinutes(5));

        PackagingSchedule solution = new PackagingSchedule();
        solution.setJobs(List.of(job));

        LocalDateTime newEnd = LocalDateTime.now().minusHours(1).plusMinutes(10);
        Map<String, CameraValue> cameraMap = Map.of("BATCH-1", new CameraValue(null, newEnd));

        when(jobRepository.getCameraFactRowMap(any())).thenReturn(cameraMap);

        service.refreshStaleCameraEndFromPmLog(solution);

        assertEquals(newEnd, job.getCameraEnd());
        verify(uploadDataService).updateCameraEndInMsLog(argThat(list -> list.size() == 1));
    }

    @Test
    void refreshStaleCameraEndFromPmLog_skipsWhenDiffLessThanOneMinute() {
        LocalDateTime cameraStart = LocalDateTime.now().minusHours(1);
        LocalDateTime oldEnd = cameraStart.plusMinutes(10);

        Job job = new Job();
        job.setIdBatch("BATCH-1");
        job.setProduct(new Product("KMC1", "Product1"));
        job.setDtv(LocalDateTime.now().minusHours(2));
        job.setCameraStart(cameraStart);
        job.setCameraEnd(oldEnd);

        PackagingSchedule solution = new PackagingSchedule();
        solution.setJobs(List.of(job));

        LocalDateTime newEnd = oldEnd.plusSeconds(30);
        Map<String, CameraValue> cameraMap = Map.of("BATCH-1", new CameraValue(null, newEnd));

        when(jobRepository.getCameraFactRowMap(any())).thenReturn(cameraMap);

        service.refreshStaleCameraEndFromPmLog(solution);

        assertEquals(oldEnd, job.getCameraEnd());
        verify(uploadDataService, never()).updateCameraEndInMsLog(any());
    }

    @Test
    void refreshStaleCameraEndFromPmLog_returnsEarlyWhenNoStaleJobs() {
        PackagingSchedule solution = new PackagingSchedule();
        solution.setJobs(List.of());

        service.refreshStaleCameraEndFromPmLog(solution);

        verify(jobRepository, never()).getCameraFactRowMap(any());
        verify(uploadDataService, never()).updateCameraEndInMsLog(any());
    }

    @Test
    void refreshStaleCameraEndFromPmLog_skipsWhenIdBatchNull() {
        Job job = new Job();
        job.setIdBatch(null);
        job.setCameraStart(LocalDateTime.now().minusHours(1));

        PackagingSchedule solution = new PackagingSchedule();
        solution.setJobs(List.of(job));

        service.refreshStaleCameraEndFromPmLog(solution);

        verify(jobRepository, never()).getCameraFactRowMap(any());
    }

    @Test
    void refreshStaleCameraEndFromPmLog_skipsWhenCameraStartNull() {
        Job job = new Job();
        job.setIdBatch("BATCH-1");
        job.setCameraStart(null);

        PackagingSchedule solution = new PackagingSchedule();
        solution.setJobs(List.of(job));

        service.refreshStaleCameraEndFromPmLog(solution);

        verify(jobRepository, never()).getCameraFactRowMap(any());
    }

    @Test
    void refreshStaleCameraEndFromPmLog_skipsWhenCameraMapHasNoEntry() {
        LocalDateTime cameraStart = LocalDateTime.now().minusHours(1);
        LocalDateTime cameraEnd = cameraStart.plusMinutes(5);

        Job job = new Job();
        job.setIdBatch("BATCH-1");
        job.setProduct(new Product("KMC1", "Product1"));
        job.setDtv(cameraStart.minusHours(1));
        job.setCameraStart(cameraStart);
        job.setCameraEnd(cameraEnd);

        PackagingSchedule solution = new PackagingSchedule();
        solution.setJobs(List.of(job));

        when(jobRepository.getCameraFactRowMap(any())).thenReturn(Map.of());

        service.refreshStaleCameraEndFromPmLog(solution);

        assertEquals(cameraEnd, job.getCameraEnd());
        verify(uploadDataService, never()).updateCameraEndInMsLog(any());
    }

    @Test
    void refreshStaleCameraEndFromPmLog_skipsWhenCameraEndNull() {
        LocalDateTime cameraStart = LocalDateTime.now().minusHours(1);
        LocalDateTime cameraEnd = cameraStart.plusMinutes(5);

        Job job = new Job();
        job.setIdBatch("BATCH-1");
        job.setProduct(new Product("KMC1", "Product1"));
        job.setDtv(cameraStart.minusHours(1));
        job.setCameraStart(cameraStart);
        job.setCameraEnd(cameraEnd);

        PackagingSchedule solution = new PackagingSchedule();
        solution.setJobs(List.of(job));

        Map<String, CameraValue> cameraMap = Map.of("BATCH-1", new CameraValue(null, null));

        when(jobRepository.getCameraFactRowMap(any())).thenReturn(cameraMap);

        service.refreshStaleCameraEndFromPmLog(solution);

        assertEquals(cameraEnd, job.getCameraEnd());
        verify(uploadDataService, never()).updateCameraEndInMsLog(any());
    }
}

