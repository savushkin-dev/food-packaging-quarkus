package org.acme.foodpackaging.selenium;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.eclipse.microprofile.config.ConfigProvider;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Page Object для страницы Планировщика производства.
 * Все локаторы подтверждены через DevTools на реальных данных.
 * Диаграмма построена на react-calendar-timeline (rct-item / rct-item-fact).
 */
public class SchedulerPage {

    private static final String URL;

    static {
        System.setProperty("smallrye.config.locations",
                Paths.get(System.getProperty("user.dir"), ".env").toUri().toString());

        URL = ConfigProvider.getConfig().getValue("scheduler.url", String.class);
    }

    private static final String COLOR_YELLOW_UNPACKED = "rgb(255, 252, 210)";
    private static final String COLOR_PURPLE_PACKED = "rgb(249, 239, 255)";
    private static final String COLOR_SERVICE_OPERATION = "rgb(203, 255, 147)";

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

    public void clickSelectAll() { clickByText("Отметить все"); }
    public void clickLoadPlan() { clickByText("Догрузить план"); }
    public void clickStartPlanning() { clickByText("Планировать"); }
    public void clickStopPlanning() { clickByText("Остановить"); }
    public void clickSort() { clickByText("Отсортировать"); }
    public void clickSave() { clickByText("Сохранить"); }
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

    public void waitUntilScoreStabilized() {
        wait.until(d -> {
            int first = getExecutionTime();
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            int second = getExecutionTime();
            return first == second;
        });
    }

    public void selectDate(LocalDate date) {
        WebElement dateInput = wait.until(d ->
                d.findElement(By.xpath("//input[@type='date']")));
        dateInput.clear();
        dateInput.sendKeys(date.format(DateTimeFormatter.ISO_LOCAL_DATE));
    }

    public LocalDate getSelectedDate() {
        WebElement dateInput = driver.findElement(By.xpath("//input[@type='date']"));
        return LocalDate.parse(dateInput.getAttribute("value"));
    }

    public int getPlannedBatchesCount() {
        return driver.findElements(By.className("rct-item")).size();
    }

    public int getServiceOperationsCount() {
        return driver.findElements(By.className("rct-item-fact")).size();
    }

    public int getUnpackedBatchesCount() {
        return countByBackgroundColor("rct-item", COLOR_YELLOW_UNPACKED);
    }

    public int getPackedBatchesCount() {
        return countByBackgroundColor("rct-item", COLOR_PURPLE_PACKED);
    }

    public int getPinnedBatchesCount() {
        List<WebElement> items = driver.findElements(By.className("rct-item"));
        return (int) items.stream()
                .filter(el -> !el.findElements(By.className("fa-thumbtack")).isEmpty())
                .count();
    }

    private int countByBackgroundColor(String className, String rgbColor) {
        List<WebElement> items = driver.findElements(By.className(className));
        return (int) items.stream()
                .filter(el -> {
                    String style = el.getAttribute("style");
                    return style != null && style.contains("background: " + rgbColor);
                })
                .count();
    }

    public void openLineSettings() { clickByText("Настройка линий"); }
    public void closeLineSettings() { clickByText("Закрыть"); }

    private void clickByText(String text) {
        WebElement el = wait.until(d ->
                d.findElement(By.xpath(
                        "//button[normalize-space(text())='" + text + "' or .//span[normalize-space(text())='" + text + "']]"
                )));
        el.click();
    }

    public void quit() { driver.quit(); }
}