package org.acme.foodpackaging.service.materials;

import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.entity.materials.PlrRnpp;
import org.acme.foodpackaging.repository.materials.RnppRepository;

import java.util.List;

@ApplicationScoped
public class RnppService {

    @Inject
    RnppRepository rnppRepository;

    @CacheResult(cacheName = "rnpp-cache")
    public List<PlrRnpp> findByKmcAndKtAndEmkAndSysn(Double sysn, String kmc, String kt, Double emk) {
        return rnppRepository.findByKmcAndKtAndEmkAndSysn(sysn, kmc, kt, emk);
    }

    @CacheInvalidateAll(cacheName = "rnpp-cache")
    public void invalidateAll() {
        // Очищает весь кэш норм
    }
}