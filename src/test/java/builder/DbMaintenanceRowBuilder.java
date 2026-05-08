package builder;

import jakarta.persistence.criteria.CriteriaBuilder;
import org.acme.foodpackaging.dto.DbMaintenanceRow;
import java.time.LocalDateTime;

public class DbMaintenanceRowBuilder {

    private LocalDateTime start = LocalDateTime.of(2025, 1, 15, 9, 34);
    private LocalDateTime end = LocalDateTime.of(2025, 1, 15, 9, 40);

    private int duration = 6;
    private String note = "Delay Note";
    private Long id = 1L;
    private String lineId = "L1";
    private int maintenanceTypeId = 2;

    public static DbMaintenanceRowBuilder aRow() {
        return new DbMaintenanceRowBuilder();
    }

    public DbMaintenanceRowBuilder withId(Long id) {
        this.id = id;
        return this;
    }

    public DbMaintenanceRowBuilder withMaintenanceTypeId(int typeId) {
        this.maintenanceTypeId = typeId;
        return this;
    }

    public DbMaintenanceRowBuilder withDuration(int duration) {
        this.duration = duration;
        return this;
    }

    public DbMaintenanceRowBuilder withStart(LocalDateTime start) {
        this.start = start;
        return this;
    }

    public DbMaintenanceRowBuilder withEnd(LocalDateTime end) {
        this.end = end;
        return this;
    }

    public DbMaintenanceRowBuilder withLineId(String lineId) {
        this.lineId = lineId;
        return this;
    }

    public DbMaintenanceRowBuilder withNote(String note) {
        this.note = note;
        return this;
    }

    public DbMaintenanceRow build() {
        return new DbMaintenanceRow(
                id,
                (short) 0,
                lineId,
                start,
                end,
                duration,
                123L,
                maintenanceTypeId,
                note
        );
    }
}

