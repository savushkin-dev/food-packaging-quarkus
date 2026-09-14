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

    private final EntityManager em;

    @Inject
    public SinvRepository(EntityManager em) {
        this.em = em;
    }

    public PlrSinv saveOrUpdate(PlrSinv plrSinv) {
        return em.merge(plrSinv);
    }

    public List<PlrSinv> findByDateAndKpp(LocalDate date, String kpp) {
        return find("dt = ?1 AND kpp = ?2", date, kpp).list();
    }

    public List<PlrSinv> findByDateAndKppAndKmc(LocalDate date, String kpp, String kmc) {
        return find("dt = ?1 AND kpp = ?2 AND kmc = ?3", date, kpp, kmc).list();
    }

    public void deleteByDateAndKpp(LocalDate date, String kpp) {
        delete("dt = ?1 AND kpp = ?2", date, kpp);
    }


}