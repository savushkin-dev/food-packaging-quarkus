package org.acme.foodpackaging.service.materials;

import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.entity.materials.PlrMt;
import org.acme.foodpackaging.repository.materials.MtRepository;

@ApplicationScoped
public class MtService {


    private final MtRepository mtRepository;

    @Inject
    public MtService(MtRepository mtRepository) {
        this.mtRepository = mtRepository;
    }

    @CacheResult(cacheName = "material-cache")
    public PlrMt getByKmt(String kmt) {
        return mtRepository.findByKmt(kmt).orElse(null);
    }

    @CacheInvalidateAll(cacheName = "material-cache")
    public void invalidateAll() {
        // метод пустой, аннотация делает всю работу
    }
}