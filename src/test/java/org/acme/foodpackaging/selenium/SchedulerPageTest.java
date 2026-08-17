package org.acme.foodpackaging.selenium;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.openqa.selenium.WebDriver;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("selenium")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SchedulerPageTest {

    private static final LocalDate DEFAULT_TEST_DATE = LocalDate.of(2026, 2, 1);

    static WebDriver driver;
    static SchedulerPage page;
    static LocalDate testDate;

    private static LocalDate resolveTestDate() {
        String override = System.getProperty("scheduler.test.date");
        return override != null ? LocalDate.parse(override) : DEFAULT_TEST_DATE;
    }

    @BeforeAll
    static void setUp() {
        driver = SchedulerPage.createLocalChromeDriver();
        page = new SchedulerPage(driver);
        page.open();
        testDate = resolveTestDate();
    }

    @AfterAll
    static void tearDown() {
        page.quit();
    }

    @Test
    @Order(1)
    void schedulerPageShouldLoad() {
        assertThat(driver.getTitle()).isNotBlank();
    }

    @Test
    @Order(2)
    void shouldSelectAllBatchesAndLoadPlan() {
        page.selectDate(testDate);

        assertThat(page.isSelectAllButtonPresent())
                .as("Кнопка 'Отметить все' должна быть на странице для даты %s. " +
                                "Строк в области выбора партий: %d.",
                        testDate, page.getBatchSelectionRowsCount())
                .isTrue();

        page.clickSelectAll();
        page.clickLoadPlan();

        assertThat(page.getPlannedBatchesCount())
                .as("После загрузки плана на графике должна появиться хотя бы одна партия")
                .isGreaterThan(0);
    }

    @Test
    @Order(3)
    void shouldRunPlanningWithoutErrors() {
        page.clickStartPlanning();
        page.waitForPlanningToAutoComplete();
        page.clickStopPlanning();

        assertThat(page.getErrorsCount())
                .as("Планирование должно завершиться без ошибок")
                .isEqualTo(0);
    }

    @Test
    @Order(4)
    void shouldSortBatches() {
        int beforeSort = page.getPlannedBatchesCount();
        page.clickSort();
        int afterSort = page.getPlannedBatchesCount();

        assertThat(afterSort)
                .as("Сортировка не должна менять количество партий на графике")
                .isEqualTo(beforeSort);
    }

    @Test
    @Order(5)
    void shouldSavePlan() {
        int beforeSave = page.getPlannedBatchesCount();
        page.clickSave();

        driver.navigate().refresh();
        page.selectDate(testDate);
        page.waitForPlannedBatchesAtLeast(beforeSave, Duration.ofSeconds(60));

        assertThat(page.getPlannedBatchesCount())
                .as("После сохранения и обновления страницы план должен сохраниться")
                .isEqualTo(beforeSave);
    }
}