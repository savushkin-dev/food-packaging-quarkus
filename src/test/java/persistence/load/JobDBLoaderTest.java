package persistence.load;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.acme.foodpackaging.persistence.load.JobDBLoader;
import org.acme.foodpackaging.record.FactKey;
import org.acme.foodpackaging.record.FactProductionRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobDBLoaderTest {

    @Mock
    EntityManager em;
    @Mock
    Query query;

    @InjectMocks
    JobDBLoader jobDBLoader;

    private LocalDateTime from;
    private LocalDateTime to;

    @BeforeEach
    void setUp() {
        from = LocalDateTime.of(2025, 1, 15, 8, 0);
        to = LocalDateTime.of(2025, 1, 16, 8, 0);
    }

    @Test
    void loadFactProductionRowsWithUniqueKeys() {
       
        FactProductionRow row1 = new FactProductionRow(
                "KMC1", 
                Timestamp.valueOf(from.plusHours(1)), 
                1, 
                1,
                Timestamp.valueOf(from.plusHours(1)),
                "L1"
        );
        FactProductionRow row2 = new FactProductionRow(
                "KMC2", 
                Timestamp.valueOf(from.plusHours(2)), 
                2, 
                1,
                Timestamp.valueOf(from.plusHours(2)),
                "L2"
        );
        List<FactProductionRow> rows = List.of(row1, row2);

        when(em.createNativeQuery(anyString(), eq("FactProductionRowMapping"))).thenReturn(query);
        when(query.setParameter(eq(1), any(Timestamp.class))).thenReturn(query);
        when(query.setParameter(eq(2), any(Timestamp.class))).thenReturn(query);
        when(query.getResultList()).thenReturn(rows);

        Map<FactKey, FactProductionRow> result = jobDBLoader.loadFactProductionRowMap(from, to);

        assertNotNull(result);
        assertEquals(2, result.size());
        
        FactKey key1 = new FactKey("KMC1", 1);
        FactKey key2 = new FactKey("KMC2", 2);
        
        assertTrue(result.containsKey(key1));
        assertTrue(result.containsKey(key2));
        assertEquals(row1, result.get(key1));
        assertEquals(row2, result.get(key2));

        verify(em).createNativeQuery(anyString(), eq("FactProductionRowMapping"));
        verify(query).setParameter(eq(1), any(Timestamp.class));
        verify(query).setParameter(eq(2), any(Timestamp.class));
        verify(query).getResultList();
    }

    @Test
    void loadFactProductionRowsWithDuplicateKeys() {
        
        FactProductionRow row1 = new FactProductionRow(
                "KMC1", 
                Timestamp.valueOf(from.plusHours(1)), 
                1, 
                1,
                Timestamp.valueOf(from.plusHours(1)),
                "L1"
        );
        FactProductionRow row2 = new FactProductionRow(
                "KMC1", 
                Timestamp.valueOf(from.plusHours(2)), 
                1, 
                1,
                Timestamp.valueOf(from.plusHours(2)),
                "L2"
        );
        FactProductionRow row3 = new FactProductionRow(
                "KMC1", 
                Timestamp.valueOf(from.plusHours(3)), 
                1, 
                1,
                Timestamp.valueOf(from.plusHours(3)),
                "L3"
        );
       
        List<FactProductionRow> rows = List.of(row1, row2, row3);

        when(em.createNativeQuery(anyString(), eq("FactProductionRowMapping"))).thenReturn(query);
        when(query.setParameter(eq(1), any(Timestamp.class))).thenReturn(query);
        when(query.setParameter(eq(2), any(Timestamp.class))).thenReturn(query);
        when(query.getResultList()).thenReturn(rows);

        Map<FactKey, FactProductionRow> result = jobDBLoader.loadFactProductionRowMap(from, to);

        assertNotNull(result);
        assertEquals(1, result.size(), "Should have only one entry for duplicate keys");
        
        FactKey key = new FactKey("KMC1", 1);
        assertTrue(result.containsKey(key));
        assertEquals(row1, result.get(key), "First occurrence should be kept");
        assertNotEquals(row2, result.get(key), "Second occurrence should be skipped");
        assertNotEquals(row3, result.get(key), "Third occurrence should be skipped");
    }

    @Test
    void loadFactProductionRowsWithEmptyResult() {

        List<FactProductionRow> emptyRows = List.of();

        when(em.createNativeQuery(anyString(), eq("FactProductionRowMapping"))).thenReturn(query);
        when(query.setParameter(eq(1), any(Timestamp.class))).thenReturn(query);
        when(query.setParameter(eq(2), any(Timestamp.class))).thenReturn(query);
        when(query.getResultList()).thenReturn(emptyRows);

        Map<FactKey, FactProductionRow> result = jobDBLoader.loadFactProductionRowMap(from, to);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void loadFactProductionRowMapWithMultipleKeys() {

        FactProductionRow row1 = new FactProductionRow("KMC1", Timestamp.valueOf(from.plusHours(1)), 
        1, 1, Timestamp.valueOf(from.plusHours(1)), "L1");
        FactProductionRow row2 = new FactProductionRow("KMC1", Timestamp.valueOf(from.plusHours(2)), 
        2, 1, Timestamp.valueOf(from.plusHours(2)), "L1");
        FactProductionRow row3 = new FactProductionRow("KMC2", Timestamp.valueOf(from.plusHours(3)), 
        1, 1, Timestamp.valueOf(from.plusHours(3)), "L2");
        List<FactProductionRow> rows = List.of(row1, row2, row3);

        when(em.createNativeQuery(anyString(), eq("FactProductionRowMapping"))).thenReturn(query);
        when(query.setParameter(eq(1), any(Timestamp.class))).thenReturn(query);
        when(query.setParameter(eq(2), any(Timestamp.class))).thenReturn(query);
        when(query.getResultList()).thenReturn(rows);

        Map<FactKey, FactProductionRow> result = jobDBLoader.loadFactProductionRowMap(from, to);

        assertNotNull(result);
        assertEquals(3, result.size(), "Should have 3 unique keys: (KMC1,1), (KMC1,2), and (KMC2,1)");
        
        FactKey key1 = new FactKey("KMC1", 1);
        FactKey key2 = new FactKey("KMC1", 2);
        FactKey key3 = new FactKey("KMC2", 1);
        
        assertTrue(result.containsKey(key1));
        assertTrue(result.containsKey(key2));
        assertTrue(result.containsKey(key3));
        assertEquals(row1, result.get(key1));
        assertEquals(row2, result.get(key2));
        assertEquals(row3, result.get(key3));
    }
}
