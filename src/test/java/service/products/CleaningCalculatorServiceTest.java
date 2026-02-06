package service.products;

import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.record.CleaningRule;
import org.acme.foodpackaging.service.products.CleaningCalculatorService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CleaningCalculatorServiceTest {

    private CleaningCalculatorService createCalculator() {

        List<CleaningRule> rules = List.of(
                new CleaningRule("1", "Классика", "Стержень", 90, false),
                new CleaningRule("1", "Плюш", "Классика", 160, false),
                new CleaningRule("1", "Стержень", "Классика", 150, false),
                new CleaningRule("1", "Классика", "Плюш", 180, false),
                new CleaningRule("1", "", "Кактус", 180, false),   // ANY → Кактус
                new CleaningRule("1", "Кактус", "", 180, false)   // Кактус → ANY
        );

        CleaningCalculatorService cleaningCalculatorService = new CleaningCalculatorService();
        cleaningCalculatorService.setRules(rules);
        return cleaningCalculatorService;
    }

    private Product product(String type) {
        Product p = new Product();
        p.setId(type);              // важно для логики cleaningCalculate
        p.setType(type);
        p.setGlaze("");             // остальные параметры — пустые
        p.setCurdMass("");
        p.setFilling("");
        return p;
    }

    private Product product(String id, String type, String glaze, String curdMass, String filling) {
        Product p = new Product();
        p.setId(id);
        p.setType(type);
        p.setGlaze(glaze);
        p.setCurdMass(curdMass);
        p.setFilling(filling);
        return p;
    }

    @Test
    void exactMatch_shouldReturnExactDuration() {

        CleaningCalculatorService calc = createCalculator();

        int time = calc.getCleaningResult(
                product("Классика"),
                product("Стержень")
        ).minutes();

        assertEquals(90, time);
    }

    @Test
    void anyToCactus_shouldMatchWildcardFrom() {

        CleaningCalculatorService calc = createCalculator();

        int time = calc.getCleaningResult(
                product("Стержень"),
                product("Кактус")
        ).minutes();

        assertEquals(180, time);
    }

    @Test
    void cactusToAny_shouldMatchWildcardTo() {

        CleaningCalculatorService calc = createCalculator();

        int time = calc.getCleaningResult(
                product("Кактус"),
                product("Плюш")
        ).minutes();

        assertEquals(180, time);
    }

    @Test
    void noRule_shouldReturnZero() {

        CleaningCalculatorService calc = createCalculator();

        int time = calc.getCleaningResult(
                product("Неизвестный"),
                product("Другой")
        ).minutes();

        assertEquals(0, time);
    }

    // --- cleaningCalculate tests ---

    @Test
    void cleaningCalculate_maintenanceCurrentOrPrevious_returnsZero() {
        CleaningCalculatorService calc = createCalculator();
        Product maintenance = product("MAINTENANCE");
        Product normal = product("Классика");

        calc.cleaningCalculate(List.of(maintenance, normal));

        // maintenance -> maintenance: 0
        assertEquals(Duration.ZERO, maintenance.getCleaningDurations().get(maintenance));
        assertEquals(0, maintenance.getCleaningResults().get(maintenance).minutes());
        // maintenance -> normal: 0
        assertEquals(Duration.ZERO, normal.getCleaningDurations().get(maintenance));
        assertEquals(0, normal.getCleaningResults().get(maintenance).minutes());
        // normal -> maintenance: 0
        assertEquals(Duration.ZERO, maintenance.getCleaningDurations().get(normal));
        assertEquals(0, maintenance.getCleaningResults().get(normal).minutes());
    }

    @Test
    void cleaningCalculate_sameProduct_returnsZero() {
        CleaningCalculatorService calc = createCalculator();
        Product p = product("Классика");

        calc.cleaningCalculate(List.of(p));

        assertEquals(Duration.ZERO, p.getCleaningDurations().get(p));
        assertEquals(0, p.getCleaningResults().get(p).minutes());
    }

    @Test
    void cleaningCalculate_sameProductDifferentPackaging_returns10Minutes() {
        CleaningCalculatorService calc = createCalculator();
        Product a = product("id1", "Vanilla", "glaze1", "mass1", "fill1");
        Product b = product("id2", "Vanilla", "glaze1", "mass1", "fill1");

        calc.cleaningCalculate(List.of(a, b));

        assertEquals(Duration.ofMinutes(10), a.getCleaningDurations().get(b));
        assertEquals(10, a.getCleaningResults().get(b).minutes());
        assertEquals(Duration.ofMinutes(10), b.getCleaningDurations().get(a));
        assertEquals(10, b.getCleaningResults().get(a).minutes());
    }

    @Test
    void cleaningCalculate_differentProducts_usesRules() {
        CleaningCalculatorService calc = createCalculator();
        Product from = product("Стержень");
        Product to = product("Классика");

        calc.cleaningCalculate(List.of(from, to));

        assertNotNull(to.getCleaningDurations());
        assertNotNull(to.getCleaningResults());
        assertEquals(Duration.ofMinutes(150), to.getCleaningDurations().get(from));
        assertEquals(150, to.getCleaningResults().get(from).minutes());
    }
}
