package org.acme.foodpackaging.record;

import java.time.LocalDateTime;

public record CameraValue(
    LocalDateTime cameraStart,
    LocalDateTime cameraEnd
) {}

