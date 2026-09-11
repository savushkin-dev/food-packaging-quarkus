package org.acme.foodpackaging.domain.value;

public record CleaningResult(
    int minutes,
    boolean isPLRLC
) {
public static CleaningResult zero() {
    return new CleaningResult(0, false);
}
}
