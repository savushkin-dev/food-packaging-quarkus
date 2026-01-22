package persistence.load;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.acme.foodpackaging.dto.DbMaintenanceRow;
import org.acme.foodpackaging.persistence.load.JobDBLoader;
import org.acme.foodpackaging.record.DbJobRow;
import org.acme.foodpackaging.record.FactKey;
import org.acme.foodpackaging.record.FactProductionRow;
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
 * Integration tests for JobDBLoader.
 * Tests actual database loading using H2 in-memory database.
 *
 * JobRepository passes from.atStartOfDay() and to.atStartOfDay() to JobDBLoader,
 * where from/to come from WorkCalendar: from = startDay.minusDays(1), to = startDay.plusDays(3).
 * testFrom/testTo simulate this range.
 */
@QuarkusTest
@Tag("database")
class JobDBLoaderIT {

    @Inject
    JobDBLoader jobDBLoader;
    @Inject
    EntityManager entityManager;

    /** Simulates WorkCalendar range: from.atStartOfDay(), to.atStartOfDay() */
    private LocalDateTime testFrom;
    private LocalDateTime testTo;

    @BeforeEach
    @Transactional
    void setUp() {
        // Create MES schema
        entityManager.createNativeQuery("CREATE SCHEMA IF NOT EXISTS MES").executeUpdate();
        
        // Create the MS_LOG table in MES schema
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
        
        // Create BD_VZPMC table for job rows
        entityManager.createNativeQuery("""
            CREATE TABLE IF NOT EXISTS MES.BD_VZPMC (
                F_GUID UUID NOT NULL PRIMARY KEY,
                DTI TIMESTAMP,
                KMC VARCHAR(255),
                NP INTEGER,
                KOLEV INTEGER,
                MASSA DOUBLE,
                PDTN TIMESTAMP,
                PDTO TIMESTAMP,
                PDUR INTEGER,
                SNPZ BIGINT,
                UX INTEGER,
                KRC CHAR(12),
                KSK VARCHAR(10),
                F_DEL INTEGER
            )
            """).executeUpdate();
        
        // Create NS_MC table for product data (JOIN with BD_VZPMC)
        entityManager.createNativeQuery("""
            CREATE TABLE IF NOT EXISTS MES.NS_MC (
                F_GUID UUID NOT NULL PRIMARY KEY,
                KMC VARCHAR(255),
                MASSA DOUBLE,
                SNM VARCHAR(255)
            )
            """).executeUpdate();
        
        // Create OEE_PEV table for maintenance rows
        entityManager.createNativeQuery("""
            CREATE TABLE IF NOT EXISTS MES.OEE_PEV (
                F_ID BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
                KRC CHAR(12),
                PDTN TIMESTAMP,
                PDTO TIMESTAMP,
                PDUR INTEGER,
                SNPZ BIGINT,
                F_DEL INTEGER,
                NOTE VARCHAR(255)
            )
            """).executeUpdate();
       
        // Clear existing data
        entityManager.createNativeQuery("DELETE FROM MES.MS_LOG").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM MES.BD_VZPMC").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM MES.NS_MC").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM MES.OEE_PEV").executeUpdate();
        
