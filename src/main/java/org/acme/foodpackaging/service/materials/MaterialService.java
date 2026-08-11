package org.acme.foodpackaging.service.materials;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.acme.foodpackaging.dto.materials.*;
import org.acme.foodpackaging.entity.materials.*;
import org.acme.foodpackaging.repository.materials.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис для работы с материалами и расчетами потребности
 */
@ApplicationScoped
public class MaterialService {


    private final MaterialRepository materialRepository;
    private final SprogService sprogService;
    private final RnppService rnppService;
    private final SinvRepository sinvRepository;
    private final ZinvRepository zinvRepository;
    private final MtService mtService;

    @Inject
    public MaterialService(MaterialRepository materialRepository, SprogService sprogService, RnppService rnppService
            , SinvRepository sinvRepository, ZinvRepository zinvRepository, MtService mtService) {
        this.materialRepository = materialRepository;
        this.sprogService = sprogService;
        this.rnppService = rnppService;
        this.sinvRepository = sinvRepository;
        this.zinvRepository = zinvRepository;
        this.mtService = mtService;
    }


    /**
     * Загружает данные для выбранной даты и МОЛ.
     * Если заказ уже был сохранен — берем из базы сохраненные значения.
     * Если заказ новый — рассчитываем все заново.
     */
    /**
     * Загружает данные для выбранной даты и МОЛ.
     */
    public List<ProductWithMaterialsDto> loadProducts(String date, String kpp) {
        LocalDate dt = LocalDate.parse(date);

        // 1. Получаем список продуктов на дату
        List<ProductDto> products = materialRepository.findProductsByDate(date);
        if (products.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. Получаем производственную программу
        PlrSprog plrSprog = sprogService.findByDate(LocalDate.parse(date));
        Double sysn = plrSprog.getSysn();

        // 3. Загружаем сохраненные данные
        List<PlrSinv> existingPlrSinv = sinvRepository.findByDateAndKpp(dt, kpp);
        Map<String, PlrSinv> existingDataMap = existingPlrSinv.stream()
                .collect(Collectors.toMap(
                        s -> s.getKmc() + "|" + s.getKt() + "|" + s.getKmt() + "|" + s.getNorm(),
                        s -> s,
                        (v1, v2) -> v1
                ));

        // 4. Загружаем кэш материалов
        Map<String, PlrMt> mtCache = loadMaterialCache(products, sysn);

        // 5. Собираем результат
        List<ProductWithMaterialsDto> result = buildResult(products, sysn, existingDataMap, mtCache, dt, kpp);

        // 6. Пересчет
        if (existingPlrSinv.isEmpty()) {
            calculateTotals(result);
        } else {
            fillAdditionalFields(result);
        }

        return result;
    }

    /**
     * Загружает кэш материалов
     */
    private Map<String, PlrMt> loadMaterialCache(List<ProductDto> products, Double sysn) {
        Map<String, PlrMt> mtCache = new HashMap<>();

        for (ProductDto product : products) {
            List<PlrRnpp> norms = rnppService.findByKmcAndKtAndEmkAndSysn(
                    sysn, product.getKmc(), product.getKt(), product.getEmk()
            );
            for (PlrRnpp norm : norms) {
                String kmt = norm.getKkom();
                if (!mtCache.containsKey(kmt)) {
                    PlrMt mt = mtService.getByKmt(kmt);
                    if (mt != null) {
                        mtCache.put(kmt, mt);
                    }
                }
            }
        }
        return mtCache;
    }

    /**
     * Собирает результат
     */
    private List<ProductWithMaterialsDto> buildResult(
            List<ProductDto> products,
            Double sysn,
            Map<String, PlrSinv> existingDataMap,
            Map<String, PlrMt> mtCache,
            LocalDate dt,
            String kpp
    ) {
        List<ProductWithMaterialsDto> result = new ArrayList<>();

        for (ProductDto product : products) {
            List<PlrRnpp> materials = rnppService.findByKmcAndKtAndEmkAndSysn(
                    sysn, product.getKmc(), product.getKt(), product.getEmk()
            );

            List<SinvDto> materialDtos = new ArrayList<>();

            for (PlrRnpp material : materials) {
                SinvDto dto = buildSinvDto(product, material, existingDataMap, mtCache, dt, kpp);
                materialDtos.add(dto);
            }

            result.add(buildProductDto(product, materialDtos, dt, kpp));
        }

        return result;
    }

    /**
     * Создает SinvDto
     */
    private SinvDto buildSinvDto(
            ProductDto product,
            PlrRnpp material,
            Map<String, PlrSinv> existingDataMap,
            Map<String, PlrMt> mtCache,
            LocalDate dt,
            String kpp
    ) {
        Double normf = BigDecimal.valueOf((product.getSumMass() / 1000) * material.getKol1t())
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();

        String key = product.getKmc() + "|" + material.getKt() + "|" + material.getKkom() + "|" + material.getKol1t();
        PlrSinv existing = existingDataMap.get(key);
        PlrMt plrMt = mtCache.get(material.getKkom());

        Double pers = existing != null ? existing.pers : null;
        if (pers == null && plrMt != null) {
            pers = plrMt.getPers() != null ? plrMt.getPers() : 0.0;
        }

        Double rnd = existing != null ? existing.rnd : null;
        if (rnd == null && plrMt != null) {
            rnd = plrMt.getRnd() != null && plrMt.getRnd() > 0 ? plrMt.getRnd() : 1.0;
        }

        return SinvDto.builder()
                .dt(dt)
                .kpp(kpp)
                .kmc(product.getKmc())
                .kt(material.getKt())
                .kmt(material.getKkom())
                .snmMt(plrMt != null ? plrMt.getSnm() : null)
                .eduMt(plrMt != null ? plrMt.getEdu() : null)
                .norm(material.getKol1t())
                .normf(normf)
                .kolf(existing != null ? existing.kolf : 0.0)
                .insurancePerc(pers)
                .roundStep(rnd)
                .order(existing != null ? existing.order : null)
                .build();
    }

    /**
     * Создает ProductWithMaterialsDto
     */
    private ProductWithMaterialsDto buildProductDto(
            ProductDto product,
            List<SinvDto> materialDtos,
            LocalDate dt,
            String kpp
    ) {
        return ProductWithMaterialsDto.builder()
                .dt(dt)
                .kpp(kpp)
                .kmc(product.getKmc())
                .kt(product.getKt())
                .ean13(product.getEan13())
                .emk(product.getEmk())
                .name(product.getProductName())
                .sumMass(product.getSumMass())
                .sumKolev(product.getSumKolev())
                .krkmc(product.getKrkmc())
                .materials(materialDtos)
                .build();
    }


    /**
     * Пересчитывает дозаказ после изменения остатка (KOLF)
     */
    public List<ProductWithMaterialsDto> recalcKolf(KolfRecalcRequest request) {
        List<ProductWithMaterialsDto> data = request.getData();

        // Обновляем остаток для всех продуктов с этим материалом
        for (ProductWithMaterialsDto product : data) {
            for (SinvDto material : product.getMaterials()) {
                if (material.getKmt().equals(request.getKmt())) {
                    material.setKolf(request.getKolf());
                    material.setOrder(null);
                }
            }
        }

        // Полностью пересчитываем все
        calculateTotals(data);

        return data;
    }


    /**
     * Сохраняет все данные в базу
     */
    @Transactional
    public void saveAll(SaveRequest request) {
        String date = request.getDate();
        String kpp = request.getKpp();
        List<ProductWithMaterialsDto> data = request.getData();

        LocalDate dt = LocalDate.parse(date);

        // Удаляем старые записи
        zinvRepository.deleteByDateAndKpp(dt, kpp);
        sinvRepository.deleteByDateAndKpp(dt, kpp);

        // Сохраняем продукты
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
                    .sumKolev(product.getSumKolev())
                    .build();
            zinvRepository.save(plrZinv);
        }

        // Сохраняем материалы со всеми расчетами
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
                        .pers(material.getInsurancePerc() != null ? material.getInsurancePerc() : 0.0)
                        .rnd(material.getRoundStep() != null && material.getRoundStep() > 0 ? material.getRoundStep() : 1.0)
                        .order(material.getOrder() != null ? material.getOrder() : 0.0)
                        .build();
                sinvRepository.saveOrUpdate(plrSinv);
            }
        }
    }

    /**
     * Полный пересчет всех полей.
     * Используется при первой загрузке или после изменения остатка
     *
     * Формула расчета:
     * 1. Норма со страховкой = totalNormf × (1 + insurancePerc / 100)
     * 2. Дефицит = Норма со страховкой - Остаток (если < 0 → 0)
     * 3. Заказ = Округлить_вверх(Дефицит / Шаг) × Шаг
     */
    private void calculateTotals(List<ProductWithMaterialsDto> data) {
        Map<String, List<SinvDto>> groupByKmt = groupByKmt(data);
        Map<String, Integer> productCountMap = countProductsPerMaterial(data);

        for (Map.Entry<String, List<SinvDto>> entry : groupByKmt.entrySet()) {
            String kmt = entry.getKey();
            List<SinvDto> materials = entry.getValue();

            double totalNormf = roundToTwo(materials.stream()
                    .mapToDouble(SinvDto::getNormf)
                    .sum());

            SinvDto first = materials.get(0);
            PlrMt plrMt = mtService.getByKmt(kmt);

            double insurancePerc = plrMt != null && plrMt.getPers() != null ? roundToTwo(plrMt.getPers()) : 0.0;
            double roundStep = plrMt != null && plrMt.getRnd() != null && plrMt.getRnd() > 0 ? roundToTwo(plrMt.getRnd()) : 1.0;
            double kolf = first.getKolf() != null ? roundToTwo(first.getKolf()) : 0.0;
            String snmMt = plrMt != null ? plrMt.getSnm() : null;

            double totalWithInsurance = roundToTwo(totalNormf * (1 + (insurancePerc / 100.0)));
            double deficit = roundToTwo(Math.max(0, totalWithInsurance - kolf));
            double order = roundToTwo(Math.ceil(deficit / roundStep) * roundStep);

            for (SinvDto material : materials) {
                material.setTotalNormf(totalNormf);
                material.setProductCount(productCountMap.getOrDefault(kmt, 1));
                material.setInsurancePerc(insurancePerc);
                material.setRoundStep(roundStep);
                material.setSnmMt(snmMt);
                material.setOrder(order);
            }
        }
    }


    /**
     * Дозаполняет поля для сохраненных заказов.
     */
    private void fillAdditionalFields(List<ProductWithMaterialsDto> data) {
        Map<String, List<SinvDto>> groupByKmt = groupByKmt(data);
        Map<String, Integer> productCountMap = countProductsPerMaterial(data);

        for (Map.Entry<String, List<SinvDto>> entry : groupByKmt.entrySet()) {
            String kmt = entry.getKey();
            List<SinvDto> materials = entry.getValue();

            double totalNormf = materials.stream()
                    .mapToDouble(SinvDto::getNormf)
                    .sum();

            SinvDto first = materials.get(0);
            String snmMt = first.getSnmMt();

            for (SinvDto material : materials) {
                material.setTotalNormf(totalNormf);
                material.setProductCount(productCountMap.getOrDefault(kmt, 1));
                material.setSnmMt(snmMt);
            }
        }
    }

    /**
     * Группирует материалы по коду материала (KMT)
     */
    private Map<String, List<SinvDto>> groupByKmt(List<ProductWithMaterialsDto> data) {
        Map<String, List<SinvDto>> groupByKmt = new HashMap<>();

        for (ProductWithMaterialsDto product : data) {
            for (SinvDto material : product.getMaterials()) {
                groupByKmt.computeIfAbsent(material.getKmt(), k -> new ArrayList<>()).add(material);
            }
        }

        return groupByKmt;
    }

    /**
     * Считает, в скольких продуктах используется каждый материал
     */
    private Map<String, Integer> countProductsPerMaterial(List<ProductWithMaterialsDto> data) {
        Map<String, Set<String>> productSetByKmt = new HashMap<>();

        for (ProductWithMaterialsDto product : data) {
            for (SinvDto material : product.getMaterials()) {
                productSetByKmt.computeIfAbsent(material.getKmt(), k -> new HashSet<>())
                        .add(product.getKmc());
            }
        }

        Map<String, Integer> productCountMap = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : productSetByKmt.entrySet()) {
            productCountMap.put(entry.getKey(), entry.getValue().size());
        }

        return productCountMap;
    }

    /**
     * Округляет число до 2 знаков после запятой
     */
    private double roundToTwo(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

}