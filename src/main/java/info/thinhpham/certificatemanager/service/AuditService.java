package info.thinhpham.certificatemanager.service;

import info.thinhpham.certificatemanager.model.CertificateInfo;
import info.thinhpham.certificatemanager.model.oracle.ServiceInventory;
import info.thinhpham.certificatemanager.model.postgres.AuditResult;
import info.thinhpham.certificatemanager.repository.oracle.ServiceInventoryRepository;
import info.thinhpham.certificatemanager.repository.postgres.AuditResultRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = {"db.oracle.enabled", "db.postgres.enabled"}, havingValue = "true")
public class AuditService {

    private final ServiceInventoryRepository serviceInventoryRepo;
    private final AuditResultRepository auditResultRepo;
    private final CertificateService certificateService;

    public AuditService(ServiceInventoryRepository serviceInventoryRepo,
                        AuditResultRepository auditResultRepo,
                        CertificateService certificateService) {
        this.serviceInventoryRepo = serviceInventoryRepo;
        this.auditResultRepo = auditResultRepo;
        this.certificateService = certificateService;
    }

    public List<AuditResult> runAudit() {
        List<String> urls = serviceInventoryRepo.findByActive(1)
                .stream()
                .map(ServiceInventory::getUrl)
                .collect(Collectors.toList());

        List<CertificateInfo> checks = certificateService.checkAll(urls);

        List<AuditResult> results = checks.stream()
                .map(this::toAuditResult)
                .collect(Collectors.toList());

        return auditResultRepo.saveAll(results);
    }

    public List<AuditResult> getHistory() {
        return auditResultRepo.findTop100ByOrderByCheckedAtDesc();
    }

    private AuditResult toAuditResult(CertificateInfo info) {
        AuditResult result = new AuditResult();
        result.setUrl(info.getHost() + ":" + info.getPort());
        result.setHost(info.getHost());
        result.setPort(info.getPort());
        result.setStatus(info.getStatus());
        result.setDaysUntilExpiry(info.getDaysUntilExpiry());
        if (info.getExpiryDate() != null) {
            result.setExpiryDate(info.getExpiryDate().toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDateTime());
        }
        result.setSubjectCn(info.getSubjectCN());
        result.setIssuerOrg(info.getIssuerOrg());
        result.setErrorMessage(info.getErrorMessage());
        result.setCheckedAt(LocalDateTime.now());
        return result;
    }
}
