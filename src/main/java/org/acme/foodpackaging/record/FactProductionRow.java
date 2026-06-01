package org.acme.foodpackaging.record;

import java.time.LocalDateTime;

public record FactProductionRow(
        String idBatch,
        String kmc,
        LocalDateTime dtv,
        Integer np,
        Integer eventType,
        LocalDateTime eventTime,
        String lineIdFact
) {}

