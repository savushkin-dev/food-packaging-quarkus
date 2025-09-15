package org.acme.foodpackaging.persistence;

import org.acme.foodpackaging.domain.Product;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class CleaningTimeToExcel {

    public CleaningTimeToExcel(List<Product> productList) throws IOException {
        createCleaningTimeExcel(productList);
    }

    private void createCleaningTimeExcel(List<Product> products) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Products");

        int rowIndex = 0;

        Row header = sheet.createRow(rowIndex++);
        header.createCell(0).setCellValue("From");
        header.createCell(1).setCellValue("To");
        header.createCell(2).setCellValue("Time");

        for (Product product : products) {
            for (Map.Entry<Product, Duration> entry : product.getCleaningDurations().entrySet()) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(entry.getKey().getName());
                row.createCell(1).setCellValue(product.getName());
                row.createCell(2).setCellValue(entry.getValue().toMinutes());
            }

            rowIndex++;
        }

        for (int i = 0; i < 3; i++) {
            sheet.autoSizeColumn(i);
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
