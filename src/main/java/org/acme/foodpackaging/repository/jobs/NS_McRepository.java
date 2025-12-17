package org.acme.foodpackaging.repository.jobs;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.entity.NS_McEntity;

import java.util.Optional;

@ApplicationScoped
public class NS_McRepository implements PanacheRepository<NS_McEntity> {

    public Optional<NS_McEntity> findByKmc(String kmc) {
        return find("kmc", kmc).firstResultOptional();
    }
}
