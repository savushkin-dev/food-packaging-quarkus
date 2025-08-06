package org.acme.foodpackaging.domain;

import java.time.Duration;
import java.util.*;

// Класс для расчета длительности мойки между различной продукцией
public class CleaningTimeCalculator {

    private static final int CHANGING_PACKAGING = 10;                               // Смена упаковки

    private static final int FROM_ROD_TO_CLASSIC = 150;                           // Переход со стержня на классик
    private static final int ROD_DIFFERENT_FILLING = 50;                         // Смена начинки в стержня
    private static final int TO_NONE_FILLING_ROD = 40;                          // Стержень с начинкой на стержень без начинки

    private static final int FROM_PLUSH_TO_CLASSIC = 50;                      // Переход с классики на плюш
    private static final int CACTUS_CLEANING = 180;                          // Мойка до и после кактуса
    private static final int DIFFERENT_CURD_MASS = 20;                      // Смена творожной массы
    private static final int CLASSIC_DIFFERENT_GLAZE = 40;                 // Смена глаузри без мойки
    private static final int CLEANING_AFTER_ALLERGEN = 180;               // Мойка после аллергена
    private static final int CLASSIC_DIFFERENT_GLAZE_FOR_ALLERGEN = 90; // смена глазури с мойкой

    public CleaningTimeCalculator(List<Product> products){
        calculateCleaningDurations(products);
    }
    private void calculateCleaningDurations(List<Product> products) {
        for (Product current : products) {
            Map<Product, Duration> durations = new HashMap<>(products.size());

            for (Product previous : products) {
                Duration duration;

                if (current.getId().equals(previous.getId())) {
                    duration = Duration.ZERO;
                } else if (isCactusTransition(current, previous)) {
                    duration = Duration.ofMinutes(CACTUS_CLEANING);
                } else if (isPlushToClassic(current, previous)) {
                    duration = Duration.ofMinutes(FROM_PLUSH_TO_CLASSIC);
                }  else if (isRodToClassic(current, previous)) {
                    duration = Duration.ofMinutes(FROM_ROD_TO_CLASSIC);
                } else if (previous.is_allergen() && !current.is_allergen()) {
                    duration = Duration.ofMinutes(CLEANING_AFTER_ALLERGEN);
                } else if (isClassicDifferentGlaze(current, previous)){
                    duration = Duration.ofMinutes(CLASSIC_DIFFERENT_GLAZE);
                } else if ( isClassicDifferentGlazeForAllergen(current, previous)){
                    duration = Duration.ofMinutes(CLASSIC_DIFFERENT_GLAZE_FOR_ALLERGEN);
                } else if (isDifferentCurdMass(current, previous)) {
                    duration = Duration.ofMinutes(DIFFERENT_CURD_MASS);
                } else if (isRodToNoneFillingRod(current, previous)) {
                    duration = Duration.ofMinutes(TO_NONE_FILLING_ROD);
                } else if (isRodDifferentFilling(current, previous)) {
                    duration = Duration.ofMinutes(ROD_DIFFERENT_FILLING);
                }  else if(sameProductButDifferentCountry(current, previous)) {
                    duration = Duration.ofMinutes(CHANGING_PACKAGING);
                } else {
                    duration = Duration.ofMinutes(CHANGING_PACKAGING);
                }
                durations.put(previous, duration);
            }
            current.setCleaningDurations(durations);
        }
    }

    private boolean isCactusTransition(Product c, Product p) { //  Возвращает true, если ровно один из двух продуктов имеет тип CACTUS — но не оба одновременно
        return c.getType() == ProductType.CACTUS ^ p.getType() == ProductType.CACTUS;
    }

    private boolean isPlushToClassic(Product c, Product p) { //  Плюш на классику
        return c.getType() == ProductType.CLASSIC && p.getType() == ProductType.PLUSH
                || (c.getType() == ProductType.PLUSH && p.getType() == ProductType.CLASSIC && !p.is_allergen());
    }

    private boolean isRodToClassic(Product c, Product p) { // Стержень на классику
        return c.getType() == ProductType.CLASSIC && p.getType() == ProductType.ROD
                || (c.getType() == ProductType.ROD && p.getType() == ProductType.CLASSIC);
    }

    private boolean isClassicDifferentGlaze(Product c, Product p) { // Условие для разной глазури в классической линейке
        return c.getType() == ProductType.CLASSIC && p.getType() == ProductType.CLASSIC
                && (!c.is_allergen() && !p.is_allergen() || c.is_allergen() && !p.is_allergen())
                && !Objects.equals(c.getGlaze(), p.getGlaze());
    }

    private boolean isClassicDifferentGlazeForAllergen(Product c, Product p) { // Условие для разной глазури в аллергенах
        return c.getType() == ProductType.CLASSIC && p.getType() == ProductType.CLASSIC
                && c.is_allergen() && p.is_allergen()
                && !Objects.equals(c.getGlaze(), p.getGlaze());
    }

    private boolean isDifferentCurdMass(Product c, Product p) { // Условие для разной творожной массы
        return c.getType() == p.getType()
                && c.getType()!=ProductType.ROD && p.getType()!=ProductType.ROD
                && Objects.equals(c.getGlaze(), p.getGlaze())
                && !Objects.equals(c.getCurdMass(), p.getCurdMass());
    }

    private boolean isRodToNoneFillingRod(Product c, Product p) { // Стержень с начинкой на стержень без начинки
        return c.getType() == ProductType.ROD && p.getType() == ProductType.ROD
                && (c.getFilling() == FillingType.NONE || p.getFilling() == FillingType.NONE);
    }

    private boolean isRodDifferentFilling(Product c, Product p) { // Стержень с разной начинкой
        return c.getType() == ProductType.ROD && p.getType() == ProductType.ROD
                && !Objects.equals(p.getFilling(), c.getFilling());
    }

    private boolean sameProductButDifferentCountry(Product c, Product p) { // Одинаковые сырки, но поставки в разные страны
        return c.getType() == p.getType()
                && Objects.equals(c.getGlaze(), p.getGlaze())
                && Objects.equals(c.getCurdMass(), p.getCurdMass())
                && !Objects.equals(c.getId(), p.getId());
    }
}
