package org.acme.foodpackaging.domain;

import java.util.Map;

public class ProductFactory {
    private static final Map<String, Boolean> IS_ALLERGEN = Map.of(
            "4810268043727", true,
            "4810268043475", true,
            "4810268054969", true,
            "4810268056826", true
    );

    public Product create(String id, String name) {
        ProductType type = determineType(name);
        boolean allergen = IS_ALLERGEN.getOrDefault(id, false);
        return new Product(id, name, type, allergen);
    }

    private ProductType determineType(String productName) {
        String lower = productName.toLowerCase();
        if (containsAll(lower, "творобушки", "флоупак")) return ProductType.ROD;
        if (containsAll(lower, "топ", "флоупак")) return ProductType.ROD;
        if (containsAll(lower, "фольга")) return ProductType.PLUSH;
        if (containsAll(lower, "кактус")) return ProductType.CACTUS;
        return ProductType.CLASSIC;
    }

    private boolean containsAll(String text, String... keywords) {
        for (String kw : keywords) {
            if (!text.contains(kw)) return false;
        }
        return true;
    }
}
