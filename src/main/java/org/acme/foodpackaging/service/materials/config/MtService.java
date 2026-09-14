package org.acme.foodpackaging.service.materials.config;

import io.quarkus.cache.CacheInvalidate;
import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.cache.CacheKey;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.acme.foodpackaging.dto.materials.MaterialSettingDto;
import org.acme.foodpackaging.entity.materials.PlrMt;
import org.acme.foodpackaging.repository.materials.MtRepository;

import java.util.Map;

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
        // Сбрасывает весь кэш
    }

    /**
     * Инвалидирует кэш для конкретного материала
     */
    @CacheInvalidate(cacheName = "material-cache")
    public void invalidate(@CacheKey String kmt) {
        // Сбрасывает кэш по ключу
    }

    /**
     * Загружает все материалы в Map по KMT (без кэша)
     */
    public Map<String, PlrMt> findAllAsMapByKmt() {
        return mtRepository.findAllAsMapByKmt();
    }

    /**
     * Обновляет флаг IN_CALC для материала
     */
    @Transactional
    public void updateSettings(MaterialSettingDto materialSettingDto) {
        String kmt = materialSettingDto.getKmt();
        PlrMt mt = mtRepository.findByKmt(kmt)
                .orElseThrow(() -> new RuntimeException("Material not found: " + kmt));
        mt.pers = materialSettingDto.getPers();
        mt.rnd = materialSettingDto.getRnd();
        mt.inCalc = materialSettingDto.getInCalc();
        invalidate(kmt);
    }
}