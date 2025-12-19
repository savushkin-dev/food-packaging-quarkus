package org.acme.foodpackaging.record;

import java.math.BigDecimal;
import java.sql.Timestamp;

public record DbMaintenanceRow(
        Integer f_id,
        String lineId,
        Timestamp startProductionDateTime,
        Timestamp endDateTime,
        Integer duration,
        BigDecimal snpz,
        String shortName
) {}
