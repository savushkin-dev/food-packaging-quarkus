package org.acme.foodpackaging.persistence.excel;

import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.exception.excel.ReportGenerationException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils.findLineById;

public class PlanReport {

    private static final String SHEET_NAME = "Statistics";
    private static final String REPORT_PATH = "src/main/target/reports/solution_statistics.xlsx";
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final int MAX_AUTO_SIZE_COLUMN = 9;

    private static final String[] CORRECT_HEADERS = {
            "Название продукта",
            "Номер партии",
            "Старт по плану",
            "План (мин)",
            "Старт по факту",
            "Факт (мин)",
            "Превышение \nдлительности фасовки"
    };

    private static final String[] INCORRECT_HEADERS = {
            "Название продукта",
            "Номер партии",
            "Старт по плану",
            "План (мин)",
            "Старт по факту",
            "Факт (мин)",
            "Превышение \nдлительности фасовки",
            "Линия план",
            "Линия факт"
    };

    private static final String[] SERVICE_HEADERS = {
            "Название",
            "Время начала",
            "Длительность",
            "Время окончания",
            "Заметка мастера"
    };

    public PlanReport(PackagingSchedule solution) {
        createExcelReport(solution);
    }

    private void createExcelReport(PackagingSchedule solution) {

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(200)) {

            SXSSFSheet sheet = workbook.createSheet(SHEET_NAME);
            sheet.trackAllColumnsForAutoSizing();

            Styles styles = createStyles(workbook);

            int rowIndex = 0;

            for (Line line : solution.getLines()) {

                if (line != null && line.getJobs() != null && !line.getJobs().isEmpty()) {

                    FactJobsByLine jobs = collectFactJobs(line.getJobs());

                    if (!jobs.correct().isEmpty() || !jobs.incorrect().isEmpty()) {
                        rowIndex = writeLineSection(sheet, solution, line, jobs, styles, rowIndex);
                    }
                }
            }

            autoSizeColumns(sheet);
            writeWorkbookToFile(workbook);

        } catch (IOException e) {
            throw new ReportGenerationException("Failed to generate Excel report", e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Styles createStyles(Workbook workbook) {

        CellStyle redStyle = workbook.createCellStyle();
        Font redFont = workbook.createFont();
        redFont.setColor(IndexedColors.RED.getIndex());
        redStyle.setFont(redFont);

        CellStyle greenStyle = workbook.createCellStyle();
        Font greenFont = workbook.createFont();
        greenFont.setColor(IndexedColors.GREEN.getIndex());
        greenStyle.setFont(greenFont);

        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFillForegroundColor(IndexedColors.SKY_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        Font headerFont = workbook.createFont();
        headerFont.setBold(true);

        headerStyle.setFont(headerFont);
        headerStyle.setWrapText(true);

        CellStyle incorrectLineStyle = workbook.createCellStyle();
        incorrectLineStyle.setFillForegroundColor(IndexedColors.ROSE.getIndex());
        incorrectLineStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        Font incorrectFont = workbook.createFont();
        incorrectFont.setBold(true);

        incorrectLineStyle.setFont(incorrectFont);

        CellStyle serviceOperationStyle = workbook.createCellStyle();
        serviceOperationStyle.setFillForegroundColor(IndexedColors.AQUA.getIndex());
        serviceOperationStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        Font serviceFont = workbook.createFont();
        serviceFont.setBold(true);

        serviceOperationStyle.setFont(serviceFont);
        serviceOperationStyle.setWrapText(true);

        return new Styles(redStyle, greenStyle, headerStyle, incorrectLineStyle, serviceOperationStyle);
    }


    private int writeLineSection(Sheet sheet,
                                 PackagingSchedule solution,
                                 Line line,
                                 FactJobsByLine jobs,
                                 Styles styles,
                                 int rowIndex) {

        Row lineRow = sheet.createRow(rowIndex++);
        Cell lineCell = lineRow.createCell(0);
        lineCell.setCellValue(line.getName());
        lineCell.setCellStyle(styles.greenStyle());

        sheet.addMergedRegion(new CellRangeAddress(rowIndex - 1, rowIndex - 1, 0, 8));

        Row titleRow = sheet.createRow(rowIndex++);
        titleRow.createCell(0).setCellValue("Фактические задачи на своей линии");

        if (!jobs.correct().isEmpty()) {
            rowIndex = writeCorrectJobsSection(sheet, jobs.correct(), styles.redStyle(), styles.headerStyle(), rowIndex);
        }

        if (!jobs.incorrect().isEmpty()) {
            rowIndex = writeIncorrectJobsSection(sheet, solution, jobs.incorrect(), styles.redStyle(), styles.incorrectLineStyle(), rowIndex);
        }

        rowIndex = writeServiceOperationsSection(sheet, line, styles.serviceOperationStyle(), rowIndex);

        return rowIndex + 2;
    }

    private int writeCorrectJobsSection(Sheet sheet,
                                        List<Job> jobs,
                                        CellStyle delayStyle,
                                        CellStyle headerStyle,
                                        int rowIndex) {

        Row header = sheet.createRow(rowIndex++);
        createHeader(header, CORRECT_HEADERS, headerStyle);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);

        for (Job job : jobs) {

            Row row = sheet.createRow(rowIndex++);

            long fact = job.getDuration().toMinutes();
            long delay = job.getDelayDuration().toMinutes();
            long plan = fact - delay;

            writeRow(row,
                    job.getName(),
                    job.getNp(),
                    format(job.getStartProductionDateTime(), formatter),
                    plan,
                    format(job.getCameraStart(), formatter),
                    fact,
                    delay
            );

            if (delay > 0) {
                row.getCell(6).setCellStyle(delayStyle);
            }
        }

        return rowIndex + 1;
    }

    private int writeIncorrectJobsSection(Sheet sheet,
                                          PackagingSchedule solution,
                                          List<Job> jobs,
                                          CellStyle delayStyle,
                                          CellStyle headerStyle,
                                          int rowIndex) {

        Row title = sheet.createRow(rowIndex++);
        title.createCell(0).setCellValue("Фактические задачи не на своей линии");

        Row header = sheet.createRow(rowIndex++);
        createHeader(header, INCORRECT_HEADERS, headerStyle);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);

        for (Job job : jobs) {

            Row row = sheet.createRow(rowIndex++);

            long fact = getFactDuration(job.getCameraStart(), job.getCameraEnd());
            long plan = job.getDuration().toMinutes();
            long delay = Math.max(fact - plan, 0);

            String factLine = findLineById(solution, job.getLineIdFact()).getName();

            writeRow(row,
                    job.getName(),
                    job.getNp(),
                    format(job.getStartProductionDateTime(), formatter),
                    plan,
                    format(job.getCameraStart(), formatter),
                    fact,
                    delay,
                    job.getLine().getName(),
                    factLine
            );

            if (delay > 0) {
                row.getCell(6).setCellStyle(delayStyle);
            }
        }

        return rowIndex + 1;
    }


    private int writeServiceOperationsSection(Sheet sheet,
                                              Line line,
                                              CellStyle style,
                                              int rowIndex) {

        Row title = sheet.createRow(rowIndex++);
        title.createCell(0).setCellValue("Сервисные операции на линии");

        Row header = sheet.createRow(rowIndex++);
        createHeader(header, SERVICE_HEADERS, style);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);

        for (Job job : line.getJobs()) {

            if (!job.isMaintenance()) {
                continue;
            }

            Row row = sheet.createRow(rowIndex++);

            writeRow(row,
                    job.getName(),
                    format(job.getStartProductionDateTime(), formatter),
                    job.getDuration().toMinutes(),
                    format(job.getEndDateTime(), formatter),
                    job.getMaintenanceNote()
            );
        }

        return rowIndex;
    }

