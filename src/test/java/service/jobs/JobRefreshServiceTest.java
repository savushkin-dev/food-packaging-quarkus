package service.jobs;

import org.acme.foodpackaging.domain.*;
import org.acme.foodpackaging.persistence.upload.UploadDataService;
import org.acme.foodpackaging.record.CameraValue;
import org.acme.foodpackaging.record.SelectionValue;
import org.acme.foodpackaging.repository.jobs.JobRepository;
import org.acme.foodpackaging.service.products.ProductService;
import org.acme.foodpackaging.service.jobs.JobRefreshService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobRefreshServiceTest {

    @InjectMocks
    JobRefreshService service;

    @Mock
    JobRepository jobRepository;
    @Mock
    ProductService productService;
    @Mock
    UploadDataService uploadDataService;

    private PackagingSchedule solution;

    @BeforeEach
    void setUp() {
        solution = new PackagingSchedule();
        solution.setJobs(new ArrayList<>());
        solution.setAllJobsById(new HashMap<>());

        WorkCalendar calendar = new WorkCalendar(LocalDate.now());
        calendar.setMinStartDateTime(LocalDateTime.now().minusHours(5));
        solution.setWorkCalendar(calendar);
    }

    @Test
    void applySelection_shouldNotDuplicateJob() {

    Job job = new Job();
    job.setSnpz(1L);

    solution.getJobs().add(job);
    solution.getAllJobsById().put(1L, job);

    when(productService.getProductList(solution)).thenReturn(List.of());

    service.applySelection(
            Map.of(1L, new SelectionValue(true, false)),
            solution
    );

    assertEquals(1, solution.getJobs().size());
}

@Test
void applySelection_shouldAddJobIfSelected() {

    Job job = new Job();
    job.setSnpz(1L);
    job.setProduct(new Product());

    solution.getAllJobsById().put(1L, job);

    when(productService.getProductList(solution)).thenReturn(List.of());

    service.applySelection(
            Map.of(1L, new SelectionValue(true, true)),
            solution
    );

    assertEquals(1, solution.getJobs().size());
}

@Test
void refreshStaleCameraEndFromPmLog_shouldUpdateWhenDiffMoreThanMinute() {

    LocalDateTime now = LocalDateTime.now();

    Product product = new Product();
    product.setId("3445678901234");

    Job job = new Job();
    job.setIdBatch("B1");
    job.setProduct(product);
    job.setCameraStart(now.minusHours(1));
    job.setCameraEnd(now.minusMinutes(10));
    job.setDtv(now);

    solution.setJobs(List.of(job));

    LocalDateTime newEnd = now.minusMinutes(5);

    when(jobRepository.getCameraFactRowMap(any()))
            .thenReturn(Map.of("B1", new CameraValue(null, newEnd)));

    service.refreshStaleCameraEndFromPmLog(solution);

    assertEquals(newEnd, job.getCameraEnd());
    verify(uploadDataService).updateCameraEndInMsLog(any());
}

@Test
void refreshStaleCameraEndFromPmLog_shouldNotUpdateWhenDiffLessThanMinute() {

    LocalDateTime now = LocalDateTime.now();

    Product product = new Product();
    product.setId("3445678901234");

    Job job = new Job();
    job.setIdBatch("B1");
    job.setProduct(product);
    job.setCameraStart(now.minusHours(1));
    job.setCameraEnd(now.minusMinutes(10));

    solution.setJobs(List.of(job));

    LocalDateTime newEnd = job.getCameraEnd().plusSeconds(30);

    when(jobRepository.getCameraFactRowMap(any()))
            .thenReturn(Map.of("B1", new CameraValue(null, newEnd)));

    service.refreshStaleCameraEndFromPmLog(solution);

    assertNotEquals(newEnd, job.getCameraEnd());
    verify(uploadDataService, never()).updateCameraEndInMsLog(any());
}
}