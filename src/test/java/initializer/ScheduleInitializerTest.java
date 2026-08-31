package initializer;

import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.dto.bdvzpmc.JobRow;
import org.acme.foodpackaging.initializer.ScheduleInitializer;
import org.acme.foodpackaging.record.InitData;
import org.acme.foodpackaging.service.align.AlignSolutionService;
import org.acme.foodpackaging.service.jobs.JobService;
import org.acme.foodpackaging.service.lines.LineService;
import org.acme.foodpackaging.service.products.ProductService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduleInitializerTest {

    @InjectMocks
    ScheduleInitializer scheduleInitializer;

    @Mock
    JobService jobService;
    @Mock
    LineService lineService;
    @Mock
    ProductService productService;
    @Mock
    AlignSolutionService alignSolutionService;

    @Test
    void buildSchedule() {
        LocalDate date = LocalDate.of(2025, 12, 24);

        List<Line> lines = List.of(new Line(), new Line());
        List<Product> products = List.of(new Product("VAN", "Vanilla"));
        List<JobRow> jobRows = List.of();

        when(lineService.getLines()).thenReturn(lines);
        when(jobService.buildJobsOnLines(any())).thenReturn(jobRows);

        doAnswer(invocation -> {
            PackagingSchedule schedule = invocation.getArgument(0);
            schedule.setProducts(products);
            return null;
        }).when(productService).buildProducts(any());

        doNothing().when(alignSolutionService).align(any());

        InitData initData = scheduleInitializer.initSchedule(date);

        PackagingSchedule schedule = initData.schedule();

        assertEquals(lines, schedule.getLines());
        assertEquals(products, schedule.getProducts());
        assertEquals(date, schedule.getWorkCalendar().getFromDate());
        assertEquals(jobRows, initData.jobsFromDbRow());

        verify(jobService).buildJobsOnLines(schedule);
        verify(productService).buildProducts(schedule);
        verify(alignSolutionService).align(schedule);
    }

    @Test
    void updateProductList() {
        PackagingSchedule schedule = new PackagingSchedule();
        List<Product> newProducts = List.of(new Product("VAN", "Vanilla"));

        when(productService.getProductList(schedule)).thenReturn(newProducts);

        PackagingSchedule updated = scheduleInitializer.updateProductList(schedule);

        assertSame(schedule, updated);
        assertEquals(newProducts, schedule.getProducts());

        verify(productService).getProductList(schedule);
    }
}

