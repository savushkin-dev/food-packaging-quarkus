package org.acme.foodpackaging.service.jobs;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.dto.row.jobs.CameraFactRow;
import org.acme.foodpackaging.repository.PmLogRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@ApplicationScoped
public class JobInfoService {


    private final PmLogRepository pmLogRepository;

    @Inject
    public JobInfoService(PmLogRepository pmLogRepository) {
        this.pmLogRepository = pmLogRepository;
    }

    public PackagingSchedule findFactPlace(PackagingSchedule solution, long snpz){
        if (solution == null) {
            return null;
        }

        Job job = solution.getAllJobsById().get(snpz);
        if (job == null) {
            return solution;
        }

        String idBatch = generateIdBatch(solution, snpz);

        int emk = job.getEmk();
        double mass = job.getProduct().getMass();

        long countBoxes = pmLogRepository.countByIdBatch(idBatch);
        int countPieces = (int) (countBoxes * emk);
        int batchWeight = (int) (countPieces * mass);

        String resultInfo = String.format(
                "%d (%d шт., %d кг.)",
                countBoxes, countPieces, batchWeight
        );

        solution.getAllJobsById().get(snpz).setPlaceFactInfo(resultInfo);

        return solution;
    }

    public PackagingSchedule findCameraFact(PackagingSchedule solution, long snpz){
        if (solution == null) {
            return null;
        }

        Job job = solution.getAllJobsById().get(snpz);
        if (job == null) {
            return solution;
        }

        String idBatch = generateIdBatch(solution, snpz);

        CameraFactRow cameraFact = pmLogRepository.getCameraFactRow(idBatch);

        LocalDateTime start = cameraFact.cameraStart() != null ?
                cameraFact.cameraStart() : null;
        LocalDateTime end = cameraFact.cameraEnd() != null ?
                cameraFact.cameraEnd() : null;

        solution.getAllJobsById().get(snpz).setCameraStart(start);
        solution.getAllJobsById().get(snpz).setCameraEnd(end);

        return solution;
    }

    public String generateIdBatch(PackagingSchedule solution, long snpz){
        Job job = solution.getAllJobsById().get(snpz);
        if (job == null) {
            throw new IllegalArgumentException("Job not found: " + snpz);
        }
        return generateIdBatch(job);
    }

    /**
     * Генерирует idBatch по данным самой задачи, без обращения к schedule.
     * Используется при создании задачи (в BD_VZPMC значение партии не хранится).
     */
    public String generateIdBatch(Job job) {
        String ean13 = job.getProduct().getEan13().substring(0, 12) + "0";
        String formattedNp = String.format("%09d", job.getNp());
        String dateToIdBatch = job.getDti().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        return ean13 + dateToIdBatch + formattedNp;
    }
}
