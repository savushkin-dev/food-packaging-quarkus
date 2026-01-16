package repository.jobs;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.acme.foodpackaging.record.FactKey;
import org.acme.foodpackaging.record.FactProductionRow;
import org.acme.foodpackaging.repository.jobs.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for JobRepository.
 * Tests actual database loading using H2 in-memory database.
 */
@QuarkusTest
@Tag("database")
class JobRepositoryIT {

    @Inject
    JobRepository jobRepository;
    @Inject
    EntityManager entityManager;

    private LocalDate testDate;

    /**
     * Helper method to insert data into MS_LOG table
     */
    private void insertMsLog(UUID id, String kmc, LocalDateTime dtv, Integer np, Integer event, 
                             LocalDateTime dt, String krc) {
        entityManager.createNativeQuery("""
            INSERT INTO MES.MS_LOG (F_GUID, KMC, DTV, NP, EVENT, DT, KRC) 
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """)
            .setParameter(1, id)
            .setParameter(2, kmc)
            .setParameter(3, Timestamp.valueOf(dtv))
            .setParameter(4, np)
            .setParameter(5, event)
            .setParameter(6, Timestamp.valueOf(dt))
            .setParameter(7, krc)
            .executeUpdate();
    }

    @BeforeEach
    @Transactional
    void setUp() {
        // Create MES schema (dbo is created in application.properties INIT parameter)
            entityManager.createNativeQuery("CREATE SCHEMA IF NOT EXISTS MES").executeUpdate();
        // Create the MS_LOG table in MES schema
        // The native query uses [MES].[dbo].[MS_LOG], but H2 doesn't support nested schemas
       
            entityManager.createNativeQuery("""
                CREATE TABLE IF NOT EXISTS MES.MS_LOG (
                    F_GUID UUID NOT NULL PRIMARY KEY,
                    KMC VARCHAR(255),
                    DTV TIMESTAMP,
                    NP INTEGER,
                    EVENT INTEGER,
                    DT TIMESTAMP,
                    KRC CHAR(12)
                )
                """).executeUpdate();
       
        // Clear existing data
       
            entityManager.createNativeQuery("DELETE FROM MES.MS_LOG").executeUpdate();
        
        testDate = LocalDate.of(2026, 1, 15);
    }

    @Test
    @Transactional
    void loadsDataWithCorrectDateRange() {
        //Create test data within the date range (testDate - 1 day to testDate + 1 day)
        LocalDateTime withinRange1 = testDate.atStartOfDay().minusHours(12); // Within range
        LocalDateTime withinRange2 = testDate.atStartOfDay().plusHours(12); // Within range
        
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();
        UUID id4 = UUID.randomUUID();
        
        // Insert into MES.dbo.MS_LOG (the table the query reads from)
        insertMsLog(id1, "KMC1", withinRange1, 1, 1, withinRange1, "L1");
        insertMsLog(id2, "KMC1", withinRange2, 2, 1, withinRange2, "L1");
        // Data outside range should not be included
        insertMsLog(id3, "KMC2", testDate.atStartOfDay().minusDays(2), 1, 1, 
                   testDate.atStartOfDay().minusDays(2), "L2");
        // Data with EVENT != 1 should not be included
        insertMsLog(id4, "KMC3", withinRange1, 1, 0, withinRange1, "L3");

        entityManager.flush();
        entityManager.clear();

        Map<FactKey, FactProductionRow> result = jobRepository.getFactProductionRowMap(testDate);

        assertNotNull(result);
        assertEquals(2, result.size(), "Should only load rows within date range and with EVENT=1");
        
        FactKey key1 = new FactKey("KMC1", 1);
        FactKey key2 = new FactKey("KMC1", 2);
        
        assertTrue(result.containsKey(key1));
        assertTrue(result.containsKey(key2));
        assertEquals("KMC1", result.get(key1).kmc());
        assertEquals("KMC1", result.get(key2).kmc());
        assertEquals(1, result.get(key1).np());
        assertEquals(2, result.get(key2).np());
    }

    @Test
    @Transactional
    void getFactProductionRowsWhenNoData() {
       
        Map<FactKey, FactProductionRow> result = jobRepository.getFactProductionRowMap(testDate);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @Transactional
    void getFactProductionRowMapIt() {
        // Create two rows with same KMC and NP (duplicate key)
        LocalDateTime withinRange = testDate.atStartOfDay();
        
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        
        insertMsLog(id1, "KMC1", withinRange, 1, 1, withinRange, "L1");
        insertMsLog(id2, "KMC1", withinRange.plusHours(1), 1, 1, withinRange.plusHours(1), "L2");

        entityManager.flush();
        entityManager.clear();

        Map<FactKey, FactProductionRow> result = jobRepository.getFactProductionRowMap(testDate);

        // Should keep first occurrence, skip duplicates
        assertNotNull(result);
        assertEquals(1, result.size(), "Should keep first occurrence when duplicate keys exist");
        
        FactKey key = new FactKey("KMC1", 1);
        assertTrue(result.containsKey(key));
    }

    @Test
    @Transactional
    void verifysDateRangeCalculation() {
       
        LocalDate specificDate = LocalDate.of(2024, 6, 20);
        LocalDateTime withinRange = specificDate.atStartOfDay();
        LocalDateTime outsideRange = specificDate.atStartOfDay().minusDays(2);
        
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        
        insertMsLog(id1, "KMC1", withinRange, 1, 1, withinRange, "L1");
        insertMsLog(id2, "KMC2", outsideRange, 1, 1, outsideRange, "L2");

        entityManager.flush();
        entityManager.clear();

        Map<FactKey, FactProductionRow> result = jobRepository.getFactProductionRowMap(specificDate);

        // Should only include data within range (specificDate - 1 day to specificDate + 1 day)
        assertNotNull(result);
        assertEquals(1, result.size(), "Should only load data within the calculated date range");
        
        FactKey key = new FactKey("KMC1", 1);
        assertTrue(result.containsKey(key));
        assertFalse(result.containsKey(new FactKey("KMC2", 1)));
    }
}
