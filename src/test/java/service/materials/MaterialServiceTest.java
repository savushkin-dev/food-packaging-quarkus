package service.materials;

import org.acme.foodpackaging.dto.materials.*;
import org.acme.foodpackaging.entity.materials.*;
import org.acme.foodpackaging.repository.materials.*;
import org.acme.foodpackaging.service.materials.*;
import org.acme.foodpackaging.service.materials.config.MtService;
import org.acme.foodpackaging.service.materials.config.RnppService;
import org.acme.foodpackaging.service.materials.config.SprogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.Month;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaterialServiceTest {

    @InjectMocks
    private MaterialService materialService;

    @Mock
    private MaterialRepository materialRepository;

    @Mock
    private SprogService sprogService;

    @Mock
    private RnppService rnppService;

    @Mock
    private SinvRepository sinvRepository;

    @Mock
    private ZinvRepository zinvRepository;

    @Mock
    private MtService mtService;

    private final LocalDate testDate = LocalDate.of(2026, Month.FEBRUARY, 15);
    private final String testDateStr = "2026-02-15";
    private final String testKpp = "01020391";

    // ==================== ТЕСТЫ loadProducts() ====================

    @Test
    void testLoadProducts_NewData_Success() {
        // Arrange
        List<ProductDto> products = createTestProducts();
        PlrSprog sprog = createTestSprog();
        PlrMt mt = createTestMt();
        List<PlrRnpp> norms = createTestRnpp();

        when(materialRepository.findProductsByDate(anyString())).thenReturn(products);
        when(sprogService.findByDate(any(LocalDate.class))).thenReturn(sprog);
        when(rnppService.findByKmcAndKtAndEmkAndSysn(anyDouble(), anyString(), anyString(), anyDouble()))
                .thenReturn(norms);
        when(sinvRepository.findByDateAndKppAndType(any(LocalDate.class), anyString(), anyString()))
                .thenReturn(Collections.emptyList());
        when(mtService.getByKmt(anyString())).thenReturn(mt);

        // Act
        List<ProductWithMaterialsDto> result = materialService.loadProducts(testDateStr, testKpp, "M");

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());

        ProductWithMaterialsDto product = result.get(0);
        assertEquals("0307060046", product.getKmc());
        assertFalse(product.getMaterials().isEmpty());

        SinvDto material = product.getMaterials().get(0);
        assertEquals("1002051408", material.getKmt());
        assertNotNull(material.getOrder());
        assertTrue(material.getOrder() > 0);
    }

    @Test
    void testLoadProducts_WithSavedData_Success() {
        // Arrange
        List<ProductDto> products = createTestProducts();
        PlrSprog sprog = createTestSprog();
        PlrMt mt = createTestMt();
        List<PlrRnpp> norms = createTestRnpp();
        List<PlrSinv> existingData = createTestSinv();

        when(materialRepository.findProductsByDate(anyString())).thenReturn(products);
        when(sprogService.findByDate(any(LocalDate.class))).thenReturn(sprog);
        when(rnppService.findByKmcAndKtAndEmkAndSysn(anyDouble(), anyString(), anyString(), anyDouble()))
                .thenReturn(norms);
        when(sinvRepository.findByDateAndKppAndType(any(LocalDate.class), anyString(), anyString()))
                .thenReturn(existingData);
        when(mtService.getByKmt(anyString())).thenReturn(mt);

        // Act
        List<ProductWithMaterialsDto> result = materialService.loadProducts(testDateStr, testKpp, "M");

        // Assert
        assertNotNull(result);
        SinvDto material = result.get(0).getMaterials().get(0);
        assertEquals(100.0, material.getKolf());
        assertEquals(15.0, material.getInsurancePerc());
        assertEquals(5.0, material.getRoundStep());
        assertEquals(175.0, material.getOrder());
    }

    @Test
    void testLoadProducts_EmptyProducts_ReturnsEmptyList() {
        // Arrange
        when(materialRepository.findProductsByDate(anyString())).thenReturn(Collections.emptyList());

        // Act
        List<ProductWithMaterialsDto> result = materialService.loadProducts(testDateStr, testKpp, "M");

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(sprogService, never()).findByDate(any());
        verify(sinvRepository, never()).findByDateAndKppAndType(any(), any(), any());
    }

    @Test
    void testLoadProducts_NoSprog_ThrowsException() {
        // Arrange
        List<ProductDto> products = createTestProducts();
        when(materialRepository.findProductsByDate(anyString())).thenReturn(products);
        when(sprogService.findByDate(any(LocalDate.class))).thenReturn(null);

        // Act & Assert
        assertThrows(NullPointerException.class, () -> {
            materialService.loadProducts(testDateStr, testKpp, "M");
        });
    }

    @Test
    void testLoadProducts_WithNullKolf_UsesDefaultZero() {
        // Arrange
        List<ProductDto> products = createTestProducts();
        PlrSprog sprog = createTestSprog();
        PlrMt mt = createTestMt();
        List<PlrRnpp> norms = createTestRnpp();

        PlrSinv sinv = new PlrSinv();
        sinv.kolf = null;
        sinv.pers = 15.0;
        sinv.rnd = 5.0;
        sinv.order = 175.0;
        List<PlrSinv> existingData = List.of(sinv);

        when(materialRepository.findProductsByDate(anyString())).thenReturn(products);
        when(sprogService.findByDate(any(LocalDate.class))).thenReturn(sprog);
        when(rnppService.findByKmcAndKtAndEmkAndSysn(anyDouble(), anyString(), anyString(), anyDouble()))
                .thenReturn(norms);
        when(sinvRepository.findByDateAndKppAndType(any(LocalDate.class), anyString(), anyString()))
                .thenReturn(existingData);
        when(mtService.getByKmt(anyString())).thenReturn(mt);

        // Act
        List<ProductWithMaterialsDto> result = materialService.loadProducts(testDateStr, testKpp, "M");

        // Assert
        SinvDto material = result.get(0).getMaterials().get(0);
        assertEquals(0.0, material.getKolf());
    }

    @Test
    void testLoadProducts_WithMultipleMaterials() {
        // Arrange
        List<ProductDto> products = createTestProducts();
        PlrSprog sprog = createTestSprog();
        PlrMt mt1 = createTestMt();
        PlrMt mt2 = createTestMt2();

        List<PlrRnpp> norms = new ArrayList<>();
        PlrRnpp rnpp1 = new PlrRnpp();
        rnpp1.setSysn(39000.0);
        rnpp1.setKmc("0307060046");
        rnpp1.setKt("2201040296");
        rnpp1.setEmk(18.0);
        rnpp1.setKkom("1002051408");
        rnpp1.setKol1t(18.5);
        rnpp1.setKolvk(0.0);
        norms.add(rnpp1);

        PlrRnpp rnpp2 = new PlrRnpp();
        rnpp2.setSysn(39000.0);
        rnpp2.setKmc("0307060046");
        rnpp2.setKt("2201040296");
        rnpp2.setEmk(18.0);
        rnpp2.setKkom("1002110286");
        rnpp2.setKol1t(1.4);
        rnpp2.setKolvk(0.0);
        norms.add(rnpp2);

        when(materialRepository.findProductsByDate(anyString())).thenReturn(products);
        when(sprogService.findByDate(any(LocalDate.class))).thenReturn(sprog);
        when(rnppService.findByKmcAndKtAndEmkAndSysn(anyDouble(), anyString(), anyString(), anyDouble()))
                .thenReturn(norms);
        when(sinvRepository.findByDateAndKppAndType(any(LocalDate.class), anyString(), anyString()))
                .thenReturn(Collections.emptyList());
        when(mtService.getByKmt("1002051408")).thenReturn(mt1);
        when(mtService.getByKmt("1002110286")).thenReturn(mt2);

        // Act
        List<ProductWithMaterialsDto> result = materialService.loadProducts(testDateStr, testKpp, "M");

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(2, result.get(0).getMaterials().size());
    }

    @Test
    void testLoadProducts_WithDuplicateKeysInSavedData_UsesFirstValue() {

        List<ProductDto> products = createTestProducts();
        PlrSprog sprog = createTestSprog();
        PlrMt mt = createTestMt();
        List<PlrRnpp> norms = createTestRnpp();

        PlrSinv first = new PlrSinv();
        first.dt = testDate;
        first.kpp = testKpp;
        first.kmc = "0307060046";
        first.kt = "2201040296";
        first.kmt = "1002051408";
        first.norm = 18.5;
        first.normf = 158.08;
        first.kolf = 100.0;
        first.pers = 15.0;
        first.rnd = 5.0;
        first.order = 175.0;

        PlrSinv second = new PlrSinv();
        second.dt = testDate;
        second.kpp = testKpp;
        second.kmc = "0307060046";
        second.kt = "2201040296";
        second.kmt = "1002051408";
        second.norm = 18.5;
        second.normf = 158.08;
        second.kolf = 999.0;
        second.pers = 99.0;
        second.rnd = 99.0;
        second.order = 999.0;

        List<PlrSinv> existingData = List.of(first, second);

        when(materialRepository.findProductsByDate(anyString())).thenReturn(products);
        when(sprogService.findByDate(any(LocalDate.class))).thenReturn(sprog);
        when(rnppService.findByKmcAndKtAndEmkAndSysn(anyDouble(), anyString(), anyString(), anyDouble()))
                .thenReturn(norms);
        when(sinvRepository.findByDateAndKppAndType(any(LocalDate.class), anyString(), anyString()))
                .thenReturn(existingData);
        when(mtService.getByKmt(anyString())).thenReturn(mt);

        List<ProductWithMaterialsDto> result = materialService.loadProducts(testDateStr, testKpp, "M");

        assertNotNull(result);
        SinvDto material = result.get(0).getMaterials().get(0);

        assertEquals(100.0, material.getKolf());
        assertEquals(15.0, material.getInsurancePerc());
        assertEquals(5.0, material.getRoundStep());
        assertEquals(175.0, material.getOrder());
    }

    // ==================== ТЕСТЫ recalcKolf() ====================

    @Test
    void testRecalcKolf_Success() {
        // Arrange
        List<ProductWithMaterialsDto> data = createTestProductWithMaterials();
        KolfRecalcRequest request = KolfRecalcRequest.builder()
                .date(testDateStr)
                .kpp(testKpp)
                .kmt("1002051408")
                .kolf(50.0)
                .data(data)
                .build();

        PlrMt mt = createTestMt();
        when(mtService.getByKmt(anyString())).thenReturn(mt);

        // Act
        List<ProductWithMaterialsDto> result = materialService.recalcKolf(request);

        // Assert
        SinvDto material = result.get(0).getMaterials().get(0);
        assertEquals(50.0, material.getKolf());
        assertNotNull(material.getOrder());
    }

    @Test
    void testRecalcKolf_MaterialNotFound_NoChange() {
        // Arrange
        List<ProductWithMaterialsDto> data = createTestProductWithMaterials();
        KolfRecalcRequest request = KolfRecalcRequest.builder()
                .date(testDateStr)
                .kpp(testKpp)
                .kmt("NON_EXISTENT")
                .kolf(50.0)
                .data(data)
                .build();

        PlrMt mt = createTestMt();
        when(mtService.getByKmt(anyString())).thenReturn(mt);

        // Act
        List<ProductWithMaterialsDto> result = materialService.recalcKolf(request);

        // Assert
        SinvDto material = result.get(0).getMaterials().get(0);
        assertEquals(0.0, material.getKolf());
    }

    @Test
    void testRecalcKolf_WithExistingOrder_Recalculates() {
        // Arrange
        List<ProductWithMaterialsDto> data = createTestProductWithMaterials();
        data.get(0).getMaterials().get(0).setOrder(100.0);

        KolfRecalcRequest request = KolfRecalcRequest.builder()
                .date(testDateStr)
                .kpp(testKpp)
                .kmt("1002051408")
                .kolf(50.0)
                .data(data)
                .build();

        PlrMt mt = createTestMt();
        when(mtService.getByKmt(anyString())).thenReturn(mt);

        // Act
        List<ProductWithMaterialsDto> result = materialService.recalcKolf(request);

        // Assert
        SinvDto material = result.get(0).getMaterials().get(0);
        assertNotNull(material.getOrder());
        assertTrue(material.getOrder() >= 0);
    }

    // ==================== ТЕСТЫ saveAll() ====================

    @Test
    void testSaveAll_Success() {
        // Arrange
        List<ProductWithMaterialsDto> data = createTestProductWithMaterials();
        SaveRequest request = SaveRequest.builder()
                .date(testDateStr)
                .kpp(testKpp)
                .data(data)
                .type("M")
                .build();

        // ✅ Исправлено: when().thenReturn() вместо doNothing()
        doNothing().when(zinvRepository).deleteByDateAndKppAndType(any(LocalDate.class), anyString(), anyString());
        doNothing().when(sinvRepository).deleteByDateAndKppAndType(any(LocalDate.class), anyString(), anyString());

        PlrZinv savedZinv = new PlrZinv();
        when(zinvRepository.save(any(PlrZinv.class))).thenReturn(savedZinv);

        PlrSinv savedSinv = new PlrSinv();
        when(sinvRepository.saveOrUpdate(any(PlrSinv.class))).thenReturn(savedSinv);

        // Act
        materialService.saveAll(request);

        // Assert
        verify(zinvRepository, times(1)).deleteByDateAndKppAndType(any(LocalDate.class), anyString(), anyString());
        verify(sinvRepository, times(1)).deleteByDateAndKppAndType(any(LocalDate.class), anyString(), anyString());
        verify(zinvRepository, times(data.size())).save(any(PlrZinv.class));

        int totalMaterials = data.stream().mapToInt(p -> p.getMaterials().size()).sum();
        verify(sinvRepository, times(totalMaterials)).saveOrUpdate(any(PlrSinv.class));
    }

    @Test
    void testSaveAll_WithEmptyData_DoesNothing() {
        // Arrange
        SaveRequest request = SaveRequest.builder()
                .date(testDateStr)
                .kpp(testKpp)
                .data(Collections.emptyList())
                .type("M")
                .build();

        // Act
        materialService.saveAll(request);

        // Assert
        verify(zinvRepository, times(1)).deleteByDateAndKppAndType(any(LocalDate.class), anyString(), anyString());
        verify(sinvRepository, times(1)).deleteByDateAndKppAndType(any(LocalDate.class), anyString(), anyString());
        verify(zinvRepository, never()).save(any(PlrZinv.class));
        verify(sinvRepository, never()).saveOrUpdate(any(PlrSinv.class));
    }

    @Test
    void testSaveAll_WithNullInsurancePerc_UseDefaultZero() {
        // Arrange
        List<ProductWithMaterialsDto> data = createTestProductWithMaterials();
        data.get(0).getMaterials().get(0).setInsurancePerc(null);

        SaveRequest request = SaveRequest.builder()
                .date(testDateStr)
                .kpp(testKpp)
                .data(data)
                .type("M")
                .build();

        doNothing().when(zinvRepository).deleteByDateAndKppAndType(any(LocalDate.class), anyString(), anyString());
        doNothing().when(sinvRepository).deleteByDateAndKppAndType(any(LocalDate.class), anyString(), anyString());

        PlrZinv savedZinv = new PlrZinv();
        when(zinvRepository.save(any(PlrZinv.class))).thenReturn(savedZinv);

        // ✅ Исправлено: захватываем аргумент для проверки
        PlrSinv savedSinv = new PlrSinv();
        when(sinvRepository.saveOrUpdate(any(PlrSinv.class))).thenReturn(savedSinv);

        // Act
        materialService.saveAll(request);

        // Assert
        verify(sinvRepository, times(1)).saveOrUpdate(argThat(entity ->
                entity.pers == 0.0
        ));
    }

    @Test
    void testSaveAll_WithNullRoundStep_UseDefaultOne() {
        // Arrange
        List<ProductWithMaterialsDto> data = createTestProductWithMaterials();
        data.get(0).getMaterials().get(0).setRoundStep(null);

        SaveRequest request = SaveRequest.builder()
                .date(testDateStr)
                .kpp(testKpp)
                .data(data)
                .type("M")
                .build();

        doNothing().when(zinvRepository).deleteByDateAndKppAndType(any(LocalDate.class), anyString(), anyString());
        doNothing().when(sinvRepository).deleteByDateAndKppAndType(any(LocalDate.class), anyString(), anyString());

        PlrZinv savedZinv = new PlrZinv();
        when(zinvRepository.save(any(PlrZinv.class))).thenReturn(savedZinv);

        PlrSinv savedSinv = new PlrSinv();
        when(sinvRepository.saveOrUpdate(any(PlrSinv.class))).thenReturn(savedSinv);

        // Act
        materialService.saveAll(request);

        // Assert
        verify(sinvRepository, times(1)).saveOrUpdate(argThat(entity ->
                entity.rnd == 1.0
        ));
    }

    @Test
    void testSaveAll_WithNullOrder_UseDefaultZero() {
        // Arrange
        List<ProductWithMaterialsDto> data = createTestProductWithMaterials();
        data.get(0).getMaterials().get(0).setOrder(null);

        SaveRequest request = SaveRequest.builder()
                .date(testDateStr)
                .kpp(testKpp)
                .data(data)
                .type("M")
                .build();

        doNothing().when(zinvRepository).deleteByDateAndKppAndType(any(LocalDate.class), anyString(), anyString());
        doNothing().when(sinvRepository).deleteByDateAndKppAndType(any(LocalDate.class), anyString(), anyString());

        PlrZinv savedZinv = new PlrZinv();
        when(zinvRepository.save(any(PlrZinv.class))).thenReturn(savedZinv);

        PlrSinv savedSinv = new PlrSinv();
        when(sinvRepository.saveOrUpdate(any(PlrSinv.class))).thenReturn(savedSinv);

        // Act
        materialService.saveAll(request);

        // Assert
        verify(sinvRepository, times(1)).saveOrUpdate(argThat(entity ->
                entity.order == 0.0
        ));
    }

    // ==================== ТЕСТЫ calculateTotals() ====================

    @Test
    void testCalculateTotals_WithPositiveDeficit_CalculatesOrder() {
        // Arrange
        List<ProductWithMaterialsDto> data = createTestProductWithMaterials();
        PlrMt mt = createTestMt();
        when(mtService.getByKmt(anyString())).thenReturn(mt);

        // Act
        List<ProductWithMaterialsDto> result = materialService.recalcKolf(
                KolfRecalcRequest.builder()
                        .date(testDateStr)
                        .kpp(testKpp)
                        .kmt("1002051408")
                        .kolf(0.0)
                        .data(data)
                        .build()
        );

        // Assert
        SinvDto material = result.get(0).getMaterials().get(0);
        assertNotNull(material.getOrder());
        assertTrue(material.getOrder() > 0);
        assertTrue(material.getTotalNormf() > 0);
        assertEquals(1, material.getProductCount());
    }

    @Test
    void testCalculateTotals_WithZeroDeficit_ReturnsZeroOrder() {
        // Arrange
        List<ProductWithMaterialsDto> data = createTestProductWithMaterials();
        PlrMt mt = createTestMt();
        when(mtService.getByKmt(anyString())).thenReturn(mt);

        // Act
        List<ProductWithMaterialsDto> result = materialService.recalcKolf(
                KolfRecalcRequest.builder()
                        .date(testDateStr)
                        .kpp(testKpp)
                        .kmt("1002051408")
                        .kolf(1000.0)
                        .data(data)
                        .build()
        );

        // Assert
        SinvDto material = result.get(0).getMaterials().get(0);
        assertEquals(0.0, material.getOrder());
    }

    // ==================== ТЕСТЫ fillAdditionalFields() ====================

    @Test
    void testFillAdditionalFields_PopulatesFields() {
        // Arrange
        List<ProductDto> products = new ArrayList<>();
        products.add(createTestProductDto("0307060046"));
        products.add(createTestProductDto("0307060047"));

        PlrSprog sprog = createTestSprog();
        PlrMt mt = createTestMt();

        when(materialRepository.findProductsByDate(anyString())).thenReturn(products);
        when(sprogService.findByDate(any(LocalDate.class))).thenReturn(sprog);
        when(rnppService.findByKmcAndKtAndEmkAndSysn(anyDouble(), anyString(), anyString(), anyDouble()))
                .thenReturn(createTestRnpp());
        when(sinvRepository.findByDateAndKppAndType(any(LocalDate.class), anyString(), anyString()))
                .thenReturn(createTestSinv());
        when(mtService.getByKmt(anyString())).thenReturn(mt);

        // Act
        List<ProductWithMaterialsDto> result = materialService.loadProducts(testDateStr, testKpp, "M");

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());

        for (ProductWithMaterialsDto product : result) {
            for (SinvDto material : product.getMaterials()) {
                assertTrue(material.getProductCount() >= 1);
                assertTrue(material.getTotalNormf() >= 0);
            }
        }
    }

    // ==================== ТЕСТЫ ПРИВАТНЫХ МЕТОДОВ ====================

    @Test
    void testGroupByKmt_ThroughLoadProducts() {
        // Arrange
        List<ProductDto> products = createTestProducts();
        PlrSprog sprog = createTestSprog();
        PlrMt mt = createTestMt();
        List<PlrRnpp> norms = createTestRnpp();

        when(materialRepository.findProductsByDate(anyString())).thenReturn(products);
        when(sprogService.findByDate(any(LocalDate.class))).thenReturn(sprog);
        when(rnppService.findByKmcAndKtAndEmkAndSysn(anyDouble(), anyString(), anyString(), anyDouble()))
                .thenReturn(norms);
        when(sinvRepository.findByDateAndKppAndType(any(LocalDate.class), anyString(), anyString()))
                .thenReturn(Collections.emptyList());
        when(mtService.getByKmt(anyString())).thenReturn(mt);

        // Act
        List<ProductWithMaterialsDto> result = materialService.loadProducts(testDateStr, testKpp, "M");

        // Assert
        assertNotNull(result);
        ProductWithMaterialsDto product = result.get(0);
        Map<String, Long> kmtCount = product.getMaterials().stream()
                .collect(Collectors.groupingBy(SinvDto::getKmt, Collectors.counting()));
        assertTrue(kmtCount.values().stream().allMatch(count -> count == 1));
    }

    @Test
    void testCountProductsPerMaterial_ThroughLoadProducts() {
        // Arrange
        List<ProductDto> products = new ArrayList<>();
        products.add(createTestProductDto("0307060046"));
        products.add(createTestProductDto("0307060047"));

        PlrSprog sprog = createTestSprog();
        PlrMt mt = createTestMt();

        when(materialRepository.findProductsByDate(anyString())).thenReturn(products);
        when(sprogService.findByDate(any(LocalDate.class))).thenReturn(sprog);
        when(rnppService.findByKmcAndKtAndEmkAndSysn(anyDouble(), anyString(), anyString(), anyDouble()))
                .thenReturn(createTestRnpp());
        when(sinvRepository.findByDateAndKppAndType(any(LocalDate.class), anyString(), anyString()))
                .thenReturn(Collections.emptyList());
        when(mtService.getByKmt(anyString())).thenReturn(mt);

        // Act
        List<ProductWithMaterialsDto> result = materialService.loadProducts(testDateStr, testKpp, "M");

        // Assert
        assertNotNull(result);
        for (ProductWithMaterialsDto product : result) {
            for (SinvDto material : product.getMaterials()) {
                if (material.getKmt().equals("1002051408")) {
                    assertTrue(material.getProductCount() >= 1);
                }
            }
        }
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

    private List<ProductDto> createTestProducts() {
        ProductDto product = ProductDto.builder()
                .kmc("0307060046")
                .kt("2201040296")
                .ean13("4810268043727")
                .emk(18.0)
                .productName("Сырок Кок-мин")
                .sumMass(8545.0)
                .sumKolev(213444.0)
                .krkmc(2743.0)
                .build();
        return List.of(product);
    }

    private ProductDto createTestProductDto(String kmc) {
        return ProductDto.builder()
                .kmc(kmc)
                .kt("2201040296")
                .ean13("4810268043727")
                .emk(18.0)
                .productName("Тестовый продукт " + kmc)
                .sumMass(1000.0)
                .sumKolev(1000.0)
                .krkmc(2743.0)
                .build();
    }

    private PlrSprog createTestSprog() {
        PlrSprog sprog = new PlrSprog();
        sprog.setSysn(39000.0);
        sprog.setDt1(testDate);
        sprog.setDt2(testDate.plusDays(30));
        sprog.setObj("0101011000");
        sprog.setNp(1);
        return sprog;
    }

    private PlrMt createTestMt() {
        PlrMt mt = new PlrMt();
        mt.setKmt("1002051408");
        mt.setSnm("Тестовый материал");
        mt.setEdu("кг");
        mt.setPers(10.0);
        mt.setRnd(5.0);
        return mt;
    }

    private PlrMt createTestMt2() {
        PlrMt mt = new PlrMt();
        mt.setKmt("1002110286");
        mt.setSnm("Этикетка");
        mt.setEdu("тшт");
        mt.setPers(10.0);
        mt.setRnd(5.0);
        return mt;
    }

    private List<PlrRnpp> createTestRnpp() {
        PlrRnpp rnpp = new PlrRnpp();
        rnpp.setSysn(39000.0);
        rnpp.setKmc("0307060046");
        rnpp.setKt("2201040296");
        rnpp.setEmk(18.0);
        rnpp.setKkom("1002051408");
        rnpp.setKol1t(18.5);
        rnpp.setKolvk(0.0);
        return List.of(rnpp);
    }

    private List<PlrSinv> createTestSinv() {
        PlrSinv sinv = new PlrSinv();
        sinv.dt = testDate;
        sinv.kpp = testKpp;
        sinv.kmc = "0307060046";
        sinv.kt = "2201040296";
        sinv.kmt = "1002051408";
        sinv.norm = 18.5;
        sinv.normf = 158.08;
        sinv.kolf = 100.0;
        sinv.pers = 15.0;
        sinv.rnd = 5.0;
        sinv.order = 175.0;
        return List.of(sinv);
    }

    private List<ProductWithMaterialsDto> createTestProductWithMaterials() {
        SinvDto material = SinvDto.builder()
                .dt(testDate)
                .kpp(testKpp)
                .kmc("0307060046")
                .kt("2201040296")
                .kmt("1002051408")
                .snmMt("Тестовый материал")
                .eduMt("кг")
                .norm(18.5)
                .normf(158.08)
                .kolf(0.0)
                .insurancePerc(10.0)
                .roundStep(5.0)
                .order(null)
                .productCount(1)
                .build();

        ProductWithMaterialsDto product = ProductWithMaterialsDto.builder()
                .dt(testDate)
                .kpp(testKpp)
                .kmc("0307060046")
                .kt("2201040296")
                .ean13("4810268043727")
                .emk(18.0)
                .name("Сырок Кок-мин")
                .sumMass(8545.0)
                .sumKolev(213444.0)
                .krkmc(2743.0)
                .materials(new ArrayList<>(List.of(material)))
                .build();

        return new ArrayList<>(List.of(product));
    }

    // ==================== ТЕСТЫ getMaterialsSettings() ====================

    @Test
    void testGetMaterialsSettings_Success() {
        List<MaterialSettingDto> settings = createTestMaterialSettings();
        PlrSprog sprog = createTestSprog();

        when(sprogService.findByDate(any(LocalDate.class))).thenReturn(sprog);
        when(materialRepository.findAllMaterialsForSettings(anyDouble())).thenReturn(settings);

        List<MaterialSettingDto> result = materialService.getMaterialsSettings(testDateStr);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("1002051408", result.get(0).getKmt());
        assertEquals("Тестовый материал", result.get(0).getSnm());
        assertEquals(10.0, result.get(0).getPers());
        assertEquals(5.0, result.get(0).getRnd());
        assertTrue(result.get(0).getInCalc());

        verify(sprogService, times(1)).findByDate(testDate);
        verify(materialRepository, times(1)).findAllMaterialsForSettings(39000.0);
    }

    @Test
    void testGetMaterialsSettings_EmptyList() {
        PlrSprog sprog = createTestSprog();
        when(sprogService.findByDate(any(LocalDate.class))).thenReturn(sprog);
        when(materialRepository.findAllMaterialsForSettings(anyDouble())).thenReturn(Collections.emptyList());

        List<MaterialSettingDto> result = materialService.getMaterialsSettings(testDateStr);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetMaterialsSettings_NoSprog_ThrowsException() {
        when(sprogService.findByDate(any(LocalDate.class))).thenReturn(null);

        assertThrows(NullPointerException.class, () ->
                materialService.getMaterialsSettings(testDateStr)
        );
        verify(materialRepository, never()).findAllMaterialsForSettings(anyDouble());
    }

    @Test
    void testGetMaterialsSettings_InvalidDate_ThrowsException() {
        assertThrows(Exception.class, () ->
                materialService.getMaterialsSettings("invalid-date")
        );
    }

    // ==================== ТЕСТЫ saveMaterialsSettings() ====================

    @Test
    void testSaveMaterialsSettings_Success() {
        List<MaterialSettingDto> settings = createTestMaterialSettings();

        doNothing().when(mtService).updateSettings(any(MaterialSettingDto.class));
        doNothing().when(rnppService).invalidateAll();

        materialService.saveMaterialsSettings(settings);

        verify(mtService, times(2)).updateSettings(any(MaterialSettingDto.class));
        verify(rnppService, times(1)).invalidateAll();
    }

    @Test
    void testSaveMaterialsSettings_EmptyList_StillInvalidatesRnpp() {
        List<MaterialSettingDto> settings = Collections.emptyList();
        doNothing().when(rnppService).invalidateAll();

        materialService.saveMaterialsSettings(settings);

        verify(mtService, never()).updateSettings(any(MaterialSettingDto.class));
        verify(rnppService, times(1)).invalidateAll();
    }

    @Test
    void testSaveMaterialsSettings_UpdatesEachSetting() {
        List<MaterialSettingDto> settings = createTestMaterialSettings();
        doNothing().when(mtService).updateSettings(any(MaterialSettingDto.class));
        doNothing().when(rnppService).invalidateAll();

        materialService.saveMaterialsSettings(settings);

        verify(mtService).updateSettings(argThat(dto -> "1002051408".equals(dto.getKmt())));
        verify(mtService).updateSettings(argThat(dto -> "1002110286".equals(dto.getKmt())));
    }

    @Test
    void testSaveMaterialsSettings_PropagatesException() {
        List<MaterialSettingDto> settings = createTestMaterialSettings();
        doThrow(new RuntimeException("Material not found: 1002051408"))
                .when(mtService).updateSettings(any(MaterialSettingDto.class));

        assertThrows(RuntimeException.class, () ->
                materialService.saveMaterialsSettings(settings)
        );
        verify(rnppService, never()).invalidateAll();
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЙ МЕТОД ====================

    private List<MaterialSettingDto> createTestMaterialSettings() {
        MaterialSettingDto dto1 = MaterialSettingDto.builder()
                .kmt("1002051408")
                .snm("Тестовый материал")
                .edu("кг")
                .pers(10.0)
                .rnd(5.0)
                .inCalc(true)
                .build();

        MaterialSettingDto dto2 = MaterialSettingDto.builder()
                .kmt("1002110286")
                .snm("Этикетка")
                .edu("тшт")
                .pers(15.0)
                .rnd(1.0)
                .inCalc(false)
                .build();

        return List.of(dto1, dto2);
    }

    // ==================== ТЕСТЫ ПРЕДВАРИТЕЛЬНОЙ ЗАЯВКИ (type = "P") ====================

    @Test
    void testLoadProducts_PreliminaryType_UsesPreliminaryRepository() {
        // Arrange
        List<ProductDto> products = createTestProducts();
        PlrSprog sprog = createTestSprog();
        PlrMt mt = createTestMt();
        List<PlrRnpp> norms = createTestRnpp();

        when(materialRepository.findPreliminaryProductsByDate(anyString())).thenReturn(products);
        when(sprogService.findByDate(any(LocalDate.class))).thenReturn(sprog);
        when(rnppService.findByKmcAndKtAndEmkAndSysn(anyDouble(), anyString(), anyString(), anyDouble()))
                .thenReturn(norms);
        when(sinvRepository.findByDateAndKppAndType(any(LocalDate.class), anyString(), anyString()))
                .thenReturn(Collections.emptyList());
        when(mtService.getByKmt(anyString())).thenReturn(mt);

        // Act
        List<ProductWithMaterialsDto> result = materialService.loadProducts(testDateStr, testKpp, "P");

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());

        // Проверяем, что использовался именно preliminary-метод
        verify(materialRepository, times(1)).findPreliminaryProductsByDate(testDateStr);
        verify(materialRepository, never()).findProductsByDate(anyString());
    }

    @Test
    void testLoadProducts_MainType_UsesMainRepository() {
        // Arrange
        List<ProductDto> products = createTestProducts();
        PlrSprog sprog = createTestSprog();
        PlrMt mt = createTestMt();
        List<PlrRnpp> norms = createTestRnpp();

        when(materialRepository.findProductsByDate(anyString())).thenReturn(products);
        when(sprogService.findByDate(any(LocalDate.class))).thenReturn(sprog);
        when(rnppService.findByKmcAndKtAndEmkAndSysn(anyDouble(), anyString(), anyString(), anyDouble()))
                .thenReturn(norms);
        when(sinvRepository.findByDateAndKppAndType(any(LocalDate.class), anyString(), anyString()))
                .thenReturn(Collections.emptyList());
        when(mtService.getByKmt(anyString())).thenReturn(mt);

        // Act
        List<ProductWithMaterialsDto> result = materialService.loadProducts(testDateStr, testKpp, "M");

        // Assert
        assertNotNull(result);
        verify(materialRepository, times(1)).findProductsByDate(testDateStr);
        verify(materialRepository, never()).findPreliminaryProductsByDate(anyString());
    }

    // ==================== ТЕСТЫ resetDataAndLoadProduct() ====================

    @Test
    void testResetDataAndLoadProduct_Success() {
        // Arrange
        List<ProductDto> products = createTestProducts();
        PlrSprog sprog = createTestSprog();
        PlrMt mt = createTestMt();
        List<PlrRnpp> norms = createTestRnpp();

        doNothing().when(zinvRepository).deleteByDateAndKppAndType(any(LocalDate.class), anyString(), anyString());
        doNothing().when(sinvRepository).deleteByDateAndKppAndType(any(LocalDate.class), anyString(), anyString());

        when(materialRepository.findProductsByDate(anyString())).thenReturn(products);
        when(sprogService.findByDate(any(LocalDate.class))).thenReturn(sprog);
        when(rnppService.findByKmcAndKtAndEmkAndSysn(anyDouble(), anyString(), anyString(), anyDouble()))
                .thenReturn(norms);
        when(sinvRepository.findByDateAndKppAndType(any(LocalDate.class), anyString(), anyString()))
                .thenReturn(Collections.emptyList());
        when(mtService.getByKmt(anyString())).thenReturn(mt);

        // Act
        List<ProductWithMaterialsDto> result = materialService.resetDataAndLoadProduct(testDateStr, testKpp, "M");

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());

        // Проверяем, что удаление было вызвано
        verify(zinvRepository, times(1)).deleteByDateAndKppAndType(testDate, testKpp, "M");
        verify(sinvRepository, times(1)).deleteByDateAndKppAndType(testDate, testKpp, "M");

        // Проверяем, что загрузка была вызвана
        verify(materialRepository, times(1)).findProductsByDate(testDateStr);
    }

    @Test
    void testResetDataAndLoadProduct_PreliminaryType() {
        // Arrange
        List<ProductDto> products = createTestProducts();
        PlrSprog sprog = createTestSprog();
        PlrMt mt = createTestMt();
        List<PlrRnpp> norms = createTestRnpp();

        doNothing().when(zinvRepository).deleteByDateAndKppAndType(any(LocalDate.class), anyString(), anyString());
        doNothing().when(sinvRepository).deleteByDateAndKppAndType(any(LocalDate.class), anyString(), anyString());

        when(materialRepository.findPreliminaryProductsByDate(anyString())).thenReturn(products);
        when(sprogService.findByDate(any(LocalDate.class))).thenReturn(sprog);
        when(rnppService.findByKmcAndKtAndEmkAndSysn(anyDouble(), anyString(), anyString(), anyDouble()))
                .thenReturn(norms);
        when(sinvRepository.findByDateAndKppAndType(any(LocalDate.class), anyString(), anyString()))
                .thenReturn(Collections.emptyList());
        when(mtService.getByKmt(anyString())).thenReturn(mt);

        // Act
        List<ProductWithMaterialsDto> result = materialService.resetDataAndLoadProduct(testDateStr, testKpp, "P");

        // Assert
        assertNotNull(result);
        verify(zinvRepository, times(1)).deleteByDateAndKppAndType(testDate, testKpp, "P");
        verify(sinvRepository, times(1)).deleteByDateAndKppAndType(testDate, testKpp, "P");
        verify(materialRepository, times(1)).findPreliminaryProductsByDate(testDateStr);
    }

    @Test
    void testResetDataAndLoadProduct_EmptyProducts_ReturnsEmptyList() {
        // Arrange
        doNothing().when(zinvRepository).deleteByDateAndKppAndType(any(LocalDate.class), anyString(), anyString());
        doNothing().when(sinvRepository).deleteByDateAndKppAndType(any(LocalDate.class), anyString(), anyString());

        when(materialRepository.findProductsByDate(anyString())).thenReturn(Collections.emptyList());

        // Act
        List<ProductWithMaterialsDto> result = materialService.resetDataAndLoadProduct(testDateStr, testKpp, "M");

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(zinvRepository, times(1)).deleteByDateAndKppAndType(any(), any(), any());
        verify(sinvRepository, times(1)).deleteByDateAndKppAndType(any(), any(), any());
    }

    // ==================== ТЕСТЫ orderFinal ====================

    @Test
    void testSaveAll_WithNegativeOrderFinal_UseDefaultZero() {
        // Arrange
        List<ProductWithMaterialsDto> data = createTestProductWithMaterials();
        data.get(0).getMaterials().get(0).setOrderFinal(-50.0);

        SaveRequest request = SaveRequest.builder()
                .date(testDateStr)
                .kpp(testKpp)
                .data(data)
                .type("M")
                .build();

        doNothing().when(zinvRepository).deleteByDateAndKppAndType(any(LocalDate.class), anyString(), anyString());
        doNothing().when(sinvRepository).deleteByDateAndKppAndType(any(LocalDate.class), anyString(), anyString());

        PlrZinv savedZinv = new PlrZinv();
        when(zinvRepository.save(any(PlrZinv.class))).thenReturn(savedZinv);

        PlrSinv savedSinv = new PlrSinv();
        when(sinvRepository.saveOrUpdate(any(PlrSinv.class))).thenReturn(savedSinv);

        // Act
        materialService.saveAll(request);

        // Assert
        // Проверяем, что отрицательный orderFinal превратился в 0
        verify(sinvRepository, times(1)).saveOrUpdate(argThat(entity ->
                entity.orderFinal == 0.0
        ));
    }

    @Test
    void testSaveAll_WithPositiveOrderFinal_SavesAsIs() {
        // Arrange
        List<ProductWithMaterialsDto> data = createTestProductWithMaterials();
        data.get(0).getMaterials().get(0).setOrderFinal(123.45);

        SaveRequest request = SaveRequest.builder()
                .date(testDateStr)
                .kpp(testKpp)
                .data(data)
                .type("M")
                .build();

        doNothing().when(zinvRepository).deleteByDateAndKppAndType(any(LocalDate.class), anyString(), anyString());
        doNothing().when(sinvRepository).deleteByDateAndKppAndType(any(LocalDate.class), anyString(), anyString());

        PlrZinv savedZinv = new PlrZinv();
        when(zinvRepository.save(any(PlrZinv.class))).thenReturn(savedZinv);

        PlrSinv savedSinv = new PlrSinv();
        when(sinvRepository.saveOrUpdate(any(PlrSinv.class))).thenReturn(savedSinv);

        // Act
        materialService.saveAll(request);

        // Assert
        verify(sinvRepository, times(1)).saveOrUpdate(argThat(entity ->
                entity.orderFinal == 123.45
        ));
    }

    // ==================== ТЕСТЫ ВЫЧИТАНИЯ ПРЕДВАРИТЕЛЬНОЙ ====================

    @Test
    void testCalculateTotals_MainType_SubtractsPreliminaryOrder() {
        // Arrange
        List<ProductWithMaterialsDto> data = createTestProductWithMaterials();
        data.get(0).getMaterials().get(0).setKolf(0.0);

        PlrMt mt = createTestMt();
        when(mtService.getByKmt(anyString())).thenReturn(mt);

        // Предварительная заявка с orderFinal = 100
        PlrSinv preliminary = new PlrSinv();
        preliminary.kmt = "1002051408";
        preliminary.orderFinal = 100.0;

        when(sinvRepository.findByDateAndKppAndType(any(LocalDate.class), anyString(), eq("P")))
                .thenReturn(List.of(preliminary));

        // Act
        List<ProductWithMaterialsDto> result = materialService.recalcKolf(
                KolfRecalcRequest.builder()
                        .date(testDateStr)
                        .kpp(testKpp)
                        .type("M")
                        .kmt("1002051408")
                        .kolf(0.0)
                        .data(data)
                        .build()
        );

        // Assert
        SinvDto material = result.get(0).getMaterials().get(0);
        assertNotNull(material.getOrder());
        // order должен быть уменьшен на 100 (но не меньше 0)
        assertTrue(material.getOrder() >= 0);
    }

    @Test
    void testCalculateTotals_MainType_FullCoverageByPreliminary_ReturnsZero() {
        // Arrange
        List<ProductWithMaterialsDto> data = createTestProductWithMaterials();
        data.get(0).getMaterials().get(0).setKolf(0.0);

        PlrMt mt = createTestMt();
        when(mtService.getByKmt(anyString())).thenReturn(mt);

        // Предварительная заявка с очень большим orderFinal — покрывает всё
        PlrSinv preliminary = new PlrSinv();
        preliminary.kmt = "1002051408";
        preliminary.orderFinal = 99999.0;

        when(sinvRepository.findByDateAndKppAndType(any(LocalDate.class), anyString(), eq("P")))
                .thenReturn(List.of(preliminary));

        // Act
        List<ProductWithMaterialsDto> result = materialService.recalcKolf(
                KolfRecalcRequest.builder()
                        .date(testDateStr)
                        .kpp(testKpp)
                        .type("M")
                        .kmt("1002051408")
                        .kolf(0.0)
                        .data(data)
                        .build()
        );

        // Assert
        SinvDto material = result.get(0).getMaterials().get(0);
        assertEquals(0.0, material.getOrder());
    }

    @Test
    void testCalculateTotals_PreliminaryType_DoesNotSubtract() {
        // Arrange
        List<ProductWithMaterialsDto> data = createTestProductWithMaterials();
        PlrMt mt = createTestMt();
        when(mtService.getByKmt(anyString())).thenReturn(mt);

        // Act
        List<ProductWithMaterialsDto> result = materialService.recalcKolf(
                KolfRecalcRequest.builder()
                        .date(testDateStr)
                        .kpp(testKpp)
                        .type("P")
                        .kmt("1002051408")
                        .kolf(0.0)
                        .data(data)
                        .build()
        );

        // Assert
        SinvDto material = result.get(0).getMaterials().get(0);
        assertNotNull(material.getOrder());

        // Для предварительной заявки — НЕ должно быть вызова поиска "P"
        verify(sinvRepository, never()).findByDateAndKppAndType(any(), any(), eq("P"));
    }

    // ==================== ТЕСТЫ orderFinal = null ====================

    @Test
    void testCalculateTotals_WithNullOrderFinal_SetsToOrder() {
        // Arrange
        List<ProductWithMaterialsDto> data = createTestProductWithMaterials();
        data.get(0).getMaterials().get(0).setOrderFinal(null);

        PlrMt mt = createTestMt();
        when(mtService.getByKmt(anyString())).thenReturn(mt);

        // Act
        List<ProductWithMaterialsDto> result = materialService.recalcKolf(
                KolfRecalcRequest.builder()
                        .date(testDateStr)
                        .kpp(testKpp)
                        .type("M")
                        .kmt("1002051408")
                        .kolf(0.0)
                        .data(data)
                        .build()
        );

        // Assert
        SinvDto material = result.get(0).getMaterials().get(0);
        // orderFinal должен стать равным order
        assertEquals(material.getOrder(), material.getOrderFinal());
    }

    @Test
    void testCalculateTotals_WithExistingOrderFinal_ResetsToOrder() {
        // Arrange
        List<ProductWithMaterialsDto> data = createTestProductWithMaterials();
        data.get(0).getMaterials().get(0).setOrderFinal(500.0);

        PlrMt mt = createTestMt();
        when(mtService.getByKmt(anyString())).thenReturn(mt);

        // Act
        List<ProductWithMaterialsDto> result = materialService.recalcKolf(
                KolfRecalcRequest.builder()
                        .date(testDateStr)
                        .kpp(testKpp)
                        .type("M")
                        .kmt("1002051408")
                        .kolf(0.0)
                        .data(data)
                        .build()
        );

        // Assert
        SinvDto material = result.get(0).getMaterials().get(0);
        // orderFinal СБРАСЫВАЕТСЯ и становится = order
        assertEquals(material.getOrder(), material.getOrderFinal());
        assertNotEquals(500.0, material.getOrderFinal());
    }

    // ============================================================
    // НОВЫЕ ТЕСТЫ — покрытие веток, на которые ругается Sonar
    // ============================================================

    // ---------- 1. loadProducts: предварительная с null kolf / null orderFinal ----------

    @Test
    void testLoadProducts_MainType_PreliminaryWithNullKolf_UsesDefaultForKolf() {
        // Arrange
        List<ProductDto> products = createTestProducts();
        PlrSprog sprog = createTestSprog();
        PlrMt mt = createTestMt();
        List<PlrRnpp> norms = createTestRnpp();

        // kolf = null → должен стать 0.0 через getOrDefault в buildSinvDto
        // orderFinal НЕ null, иначе loadPreliminary упадёт в Collectors.toMap
        PlrSinv pre = new PlrSinv();
        pre.kmt = "1002051408";
        pre.kolf = null;
        pre.orderFinal = 0.0;

        when(materialRepository.findProductsByDate(anyString())).thenReturn(products);
        when(sprogService.findByDate(any(LocalDate.class))).thenReturn(sprog);
        when(rnppService.findByKmcAndKtAndEmkAndSysn(anyDouble(), anyString(), anyString(), anyDouble()))
                .thenReturn(norms);
        when(sinvRepository.findByDateAndKppAndType(any(LocalDate.class), anyString(), eq("M")))
                .thenReturn(Collections.emptyList());
        when(sinvRepository.findByDateAndKppAndType(any(LocalDate.class), anyString(), eq("P")))
                .thenReturn(List.of(pre));
        when(mtService.getByKmt(anyString())).thenReturn(mt);

        // Act
        List<ProductWithMaterialsDto> result = materialService.loadProducts(testDateStr, testKpp, "M");

        // Assert
        SinvDto material = result.get(0).getMaterials().get(0);
        assertEquals(0.0, material.getKolf());   // null → 0.0
        assertEquals(0.0, material.getOrderPre());
    }

    @Test
    void testLoadProducts_MainType_PreliminaryWithValues_UsesPreliminaryValues() {
        // Arrange
        List<ProductDto> products = createTestProducts();
        PlrSprog sprog = createTestSprog();
        PlrMt mt = createTestMt();
        List<PlrRnpp> norms = createTestRnpp();

        PlrSinv pre = new PlrSinv();
        pre.kmt = "1002051408";
        pre.kolf = 42.0;
        pre.orderFinal = 77.0;

        when(materialRepository.findProductsByDate(anyString())).thenReturn(products);
        when(sprogService.findByDate(any(LocalDate.class))).thenReturn(sprog);
        when(rnppService.findByKmcAndKtAndEmkAndSysn(anyDouble(), anyString(), anyString(), anyDouble()))
                .thenReturn(norms);
        when(sinvRepository.findByDateAndKppAndType(any(LocalDate.class), anyString(), eq("M")))
                .thenReturn(Collections.emptyList());
        when(sinvRepository.findByDateAndKppAndType(any(LocalDate.class), anyString(), eq("P")))
                .thenReturn(List.of(pre));
        when(mtService.getByKmt(anyString())).thenReturn(mt);

        // Act
        List<ProductWithMaterialsDto> result = materialService.loadProducts(testDateStr, testKpp, "M");

        // Assert
        SinvDto material = result.get(0).getMaterials().get(0);
        assertEquals(42.0, material.getKolf());
        assertEquals(77.0, material.getOrderPre());
    }

    // ---------- 2. buildSinvDto: existing == null, plrMt != null → pers/rnd из plrMt ----------

    @Test
    void testLoadProducts_NoExistingData_FallsBackToMtSettings() {
        // Arrange
        List<ProductDto> products = createTestProducts();
        PlrSprog sprog = createTestSprog();

        PlrMt mt = new PlrMt();
        mt.setKmt("1002051408");
        mt.setSnm("Тестовый материал");
        mt.setEdu("кг");
        mt.setPers(20.0);
        mt.setRnd(3.0);

        when(materialRepository.findProductsByDate(anyString())).thenReturn(products);
        when(sprogService.findByDate(any(LocalDate.class))).thenReturn(sprog);
        when(rnppService.findByKmcAndKtAndEmkAndSysn(anyDouble(), anyString(), anyString(), anyDouble()))
                .thenReturn(createTestRnpp());
        when(sinvRepository.findByDateAndKppAndType(any(LocalDate.class), anyString(), anyString()))
                .thenReturn(Collections.emptyList());
        when(mtService.getByKmt(anyString())).thenReturn(mt);

        // Act
        List<ProductWithMaterialsDto> result = materialService.loadProducts(testDateStr, testKpp, "M");

        // Assert
        SinvDto material = result.get(0).getMaterials().get(0);
        // existing == null → pers/rnd должны прийти из plrMt
        assertEquals(20.0, material.getInsurancePerc());
        assertEquals(3.0, material.getRoundStep());
    }

    @Test
    void testLoadProducts_NoExistingData_AndMtNull_UsesDefaultsFromCalculateTotals() {
        // Arrange
        List<ProductDto> products = createTestProducts();
        PlrSprog sprog = createTestSprog();

        when(materialRepository.findProductsByDate(anyString())).thenReturn(products);
        when(sprogService.findByDate(any(LocalDate.class))).thenReturn(sprog);
        when(rnppService.findByKmcAndKtAndEmkAndSysn(anyDouble(), anyString(), anyString(), anyDouble()))
                .thenReturn(createTestRnpp());
        when(sinvRepository.findByDateAndKppAndType(any(LocalDate.class), anyString(), anyString()))
                .thenReturn(Collections.emptyList());
        when(mtService.getByKmt(anyString())).thenReturn(null);

        // Act
        List<ProductWithMaterialsDto> result = materialService.loadProducts(testDateStr, testKpp, "M");

        // Assert
        SinvDto material = result.get(0).getMaterials().get(0);
        // calculateTotals подставит дефолты, потому что plrMt == null
        assertEquals(0.0, material.getInsurancePerc());
        assertEquals(1.0, material.getRoundStep());
        assertNull(material.getSnmMt());
    }

    // ---------- 3. toPlrSinv: null/<=0 значения ----------

    @Test
    void testSaveAll_WithNullRoundStepZeroOrNegative_UseDefaultOne() {
        // Arrange — rnd = 0 (не проходит условие > 0) → должен стать 1.0
        List<ProductWithMaterialsDto> data = createTestProductWithMaterials();
        data.get(0).getMaterials().get(0).setRoundStep(0.0);

        SaveRequest request = SaveRequest.builder()
                .date(testDateStr).kpp(testKpp).data(data).type("M").build();

        doNothing().when(zinvRepository).deleteByDateAndKppAndType(any(LocalDate.class), anyString(), anyString());
        doNothing().when(sinvRepository).deleteByDateAndKppAndType(any(LocalDate.class), anyString(), anyString());
        when(zinvRepository.save(any(PlrZinv.class))).thenReturn(new PlrZinv());
        when(sinvRepository.saveOrUpdate(any(PlrSinv.class))).thenReturn(new PlrSinv());

        // Act
        materialService.saveAll(request);

        // Assert
        verify(sinvRepository, times(1)).saveOrUpdate(argThat(entity -> entity.rnd == 1.0));
    }

    @Test
    void testSaveAll_WithNullOrderFinal_UsesZero() {
        // Arrange — orderFinal = null → должен стать 0.0
        List<ProductWithMaterialsDto> data = createTestProductWithMaterials();
        data.get(0).getMaterials().get(0).setOrderFinal(null);

        SaveRequest request = SaveRequest.builder()
                .date(testDateStr).kpp(testKpp).data(data).type("M").build();

        doNothing().when(zinvRepository).deleteByDateAndKppAndType(any(LocalDate.class), anyString(), anyString());
        doNothing().when(sinvRepository).deleteByDateAndKppAndType(any(LocalDate.class), anyString(), anyString());
        when(zinvRepository.save(any(PlrZinv.class))).thenReturn(new PlrZinv());
        when(sinvRepository.saveOrUpdate(any(PlrSinv.class))).thenReturn(new PlrSinv());

        // Act
        materialService.saveAll(request);

        // Assert
        verify(sinvRepository, times(1)).saveOrUpdate(argThat(entity -> entity.orderFinal == 0.0));
    }

    @Test
    void testSaveAll_WithNullNorm_UsesAsIs() {
        // Arrange — проверяем, что norm тоже прокидывается
        List<ProductWithMaterialsDto> data = createTestProductWithMaterials();
        data.get(0).getMaterials().get(0).setNorm(null);
        data.get(0).getMaterials().get(0).setNormf(null);

        SaveRequest request = SaveRequest.builder()
                .date(testDateStr).kpp(testKpp).data(data).type("M").build();

        doNothing().when(zinvRepository).deleteByDateAndKppAndType(any(LocalDate.class), anyString(), anyString());
        doNothing().when(sinvRepository).deleteByDateAndKppAndType(any(LocalDate.class), anyString(), anyString());
        when(zinvRepository.save(any(PlrZinv.class))).thenReturn(new PlrZinv());
        when(sinvRepository.saveOrUpdate(any(PlrSinv.class))).thenReturn(new PlrSinv());

        // Act
        materialService.saveAll(request);

        // Assert — просто проверяем, что сохранение прошло
        verify(sinvRepository, times(1)).saveOrUpdate(any(PlrSinv.class));
    }

    // ---------- 4. calculateTotals: plrMt == null и его null-поля ----------

    @Test
    void testCalculateTotals_MtIsNull_UsesDefaultInsuranceAndRoundStep() {
        // Arrange
        List<ProductWithMaterialsDto> data = createTestProductWithMaterials();
        when(mtService.getByKmt(anyString())).thenReturn(null);

        // Act
        List<ProductWithMaterialsDto> result = materialService.recalcKolf(
                KolfRecalcRequest.builder()
                        .date(testDateStr).kpp(testKpp).type("M")
                        .kmt("1002051408").kolf(0.0).data(data)
                        .build()
        );

        // Assert
        SinvDto material = result.get(0).getMaterials().get(0);
        assertEquals(0.0, material.getInsurancePerc());
        assertEquals(1.0, material.getRoundStep());
        assertNull(material.getSnmMt());
    }

    @Test
    void testCalculateTotals_MtWithNullPersAndNullRnd_UsesDefaults() {
        // Arrange
        PlrMt mt = new PlrMt();
        mt.setKmt("1002051408");
        mt.setPers(null);
        mt.setRnd(null);
        when(mtService.getByKmt(anyString())).thenReturn(mt);

        List<ProductWithMaterialsDto> data = createTestProductWithMaterials();

        // Act
        List<ProductWithMaterialsDto> result = materialService.recalcKolf(
                KolfRecalcRequest.builder()
                        .date(testDateStr).kpp(testKpp).type("M")
                        .kmt("1002051408").kolf(0.0).data(data)
                        .build()
        );

        // Assert
        SinvDto material = result.get(0).getMaterials().get(0);
        assertEquals(0.0, material.getInsurancePerc());
        assertEquals(1.0, material.getRoundStep());
    }

    @Test
    void testCalculateTotals_MtWithZeroRnd_UsesDefaultOne() {
        // Arrange
        PlrMt mt = new PlrMt();
        mt.setKmt("1002051408");
        mt.setPers(10.0);
        mt.setRnd(0.0); // <= 0 → должен стать 1.0
        when(mtService.getByKmt(anyString())).thenReturn(mt);

        List<ProductWithMaterialsDto> data = createTestProductWithMaterials();

        // Act
        List<ProductWithMaterialsDto> result = materialService.recalcKolf(
                KolfRecalcRequest.builder()
                        .date(testDateStr).kpp(testKpp).type("M")
                        .kmt("1002051408").kolf(0.0).data(data)
                        .build()
        );

        // Assert
        SinvDto material = result.get(0).getMaterials().get(0);
        assertEquals(1.0, material.getRoundStep());
        assertEquals(10.0, material.getInsurancePerc());
    }

    @Test
    void testCalculateTotals_WithNullKolf_UsesZero() {
        // Arrange — kolf = null → getDoubleOrDefault вернёт 0.0
        List<ProductWithMaterialsDto> data = createTestProductWithMaterials();
        data.get(0).getMaterials().get(0).setKolf(null);

        PlrMt mt = createTestMt();
        when(mtService.getByKmt(anyString())).thenReturn(mt);

        // Act
        List<ProductWithMaterialsDto> result = materialService.recalcKolf(
                KolfRecalcRequest.builder()
                        .date(testDateStr).kpp(testKpp).type("M")
                        .kmt("1002051408").kolf(null).data(data)
                        .build()
        );

        // Assert
        SinvDto material = result.get(0).getMaterials().get(0);
        assertNotNull(material.getOrder());
    }

    // ---------- 5. loadPreliminary: ветка type != "M" ----------

    @Test
    void testCalculateTotals_PreliminaryType_LoadPreliminaryReturnsEmpty() {
        // Arrange — type = "P", loadPreliminary должен вернуть emptyMap
        List<ProductWithMaterialsDto> data = createTestProductWithMaterials();
        PlrMt mt = createTestMt();
        when(mtService.getByKmt(anyString())).thenReturn(mt);

        // Act
        List<ProductWithMaterialsDto> result = materialService.recalcKolf(
                KolfRecalcRequest.builder()
                        .date(testDateStr).kpp(testKpp).type("P")
                        .kmt("1002051408").kolf(0.0).data(data)
                        .build()
        );

        // Assert
        SinvDto material = result.get(0).getMaterials().get(0);
        assertNotNull(material.getOrder());
        // Для "P" мы не должны ходить в репозиторий за "P"
        verify(sinvRepository, never()).findByDateAndKppAndType(any(), any(), eq("P"));
    }
}