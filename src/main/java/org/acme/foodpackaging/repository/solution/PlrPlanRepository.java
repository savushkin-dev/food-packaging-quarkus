package org.acme.foodpackaging.repository.solution;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.entity.solution.PlrPlan;
import org.acme.foodpackaging.record.SolutionByVersion;

import java.time.LocalDate;
import java.util.List;

@ApplicationScoped
public class PlrPlanRepository implements PanacheRepository<PlrPlan> {
    public SolutionByVersion getSolutionByVersion(LocalDate dti, String version) {
        return find("dti = ?1 and version =?2",
                dti, version).project(SolutionByVersion.class).firstResult();
    }

    public PlrPlan findByDateAndVersion(LocalDate dti, String version) {
        if (version == null) {
            return null;
        }

        return getEntityManager()
                .createQuery(
                        "select p from PlrPlan p " +
                                "where p.dti = :dti and trim(p.version) = :version",
                        PlrPlan.class
                )
                .setParameter("dti", dti)
                .setParameter("version", version.trim())
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    public List<String> findDistinctVersionsByDate(LocalDate dti) {
        return getEntityManager()
                .createQuery(
                        "select distinct trim(p.version) from PlrPlan p " +
                                "where p.dti = :dti " +
                                "and p.version is not null " +
                                "and trim(p.version) <> '' " +
                                "order by trim(p.version)",
                        String.class
                )
                .setParameter("dti", dti)
                .getResultList();
    }
}
