package org.acme.foodpackaging.selenium;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.eclipse.microprofile.config.ConfigProvider;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.nio.file.Paths;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.net.URI;
import java.net.http.*;
import java.math.BigDecimal;

public class SchedulerPage {
    private static final String URL;

    static {
        System.setProperty(
                "smallrye.config.locations",
                Paths.get(System.getProperty("user.dir"), ".env").toUri().toString()
        );
        URL = ConfigProvider.getConfig().getValue("scheduler.url", String.class);
    }
    private static final By ANY_OVERLAY=By.xpath("//div[contains(@class,'fixed') and (contains(@class,'bg-black/50') or contains(@class,'bg-black/40'))]");
    private static final By STOP_BUTTON=By.xpath("//button[contains(., 'Остановить')]");
    private static final By COUNTDOWN_BUTTON=By.xpath("//button[.//span[starts-with(normalize-space(.), 'Подождите ')]]");
    private static final By START_BUTTON=By.xpath("//button[contains(., 'Планировать')]");
    private static final By DATE_INPUT=By.xpath("//input[@type='date']");
    private static final By TASK_SELECT_BUTTONS=By.xpath("//span[starts-with(normalize-space(.), 'Задание ')]/ancestor::div[contains(@class,'justify-between')][1]//button[contains(., 'Отметить все') or contains(., 'Снять все') or contains(., 'Нет доступных')]");
    private static final By LINE_ROWS=By.xpath("//div[contains(concat(' ',normalize-space(@class),' '),' rct-sidebar-row ')]//div[contains(concat(' ',normalize-space(@class),' '),' group-title ')]");
    private static final By SIDEBAR_ROWS=By.xpath("//div[contains(concat(' ',normalize-space(@class),' '),' rct-sidebar-row ')]");

    private Object queryLine(String line,String mode,String title,String np){
        By row=By.xpath("(" + "//div[contains(concat(' ',normalize-space(@class),' '),' group-title ')]"
                + "[normalize-space(.)=" + literal(line) + "])/ancestor::div[contains(concat(' ',normalize-space(@class),' '),' rct-sidebar-row ')][1]");
        return js().executeScript(LINE_QUERY_SCRIPT,line,mode,title,np,
                driver.findElements(row),driver.findElements(JOB_CARDS));
    }
    private static final By ERROR_TOAST=By.xpath("//*[contains(@class,'toast') or contains(@class,'error') or contains(@class,'alert')][contains(translate(., 'ОШИБКА', 'ошибка'), 'ошибка')]");
    private static final By JOB_CARDS=By.xpath("//div[contains(concat(' ',normalize-space(@class),' '),' rct-item ')]");
    private static final By MOVE_MODAL=By.xpath("//h1[normalize-space(.)='Перемещение элементов']/parent::div");
    private static final By SETTINGS_MODAL=By.xpath("//h1[normalize-space(.)='Настройки даты и времени планировщика']/parent::div");
    private static final By INFO_MODAL=By.xpath("//span[starts-with(normalize-space(.),'idBatch:')]/parent::div/parent::div");
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final WebDriver driver;
    private final WebDriverWait wait;

    public record JobCard(String title,String batch,int position,String start,String end) {
        public boolean isJob(){return position>0;}
    }
    public record LineSnapshot(String name,List<JobCard> cards) {
        public List<JobCard> jobs(){return cards.stream().filter(JobCard::isJob).toList();}
    }
    public record JobIdentity(String title,String np) {}
    public record JobTimes(LocalDateTime start,LocalDateTime end) {}

