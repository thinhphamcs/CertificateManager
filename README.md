# CertificateManager
**Enterprise SSL/TLS Lifecycle Management & Audit Engine**

CertificateManager is a high-performance Java 17 / Spring Boot application designed for Site Reliability Engineers and System Analysts who need to audit SSL/TLS certificate health across large web-service inventories — including internal services that public tools cannot reach.

---

## Features

- **Raw TLS Handshake Inspection** — connects directly at the Socket layer, bypassing HTTP entirely, to extract the full certificate chain
- **Certificate Detail Extraction** — parses expiry date, Subject Alternative Names (SANs), Subject DN, and Issuer DN
- **Expiry Status Classification** — automatically classifies each certificate as `VALID`, `WARNING` (<90 days), `CRITICAL` (<30 days), or `EXPIRED`
- **Web Dashboard** — a clean, dark-themed UI for inspecting certificates by hostname or URL
- **Internal Network Support** — runs on-premise or inside a private network, enabling audits of internal APIs and load balancers that external scanners cannot access

---

## Tech Stack

| Layer | Technology |
|---|---|
| Runtime | Java 17 |
| Framework | Spring Boot 3.x |
| UI | Thymeleaf + Bootstrap 5 |
| Build | Maven |
| Operational DB | PostgreSQL *(planned)* |
| Inventory Source | Oracle DB *(planned)* |
| Bulk Input | Apache POI / Excel *(planned)* |

---

## Roadmap

This project is being built in three deliberate phases:

**Phase 1 — Core Engine & Dashboard** *(current)*
- Manual URL input via the web dashboard
- Raw TLS handshake to inspect any publicly reachable or internally accessible host
- Certificate detail display with expiry countdown and status classification

**Phase 2 — Bulk Excel Processing**
- Accept user-uploaded Excel spreadsheets containing lists of service URLs
- Process all entries in bulk and display a results table across the entire inventory
- Export audit results

**Phase 3 — Enterprise Database Integration**
- Connect to an Oracle DB (enterprise service registry) to automatically pull the full inventory of active webservices and endpoints
- Write audit results to PostgreSQL for historical tracking and reporting

---

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.8+

### Run

```bash
git clone https://github.com/thinhphamcs/CertificateManager.git
cd CertificateManager
mvn spring-boot:run
```

Open your browser and navigate to:

```
http://localhost:8080
```

Enter any hostname or URL (e.g. `google.com`, `https://internal-api.company.com:8443`) and click **Check** to inspect its certificate.

---

## Architecture Overview

```
User Input (URL)
      │
      ▼
CertificateController  ──▶  CertificateService
                                    │
                          SSLSocket (raw TLS handshake)
                                    │
                          X509Certificate chain
                                    │
                    ┌───────────────┼──────────────────┐
                    │               │                  │
               NotAfter        IssuerDN           SubjectAltNames
                    │
             Days Until Expiry
                    │
             Status: VALID / WARNING / CRITICAL / EXPIRED
                    │
                    ▼
           Thymeleaf Dashboard
```

For a detailed design document, see [src/docs/architecture.md](src/docs/architecture.md).
