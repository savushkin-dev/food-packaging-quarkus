package org.acme.foodpackaging.service.jobs;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.entity.jobs.OeePevEntity;
import org.acme.foodpackaging.repository.OeePevRepository;
import org.acme.foodpackaging.repository.VzPMCRepository;

import java.time.Duration;

@ApplicationScoped
public class JobSaveService {

    @Inject
    VzPMCRepository vzPMCRepository;

    @Inject
    OeePevRepository oeePevRepository;


    @Transactional
    public void saveJobsByType(PackagingSchedule schedule) {

        for (int i = 0; i < schedule.getJobs().size(); i++) {

            Job job = schedule.getJobs().get(i);

            if (job.isMaintenance()) {
                OeePevEntity entityForInsert = OeePevEntity.builder()
                        .krc(job.getLine().getId())
                        .pdtn(job.getStartProductionDateTime())
                        .pdto(job.getEndDateTime())
                        .pdur((int) Duration.between(
                                job.getStartCleaningDateTime(),
                                job.getEndDateTime()
                        ).toMinutes())
                        .evtype(null)
                        .reason(null)
                        .note(job.getName())
                        .snpz(job.getSnpz())
                        .build();

                if (!job.getId().startsWith("MAINTENANCE")) {
                    OeePevEntity existing = oeePevRepository.findByFId(Long.parseLong(job.getId()));
                    if (existing != null) {
                        existing.setKrc(job.getLine().getId());
                        existing.setPdtn(job.getStartProductionDateTime());
                        existing.setPdto(job.getEndDateTime());
                        existing.setPdur((int) Duration.between(
                                job.getStartProductionDateTime(),
                                job.getEndDateTime()
                        ).toMinutes());
                        existing.setEvtype(null);
                        existing.setReason(null);
                        existing.setNote(job.getName());
                        existing.setSnpz(job.getSnpz());

                        oeePevRepository.persist(existing);
                    } else {
                        oeePevRepository.persist(entityForInsert);
                    }
                } else {
                    oeePevRepository.persist(entityForInsert);
                }

            } else {

                if (!job.getStartCleaningDateTime().isEqual(job.getStartProductionDateTime())) {

                    OeePevEntity existing = oeePevRepository.findBySnpz(job.getSnpz());
                    if (existing != null) {
                        existing.setKrc(job.getLine().getId());
                        existing.setPdtn(job.getStartCleaningDateTime());
                        existing.setPdto(job.getStartProductionDateTime());
                        existing.setPdur((int) Duration.between(
                                job.getStartCleaningDateTime(),
                                job.getStartProductionDateTime()
                        ).toMinutes());
                        existing.setEvtype(null);
                        existing.setReason(null);
                        existing.setNote("Мойка, переналадка");

                        oeePevRepository.persist(existing);
                    } else {
                        OeePevEntity entityForInsert = OeePevEntity.builder()
                                .krc(job.getLine().getId())
                                .pdtn(job.getStartCleaningDateTime())
                                .pdto(job.getStartProductionDateTime())
                                .pdur((int) Duration.between(
                                        job.getStartCleaningDateTime(),
                                        job.getStartProductionDateTime()
                                ).toMinutes())
                                .evtype(null)
                                .reason(null)
                                .note("Мойка, переналадка")
                                .snpz(job.getSnpz())
                                .build();

                        oeePevRepository.persist(entityForInsert);
                    }
                }

                vzPMCRepository.updateBySnpz(
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
    }




}
