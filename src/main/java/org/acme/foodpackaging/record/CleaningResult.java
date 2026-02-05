package org.acme.foodpackaging.record;

public record CleaningResult(
    int minutes,
    boolean isPLRLC
) {
public static CleaningResult zero() {
    return new CleaningResult(0, false);
}
}
