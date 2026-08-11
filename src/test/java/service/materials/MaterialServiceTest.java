package service.materials;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.acme.foodpackaging.dto.materials.*;
import org.acme.foodpackaging.entity.materials.*;
import org.acme.foodpackaging.repository.materials.*;
import org.acme.foodpackaging.service.materials.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@QuarkusTest
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

    private final LocalDate testDate = LocalDate.of(2026, 2, 15);
    private final String testDateStr = "2026-02-15";
    private final String testKpp = "01020391";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

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
        when(sinvRepository.findByDateAndKpp(any(LocalDate.class), anyString()))
                .thenReturn(Collections.emptyList());
        when(mtService.getByKmt(anyString())).thenReturn(mt);

        // Act
        List<ProductWithMaterialsDto> result = materialService.loadProducts(testDateStr, testKpp);

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
        when(sinvRepository.findByDateAndKpp(any(LocalDate.class), anyString()))
                .thenReturn(existingData);
        when(mtService.getByKmt(anyString())).thenReturn(mt);

        // Act
        List<ProductWithMaterialsDto> result = materialService.loadProducts(testDateStr, testKpp);

        // Assert
        assertNotNull(result);
        SinvDto material = result.get(0).getMaterials().get(0);
        assertEquals(100.0, material.getKolf());
        assertEquals(15.0, material.getInsurancePerc());
        assertEquals(5.0, material.getRoundStep());
        assertEquals(175.0, material.getOrder());
    }

    @Test
    void testLoadProducts_NoSprog_ThrowsException() {
        // Arrange
        List<ProductDto> products = createTestProducts();
        when(materialRepository.findProductsByDate(anyString())).thenReturn(products);
        when(sprogService.findByDate(any(LocalDate.class))).thenReturn(null);

        // Act & Assert
        assertThrows(NullPointerException.class, () -> {
            materialService.loadProducts(testDateStr, testKpp);
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
        when(sinvRepository.findByDateAndKpp(any(LocalDate.class), anyString()))
                .thenReturn(existingData);
        when(mtService.getByKmt(anyString())).thenReturn(mt);

        // Act
        List<ProductWithMaterialsDto> result = materialService.loadProducts(testDateStr, testKpp);

        // Assert
        SinvDto material = result.get(0).getMaterials().get(0);
        assertEquals(0.0, material.getKolf());
    }

    @Test
    void testLoadProducts_WithMultipleMaterials() {
        // Arrange
        List<ProductDto> products = createTestProducts();
        PlrSprog sprog = createTestSprog();
        PlrMt mt = createTestMt();

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
        when(sinvRepository.findByDateAndKpp(any(LocalDate.class), anyString()))
                .thenReturn(Collections.emptyList());
        when(mtService.getByKmt(anyString())).thenReturn(mt);

        // Act
        List<ProductWithMaterialsDto> result = materialService.loadProducts(testDateStr, testKpp);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(2, result.get(0).getMaterials().size());
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
        assertTrue(material.getOrder() >= 0);
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
    @Transactional
    void testSaveAll_Success() {
        // Arrange
        List<ProductWithMaterialsDto> data = createTestProductWithMaterials();
        SaveRequest request = SaveRequest.builder()
                .date(testDateStr)
                .kpp(testKpp)
                .data(data)
                .build();

        doNothing().when(zinvRepository).deleteByDateAndKpp(any(LocalDate.class), anyString());
        doNothing().when(sinvRepository).deleteByDateAndKpp(any(LocalDate.class), anyString());
        doNothing().when(zinvRepository).save(any(PlrZinv.class));
        doNothing().when(sinvRepository).saveOrUpdate(any(PlrSinv.class));

        // Act
        materialService.saveAll(request);

        // Assert
        verify(zinvRepository, times(1)).deleteByDateAndKpp(any(LocalDate.class), anyString());
        verify(sinvRepository, times(1)).deleteByDateAndKpp(any(LocalDate.class), anyString());
        verify(zinvRepository, times(data.size())).save(any(PlrZinv.class));

        int totalMaterials = data.stream().mapToInt(p -> p.getMaterials().size()).sum();
        verify(sinvRepository, times(totalMaterials)).saveOrUpdate(any(PlrSinv.class));
    }

    @Test
    @Transactional
    void testSaveAll_WithEmptyData_DoesNothing() {
        // Arrange
        SaveRequest request = SaveRequest.builder()
                .date(testDateStr)
                .kpp(testKpp)
                .data(Collections.emptyList())
                .build();

        // Act
        materialService.saveAll(request);

        // Assert
        verify(zinvRepository, times(1)).deleteByDateAndKpp(any(LocalDate.class), anyString());
        verify(sinvRepository, times(1)).deleteByDateAndKpp(any(LocalDate.class), anyString());
        verify(zinvRepository, never()).save(any(PlrZinv.class));
        verify(sinvRepository, never()).saveOrUpdate(any(PlrSinv.class));
    }

    @Test
    @Transactional
    void testSaveAll_WithNullOrder_UseDefaultZero() {
        // Arrange
        List<ProductWithMaterialsDto> data = createTestProductWithMaterials();
        data.get(0).getMaterials().get(0).setOrder(null);

        SaveRequest request = SaveRequest.builder()
                .date(testDateStr)
                .kpp(testKpp)
                .data(data)
                .build();

        doNothing().when(zinvRepository).deleteByDateAndKpp(any(LocalDate.class), anyString());
        doNothing().when(sinvRepository).deleteByDateAndKpp(any(LocalDate.class), anyString());
        doNothing().when(zinvRepository).save(any(PlrZinv.class));
        doNothing().when(sinvRepository).saveOrUpdate(any(PlrSinv.class));

        // Act
        materialService.saveAll(request);

        // Assert
        verify(sinvRepository, times(1)).saveOrUpdate(argThat(entity ->
                entity.order == 0.0
        ));
    }

    @Test
    @Transactional
    void testSaveAll_WithNullInsurancePerc_UseDefaultZero() {
        // Arrange
        List<ProductWithMaterialsDto> data = createTestProductWithMaterials();
        data.get(0).getMaterials().get(0).setInsurancePerc(null);

        SaveRequest request = SaveRequest.builder()
                .date(testDateStr)
                .kpp(testKpp)
                .data(data)
                .build();

        doNothing().when(zinvRepository).deleteByDateAndKpp(any(LocalDate.class), anyString());
        doNothing().when(sinvRepository).deleteByDateAndKpp(any(LocalDate.class), anyString());
        doNothing().when(zinvRepository).save(any(PlrZinv.class));
        doNothing().when(sinvRepository).saveOrUpdate(any(PlrSinv.class));

        // Act
        materialService.saveAll(request);

        // Assert
        verify(sinvRepository, times(1)).saveOrUpdate(argThat(entity ->
                entity.pers == 0.0
        ));
    }

    @Test
    @Transactional
    void testSaveAll_WithNullRoundStep_UseDefaultOne() {
        // Arrange
        List<ProductWithMaterialsDto> data = createTestProductWithMaterials();
        data.get(0).getMaterials().get(0).setRoundStep(null);

        SaveRequest request = SaveRequest.builder()
                .date(testDateStr)
                .kpp(testKpp)
                .data(data)
                .build();

        doNothing().when(zinvRepository).deleteByDateAndKpp(any(LocalDate.class), anyString());
        doNothing().when(sinvRepository).deleteByDateAndKpp(any(LocalDate.class), anyString());
        doNothing().when(zinvRepository).save(any(PlrZinv.class));
        doNothing().when(sinvRepository).saveOrUpdate(any(PlrSinv.class));

        // Act
        materialService.saveAll(request);

        // Assert
        verify(sinvRepository, times(1)).saveOrUpdate(argThat(entity ->
                entity.rnd == 1.0
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

    @Test
    void testRoundToTwo_ThroughCalculateTotals() {
        // Arrange
        List<ProductWithMaterialsDto> data = createTestProductWithMaterials();
        SinvDto material = data.get(0).getMaterials().get(0);
        material.setNormf(158.0825);

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
        SinvDto resultMaterial = result.get(0).getMaterials().get(0);
        assertEquals(158.08, resultMaterial.getTotalNormf(), 0.001);
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
        when(sinvRepository.findByDateAndKpp(any(LocalDate.class), anyString()))
                .thenReturn(createTestSinv());
        when(mtService.getByKmt(anyString())).thenReturn(mt);

        // Act
        List<ProductWithMaterialsDto> result = materialService.loadProducts(testDateStr, testKpp);

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
        when(sinvRepository.findByDateAndKpp(any(LocalDate.class), anyString()))
                .thenReturn(Collections.emptyList());
        when(mtService.getByKmt(anyString())).thenReturn(mt);

        // Act
        List<ProductWithMaterialsDto> result = materialService.loadProducts(testDateStr, testKpp);

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
        when(sinvRepository.findByDateAndKpp(any(LocalDate.class), anyString()))
                .thenReturn(Collections.emptyList());
        when(mtService.getByKmt(anyString())).thenReturn(mt);

        // Act
        List<ProductWithMaterialsDto> result = materialService.loadProducts(testDateStr, testKpp);

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
                .productName("Тестовый продукт")
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
}