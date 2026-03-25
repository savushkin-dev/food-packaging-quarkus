package service.builder;

import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.persistence.json.SolutionImporter;
import org.acme.foodpackaging.record.SolutionByVersion;
import org.acme.foodpackaging.repository.solution.PlrPlanRepository;
import org.acme.foodpackaging.service.builder.ScheduleBuilderByVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleBuilderByVersionTest {

    @InjectMocks
    ScheduleBuilderByVersion builder;

    @Mock
    PlrPlanRepository repository;
    @Mock
    SolutionImporter importer;

    @Test
    void buildScheduleByVersion() {

        LocalDate dti = LocalDate.of(2025, 12, 24);
        String version = "v1";

        SolutionByVersion solutionWrapper =
                new SolutionByVersion(version, "{json}");

        PackagingSchedule schedule = new PackagingSchedule();

        when(repository.getSolutionByVersion(dti, version))
                .thenReturn(solutionWrapper);

        when(importer.importFromJson(solutionWrapper))
                .thenReturn(schedule);

        PackagingSchedule resultSchedule = builder.init(dti, version);

        assertEquals(schedule, resultSchedule);

        verify(repository).getSolutionByVersion(dti, version);
        verify(importer).importFromJson(solutionWrapper);
    }
}

