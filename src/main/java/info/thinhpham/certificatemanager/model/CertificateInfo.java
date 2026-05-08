package info.thinhpham.certificatemanager.model;

import java.util.Date;
import java.util.List;
import java.util.Map;

public class CertificateInfo {

    private String host;
    private int port;
    private Date expiryDate;
    private long daysUntilExpiry;
    private String subjectDN;
    private String subjectCN;   // parsed: just the CN value from subjectDN
    private String issuerDN;
    private String issuerOrg;   // parsed: O (or CN) from issuerDN — the CA's readable name
    private List<String> subjectAlternativeNames;
    private String status;      // VALID, WARNING, CRITICAL, EXPIRED, ERROR
    private String errorMessage;

    public CertificateInfo() {}

    public CertificateInfo(String host, int port, Date expiryDate, long daysUntilExpiry,
                           String subjectDN, String issuerDN,
                           List<String> subjectAlternativeNames, String status, String errorMessage) {
        this.host = host;
        this.port = port;
        this.expiryDate = expiryDate;
        this.daysUntilExpiry = daysUntilExpiry;
        this.subjectDN = subjectDN;
        this.issuerDN = issuerDN;
        this.subjectAlternativeNames = subjectAlternativeNames;
        this.status = status;
        this.errorMessage = errorMessage;
    }

    public static CertificateInfo error(String host, int port, String message) {
        return new CertificateInfo(host, port, null, 0, null, null, List.of(), "ERROR", message);
    }

    public String getHost() { return host; }
    public int getPort() { return port; }
    public Date getExpiryDate() { return expiryDate; }
    public long getDaysUntilExpiry() { return daysUntilExpiry; }
    public String getSubjectDN() { return subjectDN; }
    public String getSubjectCN() { return subjectCN != null ? subjectCN : ""; }
    public String getIssuerDN() { return issuerDN; }
    public String getIssuerOrg() { return issuerOrg != null ? issuerOrg : ""; }
    public List<String> getSubjectAlternativeNames() { return subjectAlternativeNames; }
    public String getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }

    public void setSubjectCN(String subjectCN) { this.subjectCN = subjectCN; }
    public void setIssuerOrg(String issuerOrg) { this.issuerOrg = issuerOrg; }

    /** Expands raw issuer DN abbreviations into human-readable labels.
     *  e.g. "CN=E7,O=Let's Encrypt,C=US" → "Common Name: E7 · Organization: Let's Encrypt · Country: US" */
    public String getIssuerDNFormatted() {
        if (issuerDN == null) return "";
        Map<String, String> labels = Map.of(
                "CN", "Common Name",
                "O",  "Organization",
                "OU", "Unit",
                "C",  "Country",
                "ST", "State",
                "L",  "City"
        );
        StringBuilder sb = new StringBuilder();
        for (String part : issuerDN.split(",")) {
            int eq = part.indexOf('=');
            if (eq == -1) continue;
            String key   = part.substring(0, eq).trim();
            String value = part.substring(eq + 1).trim();
            if (sb.length() > 0) sb.append("  ·  ");
            sb.append(labels.getOrDefault(key, key)).append(": ").append(value);
        }
        return sb.toString();
    }
}
