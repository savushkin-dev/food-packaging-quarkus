package org.acme.foodpackaging.service.load;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.dto.row.jobs.CameraFactRow;
import org.acme.foodpackaging.repository.PmLogRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import java.util.HashMap;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class CameraDataLoader {

    private final PmLogRepository pmLogRepository;

    public Map<String, CameraFactRow> loadCameraRowMap(List<Job> jobs) {

        Map<String, CameraFactRow> result = HashMap.newHashMap(jobs.size());

        for (String idBatch : jobs.stream()
                .map(Job::getIdBatch)
                .filter(Objects::nonNull)
                .distinct()
                .toList()) {

            CameraFactRow row = pmLogRepository.getCameraFactRow(idBatch);

            if (row != null) {
                result.put(idBatch, row);
            }
        }

        return result;
    }
}