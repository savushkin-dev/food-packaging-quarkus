package org.acme.foodpackaging.service.materials;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.acme.foodpackaging.dto.materials.ZinvDto;
import org.acme.foodpackaging.entity.materials.Zinv;
import org.acme.foodpackaging.repository.materials.ZinvRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class ZinvService {

    @Inject
    ZinvRepository zinvRepository;

    @Transactional
    public void save(ZinvDto dto) {
        Zinv zinv = toEntity(dto);
        zinvRepository.save(zinv);
    }

    @Transactional
    public void saveAll(List<ZinvDto> dtos) {
        List<Zinv> entities = dtos.stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
        zinvRepository.saveAll(entities);
    }

    public List<ZinvDto> findByDateAndKpp(LocalDate date, String kpp) {
        return zinvRepository.findByDateAndKpp(date, kpp).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public ZinvDto findByDateAndKppAndKmc(LocalDate date, String kpp, String kmc) {
        Zinv zinv = zinvRepository.findByDateAndKppAndKmc(date, kpp, kmc);
        return zinv != null ? toDto(zinv) : null;
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
                .ean13(dto.getEan13())
                .name(dto.getName())
                .sumMass(dto.getSumMass())
                .build();
    }
}