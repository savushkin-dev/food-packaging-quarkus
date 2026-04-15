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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class CleaningDurationReport {

    private static final String SHEET_NAME = "Cleaning";
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final int MAX_AUTO_SIZE_COLUMN = 4;

    private static final String[] SERVICE_HEADERS = {
            "Название",
            "Время начала мойки",
            "Длительность (мин)",
            "Время окончания мойки"
    };

    private final LocalDate from;
    private final LocalDate to;

    public CleaningDurationReport(PackagingSchedule solution,
                                  LocalDate from,
                                  LocalDate to) {
        this.from = from;
        this.to = to;
        createExcelReport(solution);
    }

    private void createExcelReport(PackagingSchedule solution) {

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(200)) {

            SXSSFSheet sheet = workbook.createSheet(SHEET_NAME);
            sheet.trackAllColumnsForAutoSizing();

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle lineStyle = createLineStyle(workbook);

            int rowIndex = 0;

            for (Line line : solution.getLines()) {

                if (line == null || line.getJobs() == null) continue;

                List<Job> cleaningJobs = collectCleaningJobs(line.getJobs());

                if (cleaningJobs.isEmpty()) continue;

                rowIndex = writeLineSection(
                        sheet,
                        line,
                        cleaningJobs,
                        headerStyle,
                        lineStyle,
                        rowIndex
                );
            }

            autoSizeColumns(sheet);
            writeWorkbookToFile(workbook, generateReportPath());

        } catch (IOException e) {
            throw new ReportGenerationException("Error while generating cleaning report", e);
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {

        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.AQUA.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        Font font = workbook.createFont();
        font.setBold(true);

        style.setFont(font);
        style.setWrapText(true);

        return style;
    }

    private CellStyle createLineStyle(Workbook workbook) {

        CellStyle style = workbook.createCellStyle();

        Font font = workbook.createFont();
        font.setBold(true);

        style.setFont(font);

        return style;
    }

    private int writeLineSection(Sheet sheet,
                                 Line line,
                                 List<Job> jobs,
                                 CellStyle headerStyle,
                                 CellStyle lineStyle,
                                 int rowIndex) {

        Row lineRow = sheet.createRow(rowIndex++);
        Cell lineCell = lineRow.createCell(0);
        lineCell.setCellValue(line.getName());
        lineCell.setCellStyle(lineStyle);

        sheet.addMergedRegion(new CellRangeAddress(rowIndex - 1, rowIndex - 1, 0, 3));

        Row header = sheet.createRow(rowIndex++);
        createHeader(header, headerStyle);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);

        for (Job job : jobs) {

            Row row = sheet.createRow(rowIndex++);

            long duration = job.getDuration().toMinutes();

            writeRow(row,
                    job.getName(),
                    format(job.getStartCleaningDateTime(), formatter),
                    duration,
                    format(job.getEndDateTime(), formatter)
            );
        }

        return rowIndex + 2;
    }

    private List<Job> collectCleaningJobs(List<Job> jobs) {

        List<Job> result = new ArrayList<>();

        for (Job job : jobs) {


            if (job.getStartCleaningDateTime() == null ||
                    job.getStartProductionDateTime() == null) {
                continue;
            }

            if (!job.getStartProductionDateTime().isAfter(job.getStartCleaningDateTime())) {
                continue;
            }

            LocalDate cleaningDate = job.getStartCleaningDateTime().toLocalDate();

            if (!cleaningDate.isBefore(from) && !cleaningDate.isAfter(to)) {
                result.add(job);
            }
        }

        return result;
    }

    private void createHeader(Row row, CellStyle style) {
        for (int i = 0; i < CleaningDurationReport.SERVICE_HEADERS.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(CleaningDurationReport.SERVICE_HEADERS[i]);
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

    private String format(LocalDateTime time, DateTimeFormatter formatter) {
        return time == null ? "" : time.format(formatter);
    }

    private void autoSizeColumns(Sheet sheet) {

        for (int i = 0; i < MAX_AUTO_SIZE_COLUMN; i++) {

            sheet.autoSizeColumn(i);

            int maxWidth = 50 * 256;

            if (sheet.getColumnWidth(i) > maxWidth) {
                sheet.setColumnWidth(i, maxWidth);
            }
        }
    }

    private void writeWorkbookToFile(Workbook workbook, String path) throws IOException {

        try (FileOutputStream out = new FileOutputStream(path)) {
            workbook.write(out);
        }
    }

    private String generateReportPath() {

        String dir = "reports/";

        try {
            Files.createDirectories(Paths.get(dir));
        } catch (IOException e) {
            throw new ReportGenerationException("Failed to create directory", e);
        }

        String date = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        return dir + date + "_CleaningReport.xlsx";
    }
}
