package org.acme.foodpackaging.service.materials;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.acme.foodpackaging.dto.materials.ZinvDto;
import org.acme.foodpackaging.entity.materials.Zinv;
import org.acme.foodpackaging.repository.materials.ZinvRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class ZinvService {

    @Inject
    ZinvRepository zinvRepository;

    @Transactional
    public void save(Zinv zinv) {
        zinvRepository.save(zinv);
    }

    @Transactional
    public void saveAll(List<Zinv> entities) {
        zinvRepository.saveAll(entities);
    }

    public List<Zinv> findByDateAndKpp(LocalDate date, String kpp) {
        return new ArrayList<>(zinvRepository.findByDateAndKpp(date, kpp));
    }

    public Zinv findByDateAndKppAndKmc(LocalDate date, String kpp, String kmc) {
        return zinvRepository.findByDateAndKppAndKmc(date, kpp, kmc);
    }

    @Transactional
    public void deleteByDateAndKpp(LocalDate date, String kpp) {
        zinvRepository.deleteByDateAndKpp(date, kpp);
    }

    private ZinvDto toDto(Zinv entity) {
        return ZinvDto.builder()
                .dt(entity.getDt())
                .kpp(entity.getKpp())
                .kmc(entity.getKmc())
                .kt(entity.getKt())
                .emk(entity.getEmk())
                .ean13(entity.getEan13())
                .name(entity.getName())
                .sumMass(entity.getSumMass())
                .build();
    }

    private Zinv toEntity(ZinvDto dto) {
        return Zinv.builder()
                .dt(dto.getDt())
                .kpp(dto.getKpp())
                .kmc(dto.getKmc())
                .kt(dto.getKt())
                .emk(dto.getEmk())
                .ean13(dto.getEan13())
                .name(dto.getName())
                .sumMass(dto.getSumMass())
                .build();
    }
}