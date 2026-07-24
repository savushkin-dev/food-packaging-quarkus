package org.acme.foodpackaging.repository.materials;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.acme.foodpackaging.entity.materials.Sinv;

import java.time.LocalDate;
import java.util.List;

@ApplicationScoped
public class SinvRepository implements PanacheRepository<Sinv> {

    @Inject
    EntityManager em;

    public List<Sinv> findByDateAndKpp(LocalDate date, String kpp) {
        return find("dt = ?1 AND kpp = ?2", date, kpp).list();
    }

    public List<Sinv> findByDateAndKppAndKmc(LocalDate date, String kpp, String kmc) {
        return find("dt = ?1 AND kpp = ?2 AND kmc = ?3", date, kpp, kmc).list();
    }

    public void deleteByDateAndKpp(LocalDate date, String kpp) {
        delete("dt = ?1 AND kpp = ?2", date, kpp);
    }

    public void deleteByDateAndKppAndKmc(LocalDate date, String kpp, String kmc) {
        delete("dt = ?1 AND kpp = ?2 AND kmc = ?3", date, kpp, kmc);
    }

    public void updateKolfByKmt(String kmt, Double kolf, LocalDate date, String kpp) {
        update(
                "UPDATE Sinv s SET s.kolf = ?1 WHERE s.kmt = ?2 AND s.dt = ?3 AND s.kpp = ?4",
                kolf, kmt, date, kpp
        );
    }

    public void saveOrUpdate(Sinv sinv) {
        em.merge(sinv);
    }

    public void saveAll(List<Sinv> sinvList) {
        for (Sinv sinv : sinvList) {
            em.merge(sinv);
        }
        em.flush();
        em.clear();
    }
}