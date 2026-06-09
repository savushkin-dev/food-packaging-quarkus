package org.acme.foodpackaging.persistence.load;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.exception.service.CameraDataReadException;
import org.acme.foodpackaging.record.CameraFactRow;
import org.acme.foodpackaging.record.CameraValue;
import org.acme.foodpackaging.repository.PmLogRepository;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class CameraDataLoader {

    private final PmLogRepository pmLogRepository;

    @Inject
    public CameraDataLoader(PmLogRepository pmLogRepository) {
        this.pmLogRepository = pmLogRepository;
    }

    public Map<String, CameraValue> loadCameraRowMap(Iterable<Job> jobs)
            throws CameraDataReadException {

        Map<String, CameraValue> result = new HashMap<>();
        Set<String> processedBatches = new HashSet<>();

        for (Job job : jobs) {

            String idBatch = job.getIdBatch();

            if (idBatch == null || !processedBatches.add(idBatch)) {
                continue;
            }

            CameraFactRow row = pmLogRepository.getCameraFactRow(idBatch);

            if (row != null) {
                result.put(
                        idBatch,
                        new CameraValue(
                                row.cameraStart(),
                                row.cameraEnd()
                        )
                );
            }
        }

        return result;
    }
}