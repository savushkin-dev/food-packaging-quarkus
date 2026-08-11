package org.acme.foodpackaging.repository.materials;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.acme.foodpackaging.entity.materials.PlrZinv;

import java.time.LocalDate;
import java.util.List;

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

    public void saveAll(List<PlrZinv> plrZinvList) {
        for (PlrZinv z : plrZinvList) {
            em.merge(z);
        }
    }

    public List<PlrZinv> findByDateAndKpp(LocalDate date, String kpp) {
        return em.createQuery(
                        "SELECT z FROM PlrZinv z WHERE z.dt = :dt AND z.kpp = :kpp",
                        PlrZinv.class
                )
                .setParameter("dt", date)
                .setParameter("kpp", kpp)
                .getResultList();
    }

    public PlrZinv findByDateAndKppAndKmc(LocalDate date, String kpp, String kmc) {
        List<PlrZinv> result = em.createQuery(
                        "SELECT z FROM PlrZinv z WHERE z.dt = :dt AND z.kpp = :kpp AND z.kmc = :kmc",
                        PlrZinv.class
                )
                .setParameter("dt", date)
                .setParameter("kpp", kpp)
                .setParameter("kmc", kmc)
                .getResultList();

        return result.isEmpty() ? null : result.get(0);
    }

    public void deleteByDateAndKpp(LocalDate date, String kpp) {
        em.createQuery("DELETE FROM PlrZinv z WHERE z.dt = :dt AND z.kpp = :kpp")
                .setParameter("dt", date)
                .setParameter("kpp", kpp)
                .executeUpdate();
    }
}