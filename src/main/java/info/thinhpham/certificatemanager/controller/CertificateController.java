package info.thinhpham.certificatemanager.controller;

import info.thinhpham.certificatemanager.model.CertificateInfo;
import info.thinhpham.certificatemanager.service.CertificateService;
import info.thinhpham.certificatemanager.service.ExcelParserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class CertificateController {

    private final CertificateService certificateService;
    private final ExcelParserService excelParserService;

    public CertificateController(CertificateService certificateService, ExcelParserService excelParserService) {
        this.certificateService = certificateService;
        this.excelParserService = excelParserService;
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
    public String upload(@RequestParam("file") MultipartFile file, Model model) {
        try {
            List<String> urls = excelParserService.parseUrls(file);
            List<CertificateInfo> results = certificateService.checkAll(urls);
            Map<String, Long> summary = results.stream()
                    .collect(Collectors.groupingBy(CertificateInfo::getStatus, Collectors.counting()));
            model.addAttribute("bulkResults", results);
            model.addAttribute("bulkSummary", summary);
            model.addAttribute("bulkFileName", file.getOriginalFilename());
        } catch (IOException e) {
            model.addAttribute("uploadError", "Could not read Excel file: " + e.getMessage());
        }
        return "dashboard";
    }
}
