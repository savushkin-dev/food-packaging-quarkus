package org.acme.foodpackaging.service.materials;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.acme.foodpackaging.dto.materials.SinvDto;
import org.acme.foodpackaging.dto.materials.ProductDto;
import org.acme.foodpackaging.dto.materials.ProductWithMaterialsDto;
import org.acme.foodpackaging.dto.materials.ZinvDto;
import org.acme.foodpackaging.entity.materials.*;
import org.acme.foodpackaging.repository.materials.*;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class MaterialService {

    @Inject
    MaterialRepository materialRepository;

    @Inject
    SprogRepository sprogRepository;

    @Inject
    PpRepository ppRepository;

    @Inject
    RnppRepository rnppRepository;

    @Inject
    SinvRepository sinvRepository;

    @Inject
    MtRepository mtRepository;

    @Inject
    ZinvService zinvService;

    public List<ProductDto> getProductsByDate(String date) {
        return materialRepository.findProductsByDate(date);
    }

    @Transactional
    public List<ProductWithMaterialsDto> loadProductsToZinv(String date, String kpp) {
        LocalDate dt = LocalDate.parse(date);

        // 1. Получить продукты из BD_VZPMC
        List<ProductDto> products = materialRepository.findProductsByDate(date);

        // 2. Сохранить в PLR_ZINV
        List<Zinv> zinvList = products.stream()
                .map(p -> Zinv.builder()
                        .dt(dt)
                        .kpp(kpp)
                        .kmc(p.getKmc())
                        .kt(p.getKt())
                        .ean13(p.getEan13())
                        .emk(p.getEmk())
                        .name(p.getProductName())
                        .sumMass(p.getSumMass())
                        .build())
                .collect(Collectors.toList());

        zinvService.saveAll(zinvList);

        // 3. Получить SYSN
        Sprog sprog = getSprogByDate(date);
        Double sysn = sprog.getSysn();

        // 4. Удалить старые SINV
        sinvRepository.deleteByDateAndKpp(dt, kpp);


        // 5. Собрать продукты с материалами
        List<ProductWithMaterialsDto> result = new ArrayList<>();

        for (Zinv product : zinvList) {
            // Загружаем материалы для продукта
            List<Rnpp> materials = rnppRepository.findByKmcAndEmkAndSysn(
                    sysn,
                    product.getKmc(),
                    product.getKt(),
                    product.getEmk()
            );

            // Сохраняем в SINV и собираем DTO
            List<SinvDto> sinvDtos = new ArrayList<>();

            for (Rnpp material : materials) {

                BigDecimal normf = BigDecimal.valueOf(product.getSumMass())
                        .divide(BigDecimal.valueOf(1000))
                        .multiply(BigDecimal.valueOf(material.getKol1t()))
                        .setScale(2, RoundingMode.HALF_UP);

                Sinv sinv = Sinv.builder()
                        .dt(dt)
                        .kpp(kpp)
                        .kmc(product.getKmc())
                        .kmt(material.getKkom())
                        .kt(material.getKt())
                        .norm(material.getKol1t())
                        .normf(normf.doubleValue())
                        .kolf(0.0)
                        .build();

                sinvRepository.saveOrUpdate(sinv);

                sinvDtos.add(SinvDto.builder()
                        .dt(sinv.getDt())
                        .kpp(sinv.getKpp())
                        .kmc(sinv.getKmc())
                        .kt(sinv.getKt())
                        .kmt(sinv.getKmt())
                        .snmMt(mtRepository.findByKmt(sinv.getKmt()).get().getSnm())
                        .norm(sinv.getNorm())
                        .normf(sinv.getNormf())
                        .kolf(sinv.getKolf())
                        .build());
            }

            // Собираем продукт с материалами
            ProductWithMaterialsDto productWithMaterials = ProductWithMaterialsDto.builder()
                    .dt(product.getDt())
                    .kpp(product.getKpp())
                    .kmc(product.getKmc())
                    .kt(product.getKt())
                    .ean13(product.getEan13())
                    .emk(product.getEmk())
                    .name(product.getName())
                    .sumMass(product.getSumMass())
                    .materials(sinvDtos)
                    .build();

            result.add(productWithMaterials);
        }

        return result;
    }

    public Sprog getSprogByDate(String date) {
        return sprogRepository.findByDate(LocalDate.parse(date));
    }

    public List<Pp> getRecipients(){
        return ppRepository.findAll().list();
    }
}