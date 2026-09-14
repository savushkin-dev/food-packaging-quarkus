## Selenium-тесты планировщика (запуск и структура)

- Класс: `SchedulerEmptyDayTest`
- Page Object: `SchedulerPage`
- Стенд: `"http://localhost:8080/scheduler`
- Требуется: Java 21, установленный Chrome (ChromeDriver подтягивается автоматически через `WebDriverManager`)

### Запуск всего класса

```shell
mvn clean test "-Pselenium" "-Dtest=SchedulerEmptyDayTest"
```

### Запуск всего профиля selenium (без фильтра по классу)

```shell
mvn clean test "-Pselenium"
```

- `-Pselenium` — активирует профиль с Selenium-зависимостями, без него тесты не подключаются к сборке.
- `-Dtest=SchedulerEmptyDayTest` — ограничивает запуск одним классом.
- Профиль поднимает реальный Chrome (не headless), окно 1600×900.

## Сценарий (8 тестов, строго по порядку)

Тесты помечены `@Order(1)`–`@Order(8)` и `@TestMethodOrder(OrderAnnotation.class)`. `driver`/`page` — `static`, создаются один раз в `@BeforeAll`, закрываются в `@AfterAll`. Тесты не изолированы — каждый следующий использует состояние, оставленное предыдущим.

|№|Метод|Действие|Проверка|
|-|-----|--------|--------|
|1|`shouldLoadSchedulerPageWithoutErrorsAndWithPlanningArea`|Открыть страницу планировщика|Нет ошибок; область с линиями загружена|
|2|`shouldSelectDateAndShowSelectAllButtons`|Выбрать дату (10.04.2026)|Дата выбрана; кнопки "Отметить все" видны|
|3|`shouldOpenLineSettingsWithStartAndMaxTimeThenClose`|Открыть "Настройка линий"|У каждой линии заданы дата начала и максимальное время; диалог закрывается|
|4|`shouldSelectAllProductsForTargetDate`|Отметить все продукты за дату|Все чекбоксы установлены|
|5|`shouldLoadPlanSuccessfully`|Нажать "Догрузить план"|Догрузка без ошибок|
|6|`shouldStartPlanningAndShowDisabledStopButton`|Нажать "Планировать"|Кнопка стала "Остановить" и неактивна|
|7|`shouldWaitAndStopPlanningThenShowStartButtonAgain`|Дождаться активации, нажать "Остановить"|Кнопка снова "Планировать"|
|8|`shouldReadResultIndicatorsAfterPlanning`|Прочитать показатели|Ошибки/простой/время выполнения — читаемые числа|

## Технические детали

1. Ввод даты — через JS-сеттер `HTMLInputElement.prototype.value` + события `input`/`change` (обычный `sendKeys()` не всегда триггерит реактивность страницы).
2. Перед каждым кликом — `waitUntilOverlayGone()` (ожидание исчезновения модального оверлея `div.fixed.bg-black/40|50`), иначе `ElementClickInterceptedException`.
3. Диалоги подтверждения/результата (`confirmActionDialogIfPresent`, `dismissResultDialogIfPresent`) обрабатываются опционально — если диалог не появился, ошибка проглатывается (`try/catch`), тест продолжает работу.
4. Локатор строки в модалке настройки линий — `//span[contains(., 'Линия №')]/ancestor::div[2]`, поля — `input[type='datetime-local']`.
5. Проверка disabled-состояния кнопки "Остановить" использует `wait.until()` (иначе гонка: кнопка ещё не успела отрендериться после клика).

## Стек

- Selenium WebDriver + ChromeDriver (`WebDriverManager`)
- JUnit 5 (`@TestMethodOrder`, `@Order`, `@BeforeAll`/`@AfterAll`)
- AssertJ (`assertThat(...).as("...").isTrue()`)
- Maven Surefire + JaCoCo

## Результат последнего прогона

```
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 02:50 min
```

## Частые проблемы

|Симптом|Причина|
|-------|-------|
|`ElementClickInterceptedException`|Клик до исчезновения оверлея — покрыто `waitUntilOverlayGone()`|
|Тест 6/7 зависает|Расчёт на сервере дольше таймаута ожидания (3 мин)|
|`NoSuchElementException` в модалке линий|Неверный локатор строки — актуальный: `ancestor::div[2]` от `span` с "Линия №"|
|Оставшаяся вкладка/окно Chrome после прогона|`driver.quit()` не вызван — проверить, что `@AfterAll` отработал до конца|