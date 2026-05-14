# CertificateManager
**Enterprise SSL/TLS Lifecycle Management & Audit Engine**

Built for SREs and System Analysts who need to audit certificate health across large web-service inventories — including internal services that public tools cannot reach.

---

## Features

- **Raw TLS Handshake** — connects at the Socket layer, bypasses HTTP entirely, extracts the full certificate chain
- **Single URL Check** — enter any hostname or URL to instantly inspect its certificate
- **Bulk Excel Upload** — upload an `.xlsx` file of URLs; engine processes them all in parallel and returns a ranked results table
- **Excel Export** — download the original spreadsheet enriched with 14 appended audit columns (status, days, expiry, CN, intermediate CA, root CA)
- **Status Classification** — `VALID` (>90 days), `WARNING` (<90), `CRITICAL` (<30), `EXPIRED`
- **Full Trust Chain** — role-classified chain (Leaf → Intermediate → Root) with per-cert expiry and status
- **Certificate Details** — expiry date, Subject CN, SANs, Issuer/CA name, full DN breakdown
- **Oracle Inventory Audit** — reads active service URLs from an Oracle `SERVICE_INVENTORY` table, runs TLS checks in parallel, and persists results to PostgreSQL
- **Audit History** — view the last 100 audit runs stored in PostgreSQL
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
| Inventory Source | Oracle DB (ojdbc11) |
| Operational DB | PostgreSQL |

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

**Bulk check** — upload an `.xlsx` file where column A is `URL` (header row 1, data from row 2). The engine scans all entries in parallel and displays a results table sorted by severity. Click **Download Excel** to get the enriched spreadsheet.

---

## Database Integration

By default both database flags are `false` and the app runs without any DB connection. To enable:

1. Update `src/main/resources/application.yml` with your credentials and flip the feature flags to `true`:

```yaml
server:
  port: 8080

spring:
  profiles:
    active: dev

  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB

  jpa:
    open-in-view: false

  datasource:
    postgres:
      url: jdbc:postgresql://localhost:5432/your_postgres_db
      username: pg_user
      password: pg_password
      driver-class-name: org.postgresql.Driver
    oracle:
      url: jdbc:oracle:thin:@localhost:1521:xe
      username: oracle_user
      password: oracle_password
      driver-class-name: oracle.jdbc.OracleDriver

db:
  oracle:
    enabled: true   # reads SERVICE_INVENTORY table
  postgres:
    enabled: true   # writes audit_results table (auto-created on first start)
```

3. Restart the app. The **Run Audit** button on the dashboard will fetch active URLs from Oracle, run TLS checks, and persist results to PostgreSQL. **View History** shows the last 100 stored results.

**Expected Oracle schema:**
```sql
CREATE TABLE SERVICE_INVENTORY (
    ID           NUMBER        PRIMARY KEY,
    SERVICE_NAME VARCHAR2(255),
    URL          VARCHAR2(500),
    ACTIVE       NUMBER(1)     DEFAULT 1
);
```

PostgreSQL's `audit_results` table is created automatically by Hibernate on first startup.

---

## Architecture

```
Single URL ──────────────────────────────────────────────────────┐
                                                                 ▼
Excel Upload ──▶ ExcelParserService ──▶ CertificateController ──▶ CertificateService
                                               ▲                        │
Oracle DB ──▶ ServiceInventoryRepository ──────┘             SSLSocket (raw TLS)
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
                              ┌────────────────────┴──────────────────────┐
                              ▼                                            ▼
                    Thymeleaf Dashboard                        AuditResultRepository
                                                                    (PostgreSQL)
```

For the full design document see [src/docs/architecture.md](src/docs/architecture.md).
