package repository.jobs;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.acme.foodpackaging.dto.DbMaintenanceRow;
import org.acme.foodpackaging.record.DbJobRow;
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
 *
 * JobRepository receives from/to from WorkCalendar.getFromDate()/getToDate().
 * WorkCalendar(startDate) sets: fromDate = startDate.minusDays(1), toDate = startDate.plusDays(3).
 * Tests use testDate as startDay and pass workCalendarFrom/workCalendarTo to simulate this.
 */
@QuarkusTest
@Tag("database")
class JobRepositoryIT {

    @Inject
    JobRepository jobRepository;
    @Inject
    EntityManager entityManager;

    /** startDay passed to WorkCalendar(startDate) in ScheduleBuilder.buildSchedule */
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

    /**
     * Helper method to insert data into NS_MC table (product data)
     */
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

    /**
     * Helper method to insert data into BD_VZPMC table (job rows)
     */
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

    /**
     * Helper method to insert data into OEE_PEV table (maintenance rows)
     */
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

    @BeforeEach
    @Transactional
    void setUp() {
        // Create MES schema (dbo is created in application.properties INIT parameter)
            entityManager.createNativeQuery("CREATE SCHEMA IF NOT EXISTS MES").executeUpdate();
        
        // Create the MS_LOG table in MES schema
        // Note: H2 in MSSQLServer mode should handle [MES].[dbo].[MS_LOG] as MES.MS_LOG
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
        
        testDate = LocalDate.of(2026, 1, 15);
    }

