package service.builder;

import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.record.InitData;
import org.acme.foodpackaging.repository.jobs.JobRepository;
import org.acme.foodpackaging.service.builder.AlignSolutionService;
import org.acme.foodpackaging.service.jobs.JobRefreshService;
import org.acme.foodpackaging.service.jobs.JobService;
import org.acme.foodpackaging.service.products.ProductService;
import org.acme.foodpackaging.service.builder.ScheduleBuilder;
import org.acme.foodpackaging.service.lines.LineService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduleBuilderTest {

    @InjectMocks
    ScheduleBuilder builder;

    @Mock
    JobService jobService;
    @Mock
    JobRepository jobRepository;
    @Mock
    LineService lineService;
    @Mock
    ProductService productService;
    @Mock
    JobRefreshService jobRefreshService;
    @Mock
    AlignSolutionService alignSolutionService;

    @Test
    void buildSchedule() {
        LocalDate date = LocalDate.of(2025, 12, 24);

        List<Line> lines = List.of(new Line(), new Line());
        List<Product> products = List.of(new Product("VAN", "Vanilla"));
        doAnswer(invocation -> {
            PackagingSchedule sched = invocation.getArgument(0);
            sched.setJobs(new ArrayList<>());
            return null;
        }).when(jobService).initSolutionJobList(any());

        when(jobRepository.getFactProductionRowMap(any(), any())).thenReturn(Map.of());
        when(lineService.getLines()).thenReturn(lines);
        doNothing().when(lineService).initLineStartEnd(any());
        when(productService.getProductList(any())).thenReturn(products);

        InitData initData = builder.buildSchedule(date);

        PackagingSchedule schedule = initData.schedule();
        assertEquals(lines, schedule.getLines());
        assertEquals(products, schedule.getProducts());
        assertEquals(date, schedule.getWorkCalendar().getFromDate());

        verify(jobService).initSolutionJobList(schedule);
        verify(lineService).getLines();
        verify(lineService).initLineStartEnd(schedule);
        verify(productService).getProductList(schedule);

        verify(alignSolutionService).alignByFactDuration(schedule);
        verify(alignSolutionService).alignLineStartByFact(schedule);
    }

    @Test
    void updateProductList() {
        PackagingSchedule schedule = new PackagingSchedule();
        List<Product> newProducts = List.of(new Product("VAN", "Vanilla"));
        when(productService.getProductList(schedule)).thenReturn(newProducts);

        PackagingSchedule updated = builder.updateProductList(schedule);

        assertSame(schedule, updated);
        assertEquals(newProducts, schedule.getProducts());
        verify(productService).getProductList(schedule);
    }
}
