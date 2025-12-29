package service.builder;

import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.record.DbJobRow;
import org.acme.foodpackaging.record.DbMaintenanceRow;
import org.acme.foodpackaging.repository.jobs.JobRepository;
import org.acme.foodpackaging.repository.lines.LineRepository;
import org.acme.foodpackaging.repository.products.ProductRepository;
import org.acme.foodpackaging.service.builder.ScheduleBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
    JobRepository jobRepository;
    @Mock
    LineRepository lineRepository;
    @Mock
    ProductRepository productRepository;

    @Test
    void buildSchedule() {
        LocalDate date = LocalDate.of(2025, 12, 24);

        Map<Integer, DbJobRow> jobRows = Map.of(
                1, new DbJobRow(
                        new Timestamp(System.currentTimeMillis()),
                        "KMC1", 10, 5, 2.0,
                        new Timestamp(System.currentTimeMillis()),
                        new Timestamp(System.currentTimeMillis()),
                        5, new BigDecimal("123"),
                        1, null, "Vanilla"
                )
        );

        Map<Integer, DbMaintenanceRow> maintenanceRows = Map.of(
                1, new DbMaintenanceRow(
                        1,
                        "Line1",
                        new Timestamp(System.currentTimeMillis()),
                        new Timestamp(System.currentTimeMillis()),
                        2,
                        new BigDecimal("123"),
                        "Vanilla"
                )
        );

        List<Line> lines = List.of(new Line(), new Line());
        List<Product> products = List.of(new Product("VAN", "Vanilla"));
        doAnswer(invocation -> {
            PackagingSchedule sched = invocation.getArgument(0);
            sched.setJobs(new ArrayList<>());
            return null;
        }).when(jobRepository).initSolutionJobList(any());

        when(jobRepository.getDbJobRowMap(any(), any())).thenReturn(jobRows);
        when(jobRepository.getDbMaintenanceRowMap(any(), any())).thenReturn(maintenanceRows);
        when(lineRepository.getLines()).thenReturn(lines);
        doNothing().when(lineRepository).initJobListOnLine(any());
        when(productRepository.getProductList(any())).thenReturn(products);

        PackagingSchedule schedule = builder.buildSchedule(date);

        assertEquals(jobRows, schedule.getDbJobRowMap());
        assertEquals(maintenanceRows, schedule.getDbMaintenanceRowMap());
        assertEquals(lines, schedule.getLines());
        assertEquals(products, schedule.getProducts());
        assertEquals(date, schedule.getWorkCalendar().getFromDate());

        verify(jobRepository).getDbJobRowMap(any(), any());
        verify(jobRepository).getDbMaintenanceRowMap(any(), any());
        verify(jobRepository).initSolutionJobList(schedule);
        verify(lineRepository).getLines();
        verify(lineRepository).initJobListOnLine(schedule);
        verify(productRepository).getProductList(schedule);
    }

@Test
void updateProductList() {
    PackagingSchedule schedule = new PackagingSchedule();
    List<Product> newProducts = List.of(new Product("VAN", "Vanilla"));
    when(productRepository.getProductList(schedule)).thenReturn(newProducts);

    PackagingSchedule updated = builder.updateProductList(schedule);

    assertSame(schedule, updated);
    assertEquals(newProducts, schedule.getProducts());
    verify(productRepository).getProductList(schedule);
}
}
