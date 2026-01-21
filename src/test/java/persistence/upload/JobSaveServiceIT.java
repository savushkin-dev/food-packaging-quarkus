package persistence.upload;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.domain.WorkCalendar;
import org.acme.foodpackaging.dto.DbMaintenanceRow;
import org.acme.foodpackaging.entity.jobs.OeePev;
import org.acme.foodpackaging.persistence.upload.JobSaveService;
import org.acme.foodpackaging.repository.jobs.BdVpmcRepository;
import org.acme.foodpackaging.repository.jobs.OeePevRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for JobSaveService.
 * Tests actual database persistence operations using H2 in-memory database.
 */
@QuarkusTest
@Tag("database")
class JobSaveServiceIT {

    @Inject
    JobSaveService jobSaveService;
    
    @Inject
    OeePevRepository oeePevRepository;
    
    @Inject
    BdVpmcRepository bdVpmcRepository;
    
    @Inject
    EntityManager entityManager;

    private PackagingSchedule schedule;
    private Line line;
    private Product product;
    private Product maintenanceProduct;

    @BeforeEach
    @Transactional
    void setUp() {
        // Create dbo schema (entities use dbo schema)
        entityManager.createNativeQuery("CREATE SCHEMA IF NOT EXISTS dbo").executeUpdate();
        
        // Create OEE_PEV table
        entityManager.createNativeQuery("""
            CREATE TABLE IF NOT EXISTS dbo.OEE_PEV (
                F_ID BIGINT NOT NULL PRIMARY KEY IDENTITY,
                KRC CHAR(12),
                PDTN TIMESTAMP,
                PDTO TIMESTAMP,
                PDUR INTEGER,
                SNPZ BIGINT,
                F_DEL SMALLINT DEFAULT 0,
                EVTYPE INTEGER,
                REASON INTEGER,
                NOTE VARCHAR(255)
            )
            """).executeUpdate();
        
        // Create BD_VZPMC table
        entityManager.createNativeQuery("""
            CREATE TABLE IF NOT EXISTS dbo.BD_VZPMC (
                F_GUID VARCHAR(36) NOT NULL PRIMARY KEY,
                KMC VARCHAR(255),
                DTI TIMESTAMP,
                NP INTEGER,
                KOLEV INTEGER,
                MASSA DOUBLE,
                PDTN TIMESTAMP,
                PDTO TIMESTAMP,
                PDUR INTEGER,
                SNPZ INTEGER,
                UX INTEGER,
                KRC CHAR(12),
                KSK VARCHAR(10),
                F_DEL INTEGER
            )
            """).executeUpdate();
        
        // Clear existing data
        entityManager.createNativeQuery("DELETE FROM dbo.OEE_PEV").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM dbo.BD_VZPMC").executeUpdate();
        
        // Set up test data
        line = new Line("L1", "Line 1", "operator", LocalDateTime.now());
        product = new Product("PROD1", "KMC1", "KRKMC1", "Type1", "Glaze1", "100", "Filling1");
        maintenanceProduct = new Product("MAINTENANCE", "Maintenance Product");
        
        schedule = new PackagingSchedule();
        schedule.setWorkCalendar(new WorkCalendar(LocalDate.of(2025, 1, 15)));
        schedule.setJobs(new ArrayList<>());
        schedule.setDbMaintenanceRowMap(new HashMap<>());
    }

