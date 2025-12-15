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
        Timestamp startProductionDateTime,
        Timestamp endDateTime,
        Integer duration        // numeric(7,2)
) {}
