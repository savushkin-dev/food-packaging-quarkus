package org.acme.foodpackaging.initializer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.service.solution.SolutionImporter;
import org.acme.foodpackaging.dto.row.solution.SolutionVersionRow;
import org.acme.foodpackaging.repository.solution.PlrPlanRepository;

import java.time.LocalDate;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ScheduleVersionInitializer {

    private final PlrPlanRepository plrPlanRepository;
    private final SolutionImporter importer;

    /**
     * Строит сохраненное ранее расписание из json.
     */
    public PackagingSchedule initSchedule(LocalDate dti, String version) {
        SolutionVersionRow solution = plrPlanRepository.getSolutionByVersion(dti, version);
        return importer.importFromJson(solution);
    }
}