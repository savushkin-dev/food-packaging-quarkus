package org.acme.foodpackaging.selenium;

import org.junit.jupiter.api.*;
import org.openqa.selenium.WebDriver;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("selenium")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SchedulerLinesReadTest {
    private static final LocalDate PLAN_DATE = LocalDate.of(2026, Month.APRIL, 10);
    private static final LocalDate TASK_DATE = LocalDate.of(2026, Month.APRIL, 7);

    static WebDriver driver;
    static SchedulerPage page;

    @BeforeAll
    static void setUp() {
        driver = SchedulerPage.createLocalChromeDriver();
        page = new SchedulerPage(driver);
    }

    @AfterAll
    static void tearDown() {
        if (!Boolean.getBoolean("keepBrowserOpen") && driver != null) {
            driver.quit();
        }
    }

    @Test
    @Order(1)
    void shouldPreparePlanWithJobCards() {
        page.open();
        page.selectDate(PLAN_DATE);
        page.clickSelectAllForTaskDateAndWait(TASK_DATE);
        page.clickLoadPlan();
        page.clickStartPlanning();
        assertThat(page.isCountdownButtonShownAndDisabled())
                .as("После запуска планирования должна быть неактивная кнопка с обратным отсчётом")
                .isTrue();
        page.waitUntilStopButtonActive();
        page.clickStopPlanning();
        assertThat(page.isStartButtonShownAfterStop())
                .as("После остановки должна снова появиться кнопка \"Планировать\"")
                .isTrue();
    }

    @Test
    @Order(2)
    void shouldReadJobCardsGroupedByLines() {
        page.waitUntilJobCardsVisible();
        List<SchedulerPage.LineSnapshot> lines = page.readLines();

        int mapped = lines.stream().mapToInt(l -> l.cards().size()).sum();
        int jobs = lines.stream().mapToInt(l -> l.jobs().size()).sum();
        assertThat(lines).as("Должны читаться строки линий").isNotEmpty();
        assertThat(mapped)
                .as("Каждая карточка на диаграмме должна быть привязана к линии. Прочитано: " + lines)
                .isEqualTo(page.countAllJobCards())
                .isPositive();
        assertThat(jobs).as("Должна быть хотя бы одна карточка задания. Прочитано: " + lines).isPositive();
        lines.forEach(line -> line.jobs().forEach(card ->
                assertThat(card.title()).as(line.name()).isNotBlank()));
    }

    @Test
    @Order(3)
    void shouldHaveAscendingPositionsLeftToRightOnEachLine() {
        List<SchedulerPage.LineSnapshot> lines = page.readLines();
        lines.forEach(line -> {
            List<Integer> positions = line.jobs().stream().map(SchedulerPage.JobCard::position).toList();
            assertThat(positions)
                    .as(line.name() + ": номера позиций слева направо. Карточки: " + line.cards())
                    .isSorted();
        });
    }
}