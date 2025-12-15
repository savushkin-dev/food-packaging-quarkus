package org.acme.foodpackaging.record;

import java.math.BigDecimal;
import java.sql.Timestamp;

public record DbJobRow(
        BigDecimal snpz,       // numeric(12,0)
        String kmc,
        String shortName,      // SNM
        Timestamp dti,
        Integer np,
        double mass,
        Integer quantity,      // KOLEV
        Integer priority,      // UX
        String krc,
        Timestamp pdtn,
        Timestamp pdto,
        BigDecimal pdur        // numeric(7,2)
) {}
