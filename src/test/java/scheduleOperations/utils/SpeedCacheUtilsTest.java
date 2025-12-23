package scheduleOperations.utils;

import org.acme.foodpackaging.scheduleOperations.utils.SpeedCacheUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SpeedCacheUtilsTest {

    @BeforeEach
    void setup() {
        // Инициализация карты скоростей
        Map<String, Map<String, Integer>> speeds = new HashMap<>();

        Map<String, Integer> line1Speeds = new HashMap<>();
        line1Speeds.put("CLASSIC", 106);
        line1Speeds.put("KERNEL", 198);
        speeds.put("line1", line1Speeds);

        Map<String, Integer> line2Speeds = new HashMap<>();
        line2Speeds.put("CLASSIC", 240);
        line2Speeds.put("KERNEL", 0);
        speeds.put("line2", line2Speeds);

        SpeedCacheUtils.init(speeds);
    }

    @Test
    void initLineSpeeds() {
        assertNotNull(SpeedCacheUtils.getLineSpeeds());
        assertEquals(2, SpeedCacheUtils.getLineSpeeds().get("line1").size());
        assertEquals(198, SpeedCacheUtils.getLineSpeeds().get("line1").get("KERNEL"));
        assertEquals(0, SpeedCacheUtils.getLineSpeeds().get("line2").get("KERNEL"));
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
