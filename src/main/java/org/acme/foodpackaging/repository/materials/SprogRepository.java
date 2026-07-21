package org.acme.foodpackaging.repository.materials;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.entity.materials.Sprog;

@ApplicationScoped
public class SprogRepository implements PanacheRepository<Sprog> {
}