package org.acme.foodpackaging.selenium;

import org.junit.jupiter.api.*;
import org.openqa.selenium.WebDriver;

import java.time.LocalDate;
import java.time.Month;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("selenium")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SchedulerEmptyDayTest {

    private static final LocalDate TARGET_DATE = LocalDate.of(2026, Month.APRIL, 10);

    static WebDriver driver;
    static SchedulerPage page;

    @BeforeAll
    static void setUp() {
        driver = SchedulerPage.createLocalChromeDriver();
        page = new SchedulerPage(driver);
        page.open();
        page.selectDate(TARGET_DATE);
    }

    @AfterAll
    static void tearDown() {
        page.quit();
    }

    @Test
    @Order(1)
    void shouldLoadPlanAndRunPlanningFromNeighborDay() {
        page.clickSelectAllForAnyAvailableTaskDate();
        page.clickLoadPlan();
        page.clickStartPlanning();
        page.clickStopPlanning();
    }

    @Test
    @Order(2)
    void shouldOpenAndCloseLineSettingsModal() {
        page.openLineSettings();
        assertThat(page.isLineSettingsModalOpen())
                .as("Модалка настроек линий должна открыться")
                .isTrue();

        page.closeLineSettings();
        assertThat(page.isLineSettingsModalOpen())
                .as("Модалка настроек линий должна закрыться")
                .isFalse();
    }

    @Test
    @Order(3)
    void shouldSwitchToFactViewMode() {
        page.clickViewModeTab("Факт");
        assertThat(page.isViewModeTabActive("Факт"))
                .as("Вкладка \"Факт\" должна стать активной после клика")
                .isTrue();
    }

    @Test
    @Order(4)
    void shouldSortBatchesWithoutError() {
        page.clickSort();
    }

    @Test
    @Order(5)
    void shouldOpenDetailsPanelWithReadableErrorsCount() {
        page.clickDetails();
        assertThat(page.getErrorsCount())
                .as("Счётчик ошибок должен быть доступен и читаться как число")
                .isGreaterThanOrEqualTo(0);
    }
}