    @Test
    @Transactional
    void saveNewMaintenanceJob() {
        Job maintenanceJob = Job.createMaintenanceJob(
                "MAINTENANCE-" + UUID.randomUUID(),
                "L1",
                1,
                "Обслуживание",
                "Test maintenance",
                maintenanceProduct,
                60
        );
        maintenanceJob.setLine(line);
        maintenanceJob.setStartProductionDateTime(LocalDateTime.of(2025, 1, 15, 10, 0));
        maintenanceJob.setEndDateTime(LocalDateTime.of(2025, 1, 15, 11, 0));
        
        schedule.getJobs().add(maintenanceJob);
        
        jobSaveService.saveJobsByType(schedule);
        
        // Verify job was saved and fId was assigned
        assertNotNull(maintenanceJob.getFId());
        assertNotEquals("MAINTENANCE-", maintenanceJob.getId().substring(0, 12));
        
        // Verify in database
        OeePev saved = oeePevRepository.findByFId(maintenanceJob.getFId());
        assertNotNull(saved);
        assertEquals("L1", saved.getLineId());
        assertEquals(LocalDateTime.of(2025, 1, 15, 10, 0), saved.getStartProductionDateTime());
        assertEquals(LocalDateTime.of(2025, 1, 15, 11, 0), saved.getEndDateTime());
        assertEquals(60, saved.getDuration());
        assertEquals(1, saved.getMaintenanceTypeId());
        assertEquals("Test maintenance", saved.getNote());
        assertEquals(0L, saved.getSnpz());
    }

    @Test
    @Transactional
    void updateExistingMaintenanceJob() {
        // Create existing maintenance job in database
        OeePev existing = OeePev.builder()
                .fId(100L)
                .lineId("L1")
                .startProductionDateTime(LocalDateTime.of(2025, 1, 15, 9, 0))
                .endDateTime(LocalDateTime.of(2025, 1, 15, 9, 30))
                .duration(30)
                .maintenanceTypeId(1)
                .note("Old note")
                .snpz(0L)
                .fDel((short) 0)
                .build();
        oeePevRepository.persist(existing);
        
        Job maintenanceJob = Job.createMaintenanceJob(
                "100",
                "L1",
                2,
                "Обслуживание 2",
                "Updated maintenance",
                maintenanceProduct,
                45
        );
        maintenanceJob.setFId(100L);
        maintenanceJob.setLine(line);
        maintenanceJob.setStartProductionDateTime(LocalDateTime.of(2025, 1, 15, 10, 0));
        maintenanceJob.setEndDateTime(LocalDateTime.of(2025, 1, 15, 10, 45));
        
        schedule.getJobs().add(maintenanceJob);
        
        jobSaveService.saveJobsByType(schedule);
        
        // Verify update
        OeePev updated = oeePevRepository.findByFId(100L);
        assertNotNull(updated);
        assertEquals("L1", updated.getLineId());
        assertEquals(LocalDateTime.of(2025, 1, 15, 10, 0), updated.getStartProductionDateTime());
        assertEquals(LocalDateTime.of(2025, 1, 15, 10, 45), updated.getEndDateTime());
        assertEquals(45, updated.getDuration());
        assertNull(updated.getMaintenanceTypeId()); // updateMaintenanceOeePev sets this to null
        assertEquals("Обслуживание 2", updated.getNote()); // Uses job.getName()
    }

    @Test
    @Transactional
    void markDeletedMaintenanceJobs() {
        // Create maintenance row marked as deleted
        OeePev existing = OeePev.builder()
                .fId(200L)
                .lineId("L1")
                .startProductionDateTime(LocalDateTime.of(2025, 1, 15, 9, 0))
                .endDateTime(LocalDateTime.of(2025, 1, 15, 9, 30))
                .duration(30)
                .maintenanceTypeId(1)
                .note("To be deleted")
                .snpz(0L)
                .fDel((short) 0)
                .build();
        oeePevRepository.persist(existing);
        
        // Add to schedule as deleted
        DbMaintenanceRow deletedRow = new DbMaintenanceRow();
        deletedRow.setFId(200L);
        deletedRow.setFDel((short) 1);
        schedule.getDbMaintenanceRowMap().put(200L, deletedRow);
        
        jobSaveService.saveJobsByType(schedule);
        
        // Verify marked as deleted
        OeePev deleted = oeePevRepository.findByFId(200L);
        assertNotNull(deleted);
        assertEquals(1, deleted.getFDel());
    }

