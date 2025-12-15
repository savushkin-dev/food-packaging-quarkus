package org.acme.foodpackaging.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.entity.jobs.VzPMCEntity;

import java.time.LocalDateTime;

@ApplicationScoped
public class VzPMCRepository implements PanacheRepository<VzPMCEntity> {

    public void updateBySnpz(Integer snpz,
                             LocalDateTime pdtn,
                             LocalDateTime pdto,
                             Integer pdur,
                             String krc) {
        update("pdtn = ?1, pdto = ?2, pdur = ?3, krc = ?4 where snpz = ?5",
                pdtn, pdto, pdur, krc, snpz);
    }

    public VzPMCEntity findBySnpz(int snpz) {
        return find("snpz", snpz).firstResult();
    }


}
