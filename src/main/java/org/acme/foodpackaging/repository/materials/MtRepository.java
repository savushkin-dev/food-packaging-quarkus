package org.acme.foodpackaging.repository.materials;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.entity.materials.PlrMt;

import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class MtRepository implements PanacheRepository<PlrMt> {

    public Optional<PlrMt> findByKmt(String kmt) {
        return find("kmt = ?1", kmt).firstResultOptional();
    }

    /**
     * Загружает все материалы в Map по KMT
     */
    public Map<String, PlrMt> findAllAsMapByKmt() {
        return find("FROM PlrMt")
                .stream()
                .collect(Collectors.toMap(
                        PlrMt::getKmt,
                        m -> m,
                        (v1, v2) -> v1
                ));
    }
}