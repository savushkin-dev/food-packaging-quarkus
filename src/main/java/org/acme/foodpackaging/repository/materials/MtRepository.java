package org.acme.foodpackaging.repository.materials;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.entity.materials.Mt;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class MtRepository implements PanacheRepository<Mt> {

    public Optional<Mt> findByKgrAndKmt(String kgr, String kmt) {
        return find("kgr = ?1 and kmt = ?2", kgr, kmt).firstResultOptional();
    }

    public List<Mt> findByKgr(String kgr) {
        return find("kgr = ?1", kgr).list();
    }

    public Optional<Mt> findByKmt(String kmt) {
        return find("kmt = ?1", kmt).firstResultOptional();
    }

    public long deleteByKgr(String kgr) {
        return delete("kgr = ?1", kgr);
    }

    public long deleteByKmt(String kmt) {
        return delete("kmt = ?1", kmt);
    }
}