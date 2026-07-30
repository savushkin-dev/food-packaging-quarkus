package org.acme.foodpackaging.selenium;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulerPageTest {

    static WebDriver driver;
    static SchedulerPage page;

    @BeforeAll
    static void setUp() {
        driver = SchedulerPage.createLocalChromeDriver();
        page = new SchedulerPage(driver);
        page.open();
    }

    @AfterAll
    static void tearDown() {
        page.quit();
    }

    @Test
    void schedulerPageShouldLoad() {
        assertThat(driver.getTitle()).isNotBlank();
    }

    @Test
    void shouldSelectAllBatchesAndLoadPlan() {
        page.clickSelectAll();
        page.clickLoadPlan();

        assertThat(page.getPlannedBatchesCount())
                .as("После загрузки плана на графике должна появиться хотя бы одна партия")
                .isGreaterThan(0);
    }

    @Test
    void shouldRunPlanningWithoutErrors() {
        page.clickStartPlanning();
        page.waitUntilScoreStabilized();
        page.clickStopPlanning();

        assertThat(page.getErrorsCount())
                .as("Планирование должно завершиться без ошибок")
                .isEqualTo(0);
    }

    @Test
    void shouldSortBatches() {
        int beforeSort = page.getPlannedBatchesCount();
        page.clickSort();
        int afterSort = page.getPlannedBatchesCount();

        assertThat(afterSort)
                .as("Сортировка не должна менять количество партий на графике")
                .isEqualTo(beforeSort);
    }

    @Test
    void shouldSavePlan() {
        int beforeSave = page.getPlannedBatchesCount();
        page.clickSave();
        driver.navigate().refresh();

        assertThat(page.getPlannedBatchesCount())
                .as("После сохранения и обновления страницы план должен сохраниться")
                .isEqualTo(beforeSave);
    }
}