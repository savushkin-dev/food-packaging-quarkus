package org.acme.foodpackaging.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.entity.jobs.OeePevEntity;


@ApplicationScoped
public class OeePevRepository implements PanacheRepository<OeePevEntity> {


    public OeePevEntity findByFId(long fId) {
        return find("fId", fId).firstResult();
    }

    public OeePevEntity findBySnpz(long snpz) {
        return find("snpz", snpz).firstResult();
    }


}
