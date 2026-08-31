package builder;

import org.acme.foodpackaging.dto.bdvzpmc.JobRow;

import java.time.LocalDateTime;
import java.time.Month;

public class JobRowBuilder {

    private final LocalDateTime dti = LocalDateTime.of(2025, Month.JANUARY, 15, 9, 0);
    private String kmc = "KMC1";
    private final LocalDateTime start = dti;
    private final LocalDateTime end = LocalDateTime.of(2025, Month.JANUARY, 15, 9, 30);
    private Long snpz = 123L;
    private String lineId = "L1";

    private Integer emk = 18;
    private Integer np = 10;
    private Integer quantity = 2600;
    private Integer priority = 1;
    private Integer duration = 30;
    private Integer placePlan = 100;
    private Integer shift = 1;

    public static JobRowBuilder aRow() {
        return new JobRowBuilder();
    }

    public JobRowBuilder withLineId(String lineId) {
        this.lineId = lineId;
        return this;
    }

    public JobRowBuilder withSnpz(Long snpz) {
        this.snpz = snpz;
        return this;
    }

    public JobRowBuilder withKmc(String kmc) {
        this.kmc = kmc;
        return this;
    }

    public JobRowBuilder withEmk(Integer emk) {
        this.emk = emk;
        return this;
    }

    public JobRowBuilder withNp(Integer np) {
        this.np = np;
        return this;
    }

    public JobRowBuilder withQuantity(Integer quantity) {
        this.quantity = quantity;
        return this;
    }

    public JobRowBuilder withPriority(Integer priority) {
        this.priority = priority;
        return this;
    }

    public JobRowBuilder withDuration(Integer duration) {
        this.duration = duration;
        return this;
    }

    public JobRowBuilder withPlacePlan(Integer placePlan) {
        this.placePlan = placePlan;
        return this;
    }

    public JobRowBuilder withShift(Integer shift) {
        this.shift = shift;
        return this;
    }

    public JobRow build() {
        String shortName = "Test Job";
        double mass = 2.0;

        return new JobRow(
                dti, kmc, np, quantity, mass,
                start, end, duration,
                snpz, priority,
                lineId, shortName,
                emk, placePlan, shift
        );
    }
}

