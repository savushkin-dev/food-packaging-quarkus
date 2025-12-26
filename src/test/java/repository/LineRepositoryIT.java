package repository;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.acme.foodpackaging.entity.lines.LineEntity;
import org.acme.foodpackaging.repository.lines.LineRepository;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@QuarkusTest
class LineRepositoryIT {

    @Inject
    LineRepository repository;

    @Test
    void loadLinesShouldReturnAllLinesFromDB() {
        LineEntity line1 = new LineEntity();
        line1.setKrc("L1");
        line1.setSnm("Line 1");
        line1.setFDel(0);
        repository.persist(line1);

        LineEntity line2 = new LineEntity();
        line2.setKrc("L2");
        line2.setSnm("Line 2");
        line2.setFDel(0);
        repository.persist(line2);

        ConcurrentMap<String, String> result = repository.loadLines();

        assertEquals(2, result.size());
        assertEquals("Line 1", result.get("L1"));
        assertEquals("Line 2", result.get("L2"));
    }

    @Test
    void loadLinesShouldSkipDeletedLines() {
        LineEntity deletedLine = new LineEntity();
        deletedLine.setKrc("L3");
        deletedLine.setSnm("Deleted Line");
        deletedLine.setFDel(1); // помечена как удаленная
        repository.persist(deletedLine);

        ConcurrentMap<String, String> result = repository.loadLines();

        assertFalse(result.containsKey("L3"));
    }
}
