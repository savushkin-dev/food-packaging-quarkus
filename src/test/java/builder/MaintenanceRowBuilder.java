package builder;

import org.acme.foodpackaging.dto.oeepev.MaintenanceRow;
import java.time.LocalDateTime;
import java.time.Month;

public class MaintenanceRowBuilder {

    private LocalDateTime startProductionDateTime =
            LocalDateTime.of(2025, Month.JANUARY, 15, 9, 34);

    private Integer duration = 6;

    private String note = "Delay Note";
    private Long fId = 1L;
    private String lineId = "L1";
    private Integer eventTypeId = 2;

    public static MaintenanceRowBuilder aRow() {
        return new MaintenanceRowBuilder();
    }

    public MaintenanceRowBuilder withFId(Long fId) {
        this.fId = fId;
        return this;
    }

    public MaintenanceRowBuilder withEventTypeId(Integer eventTypeId) {
        this.eventTypeId = eventTypeId;
        return this;
    }

    public MaintenanceRowBuilder withDuration(Integer duration) {
        this.duration = duration;
        return this;
    }

    public MaintenanceRowBuilder withStartProductionDateTime(
            LocalDateTime startProductionDateTime
    ) {
        this.startProductionDateTime = startProductionDateTime;
        return this;
    }

    public MaintenanceRowBuilder withLineId(String lineId) {
        this.lineId = lineId;
        return this;
    }

    public MaintenanceRowBuilder withNote(String note) {
        this.note = note;
        return this;
    }

    public MaintenanceRow build() {
        return new MaintenanceRow(
                fId,
                lineId,
                note,
                startProductionDateTime,
                duration,
                eventTypeId
        );
    }
}

