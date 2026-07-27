package org.acme.foodpackaging.repository.materials;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.entity.materials.Rnpp;

import java.util.List;

@ApplicationScoped
public class RnppRepository implements PanacheRepository<Rnpp> {

    public List<Rnpp> findByKmcAndKtAndEmkAndSysn(Double sysn, String kmc, String kt, Double emk) {
        return find(
                "sysn = ?1 AND kmc = ?2 AND kt = ?3 AND emk = ?4 AND kkom LIKE '1002%'",
                sysn, kmc, kt, emk
        ).list();
    }

}