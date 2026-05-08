package info.thinhpham.certificatemanager.controller;

import info.thinhpham.certificatemanager.model.CertificateInfo;
import info.thinhpham.certificatemanager.service.CertificateService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class CertificateController {

    private final CertificateService certificateService;

    public CertificateController(CertificateService certificateService) {
        this.certificateService = certificateService;
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
}
