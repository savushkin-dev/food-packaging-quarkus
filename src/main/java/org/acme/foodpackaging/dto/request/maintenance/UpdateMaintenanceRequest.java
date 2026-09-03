package org.acme.foodpackaging.dto.request.maintenance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateMaintenanceRequest(
        @NotBlank String lineId,
        @NotNull Integer updateIndex,
        Integer maintenanceTypeId,
        String maintenanceNote,
        Integer durationMinutes
) {
}