package org.acme.foodpackaging.solver;
import org.acme.foodpackaging.domain.CleaningCalculator;
import org.acme.foodpackaging.domain.CleaningRule;
import org.acme.foodpackaging.domain.Product;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CleaningCalculatorTest {
    private CleaningCalculator createCalculator() {
        List<CleaningRule> rules = List.of(
                new CleaningRule("1", "Классика", "Стержень", 90),
                new CleaningRule("1", "Плюш", "Классика", 160),
                new CleaningRule("1", "Стержень", "Классика", 150),
                new CleaningRule("1", "Классика", "Плюш", 180),
                new CleaningRule("1", "", "Кактус", 180),   // ANY → Кактус
                new CleaningRule("1", "Кактус", "", 180)    // Кактус → ANY
        );

        return new CleaningCalculator(rules);
    }

    private Product product(String type) {
        Product p = new Product();
        p.setType(type);
        return p;
    }

    @Test
    void testExactMatch() {
        CleaningCalculator calc = createCalculator();

        int time = calc.getCleaningTime(product("Классика"), product("Стержень"));
        assertEquals(90, time, "Классика → Стержень должно быть 90");
    }

    @Test
    void testAnyToCactus() {
        CleaningCalculator calc = createCalculator();

        int time = calc.getCleaningTime(product("Стержень"), product("Кактус"));
        assertEquals(180, time, "Любой → Кактус должно быть 180");
    }

    @Test
    void testCactusToAny() {
        CleaningCalculator calc = createCalculator();

        int time = calc.getCleaningTime(product("Кактус"), product("Плюш"));
        assertEquals(180, time, "Кактус → Любой должно быть 180");
    }

    @Test
    void testDefaultZeroIfNoRule() {
        CleaningCalculator calc = createCalculator();

        int time = calc.getCleaningTime(product("Неизвестный"), product("Другой"));
        assertEquals(0, time, "Если правила нет — должно быть 0");
    }
}
