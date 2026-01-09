package repository.lines;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.acme.foodpackaging.entity.lines.PlrLines;
import org.acme.foodpackaging.repository.lines.LineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for LineRepository.
 * Tests actual database loading using H2 in-memory database.
 */
@QuarkusTest
@Tag("database")
class LineRepositoryIT {

    @Inject
    LineRepository lineRepository;
    @Inject
    EntityManager entityManager;

    @BeforeEach
    @Transactional
    void setUp() {
        // Clear existing data
        entityManager.createQuery("DELETE FROM PlrLines").executeUpdate();
    }

    @Test
    @Transactional
    void loadLines() {
        // Set up test data
        PlrLines line1 = new PlrLines();
        line1.setId(UUID.randomUUID());
        line1.setLineId("L1");
        line1.setSnm("Line 1");
        line1.setType("TYPE1");
        line1.setSpeed(100);
        line1.setFDel(0);
        entityManager.persist(line1);

        PlrLines line2 = new PlrLines();
        line2.setId(UUID.randomUUID());
        line2.setLineId("L2");
        line2.setSnm("Line 2");
        line2.setType("TYPE2");
        line2.setSpeed(200);
        line2.setFDel(0);
        entityManager.persist(line2);

        PlrLines line3 = new PlrLines();
        line3.setId(UUID.randomUUID());
        line3.setLineId("L3");
        line3.setSnm(null); // Should be filtered out
        line3.setType("TYPE3");
        line3.setSpeed(150);
        line3.setFDel(0);
        entityManager.persist(line3);

        PlrLines deletedLine = new PlrLines();
        deletedLine.setId(UUID.randomUUID());
        deletedLine.setLineId("L4");
        deletedLine.setSnm("Line 4");
        deletedLine.setType("TYPE4");
        deletedLine.setSpeed(250);
        deletedLine.setFDel(1); // Deleted, should be filtered
        entityManager.persist(deletedLine);

        ConcurrentMap<String, String> lines = lineRepository.loadLines();

        assertNotNull(lines);
        assertEquals(2, lines.size(), "Should only load lines with non-null names and fDel=0");
        assertEquals("Line 1", lines.get("L1"));
        assertEquals("Line 2", lines.get("L2"));
        assertFalse(lines.containsKey("L3"), "L3 should be excluded because snm is null");
        assertFalse(lines.containsKey("L4"), "L4 should be excluded because fDel=1");
    }

    @Test
    @Transactional
    void loadLinesEmptyResult() {
        ConcurrentMap<String, String> lines = lineRepository.loadLines();

        assertNotNull(lines);
        assertTrue(lines.isEmpty());
    }

    @Test
    @Transactional
    void loadLinesFiltersDeletedAndNullNames() {
        PlrLines validLine = new PlrLines();
        validLine.setId(UUID.randomUUID());
        validLine.setLineId("VALID");
        validLine.setSnm("Valid Line");
        validLine.setType("TYPE1");
        validLine.setSpeed(100);
        validLine.setFDel(0);
        entityManager.persist(validLine);

        PlrLines nullNameLine = new PlrLines();
        nullNameLine.setId(UUID.randomUUID());
        nullNameLine.setLineId("NULL_NAME");
        nullNameLine.setSnm(null);
        nullNameLine.setType("TYPE2");
        nullNameLine.setSpeed(200);
        nullNameLine.setFDel(0);
        entityManager.persist(nullNameLine);

        PlrLines deletedLine = new PlrLines();
        deletedLine.setId(UUID.randomUUID());
        deletedLine.setLineId("DELETED");
        deletedLine.setSnm("Deleted Line");
        deletedLine.setType("TYPE3");
        deletedLine.setSpeed(300);
        deletedLine.setFDel(1);
        entityManager.persist(deletedLine);

        ConcurrentMap<String, String> lines = lineRepository.loadLines();

        assertEquals(1, lines.size());
        assertTrue(lines.containsKey("VALID"));
        assertEquals("Valid Line", lines.get("VALID"));
    }
}
