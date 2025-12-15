package org.acme.foodpackaging.factory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.persistence.load.LoadDataService;

import java.time.Duration;
import java.time.LocalDateTime;

@ApplicationScoped
public class JobFactory {

    @Inject
    LoadDataService loadDataService;

    public Job createJob(String id, int snpz, int np, String jobName, Product product, double mass,
            int quantity, int duration, LocalDateTime minStartDateTime,
                         LocalDateTime idealEndDateTime,
                         LocalDateTime maxEndDateTime,  int priority, LocalDateTime startProductionDateTime) {

        return new Job(id, snpz, np, jobName, product, mass, quantity, Duration.ofMinutes(duration),
                minStartDateTime, idealEndDateTime, maxEndDateTime, priority, null, startProductionDateTime);
    }

    public String nameCleaner(String input) {
        return input.replaceFirst(
                "(?i)Сырок\\s*(тв\\.\\s*г\\.с|тв\\.\\s*гл\\.с|тв\\.\\s*гл\\.|тв\\.\\s*г\\.|гл\\.|тв\\.\\s*глазированный|глазированный|тв\\.\\s*глазир\\.)",
                ""
        ).trim();
    }
}
