package org.acme.foodpackaging.record;

import java.sql.Timestamp;

public record FactProductionRow(
        String idBatch,
        String kmc,
        Timestamp dtv,
        Integer np,
        Integer eventType,
        Timestamp eventTime,
        String lineIdFact
) {}

