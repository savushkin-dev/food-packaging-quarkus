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

    public void deleteByDateAndKppAndType(LocalDate date, String kpp, String type) {
        em.createQuery("DELETE FROM PlrZinv z WHERE z.dt = :dt AND z.kpp = :kpp AND z.type = :type")
                .setParameter("dt", date)
                .setParameter("kpp", kpp)
                .setParameter("type", type)
                .executeUpdate();
    }
}