package info.thinhpham.certificatemanager.model;

import java.util.Date;

public class CertChainEntry {

    private final String role; // "Leaf", "Intermediate", "Root"
    private final String subjectCN;
    private final String organization;
    private final String issuerCN;   // CN of whoever signed this cert
    private final Date expiryDate;
    private final long daysUntilExpiry;
    private final String status; // VALID, WARNING, CRITICAL, EXPIRED

    public CertChainEntry(String role, String subjectCN, String organization, String issuerCN,
                          Date expiryDate, long daysUntilExpiry, String status) {
        this.role = role;
        this.subjectCN = subjectCN;
        this.organization = organization;
        this.issuerCN = issuerCN;
        this.expiryDate = expiryDate;
        this.daysUntilExpiry = daysUntilExpiry;
        this.status = status;
    }

    public String getRole() { return role; }
    public String getSubjectCN() { return subjectCN; }
    public String getOrganization() { return organization; }
    public String getIssuerCN() { return issuerCN; }
    public Date getExpiryDate() { return expiryDate; }
    public long getDaysUntilExpiry() { return daysUntilExpiry; }
    public String getStatus() { return status; }
}
