package align;

import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.service.align.AlignCleaningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(MockitoExtension.class)
public class AlignCleaningServiceTest {

    @InjectMocks
    AlignCleaningService cleaningService;

    private PackagingSchedule solution;
    private Line line;

    @BeforeEach
    void setUp(){
        solution = new PackagingSchedule();
        Line line = getTestLine();
        Job j1 = getTestJobFirst();
        Job j2 = getTestJobSecond();

        line.setJobs(List.of(j1, j2));
        solution.setLines(List.of(line));
        solution.setJobs(List.of(j1, j2));
    }

    @Test
    void alignCleanings_shouldCalculateCleaningDelay(){
        cleaningService.alignCleanings(solution);

        assertEquals(Duration.ofMinutes(30), solution.getJobs().getLast().getDelayCleaningDuration());
        assertNull(solution.getJobs().getFirst().getDelayCleaningDuration());
    }

    @Test
    void alignCleanings_shouldSkipNullData(){
        solution.getJobs().getLast().setStartProductionDateTime(null);
        cleaningService.alignCleanings(solution);

        assertNull(solution.getJobs().getLast().getDelayCleaningDuration());
    }

    @Test
    void alignCleanings_shouldSkipSmallCleaning(){
        solution.getJobs().getLast().setCameraStart(LocalDateTime.of(2026, 4, 24, 13, 30));
        cleaningService.alignCleanings(solution);

        assertNull(solution.getJobs().getLast().getDelayCleaningDuration());
    }

    private Job getTestJobFirst(){
        Job j1 = new Job();
        j1.setId("J1");
        j1.setLine(getTestLine());
        j1.setProduct(getTestProduct());

        j1.setStartProductionDateTime(LocalDateTime.of(2026, 4, 24, 10,0));
        j1.setStartCleaningDateTime(LocalDateTime.of(2026, 4, 24, 10,0));

        j1.setCameraStart(LocalDateTime.of(2026,4, 24, 12,0));
        j1.setCameraEnd(LocalDateTime.of(2026,4,24,13,0));

        return j1;
    }

    private Job getTestJobSecond(){
        Job j2 = new Job();
        j2.setId("J2");
        j2.setLine(getTestLine());
        j2.setProduct(getTestProduct());
        j2.getProduct().setId("P2");

        j2.setStartProductionDateTime(LocalDateTime.of(2026, 4, 24, 14,0));
        j2.setStartCleaningDateTime(LocalDateTime.of(2026, 4,24, 13, 30));

        j2.setCameraStart(LocalDateTime.of(2026,4, 24, 14,0));
        j2.setCameraEnd(LocalDateTime.of(2026,4,24,15,0));

        return j2;
    }

    private Product getTestProduct(){
        return new Product("P1", "Vanilla");
    }

    private Line getTestLine(){
        return new Line("L1", "line1");
    }
}
