package service;

import org.acme.foodpackaging.domain.CleaningRule;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.service.products.CleaningCalculatorService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CleaningCalculatorServiceTest {

    private CleaningCalculatorService createCalculator() {

        List<CleaningRule> rules = List.of(
                new CleaningRule("1", "Классика", "Стержень", 90),
                new CleaningRule("1", "Плюш", "Классика", 160),
                new CleaningRule("1", "Стержень", "Классика", 150),
                new CleaningRule("1", "Классика", "Плюш", 180),
                new CleaningRule("1", "", "Кактус", 180),   // ANY → Кактус
                new CleaningRule("1", "Кактус", "", 180)    // Кактус → ANY
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

    @Test
    void exactMatch_shouldReturnExactDuration() {

        CleaningCalculatorService calc = createCalculator();

        int time = calc.getCleaningTime(
                product("Классика"),
                product("Стержень")
        );

        assertEquals(90, time);
    }

    @Test
    void anyToCactus_shouldMatchWildcardFrom() {

        CleaningCalculatorService calc = createCalculator();

        int time = calc.getCleaningTime(
                product("Стержень"),
                product("Кактус")
        );

        assertEquals(180, time);
    }

    @Test
    void cactusToAny_shouldMatchWildcardTo() {

        CleaningCalculatorService calc = createCalculator();

        int time = calc.getCleaningTime(
                product("Кактус"),
                product("Плюш")
        );

        assertEquals(180, time);
    }

    @Test
    void noRule_shouldReturnZero() {

        CleaningCalculatorService calc = createCalculator();

        int time = calc.getCleaningTime(
                product("Неизвестный"),
                product("Другой")
        );

        assertEquals(0, time);
    }
}
