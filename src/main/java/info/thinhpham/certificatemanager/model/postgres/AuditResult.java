package info.thinhpham.certificatemanager.model.postgres;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_results")
public class AuditResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String url;

    @Column
    private String host;

    @Column
    private Integer port;

    @Column
    private String status;

    @Column(name = "days_until_expiry")
    private Long daysUntilExpiry;

    @Column(name = "expiry_date")
    private LocalDateTime expiryDate;

    @Column(name = "subject_cn")
    private String subjectCn;

    @Column(name = "issuer_org")
    private String issuerOrg;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "checked_at")
    private LocalDateTime checkedAt;

    public Long getId() { return id; }
    public String getUrl() { return url; }
    public String getHost() { return host; }
    public Integer getPort() { return port; }
    public String getStatus() { return status; }
    public Long getDaysUntilExpiry() { return daysUntilExpiry; }
    public LocalDateTime getExpiryDate() { return expiryDate; }
    public String getSubjectCn() { return subjectCn; }
    public String getIssuerOrg() { return issuerOrg; }
    public String getErrorMessage() { return errorMessage; }
    public LocalDateTime getCheckedAt() { return checkedAt; }

    public void setUrl(String url) { this.url = url; }
    public void setHost(String host) { this.host = host; }
    public void setPort(Integer port) { this.port = port; }
    public void setStatus(String status) { this.status = status; }
    public void setDaysUntilExpiry(Long daysUntilExpiry) { this.daysUntilExpiry = daysUntilExpiry; }
    public void setExpiryDate(LocalDateTime expiryDate) { this.expiryDate = expiryDate; }
    public void setSubjectCn(String subjectCn) { this.subjectCn = subjectCn; }
    public void setIssuerOrg(String issuerOrg) { this.issuerOrg = issuerOrg; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public void setCheckedAt(LocalDateTime checkedAt) { this.checkedAt = checkedAt; }
}
