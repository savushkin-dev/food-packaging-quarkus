package org.acme.foodpackaging.service.materials;

import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.dto.materials.PpDto;
import org.acme.foodpackaging.entity.materials.PlrPp;
import org.acme.foodpackaging.repository.materials.PpRepository;

import java.util.List;

@ApplicationScoped
public class PpService {


    private final PpRepository ppRepository;

    @Inject
    public PpService(PpRepository ppRepository) {
        this.ppRepository = ppRepository;
    }

    @CacheResult(cacheName = "pp-search-cache")
    public List<PpDto> searchByName(String query) {
        List<PlrPp> entities = ppRepository.searchByName(query);
        return entities.stream()
                .map(this::toDto)
                .toList();
    }

    @CacheInvalidateAll(cacheName = "pp-search-cache")
    public void invalidateSearchCache() {
        // Очищает кэш поиска получателей
    }

    private PpDto toDto(PlrPp entity) {
        if (entity == null) {
            return null;
        }
        return PpDto.builder()
                .kpp(entity.kpp)
                .snm(entity.snm)
                .build();
    }
}