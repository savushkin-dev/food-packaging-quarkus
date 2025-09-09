package org.acme.foodpackaging.domain;

public enum ProductType {

    // От типа зависит техпроцесс производства
    PLUSH("10003"),
    ROD("10002"),
    CLASSIC("10001"),
    CACTUS("10004");

    private final String displayName;

    ProductType(String displayName) {
        this.displayName = displayName;
    }
    public  String getDisplayName() { return displayName; }
}
