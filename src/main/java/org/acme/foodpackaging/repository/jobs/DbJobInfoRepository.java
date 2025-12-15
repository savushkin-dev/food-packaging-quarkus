package org.acme.foodpackaging.repository.jobs;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.entity.jobs.PlrPdaynp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class DbJobInfoRepository implements PanacheRepository<PlrPdaynp> {

    public List<PlrPdaynp> findJobsForDay(LocalDate planningDay, String ksk) {

        LocalDateTime dayStart = planningDay.atStartOfDay();
        LocalDateTime nextDay = planningDay.plusDays(1).atStartOfDay();

        return find(
                "dtf >= ?1 AND dtf < ?2 AND ksk = ?3",
                dayStart, nextDay, ksk
        ).list();
    }
}
