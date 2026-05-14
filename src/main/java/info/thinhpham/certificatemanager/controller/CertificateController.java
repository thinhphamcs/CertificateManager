package info.thinhpham.certificatemanager.controller;

import info.thinhpham.certificatemanager.model.CertificateInfo;
import info.thinhpham.certificatemanager.model.postgres.AuditResult;
import info.thinhpham.certificatemanager.service.AuditService;
import info.thinhpham.certificatemanager.service.CertificateService;
import info.thinhpham.certificatemanager.service.ExcelExportService;
import info.thinhpham.certificatemanager.service.ExcelParserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
public class CertificateController {

    private final CertificateService certificateService;
    private final ExcelParserService excelParserService;
    private final ExcelExportService excelExportService;
    private final AuditService auditService;

    public CertificateController(CertificateService certificateService,
                                 ExcelParserService excelParserService,
                                 ExcelExportService excelExportService,
                                 Optional<AuditService> auditService) {
        this.certificateService = certificateService;
        this.excelParserService = excelParserService;
        this.excelExportService = excelExportService;
        this.auditService = auditService.orElse(null);
    }

    @GetMapping("/")
    public String dashboard() {
        return "dashboard";
    }

    @PostMapping("/check")
    public String check(@RequestParam String url, Model model) {
        CertificateInfo result = certificateService.check(url);
        model.addAttribute("result", result);
        model.addAttribute("checkedUrl", url);
        return "dashboard";
    }

    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file, Model model, HttpSession session) {
        try {
            byte[] fileBytes = file.getBytes();
            List<String> urls = excelParserService.parseUrls(fileBytes);
            List<CertificateInfo> results = certificateService.checkAll(urls);

            byte[] enriched = excelExportService.buildEnrichedExcel(fileBytes, results);
            session.setAttribute("enrichedExcel", enriched);
            session.setAttribute("enrichedExcelName", "audit_" + file.getOriginalFilename());

            List<CertificateInfo> sortedResults = results.stream()
                    .sorted(Comparator.comparingInt(i -> statusOrder(i.getStatus())))
                    .collect(Collectors.toList());

            Map<String, Long> summary = sortedResults.stream()
                    .collect(Collectors.groupingBy(CertificateInfo::getStatus, Collectors.counting()));

            model.addAttribute("bulkResults", sortedResults);
            model.addAttribute("bulkSummary", summary);
            model.addAttribute("bulkFileName", file.getOriginalFilename());
        } catch (IOException e) {
            model.addAttribute("uploadError", "Could not read Excel file: " + e.getMessage());
        }
        return "dashboard";
    }

    @GetMapping("/download")
    public ResponseEntity<byte[]> download(HttpSession session) {
        byte[] data = (byte[]) session.getAttribute("enrichedExcel");
        String filename = (String) session.getAttribute("enrichedExcelName");
        if (data == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    @PostMapping("/audit/run")
    public String runAudit(Model model) {
        if (auditService == null) {
            model.addAttribute("auditError", "Database not configured. Set db.oracle.enabled=true and db.postgres.enabled=true in application.yml.");
            return "dashboard";
        }
        try {
            List<AuditResult> results = auditService.runAudit();
            model.addAttribute("auditResults", results);
            model.addAttribute("auditSummary", summarise(results));
        } catch (Exception e) {
            model.addAttribute("auditError", "Audit failed: " + e.getMessage());
        }
        return "dashboard";
    }

    @GetMapping("/audit/history")
    public String auditHistory(Model model) {
        if (auditService == null) {
            model.addAttribute("auditError", "Database not configured. Set db.oracle.enabled=true and db.postgres.enabled=true in application.yml.");
            return "dashboard";
        }
        try {
            List<AuditResult> results = auditService.getHistory();
            model.addAttribute("auditResults", results);
            model.addAttribute("auditSummary", summarise(results));
        } catch (Exception e) {
            model.addAttribute("auditError", "Could not load history: " + e.getMessage());
        }
        return "dashboard";
    }

    private Map<String, Long> summarise(List<AuditResult> results) {
        return results.stream()
                .collect(Collectors.groupingBy(AuditResult::getStatus, Collectors.counting()));
    }

    private int statusOrder(String status) {
        return switch (status) {
            case "EXPIRED"  -> 0;
            case "CRITICAL" -> 1;
            case "WARNING"  -> 2;
            case "ERROR"    -> 3;
            default         -> 4;
        };
    }
}
