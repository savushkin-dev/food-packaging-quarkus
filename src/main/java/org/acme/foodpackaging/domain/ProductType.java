package org.acme.foodpackaging.domain;

public enum ProductType {

    // От типа зависит техпроцесс производства
    PLUSH("PLUSH"),
    ROD("ROD"),
    CLASSIC("CLASSIC"),
    CACTUS("CACTUS");

    private final String displayName;

    ProductType(String displayName) {
        this.displayName = displayName;
    }
}