    @Test
    @Transactional
    void saveRegularJobWithCleaningOperation() {
        // Create existing BD_VZPMC record using native query since fields are private
        UUID existingId = UUID.randomUUID();
        entityManager.createNativeQuery("""
            INSERT INTO dbo.BD_VZPMC (F_GUID, KMC, SNPZ, KRC, PDTN, PDTO, PDUR)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """)
            .setParameter(1, existingId.toString())
            .setParameter(2, "KMC1")
            .setParameter(3, 12345)
            .setParameter(4, "L1")
            .setParameter(5, java.sql.Timestamp.valueOf(LocalDateTime.of(2025, 1, 15, 8, 0)))
            .setParameter(6, java.sql.Timestamp.valueOf(LocalDateTime.of(2025, 1, 15, 9, 0)))
            .setParameter(7, 60)
            .executeUpdate();
        
        Job regularJob = new Job();
        regularJob.setId("12345");
        regularJob.setSnpz(12345L);
        regularJob.setLine(line);
        regularJob.setProduct(product);
        regularJob.setStartCleaningDateTime(LocalDateTime.of(2025, 1, 15, 9, 30));
        regularJob.setStartProductionDateTime(LocalDateTime.of(2025, 1, 15, 10, 0));
        regularJob.setEndDateTime(LocalDateTime.of(2025, 1, 15, 11, 0));
        
        schedule.getJobs().add(regularJob);
        
        jobSaveService.saveJobsByType(schedule);
        
        // Verify cleaning operation was saved
        OeePev cleaning = oeePevRepository.findBySnpz(12345L);
        assertNotNull(cleaning);
        assertEquals("L1", cleaning.getLineId());
        assertEquals(LocalDateTime.of(2025, 1, 15, 9, 30), cleaning.getStartProductionDateTime());
        assertEquals(LocalDateTime.of(2025, 1, 15, 10, 0), cleaning.getEndDateTime());
        assertEquals(30, cleaning.getDuration());
        assertEquals("Мойка, переналадка", cleaning.getNote());
        assertEquals(12345L, cleaning.getSnpz());
        
        // Verify production job was updated using native query
        @SuppressWarnings("unchecked")
        List<Object[]> results = entityManager.createNativeQuery("""
            SELECT PDTN, PDTO, PDUR, KRC FROM dbo.BD_VZPMC WHERE SNPZ = ?
            """)
            .setParameter(1, 12345)
            .getResultList();
        assertEquals(1, results.size());
        Object[] row = results.get(0);
        assertEquals(java.sql.Timestamp.valueOf(LocalDateTime.of(2025, 1, 15, 10, 0)), row[0]);
        assertEquals(java.sql.Timestamp.valueOf(LocalDateTime.of(2025, 1, 15, 11, 0)), row[1]);
        assertEquals(60, row[2]);
        assertEquals("L1", row[3]);
    }

