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
    private static final By DATE_INPUT = By.xpath("//input[@type='date']");
    private static final By TASK_SELECT_BUTTONS = By.xpath(
            "//span[starts-with(normalize-space(.), 'Задание ')]" +
                    "/ancestor::div[contains(@class,'justify-between')][1]" +
                    "//button[contains(., 'Отметить все') or contains(., 'Снять все') or contains(., 'Нет доступных')]");
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

    public void open() {
        driver.get(URL);
    }

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

        // Приложение сначала рендерит блоки "Задание N" с кнопками-заглушками
        // "Нет доступных" и только после ответа сервера обновляет их на
        // актуальное состояние. Ждём, пока хотя бы одна кнопка выйдет из
        // заглушки, иначе последующий код читает переходный DOM.
        wait.until(d -> {
            List<WebElement> buttons = d.findElements(TASK_SELECT_BUTTONS);
            return !buttons.isEmpty() && buttons.stream()
                    .anyMatch(b -> !b.getText().trim().contains(NO_BATCHES_AVAILABLE) || b.isEnabled());
        });
    }

    public void clickSelectAllForTaskDate(LocalDate taskDate) {
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

    public void clickSelectAllForAnyAvailableTaskDate() {
        List<WebElement> buttons = wait.until(d -> {
            List<WebElement> found = d.findElements(TASK_SELECT_BUTTONS);
            return found.isEmpty() ? null : found;
        });

        for (WebElement button : buttons) {
            if (button.isEnabled() && !button.getText().trim().contains(NO_BATCHES_AVAILABLE)) {
                button.click();
                return;
            }
        }
        throw new IllegalStateException(
                "Не найдено ни одного блока \"Задание ...\" со свободными партиями (везде \"" + NO_BATCHES_AVAILABLE + "\").");
    }

    public void clickLoadPlan() {
        clickByText("Догрузить план");
        confirmActionDialogIfPresent();
        dismissResultDialogIfPresent();
    }

    public void clickStartPlanning() {
        waitUntilOverlayGone();
        clickByText("Планировать");
    }

    public void clickStopPlanning() {
        wait.until(d -> isStopButtonActive(d.findElements(STOP_BUTTON)));

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

    private boolean isStopButtonActive(List<WebElement> stopButtons) {
        if (stopButtons.isEmpty()) {
            return false;
        }
        WebElement button = stopButtons.get(0);
        String classAttr = button.getAttribute("class");
        return button.isEnabled() && classAttr != null && classAttr.contains("bg-red");
    }

    public void openLineSettings() {
        waitUntilOverlayGone();
        clickByText("Настройка линий");
        wait.until(d -> !d.findElements(lineSettingsModalTitle()).isEmpty());
    }

    public boolean isLineSettingsModalOpen() {
        return !driver.findElements(lineSettingsModalTitle()).isEmpty();
    }

    public void closeLineSettings() {
        wait.until(d -> d.findElement(By.xpath("//button[normalize-space(.)='Закрыть']"))).click();
        wait.until(d -> d.findElements(lineSettingsModalTitle()).isEmpty());
        waitUntilOverlayGone();
    }

    private By lineSettingsModalTitle() {
        return By.xpath("//*[contains(text(),'Настройки даты и времени планировщика')]");
    }

    public void clickViewModeTab(String tabLabel) {
        waitUntilOverlayGone();
        clickByText(tabLabel);
    }

    public boolean isViewModeTabActive(String tabLabel) {
        WebElement tab = driver.findElement(By.xpath("//button[contains(., '" + tabLabel + "')]"));
        String classAttr = tab.getAttribute("class");
        boolean byClass = classAttr != null && (
                classAttr.contains("bg-blue") ||
                        classAttr.contains("bg-cyan") ||
                        classAttr.contains("border-b-2") ||
                        classAttr.contains("underline") ||
                        classAttr.contains("active") ||
                        classAttr.contains("text-white"));
        return byClass || "true".equals(tab.getAttribute("aria-selected"));
    }

    public void clickSort() {
        waitUntilOverlayGone();
        clickByText("Отсортировать");
        confirmActionDialogIfPresent();
        dismissResultDialogIfPresent();
    }

    public void clickSave() {
        waitUntilOverlayGone();
        clickByText("Сохранить");
        confirmActionDialogIfPresent();
        dismissResultDialogIfPresent();
    }

    public void clickSendToWork() {
        waitUntilOverlayGone();
        clickByText("Отправить в работу");
        confirmActionDialogIfPresent();
        dismissResultDialogIfPresent();
    }

    public boolean isConfirmationDialogPresent(String expectedTextFragment) {
        return !driver.findElements(By.xpath("//*[contains(text(),'" + expectedTextFragment + "')]")).isEmpty();
    }

    public void clickDetails() {
        waitUntilOverlayGone();
        clickByText("Подробнее");
    }

    public int getErrorsCount() {
        WebElement el = driver.findElement(By.xpath(
                "//div[contains(., 'Ошибки')]/preceding-sibling::div[1] | " +
                        "//*[contains(text(),'Ошибки')]/parent::*"));
        String digits = el.getText().replaceAll("\\D+", "");
        return digits.isEmpty() ? 0 : Integer.parseInt(digits);
    }

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