    private void createHeader(Row row, String[] headers, CellStyle style) {
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
    }

    private void writeRow(Row row, Object... values) {
        for (int i = 0; i < values.length; i++) {

            Cell cell = row.createCell(i);
            Object value = values[i];

            if (value instanceof Number n) {
                cell.setCellValue(n.doubleValue());
            } else if (value != null) {
                cell.setCellValue(value.toString());
            }
        }
    }

    private void autoSizeColumns(Sheet sheet) {

        for (int i = 0; i < MAX_AUTO_SIZE_COLUMN; i++) {

            sheet.autoSizeColumn(i);

            int maxWidth = 70 * 256;

            if (sheet.getColumnWidth(i) > maxWidth) {
                sheet.setColumnWidth(i, maxWidth);
            }
        }
    }

    private void writeWorkbookToFile(Workbook workbook) throws Exception {

        try (FileOutputStream out = new FileOutputStream(REPORT_PATH)) {
            workbook.write(out);
        }
    }

    private String format(LocalDateTime time, DateTimeFormatter formatter) {
        return time == null ? "" : time.format(formatter);
    }

    private FactJobsByLine collectFactJobs(List<Job> jobs) {

        List<Job> correct = new ArrayList<>();
        List<Job> incorrect = new ArrayList<>();

        for (Job job : jobs) {

            if (job.getDelayDuration() != null
                    && isYesterdayOrToday(job.getCameraStart())
                    && job.getLine().getId().equals(job.getLineIdFact())) {

                correct.add(job);
            }

            if (job.getCameraStart() != null
                    && job.getCameraEnd() != null
                    && job.getLine() != null
                    && job.getLineId() != null
                    && isYesterdayOrToday(job.getCameraStart())
                    && !job.getLine().getId().equals(job.getLineIdFact())) {

                incorrect.add(job);
            }
        }

        return new FactJobsByLine(correct, incorrect);
    }

    private long getFactDuration(LocalDateTime start, LocalDateTime end) {
        return Duration.between(start, end).toMinutes();
    }

    private boolean isYesterdayOrToday(LocalDateTime time) {

        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        return !time.toLocalDate().isBefore(yesterday)
                && !time.toLocalDate().isAfter(today);
    }

    private record FactJobsByLine(
            List<Job> correct,
            List<Job> incorrect
    ) {}

    private record Styles(
            CellStyle redStyle,
            CellStyle greenStyle,
            CellStyle headerStyle,
            CellStyle incorrectLineStyle,
            CellStyle serviceOperationStyle
    ) {}
}