        // WorkCalendar: from = startDay.minusDays(1), to = startDay.plusDays(3). JobRepository uses from.atStartOfDay(), to.atStartOfDay().
        LocalDate startDay = LocalDate.of(2026, 1, 15);
        testFrom = startDay.minusDays(1).atStartOfDay();
        testTo = startDay.plusDays(3).atStartOfDay();
    }

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

    private void insertNsMc(UUID id, String kmc, Double massa, String snm) {
        entityManager.createNativeQuery("""
            INSERT INTO MES.NS_MC (F_GUID, KMC, MASSA, SNM) 
            VALUES (?, ?, ?, ?)
            """)
            .setParameter(1, id)
            .setParameter(2, kmc)
            .setParameter(3, massa)
            .setParameter(4, snm)
            .executeUpdate();
    }

    private void insertBdVzpmc(UUID id, LocalDateTime dti, String kmc, Integer np, Integer kolev, 
                               Double massa, LocalDateTime pdtn, LocalDateTime pdto, Integer pdur,
                               Long snpz, Integer ux, String krc, String ksk, Integer fDel) {
        entityManager.createNativeQuery("""
            INSERT INTO MES.BD_VZPMC (F_GUID, DTI, KMC, NP, KOLEV, MASSA, PDTN, PDTO, PDUR, SNPZ, UX, KRC, KSK, F_DEL) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """)
            .setParameter(1, id)
            .setParameter(2, Timestamp.valueOf(dti))
            .setParameter(3, kmc)
            .setParameter(4, np)
            .setParameter(5, kolev)
            .setParameter(6, massa)
            .setParameter(7, pdtn != null ? Timestamp.valueOf(pdtn) : null)
            .setParameter(8, pdto != null ? Timestamp.valueOf(pdto) : null)
            .setParameter(9, pdur)
            .setParameter(10, snpz)
            .setParameter(11, ux)
            .setParameter(12, krc)
            .setParameter(13, ksk)
            .setParameter(14, fDel)
            .executeUpdate();
    }

    private Long insertOeePev(String krc, LocalDateTime pdtn, LocalDateTime pdto, Integer pdur,
                               Long snpz, Integer fDel, String note) {
        entityManager.createNativeQuery("""
            INSERT INTO MES.OEE_PEV (KRC, PDTN, PDTO, PDUR, SNPZ, F_DEL, NOTE) 
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """)
            .setParameter(1, krc)
            .setParameter(2, pdtn != null ? Timestamp.valueOf(pdtn) : null)
            .setParameter(3, pdto != null ? Timestamp.valueOf(pdto) : null)
            .setParameter(4, pdur)
            .setParameter(5, snpz)
            .setParameter(6, fDel)
            .setParameter(7, note)
            .executeUpdate();
        
        // Get the generated F_ID
        Object result = entityManager.createNativeQuery("SELECT MAX(F_ID) FROM MES.OEE_PEV").getSingleResult();
        return result != null ? ((Number) result).longValue() : null;
    }

    @Test
    @Transactional
    void loadJobRowMap_LoadsDataWithCorrectDateRange() {
        // Create product data in NS_MC (required for JOIN)
        UUID nsMcId1 = UUID.randomUUID();
        UUID nsMcId2 = UUID.randomUUID();
        insertNsMc(nsMcId1, "KMC1", 0.05, "Product 1"); // massa < 0.1
        insertNsMc(nsMcId2, "KMC2", 0.08, "Product 2"); // massa < 0.1
        
        // Create job data in BD_VZPMC
        UUID jobId1 = UUID.randomUUID();
        UUID jobId2 = UUID.randomUUID();
        UUID jobId3 = UUID.randomUUID();
        
        LocalDateTime dti1 = testFrom.plusHours(1);
        LocalDateTime dti2 = testFrom.plusDays(1);
        LocalDateTime dti3 = testTo.plusHours(1); // Outside range (>= to)
        
        insertBdVzpmc(jobId1, dti1, "KMC1", 1, 10, 100.0, null, null, 60, 1L, 1, "L1", "test", 0);
        insertBdVzpmc(jobId2, dti2, "KMC2", 2, 20, 200.0, null, null, 120, 2L, 2, "L2", "test", 0);
        insertBdVzpmc(jobId3, dti3, "KMC1", 3, 30, 300.0, null, null, 180, 3L, 3, "L1", "test", 0);
        
        entityManager.flush();
        entityManager.clear();
        
        Map<Long, DbJobRow> result = jobDBLoader.loadJobRowMap(testFrom, testTo, "test");
        
        assertNotNull(result);
        assertEquals(2, result.size(), "Should only load rows within date range");
        assertTrue(result.containsKey(1L));
        assertTrue(result.containsKey(2L));
        assertFalse(result.containsKey(3L), "Should exclude data at or after 'to' date");
        
        DbJobRow row1 = result.get(1L);
        assertEquals("KMC1", row1.kmc());
        assertEquals(1, row1.np());
        assertEquals(10, row1.quantity());
    }

    @Test
    @Transactional
    void loadJobRowMap_ThrowsExceptionOnDuplicateSnpz() {
        UUID nsMcId = UUID.randomUUID();
        insertNsMc(nsMcId, "KMC1", 0.05, "Product 1");
        
        UUID jobId1 = UUID.randomUUID();
        UUID jobId2 = UUID.randomUUID();
        
        LocalDateTime dti = testFrom.plusHours(1);
        
        // Two rows with same SNPZ
        insertBdVzpmc(jobId1, dti, "KMC1", 1, 10, 100.0, null, null, 60, 1L, 1, "L1", "test", 0);
        insertBdVzpmc(jobId2, dti.plusHours(1), "KMC1", 2, 20, 200.0, null, null, 120, 1L, 2, "L1", "test", 0);
        
        entityManager.flush();
        entityManager.clear();
        
        // Should throw IllegalStateException for duplicate SNPZ
        assertThrows(IllegalStateException.class, () -> {
            jobDBLoader.loadJobRowMap(testFrom, testTo, "test");
        }, "Should throw exception when duplicate SNPZ exists");
    }

    @Test
    @Transactional
    void loadMaintenanceRowMap_LoadsDataWithCorrectDateRange() {
        LocalDateTime pdtn1 = testFrom.plusHours(1);
        LocalDateTime pdtn2 = testFrom.plusDays(1);
        LocalDateTime pdtn3 = testTo.plusHours(1); // Outside range
        
        Long fId1 = insertOeePev("L1", pdtn1, pdtn1.plusHours(2), 120, 0L, 0, "Maintenance 1");
        Long fId2 = insertOeePev("L2", pdtn2, pdtn2.plusHours(3), 180, null, 0, "Maintenance 2");
        Long fId3 = insertOeePev("L3", pdtn3, pdtn3.plusHours(1), 60, 0L, 0, "Maintenance 3");
        
        entityManager.flush();
        entityManager.clear();
        
        Map<Long, DbMaintenanceRow> result = jobDBLoader.loadMaintenanceRowMap(testFrom, testTo);
        
        assertNotNull(result);
        assertEquals(2, result.size(), "Should only load rows within date range");
        assertTrue(result.containsKey(fId1));
        assertTrue(result.containsKey(fId2));
        assertFalse(result.containsKey(fId3), "Should exclude data at or after 'to' date");
        
        DbMaintenanceRow row1 = result.get(fId1);
        assertEquals("L1", row1.getLineId());
        assertEquals(120, row1.getDuration());
    }

    @Test
    @Transactional
    void loadMaintenanceRowMap_ThrowsExceptionOnDuplicateFId() {
        LocalDateTime pdtn = testFrom.plusHours(1);
        
        // Insert first maintenance
        Long fId1 = insertOeePev("L1", pdtn, pdtn.plusHours(2), 120, 0L, 0, "Maintenance 1");
        
        // Manually insert another row with same F_ID (shouldn't happen in practice, but test the behavior)
        entityManager.createNativeQuery("""
            INSERT INTO MES.OEE_PEV (F_ID, KRC, PDTN, PDTO, PDUR, SNPZ, F_DEL, NOTE) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """)
            .setParameter(1, fId1)
            .setParameter(2, "L2")
            .setParameter(3, Timestamp.valueOf(pdtn))
            .setParameter(4, Timestamp.valueOf(pdtn.plusHours(1)))
            .setParameter(5, 60)
            .setParameter(6, 0L)
            .setParameter(7, 0)
            .setParameter(8, "Maintenance 2")
            .executeUpdate();
        
        entityManager.flush();
        entityManager.clear();
        
        // Should throw IllegalStateException for duplicate F_ID
        assertThrows(IllegalStateException.class, () -> {
            jobDBLoader.loadMaintenanceRowMap(testFrom, testTo);
        }, "Should throw exception when duplicate F_ID exists");
    }

    @Test
    @Transactional
    void loadFactProductionRowMap_LoadsDataWithCorrectDateRange() {
        // SQL query: DTV > ?1 AND DTV <= ?2 (exclusive start, inclusive end)
        LocalDateTime withinRange1 = testFrom.plusHours(1); // Within range
        LocalDateTime atEndBoundary = testTo; // At end boundary (inclusive)
        LocalDateTime outsideRangeBefore = testFrom.minusHours(1); // Outside range (before from)
        LocalDateTime outsideRangeAfter = testTo.plusHours(1); // Outside range (after to)
        
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();
        UUID id4 = UUID.randomUUID();
        UUID id5 = UUID.randomUUID();
        
        insertMsLog(id1, "KMC1", withinRange1, 1, 1, withinRange1, "L1");
        insertMsLog(id2, "KMC1", atEndBoundary, 2, 1, atEndBoundary, "L1");
        insertMsLog(id3, "KMC2", outsideRangeBefore, 1, 1, outsideRangeBefore, "L2");
        insertMsLog(id4, "KMC3", outsideRangeAfter, 1, 1, outsideRangeAfter, "L3");
        insertMsLog(id5, "KMC4", withinRange1, 1, 0, withinRange1, "L4"); // EVENT != 1
        
        entityManager.flush();
        entityManager.clear();
        
        Map<FactKey, FactProductionRow> result = jobDBLoader.loadFactProductionRowMap(testFrom, testTo);
        
        assertNotNull(result);
        assertEquals(2, result.size(), "Should only load rows within date range and with EVENT=1");
        
        FactKey key1 = new FactKey("KMC1", 1);
        FactKey key2 = new FactKey("KMC1", 2);
        
        assertTrue(result.containsKey(key1));
        assertTrue(result.containsKey(key2));
        assertEquals("KMC1", result.get(key1).kmc());
        assertEquals("KMC1", result.get(key2).kmc());
    }

    @Test
    @Transactional
    void loadFactProductionRowMap_KeepsFirstOccurrenceOnDuplicateKeys() {
        LocalDateTime withinRange = testFrom.plusHours(1);
        
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();
        
        // Three rows with same KMC and NP (duplicate key)
        insertMsLog(id1, "KMC1", withinRange, 1, 1, withinRange, "L1");
        insertMsLog(id2, "KMC1", withinRange.plusHours(1), 1, 1, withinRange.plusHours(1), "L2");
        insertMsLog(id3, "KMC1", withinRange.plusHours(2), 1, 1, withinRange.plusHours(2), "L3");
        
        entityManager.flush();
        entityManager.clear();
        
        Map<FactKey, FactProductionRow> result = jobDBLoader.loadFactProductionRowMap(testFrom, testTo);
        
        // Should keep first occurrence, skip duplicates
        assertNotNull(result);
        assertEquals(1, result.size(), "Should keep first occurrence when duplicate keys exist");
        
        FactKey key = new FactKey("KMC1", 1);
        assertTrue(result.containsKey(key));
        assertEquals("L1", result.get(key).lineIdFact(), "Should keep first occurrence");
    }
}
