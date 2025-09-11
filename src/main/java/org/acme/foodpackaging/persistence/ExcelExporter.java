package org.acme.foodpackaging.persistence;

import org.acme.foodpackaging.domain.Job;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.acme.foodpackaging.sql.SqlQueries.LOAD_LABELING_CACTUS_COCONUT_ALMONDS;

public class ExcelExporter {

    private File exportedFile;
    private final int cellCount = 8;
    public ExcelExporter(String dbLabelingUrl, String date, List<Job> jobs) {
        importDataFromDB(dbLabelingUrl, date, jobs);
    }

    public File getExportedFile() {
        return exportedFile;
    }

    private String  formatTime(Duration duration){
        long totalMinutes = duration.toMinutes();
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        return String.format("%02d:%02d", hours, minutes);
    }

    private void importDataFromDB(String dbLabelingUrl, String date, List<Job> jobs) {
        try (Connection connection = DriverManager.getConnection(dbLabelingUrl);
             PreparedStatement ps = connection.prepareStatement(LOAD_LABELING_CACTUS_COCONUT_ALMONDS);
             Workbook workbook = new XSSFWorkbook()) {

            ps.setString(1, "0307060162%");
            ps.setString(2, "0307060046%");
            ps.setDate(3, java.sql.Date.valueOf(date));

            Sheet sheet = workbook.createSheet("Data");
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("NP");
            headerRow.createCell(1).setCellValue("SNM");
            headerRow.createCell(2).setCellValue("KMC");
            headerRow.createCell(3).setCellValue("Количество (факт)");
            headerRow.createCell(4).setCellValue("Количество (планировщик)");
            headerRow.createCell(5).setCellValue("Время старта выполнения (факт)");
            headerRow.createCell(6).setCellValue("Время завершения фасовки (факт)");
            headerRow.createCell(7).setCellValue("Продолжительность фасовки (факт)");
            headerRow.createCell(8).setCellValue("Продолжительность фасовки (планировщик)");

            try (ResultSet rs = ps.executeQuery()) {
                int rowIdx = 1;
                while (rs.next()) {
                    String kmc = rs.getString("KMC");
                    String np = rs.getString("NP");
                    String nkole = rs.getString("NKOLE");
                    LocalDateTime dts = rs.getTimestamp("DTS").toLocalDateTime();
                    LocalDateTime dte = rs.getTimestamp("DTE").toLocalDateTime();

                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

                    Duration jobDuration = Duration.between(dts, dte);

                    Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(np);
                    row.createCell(2).setCellValue(kmc);
                    row.createCell(3).setCellValue(nkole);
                    row.createCell(5).setCellValue(dts.format(formatter));
                    row.createCell(6).setCellValue(dte.format(formatter));
                    row.createCell(7).setCellValue(formatTime(jobDuration));
                    for(Job job : jobs){
                        if(job.getProduct().getKmc().equals(kmc)){
                            row.createCell(1).setCellValue(job.getName());
                            row.createCell(4).setCellValue(job.getQuantity());
                            row.createCell(8).setCellValue(formatTime(job.getDuration()));
                        }
                    }
                }
                for (int i = 0; i < cellCount; i++) {
                    sheet.autoSizeColumn(i);
                }

                File exportDir = new File("src/main/resources/excelExport");
                if (!exportDir.exists()) {
                    boolean created = exportDir.mkdirs();
                    if (!created) {
                        System.err.println("Не удалось создать каталог: " + exportDir.getAbsolutePath());
                        return;
                    }
                }
                exportedFile = new File(exportDir, date + ".xlsx");

                try (FileOutputStream fos = new FileOutputStream("src/main/resources/excelExport/" + date + ".xlsx")) {
                    workbook.write(fos);
                    System.out.println("✅ Данные успешно экспортированы в Excel:" + "src/main/resources/excelExport/" + date + ".xlsx");
                }
            }

        }
        catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }
}
