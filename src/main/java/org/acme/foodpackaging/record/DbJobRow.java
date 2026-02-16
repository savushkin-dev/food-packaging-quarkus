package org.acme.foodpackaging.record;

import java.sql.Timestamp;

public record DbJobRow(
        Timestamp dti,
        String kmc,
        Integer np,
        Integer quantity,                          // KOLEV
        double mass,
        Timestamp startProductionDateTime,       // PDTN
        Timestamp endDateTime,
        Integer duration,                      // numeric(7,2)
        Long snpz,                            // numeric(12,0)
        Integer priority,                    // UX
        String lineId,                      // krc
        String shortName,                // SNM
        Integer emk
) {}
