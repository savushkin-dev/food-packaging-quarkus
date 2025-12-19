package org.acme.foodpackaging.solver.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.record.DbJobRow;
import org.acme.foodpackaging.repository.jobs.JobRepository;
import org.acme.foodpackaging.service.jobs.JobRefreshService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class JobRefreshServiceTest {

    @Inject
    JobRepository repository;
    @Inject
    JobRefreshService service;

    List<Job> jobs;
    List<Job> exampleJobs;
    Map<Integer, Job> jobIdMap;
    Map<Integer, DbJobRow> dbJobRowMap;

    @BeforeEach
    void setup() {
        jobs = new ArrayList<>();
        exampleJobs = new ArrayList<>();
        jobIdMap = new HashMap<>();
        dbJobRowMap = new HashMap<>();

        LocalDateTime minStartDate = LocalDateTime.of(2025,12,17,8,0);
        LocalDateTime idealEndDateTime = LocalDateTime.of(2025,12,20,2,0);
        LocalDateTime maxEndDateTime = LocalDateTime.of(2025,12,20,7,0);

        LocalDateTime startProductionDateTime1 = LocalDateTime.of(2025,12,17,8,0);
        LocalDateTime startProductionDateTime2 = LocalDateTime.of(2025,12,17,8,26);;

        dbJobRowMap.put(
                1, new DbJobRow(
                        BigDecimal.valueOf(1), "KMC-001", "Сырок глазированный ваниль",
                        Timestamp.valueOf(LocalDateTime.of(2025,12,14,8,0)),
                        40, 40, 5720, 0, "170610010000",
                        Timestamp.valueOf(LocalDateTime.of(2025,12,17,8,0)),
                        Timestamp.valueOf(LocalDateTime.of(2025,12,17,8,26)), 26
                        ));

        dbJobRowMap.put(
              2, new DbJobRow(
                        BigDecimal.valueOf(1), "KMC-001", "Сырок глазированный ваниль",
                        Timestamp.valueOf(LocalDateTime.of(2025,12,14,8,0)),
                        40, 40, 5110, 0, "170610020000",
                        Timestamp.valueOf(LocalDateTime.of(2025,12,17,8,26)),
                        Timestamp.valueOf(LocalDateTime.of(2025,12,17,8,56)), 30
                ));

        Product product = new Product(
                "Сырок глазированный ванильный", "KMC-001", "170610010000",
                "CLASSIC", "10004", "11", "0"
        );

        Job job1 = new Job(
                "1","170610010000", 1, 40, "Сырок глазированный ваниль", product,
                40, 5720, Duration.ofMinutes(26), minStartDate, idealEndDateTime, maxEndDateTime, 0,null, startProductionDateTime1
        );

        Job job2 = new Job(
                "2","170610010000", 2, 40, "Сырок глазированный ваниль", product,
                40, 6600, Duration.ofMinutes(30), minStartDate, idealEndDateTime, maxEndDateTime, 0,null, startProductionDateTime2
        );

        Line line2 = new Line("170610010000", "Линия №2");
        job1.setLine(line2);
        job2.setLine(line2);
        exampleJobs.add(job1);
        exampleJobs.add(job2);

       line2.setJobs(exampleJobs);

       jobIdMap.put(
                1, exampleJobs.getFirst());

        jobIdMap.put(
                2, exampleJobs.getLast());
        repository.setJobs(exampleJobs);
        repository.setJobIdMap(jobIdMap);
        repository.setDbJobRowMap(dbJobRowMap);
    }

    Line getTestLine(){
        return new Line("170610010000", "Линия №2");
    }

    List<Job> getTestJobs(){
        return List.of(
                new Job(
                        "1","170610010000", 1, 40, "Сырок глазированный ваниль", getTestProduct(),
                        40, 5720, Duration.ofMinutes(26), getTestMinStartDate(), getTestIdealEndDateTime(), getTestMaxEndDateTime(),
                        0,null, getTestStartProductionDateTime()
                ),
                new Job(
                        "2","170610010000", 2, 40, "Сырок глазированный ваниль", getTestProduct(),
                        40, 6600, Duration.ofMinutes(30),  getTestMinStartDate(), getTestIdealEndDateTime(), getTestMaxEndDateTime(),
                        0,null, getTestStartProductionDateTime().plusMinutes(26)
                )

        );
    }

    Map<Integer, DbJobRow> getTestDbJobRowMap(){
       return Map.of(
                1, new DbJobRow(
                        BigDecimal.valueOf(1), "KMC-001", "Сырок глазированный ваниль",
                        Timestamp.valueOf(LocalDateTime.of(2025,12,14,8,0)),
                        40, 40, 5720, 0, "170610010000",
                        Timestamp.valueOf(LocalDateTime.of(2025,12,17,8,0)),
                        Timestamp.valueOf(LocalDateTime.of(2025,12,17,8,26)), 26
                ),
                2, new DbJobRow(
                        BigDecimal.valueOf(1), "KMC-001", "Сырок глазированный ваниль",
                        Timestamp.valueOf(LocalDateTime.of(2025,12,14,8,0)),
                        40, 40, 5110, 0, "170610020000",
                        Timestamp.valueOf(LocalDateTime.of(2025,12,17,8,26)),
                        Timestamp.valueOf(LocalDateTime.of(2025,12,17,8,56)), 30
                ));

    }

    LocalDateTime getTestMinStartDate(){
        return LocalDateTime.of(2025,12,17,8,0);
    }

    LocalDateTime getTestIdealEndDateTime(){
        return LocalDateTime.of(2025,12,17,8,0);
    }

    LocalDateTime getTestMaxEndDateTime(){
        return LocalDateTime.of(2025,12,17,8,0);
    }

    LocalDateTime getTestStartProductionDateTime(){
        return LocalDateTime.of(2025,12,17,8,0);
    }


    Product getTestProduct(){
        return new Product( "Сырок глазированный ванильный", "KMC-001", "170610010000",
                "CLASSIC", "10004", "11", "0"
        );
    }

    void setTestJobsOnLine(List<Job> testJobs){
        Line testLine = getTestLine();
        for(Job testJob : testJobs){
            testJob.setLine(testLine);
        }
        testLine.setJobs(testJobs);
    }

    @Test
    void shouldAddJobWhenEnabledTrue() {
        Map<Integer, Boolean> selection = Map.of(1, true);

        service.applySelection(selection);

        assertEquals(2, repository.getJobs().size());
        assertTrue(jobIdMap.containsKey(1));
    }

    @Test
    void shouldNotAddJobIfNotInDb() {
        Map<Integer, Boolean> selection = Map.of(999, true);

        service.applySelection(selection);

        assertTrue(jobs.isEmpty());
    }

    @Test
    void shouldRemoveJobWhenEnabledFalse() {
        Job job = exampleJobs.getFirst();
        jobs.add(job);
        jobIdMap.put(1, job);

        Map<Integer, Boolean> selection = Map.of(1, false);

        service.applySelection(selection);

        assertTrue(jobs.isEmpty());
        assertFalse(jobIdMap.containsKey(1));
    }

    @Test
    void shouldDetachJobFromLineOnRemove() {
        Job job = exampleJobs.getFirst();
        Line line = job.getLine();

        service.applySelection(Map.of(1, false));

        assertNull(job.getLine());
        assertEquals(1, line.getJobs().size());;
    }
}
