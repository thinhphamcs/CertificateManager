package info.thinhpham.certificatemanager.service;

import info.thinhpham.certificatemanager.model.CertChainEntry;
import info.thinhpham.certificatemanager.model.CertificateInfo;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ExcelExportService {

    private static final String[] APPENDED_HEADERS = {
            "Status", "Days Until Expiry", "Expiry Date", "Common Name",
            "Intermediate CA", "Intermediate CA CN", "Interm. Status", "Interm. Days Until Expiry", "Interm. Expiry Date",
            "Root CA", "Root In Chain", "Root Status", "Root Days Until Expiry", "Root Expiry Date"
    };

    public byte[] buildEnrichedExcel(byte[] originalBytes, List<CertificateInfo> orderedResults) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(originalBytes));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.getSheetAt(0);
            int startCol = findStartColumn(sheet);

            writeHeaders(sheet.getRow(0) != null ? sheet.getRow(0) : sheet.createRow(0), startCol);

            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy");
            for (int i = 0; i < orderedResults.size(); i++) {
                Row row = sheet.getRow(i + 1);
                if (row == null) row = sheet.createRow(i + 1);
                writeResultRow(row, startCol, orderedResults.get(i), sdf);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private int findStartColumn(Sheet sheet) {
        Row header = sheet.getRow(0);
        if (header == null) return 1;
        int col = 1;
        while (header.getCell(col) != null && !header.getCell(col).getStringCellValue().isBlank()) col++;
        return col;
    }

    private void writeHeaders(Row header, int startCol) {
        for (int i = 0; i < APPENDED_HEADERS.length; i++) {
            header.createCell(startCol + i).setCellValue(APPENDED_HEADERS[i]);
        }
    }

    private void writeResultRow(Row row, int startCol, CertificateInfo info, SimpleDateFormat sdf) {
        row.createCell(startCol).setCellValue(info.getStatus());
        row.createCell(startCol + 1).setCellValue(info.getStatus().equals("ERROR") ? "" : String.valueOf(info.getDaysUntilExpiry()));
        row.createCell(startCol + 2).setCellValue(info.getExpiryDate() != null ? sdf.format(info.getExpiryDate()) : "");
        row.createCell(startCol + 3).setCellValue(info.getSubjectCN());

        // Intermediate CA — soonest expiring
        long intermDays = info.getEarliestIntermediateDays();
        boolean hasInterm = intermDays != -999;
        row.createCell(startCol + 4).setCellValue(hasInterm ? info.getIssuerOrg() : "");
        row.createCell(startCol + 5).setCellValue(hasInterm ? info.getEarliestIntermediateCN() : "");
        row.createCell(startCol + 6).setCellValue(hasInterm ? info.getEarliestIntermediateStatus() : "");
        row.createCell(startCol + 7).setCellValue(hasInterm ? String.valueOf(intermDays) : "");
        row.createCell(startCol + 8).setCellValue(hasInterm && info.getEarliestIntermediateExpiry() != null
                ? sdf.format(info.getEarliestIntermediateExpiry()) : "");

        // Root CA
        boolean rootInChain = info.isRootInChain();
        row.createCell(startCol + 9).setCellValue(info.getRootCACN());
        row.createCell(startCol + 10).setCellValue(rootInChain ? "Yes" : "No");
        row.createCell(startCol + 11).setCellValue(rootInChain ? info.getRootStatus() : "");
        row.createCell(startCol + 12).setCellValue(rootInChain ? String.valueOf(info.getRootDays()) : "");
        row.createCell(startCol + 13).setCellValue(rootInChain && info.getRootExpiry() != null
                ? sdf.format(info.getRootExpiry()) : "");
    }
}
