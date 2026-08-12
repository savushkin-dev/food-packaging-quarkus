package org.acme.foodpackaging.service.materials;

import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.entity.materials.PlrSprog;
import org.acme.foodpackaging.repository.materials.SprogRepository;

import java.time.LocalDate;

@ApplicationScoped
public class SprogService {

    private final SprogRepository sprogRepository;

    @Inject
    public SprogService(SprogRepository sprogRepository) {
        this.sprogRepository = sprogRepository;
    }

    @CacheResult(cacheName = "sprog-cache")
    public PlrSprog findByDate(LocalDate date) {
        return sprogRepository.findByDate(date);
    }

    @CacheInvalidateAll(cacheName = "sprog-cache")
    public void invalidateAll() {
        // Очищает весь кэш программ
    }

    public void deleteAll(){
        invalidateAll();
        sprogRepository.deleteAll();
    }
}