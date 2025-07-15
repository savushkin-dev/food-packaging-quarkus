package org.acme.foodpackaging.domain;

import java.time.Duration;
import java.util.*;

public class CleaningTimeCalculator {
    private static final int ALLERGEN_DIFFERENT_GLAZE = 90;
    private static final int CLEANING_AFTER_ALLERGEN = 240;
    private static final int CACTUS_CLEANING = 180;
    private static final int MIN_CLASSIC_GLAZE = 30;
    private static final int MAX_CLASSIC_GLAZE = 50;
    private static final int FROM_ROD_TO_CLASSIC = 150;
    private static final int ROD_DIFFERENT_FILLING = 50;
    private static final int DIFFERENT_CURD_MASS = 20;
    private static final int CHANGING_PACKAGING = 10;
    private static final int TO_NONE_FILLING_ROD = 40;

    private final Random random = new Random();

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
                } else if (previous.is_allergen() && !current.is_allergen()) {
                    duration = Duration.ofMinutes(CLEANING_AFTER_ALLERGEN);
                } else if (isRodToClassic(current, previous)) {
                    duration = Duration.ofMinutes(FROM_ROD_TO_CLASSIC);
                } else if (isRodNoneAfterRod(current, previous)) {
                    duration = Duration.ofMinutes(TO_NONE_FILLING_ROD);
                } else if (isRodChangePackaging(current, previous)) {
                    duration = Duration.ofMinutes(CHANGING_PACKAGING);
                } else if (isRodDifferentFilling(current, previous)) {
                    duration = Duration.ofMinutes(ROD_DIFFERENT_FILLING);
                } else if (isAllergenDifferentGlaze(current, previous)) {
                    duration = Duration.ofMinutes(ALLERGEN_DIFFERENT_GLAZE);
                } else if (!current.is_allergen() && previous.is_allergen()) {
                    duration = Duration.ofMinutes(CLEANING_AFTER_ALLERGEN);
                } else if (isClassicDifferentGlaze(current, previous)) {
                    int minutes = MIN_CLASSIC_GLAZE + random.nextInt(MAX_CLASSIC_GLAZE - MIN_CLASSIC_GLAZE);
                    duration = Duration.ofMinutes(minutes);
                } else if (sameTypeAndGlazeButDifferentId(current, previous)) {
                    duration = Duration.ofMinutes(DIFFERENT_CURD_MASS);
                } else {
                    duration = Duration.ofMinutes(MAX_CLASSIC_GLAZE);
                }

                durations.put(previous, duration);
            }

            current.setCleaningDurations(durations);
        }
    }

    private boolean isCactusTransition(Product c, Product p) {
        return c.getType() == ProductType.CACTUS ^ p.getType() == ProductType.CACTUS;
    }

    private boolean isRodToClassic(Product c, Product p) {
        return c.getType() == ProductType.CLASSIC && p.getType() == ProductType.ROD;
    }

    private boolean isRodNoneAfterRod(Product c, Product p) {
        return c.getType() == ProductType.ROD && p.getType() == ProductType.ROD
                && c.getFilling() == FillingType.NONE;
    }

    private boolean isRodChangePackaging(Product c, Product p) {
        return c.getType() == ProductType.ROD && p.getType() == ProductType.ROD
                && Objects.equals(p.getGlaze(), c.getGlaze())
                && Objects.equals(p.getFilling(), c.getFilling())
                && !Objects.equals(p.getId(), c.getId());
    }

    private boolean isRodDifferentFilling(Product c, Product p) {
        return c.getType() == ProductType.ROD && p.getType() == ProductType.ROD
                && !p.getGlaze().equals(GlazeType.C65_47); // можно доработать
    }

    private boolean isAllergenDifferentGlaze(Product c, Product p) {
        return c.is_allergen() && p.is_allergen()
                && c.getType() == ProductType.CLASSIC
                && p.getType() == ProductType.CLASSIC
                && !Objects.equals(c.getGlaze(), p.getGlaze());
    }

    private boolean isClassicDifferentGlaze(Product c, Product p) {
        return c.getType() == ProductType.CLASSIC && p.getType() == ProductType.CLASSIC
                && !Objects.equals(c.getGlaze(), p.getGlaze());
    }

    private boolean sameTypeAndGlazeButDifferentId(Product c, Product p) {
        return c.getType() == p.getType()
                && Objects.equals(c.getGlaze(), p.getGlaze())
                && !Objects.equals(c.getId(), p.getId());
    }
}
