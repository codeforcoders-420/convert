package exceltemplate.convert;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class ChangefileGenerate {
	public static void main(String[] args) {
		final String inputFolder = "C:\\Users\\rajas\\Desktop\\Excelcompare\\input"; // Folder where input file is
																						// placed
		final String outputFilePath = "C:\\Users\\rajas\\Desktop\\Excelcompare\\output"; // Output file

		try {
			File folder = new File(inputFolder);
			File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".xlsx"));

			if (files == null || files.length == 0) {
				System.out.println("No Excel files found in the folder.");
				return;
			}

			File inputFile = files[0]; // Assuming only one input file
			FileInputStream fis = new FileInputStream(inputFile);
			Workbook workbook = new XSSFWorkbook(fis);
			Sheet sheet = workbook.getSheetAt(0);

			int headerRowIndex = findHeaderRow(sheet);
			if (headerRowIndex == -1) {
				System.out.println("Header row not found.");
				return;
			}

			// Read data after the detected header row
			List<List<String>> filteredData = processSheet(sheet, headerRowIndex, "Non Fac Fee", false, false);
			writeFilteredDataToExcel(filteredData, outputFilePath, "MI MD MCAID");

			List<List<String>> filteredData2 = processSheet(sheet, headerRowIndex, "Fac Fee", false, false);
			writeFilteredDataToExcel(filteredData2, outputFilePath, "MI MD MFAC");

			List<List<String>> filteredData3 = processSheet(sheet, headerRowIndex, "Non Fac Fee", true, false);
			writeFilteredDataToExcel(filteredData3, outputFilePath, "MI MD MCAIDMAN");

			List<List<String>> filteredData4 = processSheet(sheet, headerRowIndex, "Non Fac Fee", false, true);
			writeFilteredDataToExcel(filteredData4, outputFilePath, "MI MD MCAIDMANU21");

			System.out.println("Filtered data saved to: " + outputFilePath);
			workbook.close();
			fis.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static int findHeaderRow(Sheet sheet) {
		List<String> expectedHeaders = Arrays.asList("Code", "Short Description", "Modifier", "Age Range",
				"Non Fac Fee", "Fac Fee", "Effective Date**");

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

	private static List<List<String>> processSheet(Sheet sheet, int headerRowIndex, String feeColumnName,
			boolean isManualRates, boolean isUnder21) {
		List<List<String>> result = new ArrayList<>();
		Row headerRow = sheet.getRow(headerRowIndex);

		// Get column indices
		int codeIndex = getColumnIndex(headerRow, "Code");
		int modifierIndex = getColumnIndex(headerRow, "Modifier");
		int ageRangeIndex = getColumnIndex(headerRow, "Age Range");
		int nonFacFeeIndex = getColumnIndex(headerRow, feeColumnName);
		int effectiveDateIndex = getColumnIndex(headerRow, "Effective Date**");

		if (codeIndex == -1 || modifierIndex == -1 || ageRangeIndex == -1 || nonFacFeeIndex == -1
				|| effectiveDateIndex == -1) {
			System.out.println("Some required columns are missing.");
			return result;
		}

		// Add header row for output file
		result.add(Arrays.asList("Code", "Modifier", "Age Range", feeColumnName, "Effective Date"));

		// Process rows
		for (int i = headerRowIndex + 1; i <= sheet.getLastRowNum(); i++) {
			Row row = sheet.getRow(i);
			if (row == null)
				continue;

			String code = getCellValue(row, codeIndex);
			String modifier = getCellValue(row, modifierIndex);
			String ageRange = getCellValue(row, ageRangeIndex);
			String nonFacFee = getCellValue(row, nonFacFeeIndex);
			String effectiveDate = getCellValue(row, effectiveDateIndex);

			if (nonFacFee == null || nonFacFee.trim().isEmpty()) {
				continue; // Skip rows without a Non Fac Fee value
			}

			// Normalize and check Non Fac Fee
			nonFacFee = nonFacFee.replace("$", "").trim();

			if (isManualRates) {
				if (!nonFacFee.equalsIgnoreCase("M")) {
					continue; // Keep only 'M' values
				}
				nonFacFee = "0.01"; // Convert 'M' to $0.01
			}

			if (isUnder21) {
				if (!isAgeUnder21(ageRange)) {
					continue; // Keep only under 21 records
				}
			} else {
				if (nonFacFee.equalsIgnoreCase("M") || nonFacFee.equalsIgnoreCase("NA") || nonFacFee.equals("0.00")
						|| nonFacFee.equals("0")) {
					continue; // Ignore these rows
				}
				if (isAgeUnder21(ageRange)) {
					continue; // Ignore rows where BOTH min & max age are under 21
				}
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
		if (cell == null)
			return "";

		switch (cell.getCellType()) {
		case STRING:
			return cell.getStringCellValue().trim();
		case NUMERIC:
			if (DateUtil.isCellDateFormatted(cell)) {
				return cell.getDateCellValue().toString();
			} else {
				double value = cell.getNumericCellValue();
				return (value % 1 == 0) ? String.valueOf((int) value) : String.valueOf(value);
			}
		case BOOLEAN:
			return String.valueOf(cell.getBooleanCellValue());
		case FORMULA:
			return cell.getCellFormula();
		default:
			return "";
		}
	}

	private static boolean isAgeUnder21(String ageRange) {
		if (ageRange == null || ageRange.isEmpty()) {
			return false; // Treat missing values as under 21 (ignore row)
		}

		// Extract numbers from the string
		List<Integer> numbers = new ArrayList<>();
		String[] words = ageRange.split(" ");

		for (String word : words) {
			// System.out.println("WORD " +word);
			try {
				numbers.add(Integer.parseInt(word));

			} catch (NumberFormatException ignored) {
				// Ignore non-numeric words
			}
		}

		int minAge = numbers.get(0); // First number found
		int maxAge = (numbers.size() > 1) ? numbers.get(1) : minAge; // If only one number, treat it as min and max

		// If BOTH min and max are under 21, ignore the row
		return maxAge <= 21 ? true : false;

	}

	private static void writeFilteredDataToExcel(List<List<String>> data, String outputPath, String fname) {
		try {
			// Ensure output directory exists
			File outputFile = new File(outputPath);
			File parentDir = outputFile.getParentFile();
			if (parentDir != null && !parentDir.exists()) {
				parentDir.mkdirs();
			}

			String timestamp = new SimpleDateFormat("ddMMyyyy_HHmmss").format(new Date());
			String outputFileName = outputPath + "/" + fname + "_" + timestamp + ".xlsx";

			try (Workbook workbook = new XSSFWorkbook(); FileOutputStream fos = new FileOutputStream(outputFileName)) {
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
}