    @Test
    @Transactional
    void loadsDataWithCorrectDateRange() {
        // WorkCalendar: from = startDay.minusDays(1), to = startDay.plusDays(3)
        LocalDate workCalendarFrom = testDate.minusDays(1);
        LocalDate workCalendarTo = testDate.plusDays(3);
        // SQL: DTV > ?1 AND DTV <= ?2 (exclusive start, inclusive end)
        LocalDateTime withinRange1 = workCalendarFrom.atStartOfDay().plusHours(1);
        LocalDateTime withinRange2 = workCalendarTo.atStartOfDay(); // at end boundary (inclusive)
        LocalDateTime outsideRangeBefore = workCalendarFrom.atStartOfDay().minusHours(1);
        LocalDateTime outsideRangeAfter = workCalendarTo.atStartOfDay().plusHours(1);
        
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();
        UUID id4 = UUID.randomUUID();
        UUID id5 = UUID.randomUUID();
        
        insertMsLog(id1, "KMC1", withinRange1, 1, 1, withinRange1, "L1");
        insertMsLog(id2, "KMC1", withinRange2, 2, 1, withinRange2, "L1");
        insertMsLog(id3, "KMC2", outsideRangeBefore, 1, 1, outsideRangeBefore, "L2");
        insertMsLog(id4, "KMC3", outsideRangeAfter, 1, 1, outsideRangeAfter, "L3");
        insertMsLog(id5, "KMC4", withinRange1, 1, 0, withinRange1, "L4");

        entityManager.flush();
        entityManager.clear();

        Map<FactKey, FactProductionRow> result = jobRepository.getFactProductionRowMap(workCalendarFrom, workCalendarTo);

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
        LocalDate workCalendarFrom = testDate.minusDays(1);
        LocalDate workCalendarTo = testDate.plusDays(3);
       
        Map<FactKey, FactProductionRow> result = jobRepository.getFactProductionRowMap(workCalendarFrom, workCalendarTo);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @Transactional
    void getFactProductionRowMapIt() {
        LocalDate workCalendarFrom = testDate.minusDays(1);
        LocalDate workCalendarTo = testDate.plusDays(3);
        LocalDateTime withinRange = workCalendarFrom.atStartOfDay().plusHours(1);
        
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        
        insertMsLog(id1, "KMC1", withinRange, 1, 1, withinRange, "L1");
        insertMsLog(id2, "KMC1", withinRange.plusHours(1), 1, 1, withinRange.plusHours(1), "L2");

        entityManager.flush();
        entityManager.clear();

        Map<FactKey, FactProductionRow> result = jobRepository.getFactProductionRowMap(workCalendarFrom, workCalendarTo);

        // Should keep first occurrence, skip duplicates
        assertNotNull(result);
        assertEquals(1, result.size(), "Should keep first occurrence when duplicate keys exist");
        
        FactKey key = new FactKey("KMC1", 1);
        assertTrue(result.containsKey(key));
    }

    @Test
    @Transactional
    void verifysDateRangeCalculation() {
        LocalDate startDay = LocalDate.of(2024, 6, 20);
        LocalDate workCalendarFrom = startDay.minusDays(1);
        LocalDate workCalendarTo = startDay.plusDays(3);
        // SQL: DTV > ?1 AND DTV <= ?2
        LocalDateTime withinRange = workCalendarFrom.atStartOfDay().plusHours(1);
        LocalDateTime atEndBoundary = workCalendarTo.atStartOfDay();
        LocalDateTime outsideRangeBefore = workCalendarFrom.atStartOfDay().minusHours(1);
        LocalDateTime outsideRangeAfter = workCalendarTo.atStartOfDay().plusHours(1);
        
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();
        UUID id4 = UUID.randomUUID();
        
        insertMsLog(id1, "KMC1", withinRange, 1, 1, withinRange, "L1");
        insertMsLog(id2, "KMC2", atEndBoundary, 1, 1, atEndBoundary, "L2");
        insertMsLog(id3, "KMC3", outsideRangeBefore, 1, 1, outsideRangeBefore, "L3");
        insertMsLog(id4, "KMC4", outsideRangeAfter, 1, 1, outsideRangeAfter, "L4");

        entityManager.flush();
        entityManager.clear();

        Map<FactKey, FactProductionRow> result = jobRepository.getFactProductionRowMap(workCalendarFrom, workCalendarTo);

        assertNotNull(result);
        assertEquals(2, result.size(), "Should only load data within the date range");
        
        FactKey key1 = new FactKey("KMC1", 1);
        FactKey key2 = new FactKey("KMC2", 1);
        assertTrue(result.containsKey(key1), "Should include data within range");
        assertTrue(result.containsKey(key2), "Should include data at end boundary (inclusive)");
        assertFalse(result.containsKey(new FactKey("KMC3", 1)), "Should exclude data before from date");
        assertFalse(result.containsKey(new FactKey("KMC4", 1)), "Should exclude data after to date");
    }

    @Test
    @Transactional
    void verifiesDateRangeBoundaries() {
        // WorkCalendar: from = startDay.minusDays(1), to = startDay.plusDays(3). SQL: DTV > ?1 AND DTV <= ?2
        LocalDate startDay = LocalDate.of(2024, 6, 20);
        LocalDate workCalendarFrom = startDay.minusDays(1);
        LocalDate workCalendarTo = startDay.plusDays(3);
        LocalDateTime justAfterFrom = workCalendarFrom.atStartOfDay().plusSeconds(1);
        LocalDateTime exactlyAtTo = workCalendarTo.atStartOfDay();
        LocalDateTime exactlyAtFrom = workCalendarFrom.atStartOfDay();
        LocalDateTime justAfterTo = workCalendarTo.atStartOfDay().plusSeconds(1);
        
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();
        UUID id4 = UUID.randomUUID();
        
        insertMsLog(id1, "KMC1", justAfterFrom, 1, 1, justAfterFrom, "L1");
        insertMsLog(id2, "KMC2", exactlyAtTo, 1, 1, exactlyAtTo, "L2");
        insertMsLog(id3, "KMC3", exactlyAtFrom, 1, 1, exactlyAtFrom, "L3");
        insertMsLog(id4, "KMC4", justAfterTo, 1, 1, justAfterTo, "L4");

        entityManager.flush();
        entityManager.clear();

        Map<FactKey, FactProductionRow> result = jobRepository.getFactProductionRowMap(workCalendarFrom, workCalendarTo);

        assertNotNull(result);
        assertEquals(2, result.size(), "Should include data at boundaries");
        
        assertTrue(result.containsKey(new FactKey("KMC1", 1)), "Should include data just after from date");
        assertTrue(result.containsKey(new FactKey("KMC2", 1)), "Should include data at to date (inclusive)");
        assertFalse(result.containsKey(new FactKey("KMC3", 1)), "Should exclude data exactly at from date (exclusive, uses >)");
        assertFalse(result.containsKey(new FactKey("KMC4", 1)), "Should exclude data just after to date");
    }

    @Test
    @Transactional
    void getDbJobRowMap_LoadsDataWithCorrectDateRange() {
        LocalDate workCalendarFrom = testDate.minusDays(1);
        LocalDate workCalendarTo = testDate.plusDays(3);
        // LOAD_JOBS_DB: DTI >= from.atStartOfDay() AND DTI < to.atStartOfDay()
        
        UUID nsMcId1 = UUID.randomUUID();
        UUID nsMcId2 = UUID.randomUUID();
        insertNsMc(nsMcId1, "KMC1", 0.05, "Product 1");
        insertNsMc(nsMcId2, "KMC2", 0.08, "Product 2");
        
        UUID jobId1 = UUID.randomUUID();
        UUID jobId2 = UUID.randomUUID();
        UUID jobId3 = UUID.randomUUID();
        
        LocalDateTime dti1 = workCalendarFrom.atStartOfDay().plusHours(1);
        LocalDateTime dti2 = testDate.atStartOfDay().plusHours(1); // within [from, to)
        LocalDateTime dti3 = workCalendarTo.atStartOfDay().plusHours(1); // outside (DTI < to.atStartOfDay())
        
        insertBdVzpmc(jobId1, dti1, "KMC1", 1, 10, 100.0, null, null, 60, 1L, 1, "L1", "test", 0);
        insertBdVzpmc(jobId2, dti2, "KMC2", 2, 20, 200.0, null, null, 120, 2L, 2, "L2", "test", 0);
        insertBdVzpmc(jobId3, dti3, "KMC1", 3, 30, 300.0, null, null, 180, 3L, 3, "L1", "test", 0);
        
        entityManager.flush();
        entityManager.clear();
        
        Map<Long, DbJobRow> result = jobRepository.getDbJobRowMap(workCalendarFrom, workCalendarTo);
        
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
    void getDbJobRowMap_FiltersByKsk() {
        LocalDate workCalendarFrom = testDate.minusDays(1);
        LocalDate workCalendarTo = testDate.plusDays(3);
        
        UUID nsMcId = UUID.randomUUID();
        insertNsMc(nsMcId, "KMC1", 0.05, "Product 1");
        
        UUID jobId1 = UUID.randomUUID();
        UUID jobId2 = UUID.randomUUID();
        
        insertBdVzpmc(jobId1, workCalendarFrom.atStartOfDay().plusHours(1), "KMC1", 1, 10, 100.0, 
                     null, null, 60, 1L, 1, "L1", "test", 0);
        insertBdVzpmc(jobId2, workCalendarFrom.atStartOfDay().plusHours(2), "KMC1", 2, 20, 200.0, 
                     null, null, 120, 2L, 2, "L1", "other", 0); // Different KSK
        
        entityManager.flush();
        entityManager.clear();
        
        Map<Long, DbJobRow> result = jobRepository.getDbJobRowMap(workCalendarFrom, workCalendarTo);
        
        // Should only include rows with KSK = "test" (from test properties)
        assertEquals(1, result.size());
        assertTrue(result.containsKey(1L));
        assertFalse(result.containsKey(2L));
    }

    @Test
    @Transactional
    void getDbJobRowMap_FiltersDeletedAndInvalidRows() {
        LocalDate workCalendarFrom = testDate.minusDays(1);
        LocalDate workCalendarTo = testDate.plusDays(3);
        
        UUID nsMcId = UUID.randomUUID();
        insertNsMc(nsMcId, "KMC1", 0.05, "Product 1");
        
        UUID jobId1 = UUID.randomUUID();
        UUID jobId2 = UUID.randomUUID();
        UUID jobId3 = UUID.randomUUID();
        UUID jobId4 = UUID.randomUUID();
        
        insertBdVzpmc(jobId1, workCalendarFrom.atStartOfDay().plusHours(1), "KMC1", 1, 10, 100.0, 
                     null, null, 60, 1L, 1, "L1", "test", 0);
        insertBdVzpmc(jobId2, workCalendarFrom.atStartOfDay().plusHours(2), "KMC1", 2, 20, 200.0, 
                     null, null, 120, 2L, 2, "L1", "test", 1);
        insertBdVzpmc(jobId3, workCalendarFrom.atStartOfDay().plusHours(3), "KMC1", 0, 30, 300.0, 
                     null, null, 180, 3L, 3, "L1", "test", 0);
        UUID nsMcId2 = UUID.randomUUID();
        insertNsMc(nsMcId2, "KMC2", 0.15, "Product 2");
        insertBdVzpmc(jobId4, workCalendarFrom.atStartOfDay().plusHours(4), "KMC2", 4, 40, 400.0, 
                     null, null, 240, 4L, 4, "L1", "test", 0);
        
        entityManager.flush();
        entityManager.clear();
        
        Map<Long, DbJobRow> result = jobRepository.getDbJobRowMap(workCalendarFrom, workCalendarTo);
        
        assertEquals(1, result.size(), "Should only include valid, non-deleted rows");
        assertTrue(result.containsKey(1L));
        assertFalse(result.containsKey(2L), "Should exclude deleted rows");
        assertFalse(result.containsKey(3L), "Should exclude rows with NP <= 0");
        assertFalse(result.containsKey(4L), "Should exclude rows with massa >= 0.1");
    }

    @Test
    @Transactional
    void getDbMaintenanceRowMap_LoadsDataWithCorrectDateRange() {
        LocalDate workCalendarFrom = testDate.minusDays(1);
        LocalDate workCalendarTo = testDate.plusDays(3);
        
        LocalDateTime pdtn1 = workCalendarFrom.atStartOfDay().plusHours(1);
        LocalDateTime pdtn2 = testDate.atStartOfDay().plusHours(1);
        LocalDateTime pdtn3 = workCalendarTo.atStartOfDay().plusHours(1); // outside [from, to)
        
        Long fId1 = insertOeePev("L1", pdtn1, pdtn1.plusHours(2), 120, 0L, 0, "Maintenance 1");
        Long fId2 = insertOeePev("L2", pdtn2, pdtn2.plusHours(3), 180, null, 0, "Maintenance 2");
        Long fId3 = insertOeePev("L3", pdtn3, pdtn3.plusHours(1), 60, 0L, 0, "Maintenance 3");
        
        entityManager.flush();
        entityManager.clear();
        
        Map<Long, DbMaintenanceRow> result = jobRepository.getDbMaintenanceRowMap(workCalendarFrom, workCalendarTo);

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
    void getDbMaintenanceRowMap_LoadsByPdtNOrPdtO() {
        LocalDate workCalendarFrom = testDate.minusDays(1);
        LocalDate workCalendarTo = testDate.plusDays(3);
        
        LocalDateTime pdtN1 = workCalendarFrom.atStartOfDay().plusHours(1);
        Long fId1 = insertOeePev("L1", pdtN1, pdtN1.plusHours(2), 120, 0L, 0, "Maintenance 1");
        
        LocalDateTime pdtN2 = workCalendarFrom.atStartOfDay().minusDays(1);
        LocalDateTime pdtO2 = workCalendarFrom.atStartOfDay().plusHours(1);
        Long fId2 = insertOeePev("L2", pdtN2, pdtO2, 180, null, 0, "Maintenance 2");
        
        LocalDateTime pdtN3 = workCalendarTo.atStartOfDay().plusDays(1);
        Long fId3 = insertOeePev("L3", pdtN3, pdtN3.plusHours(1), 60, 0L, 0, "Maintenance 3");
        
        entityManager.flush();
        entityManager.clear();
        
        Map<Long, DbMaintenanceRow> result = jobRepository.getDbMaintenanceRowMap(workCalendarFrom, workCalendarTo);
        
        assertEquals(2, result.size(), "Should include maintenance that starts OR ends in range");
        assertTrue(result.containsKey(fId1), "Should include maintenance that starts in range");
        assertTrue(result.containsKey(fId2), "Should include maintenance that ends in range");
        assertFalse(result.containsKey(fId3), "Should exclude maintenance outside range");
    }

    @Test
    @Transactional
    void getDbMaintenanceRowMap_FiltersDeletedAndInvalidRows() {
        LocalDate workCalendarFrom = testDate.minusDays(1);
        LocalDate workCalendarTo = testDate.plusDays(3);
        
        LocalDateTime pdtN = workCalendarFrom.atStartOfDay().plusHours(1);
        
        Long fId1 = insertOeePev("L1", pdtN, pdtN.plusHours(2), 120, 0L, 0, "Maintenance 1");
        Long fId2 = insertOeePev("L2", pdtN, pdtN.plusHours(3), 180, null, 0, "Maintenance 2");
        Long fId3 = insertOeePev("L3", pdtN, pdtN.plusHours(1), 60, 0L, 1, "Maintenance 3");
        Long fId4 = insertOeePev("L4", pdtN, pdtN.plusHours(1), 60, 1L, 0, "Maintenance 4");
        
        entityManager.flush();
        entityManager.clear();
        
        Map<Long, DbMaintenanceRow> result = jobRepository.getDbMaintenanceRowMap(workCalendarFrom, workCalendarTo);
        
        assertEquals(2, result.size(), "Should only include valid, non-deleted maintenance");
        assertTrue(result.containsKey(fId1), "Should include maintenance with SNPZ = 0");
        assertTrue(result.containsKey(fId2), "Should include maintenance with SNPZ = NULL");
        assertFalse(result.containsKey(fId3), "Should exclude deleted maintenance");
        assertFalse(result.containsKey(fId4), "Should exclude maintenance with SNPZ != 0 and != NULL");
    }

    @Test
    @Transactional
    void getDbMaintenanceRowMap_ReturnsEmptyMapWhenNoData() {
        LocalDate workCalendarFrom = testDate.minusDays(1);
        LocalDate workCalendarTo = testDate.plusDays(3);
        
        Map<Long, DbMaintenanceRow> result = jobRepository.getDbMaintenanceRowMap(workCalendarFrom, workCalendarTo);
        
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
