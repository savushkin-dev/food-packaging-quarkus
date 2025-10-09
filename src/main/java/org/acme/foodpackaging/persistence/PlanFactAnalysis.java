package org.acme.foodpackaging.persistence;

import org.acme.foodpackaging.domain.Job;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.acme.foodpackaging.sql.SqlQueries.LOAD_LINE_WORK_FACT;

public class PlanFactAnalysis {

    public record Key(String productId, String np) {}

    public static class FactData {
        LocalDateTime startDate;
        LocalDateTime endDate;
        String lineId;
        String productId;
        String np;
        Duration duration;

        public void updateEnd(LocalDateTime newEnd) {
            if (endDate == null || newEnd.isAfter(endDate)) {
                this.endDate = newEnd;
                this.duration = Duration.ofMinutes(Duration.between(startDate, endDate).toMinutes());
            }
        }
    }
    private File exportFile;
    private Map<Key, FactData> factedMap;
    private final int cellCount = 9;
    private final String date;
    private final String jdbcUrl;
    private final String user;
    private final String password;

        public PlanFactAnalysis(String date) {
            Config config = ConfigProvider.getConfig();
            jdbcUrl = config.getValue("plan.db.url", String.class);
            user = config.getValue("plan.db.user", String.class);
            password = config.getValue("plan.db.password", String.class);
            this.date = date;
            this.factedMap = readDataFromDB();
        }

        public Map<Key, FactData> getFactedMap() {
            if (factedMap == null) {
                factedMap = readDataFromDB();
            }
            return factedMap;
        }

    public File getExportFile() {
        return exportFile;
    }

    private Map<Key, FactData> readDataFromDB() {

        Map<Key, FactData> result = new HashMap<>();

        try (Connection connection = DriverManager.getConnection(jdbcUrl, user, password);
             PreparedStatement ps = connection.prepareStatement(LOAD_LINE_WORK_FACT)) {

            LocalDateTime start = LocalDateTime.parse(date + "T00:00:00");
            LocalDateTime end = LocalDateTime.parse(date + "T23:59:59");

            ps.setObject(1, start);
            ps.setObject(2, end);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LocalDateTime datetime = rs.getTimestamp("datetime1").toLocalDateTime();
                    String lineId = rs.getString("wc");
                    String product_id = rs.getString("producttype");
                    String np = rs.getString("batch");


                    Key key = new Key(product_id, np);
                    result.compute(key, (k, agg) -> {
                        if (agg == null) {
                            FactData newFact = new FactData();
                            newFact.startDate = datetime; newFact.endDate = datetime;
                                newFact.lineId = lineId; newFact.productId = product_id;
                                newFact.np = np; newFact.duration = Duration.ofMinutes(1);
                                return newFact;
                            } else {
                                agg.updateEnd(datetime);
                                return agg;
                            }
                        });
                    }
                }

            } catch (Exception e) {
                System.err.println("Failed load data for plan analyzing: " + e.getMessage());
            }

            return result;
        }

    public void excelWrite(List<Job> jobs) {
        String templateFile = "src/main/resources/PlanFactTemplate/PlanFactTemplate.xlsx";
        String outputDirPath = "src/main/resources/PlanFactExport";
        String outputFileName = date + "PlanFact.xlsx";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        try (FileInputStream fis = new FileInputStream(templateFile);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            int rowIndex = 2;

            for (Job job : jobs) {
                Key key = new Key(job.getProduct().getKrKmc(), job.getNp());
                FactData fact = factedMap.get(key);

                if (fact == null) {
                    continue;
                }

                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    row = sheet.createRow(rowIndex);
                }

                int col = 0;

                // Timefold data
                row.createCell(col++).setCellValue(job.getProduct().getKrKmc());
                row.createCell(col++).setCellValue(job.getProduct().getName());
                row.createCell(col++).setCellValue(job.getNp());
                row.createCell(col++).setCellValue(job.getLine().getName());
                row.createCell(col++).setCellValue(job.getStartProductionDateTime().format(formatter));
                row.createCell(col++).setCellValue(job.getDuration().toMinutes());

                // Fact fata from db
                row.createCell(col++).setCellValue(fact.lineId);
                row.createCell(col++).setCellValue(fact.startDate.format(formatter));
                row.createCell(col).setCellValue(fact.duration.toMinutes());

                rowIndex++;
            }

            File exportDir = new File(outputDirPath);
            if (!exportDir.exists() && !exportDir.mkdirs()) {
                System.err.println("Directory " + exportDir.getAbsolutePath() + "was not created.");
                return;
            }

            exportFile = new File(exportDir, outputFileName);
            try (FileOutputStream fos = new FileOutputStream(exportFile)) {
                workbook.write(fos);
            }

            System.out.println("PlanFact data is exported: " + exportFile.getAbsolutePath());

        } catch (IOException e) {
            System.err.println("Failed to export fact packaging data: " + e.getMessage());
        }
    }

}

