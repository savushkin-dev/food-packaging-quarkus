package org.acme.foodpackaging.selenium;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class SchedulerPage {

    private static final String URL = "http://10.30.0.5:7980/scheduler";

    private static final By ANY_OVERLAY = By.xpath(
            "//div[contains(@class,'fixed') and (contains(@class,'bg-black/50') or contains(@class,'bg-black/40'))]");
    private static final By STOP_BUTTON = By.xpath("//button[contains(., 'Остановить')]");
    private static final By START_BUTTON = By.xpath("//button[contains(., 'Планировать')]");
    private static final By DATE_INPUT = By.xpath("//input[@type='date']");
    private static final By TASK_SELECT_BUTTONS = By.xpath(
            "//span[starts-with(normalize-space(.), 'Задание ')]" +
                    "/ancestor::div[contains(@class,'justify-between')][1]" +
                    "//button[contains(., 'Отметить все') or contains(., 'Снять все') or contains(., 'Нет доступных')]");
    private static final By LINE_ROWS = By.xpath("//*[starts-with(normalize-space(.), 'Линия №')]");
    private static final By ERROR_TOAST = By.xpath(
            "//*[contains(@class,'toast') or contains(@class,'error') or contains(@class,'alert')]" +
                    "[contains(translate(., 'ОШИБКА', 'ошибка'), 'ошибка')]");
    private static final String NO_BATCHES_AVAILABLE = "Нет доступных";

    private final WebDriver driver;
    private final WebDriverWait wait;

    public SchedulerPage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofMinutes(3));
    }

    public static WebDriver createLocalChromeDriver() {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--window-size=1600,900");
        return new ChromeDriver(options);
    }

    // --- Тест 1: загрузка страницы ---

    public void open() {
        driver.get(URL);
        wait.until(d -> !d.findElements(LINE_ROWS).isEmpty());
    }

    public boolean hasNoErrorMessages() {
        return driver.findElements(ERROR_TOAST).isEmpty();
    }

    public boolean isPlanningAreaWithLinesLoaded() {
        return !driver.findElements(LINE_ROWS).isEmpty();
    }

    // --- Тест 2: выбор даты ---

    public void selectDate(LocalDate date) {
        WebElement dateInput = wait.until(d -> d.findElement(DATE_INPUT));
        String isoValue = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        ((JavascriptExecutor) driver).executeScript(
                "var input = arguments[0];" +
                        "var value = arguments[1];" +
                        "var nativeSetter = Object.getOwnPropertyDescriptor(" +
                        "    window.HTMLInputElement.prototype, 'value').set;" +
                        "nativeSetter.call(input, value);" +
                        "input.dispatchEvent(new Event('input', { bubbles: true }));" +
                        "input.dispatchEvent(new Event('change', { bubbles: true }));",
                dateInput, isoValue);

        wait.until(d -> isoValue.equals(d.findElement(DATE_INPUT).getAttribute("value")));

        wait.until(d -> {
            List<WebElement> buttons = d.findElements(TASK_SELECT_BUTTONS);
            return !buttons.isEmpty() && buttons.stream()
                    .anyMatch(b -> !b.getText().trim().contains(NO_BATCHES_AVAILABLE) || b.isEnabled());
        });
        waitUntilOverlayGone();
    }

    public boolean isDateSelected(LocalDate date) {
        String actual = driver.findElement(DATE_INPUT).getAttribute("value");
        return date.format(DateTimeFormatter.ISO_LOCAL_DATE).equals(actual);
    }

    public boolean areSelectAllButtonsPresent() {
        return !driver.findElements(TASK_SELECT_BUTTONS).isEmpty();
    }

    // --- Тест 3: настройка линий ---

    public void openLineSettings() {
        waitUntilOverlayGone();
        clickByText("Настройка линий");
        wait.until(d -> !d.findElements(lineSettingsModalTitle()).isEmpty());
    }

    public boolean isLineSettingsModalOpen() {
        return !driver.findElements(lineSettingsModalTitle()).isEmpty();
    }

    public boolean lineSettingsHaveStartAndMaxTimeForEachLine() {
        List<WebElement> rows = driver.findElements(By.xpath(
                "//span[contains(normalize-space(.), 'Линия №')]/ancestor::div[2]"));
        if (rows.isEmpty()) {
            return false;
        }
        return rows.stream().allMatch(row -> {
            List<WebElement> inputs = row.findElements(By.xpath(".//input[@type='datetime-local']"));
            return inputs.size() == 2
                    && inputs.stream().allMatch(input -> {
                String value = input.getAttribute("value");
                return value != null && !value.isEmpty();
            });
        });
    }

    public void closeLineSettings() {
        wait.until(d -> d.findElement(By.xpath("//button[normalize-space(.)='Закрыть']"))).click();
        wait.until(d -> d.findElements(lineSettingsModalTitle()).isEmpty());
        waitUntilOverlayGone();
    }

    private By lineSettingsModalTitle() {
        return By.xpath("//*[contains(text(),'Настройки даты и времени планировщика')]");
    }

    // --- Тест 4: выбор всех продуктов на дату ---

    public void clickSelectAllForTaskDateAndWait(LocalDate taskDate) {
        waitUntilOverlayGone();
        String label = "Задание " + taskDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        WebElement button = wait.until(d -> d.findElement(By.xpath(
                "//span[normalize-space(.)='" + label + "']" +
                        "/ancestor::div[contains(@class,'justify-between')][1]" +
                        "//button[contains(., 'Отметить все') or contains(., 'Снять все') or contains(., '" + NO_BATCHES_AVAILABLE + "')]")));

        if (button.getText().trim().contains(NO_BATCHES_AVAILABLE)) {
            throw new IllegalStateException(
                    "У блока \"" + label + "\" нет свободных партий (кнопка \"" + NO_BATCHES_AVAILABLE + "\").");
        }
        button.click();
    }

    public boolean areAllRowCheckboxesCheckedForTaskDate(LocalDate taskDate) {
        String label = "Задание " + taskDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        WebElement block = driver.findElement(By.xpath(
                "//span[normalize-space(.)='" + label + "']/ancestor::div[contains(@class,'justify-between')][1]" +
                        "/following::div[1]"));
        List<WebElement> checkboxes = block.findElements(By.xpath(".//input[@type='checkbox']"));
        return !checkboxes.isEmpty() && checkboxes.stream().allMatch(WebElement::isSelected);
    }

    // --- Тест 5: догрузка плана ---

    public void clickLoadPlan() {
        waitUntilOverlayGone();
        clickByText("Догрузить план");
        confirmActionDialogIfPresent();
        dismissResultDialogIfPresent();
    }

    public boolean isLoadPlanSuccessful() {
        return hasNoErrorMessages();
    }

    // --- Тест 6: запуск планирования ---

    public void clickStartPlanning() {
        waitUntilOverlayGone();
        clickByText("Планировать");
    }

    public boolean isStopButtonShownAndInitiallyDisabled() {
        List<WebElement> stopButtons = wait.until(d -> {
            List<WebElement> found = d.findElements(STOP_BUTTON);
            return found.isEmpty() ? null : found;
        });
        return stopButtons != null && !stopButtons.isEmpty() && !stopButtons.get(0).isEnabled();
    }

    // --- Тест 7: остановка планирования ---

    public void waitUntilStopButtonActive() {
        wait.until(d -> isStopButtonActive(d.findElements(STOP_BUTTON)));
    }

    public void clickStopPlanning() {
        wait.until(d -> {
            List<WebElement> buttons = d.findElements(STOP_BUTTON);
            if (buttons.isEmpty()) {
                return true;
            }
            if (isStopButtonActive(buttons)) {
                buttons.get(0).click();
                return true;
            }
            return false;
        });
    }

    public boolean isStartButtonShownAfterStop() {
        return wait.until(d -> !d.findElements(START_BUTTON).isEmpty());
    }

    private boolean isStopButtonActive(List<WebElement> stopButtons) {
        if (stopButtons.isEmpty()) {
            return false;
        }
        WebElement button = stopButtons.get(0);
        String classAttr = button.getAttribute("class");
        return button.isEnabled() && classAttr != null && classAttr.contains("bg-red");
    }

    // --- Тест 8: итоговые показатели ---

    public int getErrorsCount() {
        return readIndicatorNumber("Ошибки");
    }

    public int getDowntimeMinutes() {
        return readIndicatorNumber("Время простоя");
    }

    public int getExecutionTimeMinutes() {
        return readIndicatorNumber("Время выполнения");
    }

    private int readIndicatorNumber(String label) {
        WebElement el = driver.findElement(By.xpath(
                "//div[contains(., '" + label + "')]/preceding-sibling::div[1] | " +
                        "//*[contains(text(),'" + label + "')]/parent::*"));
        String digits = el.getText().replaceAll("\\D+", "");
        return digits.isEmpty() ? 0 : Integer.parseInt(digits);
    }

    // --- Общие служебные методы ---

    private void confirmActionDialogIfPresent() {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(5))
                    .until(d -> d.findElement(By.xpath("//button[normalize-space(.)='Да']")))
                    .click();
            wait.until(d -> d.findElements(By.xpath("//*[contains(text(),'Подтверждение действия')]")).isEmpty());
        } catch (Exception ignored) {
        }
    }

    private void dismissResultDialogIfPresent() {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(5))
                    .until(d -> d.findElement(By.xpath("//div[contains(., 'Результат операции')]//button[contains(., 'ОК')]")))
                    .click();
        } catch (Exception ignored) {
        }
        waitUntilOverlayGone();
    }

    private void waitUntilOverlayGone() {
        wait.until(d -> d.findElements(ANY_OVERLAY).isEmpty());
    }

    private void clickByText(String text) {
        wait.until(d -> d.findElement(By.xpath("//button[contains(., '" + text + "')]"))).click();
    }

    public void quit() {
        driver.quit();
    }
}