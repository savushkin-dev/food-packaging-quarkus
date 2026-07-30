package org.acme.foodpackaging.service.materials;

import io.quarkus.cache.CacheInvalidate;
import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.entity.materials.PlrMt;
import org.acme.foodpackaging.repository.materials.MtRepository;

@ApplicationScoped
public class MtService {

    @Inject
    MtRepository mtRepository;

    /**
     * Получить материал по KMT с кэшированием.
     * Первый запрос — в БД, последующие — из кэша.
     */
    @CacheResult(cacheName = "material-cache")
    public PlrMt getByKmt(String kmt) {
        return mtRepository.findByKmt(kmt).orElse(null);
    }

    /**
     * Очистить кэш для конкретного KMT.
     * Использовать при обновлении материала.
     */
    @CacheInvalidate(cacheName = "material-cache")
    public void invalidate(String kmt) {
        // метод пустой, аннотация делает всю работу
    }

    /**
     * Очистить весь кэш материалов.
     * Использовать при массовом обновлении.
     */
    @CacheInvalidateAll(cacheName = "material-cache")
    public void invalidateAll() {
        // метод пустой, аннотация делает всю работу
    }
}