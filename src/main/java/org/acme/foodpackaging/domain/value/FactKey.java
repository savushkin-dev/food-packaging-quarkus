package org.acme.foodpackaging.domain.value;

/**
 * Ключ для сопоставления фактических данных производства (MS_LOG) с задачей
 * по номеру партии (idBatch) и типу события.
 */
public record FactKey(String idBatch, EventType eventType) {

    /**
     * Типы событий фактического производства, фиксируемые в MS_LOG.
     */
    public enum EventType {
        START_FACT(1),
        START_CAMERA(2),
        END_CAMERA(3);

        private final int code;

        EventType(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        public static EventType fromCode(int code) {
            for (EventType type : values()) {
                if (type.code == code) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown fact event type code: " + code);
        }
    }
}
