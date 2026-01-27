package org.acme.foodpackaging.record;

import java.sql.Timestamp;

public record CameraFactRow (
    String idBatch,
    Timestamp cameraStart,
    Timestamp cameraEnd
) {}
