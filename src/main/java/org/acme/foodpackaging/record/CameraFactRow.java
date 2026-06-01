package org.acme.foodpackaging.record;

import java.time.LocalDateTime;

public record CameraFactRow (
    LocalDateTime cameraStart,
    LocalDateTime cameraEnd
) {}
