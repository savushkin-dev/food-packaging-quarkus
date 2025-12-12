package org.acme.foodpackaging.record;

import java.time.LocalDateTime;

public record DbJobInfo(
        int snpz,
        int np,
        int quantity,
        int priority,
        double mass,
        String shortName,
        String kmc,
        LocalDateTime dti,
        LocalDateTime dtf
) {}
