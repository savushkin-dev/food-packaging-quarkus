package org.acme.foodpackaging.persistence.upload;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.entity.solution.PlrPlan;
import org.acme.foodpackaging.exception.service.InvalidSolutionException;
import org.acme.foodpackaging.exception.service.SolutionParsingException;
import org.acme.foodpackaging.repository.solution.PlrPlanRepository;

import java.util.UUID;

@ApplicationScoped
public class SolutionVersionExportService {

    private final ObjectMapper objectMapper;
    private final PlrPlanRepository repository;

    @Inject
    public SolutionVersionExportService(ObjectMapper objectMapper,
                                 PlrPlanRepository repository) {
        this.objectMapper = objectMapper;
        this.repository = repository;
    }

    @Transactional
    public void export(PackagingSchedule schedule,
                       String version) {

        if (schedule == null) {
            throw new InvalidSolutionException("Schedule is null");
        }

        try {
            String json = objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(schedule);

            PlrPlan entity = new PlrPlan();
            entity.setId(UUID.randomUUID());
            entity.setDti(schedule.getDti());
            entity.setVersion(version);
            entity.setSolutionJson(json);

            repository.persist(entity);

        } catch (JsonProcessingException e) {
            throw new SolutionParsingException(
                    "Failed to serialize PackagingSchedule", e
            );
        }
    }
}
