package service;

import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.record.DbJobRow;
import org.acme.foodpackaging.repository.jobs.JobRepository;
import org.acme.foodpackaging.service.products.ProductService;
import org.acme.foodpackaging.service.jobs.JobRefreshService;
import org.acme.foodpackaging.service.jobs.JobService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
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

        Map<Long, Boolean> selection = Map.of(1L, true);

        service.applySelection(selection, solution);

        assertEquals(1, solution.getJobs().size());
        assertSame(job, solution.getJobs().getFirst());
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

        service.applySelection(Map.of(1L, true), solution);

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

        service.applySelection(Map.of(1L, false), solution);

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
}

