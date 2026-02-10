package org.acme.foodpackaging.scheduleoperations.utils;

import lombok.Getter;
import java.util.Map;

public class CleaningDurationUtils {

    @Getter
    private static Map<String, Integer> linesCleaning;

    private CleaningDurationUtils() {}

    public static void init(Map<String, Integer> cleanings) {
        linesCleaning = cleanings;
    }
}
