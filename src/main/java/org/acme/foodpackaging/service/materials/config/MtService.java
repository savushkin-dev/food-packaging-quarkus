package org.acme.foodpackaging.service.materials.config;

import io.quarkus.cache.CacheInvalidate;
import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.cache.CacheKey;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.acme.foodpackaging.entity.materials.PlrMt;
import org.acme.foodpackaging.repository.materials.MtRepository;

import java.util.List;
import java.util.Map;
import java.util.Set;

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

    }

    /**
     * Инвалидирует кэш для конкретного материала
     */
    @CacheInvalidate(cacheName = "material-cache")
    public void invalidate(@CacheKey String kmt) {

    }

    /**
     * Загружает все материалы в Map по KMT (без кэша)
     */
    public Map<String, PlrMt> findAllAsMapByKmt() {
        return mtRepository.findAllAsMapByKmt();
    }

    /**
     * Массовая загрузка материалов по списку KMT
     */
    public List<PlrMt> getByKmtList(Set<String> kmtList) {
        if (kmtList == null || kmtList.isEmpty()) {
            return List.of();
        }
        return mtRepository.findByKmtIn(kmtList);
    }

    /**
     * Обновляет флаг IN_CALC для материала
     */
    @Transactional
    public void updateInCalc(String kmt, Boolean inCalc) {
        PlrMt mt = mtRepository.findByKmt(kmt)
                .orElseThrow(() -> new RuntimeException("Материал не найден: " + kmt));
        mt.inCalc = inCalc;
        invalidate(kmt);
    }
}