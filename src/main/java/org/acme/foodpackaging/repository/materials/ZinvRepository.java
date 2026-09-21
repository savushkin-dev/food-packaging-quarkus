package org.acme.foodpackaging.repository.materials;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.acme.foodpackaging.entity.materials.PlrZinv;

import java.time.LocalDate;

@ApplicationScoped
public class ZinvRepository implements PanacheRepository<PlrZinv> {

    private final EntityManager em;

    @Inject
    public ZinvRepository(EntityManager em) {
        this.em = em;
    }

    public PlrZinv save(PlrZinv plrZinv) {
        return em.merge(plrZinv);
    }

    public void deleteByDateAndKppAndType(LocalDate date, String kpp, String type) {
        delete("dt = ?1 AND kpp = ?2 AND type = ?3", date, kpp, type);
    }
}