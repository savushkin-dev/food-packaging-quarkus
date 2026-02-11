package org.acme.foodpackaging.record;

import org.acme.foodpackaging.dto.DbMaintenanceRow;

import java.util.Map;

public record MaintenanceData(
        Map<Long, DbMaintenanceRow> maintenanceByFid,
        Map<Long, DbMaintenanceRow> cleaningBySnpz
) {}
