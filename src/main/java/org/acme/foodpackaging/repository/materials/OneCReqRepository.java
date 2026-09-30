package org.acme.foodpackaging.repository.materials;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.entity.materials.Plr1cReq;

import java.time.LocalDate;
import java.util.List;

@ApplicationScoped
public class OneCReqRepository implements PanacheRepository<Plr1cReq> {

    public List<Plr1cReq> findByDtAndKpp2AndType(LocalDate dt, String kpp2, String type) {
        return find("dt = ?1 AND kpp2 = ?2 AND type = ?3 ORDER BY sentAt DESC", dt, kpp2, type).list();
    }

}