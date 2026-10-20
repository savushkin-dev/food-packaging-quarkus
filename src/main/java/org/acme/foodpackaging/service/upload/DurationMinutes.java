package org.acme.foodpackaging.service.upload;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

final class DurationMinutes {

    private DurationMinutes() {
    }

    static int between(LocalDateTime start, LocalDateTime end) {
        ZoneId zoneId = ZoneId.systemDefault();
        if (start == null || end == null) {
            return 0;
        }
        return (int) Duration.between(start.atZone(zoneId), end.atZone(zoneId)).toMinutes();
    }

    static int of(Duration duration) {
        if (duration == null) return 0;
        return (int) duration.toMinutes();
    }
}
