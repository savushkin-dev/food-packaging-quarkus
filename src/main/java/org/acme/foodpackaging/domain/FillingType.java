package org.acme.foodpackaging.domain;

import java.util.Map;

public enum FillingType {
    CONDENSED_MILK("Вареная сгущенка"),
    CHOCOLATE("Шоколад"),
    STRAWBERRY("Клубника"),
    RASPBERRY("Малина"),
    MANGO("Манго"),
    CARAMEL_PEANUT("Карамель-Арахис"),
    HAZELNUT("Фундук"),
    NONE("Без начинки");

    // Классификация начинок стрежней по ean13
    private static final Map<String, FillingType> ID_TO_FILLING = Map.ofEntries(
            Map.entry("4810268050671", CONDENSED_MILK),
            Map.entry("4810268050640", CHOCOLATE),
            Map.entry("4810268050138", CHOCOLATE),
            Map.entry("4810268050657", STRAWBERRY),
            Map.entry("4810268050121", STRAWBERRY),
            Map.entry("4810268054969", HAZELNUT),
            Map.entry("4810268050664", MANGO),
            Map.entry("4810268056826", CARAMEL_PEANUT),
            Map.entry("4810268053153", RASPBERRY),
            Map.entry("4810268050282", NONE)
    );

    private final String displayName;

    FillingType(String displayName) {
        this.displayName = displayName;
    }

    public static FillingType fromProduct(String productId) {
        return ID_TO_FILLING.getOrDefault(productId, NONE);
    }
}

