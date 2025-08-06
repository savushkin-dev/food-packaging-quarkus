package org.acme.foodpackaging.domain;

import java.util.Map;

// Класс для создания объектов продукции
public class ProductFactory {
    private static final Map<String, Boolean> IS_ALLERGEN = Map.of( // ean13 аллергенов
                "4810268043727", true,
            "4810268043475", true, // Фисташка
                "4810268054969", true,
            "4810268056826", true,
            "4810268059773", true,
            "4810268059858", true,  // Дубай фисташка
                 "4810268053870", true, // Картошка
                 "4810268054228",     true  // Бискотти
    );

    public Product create(String id, String name) {
        ProductType type = determineType(name);
        boolean allergen = IS_ALLERGEN.getOrDefault(id, false);
        return new Product(id, name, type, allergen);
    }

    private ProductType determineType(String productName) { // Присвоение типа продукту методом поиска ключевых слов в названии
        String lower = productName.toLowerCase();
        if (containsAll(lower, "творобушки")) return ProductType.ROD;
        if (containsAll(lower, "топ")) return ProductType.ROD;
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
