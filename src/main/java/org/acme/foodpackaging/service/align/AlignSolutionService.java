package org.acme.foodpackaging.service.align;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.service.lines.LineService;

@ApplicationScoped
public class AlignSolutionService {

    private final AlignDurationService durationService;
    private final AlignCleaningService cleaningService;
    private final LineService lineService;

    public AlignSolutionService(AlignDurationService durationService,
                                AlignCleaningService cleaningService, LineService lineService) {
        this.durationService = durationService;
        this.cleaningService = cleaningService;
        this.lineService = lineService;
    }

    public void align(PackagingSchedule schedule) {
        durationService.alignByFactDuration(schedule);
        cleaningService.alignCleanings(schedule);
        lineService.setMaxEndDateTimeByLastJob(schedule);
    }
}
