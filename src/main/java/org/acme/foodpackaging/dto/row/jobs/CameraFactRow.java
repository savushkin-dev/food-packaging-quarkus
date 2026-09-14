package org.acme.foodpackaging.dto.row.jobs;

import java.time.LocalDateTime;

public record CameraFactRow (
    LocalDateTime cameraStart,
    LocalDateTime cameraEnd
) {}
