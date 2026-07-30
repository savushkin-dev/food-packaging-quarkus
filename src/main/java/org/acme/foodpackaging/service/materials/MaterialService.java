package org.acme.foodpackaging.service.materials;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.acme.foodpackaging.dto.materials.*;
import org.acme.foodpackaging.entity.materials.*;
import org.acme.foodpackaging.repository.materials.*;

import java.time.LocalDate;
import java.util.*;
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
    ZinvRepository zinvRepository;

    @Inject
    MtRepository mtRepository;

    @Inject
    ZinvService zinvService;

    public List<ProductDto> getProductsByDate(String date) {
        return materialRepository.findProductsByDate(date);
    }

    // ===== ЗАГРУЗКА (БЕЗ СОХРАНЕНИЯ) =====
    public List<ProductWithMaterialsDto> loadProducts(String date, String kpp) {
        LocalDate dt = LocalDate.parse(date);

        List<ProductDto> products = materialRepository.findProductsByDate(date);

        PlrSprog plrSprog = getSprogByDate(date);
        Double sysn = plrSprog.getSysn();

        List<PlrSinv> existingPlrSinv = sinvRepository.findByDateAndKpp(dt, kpp);
        Map<String, Double> existingKolfMap = existingPlrSinv.stream()
                .collect(Collectors.toMap(
                        s -> s.getKmc() + "|" + s.getKt() + "|" + s.getKmt() + "|" + s.getNorm(),
                        PlrSinv::getKolf,
                        (v1, v2) -> v1
                ));

        List<ProductWithMaterialsDto> result = new ArrayList<>();

        for (ProductDto product : products) {
            List<PlrRnpp> materials = rnppRepository.findByKmcAndKtAndEmkAndSysn(
                    sysn,
                    product.getKmc(),
                    product.getKt(),
                    product.getEmk()
            );

            List<SinvDto> materialDtos = new ArrayList<>();

            for (PlrRnpp material : materials) {
                double normf = (product.getSumMass() / 1000) * material.getKol1t();

                String key = product.getKmc() + "|" + material.getKt() + "|" + material.getKkom() + "|" + material.getKol1t();
                Double kolf = existingKolfMap.getOrDefault(key, 0.0);

                PlrMt plrMt = mtRepository.findByKmt(material.getKkom()).orElse(null);
                String materialName = plrMt != null ? plrMt.getSnm() : null;
                String materialEdu = plrMt != null ? plrMt.getEdu() : null;

                SinvDto dto = SinvDto.builder()
                        .dt(dt)
                        .kpp(kpp)
                        .kmc(product.getKmc())
                        .kt(material.getKt())
                        .kmt(material.getKkom())
                        .snmMt(materialName)
                        .eduMt(materialEdu)
                        .norm(material.getKol1t())
                        .normf(normf)
                        .kolf(kolf)
                        .build();

                materialDtos.add(dto);
            }

            result.add(ProductWithMaterialsDto.builder()
                    .dt(dt)
                    .kpp(kpp)
                    .kmc(product.getKmc())
                    .kt(product.getKt())
                    .ean13(product.getEan13())
                    .emk(product.getEmk())
                    .name(product.getProductName())
                    .sumMass(product.getSumMass())
                    .materials(materialDtos)
                    .build());
        }

        calculateTotals(result);

        return result;
    }

    // ===== ПЕРЕСЧЕТ KOLF =====
    public List<ProductWithMaterialsDto> recalcKolf(KolfRecalcRequest request) {
        List<ProductWithMaterialsDto> data = request.getData();

        for (ProductWithMaterialsDto product : data) {
            for (SinvDto material : product.getMaterials()) {
                if (material.getKmt().equals(request.getKmt())) {
                    material.setKolf(request.getKolf());
                }
            }
        }

        calculateTotals(data);

        return data;
    }

    // ===== СОХРАНЕНИЕ =====
    @Transactional
    public void saveAll(SaveRequest request) {
        String date = request.getDate();
        String kpp = request.getKpp();
        List<ProductWithMaterialsDto> data = request.getData();

        LocalDate dt = LocalDate.parse(date);

        zinvRepository.deleteByDateAndKpp(dt, kpp);
        sinvRepository.deleteByDateAndKpp(dt, kpp);

        for (ProductWithMaterialsDto product : data) {
            PlrZinv plrZinv = PlrZinv.builder()
                    .dt(dt)
                    .kpp(kpp)
                    .kmc(product.getKmc())
                    .kt(product.getKt())
                    .ean13(product.getEan13())
                    .emk(product.getEmk())
                    .name(product.getName())
                    .sumMass(product.getSumMass())
                    .build();
            zinvService.save(plrZinv);
        }

        for (ProductWithMaterialsDto product : data) {
            for (SinvDto material : product.getMaterials()) {
                PlrSinv plrSinv = PlrSinv.builder()
                        .dt(dt)
                        .kpp(kpp)
                        .kmc(product.getKmc())
                        .kt(material.getKt())
                        .kmt(material.getKmt())
                        .norm(material.getNorm())
                        .normf(material.getNormf())
                        .kolf(material.getKolf())
                        .build();
                sinvRepository.saveOrUpdate(plrSinv);
            }
        }
    }

    // ===== РАСЧЕТ =====
    private void calculateTotals(List<ProductWithMaterialsDto> data) {
        // Группировка по kmt
        Map<String, List<SinvDto>> groupByKmt = new HashMap<>();

        for (ProductWithMaterialsDto product : data) {
            for (SinvDto material : product.getMaterials()) {
                groupByKmt.computeIfAbsent(material.getKmt(), k -> new ArrayList<>()).add(material);
            }
        }

        // Расчет productCount (сколько уникальных продуктов используют каждый материал)
        Map<String, Integer> productCountMap = new HashMap<>();
        for (ProductWithMaterialsDto product : data) {
            Set<String> uniqueKmtInProduct = product.getMaterials().stream()
                    .map(SinvDto::getKmt)
                    .collect(Collectors.toSet());
            for (String kmt : uniqueKmtInProduct) {
                productCountMap.put(kmt, productCountMap.getOrDefault(kmt, 0) + 1);
            }
        }

        // Расчет для каждого kmt
        for (Map.Entry<String, List<SinvDto>> entry : groupByKmt.entrySet()) {
            String kmt = entry.getKey();
            List<SinvDto> materials = entry.getValue();

            double totalNormf = materials.stream()
                    .mapToDouble(SinvDto::getNormf)
                    .sum();

            double insurancePerc = 0.0;
            double roundStep = 1.0;
            double kolf = 0.0;
            String snmMt = null;

            if (!materials.isEmpty()) {
                SinvDto first = materials.get(0);
                PlrMt plrMt = mtRepository.findByKmt(kmt).orElse(null);
                insurancePerc = plrMt != null && plrMt.getPers() != null ? plrMt.getPers() : 0.0;
                roundStep = plrMt != null && plrMt.getRnd() != null && plrMt.getRnd() > 0 ? plrMt.getRnd() : 1.0;
                kolf = first.getKolf() != null ? first.getKolf() : 0.0;
                snmMt = plrMt != null ? plrMt.getSnm() : null;

                for (SinvDto material : materials) {
                    material.setInsurancePerc(insurancePerc);
                    material.setRoundStep(roundStep);
                    material.setSnmMt(snmMt);
                    material.setProductCount(productCountMap.getOrDefault(kmt, 1));
                }
            }

            double deficit = totalNormf - kolf;
            if (deficit < 0) deficit = 0;

            double withInsurance = deficit * (1 + (insurancePerc / 100.0));
            double trnd = Math.ceil(withInsurance / roundStep) * roundStep;
            double order = trnd;

            for (SinvDto material : materials) {
                material.setTotalNormf(totalNormf);
                material.setTrnd(trnd);
                material.setOrder(order);
            }
        }
    }

    public PlrSprog getSprogByDate(String date) {
        return sprogRepository.findByDate(LocalDate.parse(date));
    }

    public List<PlrPp> getRecipients() {
        return ppRepository.findAll().list();
    }

    public List<PpDto> searchRecipients(String query) {
        List<PlrPp> entities = ppRepository.searchByName(query);
        return entities.stream()
                .map(this::PpToPpDto)
                .collect(Collectors.toList());
    }

    public PpDto PpToPpDto(PlrPp entity) {
        if (entity == null) {
            return null;
        }

        return PpDto.builder()
                .kpp(entity.kpp)
                .snm(entity.snm)
                .build();
    }

}