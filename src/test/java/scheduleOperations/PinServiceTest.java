package scheduleOperations;

import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.dto.PinRequestDTO;
import org.acme.foodpackaging.scheduleOperations.PinService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PinServiceTest {

    private PinService service;
    private Line line;

    @BeforeEach
    void setup() {
        service = new PinService();

        line = new Line(
                "line1",
                "Line 1",
                "operator",
                LocalDateTime.now()
        );

        line.setJobs(new ArrayList<>(List.of(
                job("1"), job("2"), job("3")
        )));
    }

    private Job job(String id) {
        return new Job(
                id,
                "Job " + id,
                null, null,
                null, null, null,
                0, false,
                null, null
        );
    }
    // ------------------------------------------------------------------
    // pinJobs
    // ------------------------------------------------------------------
    @Test
    void pinJobsWithNullCount() {
        service.pinJobs(line, null);

        assertEquals(0, line.getFirstUnpinnedIndex());
    }

    @Test
    void pinJobsWithZeroCount() {
        service.pinJobs(line, 0);

        assertEquals(0, line.getFirstUnpinnedIndex());
    }

    @Test
    void pinJobsWithNegativeCount() {
        service.pinJobs(line, -3);

        assertEquals(0, line.getFirstUnpinnedIndex());
    }

    @Test
    void pinJobsWithLessThanJobsSizeCount() {
        service.pinJobs(line, 2);

        assertEquals(2, line.getFirstUnpinnedIndex());
    }

    @Test
    void pinJobsWithThanJobsSizeCount() {
        service.pinJobs(line, 10);

        assertEquals(3, line.getFirstUnpinnedIndex());
    }
    // ------------------------------------------------------------------
    // pinAllLines / unPinAllLines
    // ------------------------------------------------------------------
    @Test
    void pinAllLine() {
        Line line2 = new Line("line2", "Line 2", "operator", LocalDateTime.now());
        line2.setJobs(new ArrayList<>(List.of(job("A"), job("B"))));

        service.pinAllLines(List.of(line, line2));

        assertEquals(3, line.getFirstUnpinnedIndex());
        assertEquals(2, line2.getFirstUnpinnedIndex());
    }

    @Test
    void unPinAllLines() {
        line.setFirstUnpinnedIndex(3);

        service.unPinAllLines(List.of(line));

        assertEquals(0, line.getFirstUnpinnedIndex());
    }
    // ------------------------------------------------------------------
    // pinLine
    // ------------------------------------------------------------------
    @Test
    void pinAllOneLine() {
        PinRequestDTO request = new PinRequestDTO();
        request.setPinAll(true);

        service.pinLine(line, request);

        assertEquals(3, line.getFirstUnpinnedIndex());
    }

    @Test
    void pinOneLineWithCount() {
        PinRequestDTO request = new PinRequestDTO();
        request.setPinCount(1);

        service.pinLine(line, request);

        assertEquals(1, line.getFirstUnpinnedIndex());
    }

    @Test
    void pinLineWithNoFlags() {
        PinRequestDTO request = new PinRequestDTO();

        service.pinLine(line, request);

        assertEquals(0, line.getFirstUnpinnedIndex());
    }
}
