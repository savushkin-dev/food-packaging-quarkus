package org.acme.foodpackaging.service.jobs;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.repository.PmLogRepository;

import java.time.format.DateTimeFormatter;

@ApplicationScoped
public class JobInfoService {

    @Inject
    PmLogRepository pmLogRepository;

    public PackagingSchedule determineFactPlace(PackagingSchedule solution, long snpz){

        Job job = solution.getJobIdMap().get(snpz);

        String idBatch = generateIdBatch(job, snpz, solution);

        int emk = job.getEmk();
        double mass = job.getProduct().getMass();

        long countBoxes = pmLogRepository.countByIdBatchNative(idBatch);
        int countPieces = (int) (countBoxes * emk);
        int batchWeight = (int) (countPieces * mass);

        String resultInfo = String.format(
                "%d (%d шт., %d кг.)",
                countBoxes, countPieces, batchWeight
        );

        solution.getJobIdMap().get(snpz).setPlaceFactInfo(resultInfo);

        return solution;
    }

    public String generateIdBatch(Job job, long snpz, PackagingSchedule solution){
        String ean13 = job.getProduct().getEan13().substring(0, 12) + "0";
        String formattedNp = String.format("%09d", job.getNp());

        String dateToIdBatch = solution.getDbJobRowMap().get(snpz).dti().toLocalDateTime()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd"));;

        return ean13 + dateToIdBatch + formattedNp;
    }



}
