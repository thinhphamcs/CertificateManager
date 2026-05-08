package info.thinhpham.certificatemanager.service;

import info.thinhpham.certificatemanager.model.CertificateInfo;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.security.cert.Certificate;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CertificateService {

    private static final int DEFAULT_PORT = 443;
    private static final int SOCKET_TIMEOUT_MS = 7000;

    public CertificateInfo check(String rawUrl) {
        String host = parseHost(rawUrl);
        int port = parsePort(rawUrl);

        try {
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            try (SSLSocket socket = (SSLSocket) factory.createSocket(host, port)) {
                socket.setSoTimeout(SOCKET_TIMEOUT_MS);
                socket.startHandshake();

                Certificate[] chain = socket.getSession().getPeerCertificates();
                X509Certificate leaf = (X509Certificate) chain[0];

                Date expiryDate = leaf.getNotAfter();
                long daysRemaining = ChronoUnit.DAYS.between(
                        LocalDate.now(),
                        expiryDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                );

                String subjectDN = leaf.getSubjectX500Principal().getName();
                String issuerDN = leaf.getIssuerX500Principal().getName();

                CertificateInfo info = new CertificateInfo(
                        host, port, expiryDate, daysRemaining,
                        subjectDN, issuerDN,
                        extractSANs(leaf), resolveStatus(daysRemaining), null
                );
                info.setSubjectCN(parseDNComponent(subjectDN, "CN"));
                // Prefer O (organisation name) for the CA label; fall back to CN
                String org = parseDNComponent(issuerDN, "O");
                info.setIssuerOrg(org.isEmpty() ? parseDNComponent(issuerDN, "CN") : org);

                return info;
            }
        } catch (Exception e) {
            return CertificateInfo.error(host, port, e.getMessage());
        }
    }

    /**
     * Extracts a single attribute value from an X.500 DN string.
     * e.g. parseDNComponent("CN=E7,O=Let's Encrypt,C=US", "O") → "Let's Encrypt"
     */
    private String parseDNComponent(String dn, String key) {
        String prefix = key + "=";
        for (String part : dn.split(",")) {
            String trimmed = part.trim();
            if (trimmed.startsWith(prefix)) {
                return trimmed.substring(prefix.length());
            }
        }
        return "";
    }

    private String parseHost(String rawUrl) {
        String cleaned = rawUrl.trim();
        if (cleaned.startsWith("https://")) cleaned = cleaned.substring(8);
        else if (cleaned.startsWith("http://")) cleaned = cleaned.substring(7);

        int slashIdx = cleaned.indexOf('/');
        if (slashIdx != -1) cleaned = cleaned.substring(0, slashIdx);

        int colonIdx = cleaned.lastIndexOf(':');
        if (colonIdx != -1) cleaned = cleaned.substring(0, colonIdx);

        return cleaned;
    }

    private int parsePort(String rawUrl) {
        String cleaned = rawUrl.trim();
        if (cleaned.startsWith("https://")) cleaned = cleaned.substring(8);
        else if (cleaned.startsWith("http://")) cleaned = cleaned.substring(7);

        int slashIdx = cleaned.indexOf('/');
        if (slashIdx != -1) cleaned = cleaned.substring(0, slashIdx);

        int colonIdx = cleaned.lastIndexOf(':');
        if (colonIdx != -1) {
            try {
                return Integer.parseInt(cleaned.substring(colonIdx + 1));
            } catch (NumberFormatException ignored) {}
        }
        return DEFAULT_PORT;
    }

    private String resolveStatus(long daysRemaining) {
        if (daysRemaining < 0) return "EXPIRED";
        if (daysRemaining < 30) return "CRITICAL";
        if (daysRemaining < 90) return "WARNING";
        return "VALID";
    }

    private List<String> extractSANs(X509Certificate cert) throws CertificateParsingException {
        Collection<List<?>> sans = cert.getSubjectAlternativeNames();
        if (sans == null) return List.of();
        return sans.stream()
                .filter(san -> Integer.valueOf(2).equals(san.get(0))) // 2 = dNSName
                .map(san -> (String) san.get(1))
                .collect(Collectors.toList());
    }
}
