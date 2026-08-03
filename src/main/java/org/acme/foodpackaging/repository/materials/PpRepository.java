package org.acme.foodpackaging.repository.materials;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.entity.materials.PlrPp;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class PpRepository implements PanacheRepository<PlrPp> {

    public List<PlrPp> searchByName(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return find(
                "kpp LIKE '0102%' AND UPPER(snm) LIKE UPPER(?1)",
                "%" + query.trim() + "%"
        ).list();
    }
}