    public SchedulerPage(WebDriver driver){this.driver=driver;wait=new WebDriverWait(driver,Duration.ofMinutes(3));}
    private JavascriptExecutor js(){return (JavascriptExecutor)driver;}
    public static WebDriver createLocalChromeDriver(){
        WebDriverManager.chromedriver().setup();
        ChromeOptions options=new ChromeOptions();options.addArguments("--window-size=1600,900");
        WebDriver driver=new ChromeDriver(options);driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60));return driver;
    }
    public void open(){
        for(int attempt=1;attempt<=3;attempt++){
            try{driver.get(URL);new WebDriverWait(driver,Duration.ofSeconds(45)).until(d->!d.findElements(LINE_ROWS).isEmpty());return;}
            catch(TimeoutException e){if(attempt==3)throw new IllegalStateException("Страница не загрузилась: "+URL+", "+driver.getCurrentUrl(),e);}
        }
    }
    public boolean hasNoErrorMessages(){return driver.findElements(ERROR_TOAST).isEmpty()&&driver.findElements(By.xpath("//*[normalize-space(.)='Ошибка операции']")).isEmpty();}
    public boolean isPlanningAreaWithLinesLoaded(){return !driver.findElements(LINE_ROWS).isEmpty();}
    public void selectDate(LocalDate date){
        String value=date.toString();setInput(wait.until(d->d.findElement(DATE_INPUT)),value);
        wait.until(d->value.equals(d.findElement(DATE_INPUT).getDomProperty("value")));
        wait.until(d->{List<WebElement> buttons=d.findElements(TASK_SELECT_BUTTONS);return !buttons.isEmpty()&&buttons.stream().anyMatch(b->!b.getText().contains("Нет доступных")||b.isEnabled());});
        waitUntilOverlayGone();
    }
    public boolean isDateSelected(LocalDate date){return date.toString().equals(driver.findElement(DATE_INPUT).getDomProperty("value"));}
    public boolean areSelectAllButtonsPresent(){return !driver.findElements(TASK_SELECT_BUTTONS).isEmpty();}
    public void openLineSettings(){waitUntilOverlayGone();clickByText("Настройка линий");wait.until(ExpectedConditions.visibilityOfElementLocated(SETTINGS_MODAL));}
    public boolean isLineSettingsModalOpen(){return driver.findElements(SETTINGS_MODAL).stream().anyMatch(WebElement::isDisplayed);}
    public boolean lineSettingsHaveStartAndMaxTimeForEachLine(){
        List<WebElement> rows=driver.findElement(SETTINGS_MODAL).findElements(By.xpath(".//span[contains(normalize-space(.),'Линия №')]/parent::div/parent::div"));
        return !rows.isEmpty()&&rows.stream().allMatch(row->{List<WebElement> inputs=row.findElements(By.xpath(".//input[@type='datetime-local']"));return inputs.size()==2&&inputs.stream().allMatch(i->i.getDomProperty("value")!=null&&!i.getDomProperty("value").isEmpty());});
    }
    public void closeLineSettings(){waitForInteractable(SETTINGS_MODAL,By.xpath(".//button[normalize-space(.)='Закрыть']")).click();wait.until(ExpectedConditions.invisibilityOfElementLocated(SETTINGS_MODAL));waitUntilOverlayGone();}
    private String taskLabel(LocalDate date){return "Задание "+date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));}
    public void clickSelectAllForTaskDateAndWait(LocalDate date){
        waitUntilOverlayGone();
        WebElement button=wait.until(d->d.findElement(By.xpath("//span[normalize-space(.)="+literal(taskLabel(date))+"]/ancestor::div[contains(@class,'justify-between')][1]//button[contains(.,'Отметить все') or contains(.,'Снять все') or contains(.,'Нет доступных')]")));
        if(button.getText().contains("Нет доступных"))throw new IllegalStateException("Нет свободных партий: "+taskLabel(date));
        if(!button.getText().contains("Снять все"))button.click();
    }
    public boolean areAllRowCheckboxesCheckedForTaskDate(LocalDate date){
        List<WebElement> checks=driver.findElement(By.xpath("//span[normalize-space(.)="+literal(taskLabel(date))+"]/ancestor::div[contains(@class,'justify-between')][1]/following::div[1]")).findElements(By.xpath(".//input[@type='checkbox']"));
        return !checks.isEmpty()&&checks.stream().allMatch(WebElement::isSelected);
    }
    public void clickLoadPlan(){waitUntilOverlayGone();clickByText("Догрузить план");confirmActionDialogIfPresent();dismissResultDialogIfPresent();}
    public boolean isLoadPlanSuccessful(){return hasNoErrorMessages();}
    public void clickStartPlanning(){waitUntilOverlayGone();wait.until(ExpectedConditions.elementToBeClickable(START_BUTTON)).click();}
    public boolean isCountdownButtonShownAndDisabled(){
        return new WebDriverWait(driver,Duration.ofSeconds(15)).until(d->{try{return d.findElements(COUNTDOWN_BUTTON).stream().anyMatch(b->b.isDisplayed()&&!b.isEnabled()&&b.getText().trim().matches("Подождите\\s+\\d+:\\d{2}"));}catch(StaleElementReferenceException e){return false;}});
    }
    public boolean isStopButtonShownAndInitiallyDisabled(){return isCountdownButtonShownAndDisabled();}
    public void waitUntilStopButtonActive(){new WebDriverWait(driver,Duration.ofMinutes(4)).until(ExpectedConditions.elementToBeClickable(STOP_BUTTON));}
    public void clickStopPlanning(){wait.until(ExpectedConditions.elementToBeClickable(STOP_BUTTON)).click();}
    public boolean isStartButtonShownAfterStop(){return wait.until(d->{try{return d.findElements(START_BUTTON).stream().anyMatch(WebElement::isDisplayed);}catch(StaleElementReferenceException e){return false;}});}
    public int getErrorsCount(){return readIndicatorNumber("Ошибки");}
    public int getDowntimeMinutes(){return readIndicatorNumber("Время простоя");}
    public int getExecutionTimeMinutes(){return readIndicatorNumber("Время выполнения");}
    private int readIndicatorNumber(String label){String value=driver.findElement(By.xpath("//span[normalize-space(.)="+literal(label)+"]/preceding-sibling::span[1]")).getText().replaceAll("\\D+","");return value.isEmpty()?0:Integer.parseInt(value);}
    public void waitUntilJobCardsVisible(){wait.until(d->!d.findElements(JOB_CARDS).isEmpty());}
    public int countAllJobCards(){return driver.findElements(JOB_CARDS).size();}

    private static final String READ_LINES_SCRIPT="""
        const one=(root,path)=>document.evaluate(path,root,null,XPathResult.FIRST_ORDERED_NODE_TYPE,null).singleNodeValue;
        const badges=item=>Array.from(item.getElementsByTagName("span")).filter(e=>e.classList.contains("px-1")&&e.classList.contains("rounded"));
        const rows=arguments[0].map(r=>{
          const b=r.getBoundingClientRect(),t=one(r,".//div[contains(concat(' ',normalize-space(@class),' '),' group-title ')]");
          return {name:(t?t.textContent:r.textContent).trim(),top:b.top,bottom:b.bottom,cards:[]};
        });
        const val=(item,label)=>{const s=badges(item).find(x=>x.textContent.includes(label));return s&&s.firstElementChild?s.firstElementChild.textContent.trim():'';};
        for(const item of arguments[1]){
          const b=item.getBoundingClientRect(),cy=b.top+b.height/2;
          const row=rows.find(r=>cy>=r.top&&cy<r.bottom);if(!row||!b.width||!b.height)continue;
          const s=badges(item).find(x=>x.textContent.includes('Время'));
          const m=s?s.textContent.match(/(\\d{2}:\\d{2})\\s*-\\s*(\\d{2}:\\d{2})/):null;
          row.cards.push({left:b.left,title:item.getAttribute('title')||'',batch:val(item,'№ партии'),position:val(item,'Позиция на линии'),start:m?m[1]:'',end:m?m[2]:''});
        }
        rows.forEach(r=>r.cards.sort((a,b)=>a.left-b.left));return rows;
        """;
    @SuppressWarnings("unchecked")
    public List<LineSnapshot> readLines(){
        List<Map<String,Object>> raw=(List<Map<String,Object>>)js().executeScript(READ_LINES_SCRIPT,driver.findElements(SIDEBAR_ROWS),driver.findElements(JOB_CARDS));List<LineSnapshot> result=new ArrayList<>();
        for(Map<String,Object> row:raw){List<JobCard> cards=new ArrayList<>();
            for(Map<String,Object> c:(List<Map<String,Object>>)row.get("cards"))cards.add(new JobCard(String.valueOf(c.get("title")),String.valueOf(c.get("batch")),parseIntOrMinusOne(String.valueOf(c.get("position"))),String.valueOf(c.get("start")),String.valueOf(c.get("end"))));
            result.add(new LineSnapshot(String.valueOf(row.get("name")),cards));}
        return result;
    }
    private static int parseIntOrMinusOne(String text){String digits=text.replaceAll("\\D+","");return digits.isEmpty()?-1:Integer.parseInt(digits);}

    private static final String LINE_QUERY_SCRIPT="""
        const line=arguments[0],mode=arguments[1];
        const norm=s=>s.replace(/\\s+/g,' ').trim();
        const row=arguments[4][0];if(!row)return null;
        const bounds=row.getBoundingClientRect();
        const one=(root,path)=>document.evaluate(path,root,null,XPathResult.FIRST_ORDERED_NODE_TYPE,null).singleNodeValue;
        const text=e=>e?(e.innerText??e.textContent).trim():'';
        const identity=card=>({title:card.getAttribute('title'),np:text(one(card,".//span[normalize-space(.)='№ партии']/preceding-sibling::span[1]"))});
        const entries=[];
        for(const card of arguments[5]){
            if(!one(card,".//span[contains(normalize-space(.),'Позиция на линии')]"))continue;
            const b=card.getBoundingClientRect(),cy=b.top+b.height/2;
            if(cy<bounds.top||cy>=bounds.bottom||b.width<=0||b.height<=0)continue;
            const raw=text(one(card,".//span[contains(@class,'text-violet-600')][1]"));
            if(!/^[+-]?\\d+$/.test(raw))throw new Error('Invalid job position: '+raw);
            entries.push({card,position:Number(raw)});
        }
        entries.sort((a,b)=>a.position-b.position);
        if(mode==='target'){
            const wantedTitle=arguments[2],wantedNp=arguments[3];
            const found=entries.filter(e=>{const id=identity(e.card);return id.title===wantedTitle&&id.np===wantedNp;});
            if(found.length>1)throw new Error('Ambiguous job identity: '+wantedTitle+' / '+wantedNp);
            if(!found.length)return null;
            const card=found[0].card;card.scrollIntoView({block:'center',inline:'nearest'});return card;
        }
        if(mode==='pins')return entries.map(e=>!!one(e.card,".//i[contains(concat(' ',normalize-space(@class),' '),' fa-thumbtack ')]"));
        return entries.map(e=>identity(e.card));
        """;
    @SuppressWarnings("unchecked")
    private List<JobIdentity> readJobOrderNow(String line){
        List<Map<String,Object>> raw=(List<Map<String,Object>>)queryLine(line,"order",null,null);if(raw==null)return null;
        List<JobIdentity> result=new ArrayList<>(raw.size());for(Map<String,Object> item:raw)result.add(new JobIdentity((String)item.get("title"),(String)item.get("np")));return result;
    }
    public List<String> getLineNames(){return driver.findElements(By.xpath("//div[contains(concat(' ',normalize-space(@class),' '),' group-title ')]")).stream().map(WebElement::getText).map(String::trim).filter(s->!s.isEmpty()).toList();}
    public List<JobIdentity> getJobOrder(String line){return wait.until(d->{try{return readJobOrderNow(line);}catch(StaleElementReferenceException e){return null;}});}
    public List<JobIdentity> getJobOrderInRange(String line,int from,int count){List<JobIdentity> order=getJobOrder(line);validateRange(order.size(),from,count);return List.copyOf(order.subList(from,from+count));}
    private JobIdentity jobIdentity(String line,int index){return wait.until(d->{List<JobIdentity> order=readJobOrderNow(line);return order!=null&&index>=0&&index<order.size()?order.get(index):null;});}
    private void actOnJob(String line,JobIdentity identity,Consumer<WebElement> action){
        new WebDriverWait(driver,Duration.ofSeconds(20)).withMessage("Карточка не готова к действию: "+line+" / "+identity).until(d->{
            try{WebElement card=(WebElement)queryLine(line,"target",identity.title(),identity.np());if(card==null)return false;action.accept(card);return true;}
            catch(StaleElementReferenceException e){return false;}});
    }
    private void reveal(WebElement e){js().executeScript("arguments[0].scrollIntoView({block:'center',inline:'nearest'});",e);}
    public void selectJobRange(String line,int from,int count){
        List<JobIdentity> order=getJobOrder(line);validateRange(order.size(),from,count);JobIdentity first=order.get(from),last=order.get(from+count-1);
        actOnJob(line,first,e->new Actions(driver).click(e).perform());
        if(count>1)actOnJob(line,last,e->{try{new Actions(driver).keyDown(Keys.SHIFT).click(e).keyUp(Keys.SHIFT).perform();}finally{new Actions(driver).keyUp(Keys.SHIFT).perform();}});
    }
    private void contextMenu(String line,int index){JobIdentity target=jobIdentity(line,index);actOnJob(line,target,e->new Actions(driver).contextClick(e).perform());}
    private void exactButton(String text){wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[normalize-space(.)="+literal(text)+"]"))).click();}
    private WebElement waitForInteractable(By scope,By locator){
        return new WebDriverWait(driver,Duration.ofSeconds(15)).withMessage("Нет видимого доступного элемента: "+scope+" -> "+locator).until(d->{
            try{for(WebElement container:d.findElements(scope)){if(!container.isDisplayed())continue;for(WebElement element:container.findElements(locator))if(element.isDisplayed()&&element.isEnabled())return element;}return null;}
            catch(StaleElementReferenceException e){return null;}});
    }
    public void moveJob(String fromLine,String toLine,int fromIndex,int count,int insertIndex){
        if(insertIndex<0)throw new IllegalArgumentException("insertIndex must be non-negative");waitUntilOverlayGone();
        List<JobIdentity> source=new ArrayList<>(getJobOrder(fromLine));validateRange(source.size(),fromIndex,count);
        List<JobIdentity> moved=new ArrayList<>(source.subList(fromIndex,fromIndex+count));source.subList(fromIndex,fromIndex+count).clear();
        boolean same=fromLine.equals(toLine);List<JobIdentity> target=same?source:new ArrayList<>(getJobOrder(toLine));
        int index=Math.min(insertIndex,target.size());target.addAll(index,moved);
        selectJobRange(fromLine,fromIndex,count);contextMenu(fromLine,fromIndex);exactButton("Переместить");
        WebDriverWait modalWait=new WebDriverWait(driver,Duration.ofSeconds(15));modalWait.until(ExpectedConditions.visibilityOfElementLocated(MOVE_MODAL));
        By controlLocator=By.xpath(".//input[@role='combobox']/ancestor::div[contains(concat(' ',normalize-space(@class),' '),'-control ')][1]");
        WebElement control=waitForInteractable(MOVE_MODAL,controlLocator);reveal(control);control.click();
        modalWait.withMessage("Список линий не открылся").until(d->{try{return d.findElements(MOVE_MODAL).stream().filter(WebElement::isDisplayed).flatMap(m->m.findElements(By.xpath(".//input[@role='combobox']")).stream()).anyMatch(input->"true".equals(input.getDomAttribute("aria-expanded")));}catch(StaleElementReferenceException e){return false;}});
        By optionLocator=By.xpath("//*[@role='option' and normalize-space(.)="+literal(toLine)+"]");
        new WebDriverWait(driver,Duration.ofSeconds(15)).withMessage("В списке не найдена доступная линия: "+toLine).until(d->{try{return d.findElements(optionLocator).stream().filter(WebElement::isDisplayed).filter(WebElement::isEnabled).findFirst().orElse(null);}catch(StaleElementReferenceException e){return null;}}).click();
        By selectedValue=By.xpath(".//div[contains(concat(' ',normalize-space(@class),' '),'-singleValue ') and normalize-space(.)="+literal(toLine)+"]");
        modalWait.withMessage("Выбранная линия не обновилась: "+toLine).until(d->{try{return d.findElements(MOVE_MODAL).stream().filter(WebElement::isDisplayed).flatMap(m->m.findElements(selectedValue).stream()).anyMatch(WebElement::isDisplayed);}catch(StaleElementReferenceException e){return false;}});
        WebElement toEnd=waitForInteractable(MOVE_MODAL,By.xpath(".//input[@type='checkbox']"));if(toEnd.isSelected())toEnd.click();
        String positionValue=String.valueOf(index+1);setInput(waitForInteractable(MOVE_MODAL,By.xpath(".//input[@type='number']")),positionValue);
        modalWait.withMessage("Не удалось установить позицию: "+positionValue).until(d->{try{return positionValue.equals(d.findElement(MOVE_MODAL).findElement(By.xpath(".//input[@type='number']")).getDomProperty("value"));}catch(StaleElementReferenceException e){return false;}});
        waitForInteractable(MOVE_MODAL,By.xpath(".//button[normalize-space(.)='Применить']")).click();
        wait.until(ExpectedConditions.invisibilityOfElementLocated(MOVE_MODAL));waitUntilOverlayGone();waitOrder(toLine,target);if(!same)waitOrder(fromLine,source);
    }
    public void sortRange(String line,int from,int count,boolean ascending){
        if(count<2)throw new IllegalArgumentException("Нужно минимум два задания");List<JobIdentity> expected=new ArrayList<>(getJobOrder(line));validateRange(expected.size(),from,count);
        List<JobIdentity> range=expected.subList(from,from+count);if(range.stream().anyMatch(j->j.np().isBlank()))throw new IllegalArgumentException("Нужен диапазон с видимыми NP без maintenance");
        Comparator<JobIdentity> cmp=Comparator.comparingInt(j->Integer.parseInt(j.np()));range.sort(ascending?cmp:cmp.reversed());
        waitUntilOverlayGone();selectJobRange(line,from,count);contextMenu(line,from);exactButton(ascending?"Отсортировать по возрастанию":"Отсортировать по убыванию");waitUntilOverlayGone();waitOrder(line,expected);
    }
    @SuppressWarnings("unchecked")
    public List<Boolean> getPinnedState(String line){return wait.until(d->(List<Boolean>)queryLine(line,"pins",null,null));}
    public void pinLineThrough(String line,int count){
        validateRange(getJobOrder(line).size(),0,count);waitUntilOverlayGone();selectJobRange(line,count-1,1);contextMenu(line,count-1);exactButton("Закрепить линию по");waitUntilOverlayGone();
        wait.until(d->{try{List<Boolean> pins=getPinnedState(line);if(pins.isEmpty())return false;for(int i=0;i<pins.size();i++)if(pins.get(i)!=(i<count))return false;return true;}catch(StaleElementReferenceException e){return false;}});
    }
    public void unpinLine(String line){waitUntilOverlayGone();selectJobRange(line,0,1);contextMenu(line,0);exactButton("Открепить линию");waitUntilOverlayGone();wait.until(d->{try{return getPinnedState(line).stream().noneMatch(Boolean::booleanValue);}catch(StaleElementReferenceException e){return false;}});}
    private WebElement lineStartInput(String line){return driver.findElement(SETTINGS_MODAL).findElement(By.xpath(".//span[normalize-space(.)="+literal(line+":")+"]/parent::div/parent::div/div[2]/input[@type='datetime-local']"));}
    public LocalDateTime getLineStart(String line){openLineSettings();LocalDateTime result=LocalDateTime.parse(lineStartInput(line).getDomProperty("value"));closeLineSettings();return result;}
    public void changeLineStart(String line,LocalDateTime start){
        openLineSettings();String value=start.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));setInput(lineStartInput(line),value);
        wait.until(d->value.equals(lineStartInput(line).getDomProperty("value")));closeLineSettings();
        wait.until(d->readJobTimes(line,0).start().equals(start));
    }
    public JobTimes readJobTimes(String line,int index){
        waitUntilOverlayGone();JobIdentity target=jobIdentity(line,index);actOnJob(line,target,e->new Actions(driver).doubleClick(e).perform());
        wait.until(ExpectedConditions.visibilityOfElementLocated(INFO_MODAL));
        JobTimes times=wait.until(d->{try{WebElement modal=d.findElement(INFO_MODAL);return new JobTimes(dateField(modal,"Начало по плану:"),dateField(modal,"Конец по плану:"));}catch(StaleElementReferenceException e){return null;}});
        new WebDriverWait(driver,Duration.ofSeconds(15)).until(d->{try{
            if(d.findElements(INFO_MODAL).stream().noneMatch(WebElement::isDisplayed))return true;
            Optional<WebElement> backdrop=d.findElements(ANY_OVERLAY).stream().filter(WebElement::isDisplayed).findFirst();if(backdrop.isEmpty())return false;
            js().executeScript("arguments[0].click();",backdrop.get());return true;
        }catch(StaleElementReferenceException e){return false;}});
        wait.until(ExpectedConditions.invisibilityOfElementLocated(INFO_MODAL));return times;
    }
    private LocalDateTime dateField(WebElement modal,String label){return LocalDateTime.parse(modal.findElement(By.xpath(".//span[normalize-space(.)="+literal(label)+"]/following-sibling::span[1]")).getText().trim(),DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));}

    /** Read-only verification; all changes to the plan still go through the UI. */
    public Map<JobIdentity,JobTimes> readLineJobTimesFromApi(String line){
        List<JobIdentity> order=getJobOrder(line);
        if(order.isEmpty()||new HashSet<>(order).size()!=order.size())throw new IllegalStateException("Пустой/неоднозначный порядок карточек: "+line);
        Map<?,?> root=readFrontData();
        if(!(root.get("lines") instanceof List<?> lines)||!(root.get("jobs") instanceof List<?> jobs))throw new IllegalStateException("В frontData нет массивов lines/jobs");
        List<Map<?,?>> matchedLines=new ArrayList<>();
        for(Object value:lines)if(value instanceof Map<?,?> l&&line.equals(text(l.get("name"))))matchedLines.add(l);
        if(matchedLines.size()!=1)throw new IllegalStateException("Линия не найдена однозначно в API: "+line);
        String lineId=text(matchedLines.get(0).get("id"));Map<JobIdentity,List<Map<?,?>>> candidates=new HashMap<>();
        for(Object value:jobs){
            if(!(value instanceof Map<?,?> job)||!(job.get("line") instanceof Map<?,?> assigned)||!lineId.equals(text(assigned.get("id"))))continue;
            JobIdentity identity=new JobIdentity(text(job.get("name")),text(job.get("np")));
            candidates.computeIfAbsent(identity,k->new ArrayList<>()).add(job);
        }
        Set<JobIdentity> uiJobs=new LinkedHashSet<>(order);
        Set<JobIdentity> missing=new LinkedHashSet<>(uiJobs);
        missing.removeAll(candidates.keySet());
        Set<JobIdentity> extra=new LinkedHashSet<>(candidates.keySet());
        extra.removeAll(uiJobs);
        if(!missing.isEmpty()||!extra.isEmpty()){
            throw new IllegalStateException("UI/API: разные наборы заданий линии " + line
                    + "; отсутствуют в API: " + missing + "; отсутствуют в UI: " + extra);
        }
        Map<JobIdentity,JobTimes> result=new LinkedHashMap<>();
        for(JobIdentity identity:order){
            List<Map<?,?>> found=candidates.getOrDefault(identity,List.of());
            if(found.size()!=1)throw new IllegalStateException("Неоднозначное сопоставление UI/API: "+identity+"; совпадений="+found.size());
            Map<?,?> job=found.get(0);LocalDateTime start=apiDate(job,"startProductionDateTime");LocalDateTime end=apiDate(job,"endDateTime");
            Object delayValue=job.get("delayDuration");BigDecimal delay=delayValue==null?BigDecimal.ZERO:new BigDecimal(delayValue.toString());
            end=end.minusNanos(delay.multiply(new BigDecimal("60000000000")).longValueExact());
            result.put(identity,new JobTimes(start.withNano(0),end.withNano(0)));
        }
        if(!getJobOrder(line).equals(order))throw new IllegalStateException("Порядок изменился во время снимка API: "+line);
        return Collections.unmodifiableMap(result);
    }
    private String requireSolverSessionId(){
        Object value=js().executeScript("return window.sessionStorage.getItem('solverSessionId');");
        if(!(value instanceof String id)||id.isBlank())throw new IllegalStateException(
                "В sessionStorage браузера Selenium нет solverSessionId. Чтение frontData без X-Session-Id запрещено: нельзя проверять другой план.");
        return id;
    }
    private Map<?,?> readFrontData(){
        String sessionId=requireSolverSessionId();
        URI uri=URI.create(System.getProperty("scheduler.test.frontDataUrl","http://10.30.0.5:8083/schedule/frontData"));
        if(!Set.of("http","https").contains(uri.getScheme()))throw new IllegalArgumentException("frontDataUrl должен быть HTTP(S)");
        HttpRequest.Builder request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
                .header("Accept","application/json").header("X-Session-Id",sessionId).GET();
        URI browser=URI.create(driver.getCurrentUrl());
        if(Objects.equals(uri.getHost(),browser.getHost())){
            String path=uri.getPath().isEmpty()?"/":uri.getPath();List<String> cookies=new ArrayList<>();
            for(Cookie c:driver.manage().getCookies()){
                String cp=c.getPath()==null?"/":c.getPath();
                boolean matches=path.equals(cp)||(path.startsWith(cp)&&(cp.endsWith("/")||path.charAt(cp.length())=='/'));
                if(matches&&(!c.isSecure()||"https".equals(uri.getScheme())))cookies.add(c.getName()+"="+c.getValue());
            }
            if(!cookies.isEmpty())request.header("Cookie",String.join("; ",cookies));
        }
        try{
            HttpResponse<String> response=HTTP.send(request.build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200){
                String message="";
                try{
                    Object error=js().executeScript("return JSON.parse(arguments[0]);",response.body());
                    if(error instanceof Map<?,?> fields)message=text(fields.get("message"));
                }catch(JavascriptException ignored){}
                throw new IllegalStateException("GET frontData: HTTP "+response.statusCode()
                        +(message.isBlank()?"":"; "+message)
                        +". Заголовок X-Session-Id взят из sessionStorage браузера Selenium; проверки не пропущены.");
            }
            if(!sessionId.equals(requireSolverSessionId()))throw new IllegalStateException("solverSessionId изменился во время чтения frontData");
            Object parsed=js().executeScript("return JSON.parse(arguments[0]);",response.body());
            if(!(parsed instanceof Map<?,?> root))throw new IllegalStateException("frontData вернул не JSON-объект");return root;
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Чтение frontData прервано",e);}
        catch(java.io.IOException e){throw new IllegalStateException("Не удалось прочитать "+uri,e);}
    }
    private static String text(Object value){return value==null?"":value.toString().trim();}
    private static LocalDateTime apiDate(Map<?,?> job,String field){String value=text(job.get(field));if(value.isBlank())throw new IllegalStateException("Пустое "+field+", job.id="+job.get("id"));return LocalDateTime.parse(value);}
    private void waitOrder(String line,List<JobIdentity> expected){wait.until(d->{if(!hasNoErrorMessages())throw new IllegalStateException("Планировщик показал ошибку операции");try{return expected.equals(readJobOrderNow(line));}catch(StaleElementReferenceException e){return false;}});}
    private void setInput(WebElement input,String value){js().executeScript("const setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;setter.call(arguments[0],arguments[1]);arguments[0].dispatchEvent(new Event('input',{bubbles:true}));arguments[0].dispatchEvent(new Event('change',{bubbles:true}));",input,value);}
    private static void validateRange(int size,int from,int count){if(from<0||count<=0||(long)from+count>size)throw new IllegalArgumentException("Некорректный диапазон: "+from+", "+count+", size="+size);}
    private static String literal(String value){if(!value.contains("'"))return "'"+value+"'";if(!value.contains("\""))return "\""+value+"\"";return "concat('"+value.replace("'","',\"'\",'")+"')";}
    private void confirmActionDialogIfPresent(){try{new WebDriverWait(driver,Duration.ofSeconds(5)).until(ExpectedConditions.elementToBeClickable(By.xpath("//button[normalize-space(.)='Да']"))).click();wait.until(d->d.findElements(By.xpath("//*[contains(text(),'Подтверждение действия')]")).isEmpty());}catch(TimeoutException ignored){}}
    private void dismissResultDialogIfPresent(){try{new WebDriverWait(driver,Duration.ofSeconds(5)).until(ExpectedConditions.elementToBeClickable(By.xpath("//button[normalize-space(.)='ОК' or normalize-space(.)='OK']"))).click();}catch(TimeoutException ignored){}waitUntilOverlayGone();}
    private void waitUntilOverlayGone(){new WebDriverWait(driver,Duration.ofSeconds(30)).withMessage("На странице остаётся видимый overlay; возможно, открыто модальное окно").until(d->{try{return d.findElements(ANY_OVERLAY).stream().noneMatch(WebElement::isDisplayed);}catch(StaleElementReferenceException e){return false;}});}
    private void clickByText(String text){wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(.,"+literal(text)+")]"))).click();}
    public void quit(){driver.quit();}
}