package org.acme.foodpackaging.repository.jobs;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.entity.jobs.OeePev;


@ApplicationScoped
public class OeePevRepository implements PanacheRepository<OeePev> {


    public OeePev findByFId(Long fId) {
        return find("fId", fId).firstResult();
    }

    public OeePev findBySnpz(Long snpz) {
        return find("snpz", snpz).firstResult();
    }
}
