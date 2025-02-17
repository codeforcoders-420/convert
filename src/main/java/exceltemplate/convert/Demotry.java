package exceltemplate.convert;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.file.*;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Demotry {

    private static final String INPUT_FOLDER = "C:\\Users\\rajas\\Desktop\\Excelcompare\\Input"; // Change path as needed
    private static final String OUTPUT_FOLDER = "C:\\Users\\rajas\\Desktop\\Excelcompare\\Output";

    public static void main(String[] args) {
        File folder = new File(INPUT_FOLDER);
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".xlsx"));

        if (files == null || files.length == 0) {
            System.out.println("No Excel files found in the directory.");
            return;
        }

        for (File file : files) {
            processExcelFile(file);
        }
        System.out.println("Processing complete!");
    }

    private static void processExcelFile(File inputFile) {
        try (FileInputStream fis = new FileInputStream(inputFile);
             Workbook inputWorkbook = new XSSFWorkbook(fis);
             Workbook outputWorkbook = new XSSFWorkbook()) {

            Sheet inputSheet = inputWorkbook.getSheetAt(0);
            Sheet outputSheet = outputWorkbook.createSheet("Sheet1");
            
            int headerRowIndex = findHeaderRow(inputSheet);
            if (headerRowIndex == -1) {
                System.out.println("Header row not found.");
                return;
            }

            // Read data after the detected header row
            List<List<String>> filteredData = processSheet(inputSheet, headerRowIndex);
            writeFilteredDataToExcel(filteredData, OUTPUT_FOLDER);

            System.out.println("Filtered data saved to: " + OUTPUT_FOLDER);
            inputWorkbook.close();
            fis.close();



        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static int findHeaderRow(Sheet sheet) {
        List<String> expectedHeaders = Arrays.asList("Code", "Short Description", "Modifier", "Age Range", "Non Fac Fee", "Fac Fee", "Effective Date**");

        for (Row row : sheet) {
            List<String> rowValues = new ArrayList<>();
            for (Cell cell : row) {
                rowValues.add(cell.toString().trim());
            }

            if (rowValues.containsAll(expectedHeaders)) {
                return row.getRowNum();
            }
        }
        return -1; // Header row not found
    }
    
    private static List<List<String>> processSheet(Sheet sheet, int headerRowIndex) {
        List<List<String>> result = new ArrayList<>();
        Row headerRow = sheet.getRow(headerRowIndex);

        // Get column indices
        int codeIndex = getColumnIndex(headerRow, "Code");
        int modifierIndex = getColumnIndex(headerRow, "Modifier");
        int ageRangeIndex = getColumnIndex(headerRow, "Age Range");
        int nonFacFeeIndex = getColumnIndex(headerRow, "Non Fac Fee");
        int effectiveDateIndex = getColumnIndex(headerRow, "Effective Date**");

        if (codeIndex == -1 || modifierIndex == -1 || ageRangeIndex == -1 || nonFacFeeIndex == -1 || effectiveDateIndex == -1) {
            System.out.println("Some required columns are missing.");
            return result;
        }

        // Add header row for output file
        result.add(Arrays.asList("Code", "Modifier", "Age Range", "Non Fac Fee", "Effective Date"));

        // Process rows
        for (int i = headerRowIndex + 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            String code = getCellValue(row, codeIndex);
            String modifier = getCellValue(row, modifierIndex);
            String ageRange = getCellValue(row, ageRangeIndex);
            String nonFacFee = getCellValue(row, nonFacFeeIndex);
            String effectiveDate = getCellValue(row, effectiveDateIndex);

            // Apply filtering rules
            if (nonFacFee.equalsIgnoreCase("M") || nonFacFee.equalsIgnoreCase("NA") || nonFacFee.equals("$0.00")) {
                continue;
            }

            try {
                int age = Integer.parseInt(ageRange.replaceAll("[^0-9]", ""));
                if (age < 21) continue;
            } catch (NumberFormatException e) {
                continue; // Skip row if Age Range is invalid
            }

            // Add filtered row
            result.add(Arrays.asList(code, modifier, ageRange, nonFacFee, effectiveDate));
        }
        return result;
    }
    
    private static int getColumnIndex(Row headerRow, String columnName) {
        for (Cell cell : headerRow) {
            if (cell.toString().trim().equalsIgnoreCase(columnName)) {
                return cell.getColumnIndex();
            }
        }
        return -1;
    }
    
    private static String getCellValue(Row row, int columnIndex) {
        Cell cell = row.getCell(columnIndex);
        return (cell == null) ? "" : cell.toString().trim();
    }
    
    private static void writeFilteredDataToExcel(List<List<String>> data, String outputPath) {
        try {
            // Ensure output directory exists
            File outputFile = new File(outputPath);
            File parentDir = outputFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs(); // Create the directory if it doesn't exist
            }

            try (Workbook workbook = new XSSFWorkbook(); FileOutputStream fos = new FileOutputStream(outputFile)) {
                Sheet sheet = workbook.createSheet("Filtered Data");

                for (int i = 0; i < data.size(); i++) {
                    Row row = sheet.createRow(i);
                    List<String> rowData = data.get(i);
                    for (int j = 0; j < rowData.size(); j++) {
                        row.createCell(j).setCellValue(rowData.get(j));
                    }
                }

                workbook.write(fos);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void createTextCell(Row row, int column, String value, CellStyle textStyle) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(textStyle);
    }

    private static String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        cell.setCellType(CellType.STRING);
        return cell.getStringCellValue().trim();
    }

    private static String formatEffectiveDate(Cell cell) {
        Date date = getCellDateValue(cell);
        if (date == null) return "01/01/2022";

        Date threshold = new Date(122, 0, 1); // 01/01/2022
        if (date.before(threshold)) {
            return "01/01/2022";
        }
        return new SimpleDateFormat("MM/dd/yyyy").format(date);
    }

    private static String formatEndDate(Cell cell) {
        String dateStr = formatDate(cell);
        return dateStr.equals("12/31/2299") ? "12/31/9999" : dateStr;
    }

    private static String formatDate(Cell cell) {
        Date date = getCellDateValue(cell);
        if (date == null) return "";
        return new SimpleDateFormat("MM/dd/yyyy").format(date);
    }

    private static String formatPricingMethod(Cell cell) {
        String value = getCellValueAsString(cell);
        return value.equalsIgnoreCase("Allowed Amount") ? "Allowed" : value;
    }

    private static String formatNewRate(Cell cell) {
        if (cell == null) return "$0.0000";
        try {
            double rate = cell.getNumericCellValue();
            return "$" + new DecimalFormat("0.0000").format(rate);
        } catch (Exception e) {
            return "$0.0000"; // Default if parsing fails
        }
    }

    private static Date getCellDateValue(Cell cell) {
        if (cell == null) return null;
        try {
            return cell.getDateCellValue();
        } catch (Exception e) {
            return null;
        }
    }
}
