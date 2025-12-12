package org.acme.foodpackaging.scheduleOperations.utils;

import lombok.Getter;

import java.util.Map;

public class SpeedCacheUtils {
    @Getter
    private static Map<String, Map<String, Integer>> lineSpeeds;

    public static void init(Map<String, Map<String, Integer>> speeds) {
        lineSpeeds = speeds;
    }

    public static Integer getSpeed(String lineId, String productType) {
        if (lineId == null || productType == null) return null;
        Map<String, Integer> productSpeeds = lineSpeeds.get(lineId);
        if (productSpeeds == null) return null;
        return productSpeeds.get(productType);
    }
}