    @Test
    @Transactional
    void saveRegularJobWithoutCleaningOperation() {
        // Create existing BD_VZPMC record using native query
        UUID existingId2 = UUID.randomUUID();
        entityManager.createNativeQuery("""
            INSERT INTO dbo.BD_VZPMC (F_GUID, KMC, SNPZ, KRC, PDTN, PDTO, PDUR)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """)
            .setParameter(1, existingId2.toString())
            .setParameter(2, "KMC1")
            .setParameter(3, 54321)
            .setParameter(4, "L1")
            .setParameter(5, java.sql.Timestamp.valueOf(LocalDateTime.of(2025, 1, 15, 8, 0)))
            .setParameter(6, java.sql.Timestamp.valueOf(LocalDateTime.of(2025, 1, 15, 9, 0)))
            .setParameter(7, 60)
            .executeUpdate();
        
        Job regularJob = new Job();
        regularJob.setId("54321");
        regularJob.setSnpz(54321L);
        regularJob.setLine(line);
        regularJob.setProduct(product);
        // No cleaning - startCleaningDateTime equals startProductionDateTime
        LocalDateTime startTime = LocalDateTime.of(2025, 1, 15, 10, 0);
        regularJob.setStartCleaningDateTime(startTime);
        regularJob.setStartProductionDateTime(startTime);
        regularJob.setEndDateTime(LocalDateTime.of(2025, 1, 15, 11, 0));
        
        schedule.getJobs().add(regularJob);
        
        jobSaveService.saveJobsByType(schedule);
        
        // Verify no cleaning operation was created
        OeePev cleaning = oeePevRepository.findBySnpz(54321L);
        assertNull(cleaning);
        
        // Verify production job was updated using native query
        @SuppressWarnings("unchecked")
        List<Object[]> results2 = entityManager.createNativeQuery("""
            SELECT PDTN, PDTO, PDUR FROM dbo.BD_VZPMC WHERE SNPZ = ?
            """)
            .setParameter(1, 54321)
            .getResultList();
        assertEquals(1, results2.size());
        Object[] row2 = results2.get(0);
        assertEquals(java.sql.Timestamp.valueOf(startTime), row2[0]);
        assertEquals(java.sql.Timestamp.valueOf(LocalDateTime.of(2025, 1, 15, 11, 0)), row2[1]);
        assertEquals(60, row2[2]);
    }

    @Test
    @Transactional
    void saveJobsByType_mixedJobs() {
        // Create existing BD_VZPMC record using native query
        UUID existingProdId = UUID.randomUUID();
        entityManager.createNativeQuery("""
            INSERT INTO dbo.BD_VZPMC (F_GUID, KMC, SNPZ, KRC)
            VALUES (?, ?, ?, ?)
            """)
            .setParameter(1, existingProdId.toString())
            .setParameter(2, "KMC1")
            .setParameter(3, 11111)
            .setParameter(4, "L1")
            .executeUpdate();
        
        OeePev existingMaint = OeePev.builder()
                .fId(300L)
                .lineId("L1")
                .snpz(0L)
                .fDel((short) 0)
                .build();
        oeePevRepository.persist(existingMaint);
        
        // Create mixed jobs
        Job maintenanceJob = Job.createMaintenanceJob(
                "MAINTENANCE-" + UUID.randomUUID(),
                "L1",
                1,
                "Обслуживание",
                "Mixed test",
                maintenanceProduct,
                30
        );
        maintenanceJob.setLine(line);
        maintenanceJob.setStartProductionDateTime(LocalDateTime.of(2025, 1, 15, 10, 0));
        maintenanceJob.setEndDateTime(LocalDateTime.of(2025, 1, 15, 10, 30));
        
        Job regularJob = new Job();
        regularJob.setId("11111");
        regularJob.setSnpz(11111L);
        regularJob.setLine(line);
        regularJob.setProduct(product);
        LocalDateTime startTime = LocalDateTime.of(2025, 1, 15, 11, 0);
        regularJob.setStartCleaningDateTime(startTime);
        regularJob.setStartProductionDateTime(startTime);
        regularJob.setEndDateTime(LocalDateTime.of(2025, 1, 15, 12, 0));
        
        schedule.getJobs().add(maintenanceJob);
        schedule.getJobs().add(regularJob);
        
        jobSaveService.saveJobsByType(schedule);
        
        // Verify both were processed
        assertNotNull(maintenanceJob.getFId());
        OeePev savedMaint = oeePevRepository.findByFId(maintenanceJob.getFId());
        assertNotNull(savedMaint);
        
        // Verify production job was updated using native query
        @SuppressWarnings("unchecked")
        List<Object[]> results3 = entityManager.createNativeQuery("""
            SELECT PDTN FROM dbo.BD_VZPMC WHERE SNPZ = ?
            """)
            .setParameter(1, 11111)
            .getResultList();
        assertEquals(1, results3.size());
        assertEquals(java.sql.Timestamp.valueOf(startTime), results3.get(0)[0]);
    }
}
