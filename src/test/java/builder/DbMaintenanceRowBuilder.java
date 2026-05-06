package builder;

import org.acme.foodpackaging.dto.DbMaintenanceRow;
import java.time.LocalDateTime;

public class DbMaintenanceRowBuilder {
    private final LocalDateTime start = LocalDateTime.of(2025, 1, 15, 9, 34);
    private final LocalDateTime end = LocalDateTime.of(2025, 1, 15, 9, 40);

    public static DbMaintenanceRowBuilder aRow() {
        return new DbMaintenanceRowBuilder();
    }

    public DbMaintenanceRow build() {
        int duration = 6;
        String note = "Delay Note";
        Long id = 1L;
        String lineId = "L1";
        return new DbMaintenanceRow(
                id, (short) 0, lineId,
                start, end, duration,
                123L, 1, note
        );
    }
}
