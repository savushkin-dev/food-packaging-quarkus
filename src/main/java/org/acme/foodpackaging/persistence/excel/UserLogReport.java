package org.acme.foodpackaging.persistence.excel;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.inject.spi.CDI;
import org.acme.foodpackaging.entity.RequestLog;
import org.acme.foodpackaging.repository.RequestLogRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class UserLogReport {

    private static final String SHEET_NAME = "User Logs";
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final int MAX_AUTO_SIZE_COLUMN = 4;

    private static final String[] HEADERS = {
            "DT",
            "IP",
            "METHOD",
            "QUERY"
    };

    private final ObjectMapper objectMapper = new ObjectMapper();

    public UserLogReport(LocalDate from, LocalDate to) {
        createExcelReport(from, to);
    }

    private void createExcelReport(LocalDate from, LocalDate to) {

        RequestLogRepository repository = CDI.current().select(RequestLogRepository.class).get();

        List<RequestLog> logs = repository.find(
                "(method = ?1 or method = ?2) and dateTime >= ?3 and dateTime <= ?4 order by dateTime",
                "stopSolving",
                "save",
                from.atStartOfDay(),
                to.plusDays(1).atStartOfDay()
        ).list();

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(200)) {

            SXSSFSheet sheet = workbook.createSheet(SHEET_NAME);
            sheet.trackAllColumnsForAutoSizing();

            CellStyle headerStyle = createHeaderStyle(workbook);

            int rowIndex = 0;

            Row header = sheet.createRow(rowIndex++);
            createHeader(header, headerStyle);

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);

            for (RequestLog log : logs) {

                if(!isValidLog(log)) continue;

                Row row = sheet.createRow(rowIndex++);

                writeRow(row,
                        format(log.getDateTime(), formatter),
                        trim(log.getIp()),
                        trim(log.getMethod()),
                        parseQuery(log.getQuery())
                );
            }

            autoSizeColumns(sheet);
            writeWorkbookToFile(workbook, generateReportPath(from, to));

        } catch (IOException e) {
            throw new RuntimeException("Error while generating UserLog report", e);
        }
    }
    private CellStyle createHeaderStyle(Workbook workbook) {

        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.SKY_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        Font font = workbook.createFont();
        font.setBold(true);

        style.setFont(font);
        style.setWrapText(true);

        return style;
    }

    private void createHeader(Row row, CellStyle style) {
        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(HEADERS[i]);
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

    private String parseQuery(String query) {

        if (query == null || query.isBlank()) {
            return "";
        }

        try {
            Object json = objectMapper.readValue(query.trim(), Object.class);
            return objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(json);
        } catch (Exception e) {
            return query.trim();
        }
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String format(LocalDateTime time, DateTimeFormatter formatter) {
        return time == null ? "" : time.format(formatter);
    }

    private void autoSizeColumns(Sheet sheet) {

        int maxWidth = 255 * 256;

        for (int i = 0; i < MAX_AUTO_SIZE_COLUMN; i++) {

            if (i == 3) {
                sheet.setColumnWidth(i, 100 * 256); // QUERY колонка
                continue;
            }

            sheet.autoSizeColumn(i);

            int width = sheet.getColumnWidth(i) + 1024;
            sheet.setColumnWidth(i, Math.min(width, maxWidth));
        }
    }

    private void writeWorkbookToFile(Workbook workbook, String path) throws IOException {

        try (FileOutputStream out = new FileOutputStream(path)) {
            workbook.write(out);
        }
    }

    private boolean isValidLog(RequestLog log) {

        String method = trim(log.getMethod());

        if ("save".equalsIgnoreCase(method) || "stopSolving".equalsIgnoreCase(method)) {

            String query = log.getQuery();

            if (query == null || query.isBlank()) {
                return false;
            }

            query = query.trim();

            return !query.equals("{}");
        }

        return false;
    }

    private String generateReportPath(LocalDate from, LocalDate to) {

        String dir = "reports/";

        try {
            Files.createDirectories(Paths.get(dir));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create directory", e);
        }

        return dir + from + "—" + to + "_UserLogReport.xlsx";
    }
}
