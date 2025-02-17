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

public class ExcelProcessor {

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

            // Create styles for text format
            XSSFCellStyle textStyle = (XSSFCellStyle) outputWorkbook.createCellStyle();
            textStyle.setDataFormat(outputWorkbook.createDataFormat().getFormat("@"));

            // Define correct output headers
            String[] headers = {
                    "SCH_NAME", "ProcedureCode", "Mod1", "Mod2", "Mod3", "Mod4",
                    "Effective Date", "End Date", "Pricing Method", "New Rate", "OldRate", "Comments"
            };

            // Create header row
            Row headerRow = outputSheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(textStyle);
            }

            // Read values from input sheet for SCH_NAME and file naming
            String schNameValue = getCellValueAsString(inputSheet.getRow(0).getCell(1)); // B1 for SCH_NAME
            String fileNamingValue = getCellValueAsString(inputSheet.getRow(4).getCell(1)); // B5 for filename

            Iterator<Row> rowIterator = inputSheet.iterator();
            int outputRowIndex = 1;
            SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");

            // Skip first two rows
            for (int i = 0; i < 2 && rowIterator.hasNext(); i++) {
                rowIterator.next();
            }

            while (rowIterator.hasNext()) {
                Row inputRow = rowIterator.next();
                Row outputRow = outputSheet.createRow(outputRowIndex++);
                
                // SCH_NAME (Apply B1 value from Input sheet)
                createTextCell(outputRow, 0, schNameValue, textStyle);

                // Populate output row based on input columns
                createTextCell(outputRow, 1, getCellValueAsString(inputRow.getCell(0)), textStyle); // ProcedureCode
                createTextCell(outputRow, 2, getCellValueAsString(inputRow.getCell(1)), textStyle); // Mod1
                createTextCell(outputRow, 3, getCellValueAsString(inputRow.getCell(2)), textStyle); // Mod2
                createTextCell(outputRow, 4, getCellValueAsString(inputRow.getCell(3)), textStyle); // Mod3
                createTextCell(outputRow, 5, getCellValueAsString(inputRow.getCell(4)), textStyle); // Mod4
                
                // Effective Date (Transform if < 01/01/2022)
                createTextCell(outputRow, 6, formatEffectiveDate(inputRow.getCell(5)), textStyle);

                // End Date (Modify 12/31/2299 → 12/31/9999)
                createTextCell(outputRow, 7, formatEndDate(inputRow.getCell(6)), textStyle);

                // Pricing Method (Transform "Allowed Amount" to "Allowed")
                createTextCell(outputRow, 8, formatPricingMethod(inputRow.getCell(7)), textStyle);

                // New Rate (Use input value with 4 decimal places)
                createTextCell(outputRow, 9, formatNewRate(inputRow.getCell(8)), textStyle);

                // OldRate (Fixed as "N/A")
                createTextCell(outputRow, 10, "N/A", textStyle);

                // Comments (Fixed as "New Code")
                createTextCell(outputRow, 11, "New Code", textStyle);
            }

            // Generate output file name
            String timestamp = new SimpleDateFormat("ddMMyyyy_HHmmss").format(new Date());
            String outputFileName = OUTPUT_FOLDER + "/" + fileNamingValue + "_ChangeFile_" + timestamp + ".xlsx";

            // Save output file
            try (FileOutputStream fos = new FileOutputStream(outputFileName)) {
                outputWorkbook.write(fos);
            }

            System.out.println("Processed: " + inputFile.getName() + " -> " + outputFileName);

        } catch (Exception e) {
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
