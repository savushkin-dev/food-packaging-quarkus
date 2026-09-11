package org.acme.foodpackaging.repository.materials;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.acme.foodpackaging.entity.materials.PlrZinv;

import java.time.LocalDate;

@ApplicationScoped
public class ZinvRepository {

    private final EntityManager em;

    @Inject
    public ZinvRepository(EntityManager em) {
        this.em = em;
    }

    public PlrZinv save(PlrZinv plrZinv) {
        return em.merge(plrZinv);
    }

    public void deleteByDateAndKpp(LocalDate date, String kpp) {
        em.createQuery("DELETE FROM PlrZinv z WHERE z.dt = :dt AND z.kpp = :kpp")
                .setParameter("dt", date)
                .setParameter("kpp", kpp)
                .executeUpdate();
    }
}