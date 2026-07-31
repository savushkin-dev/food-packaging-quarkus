package org.acme.foodpackaging.selenium;

import org.junit.jupiter.api.*;
import org.openqa.selenium.WebDriver;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
@Tag("selenium")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SchedulerEmptyDayTest {

    // TODO: заменить на реальную "пустую" дату из системы
    private static final LocalDate TEST_DATE = LocalDate.now().plusMonths(2);

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
    void shouldOpenSchedulerPage() {
        assertThat(driver.getTitle()).isNotBlank();
    }

    @Test
    @Order(2)
    void shouldSelectEmptyDate() {
        page.selectDate(TEST_DATE);

        assertThat(page.getSelectedDate())
                .as("Выбранная дата должна совпадать с запрошенной")
                .isEqualTo(TEST_DATE);
    }

    @Test
    @Order(3)
    void ganttChartShouldBeEmptyBeforeLoadingPlan() {
        assertThat(page.getPlannedBatchesCount())
                .as("На пустой день не должно быть запланированных партий")
                .isZero();

        assertThat(page.getServiceOperationsCount())
                .as("На пустой день не должно быть сервисных операций")
                .isZero();
    }

    @Test
    @Order(4)
    void shouldSelectAllBatchesForEmptyDay() {
        page.clickSelectAll();

        assertThat(page.getPinnedBatchesCount())
                .as("До загрузки плана не должно быть закреплённых партий")
                .isZero();
    }

    @Test
    @Order(5)
    void shouldLoadPlanFromScratch() {
        page.clickLoadPlan();

        assertThat(page.getPlannedBatchesCount())
                .as("После загрузки плана на графике должны появиться партии")
                .isGreaterThan(0);

        assertThat(page.getUnpackedBatchesCount())
                .as("Изначально загруженные партии должны быть нерасфасованными (жёлтыми)")
                .isGreaterThan(0);

        assertThat(page.getPackedBatchesCount())
                .as("На пустой день изначально не должно быть расфасованных партий")
                .isZero();
    }

    @Test
    @Order(6)
    void shouldConfigureLinesBeforePlanning() {
        page.openLineSettings();
        page.closeLineSettings();

        assertThat(page.getPlannedBatchesCount())
                .as("Открытие и закрытие настройки линий не должно менять план")
                .isGreaterThan(0);
    }

    @Test
    @Order(7)
    void shouldRunPlanningFromEmptyDayAndReachZeroErrors() {
        page.clickStartPlanning();
        page.waitUntilScoreStabilized();
        page.clickStopPlanning();

        assertThat(page.getErrorsCount())
                .as("Планирование с пустого дня должно завершиться без ошибок")
                .isEqualTo(0);

        assertThat(page.getPackedBatchesCount())
                .as("После планирования часть партий должна стать расфасованной")
                .isGreaterThan(0);
    }

    @Test
    @Order(8)
    void shouldSortBatchesAfterPlanning() {
        int beforeSort = page.getPlannedBatchesCount();
        page.clickSort();
        int afterSort = page.getPlannedBatchesCount();

        assertThat(afterSort)
                .as("Сортировка не должна менять количество партий на графике")
                .isEqualTo(beforeSort);
    }

    @Test
    @Order(9)
    void shouldSaveNewlyCreatedPlan() {
        page.clickSave();
        driver.navigate().refresh();
        page.selectDate(TEST_DATE);

        assertThat(page.getPlannedBatchesCount())
                .as("После сохранения и обновления страницы план должен остаться на месте")
                .isGreaterThan(0);
    }
}