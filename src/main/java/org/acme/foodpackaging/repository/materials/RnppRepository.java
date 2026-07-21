package org.acme.foodpackaging.repository.materials;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.entity.materials.Rnpp;

@ApplicationScoped
public class RnppRepository implements PanacheRepository<Rnpp> {
}