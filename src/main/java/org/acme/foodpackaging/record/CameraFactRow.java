package org.acme.foodpackaging.record;

import java.sql.Timestamp;

public record CameraFactRow (
    Timestamp cameraStart,
    Timestamp cameraEnd
) {}
