package org.acme.foodpackaging.record;

import java.sql.Timestamp;

public record MsLogInsertRow(
    String idBatch,
    String productId,
    String lineIdFact,
    Integer np,
    Integer eventType,
    Timestamp dtv,
    Timestamp eventTime
) {}