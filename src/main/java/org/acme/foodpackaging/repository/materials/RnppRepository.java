package org.acme.foodpackaging.repository.materials;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.entity.materials.PlrRnpp;

import java.util.List;

@ApplicationScoped
public class RnppRepository implements PanacheRepository<PlrRnpp> {

    public List<PlrRnpp> findByKmcAndKtAndEmkAndSysn(Double sysn, String kmc, String kt, Double emk) {
        return find(
                "sysn = ?1 AND kmc = ?2 AND kt = ?3 AND emk = ?4 AND kkom LIKE '1002%'",
                sysn, kmc, kt, emk
        ).list();
    }

    // загружает ВСЕ материалы для SYSN за один запрос
    public List<PlrRnpp> findBySysn(Double sysn) {
        return find(
                "sysn = ?1 AND kkom LIKE '1002%'",
                sysn
        ).list();
    }

}