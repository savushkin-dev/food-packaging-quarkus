package org.acme.foodpackaging.selenium;

import org.junit.jupiter.api.*;
import org.openqa.selenium.WebDriver;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("selenium")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SchedulerEmptyDayTest {

    private static final LocalDate TEST_DATE = LocalDate.of(2026, 3, 13);

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
    @Order(1)
    void shouldSelectEmptyDate() {
        page.selectDate(TEST_DATE);

        assertThat(page.getSelectedDate()).isEqualTo(TEST_DATE);
        assertThat(page.getPlannedBatchesCount()).isZero();
    }

    @Test
    @Order(2)
    void shouldSelectAllBatches() {
        page.clickSelectAll();
    }

    @Test
    @Order(3)
    void shouldLoadPlan() {
        page.clickLoadPlan();
    }

    @Test
    @Order(4)
    void shouldRunPlanningFromEmptyDayWithoutErrors() {
        page.clickStartPlanning();
        page.waitForPlanningToAutoComplete();
        page.clickStopPlanning();

        assertThat(page.getErrorsCount())
                .as("Планирование на пустой день должно завершиться без ошибок")
                .isEqualTo(0);
    }
}