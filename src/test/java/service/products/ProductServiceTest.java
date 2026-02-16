package service.products;

import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.persistence.load.LoadDataService;
import org.acme.foodpackaging.record.CleaningRule;
import org.acme.foodpackaging.record.DbJobRow;
import org.acme.foodpackaging.service.products.CleaningCalculatorService;
import org.acme.foodpackaging.service.products.ProductService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    void getProductList() {
        PackagingSchedule schedule = new PackagingSchedule();
        schedule.setJobs(new ArrayList<>());
        
        DbJobRow jobRow1 = new DbJobRow(
                new Timestamp(System.currentTimeMillis()),
                "KMC1", 10, 5, 2.0,
                new Timestamp(System.currentTimeMillis()),
                new Timestamp(System.currentTimeMillis()),
                5, 123L, 1, "L1", "Product1", 18
        );
        DbJobRow jobRow2 = new DbJobRow(
                new Timestamp(System.currentTimeMillis()),
                "KMC2", 20, 10, 3.0,
                new Timestamp(System.currentTimeMillis()),
                new Timestamp(System.currentTimeMillis()),
                10, 124L, 2, "L2", "Product2", 19
        );
        schedule.setDbJobRowMap(Map.of(123L, jobRow1, 124L, jobRow2));

        Product product1 = new Product("Product1", "KMC1", "KRKMC1", "Type1", "Glaze1", "100", "Filling1");
        Product product2 = new Product("Product2", "KMC2", "KRKMC2", "Type2", "Glaze2", "200", "Filling2");
        Map<String, Product> products = Map.of("KMC1", product1, "KMC2", product2);
        List<CleaningRule> cleaningRules = List.of();

        when(loadDataService.getProducts()).thenReturn(products);
        when(loadDataService.getCleaningRules()).thenReturn(cleaningRules);
        doNothing().when(cleaningCalculator).setRules(any());
        doNothing().when(cleaningCalculator).cleaningCalculate(any());

        List<Product> result = productService.getProductList(schedule);

        assertNotNull(result);
        assertEquals(3, result.size(), "Should contain 2 products + 1 maintenance product");
        assertTrue(result.contains(product1));
        assertTrue(result.contains(product2));
        assertTrue(result.stream().anyMatch(p -> p.getName().equals("Maintenance Product")),
                "Maintenance product should be included");

        verify(loadDataService).getProducts();
        verify(loadDataService).getCleaningRules();
        verify(cleaningCalculator).setRules(cleaningRules);
        verify(cleaningCalculator).cleaningCalculate(any());
    }

    @Test
    void getProductListWithMaintenanceJobs() {
        PackagingSchedule schedule = new PackagingSchedule();
        
        Job maintenanceJob = new Job();
        maintenanceJob.setMaintenance(true);
        schedule.setJobs(List.of(maintenanceJob));

        DbJobRow jobRow = new DbJobRow(
                new Timestamp(System.currentTimeMillis()),
                "KMC1", 10, 5, 2.0,
                new Timestamp(System.currentTimeMillis()),
                new Timestamp(System.currentTimeMillis()),
                5, 123L, 1, "L1", "Product1", 18
        );
        schedule.setDbJobRowMap(Map.of(123L, jobRow));

        Product product = new Product("Product1", "KMC1", "KRKMC1", "Type1", "Glaze1", "100", "Filling1");
        Map<String, Product> products = Map.of("KMC1", product);

        when(loadDataService.getProducts()).thenReturn(products);
        when(loadDataService.getCleaningRules()).thenReturn(List.of());
        doNothing().when(cleaningCalculator).setRules(any());
        doNothing().when(cleaningCalculator).cleaningCalculate(any());

        List<Product> result = productService.getProductList(schedule);

        assertNotNull(result);
        assertNotNull(maintenanceJob.getProduct(), "Maintenance job should have product set");
        assertEquals("Maintenance Product", maintenanceJob.getProduct().getName());
    }

    @Test
    void getProductListThrowsWhenProductNotFound() {
        PackagingSchedule schedule = new PackagingSchedule();
        schedule.setJobs(new ArrayList<>());

        DbJobRow jobRow = new DbJobRow(
                new Timestamp(System.currentTimeMillis()),
                "UNKNOWN_KMC", 10, 5, 2.0,
                new Timestamp(System.currentTimeMillis()),
                new Timestamp(System.currentTimeMillis()),
                5, 123L, 1, "L1", "Product1", 18
        );
        schedule.setDbJobRowMap(Map.of(123L, jobRow));

        when(loadDataService.getProducts()).thenReturn(Map.of());

        IllegalStateException exception = assertThrows(IllegalStateException.class, 
                () -> productService.getProductList(schedule));
        
        assertTrue(exception.getMessage().contains("Product not found for KMC: UNKNOWN_KMC"));
    }

    @Test
    void getProductListWithDuplicateKMCs() {
        PackagingSchedule schedule = new PackagingSchedule();
        schedule.setJobs(new ArrayList<>());

        DbJobRow jobRow1 = new DbJobRow(
                new Timestamp(System.currentTimeMillis()),
                "KMC1", 10, 5, 2.0,
                new Timestamp(System.currentTimeMillis()),
                new Timestamp(System.currentTimeMillis()),
                5, 123L, 1, "L1", "Product1", 18
        );
        DbJobRow jobRow2 = new DbJobRow(
                new Timestamp(System.currentTimeMillis()),
                "KMC1", 20, 10, 3.0,
                new Timestamp(System.currentTimeMillis()),
                new Timestamp(System.currentTimeMillis()),
                10, 124L, 2, "L2", "Product1", 19
        );
        schedule.setDbJobRowMap(Map.of(123L, jobRow1, 124L, jobRow2));

        Product product = new Product("Product1", "KMC1", "KRKMC1", "Type1", "Glaze1", "100", "Filling1");
        Map<String, Product> products = Map.of("KMC1", product);

        when(loadDataService.getProducts()).thenReturn(products);
        when(loadDataService.getCleaningRules()).thenReturn(List.of());
        doNothing().when(cleaningCalculator).setRules(any());
        doNothing().when(cleaningCalculator).cleaningCalculate(any());

        List<Product> result = productService.getProductList(schedule);

        assertNotNull(result);
        assertEquals(2, result.size(), "Should have only one unique product (KMC1) + maintenance product");
        assertTrue(result.contains(product));
    }

    @Test
    void getProductListFiltersNullKMCs() {
        PackagingSchedule schedule = new PackagingSchedule();
        schedule.setJobs(new ArrayList<>());

        DbJobRow jobRow1 = new DbJobRow(
                new Timestamp(System.currentTimeMillis()),
                "KMC1", 10, 5, 2.0,
                new Timestamp(System.currentTimeMillis()),
                new Timestamp(System.currentTimeMillis()),
                5, 123L, 1, "L1", "Product1", 18
        );
        DbJobRow jobRow2 = new DbJobRow(
                new Timestamp(System.currentTimeMillis()),
                null, 20, 10, 3.0, // null KMC should be filtered
                new Timestamp(System.currentTimeMillis()),
                new Timestamp(System.currentTimeMillis()),
                10, 124L, 2, "L2", "Product2", 19
        );
        schedule.setDbJobRowMap(Map.of(123L, jobRow1, 124L, jobRow2));

        Product product = new Product("Product1", "KMC1", "KRKMC1", "Type1", "Glaze1", "100", "Filling1");
        Map<String, Product> products = Map.of("KMC1", product);

        when(loadDataService.getProducts()).thenReturn(products);
        when(loadDataService.getCleaningRules()).thenReturn(List.of());
        doNothing().when(cleaningCalculator).setRules(any());
        doNothing().when(cleaningCalculator).cleaningCalculate(any());

        List<Product> result = productService.getProductList(schedule);

        assertNotNull(result);
        assertEquals(2, result.size(), "Should have 1 product + 1 maintenance product (null KMC filtered)");
        assertTrue(result.contains(product));
    }

    @Test
    void getProductListWithEmptySchedule() {
        PackagingSchedule schedule = new PackagingSchedule();
        schedule.setJobs(new ArrayList<>());
        schedule.setDbJobRowMap(Map.of());

        when(loadDataService.getProducts()).thenReturn(Map.of());
        when(loadDataService.getCleaningRules()).thenReturn(List.of());
        doNothing().when(cleaningCalculator).setRules(any());
        doNothing().when(cleaningCalculator).cleaningCalculate(any());

        List<Product> result = productService.getProductList(schedule);

        assertNotNull(result);
        assertEquals(1, result.size(), "Should only have maintenance product when schedule is empty");
        assertTrue(result.stream().anyMatch(p -> p.getName().equals("Maintenance Product")));
        
        verify(cleaningCalculator).cleaningCalculate(any());
    }
}
