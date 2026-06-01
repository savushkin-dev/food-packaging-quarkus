package org.acme.foodpackaging.persistence.excel;

import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.exception.excel.ReportGenerationException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import java.io.ByteArrayOutputStream;

import java.io.IOException;
import java.time.Duration;
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

    public byte[] createExcelReport(
            PackagingSchedule solution,
            LocalDate from,
            LocalDate to) {

        try (
                SXSSFWorkbook workbook = new SXSSFWorkbook(200);
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            SXSSFSheet sheet = workbook.createSheet(SHEET_NAME);
            sheet.trackAllColumnsForAutoSizing();

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle lineStyle = createLineStyle(workbook);

            int rowIndex = 0;

            for (Line line : solution.getLines()) {

                if (line == null || line.getJobs() == null) {
                    continue;
                }

                List<Job> cleaningJobs =
                        collectCleaningJobs(line.getJobs(), from, to);

                if (!cleaningJobs.isEmpty()) {

                    rowIndex = writeLineSection(
                            sheet,
                            line,
                            cleaningJobs,
                            headerStyle,
                            lineStyle,
                            rowIndex
                    );
                }
            }
            autoSizeColumns(sheet);
            workbook.write(out);

            return out.toByteArray();

        } catch (IOException e) {
            throw new ReportGenerationException(
                    "Error while generating cleaning report",
                    e
            );
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

    private int writeLineSection(
            Sheet sheet,
            Line line,
            List<Job> jobs,
            CellStyle headerStyle,
            CellStyle lineStyle,
            int rowIndex) {

        Row lineRow = sheet.createRow(rowIndex++);

        Cell lineCell = lineRow.createCell(0);
        lineCell.setCellValue(line.getName());
        lineCell.setCellStyle(lineStyle);

        sheet.addMergedRegion(
                new CellRangeAddress(
                        rowIndex - 1,
                        rowIndex - 1,
                        0,
                        3));

        Row header = sheet.createRow(rowIndex++);
        createHeader(header, headerStyle);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);

        for (Job job : jobs) {

            Row row = sheet.createRow(rowIndex++);

            String name;
            LocalDateTime start;
            LocalDateTime end;
            long duration;

            if (job.isMaintenance()) {

                name = "Мойка, сервисная операция";

                start = job.getStartProductionDateTime();
                end = job.getEndDateTime();

            } else {

                name = "Мойка, переналадка";

                start = job.getStartCleaningDateTime();
                end = job.getStartProductionDateTime();
            }

            duration = Duration.between(start, end).toMinutes();

            writeRow(
                    row,
                    name,
                    format(start, formatter),
                    duration,
                    format(end, formatter));
        }

        return rowIndex + 2;
    }

    private List<Job> collectCleaningJobs(
            List<Job> jobs,
            LocalDate from,
            LocalDate to) {

        List<Job> result = new ArrayList<>();

        for (Job job : jobs) {

            if (job.getStartCleaningDateTime() == null ||
                    job.getStartProductionDateTime() == null) {
                continue;
            }

            boolean isProductionAfterCleaning = job.getStartProductionDateTime()
                    .isAfter(job.getStartCleaningDateTime());

            boolean isMaintenanceMatch = job.isMaintenance()
                    && job.getMaintenanceTypeId() != null
                    && job.getMaintenanceTypeId() == 2;

            if (!isMaintenanceMatch && !isProductionAfterCleaning) {
                continue;
            }

            LocalDate dateForFilter = job.isMaintenance()
                    ? job.getStartProductionDateTime().toLocalDate()
                    : job.getStartCleaningDateTime().toLocalDate();

            if (dateForFilter.isBefore(from)
                    || dateForFilter.isAfter(to)) {
                continue;
            }

            result.add(job);
        }

        return result;
    }

    private void createHeader(Row row, CellStyle style) {

        for (int i = 0; i < SERVICE_HEADERS.length; i++) {

            Cell cell = row.createCell(i);

            cell.setCellValue(SERVICE_HEADERS[i]);
            cell.setCellStyle(style);
        }
    }

    private void writeRow(Row row, Object... values) {

        for (int i = 0; i < values.length; i++) {
            Cell cell = row.createCell(i);
            Object value = values[i];

            if (value instanceof Number number) {

                cell.setCellValue(number.doubleValue());

            } else if (value != null) {

                cell.setCellValue(value.toString());
            }
        }
    }

    private String format(
            LocalDateTime time,
            DateTimeFormatter formatter) {
        return time == null
                ? ""
                : time.format(formatter);
    }

    private void autoSizeColumns(Sheet sheet) {

        int maxWidth = 255 * 256;

        for (int i = 0; i < MAX_AUTO_SIZE_COLUMN; i++) {

            sheet.autoSizeColumn(i);
            int width = sheet.getColumnWidth(i) + 1024;
            sheet.setColumnWidth(
                    i,
                    Math.min(width, maxWidth));
        }
    }
}