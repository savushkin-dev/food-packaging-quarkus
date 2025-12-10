package org.acme.foodpackaging.persistence.load;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.entity.jobs.JobEntity;

import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class JobDBLoader implements PanacheRepository<JobEntity> {

    public List<JobEntity> loadJobs(LocalDateTime date, String ksk, double maxMass) {

        return find("""
            dtf = ?1
            and ksk = ?2
            and mc.massa < ?3
            order by kmc, np
            """, date, ksk, maxMass)
                .list();
    }
}
