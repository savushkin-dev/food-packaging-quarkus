package org.acme.foodpackaging.repository.solution;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.entity.solution.PlrPlan;
import org.acme.foodpackaging.record.SolutionByVersion;

import java.time.LocalDate;

@ApplicationScoped
public class PlrPlanRepository implements PanacheRepository<PlrPlan> {
    public SolutionByVersion getSolutionByVersion(LocalDate dti, String version) {
        return find("dti = ?1 and version =?2",
                dti, version).project(SolutionByVersion.class).firstResult();
    }
}
