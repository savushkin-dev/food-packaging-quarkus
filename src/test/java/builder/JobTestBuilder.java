package builder;

import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.Product;

import java.time.Duration;
import java.time.LocalDateTime;

public class JobTestBuilder {

    private final Job job = new Job();

    private JobTestBuilder() {}

    public static JobTestBuilder aJob() {
        return new JobTestBuilder();
    }

    public JobTestBuilder withId(String id) {
        job.setId(id);
        return this;
    }

    public JobTestBuilder withProduct(Product product) {
        job.setProduct(product);
        return this;
    }

    public JobTestBuilder withDurationMinutes(long minutes) {
        job.setDuration(Duration.ofMinutes(minutes));
        return this;
    }

    public JobTestBuilder asMaintenance() {
        job.setMaintenance(true);
        return this;
    }

    public JobTestBuilder startingAt(LocalDateTime start) {
        job.setStartCleaningDateTime(start);
        job.setStartProductionDateTime(start);
        if (job.getDuration() != null) {
            job.setEndDateTime(start.plus(job.getDuration()));
        }
        return this;
    }

    public JobTestBuilder withCamera(LocalDateTime start, LocalDateTime end) {
        job.setCameraStart(start);
        job.setCameraEnd(end);
        return this;
    }

    public JobTestBuilder withStartProductionDateTime(LocalDateTime startProductionDateTime) {
        job.setStartProductionDateTime(startProductionDateTime);
        return this;
    }

    public JobTestBuilder withStartCleaningDateTime(LocalDateTime startCleaningDateTime) {
        job.setStartCleaningDateTime(startCleaningDateTime);
        return this;
    }

    public JobTestBuilder withEndDateTime(LocalDateTime endDateTime) {
        job.setEndDateTime(endDateTime);
        return this;
    }


    public JobTestBuilder withQuantity(int quantity){
        job.setQuantity(quantity);
        return this;
    }

    public JobTestBuilder withLine(Line line) {
        job.setLine(line);
        return this;
    }

    public JobTestBuilder withLineIdFact(String lineId) {
        job.setLineIdFact(lineId);
        return this;
    }

    public JobTestBuilder withCleaningDelay(Duration delay) {
        job.setCleaningDelay(delay);
        return this;
    }

    public Job build() {
        return job;
    }
}