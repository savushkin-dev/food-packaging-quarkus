package service.lines;

import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.dto.plrlc.EquipmentPeriodDto;
import org.acme.foodpackaging.entity.lines.PlrLines;
import org.acme.foodpackaging.repository.lines.LineRepository;
import org.acme.foodpackaging.repository.lines.PlrLcRepository;
import org.acme.foodpackaging.service.lines.LineActivitySyncService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static io.smallrye.common.constraint.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LineActivitySyncServiceTest {

        @Mock
        private LineRepository lineRepository;
        @Mock
        private PlrLcRepository plrLcRepository;

        @InjectMocks
        private LineActivitySyncService service;

        @Test
        void shouldRemoveInactiveLinesFromSchedule() {

                PackagingSchedule schedule = new PackagingSchedule();
                schedule.setLines(new ArrayList<>(List.of(
                                line("L1"),
                                line("L2"),
                                line("L3"))));

                when(plrLcRepository.loadEquipmentPeriods())
                                .thenReturn(List.of(
                                                period("L1", LocalDate.of(2025, Month.JANUARY, 1),
                                                                LocalDate.of(2025, Month.DECEMBER, 31))));

                LocalDate from = LocalDate.of(2025, Month.JUNE, 1);
                LocalDate to = LocalDate.of(2025, Month.JUNE, 30);

                service.syncLines(schedule, from, to);
                assertEquals(1, schedule.getLines().size());
                assertEquals("L1", schedule.getLines().getFirst().getId());
        }

        @Test
        void shouldAddMissingActiveLines() {
                PackagingSchedule schedule = new PackagingSchedule();
                schedule.setLines(new ArrayList<>());

                when(plrLcRepository.loadEquipmentPeriods())
                                .thenReturn(List.of(
                                                period("L1", LocalDate.of(2025, Month.JANUARY, 1), null), // active
                                                period("L2", LocalDate.of(2025, Month.JANUARY, 1),
                                                                LocalDate.of(2025, Month.DECEMBER, 31))));

                when(lineRepository.findLineInfo("L1"))
                                .thenReturn(Optional.of(lineEntity("L1", "Line 1")));

                when(lineRepository.findLineInfo("L2"))
                                .thenReturn(Optional.of(lineEntity("L2", "Line 2")));

                LocalDate from = LocalDate.of(2025, Month.JUNE, 1);
                LocalDate to = LocalDate.of(2025, Month.JUNE, 30);

                service.syncLines(schedule, from, to);

                assertEquals(2, schedule.getLines().size());

                Set<String> ids = schedule.getLines()
                                .stream()
                                .map(Line::getId)
                                .collect(Collectors.toSet());

                assertTrue(ids.contains("L1"));
                assertTrue(ids.contains("L2"));
        }

        @Test
        void shouldNotDuplicateExistingLines() {
                PackagingSchedule schedule = new PackagingSchedule();
                schedule.setLines(new ArrayList<>(List.of(
                                line("L1"))));

                when(plrLcRepository.loadEquipmentPeriods())
                                .thenReturn(List.of(
                                                period("L1", LocalDate.of(2025, Month.JANUARY, 1), null)));

                LocalDate from = LocalDate.of(2025, Month.JUNE, 1);
                LocalDate to = LocalDate.of(2025, Month.JUNE, 30);

                service.syncLines(schedule, from, to);
                assertEquals(1, schedule.getLines().size());
        }

        @Test
        void shouldRemoveAllLinesIfNoneActive() {
                PackagingSchedule schedule = new PackagingSchedule();
                schedule.setLines(new ArrayList<>(List.of(
                                line("L1"),
                                line("L2"))));

                when(plrLcRepository.loadEquipmentPeriods())
                                .thenReturn(List.of(
                                                period("L3", LocalDate.of(2020, Month.JANUARY, 1),
                                                                LocalDate.of(2020, Month.DECEMBER, 31))));

                LocalDate from = LocalDate.of(2025, Month.JUNE, 1);
                LocalDate to = LocalDate.of(2025, Month.JUNE, 30);
                service.syncLines(schedule, from, to);
                assertTrue(schedule.getLines().isEmpty());
        }

        @Test
        void shouldIgnorePeriodWithNullBeginDate() {
                PackagingSchedule schedule = new PackagingSchedule();
                schedule.setLines(new ArrayList<>());

                when(plrLcRepository.loadEquipmentPeriods())
                                .thenReturn(List.of(period("L1", null, null)));

                LocalDate from = LocalDate.of(2025, Month.JUNE, 1);
                LocalDate to = LocalDate.of(2025, Month.JUNE, 30);

                service.syncLines(schedule, from, to);

                assertTrue(schedule.getLines().isEmpty());

                verifyNoInteractions(lineRepository);
        }

        @Test
        void shouldNotAddLineWhenLineInfoNotFound() {
                PackagingSchedule schedule = new PackagingSchedule();
                schedule.setLines(new ArrayList<>());

                when(plrLcRepository.loadEquipmentPeriods())
                                .thenReturn(List.of(period("L1",
                                LocalDate.of(2025, Month.JANUARY, 1),null)));

                when(lineRepository.findLineInfo("L1"))
                                .thenReturn(Optional.empty());

                service.syncLines(
                                schedule,
                                LocalDate.of(2025, Month.JUNE, 1),
                                LocalDate.of(2025, Month.JUNE, 30));

                assertTrue(schedule.getLines().isEmpty());
        }

        private Line line(String id) {
                Line l = new Line();
                l.setId(id);
                l.setName("test");
                return l;
        }

        private EquipmentPeriodDto period(String id, LocalDate begin, LocalDate end) {
                return new EquipmentPeriodDto(id, begin, end);
        }

        private PlrLines lineEntity(String id, String name) {
                PlrLines e = new PlrLines();
                e.setLineId(id);
                e.setSnm(name);
                return e;
        }
}
