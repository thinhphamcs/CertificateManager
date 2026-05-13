package info.thinhpham.certificatemanager.service;

import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class ExcelParserService {

    public List<String> parseUrls(MultipartFile file) throws IOException {
        List<String> urls = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                Cell cell = row.getCell(0);
                if (cell == null) continue;
                String value = extractString(cell).trim();
                if (!value.isEmpty()) urls.add(value);
            }
        }
        return urls;
    }

    private String extractString(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case FORMULA -> cell.getCachedFormulaResultType() == CellType.STRING
                    ? cell.getStringCellValue() : String.valueOf((long) cell.getNumericCellValue());
            default -> "";
        };
    }
}
