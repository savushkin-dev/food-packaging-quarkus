package org.acme.foodpackaging.dto.row.products;

public record CleaningResult(
    int minutes,
    boolean isPLRLC
) {
public static CleaningResult zero() {
    return new CleaningResult(0, false);
}
}
