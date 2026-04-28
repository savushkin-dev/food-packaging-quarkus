package org.acme.foodpackaging.service.align;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.service.lines.LineService;

@ApplicationScoped
public class AlignSolutionService {

    private final AlignDurationService durationService;
    private final AlignByLastChainService lastChainService;
    private final AlignCleaningService cleaningService;
    private final LineService lineService;

    public AlignSolutionService(AlignDurationService durationService,
                                AlignByLastChainService lastChainService,
                                AlignCleaningService cleaningService, LineService lineService) {
        this.durationService = durationService;
        this.lastChainService = lastChainService;
        this.cleaningService = cleaningService;
        this.lineService = lineService;
    }

    public void align(PackagingSchedule schedule) {
        durationService.alignByFactDuration(schedule);
        cleaningService.alignCleanings(schedule);
        lastChainService.alignLineStartByFact(schedule);
        lineService.setMaxEndDateTimeByLastJob(schedule);
    }
}
