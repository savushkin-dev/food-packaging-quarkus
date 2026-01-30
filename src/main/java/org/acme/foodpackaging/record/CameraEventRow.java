package org.acme.foodpackaging.record;

import java.sql.Timestamp;

public record CameraEventRow(
        String idBatch,
        Integer eventType,
        Timestamp eventTime
) {}

