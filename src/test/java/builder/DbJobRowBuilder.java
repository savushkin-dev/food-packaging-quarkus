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

    private Integer emk = 18;
    private Integer np = 10;
    private Integer quantity = 2600;
    private Integer priority = 1;
    private Integer duration = 30;
    private Integer placePlan = 100;
    private Integer shift = 1;

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

    public DbJobRowBuilder withEmk(Integer emk) {
        this.emk = emk;
        return this;
    }

    public DbJobRowBuilder withNp(Integer np) {
        this.np = np;
        return this;
    }

    public DbJobRowBuilder withQuantity(Integer quantity) {
        this.quantity = quantity;
        return this;
    }

    public DbJobRowBuilder withPriority(Integer priority) {
        this.priority = priority;
        return this;
    }

    public DbJobRowBuilder withDuration(Integer duration) {
        this.duration = duration;
        return this;
    }

    public DbJobRowBuilder withPlacePlan(Integer placePlan) {
        this.placePlan = placePlan;
        return this;
    }

    public DbJobRowBuilder withShift(Integer shift) {
        this.shift = shift;
        return this;
    }

    public DbJobRow build() {
        String shortName = "Test Job";
        double mass = 2.0;

        return new DbJobRow(
                dti, kmc, np, quantity, mass,
                start, end, duration,
                snpz, priority,
                lineId, shortName,
                emk, placePlan, shift
        );
    }
}
