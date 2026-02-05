package org.acme.foodpackaging.scheduleOperations.utils;

import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Map;

public class SpeedCacheUtils {
    @Getter
    private static Map<String, Map<String, Pair<Integer, Integer>>> lineSpeeds;

    public static void init(Map<String, Map<String, Pair<Integer, Integer>>> speeds) {
        lineSpeeds = speeds;
    }

    public static Integer getSpeed(String lineId, String productType) {
        Pair<Integer, Integer> pair = getSpeedPair(lineId, productType);
        return pair != null ? pair.getLeft() : null;
    }

    public static Integer getHandPackagingSpeed(String lineId, String productType) {
        Pair<Integer, Integer> pair = getSpeedPair(lineId, productType);
        return pair != null ? pair.getRight() : null;
    }

    private static Pair<Integer, Integer> getSpeedPair(String lineId, String productType) {
        if (lineId == null || productType == null) return null;
        Map<String, Pair<Integer, Integer>> productSpeeds = lineSpeeds.get(lineId);
        if (productSpeeds == null) return null;
        return productSpeeds.get(productType);
    }
}
