package builder;

import org.acme.foodpackaging.record.DbJobRow;
import java.time.LocalDateTime;

public class DbJobRowBuilder {

    private final LocalDateTime dti = LocalDateTime.of(2025, 1, 15, 9, 0);
    private String kmc = "KMC1";
    private final LocalDateTime start = dti;
    private final LocalDateTime end = LocalDateTime.of(2025, 1, 15, 9, 30);
    private Long snpz = 123L;
    private String lineId = "L1";

    public static DbJobRowBuilder aRow() {
        return new DbJobRowBuilder();
    }

    public DbJobRowBuilder withLineId(String lineId) {
        this.lineId = lineId;
        return this;
    }

    public DbJobRowBuilder withSnpz(Long snpz) {
        this.snpz = snpz;
        return this;
    }

    public DbJobRowBuilder withKmc(String kmc) {
        this.kmc = kmc;
        return this;
    }

    public DbJobRow build() {
        String shortName = "Test Job";
        Integer priority = 1;
        Integer duration = 30;
        Integer quantity = 2600;
        Integer np = 10;
        double mass = 2.0;
        return new DbJobRow(
                dti, kmc, np, quantity, mass,
                start, end, duration,
                snpz, priority,
                lineId, shortName,
                18, 100, 1
        );
    }
}

