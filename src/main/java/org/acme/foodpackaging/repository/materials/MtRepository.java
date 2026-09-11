package org.acme.foodpackaging.repository.materials;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.entity.materials.PlrMt;

import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class MtRepository implements PanacheRepository<PlrMt> {

    public Optional<PlrMt> findByKgrAndKmt(String kgr, String kmt) {
        return find("kgr = ?1 and kmt = ?2", kgr, kmt).firstResultOptional();
    }

    public List<PlrMt> findByKgr(String kgr) {
        return find("kgr = ?1", kgr).list();
    }

    public Optional<PlrMt> findByKmt(String kmt) {
        return find("kmt = ?1", kmt).firstResultOptional();
    }

    public long deleteByKgr(String kgr) {
        return delete("kgr = ?1", kgr);
    }

    public long deleteByKmt(String kmt) {
        return delete("kmt = ?1", kmt);
    }

    public List<PlrMt> findByKmtIn(Set<String> kmtList) {
        if (kmtList == null || kmtList.isEmpty()) {
            return Collections.emptyList();
        }
        return find("kmt IN ?1", kmtList).list();
    }

    /**
     * Загружает все материалы для импорта
     */
    public List<PlrMt> findAllForImport() {
        return find("ORDER BY kmt").list();
    }

    /**
     * Загружает все материалы в Map по KMT
     */
    public Map<String, PlrMt> findAllAsMapByKmt() {
        return find("ORDER BY kmt")
                .stream()
                .collect(Collectors.toMap(
                        PlrMt::getKmt,
                        m -> m,
                        (v1, v2) -> v1
                ));
    }
}