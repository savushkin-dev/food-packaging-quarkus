package org.acme.foodpackaging.selenium;

import org.junit.jupiter.api.*;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("selenium")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SchedulerEmptyDayTest {

    private static final LocalDate TARGET_DATE = LocalDate.of(2026, Month.MARCH, 25);

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
        List<WebElement> taskLabels = driver.findElements(
                By.xpath("//span[starts-with(normalize-space(.), 'Задание ')]"));
        System.out.println("=== DEBUG: найдено блоков 'Задание ...': " + taskLabels.size() + " ===");
        System.out.println(taskLabels.stream()
                .map(WebElement::getText)
                .collect(Collectors.joining(" | ")));

        List<WebElement> allSelectButtons = driver.findElements(By.xpath(
                "//button[contains(., 'Отметить все') or contains(., 'Снять все') or contains(., 'Нет доступных')]"));
        System.out.println("=== DEBUG: найдено кнопок выбора: " + allSelectButtons.size() + " ===");
        System.out.println(allSelectButtons.stream()
                .map(b -> "[" + b.getText().trim() + ", enabled=" + b.isEnabled() + "]")
                .collect(Collectors.joining(" | ")));

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
    void shouldSaveCurrentPlanWithoutError() {
        page.clickSave();
    }

    @Test
    @Order(6)
    void shouldOpenDetailsPanelWithReadableErrorsCount() {
        page.clickDetails();
        assertThat(page.getErrorsCount())
                .as("Счётчик ошибок должен быть доступен и читаться как число")
                .isGreaterThanOrEqualTo(0);
    }
}