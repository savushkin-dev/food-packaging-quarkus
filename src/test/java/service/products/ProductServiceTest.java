package service.products;

import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.persistence.load.LoadDataService;
import org.acme.foodpackaging.service.products.CleaningCalculatorService;
import org.acme.foodpackaging.service.products.ProductService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ProductService business logic.
 * Tests are isolated with mocked dependencies.
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @InjectMocks
    ProductService productService;

    @Mock
    CleaningCalculatorService cleaningCalculator;
    @Mock
    LoadDataService loadDataService;

@Test
void getProductList_basicFlow() {

    PackagingSchedule schedule = new PackagingSchedule();

    Product maintenance = new Product("Maintenance Product", "MNT",
            null, null, null, null, null);
    schedule.setMaintenanceProduct(maintenance);

    Product product1 = new Product("Product1", "KMC1",
            null, null, null, null, null);

    Job job = new Job();
    job.setProduct(product1);

    schedule.setJobs(List.of(job));

    when(loadDataService.getCleaningRules()).thenReturn(List.of());

    List<Product> result = productService.getProductList(schedule);

    assertEquals(2, result.size());
    assertTrue(result.contains(product1));
    assertTrue(result.contains(maintenance));

    verify(cleaningCalculator).setRules(any());
    verify(cleaningCalculator).cleaningCalculate(result);
}

@Test
void getProductList_shouldRemoveDuplicates() {

    PackagingSchedule schedule = new PackagingSchedule();

    Product maintenance = new Product("Maintenance Product", "MNT",
            null, null, null, null, null);
    schedule.setMaintenanceProduct(maintenance);

    Product product = new Product("Product1", "KMC1",
            null, null, null, null, null);

    Job job1 = new Job();
    job1.setProduct(product);

    Job job2 = new Job();
    job2.setProduct(product);

    schedule.setJobs(List.of(job1, job2));

    when(loadDataService.getCleaningRules()).thenReturn(List.of());

    List<Product> result = productService.getProductList(schedule);

    assertEquals(2, result.size()); // 1 unique + maintenance
}

@Test
void getProductList_shouldAssignMaintenanceProductToMaintenanceJobs() {

    PackagingSchedule schedule = new PackagingSchedule();

    Product maintenance = new Product("Maintenance Product", "MNT",
            null, null, null, null, null);
    schedule.setMaintenanceProduct(maintenance);

    Job maintenanceJob = new Job();
    maintenanceJob.setMaintenance(true);

    schedule.setJobs(List.of(maintenanceJob));

    when(loadDataService.getCleaningRules()).thenReturn(List.of());

    List<Product> result = productService.getProductList(schedule);

    assertEquals(1, result.size());
    assertEquals(maintenance, maintenanceJob.getProduct());
}
}
