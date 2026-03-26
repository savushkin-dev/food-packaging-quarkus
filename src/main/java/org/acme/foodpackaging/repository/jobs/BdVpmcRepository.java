package org.acme.foodpackaging.repository.jobs;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.entity.jobs.BdVzpmc;

import java.time.LocalDateTime;

@ApplicationScoped
public class BdVpmcRepository implements PanacheRepository<BdVzpmc> {

    public void updateBySnpz(Long snpz,
                             LocalDateTime startProductionDateTime,
                             LocalDateTime endDateTime,
                             Integer duration,
                             String lineId, Boolean isHandPackaging) {
        update("startProductionDateTime = ?1, endDateTime = ?2, duration = ?3, lineId = ?4, isHandPackaging =?5 where snpz = ?6",
                startProductionDateTime, endDateTime, duration, lineId, snpz);
    }

    public BdVzpmc findBySnpz(Long snpz) {
        return find("snpz", snpz).firstResult();
    }
}
