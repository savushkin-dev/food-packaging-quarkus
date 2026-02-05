package scheduleOperations.utils;

import org.acme.foodpackaging.scheduleOperations.utils.SpeedCacheUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SpeedCacheUtilsTest {

    @BeforeEach
    void setup() {
        // Инициализация карты скоростей
        Map<String, Map<String, Pair<Integer, Integer>>> speeds = new HashMap<>();

        Map<String, Pair<Integer, Integer>> line1Speeds = new HashMap<>();
        line1Speeds.put("CLASSIC", Pair.of(106, 50));
        line1Speeds.put("KERNEL", Pair.of(198, 90));
        speeds.put("line1", line1Speeds);

        Map<String, Pair<Integer, Integer>> line2Speeds = new HashMap<>();
        line2Speeds.put("CLASSIC", Pair.of(240, 100));
        line2Speeds.put("KERNEL", Pair.of(0, 0));
        speeds.put("line2", line2Speeds);

        SpeedCacheUtils.init(speeds);
    }

    @Test
    void initLineSpeeds() {
        assertNotNull(SpeedCacheUtils.getLineSpeeds());
        assertEquals(2, SpeedCacheUtils.getLineSpeeds().get("line1").size());
        assertEquals(Pair.of(198, 90), SpeedCacheUtils.getLineSpeeds().get("line1").get("KERNEL"));
        assertEquals(Pair.of(0, 0), SpeedCacheUtils.getLineSpeeds().get("line2").get("KERNEL"));
    }

    @Test
    void getHandPackagingSpeed() {
        assertEquals(50, SpeedCacheUtils.getHandPackagingSpeed("line1", "CLASSIC"));
        assertEquals(90, SpeedCacheUtils.getHandPackagingSpeed("line1", "KERNEL"));
        assertEquals(100, SpeedCacheUtils.getHandPackagingSpeed("line2", "CLASSIC"));
        assertEquals(0, SpeedCacheUtils.getHandPackagingSpeed("line2", "KERNEL"));
    }

    @Test
    void getSpeed() {
        assertEquals(106, SpeedCacheUtils.getSpeed("line1", "CLASSIC"));
        assertEquals(198, SpeedCacheUtils.getSpeed("line1", "KERNEL"));
        assertEquals(240, SpeedCacheUtils.getSpeed("line2", "CLASSIC"));
        assertEquals(0, SpeedCacheUtils.getSpeed("line2", "KERNEL"));
    }

    @Test
    void getSpeedForUnknownLineOrProduct() {
        assertNull(SpeedCacheUtils.getSpeed("line3", "CLASSIC"));
        assertNull(SpeedCacheUtils.getSpeed("line1", "UNKNOWN_TYPE"));
    }

    @Test
    void getSpeedForNullInputs() {
        assertNull(SpeedCacheUtils.getSpeed(null, "CLASSIC"));
        assertNull(SpeedCacheUtils.getSpeed("line1", null));
        assertNull(SpeedCacheUtils.getSpeed(null, null));
    }
}
