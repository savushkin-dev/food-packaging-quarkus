package org.acme.foodpackaging.dto.request.maintenance;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

public record AddMaintenanceRequest(
        String lineId,
        String maintenanceNote,
        Integer maintenanceTypeId,
        Integer durationMinutes,
        Integer insertIndex,
        Integer alignExtraCleaning,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm[:ss]") LocalDateTime startProductionDateTime
) {
    public boolean isEmptyLineMode() {
        return startProductionDateTime != null;
    }
}
