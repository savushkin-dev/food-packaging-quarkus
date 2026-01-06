package persistence.load;

import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.entity.lines.PlrLines;
import org.acme.foodpackaging.persistence.load.LoadDataService;
import org.acme.foodpackaging.record.CleaningRule;
import org.acme.foodpackaging.repository.lines.LineRepository;
import org.acme.foodpackaging.repository.products.CleaningRuleRepository;
import org.acme.foodpackaging.repository.products.ProductRepository;
import org.acme.foodpackaging.scheduleOperations.utils.SpeedCacheUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("database")
class LoadDataServiceTest {

    @InjectMocks
    LoadDataService loadDataService;

    @Mock
    LineRepository lineRepository;
    @Mock
    ProductRepository productRepository;
    @Mock
    CleaningRuleRepository cleaningRuleRepository;

    @BeforeEach
    void setUp() {
        SpeedCacheUtils.init(Map.of());
    }

    @Test
    void refresh() {

        PlrLines line1 = createMockLine("L1", "Line 1", "TYPE1", 100);
        PlrLines line2 = createMockLine("L2", "Line 2", "TYPE2", 200);
        List<PlrLines> lineEntities = List.of(line1, line2);

        Product product = new Product("Prod1", "KMC1", "KRKMC1", "Type1", "Glaze1", "100", "Filling1");
        Map<String, Product> products = new HashMap<>();
        products.put("KMC1", product);
        
        List<CleaningRule> cleaningRules = List.of(
                new CleaningRule("1", "Type1", "Type2", 10)
        );

        @SuppressWarnings("unchecked")
        io.quarkus.hibernate.orm.panache.PanacheQuery<PlrLines> query = mock(io.quarkus.hibernate.orm.panache.PanacheQuery.class);
        when(lineRepository.find("fDel = 0")).thenReturn(query);
        when(query.list()).thenReturn(lineEntities);
        when(productRepository.loadProducts()).thenReturn(products);
        when(cleaningRuleRepository.loadRules()).thenReturn(cleaningRules);

        loadDataService.refresh();

        ConcurrentMap<String, String> lines = loadDataService.getLines();
        assertNotNull(lines);
        assertEquals(2, lines.size());
        assertEquals("Line 1", lines.get("L1"));
        assertEquals("Line 2", lines.get("L2"));

        Map<String, Product> loadedProducts = loadDataService.getProducts();
        assertNotNull(loadedProducts);
        assertEquals(1, loadedProducts.size());

        List<CleaningRule> loadedRules = loadDataService.getCleaningRules();
        assertNotNull(loadedRules);
        assertEquals(1, loadedRules.size());

        verify(lineRepository, atLeastOnce()).find("fDel = 0");
        verify(productRepository).loadProducts();
        verify(cleaningRuleRepository).loadRules();
    }

    @Test
    void refreshFiltersNullNames() {
        
        PlrLines lineWithName = createMockLine("L1", "Line 1", "TYPE1", 100);
        PlrLines lineWithoutName = createMockLine("L2", null, "TYPE2", 200);
        List<PlrLines> lineEntities = List.of(lineWithName, lineWithoutName);

        @SuppressWarnings("unchecked")
        io.quarkus.hibernate.orm.panache.PanacheQuery<PlrLines> query = mock(io.quarkus.hibernate.orm.panache.PanacheQuery.class);
        when(lineRepository.find("fDel = 0")).thenReturn(query);
        when(query.list()).thenReturn(lineEntities);
        when(productRepository.loadProducts()).thenReturn(new HashMap<>());
        when(cleaningRuleRepository.loadRules()).thenReturn(List.of());

        loadDataService.refresh();

        ConcurrentMap<String, String> lines = loadDataService.getLines();
        assertNotNull(lines);
        assertEquals(1, lines.size()); // Only line with name should be included
        assertTrue(lines.containsKey("L1"));
        assertFalse(lines.containsKey("L2"));
    }

    @Test
    void refreshHandlesEmptyData() {
        
        @SuppressWarnings("unchecked")
        io.quarkus.hibernate.orm.panache.PanacheQuery<PlrLines> query = mock(io.quarkus.hibernate.orm.panache.PanacheQuery.class);
        when(lineRepository.find("fDel = 0")).thenReturn(query);
        when(query.list()).thenReturn(List.of());
        when(productRepository.loadProducts()).thenReturn(new HashMap<>());
        when(cleaningRuleRepository.loadRules()).thenReturn(List.of());

        loadDataService.refresh();

        assertNotNull(loadDataService.getLines());
        assertTrue(loadDataService.getLines().isEmpty());
        assertNotNull(loadDataService.getProducts());
        assertTrue(loadDataService.getProducts().isEmpty());
        assertNotNull(loadDataService.getCleaningRules());
        assertTrue(loadDataService.getCleaningRules().isEmpty());
    }

    private PlrLines createMockLine(String lineId, String name, String type, Integer speed) {
        PlrLines line = mock(PlrLines.class);
        when(line.getLineId()).thenReturn(lineId);
        when(line.getSnm()).thenReturn(name);
        when(line.getType()).thenReturn(type);
        when(line.getSpeed()).thenReturn(speed);
        return line;
    }
}

