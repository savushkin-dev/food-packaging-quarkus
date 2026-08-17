package org.acme.foodpackaging.selenium;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
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

    private static final String COLOR_YELLOW_UNPACKED = "rgb(255, 252, 210)";
    private static final String COLOR_PURPLE_PACKED = "rgb(249, 239, 255)";
    private static final String COLOR_SERVICE_OPERATION = "rgb(203, 255, 147)";

    private static final Duration PLANNING_AUTO_COMPLETE_DURATION = Duration.ofSeconds(120);
    private static final Duration PLANNING_SAFETY_BUFFER = Duration.ofSeconds(3);

    private static final By OVERLAY_LOCATOR = By.xpath(
            "//div[contains(@class,'fixed') and contains(@class,'bg-black/50')]"
    );

    private static final By ANY_DIALOG_OK_BUTTON = By.xpath(
            "//button[normalize-space(text())='ОК' or .//span[normalize-space(text())='ОК']]"
    );

    private final WebDriver driver;
    private final WebDriverWait wait;

    private volatile long planningStartedAtMillis = -1;

    public SchedulerPage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofMinutes(3));
    }

    public static WebDriver createLocalChromeDriver() {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--window-size=1600,900");
        options.addArguments("--disable-extensions", "--disable-notifications");

        boolean forceHeadless = "true".equalsIgnoreCase(System.getProperty("scheduler.test.headless"))
                || "true".equalsIgnoreCase(System.getenv("CI"));
        if (forceHeadless) {
            options.addArguments("--headless=new");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--disable-gpu");
        }

        return new ChromeDriver(options);
    }

    public void open() {
        driver.get(URL);
    }

    public void clickSelectAll() { clickByText("Отметить все"); }

    public void clickLoadPlan() {
        clickByText("Догрузить план");
        dismissResultDialogIfPresent();
    }

    public void clickStartPlanning() {
        clickByText("Планировать");
        planningStartedAtMillis = System.currentTimeMillis();
    }

    public void waitForPlanningToAutoComplete() {
        if (planningStartedAtMillis < 0) {
            throw new IllegalStateException(
                    "clickStartPlanning() должен быть вызван перед waitForPlanningToAutoComplete()"
            );
        }
        long elapsedMillis = System.currentTimeMillis() - planningStartedAtMillis;
        long remainingMillis = PLANNING_AUTO_COMPLETE_DURATION.toMillis() - elapsedMillis;
        if (remainingMillis > 0) {
            sleepSilently(remainingMillis);
        }
        sleepSilently(PLANNING_SAFETY_BUFFER.toMillis());
    }

    private void sleepSilently(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void clickStopPlanning() {
        clickByText("Остановить");
        planningStartedAtMillis = -1;
        dismissResultDialogIfPresent();
        waitForOverlayToDisappear(Duration.ofSeconds(15));
    }

    public void clickSort() { clickByText("Отсортировать"); }

    public void clickSave() {
        clickByText("Сохранить");
        dismissResultDialogIfPresent();
    }

    public void clickSendToWork() { clickByText("Отправить в работу"); }

    public int getErrorsCount() { return getIndicatorValue("Ошибки"); }
    public int getIdleTime() { return getIndicatorValue("Время простоя"); }
    public int getExecutionTime() { return getIndicatorValue("Время выполнения"); }

    private int getIndicatorValue(String label) {
        WebElement valueSpan = wait.until(d -> d.findElement(By.xpath(
                "//div[contains(@class,'flex-col')][.//span[text()='" + label + "']]" +
                        "//span[contains(@class,'text-gray-800')]"
        )));
        return Integer.parseInt(valueSpan.getText().trim());
    }

    public String getScoreIndicatorText() {
        return String.format("Ошибки=%d, Простой=%d, Выполнение=%d",
                getErrorsCount(), getIdleTime(), getExecutionTime());
    }

    public void selectDate(LocalDate date) {
        WebElement dateInput = wait.until(d ->
                d.findElement(By.xpath("//input[@type='date']")));
        String isoDate = date.format(DateTimeFormatter.ISO_LOCAL_DATE);

        ((JavascriptExecutor) driver).executeScript(
                "var input = arguments[0];" +
                        "var value = arguments[1];" +
                        "var nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;" +
                        "nativeSetter.call(input, value);" +
                        "input.dispatchEvent(new Event('input', { bubbles: true }));" +
                        "input.dispatchEvent(new Event('change', { bubbles: true }));",
                dateInput, isoDate
        );

        clickConfirmDate();
        dismissResultDialogIfPresent();
        waitForBatchAreaToSettle();

        LocalDate actual = getSelectedDate();
        if (!actual.equals(date)) {
            throw new IllegalStateException(
                    "Не удалось установить дату " + date + " - после установки поле показывает " + actual
                            + ". Возможно, изменился способ управления датой в приложении."
            );
        }
    }

    private void clickConfirmDate() {
        List<WebElement> confirmButtons = driver.findElements(By.xpath(
                "//button[normalize-space(text())='ОК' or normalize-space(text())='OK' " +
                        "or .//span[normalize-space(text())='ОК'] or .//span[normalize-space(text())='OK']]"
        ));
        if (!confirmButtons.isEmpty()) {
            confirmButtons.get(0).click();
        }
    }

    private void dismissResultDialogIfPresent() {
        if (driver.findElements(OVERLAY_LOCATOR).isEmpty()) {
            return;
        }
        try {
            WebElement okButton = new WebDriverWait(driver, Duration.ofSeconds(5))
                    .until(d -> {
                        List<WebElement> found = d.findElements(ANY_DIALOG_OK_BUTTON);
                        return found.isEmpty() ? null : found.get(0);
                    });
            okButton.click();
            waitForOverlayToDisappear(Duration.ofSeconds(10));
        } catch (TimeoutException ignored) {
            // Кнопки "ОК" нет - дальнейшие клики разберутся сами через повторы.
        }
    }

    private void waitForBatchAreaToSettle() {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(10)).until(d ->
                    !d.findElements(By.xpath("//div[.//span[contains(text(),'Масса:')]]")).isEmpty()
                            || !d.findElements(By.xpath(
                            "//button[normalize-space(text())='Отметить все' or .//span[normalize-space(text())='Отметить все']]"
                    )).isEmpty()
            );
        } catch (TimeoutException ignored) {
            // Возможно на эту дату нет заданий.
        }
    }

    private void waitForOverlayToDisappear(Duration timeout) {
        try {
            new WebDriverWait(driver, timeout).until(d ->
                    d.findElements(OVERLAY_LOCATOR).isEmpty()
            );
        } catch (TimeoutException ignored) {
            // Обработается в clickByText через повторные  клики
        }
    }

    public void waitForPlannedBatchesAtLeast(int minCount, Duration timeout) {
        try {
            new WebDriverWait(driver, timeout).until(d -> getPlannedBatchesCount() >= minCount);
        } catch (TimeoutException e) {
            throw new TimeoutException(
                    "На графике так и не появилось хотя бы " + minCount
                            + " партий за " + timeout.getSeconds() + " секунд. Сейчас: " + getPlannedBatchesCount(),
                    e
            );
        }
    }

    public LocalDate getSelectedDate() {
        WebElement dateInput = driver.findElement(By.xpath("//input[@type='date']"));
        return LocalDate.parse(dateInput.getAttribute("value"));
    }

    public boolean isSelectAllButtonPresent() {
        List<WebElement> found = driver.findElements(By.xpath(
                "//button[normalize-space(text())='Отметить все' or .//span[normalize-space(text())='Отметить все']]"
        ));
        return !found.isEmpty();
    }

    public int getBatchSelectionRowsCount() {
        return driver.findElements(By.xpath("//div[.//span[contains(text(),'Масса:')]]")).size();
    }

    public LocalDate findFirstDateWithBatches(LocalDate startDate, int maxDaysToCheck, int direction) {
        for (int i = 0; i < maxDaysToCheck; i++) {
            LocalDate candidate = startDate.plusDays((long) i * direction);
            selectDate(candidate);
            if (getBatchSelectionRowsCount() > 0) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Не найдено ни одной даты с производственными заданиями в диапазоне поиска от "
                        + startDate + " (направление: " + (direction > 0 ? "вперёд" : "назад")
                        + ", " + maxDaysToCheck + " дней)."
        );
    }

    public int getPlannedBatchesCount() {
        return driver.findElements(By.className("rct-item")).size();
    }

    public int getServiceOperationsCount() {
        return driver.findElements(By.className("rct-item-fact")).size();
    }

    public int getUnpackedBatchesCount() {
        return countByBackgroundColor(COLOR_YELLOW_UNPACKED);
    }

    public int getPackedBatchesCount() {
        return countByBackgroundColor(COLOR_PURPLE_PACKED);
    }

    public int getPinnedBatchesCount() {
        return countWithRetry(() -> {
            List<WebElement> items = driver.findElements(By.className("rct-item"));
            int count = 0;
            for (WebElement el : items) {
                if (!el.findElements(By.className("fa-thumbtack")).isEmpty()) {
                    count++;
                }
            }
            return count;
        });
    }

    private int countByBackgroundColor(String rgbColor) {
        return countWithRetry(() -> {
            List<WebElement> items = driver.findElements(By.className("rct-item"));
            int count = 0;
            for (WebElement el : items) {
                String style = el.getAttribute("style");
                if (style != null && style.contains("background: " + rgbColor)) {
                    count++;
                }
            }
            return count;
        });
    }

    private int countWithRetry(java.util.function.Supplier<Integer> counter) {
        int attempts = 0;
        while (true) {
            try {
                return counter.get();
            } catch (StaleElementReferenceException e) {
                attempts++;
                if (attempts >= 5) {
                    throw e;
                }
                try { Thread.sleep(300); } catch (InterruptedException ignored) {}
            }
        }
    }

    public void openLineSettings() { clickByText("Настройка линий"); }
    public void closeLineSettings() { clickByText("Закрыть"); }

    private void clickByText(String text) {
        By locator = By.xpath(
                "//button[normalize-space(text())='" + text + "' or .//span[normalize-space(text())='" + text + "']]"
        );

        dismissResultDialogIfPresent();
        waitForOverlayToDisappear(Duration.ofSeconds(20));
        WebElement el = wait.until(d -> d.findElement(locator));

        int attempts = 0;
        while (true) {
            try {
                el.click();
                return;
            } catch (ElementClickInterceptedException e) {
                attempts++;
                if (attempts >= 3) {
                    throw e;
                }
                dismissResultDialogIfPresent();
                waitForOverlayToDisappear(Duration.ofSeconds(20));
                el = wait.until(d -> d.findElement(locator));
            }
        }
    }

    public void quit() { driver.quit(); }
}