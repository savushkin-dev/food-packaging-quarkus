package org.acme.foodpackaging.dto.row.products;

public record ProductRow(
    String kmc,
    String ean13,
    String type,
    String glaze,
    String mass,
    String filling,
    String shortName,
    String krkmc,
    Double massa
) {}
