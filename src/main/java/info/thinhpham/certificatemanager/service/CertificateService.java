package info.thinhpham.certificatemanager.service;

import info.thinhpham.certificatemanager.model.CertChainEntry;
import info.thinhpham.certificatemanager.model.CertificateInfo;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import javax.net.ssl.*;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import javax.security.auth.x500.X500Principal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CertificateService {

    private static final int DEFAULT_PORT = 443;
    private static final int SOCKET_TIMEOUT_MS = 7000;

    private Set<X500Principal> trustedRootSubjects = new HashSet<>();

    @PostConstruct
    private void loadTrustedRoots() {
        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null);
            for (TrustManager tm : tmf.getTrustManagers()) {
                if (tm instanceof X509TrustManager xtm) {
                    for (X509Certificate cert : xtm.getAcceptedIssuers()) {
                        trustedRootSubjects.add(cert.getSubjectX500Principal());
                    }
                }
            }
        } catch (Exception ignored) {
            // fall back to position-based if trust store unavailable
        }
    }

    public List<CertificateInfo> checkAll(List<String> urls) {
        return urls.parallelStream()
                .map(this::check)
                .collect(Collectors.toList()); // preserves original row order for export
    }

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
                String org = parseDNComponent(issuerDN, "O");
                info.setIssuerOrg(org.isEmpty() ? parseDNComponent(issuerDN, "CN") : org);
                info.setTrustChain(buildTrustChain(chain));

                return info;
            }
        } catch (Exception e) {
            return CertificateInfo.error(host, port, e.getMessage());
        }
    }

    private List<CertChainEntry> buildTrustChain(Certificate[] chain) {
        List<CertChainEntry> result = new ArrayList<>();
        for (int i = 0; i < chain.length; i++) {
            X509Certificate cert = (X509Certificate) chain[i];
            String subjectDN = cert.getSubjectX500Principal().getName();
            String issuerDN  = cert.getIssuerX500Principal().getName();
            String role;
            if (i == 0) {
                role = "Leaf";
            } else if (!trustedRootSubjects.isEmpty()) {
                role = trustedRootSubjects.contains(cert.getSubjectX500Principal()) ? "Root" : "Intermediate";
            } else {
                role = (i == chain.length - 1) ? "Root" : "Intermediate"; // fallback if trust store failed
            }
            Date expiry = cert.getNotAfter();
            long days = ChronoUnit.DAYS.between(
                    LocalDate.now(),
                    expiry.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
            result.add(new CertChainEntry(role,
                    parseDNComponent(subjectDN, "CN"), parseDNComponent(subjectDN, "O"),
                    parseDNComponent(issuerDN, "CN"),
                    expiry, days, resolveStatus(days)));
        }
        return result;
    }

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
