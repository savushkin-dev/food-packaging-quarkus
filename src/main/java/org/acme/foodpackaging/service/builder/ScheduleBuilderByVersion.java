package org.acme.foodpackaging.service.builder;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.persistence.json.SolutionImporter;
import org.acme.foodpackaging.record.SolutionByVersion;
import org.acme.foodpackaging.repository.solution.PlrPlanRepository;

import java.time.LocalDate;

@ApplicationScoped
public class ScheduleBuilderByVersion {
    @Inject
    public ScheduleBuilderByVersion(PlrPlanRepository plrPlanRepository, SolutionImporter importer){
        this.plrPlanRepository = plrPlanRepository;
        this.importer = importer;
    }

    private final PlrPlanRepository plrPlanRepository;
    private final SolutionImporter importer;

    public PackagingSchedule init(LocalDate dti, String version) {
        SolutionByVersion solution =
                plrPlanRepository.getSolutionByVersion(dti, version);

        return importer.importFromJson(solution);
    }
}
