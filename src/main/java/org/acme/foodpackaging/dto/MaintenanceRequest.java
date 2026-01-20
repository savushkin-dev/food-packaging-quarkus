package org.acme.foodpackaging.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
public class MaintenanceRequest {

    private String lineId;
    private String maintenanceNote;
    private Integer maintenanceTypeId;
    private Integer durationMinutes;
    private Integer insertIndex;
    private Integer updateIndex;
    private Integer removeIndex;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm[:ss]")
    private LocalDateTime startProductionDateTime;

    public boolean isEmptyLineMode() {
        return startProductionDateTime != null;
    }

    public boolean isUpdateLineMode() {
        return updateIndex != null;
    }

    public boolean isRemoveLineMode() {
        return removeIndex != null;
    }
}
