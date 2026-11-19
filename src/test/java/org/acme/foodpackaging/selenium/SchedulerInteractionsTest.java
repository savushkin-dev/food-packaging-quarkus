package org.acme.foodpackaging.selenium;

import org.junit.jupiter.api.*;
import org.openqa.selenium.WebDriver;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import org.acme.foodpackaging.selenium.SchedulerPage.JobIdentity;
import org.acme.foodpackaging.selenium.SchedulerPage.JobTimes;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("selenium")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SchedulerInteractionsTest {
    private static final LocalDate PLAN_DATE=LocalDate.parse(System.getProperty("scheduler.test.planDate","2026-04-10"));
    private static final LocalDate TASK_DATE=LocalDate.parse(System.getProperty("scheduler.test.taskDate","2026-04-07"));
    private static final String SOURCE_LINE=System.getProperty("scheduler.test.sourceLine","Линия №2 GSL6");
    private static final String TARGET_LINE=System.getProperty("scheduler.test.targetLine","Линия №3 GSL6");
    private static final int SOURCE_INDEX=Integer.parseInt(System.getProperty("scheduler.test.sourceIndex","0"));
    private static WebDriver driver;
    private static SchedulerPage page;
    private long testStarted;

    private static void log(String message){System.out.printf("[%s] [SELENIUM] %s%n",LocalTime.now().withNano(0),message);}
    @BeforeAll
    static void setUp(){
        log("Подготовка: запуск Chrome");driver=SchedulerPage.createLocalChromeDriver();page=new SchedulerPage(driver);
        log("Подготовка: открытие страницы");page.open();
        log("Подготовка: выбор даты плана "+PLAN_DATE);page.selectDate(PLAN_DATE);
        assertThat(page.isDateSelected(PLAN_DATE)).isTrue();
        log("Подготовка: выбор заданий за "+TASK_DATE);page.clickSelectAllForTaskDateAndWait(TASK_DATE);
        assertThat(page.areAllRowCheckboxesCheckedForTaskDate(TASK_DATE)).isTrue();
        log("Подготовка: догрузка плана");page.clickLoadPlan();assertThat(page.isLoadPlanSuccessful()).isTrue();
        log("Подготовка: запуск планирования");page.clickStartPlanning();
        log("Подготовка: ожидание доступности кнопки остановки");page.waitUntilStopButtonActive();
        log("Подготовка: остановка планирования");page.clickStopPlanning();assertThat(page.isStartButtonShownAfterStop()).isTrue();page.waitUntilJobCardsVisible();
        log("Подготовка: проверка линий и заданий: "+SOURCE_LINE+" -> "+TARGET_LINE);
        assertThat(SOURCE_LINE).isNotEqualTo(TARGET_LINE);
        assertThat(page.getLineNames()).contains(SOURCE_LINE,TARGET_LINE);
        List<JobIdentity> source=page.getJobOrder(SOURCE_LINE),target=page.getJobOrder(TARGET_LINE);
        assertThat(source).hasSizeGreaterThanOrEqualTo(4).doesNotHaveDuplicates();assertThat(target).isNotEmpty().doesNotHaveDuplicates();
        assertThat(SOURCE_INDEX).isBetween(0,source.size()-1);
        log("Подготовка: снятие закрепления исходной линии");page.unpinLine(SOURCE_LINE);
        log("Подготовка: снятие закрепления целевой линии");page.unpinLine(TARGET_LINE);
        log("Подготовка завершена");
    }
    @BeforeEach
    void start(TestInfo info){testStarted=System.nanoTime();log("НАЧАЛО: "+info.getTestMethod().orElseThrow().getName());}
    @AfterEach
    void finish(TestInfo info){log("КОНЕЦ ВЫПОЛНЕНИЯ: "+info.getTestMethod().orElseThrow().getName()+"; "+((System.nanoTime()-testStarted)/1_000_000_000)+" с. Результат PASS/FAIL смотрите в отчёте Surefire.");}
    @AfterAll
    static void tearDown(){
        if(driver==null)return;
        if(Boolean.getBoolean("keepBrowserOpen"))log("Завершение: Chrome оставлен открытым (keepBrowserOpen=true)");
        else driver.quit();
    }

    @Test @Order(1)
    void shouldMoveJobBetweenLinesAndPreserveRemainingOrder(){
        List<JobIdentity> sourceBefore=page.getJobOrder(SOURCE_LINE),targetBefore=page.getJobOrder(TARGET_LINE);
        JobIdentity moved=sourceBefore.get(SOURCE_INDEX);
        assertThat(moved.np()).as("У переносимого задания должен быть видимый NP").isNotBlank();
        assertThat(targetBefore).doesNotContain(moved);
        int insert=Math.min(1,targetBefore.size());
        List<JobIdentity> expectedSource=new ArrayList<>(sourceBefore);expectedSource.remove(SOURCE_INDEX);
        List<JobIdentity> expectedTarget=new ArrayList<>(targetBefore);expectedTarget.add(insert,moved);
        try{
            log("Перенос между линиями: "+SOURCE_LINE+" -> "+TARGET_LINE+"; "+moved);
            page.moveJob(SOURCE_LINE,TARGET_LINE,SOURCE_INDEX,1,insert);
            assertThat(page.getJobOrder(SOURCE_LINE)).containsExactlyElementsOf(expectedSource);
            assertThat(page.getJobOrder(TARGET_LINE)).containsExactlyElementsOf(expectedTarget);
            assertThat(page.getJobOrder(TARGET_LINE)).filteredOn(moved::equals).hasSize(1);
            assertThat(page.getJobOrder(SOURCE_LINE)).doesNotContain(moved);
        }finally{
            log("Восстановление: возврат перенесённого задания");
            List<JobIdentity> target=page.getJobOrder(TARGET_LINE);
            if(target.contains(moved)&&!page.getJobOrder(SOURCE_LINE).contains(moved))page.moveJob(TARGET_LINE,SOURCE_LINE,target.indexOf(moved),1,SOURCE_INDEX);
            assertThat(page.getJobOrder(SOURCE_LINE)).containsExactlyElementsOf(sourceBefore);
            assertThat(page.getJobOrder(TARGET_LINE)).containsExactlyElementsOf(targetBefore);
        }
    }

    @Test @Order(2)
    void shouldMoveJobWithinLineUsingIndexAfterRemoval(){
        List<JobIdentity> before=page.getJobOrder(SOURCE_LINE),expected=new ArrayList<>(before);
        JobIdentity moved=expected.remove(0);expected.add(2,moved);
        try{
            log("Перестановка внутри линии: индекс 0 -> 2 после удаления");
            page.moveJob(SOURCE_LINE,SOURCE_LINE,0,1,2);
            assertThat(page.getJobOrder(SOURCE_LINE)).containsExactlyElementsOf(expected);
        }finally{log("Восстановление: исходная позиция задания");restoreOrder(SOURCE_LINE,before);}
    }

    @Test @Order(3)
    void shouldSortRangeAscendingAndDescendingByNp(){
        List<JobIdentity> before=page.getJobOrder(SOURCE_LINE);int from=findSortableRange(before,3),count=3;
        try{
            log("Сортировка: начальная сортировка по убыванию");
            page.sortRange(SOURCE_LINE,from,count,false);
            List<JobIdentity> descending=sortedRange(before,from,count,false);
            assertThat(page.getJobOrder(SOURCE_LINE)).containsExactlyElementsOf(descending);
            assertThat(npValues(page.getJobOrderInRange(SOURCE_LINE,from,count))).isSortedAccordingTo(Comparator.reverseOrder());
            log("Сортировка: по возрастанию");page.sortRange(SOURCE_LINE,from,count,true);
            List<JobIdentity> ascending=sortedRange(before,from,count,true);
            assertThat(page.getJobOrder(SOURCE_LINE)).containsExactlyElementsOf(ascending);
            assertThat(npValues(page.getJobOrderInRange(SOURCE_LINE,from,count))).isSorted();
            log("Сортировка: по убыванию");page.sortRange(SOURCE_LINE,from,count,false);
            assertThat(page.getJobOrder(SOURCE_LINE)).containsExactlyElementsOf(descending);
            assertThat(npValues(page.getJobOrderInRange(SOURCE_LINE,from,count))).isSortedAccordingTo(Comparator.reverseOrder());
        }finally{log("Восстановление: исходный порядок после сортировки");restoreOrder(SOURCE_LINE,before);}
    }

    @Test @Order(4)
    void shouldPinOnlyPrefixThroughSelectedJob(){
        List<JobIdentity> before=page.getJobOrder(SOURCE_LINE);int count=2;
        try{
            log("Закрепление первых "+count+" заданий линии");page.pinLineThrough(SOURCE_LINE,count);
            List<Boolean> pins=page.getPinnedState(SOURCE_LINE);assertThat(pins).hasSize(before.size());
            for(int i=0;i<pins.size();i++)assertThat(pins.get(i)).as("Закрепление карточки с индексом %s",i).isEqualTo(i<count);
            assertThat(page.getJobOrder(SOURCE_LINE)).containsExactlyElementsOf(before);
        }finally{
            log("Восстановление: снятие закрепления");page.unpinLine(SOURCE_LINE);
            assertThat(page.getPinnedState(SOURCE_LINE)).hasSize(before.size()).containsOnly(false);
            assertThat(page.getJobOrder(SOURCE_LINE)).containsExactlyElementsOf(before);
        }
    }

    @Test @Order(5)
    void shouldShiftEntireLineWhenStartTimeChanges(){
        List<JobIdentity> originalOrder=page.getJobOrder(SOURCE_LINE);assertThat(originalOrder).isNotEmpty().doesNotHaveDuplicates();
        LocalDateTime originalStart=page.getLineStart(SOURCE_LINE),shiftedStart=originalStart.plusMinutes(30);
        log("Сдвиг времени: API-снимок всех "+originalOrder.size()+" заданий до изменения");
        Map<JobIdentity,JobTimes> before=page.readLineJobTimesFromApi(SOURCE_LINE);
        assertThat(before.keySet()).containsExactlyElementsOf(originalOrder);
        JobIdentity first=originalOrder.get(0);
        assertThat(page.readJobTimes(SOURCE_LINE,0)).as("Окно информации должно совпадать с API до изменения").isEqualTo(before.get(first));
        try{
            log("Сдвиг времени: изменение начала линии на "+shiftedStart);page.changeLineStart(SOURCE_LINE,shiftedStart);
            log("Сдвиг времени: API-снимок всех заданий после изменения");Map<JobIdentity,JobTimes> after=page.readLineJobTimesFromApi(SOURCE_LINE);
            assertThat(page.getLineStart(SOURCE_LINE)).isEqualTo(shiftedStart);
            assertThat(page.getJobOrder(SOURCE_LINE)).containsExactlyElementsOf(originalOrder);
            assertThat(after.keySet()).containsExactlyElementsOf(originalOrder);
            assertThat(after.get(first).start()).as("Первое задание начинается в новое время начала линии").isEqualTo(shiftedStart);
            for(JobIdentity identity:originalOrder){
                JobTimes oldTimes=before.get(identity),newTimes=after.get(identity);assertThat(newTimes).as("Задание %s",identity).isNotNull();
                assertThat(newTimes.start()).as("Сдвиг начала %s",identity).isEqualTo(oldTimes.start().plusMinutes(30));
                assertThat(newTimes.end()).as("Сдвиг конца %s",identity).isEqualTo(oldTimes.end().plusMinutes(30));
            }
            assertThat(page.readJobTimes(SOURCE_LINE,0)).as("Окно информации должно совпадать с API после изменения").isEqualTo(after.get(first));
        }finally{
            log("Восстановление: исходное начало линии "+originalStart);page.changeLineStart(SOURCE_LINE,originalStart);
            assertThat(page.getLineStart(SOURCE_LINE)).isEqualTo(originalStart);
            assertThat(page.getJobOrder(SOURCE_LINE)).containsExactlyElementsOf(originalOrder);
            assertThat(page.readLineJobTimesFromApi(SOURCE_LINE)).as("Времена всех заданий после восстановления").isEqualTo(before);
        }
    }
    private static List<Integer> npValues(List<JobIdentity> jobs){return jobs.stream().map(j->Integer.parseInt(j.np())).toList();}
    private static int findSortableRange(List<JobIdentity> order,int count){
        for(int from=0;from+count<=order.size();from++){
            List<JobIdentity> range=order.subList(from,from+count);
            if(range.stream().allMatch(j->j.np().matches("\\d+"))&&new HashSet<>(npValues(range)).size()==count)return from;
        }
        throw new IllegalStateException("Нет "+count+" соседних заданий с непустыми различными числовыми NP");
    }
    private static List<JobIdentity> sortedRange(List<JobIdentity> before,int from,int count,boolean ascending){
        List<JobIdentity> result=new ArrayList<>(before);Comparator<JobIdentity> cmp=Comparator.comparingInt(j->Integer.parseInt(j.np()));
        result.subList(from,from+count).sort(ascending?cmp:cmp.reversed());return result;
    }
    private static void restoreOrder(String line,List<JobIdentity> expected){
        List<JobIdentity> current=page.getJobOrder(line);
        assertThat(current).as("Перед восстановлением состав заданий должен сохраниться").containsExactlyInAnyOrderElementsOf(expected);
        for(int i=0;i<expected.size();i++){
            current=page.getJobOrder(line);if(current.get(i).equals(expected.get(i)))continue;
            int from=current.indexOf(expected.get(i));if(from<0)throw new IllegalStateException("Потеряно задание "+expected.get(i));
            log("Восстановление порядка: индекс "+from+" -> "+i);page.moveJob(line,line,from,1,i);
        }
        assertThat(page.getJobOrder(line)).containsExactlyElementsOf(expected);
    }
}