package info.thinhpham.certificatemanager.model;

import java.util.ArrayList;
import java.util.Comparator;
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
    private List<CertChainEntry> trustChain = new ArrayList<>();

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
    public List<CertChainEntry> getTrustChain() { return trustChain; }
    public void setTrustChain(List<CertChainEntry> trustChain) { this.trustChain = trustChain; }

    public String getRootCACN() {
        if (trustChain == null || trustChain.isEmpty()) return "";
        return trustChain.stream()
                .filter(e -> e.getRole().equals("Root"))
                .map(CertChainEntry::getSubjectCN)
                .findFirst()
                // root not sent by server — infer from the issuer of the last cert
                .orElseGet(() -> trustChain.get(trustChain.size() - 1).getIssuerCN());
    }

    public String getEarliestIntermediateCN() {
        if (trustChain == null) return "";
        return trustChain.stream()
                .filter(e -> e.getRole().equals("Intermediate"))
                .min(Comparator.comparingLong(CertChainEntry::getDaysUntilExpiry))
                .map(CertChainEntry::getSubjectCN)
                .orElse("");
    }

    public Date getEarliestIntermediateExpiry() {
        if (trustChain == null) return null;
        return trustChain.stream()
                .filter(e -> e.getRole().equals("Intermediate"))
                .min(Comparator.comparingLong(CertChainEntry::getDaysUntilExpiry))
                .map(CertChainEntry::getExpiryDate)
                .orElse(null);
    }

    public boolean isRootInChain() {
        if (trustChain == null) return false;
        return trustChain.stream().anyMatch(e -> e.getRole().equals("Root"));
    }

    public Date getRootExpiry() {
        if (trustChain == null) return null;
        return trustChain.stream()
                .filter(e -> e.getRole().equals("Root"))
                .map(CertChainEntry::getExpiryDate)
                .findFirst().orElse(null);
    }

    /** Days until the soonest-expiring intermediate cert, or -999 if no intermediates in chain. */
    public long getEarliestIntermediateDays() {
        if (trustChain == null) return -999;
        return trustChain.stream()
                .filter(e -> e.getRole().equals("Intermediate"))
                .mapToLong(CertChainEntry::getDaysUntilExpiry)
                .min().orElse(-999);
    }

    public String getEarliestIntermediateStatus() {
        if (trustChain == null) return "";
        return trustChain.stream()
                .filter(e -> e.getRole().equals("Intermediate"))
                .min(Comparator.comparingLong(CertChainEntry::getDaysUntilExpiry))
                .map(CertChainEntry::getStatus)
                .orElse("");
    }

    public long getRootDays() {
        if (trustChain == null) return -999;
        return trustChain.stream()
                .filter(e -> e.getRole().equals("Root"))
                .mapToLong(CertChainEntry::getDaysUntilExpiry)
                .findFirst().orElse(-999);
    }

    public String getRootStatus() {
        if (trustChain == null) return "";
        return trustChain.stream()
                .filter(e -> e.getRole().equals("Root"))
                .map(CertChainEntry::getStatus)
                .findFirst().orElse("");
    }

    /** Worst status across intermediate and root certs (excludes leaf — that's already the main status). */
    public String getChainHealthStatus() {
        if (trustChain == null || trustChain.size() <= 1) return "VALID";
        int worst = trustChain.stream()
                .filter(e -> !e.getRole().equals("Leaf"))
                .mapToInt(e -> statusOrder(e.getStatus()))
                .min().orElse(4);
        return switch (worst) {
            case 0 -> "EXPIRED";
            case 1 -> "CRITICAL";
            case 2 -> "WARNING";
            case 3 -> "ERROR";
            default -> "VALID";
        };
    }

    private int statusOrder(String s) {
        return switch (s) {
            case "EXPIRED"  -> 0;
            case "CRITICAL" -> 1;
            case "WARNING"  -> 2;
            case "ERROR"    -> 3;
            default         -> 4;
        };
    }

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
