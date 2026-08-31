package org.acme.foodpackaging.initializer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.dto.bdvzpmc.JobRow;
import org.acme.foodpackaging.record.InitData;
import org.acme.foodpackaging.service.align.AlignSolutionService;
import org.acme.foodpackaging.service.jobs.JobService;
import org.acme.foodpackaging.service.lines.LineService;
import org.acme.foodpackaging.service.products.ProductService;

import java.time.LocalDate;
import java.util.List;

import static org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils.*;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ScheduleInitializer {

    private final JobService jobService;
    private final LineService lineService;
    private final ProductService productService;
    private final AlignSolutionService alignSolutionService;

    /**
     * Строит новое расписание с нуля на заданную дату.
     * Порядок шагов важен:
     * 1. Линии должны существовать до раскладки джобов по ним.
     * 2. Джобы строятся до продуктов — продукты зависят от уже размещённых джобов.
     * 3. Дата пустого решения выставляется после наполнения — иначе перетрётся при непустом расписании.
     * 4. Джобы без линии — мусор, удаляются перед выравниванием.
     * 5. Align — последний шаг, работает с уже консистентным schedule.
     */
    public InitData initSchedule(LocalDate startDate) {
        PackagingSchedule schedule = createEmptySchedule(startDate);
        List<JobRow> jobRows = attachJobs(schedule);
        attachProducts(schedule);
        finalizeEmptyState(schedule, startDate);
        cleanupOrphanJobs(schedule);
        alignSolutionService.align(schedule);

        return new InitData(schedule, jobRows);
    }

    public PackagingSchedule updateProductList(PackagingSchedule schedule) {
        schedule.setProducts(productService.getProductList(schedule));
        return schedule;
    }

    private PackagingSchedule createEmptySchedule(LocalDate startDate) {
        return new PackagingSchedule(lineService.getLines(), startDate);
    }

    private List<JobRow> attachJobs(PackagingSchedule schedule) {
        return jobService.buildJobsOnLines(schedule);
    }

    private void attachProducts(PackagingSchedule schedule) {
        productService.buildProducts(schedule);
    }

    private void finalizeEmptyState(PackagingSchedule schedule, LocalDate startDate) {
        schedule.setDateForEmptySolution(startDate);
    }

    private void cleanupOrphanJobs(PackagingSchedule schedule) {
        removeJobsWithoutLine(schedule.getJobs());
    }
}
