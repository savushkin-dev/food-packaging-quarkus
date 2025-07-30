package org.acme.foodpackaging.domain;

import java.util.Map;

public enum CurdMassType {
    CARAMEL("Карамель"),
    PISTACHIO("Фисташка"),
    VANILLA_CONDENSED_MILK("Ваниль Вареная сгущенка"),
    KARTOSHKA("Картошка"),
    BISCOTTI("Бискотти"),
    TOFFEE("Тоффи"),
    DUBAI_PISTACHIO("Дубай фисташка"),
    MARSHMALLOW("Зефир"),
    COCONUT_ALMONDS("Кокос-миндаль"),
    COCOA("Какао"),
    CARAMEL_COFFEE("Кофе-Карамель"),
    CHOCOLATE("Шоколад"),
    VANILLA("Ваниль"); // ← здесь была ошибка

    private static final Map<String, CurdMassType> ID_TO_CURD_MASS = Map.ofEntries(
            Map.entry("4810268040450", CARAMEL),
            Map.entry("4810268043475", PISTACHIO),
            Map.entry("4810268045066", VANILLA_CONDENSED_MILK),
            Map.entry("4810268049866", VANILLA_CONDENSED_MILK),
            Map.entry("4810268053870", KARTOSHKA),
            Map.entry("4810268054228", BISCOTTI),
            Map.entry("4810268057748", TOFFEE),
            Map.entry("4810268059773", DUBAI_PISTACHIO),
            Map.entry("4810268058554", MARSHMALLOW),
            Map.entry("4810268043727", COCONUT_ALMONDS),
            Map.entry("4810268047640", COCOA),
            Map.entry("4810268044977", COCOA),
            Map.entry("4810268055492", CARAMEL_COFFEE),
            Map.entry("4810268047572", CHOCOLATE),
            Map.entry("4810268050640", CHOCOLATE),
            Map.entry("4810268050138", CHOCOLATE)
    );

    private final String displayName;

    CurdMassType(String displayName) {
        this.displayName = displayName;
    }

    public static CurdMassType fromProduct(String productId) {
        return ID_TO_CURD_MASS.getOrDefault(productId, VANILLA);
    }
}
