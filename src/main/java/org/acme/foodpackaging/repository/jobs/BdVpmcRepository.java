package org.acme.foodpackaging.repository.jobs;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.entity.jobs.BdVzpmc;

import java.time.LocalDateTime;

@ApplicationScoped
public class BdVpmcRepository implements PanacheRepository<BdVzpmc> {

    public void updateBySnpz(Long snpz,
                             LocalDateTime startProductionDateTime,
                             LocalDateTime enDateTime,
                             Integer duration,
                             String lineId) {
        update("startProductionDateTime = ?1, enDateTime = ?2, duration = ?3, lineId = ?4 where snpz = ?5",
                startProductionDateTime, enDateTime, duration, lineId, snpz);
    }

    public BdVzpmc findBySnpz(long snpz) {
        return find("snpz", snpz).firstResult();
    }
}
