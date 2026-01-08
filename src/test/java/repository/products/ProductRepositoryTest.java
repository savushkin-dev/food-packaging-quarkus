package repository.products;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.persistence.load.LoadDataService;
import org.acme.foodpackaging.record.CleaningRule;
import org.acme.foodpackaging.record.DbJobRow;
import org.acme.foodpackaging.record.ProductRow;
import org.acme.foodpackaging.repository.products.ProductRepository;
import org.acme.foodpackaging.service.products.CleaningCalculatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("database")
class ProductRepositoryTest {

    @InjectMocks
    ProductRepository productRepository;

    @Mock
    CleaningCalculatorService cleaningCalculator;
    @Mock
    LoadDataService loadDataService;
    @Mock
    EntityManager entityManager;
    @Mock
    TypedQuery<ProductRow> typedQuery;

    @BeforeEach
    void setUp() {
        lenient().when(entityManager.createQuery(anyString(), eq(ProductRow.class))).thenReturn(typedQuery);
    }

    @Test
    void loadProducts() {
       
        ProductRow row1 = new ProductRow("KMC1", "EAN1", "Type1", "Glaze1", "100", "Filling1", "Product1", "KRKMC1");
        ProductRow row2 = new ProductRow("KMC2", "EAN2", "Type2", "Glaze2", "200", "Filling2", "Product2", "KRKMC2");
        List<ProductRow> rows = List.of(row1, row2);
        when(typedQuery.getResultList()).thenReturn(rows);

        Map<String, Product> result = productRepository.loadProducts();

        
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.containsKey("KMC1"));
        assertTrue(result.containsKey("KMC2"));

        Product product1 = result.get("KMC1");
        assertEquals("Product1", product1.getName());
        assertEquals("KMC1", product1.getId());
        assertEquals("KRKMC1", product1.getKrKmc());
        assertEquals("Type1", product1.getType());
        assertEquals("Glaze1", product1.getGlaze());
        assertEquals("Filling1", product1.getFilling());

        verify(entityManager).createQuery(anyString(), eq(ProductRow.class));
        verify(typedQuery).getResultList();
    }

    @Test
    void loadProductsEmptyResult() {
    
        when(typedQuery.getResultList()).thenReturn(List.of());

        Map<String, Product> result = productRepository.loadProducts();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getProductList() {
      
        PackagingSchedule schedule = new PackagingSchedule();
        schedule.setJobs(new ArrayList<>());
        
        DbJobRow jobRow1 = new DbJobRow(
                new Timestamp(System.currentTimeMillis()),
                "KMC1", 10, 5, 2.0,
                new Timestamp(System.currentTimeMillis()),
                new Timestamp(System.currentTimeMillis()),
                5, 123L, 1, "L1", "Product1"
        );
        DbJobRow jobRow2 = new DbJobRow(
                new Timestamp(System.currentTimeMillis()),
                "KMC2", 20, 10, 3.0,
                new Timestamp(System.currentTimeMillis()),
                new Timestamp(System.currentTimeMillis()),
                10, 124L, 2, "L2", "Product2"
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

        List<Product> result = productRepository.getProductList(schedule);

        assertNotNull(result);
        assertEquals(3, result.size()); // 2 products + 1 maintenance product
        assertTrue(result.contains(product1));
        assertTrue(result.contains(product2));
        // Maintenance product should be included
        assertTrue(result.stream().anyMatch(p -> p.getName().equals("Maintenance Product")));

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
                5, 123L, 1, "L1", "Product1"
        );
        schedule.setDbJobRowMap(Map.of(123L, jobRow));

        Product product = new Product("Product1", "KMC1", "KRKMC1", "Type1", "Glaze1", "100", "Filling1");
        Map<String, Product> products = Map.of("KMC1", product);

        when(loadDataService.getProducts()).thenReturn(products);
        when(loadDataService.getCleaningRules()).thenReturn(List.of());
        doNothing().when(cleaningCalculator).setRules(any());
        doNothing().when(cleaningCalculator).cleaningCalculate(any());

        List<Product> result = productRepository.getProductList(schedule);

        assertNotNull(result);
        assertNotNull(maintenanceJob.getProduct());
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
                5, 123L, 1, "L1", "Product1"
        );
        schedule.setDbJobRowMap(Map.of(123L, jobRow));

        when(loadDataService.getProducts()).thenReturn(Map.of());

        IllegalStateException exception = assertThrows(IllegalStateException.class, 
                () -> productRepository.getProductList(schedule));
        
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
                5, 123L, 1, "L1", "Product1"
        );
        DbJobRow jobRow2 = new DbJobRow(
                new Timestamp(System.currentTimeMillis()),
                "KMC1", 20, 10, 3.0,
                new Timestamp(System.currentTimeMillis()),
                new Timestamp(System.currentTimeMillis()),
                10, 124L, 2, "L2", "Product1"
        );
        schedule.setDbJobRowMap(Map.of(123L, jobRow1, 124L, jobRow2));

        Product product = new Product("Product1", "KMC1", "KRKMC1", "Type1", "Glaze1", "100", "Filling1");
        Map<String, Product> products = Map.of("KMC1", product);

        when(loadDataService.getProducts()).thenReturn(products);
        when(loadDataService.getCleaningRules()).thenReturn(List.of());
        doNothing().when(cleaningCalculator).setRules(any());
        doNothing().when(cleaningCalculator).cleaningCalculate(any());

        List<Product> result = productRepository.getProductList(schedule);

        assertNotNull(result);
        // Should have only one unique product (KMC1) + maintenance product = 2 total
        assertEquals(2, result.size());
        assertTrue(result.contains(product));
    }
}

