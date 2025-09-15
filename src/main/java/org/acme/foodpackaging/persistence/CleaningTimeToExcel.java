package org.acme.foodpackaging.persistence;

import org.acme.foodpackaging.domain.Product;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;

public class CleaningTimeToExcel {

    public CleaningTimeToExcel(List<Product> productList) throws IOException {
        createCleaningTimeExcel(productList);
    }

    private void createCleaningTimeExcel(List<Product> products) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("CleaningTimes");
        Font headerFont = workbook.createFont();
        headerFont.setFontName("Calibri"); // или Arial, как в остальной таблице
        headerFont.setFontHeightInPoints((short) 11);
        headerFont.setBold(true);

        CellStyle normalHeaderStyle = workbook.createCellStyle();
        normalHeaderStyle.setFont(headerFont);
        normalHeaderStyle.setAlignment(HorizontalAlignment.CENTER);
        normalHeaderStyle.setVerticalAlignment(VerticalAlignment.CENTER);

        CellStyle rotatedStyle = workbook.createCellStyle();
        rotatedStyle.setFont(headerFont);
        rotatedStyle.setRotation((short) 90);
        rotatedStyle.setAlignment(HorizontalAlignment.CENTER);
        rotatedStyle.setVerticalAlignment(VerticalAlignment.BOTTOM);

        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Переход с");

        int colIndex = 1;
        for (Product product : products) {
            Cell cell = headerRow.createCell(colIndex);
            cell.setCellValue(product.getName());
            cell.setCellStyle(rotatedStyle);
            colIndex++;
        }

        headerRow.setHeightInPoints(250);

        int rowIndex = 1;
        for (Product fromProduct : products) {
            Row row = sheet.createRow(rowIndex);
            row.createCell(0).setCellValue(fromProduct.getName());

            colIndex = 1;
            for (Product toProduct : products) {
                Duration duration = fromProduct.getCleaningDurations().get(toProduct);
                if (duration != null) {
                    row.createCell(colIndex).setCellValue(duration.toMinutes());
                } else {
                    row.createCell(colIndex).setCellValue("-");
                }
                colIndex++;
            }
            rowIndex++;
        }

         sheet.setColumnWidth(0, 60*256);
        for (int i = 1; i <= products.size(); i++) {
            sheet.setColumnWidth(i, 7 * 270);
        }

        File exportDir = new File("src/main/resources/excelExport");
        if (!exportDir.exists() && !exportDir.mkdirs()) {
            throw new IOException("Не удалось создать каталог: " + exportDir.getAbsolutePath());
        }
        File exportFile = new File(exportDir, "cleaning_times.xlsx");

        try (FileOutputStream fos = new FileOutputStream(exportFile)) {
            workbook.write(fos);
        }
        workbook.close();

        System.out.println("Excel успешно создан: " + exportFile.getAbsolutePath());
     }
    }
