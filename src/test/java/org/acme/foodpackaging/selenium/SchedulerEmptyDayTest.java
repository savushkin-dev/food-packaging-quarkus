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
    }

    @AfterAll
    static void tearDown() {
        page.quit();
    }

    @Test
    @Order(1)
    void shouldLoadSchedulerPageWithoutErrorsAndWithPlanningArea() {
        page.open();
        assertThat(page.hasNoErrorMessages())
                .as("При загрузке страницы не должно быть сообщений об ошибке")
                .isTrue();
        assertThat(page.isPlanningAreaWithLinesLoaded())
                .as("Должна быть загружена область планирования с линиями")
                .isTrue();
    }

    @Test
    @Order(2)
    void shouldSelectDateAndShowSelectAllButtons() {
        page.selectDate(TARGET_DATE);
        assertThat(page.isDateSelected(TARGET_DATE))
                .as("Дата " + TARGET_DATE + " должна быть выбрана")
                .isTrue();
        assertThat(page.areSelectAllButtonsPresent())
                .as("Должны быть в наличии кнопки \"Отметить все\"")
                .isTrue();
    }

    @Test
    @Order(3)
    void shouldOpenLineSettingsWithStartAndMaxTimeThenClose() {
        page.openLineSettings();
        assertThat(page.isLineSettingsModalOpen())
                .as("Диалог настройки линий должен открыться")
                .isTrue();
        assertThat(page.lineSettingsHaveStartAndMaxTimeForEachLine())
                .as("Для каждой линии должны быть заданы дата начала и максимальное время")
                .isTrue();

        page.closeLineSettings();
        assertThat(page.isLineSettingsModalOpen())
                .as("Диалог настройки линий должен закрыться")
                .isFalse();
    }

    @Test
    @Order(4)
    void shouldSelectAllProductsForTargetDate() {
        page.clickSelectAllForTaskDateAndWait(TARGET_DATE);
        assertThat(page.areAllRowCheckboxesCheckedForTaskDate(TARGET_DATE))
                .as("Все галочки продуктов за " + TARGET_DATE + " должны быть установлены")
                .isTrue();
    }

    @Test
    @Order(5)
    void shouldLoadPlanSuccessfully() {
        page.clickLoadPlan();
        assertThat(page.isLoadPlanSuccessful())
                .as("Дозагрузка плана должна пройти без ошибок")
                .isTrue();
    }

    @Test
    @Order(6)
    void shouldStartPlanningAndShowDisabledStopButton() {
        page.clickStartPlanning();
        assertThat(page.isStopButtonShownAndInitiallyDisabled())
                .as("После нажатия \"Планировать\" кнопка должна стать \"Остановить\" и быть неактивной")
                .isTrue();
    }

    @Test
    @Order(7)
    void shouldWaitAndStopPlanningThenShowStartButtonAgain() {
        page.waitUntilStopButtonActive();
        page.clickStopPlanning();
        assertThat(page.isStartButtonShownAfterStop())
                .as("После остановки кнопка должна снова называться \"Планировать\"")
                .isTrue();
    }

    @Test
    @Order(8)
    void shouldReadResultIndicatorsAfterPlanning() {
        int errors = page.getErrorsCount();
        int downtime = page.getDowntimeMinutes();
        int executionTime = page.getExecutionTimeMinutes();

        assertThat(errors).as("Количество ошибок должно быть читаемым числом").isGreaterThanOrEqualTo(0);
        assertThat(downtime).as("Время простоя должно быть читаемым числом").isGreaterThanOrEqualTo(0);
        assertThat(executionTime).as("Время выполнения должно быть читаемым числом").isGreaterThanOrEqualTo(0);
    }
}