package org.acme.foodpackaging.repository.materials;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.entity.materials.Pp;

import java.util.Optional;

@ApplicationScoped
public class PpRepository implements PanacheRepository<Pp> {

    public Optional<Pp> findByKpp(String kpp) {
        return find("kpp = ?1", kpp).firstResultOptional();
    }

    public long deleteByKpp(String kpp) {
        return delete("kpp = ?1", kpp);
    }
}