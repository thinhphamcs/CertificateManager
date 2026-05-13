# CertificateManager
**Enterprise SSL/TLS Lifecycle Management & Audit Engine**

Built for SREs and System Analysts who need to audit certificate health across large web-service inventories — including internal services that public tools cannot reach.

---

## Features

- **Raw TLS Handshake** — connects at the Socket layer, bypasses HTTP entirely, extracts the full certificate chain
- **Single URL Check** — enter any hostname or URL to instantly inspect its certificate
- **Bulk Excel Upload** — upload an `.xlsx` file of URLs; engine processes them all in parallel and returns a ranked results table
- **Status Classification** — `VALID` (>90 days), `WARNING` (<90), `CRITICAL` (<30), `EXPIRED`
- **Certificate Details** — expiry date, Subject CN, SANs, Issuer/CA name, full DN breakdown
- **Internal Network Support** — runs on-premise; audits internal APIs and load balancers that external scanners cannot reach

---

## Tech Stack

| Layer | Technology |
|---|---|
| Runtime | Java 17 |
| Framework | Spring Boot 3.x |
| UI | Thymeleaf + Bootstrap 5 |
| Bulk Input | Apache POI 5.x |
| Build | Maven |
| Operational DB | PostgreSQL *(Phase 3)* |
| Inventory Source | Oracle DB *(Phase 3)* |

---

## Getting Started

**Prerequisites:** Java 17+, Maven 3.8+

```bash
git clone https://github.com/thinhphamcs/CertificateManager.git
cd CertificateManager
mvn spring-boot:run
```

Open `http://localhost:8080`.

**Single check** — enter a hostname or URL (e.g. `google.com`, `https://internal-api.company.com:8443`) and click **Check**.

**Bulk check** — upload an `.xlsx` file where column A is `URL` (header row 1, data from row 2). The engine scans all entries in parallel and displays a results table sorted by severity.

---

## Architecture

```
Single URL ──────────────────────────────────┐
                                             ▼
Excel Upload ──▶ ExcelParserService ──▶ CertificateController ──▶ CertificateService
                                                                         │
                                                               SSLSocket (raw TLS handshake)
                                                                         │
                                                               X509Certificate chain
                                                                         │
                                                    ┌────────────────────┼──────────────────┐
                                                    │                    │                  │
                                               NotAfter             IssuerDN          SubjectAltNames
                                                    │
                                             Days Until Expiry
                                                    │
                                        VALID / WARNING / CRITICAL / EXPIRED
                                                    │
                                                    ▼
                                          Thymeleaf Dashboard
```

For the full design document see [src/docs/architecture.md](src/docs/architecture.md).
