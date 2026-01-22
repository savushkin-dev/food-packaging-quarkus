package org.acme.foodpackaging.persistence.upload;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import org.acme.foodpackaging.exception.UploadDataException;
import org.acme.foodpackaging.domain.Job;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;

import static io.smallrye.config._private.ConfigLogging.log;
import static org.acme.foodpackaging.sql.SqlQueries.*;

@ApplicationScoped
public class UploadDataService {

    private final EntityManager entityManager;
    private final String ksk;
    private final String krca;

    @Inject
    public UploadDataService(
            EntityManager entityManager,
            @ConfigProperty(name = "ksk") String ksk,
            @ConfigProperty(name = "krca") String krca
    ) {
        this.entityManager = entityManager;
        this.ksk = ksk;
        this.krca = krca;
    }

    /**
     * Отправляет задачи в работу (UPDATE_WORK + процедура)
     * Фильтрует сервисные операции перед обработкой
     */
    @Transactional
    public void sendToWork(List<Job> jobs) {
        List<Job> jobsToProcess = jobs.stream()
                .filter(job -> job.getSnpz() != null)
                .toList();

        if (jobsToProcess.isEmpty()) {
            log.info("No jobs with non-null snpz to process");
            return;
        }

        try {
            for (Job job : jobsToProcess) {
                entityManager.createNativeQuery(UPDATE_WORK)
                        .setParameter(1, job.getLine().getId())
                        .setParameter(2, job.getStartProductionDateTime())
                        .setParameter(3, job.getEndDateTime())
                        .setParameter(4, job.getDuration().toMinutes())
                        .setParameter(5, job.getSnpz())
                        .executeUpdate();
            }
            entityManager.createNativeQuery(REFRESH_FASP)
                    .setParameter(1, krca)
                    .setParameter(2, ksk)
                    .executeUpdate();

            log.info("Successfully UPDATE_WORK for " + jobsToProcess.size() + " jobs");
        } catch (PersistenceException e) {
            log.error("Error UPDATE_WORK", e);
            throw new UploadDataException("Failed to execute UPDATE_WORK and REFRESH_FASP", e);
        }
    }
}

