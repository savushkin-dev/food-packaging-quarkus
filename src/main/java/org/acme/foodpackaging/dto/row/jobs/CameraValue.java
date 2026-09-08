package org.acme.foodpackaging.dto.row.jobs;

import java.time.LocalDateTime;

public record CameraValue(
    LocalDateTime cameraStart,
    LocalDateTime cameraEnd
) {}

