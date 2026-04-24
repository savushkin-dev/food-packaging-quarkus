package align;

import org.acme.foodpackaging.domain.*;
import org.acme.foodpackaging.scheduleoperations.MaintenanceJob;
import org.acme.foodpackaging.service.align.AlignByLastChainService;
import org.acme.foodpackaging.service.align.AlignCleaningService;
import org.acme.foodpackaging.service.align.AlignDurationService;
import org.acme.foodpackaging.service.align.AlignSolutionService;
import org.acme.foodpackaging.service.lines.LineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlignSolutionServiceTest {

    @Mock
    MaintenanceJob maintenanceJob;
    @Mock
    LineService lineService;
    @Mock
    AlignDurationService alignDuration;
    @Mock
    AlignByLastChainService alignLastChain;
    @Mock
    AlignCleaningService cleaningService;

    private AlignSolutionService alignSolution;

    @BeforeEach
    void setUp() {
        alignSolution = new AlignSolutionService(
                alignDuration,
                alignLastChain,
                cleaningService,
                lineService
        );
    }

    @Test
    void shouldCallAllServicesInOrder() {
        PackagingSchedule schedule = new PackagingSchedule();
        alignSolution.align(schedule);

        verify(alignDuration).alignByFactDuration(schedule);
        verify(alignLastChain).alignLineStartByFact(schedule);
        verify(cleaningService).alignCleanings(schedule);
        verify(lineService).setMaxEndDateTimeByLastJob(schedule);
    }
}