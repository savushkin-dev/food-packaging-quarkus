package org.acme.foodpackaging.repository.materials;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.acme.foodpackaging.entity.materials.Zinv;

import java.time.LocalDate;
import java.util.List;

@ApplicationScoped
public class ZinvRepository {

    @Inject
    EntityManager em;

    public void save(Zinv zinv) {
        em.merge(zinv);
    }

    public void saveAll(List<Zinv> zinvList) {
        for (Zinv z : zinvList) {
            em.merge(z);
        }
    }

    public List<Zinv> findByDateAndKpp(LocalDate date, String kpp) {
        return em.createQuery(
                        "SELECT z FROM Zinv z WHERE z.dt = :dt AND z.kpp = :kpp",
                        Zinv.class
                )
                .setParameter("dt", date)
                .setParameter("kpp", kpp)
                .getResultList();
    }

    public Zinv findByDateAndKppAndKmc(LocalDate date, String kpp, String kmc) {
        List<Zinv> result = em.createQuery(
                        "SELECT z FROM Zinv z WHERE z.dt = :dt AND z.kpp = :kpp AND z.kmc = :kmc",
                        Zinv.class
                )
                .setParameter("dt", date)
                .setParameter("kpp", kpp)
                .setParameter("kmc", kmc)
                .getResultList();

        return result.isEmpty() ? null : result.get(0);
    }

    public void deleteByDateAndKpp(LocalDate date, String kpp) {
        em.createQuery("DELETE FROM Zinv z WHERE z.dt = :dt AND z.kpp = :kpp")
                .setParameter("dt", date)
                .setParameter("kpp", kpp)
                .executeUpdate();
    }
}