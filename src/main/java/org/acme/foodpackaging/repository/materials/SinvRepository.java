package org.acme.foodpackaging.repository.materials;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.acme.foodpackaging.entity.materials.PlrSinv;

import java.time.LocalDate;
import java.util.List;

@ApplicationScoped
public class SinvRepository implements PanacheRepository<PlrSinv> {

    @Inject
    EntityManager em;

    public List<PlrSinv> findByDateAndKpp(LocalDate date, String kpp) {
        return find("dt = ?1 AND kpp = ?2", date, kpp).list();
    }

    public List<PlrSinv> findByDateAndKppAndKmc(LocalDate date, String kpp, String kmc) {
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

    public PlrSinv findByKmtAndDateAndKpp(String kmt, LocalDate date, String kpp) {
        return find("kmt = ?1 AND dt = ?2 AND kpp = ?3", kmt, date, kpp)
                .firstResult();
    }

    public void saveOrUpdate(PlrSinv plrSinv) {
        em.merge(plrSinv);
    }

    public void saveAll(List<PlrSinv> plrSinvList) {
        for (PlrSinv plrSinv : plrSinvList) {
            em.merge(plrSinv);
        }
        em.flush();
        em.clear();
    }
}