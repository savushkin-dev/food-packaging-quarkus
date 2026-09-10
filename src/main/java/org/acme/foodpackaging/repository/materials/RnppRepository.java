package org.acme.foodpackaging.repository.materials;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.entity.materials.PlrRnpp;

import java.util.List;

@ApplicationScoped
public class RnppRepository implements PanacheRepository<PlrRnpp> {

    public List<PlrRnpp> findByKmcAndKtAndEmkAndSysn(Double sysn, String kmc, String kt, Double emk) {
        return find(
                "sysn = ?1 AND kmc = ?2 AND kt = ?3 AND emk = ?4 " +
                        "AND (kkom LIKE '1001%' OR kkom LIKE '1002%' OR kkom LIKE '1005%') " +
                        "AND EXISTS (SELECT 1 FROM PlrMt mt WHERE mt.kmt = kkom AND mt.inCalc = true)",
                sysn, kmc, kt, emk
        ).list();
    }

}