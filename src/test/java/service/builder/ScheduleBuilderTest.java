package service.builder;

import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.record.DbJobRow;
import org.acme.foodpackaging.dto.DbMaintenanceRow;
import org.acme.foodpackaging.record.InitData;
import org.acme.foodpackaging.repository.jobs.JobRepository;
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

import java.sql.Timestamp;
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

    @Test
    void buildSchedule() {
        LocalDate date = LocalDate.of(2025, 12, 24);

        // MS_LOG events (combined load) - empty for this test
        java.util.List<org.acme.foodpackaging.record.FactProductionRow> msLogEvents = java.util.List.of();

        Map<Long, DbJobRow> jobRows = Map.of(
                1L,  new DbJobRow(
                        new Timestamp(System.currentTimeMillis()),
                        "KMC1", 10, 5, 2.0,
                        new Timestamp(System.currentTimeMillis()),
                         new Timestamp(System.currentTimeMillis()),
                        5, 123L, 1, null, "Vanilla", 1
                )
        );

        List<DbMaintenanceRow> maintenanceRows = List.of(
                 new DbMaintenanceRow(
                        1L, (short) 1,
                        "Line1",
                        new Timestamp(System.currentTimeMillis()),
                        new Timestamp(System.currentTimeMillis()),
                        2, 123L, 1, "Maintenance Note"
                )
        );

        List<Line> lines = List.of(new Line(), new Line());
        List<Product> products = List.of(new Product("VAN", "Vanilla"));
        doAnswer(invocation -> {
            PackagingSchedule sched = invocation.getArgument(0);
            sched.setJobs(new ArrayList<>());
            return null;
        }).when(jobService).initSolutionJobList(any());

        when(jobRepository.getDbJobRowMap(any(), any())).thenReturn(jobRows);
        when(jobRepository.getFactProductionRowMap(any(), any())).thenReturn(Map.of());
        when(lineService.getLines()).thenReturn(lines);
        doNothing().when(lineService).initLineStartEnd(any());
        when(productService.getProductList(any())).thenReturn(products);

        InitData initData = builder.buildSchedule(date);

        PackagingSchedule schedule = initData.schedule();
        assertEquals(lines, schedule.getLines());
        assertEquals(products, schedule.getProducts());
        assertEquals(date, schedule.getWorkCalendar().getFromDate());

        verify(jobRepository).getDbJobRowMap(any(), any());
        verify(jobService).initSolutionJobList(schedule);
        verify(lineService).getLines();
        verify(lineService).initLineStartEnd(schedule);
        verify(productService).getProductList(schedule);
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
