package org.acme.foodpackaging.service.jobs;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.dto.DbMaintenanceRow;
import org.acme.foodpackaging.entity.jobs.OeePev;
import org.acme.foodpackaging.repository.jobs.BdVpmcRepository;
import org.acme.foodpackaging.repository.jobs.OeePevRepository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class JobSaveService {

    @Inject
    BdVpmcRepository bdVpmcRepository;

    @Inject
    OeePevRepository oeePevRepository;


    @Transactional
    public void saveJobsByType(PackagingSchedule schedule) {

        for (Long key : schedule.getDbMaintenanceRowMap().keySet()) {
            DbMaintenanceRow dbMaintenanceRow = schedule.getDbMaintenanceRowMap().get(key);
            if(dbMaintenanceRow.getFDel() == 1){
                OeePev existing = oeePevRepository.findByFId(dbMaintenanceRow.getFId());
                existing.setFDel((short) 1);
                oeePevRepository.persist(existing);
            }
        }

        List<Job> updatedJobs = new ArrayList<>(schedule.getJobs());

        for (int i = 0; i < schedule.getJobs().size(); i++) {

            Job job = schedule.getJobs().get(i);

            if (job.isMaintenance()) {
                OeePev entityForInsert = OeePev.builder()
                        .lineId(job.getLine().getId())
                        .startProductionDateTime(job.getStartProductionDateTime())
                        .endDateTime(job.getEndDateTime())
                        .duration((int)job.getDuration().toMinutes()
                        )
                        .maintenanceTypeId(job.getMaintenanceTypeId())
                        .reason(null)
                        .note(job.getMaintenanceNote())
                        .snpz(0L)
                        .build();

                if (!job.getId().startsWith("MAINTENANCE")) {
                    OeePev existing = oeePevRepository.findByFId(job.getFId());
                    if (existing != null) {
                        existing.setLineId(job.getLine().getId());
                        existing.setStartProductionDateTime(job.getStartProductionDateTime());
                        existing.setEndDateTime(job.getEndDateTime());
                        existing.setDuration((int) Duration.between(
                                job.getStartProductionDateTime(),
                                job.getEndDateTime()
                        ).toMinutes());
                        existing.setMaintenanceTypeId(null);
                        existing.setReason(null);
                        existing.setNote(job.getName());
                        existing.setSnpz(0L);

                        oeePevRepository.persist(existing);
                    } else {
                        oeePevRepository.persist(entityForInsert);
                    }
                } else {
                    oeePevRepository.persist(entityForInsert);

                    //При сохранении сервисной операции получаем назначенный fId и присваиваем в план сессии
                    long fId = entityForInsert.getFId();
                    job.setId(String.valueOf(fId));
                    job.setFId(fId);
                    updatedJobs.set(i, job);
                }

            } else {

                if (!job.getStartCleaningDateTime().isEqual(job.getStartProductionDateTime())) {

                    OeePev existing = oeePevRepository.findBySnpz(job.getSnpz());
                    if (existing != null) {
                        existing.setLineId(job.getLine().getId());
                        existing.setStartProductionDateTime(job.getStartCleaningDateTime());
                        existing.setEndDateTime(job.getStartProductionDateTime());
                        existing.setDuration((int) Duration.between(
                                job.getStartCleaningDateTime(),
                                job.getStartProductionDateTime()
                        ).toMinutes());
                        existing.setMaintenanceTypeId(null);
                        existing.setReason(null);
                        existing.setNote("Мойка, переналадка");

                        oeePevRepository.persist(existing);
                    } else {
                        OeePev entityForInsert = OeePev.builder()
                                .lineId(job.getLine().getId())
                                .startProductionDateTime(job.getStartCleaningDateTime())
                                .endDateTime(job.getStartProductionDateTime())
                                .duration((int) Duration.between(
                                        job.getStartCleaningDateTime(),
                                        job.getStartProductionDateTime()
                                ).toMinutes())
                                .maintenanceTypeId(null)
                                .reason(null)
                                .note("Мойка, переналадка")
                                .snpz(job.getSnpz())
                                .build();

                        oeePevRepository.persist(entityForInsert);
                    }
                }

                bdVpmcRepository.updateBySnpz(
                        job.getSnpz(),
                        job.getStartProductionDateTime(),
                        job.getEndDateTime(),
                        (int) Duration.between(
                                job.getStartProductionDateTime(),
                                job.getEndDateTime()
                        ).toMinutes(),
                        job.getLine().getId()
                );

            }
        }
        schedule.setJobs(updatedJobs);
    }
}
