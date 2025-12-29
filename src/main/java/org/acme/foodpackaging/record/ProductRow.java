package org.acme.foodpackaging.record;

public record ProductRow(
    String kmc,
    String ean13,
    String type,
    String glaze,
    String mass,
    String filling,
    String shortName,
    String krkmc
) {}

