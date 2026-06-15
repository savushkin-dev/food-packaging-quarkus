package org.acme.foodpackaging.persistence.load;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.record.CameraFactRow;
import org.acme.foodpackaging.record.CameraValue;
import org.acme.foodpackaging.repository.PmLogRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import java.util.HashMap;

@ApplicationScoped
public class CameraDataLoader {

    private final PmLogRepository pmLogRepository;

    @Inject
    public CameraDataLoader(PmLogRepository pmLogRepository) {
        this.pmLogRepository = pmLogRepository;
    }

    public Map<String, CameraValue> loadCameraRowMap(List<Job> jobs) {

        Map<String, CameraValue> result = new HashMap<>();

        for (String idBatch : jobs.stream()
                .map(Job::getIdBatch)
                .filter(Objects::nonNull)
                .distinct()
                .toList()) {

            CameraFactRow row = pmLogRepository.getCameraFactRow(idBatch);

            if (row != null) {
                result.put(idBatch, new CameraValue(
                        row.cameraStart(),
                        row.cameraEnd()));
            }
        }

        return result;
    }
}