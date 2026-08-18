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

    private final WebDriver driver;
    private final WebDriverWait wait;

    public SchedulerPage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofMinutes(3));
    }

    public static WebDriver createLocalChromeDriver() {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        // options.addArguments("--headless=new"); // раскомментировать для CI
        options.addArguments("--window-size=1600,900");
        return new ChromeDriver(options);
    }

    public void open() {
        driver.get(URL);
    }

    public void selectDate(LocalDate date) {
        WebElement dateInput = wait.until(d -> d.findElement(By.xpath("//input[@type='date']")));
        String isoValue = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript(
                "var input = arguments[0];" +
                        "var value = arguments[1];" +
                        "var nativeSetter = Object.getOwnPropertyDescriptor(" +
                        "    window.HTMLInputElement.prototype, 'value').set;" +
                        "nativeSetter.call(input, value);" +
                        "input.dispatchEvent(new Event('input', { bubbles: true }));" +
                        "input.dispatchEvent(new Event('change', { bubbles: true }));",
                dateInput, isoValue);
        wait.until(d -> isoValue.equals(
                d.findElement(By.xpath("//input[@type='date']")).getAttribute("value")));
    }

    public void clickSelectAllForTaskDate(LocalDate taskDate) {
        String label = "Задание " + taskDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        WebElement anyButton = wait.until(d -> d.findElement(By.xpath(
                "//span[normalize-space(.)='" + label + "']" +
                        "/ancestor::div[contains(@class,'justify-between')][1]" +
                        "//button[contains(., 'Отметить все') or contains(., 'Снять все') or contains(., 'Нет доступных')]")));

        if (anyButton.getText().trim().contains("Нет доступных")) {
            throw new IllegalStateException(
                    "У блока \"" + label + "\" нет свободных партий (кнопка \"Нет доступных\").");
        }
        anyButton.click();
    }

    public void clickSelectAllForAnyAvailableTaskDate() {
        List<WebElement> candidateButtons = wait.until(d -> {
            List<WebElement> buttons = d.findElements(By.xpath(
                    "//span[starts-with(normalize-space(.), 'Задание ')]" +
                            "/ancestor::div[contains(@class,'justify-between')][1]" +
                            "//button[contains(., 'Отметить все') or contains(., 'Снять все')]"));
            return buttons.isEmpty() ? null : buttons;
        });

        for (WebElement button : candidateButtons) {
            if (button.isEnabled()) {
                button.click();
                return;
            }
        }
        throw new IllegalStateException(
                "Не найдено ни одного блока \"Задание ...\" со свободными партиями.");
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
        wait.until(d -> {
            List<WebElement> buttons = d.findElements(STOP_BUTTON);
            if (buttons.isEmpty()) {
                return false;
            }
            WebElement el = buttons.get(0);
            String classAttr = el.getAttribute("class");
            return el.isEnabled() && classAttr != null && classAttr.contains("bg-red");
        });

        wait.until(d -> {
            List<WebElement> buttons = d.findElements(STOP_BUTTON);
            if (buttons.isEmpty()) {
                return true;
            }
            WebElement el = buttons.get(0);
            String classAttr = el.getAttribute("class");
            boolean isEnabled = el.isEnabled();
            boolean looksActive = classAttr != null && classAttr.contains("bg-red");
            if (isEnabled && looksActive) {
                el.click();
                return true;
            }
            return false;
        });
    }


    public void openLineSettings() {
        waitUntilOverlayGone();
        clickByText("Настройка линий");
        wait.until(d -> !d.findElements(By.xpath(
                "//*[contains(text(),'Настройки даты и времени планировщика')]")).isEmpty());
    }

    public boolean isLineSettingsModalOpen() {
        return !driver.findElements(By.xpath(
                "//*[contains(text(),'Настройки даты и времени планировщика')]")).isEmpty();
    }

    public void closeLineSettings() {
        WebElement closeButton = wait.until(d -> d.findElement(
                By.xpath("//button[normalize-space(.)='Закрыть']")));
        closeButton.click();
        wait.until(d -> d.findElements(By.xpath(
                "//*[contains(text(),'Настройки даты и времени планировщика')]")).isEmpty());
        waitUntilOverlayGone();
    }


    public void clickViewModeTab(String tabLabel) {
        waitUntilOverlayGone();
        clickByText(tabLabel);
    }

    public boolean isViewModeTabActive(String tabLabel) {
        WebElement tab = driver.findElement(By.xpath("//button[contains(., '" + tabLabel + "')]"));
        String classAttr = tab.getAttribute("class");
        String ariaSelected = tab.getAttribute("aria-selected");
        boolean byClass = classAttr != null && (
                classAttr.contains("bg-blue") ||
                        classAttr.contains("bg-cyan") ||
                        classAttr.contains("border-b-2") ||
                        classAttr.contains("underline") ||
                        classAttr.contains("active") ||
                        classAttr.contains("text-white"));
        boolean byAria = "true".equals(ariaSelected);
        return byClass || byAria;
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
            WebElement yesButton = new WebDriverWait(driver, Duration.ofSeconds(5)).until(d ->
                    d.findElement(By.xpath("//button[normalize-space(.)='Да']")));
            yesButton.click();
            wait.until(d -> d.findElements(By.xpath(
                    "//*[contains(text(),'Подтверждение действия')]")).isEmpty());
        } catch (Exception ignored) {
        }
    }

    private void dismissResultDialogIfPresent() {
        try {
            WebElement okButton = new WebDriverWait(driver, Duration.ofSeconds(5)).until(d ->
                    d.findElement(By.xpath("//div[contains(., 'Результат операции')]//button[contains(., 'ОК')]")));
            okButton.click();
        } catch (Exception ignored) {
        }
        waitUntilOverlayGone();
    }

    private void waitUntilOverlayGone() {
        wait.until(d -> d.findElements(ANY_OVERLAY).isEmpty());
    }

    private void clickByText(String text) {
        WebElement el = wait.until(d ->
                d.findElement(By.xpath("//button[contains(., '" + text + "')]")));
        el.click();
    }

    public void quit() {
        driver.quit();
    }
}