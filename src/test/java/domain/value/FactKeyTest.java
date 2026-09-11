package domain.value;

import org.acme.foodpackaging.domain.value.FactKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FactKey} and its nested {@link FactKey.EventType}.
 */
class FactKeyTest {

    // ===== record accessors =====

    @Test
    void exposesIdBatchAndEventType() {
        FactKey key = new FactKey("BATCH1", FactKey.EventType.START_FACT);

        assertEquals("BATCH1", key.idBatch());
        assertEquals(FactKey.EventType.START_FACT, key.eventType());
    }

    @Test
    void allowsNullIdBatch() {
        FactKey key = new FactKey(null, FactKey.EventType.START_CAMERA);

        assertNull(key.idBatch());
        assertEquals(FactKey.EventType.START_CAMERA, key.eventType());
    }

    // ===== record equality (also used as a Map key) =====

    @Test
    void equalKeysAreEqualAndShareHashCode() {
        FactKey first = new FactKey("BATCH1", FactKey.EventType.END_CAMERA);
        FactKey second = new FactKey("BATCH1", FactKey.EventType.END_CAMERA);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void differentIdBatchOrEventTypeAreNotEqual() {
        FactKey key = new FactKey("BATCH1", FactKey.EventType.START_FACT);

        assertNotEquals(key, new FactKey("BATCH2", FactKey.EventType.START_FACT));
        assertNotEquals(key, new FactKey("BATCH1", FactKey.EventType.START_CAMERA));
    }

    // ===== EventType.code() =====

    @Test
    void eventTypeCodesMatchMsLogEventColumn() {
        assertEquals(1, FactKey.EventType.START_FACT.code());
        assertEquals(2, FactKey.EventType.START_CAMERA.code());
        assertEquals(3, FactKey.EventType.END_CAMERA.code());
    }

    // ===== EventType.fromCode() =====

    @Test
    void fromCode_resolvesEachKnownCode() {
        assertEquals(FactKey.EventType.START_FACT, FactKey.EventType.fromCode(1));
        assertEquals(FactKey.EventType.START_CAMERA, FactKey.EventType.fromCode(2));
        assertEquals(FactKey.EventType.END_CAMERA, FactKey.EventType.fromCode(3));
    }

    @Test
    void fromCode_throwsForUnknownCode() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> FactKey.EventType.fromCode(0));

        assertTrue(ex.getMessage().contains("0"));
    }
}
