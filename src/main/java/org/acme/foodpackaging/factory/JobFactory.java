package org.acme.foodpackaging.factory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.persistence.load.LoadDataService;

import java.time.LocalDateTime;

@ApplicationScoped
public class JobFactory {

    @Inject
    LoadDataService loadDataService;

    public Job createJob(String id, String jobName, int snpz, int np, Product product,
                         double mass, int quantity, int priority,
                         LocalDateTime minStartDateTime,
                         LocalDateTime idealEndDateTime,
                         LocalDateTime maxEndDateTime) {

        Job job = new Job(id, jobName, snpz, np, product, mass, quantity,
                minStartDateTime, idealEndDateTime, maxEndDateTime, priority, false);
        return job;
    }

    public String nameCleaner(String input) {
        return input.replaceFirst(
                "(?i)Сырок\\s*(тв\\.\\s*г\\.с|тв\\.\\s*гл\\.с|тв\\.\\s*гл\\.|тв\\.\\s*г\\.|гл\\.|тв\\.\\s*глазированный|глазированный|тв\\.\\s*глазир\\.)",
                ""
        ).trim();
    }
}
