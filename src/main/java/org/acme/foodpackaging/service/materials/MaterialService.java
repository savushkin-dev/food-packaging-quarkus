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
import java.util.Map;
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
        sinvRepository.getEntityManager().flush();
        sinvRepository.getEntityManager().clear();

        // 5. Сохраняем материалы и собираем их в список
        List<Sinv> allSavedSinv = new ArrayList<>();

        for (Zinv product : zinvList) {
            List<Rnpp> materials = rnppRepository.findByKmcAndKtAndEmkAndSysn(
                    sysn,
                    product.getKmc(),
                    product.getKt(),
                    product.getEmk()
            );

            for (Rnpp material : materials) {
                BigDecimal normf = BigDecimal.valueOf(product.getSumMass())
                        .divide(BigDecimal.valueOf(1000))
                        .multiply(BigDecimal.valueOf(material.getKol1t()))
                        .setScale(2, RoundingMode.HALF_UP);

                Sinv sinv = Sinv.builder()
                        .dt(dt)
                        .kpp(kpp)
                        .kmc(product.getKmc())
                        .kt(material.getKt())
                        .kmt(material.getKkom())
                        .norm(material.getKol1t())
                        .normf(normf.doubleValue())
                        .kolf(0.0)
                        .build();

                sinvRepository.saveOrUpdate(sinv);
                allSavedSinv.add(sinv);
            }
        }

        // 6. РАССЧИТАТЬ totalNormf по всем материалам
        Map<String, Double> totalNormMap = allSavedSinv.stream()
                .collect(Collectors.groupingBy(
                        Sinv::getKmt,
                        Collectors.summingDouble(Sinv::getNormf)
                ));

        Map<String, Long> productCountMap = allSavedSinv.stream()
                .collect(Collectors.groupingBy(
                        Sinv::getKmt,
                        Collectors.mapping(Sinv::getKmc, Collectors.toSet())
                ))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> (long) e.getValue().size()
                ));

        // 7. Собрать результат
        List<ProductWithMaterialsDto> result = new ArrayList<>();

        for (Zinv product : zinvList) {
            List<SinvDto> materialDtos = new ArrayList<>();

            for (Sinv sinv : allSavedSinv) {
                if (sinv.getKmc().equals(product.getKmc()) && sinv.getKt().equals(product.getKt())) {
                    // Получаем Mt для страховки и округления
                    Mt mt = mtRepository.findByKmt(sinv.getKmt()).orElseThrow();

                    // ===== РАСЧЕТ TRND И ORDER (НОВАЯ ФОРМУЛА) =====
                    double totalNormf = totalNormMap.getOrDefault(sinv.getKmt(), 0.0);
                    double insurancePerc = mt.getPers() != null ? mt.getPers().doubleValue() : 0.0;
                    double roundStep = mt.getRnd() != null && mt.getRnd().doubleValue() > 0
                            ? mt.getRnd().doubleValue()
                            : 1.0;
                    double kolf = sinv.getKolf() != null ? sinv.getKolf() : 0.0;

                    // 1. Сначала вычитаем остаток (дефицит)
                    double deficit = totalNormf - kolf;
                    if (deficit < 0) {
                        deficit = 0;
                    }

                    // 2. Страховка только на дефицит
                    double withInsurance = deficit * (1 + (insurancePerc / 100.0));

                    // 3. Округление вверх до шага (минимальная единица заказа)
                    double trnd = Math.ceil(withInsurance / roundStep) * roundStep;

                    // 4. Итоговый дозаказ (KOLF уже вычтен)
                    double order = trnd;

                    SinvDto dto = SinvDto.builder()
                            .dt(sinv.getDt())
                            .kpp(sinv.getKpp())
                            .kmc(sinv.getKmc())
                            .kt(sinv.getKt())
                            .kmt(sinv.getKmt())
                            .snmMt(mt.getSnm())
                            .norm(sinv.getNorm())
                            .normf(sinv.getNormf())
                            .totalNormf(totalNormf)
                            .kolf(kolf)
                            .insurancePerc(insurancePerc)
                            .roundStep(roundStep)
                            .trnd(trnd)
                            .order(order)
                            .productCount(productCountMap.getOrDefault(sinv.getKmt(), 0L).intValue())
                            .build();

                    materialDtos.add(dto);
                }
            }

            ProductWithMaterialsDto productWithMaterials = ProductWithMaterialsDto.builder()
                    .dt(product.getDt())
                    .kpp(product.getKpp())
                    .kmc(product.getKmc())
                    .kt(product.getKt())
                    .ean13(product.getEan13())
                    .emk(product.getEmk())
                    .name(product.getName())
                    .sumMass(product.getSumMass())
                    .materials(materialDtos)
                    .build();

            result.add(productWithMaterials);
        }

        return result;
    }

    public SinvDto getUpdatedMaterial(String kmt, String date, String kpp) {
        LocalDate dt = LocalDate.parse(date);

        // 1. Найти SINV
        Sinv sinv = sinvRepository.findByKmtAndDateAndKpp(kmt, dt, kpp);
        if (sinv == null) {
            return null;
        }

        // 2. Получить все SINV для расчета totalNormf
        List<Sinv> allSinv = sinvRepository.findByDateAndKpp(dt, kpp);

        // 3. Рассчитать totalNormf
        Map<String, Double> totalNormMap = allSinv.stream()
                .collect(Collectors.groupingBy(
                        Sinv::getKmt,
                        Collectors.summingDouble(Sinv::getNormf)
                ));

        // 4. Рассчитать productCount
        Map<String, Long> productCountMap = allSinv.stream()
                .collect(Collectors.groupingBy(
                        Sinv::getKmt,
                        Collectors.mapping(Sinv::getKmc, Collectors.toSet())
                ))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> (long) e.getValue().size()
                ));

        // 5. Получить Mt
        Mt mt = mtRepository.findByKmt(sinv.getKmt()).orElse(null);

        // 6. Рассчитать TRND и ORDER
        double totalNormf = totalNormMap.getOrDefault(sinv.getKmt(), 0.0);
        double insurancePerc = mt != null && mt.getPers() != null ? mt.getPers().doubleValue() : 0.0;
        double roundStep = mt != null && mt.getRnd() != null && mt.getRnd().doubleValue() > 0
                ? mt.getRnd().doubleValue()
                : 1.0;
        double kolf = sinv.getKolf() != null ? sinv.getKolf() : 0.0;

        // Дефицит
        double deficit = totalNormf - kolf;
        if (deficit < 0) deficit = 0;

        // Страховка только на дефицит
        double withInsurance = deficit * (1 + (insurancePerc / 100.0));

        // Округление вверх до шага
        double trnd = Math.ceil(withInsurance / roundStep) * roundStep;

        // Итоговый дозаказ
        double order = trnd;

        // 7. Собрать DTO
        return SinvDto.builder()
                .dt(sinv.getDt())
                .kpp(sinv.getKpp())
                .kmc(sinv.getKmc())
                .kt(sinv.getKt())
                .kmt(sinv.getKmt())
                .snmMt(mt != null ? mt.getSnm() : null)
                .norm(sinv.getNorm())
                .normf(sinv.getNormf())
                .totalNormf(totalNormf)
                .kolf(kolf)
                .insurancePerc(insurancePerc)
                .roundStep(roundStep)
                .trnd(trnd)
                .order(order)
                .productCount(productCountMap.getOrDefault(sinv.getKmt(), 0L).intValue())
                .build();
    }

    @Transactional
    public void updateKolf(String kmt, Double kolf, String date, String kpp) {
        LocalDate dt = LocalDate.parse(date);
        sinvRepository.updateKolfByKmt(kmt, kolf, dt, kpp);
    }

    public Sprog getSprogByDate(String date) {
        return sprogRepository.findByDate(LocalDate.parse(date));
    }

    public List<Pp> getRecipients(){
        return ppRepository.findAll().list();
    }
}