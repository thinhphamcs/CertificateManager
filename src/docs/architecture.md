# CertificateManager — Architecture & Design Document

## 1. Vision

In a modern enterprise, certificate outages are rarely caused by a single public-facing domain. They are caused by "hidden" internal webservices, private APIs, and internal load balancers that live behind a firewall. Public scanners (e.g. SSL Labs) cannot reach these services.

CertificateManager is designed to run **on-premise or inside a private network**, bridging the gap between a static service inventory and the live certificate reality across that inventory.

---

## 2. System Overview

```
┌──────────────────────────────────────────────────────────────┐
│                        Input Sources                         │
│                                                              │
│   [Manual URL]   [Excel Upload]   [Oracle SERVICE_INVENTORY] │
│        │               │                      │              │
└────────┼───────────────┼─────────────────────-┼──────────────┘
         │               │                      │
         └───────────────▼──────────────────────┘
                         │
               ┌─────────▼──────────┐
               │  CertificateService │
               │   (Spring Boot)     │
               └─────────┬──────────┘
                         │
               Raw TLS Handshake (SSLSocket)
                         │
               X509Certificate Chain
                         │
          ┌──────────────┼──────────────────┐
          │              │                  │
     NotAfter       IssuerDN          SubjectAltNames
          │
   Days Until Expiry
          │
   Status Classification
   VALID / WARNING / CRITICAL / EXPIRED
          │
          ▼                           ▼
┌─────────────────────┐    ┌──────────────────────┐
│  Thymeleaf Dashboard│    │  PostgreSQL           │
│  (Web UI)           │    │  audit_results table  │
└─────────────────────┘    └──────────────────────┘
```

---

## 3. Component Breakdown

### 3.1 CertificateService

The core of the engine. Takes a raw URL or hostname and performs a direct TLS handshake at the **Socket layer** — no HTTP client, no REST call. This is intentional: it mirrors exactly what a browser or API client would negotiate, and works against any TCP service presenting a TLS certificate regardless of application protocol.

**Handshake flow:**
1. Parse host and port from the input URL (defaults to port 443)
2. Open an `SSLSocket` via `SSLSocketFactory.getDefault()`
3. Call `socket.startHandshake()` to complete the TLS negotiation
4. Read `socket.getSession().getPeerCertificates()` — this is the full chain
5. Cast the first entry to `X509Certificate` (the leaf / end-entity cert)
6. Extract fields and compute status

**Extracted fields:**
| Field | Source |
|---|---|
| Expiry Date | `X509Certificate.getNotAfter()` |
| Subject DN | `X509Certificate.getSubjectX500Principal()` |
| Issuer DN | `X509Certificate.getIssuerX500Principal()` |
| SANs (DNS names) | `X509Certificate.getSubjectAlternativeNames()` filtered to type `2` (dNSName) |
| Days Until Expiry | `ChronoUnit.DAYS.between(LocalDate.now(), expiryDate)` |

**Status thresholds:**
| Status | Condition |
|---|---|
| `VALID` | > 90 days remaining |
| `WARNING` | 30–90 days remaining |
| `CRITICAL` | 0–29 days remaining |
| `EXPIRED` | Past expiry date |
| `ERROR` | Socket or handshake failure |

`checkAll(List<String> urls)` runs `check()` across all URLs via `parallelStream()`.

### 3.2 ExcelParserService

Reads an `.xlsx` file via Apache POI `WorkbookFactory`. Skips row 1 (header), extracts column A values from row 2 onward. Handles `STRING`, `NUMERIC`, and `FORMULA` cell types. Returns a `List<String>` of raw URL values passed directly to `CertificateService.checkAll()`.

Expected Excel format: column A header = `URL`, one URL per row from row 2.

### 3.3 ExcelExportService

Takes the original uploaded workbook bytes and a `List<CertificateInfo>` and appends 14 audit columns to each data row (status, days until expiry, expiry date, subject CN, intermediate CA name/status/days/expiry, root CA name/status/days/expiry, chain health). Returns the enriched workbook as a `byte[]` for session storage and download.

### 3.4 AuditService

Orchestrates the Oracle → TLS check → PostgreSQL pipeline. Only instantiated when both `oracleEntityManagerFactory` and `postgresEntityManagerFactory` beans are present (`@ConditionalOnBean`).

**Flow:**
1. `ServiceInventoryRepository.findByActive(1)` — fetches active service URLs from Oracle
2. `CertificateService.checkAll(urls)` — runs parallel TLS handshakes
3. Maps each `CertificateInfo` to an `AuditResult` entity (host, port, status, days, expiry, CN, issuer, timestamp)
4. `AuditResultRepository.saveAll()` — persists to PostgreSQL

`getHistory()` returns the 100 most recent audit results ordered by `checked_at` descending.

### 3.5 CertificateController

A standard Spring MVC `@Controller`. `AuditService` is injected as `Optional<AuditService>` — if the DB beans are not active, it resolves to `null` and the audit endpoints return a "not configured" message rather than crashing.

| Method | Path | Description |
|---|---|---|
| `GET` | `/` | Renders the empty dashboard |
| `POST` | `/check` | Single URL check |
| `POST` | `/upload` | Bulk Excel upload — parallel check + session storage of enriched Excel |
| `GET` | `/download` | Returns enriched Excel from session |
| `POST` | `/audit/run` | Reads Oracle inventory, runs checks, writes to PostgreSQL, renders results |
| `GET` | `/audit/history` | Fetches last 100 results from PostgreSQL, renders on dashboard |

### 3.6 Dashboard (Thymeleaf)

Server-side rendered HTML using Thymeleaf and Bootstrap 5. No separate frontend build process or Node.js toolchain required. Renders four independent result areas:

- Single-check result card
- Bulk Excel results table with summary chips and download link
- Oracle Inventory Audit card (Run Audit + View History buttons)
- Audit Results table (from Oracle audit or history view)

---

## 4. Data Flows

### Single URL Check
```
User enters URL → POST /check → CertificateService.check() → result card on dashboard
```

### Bulk Excel Upload
```
User uploads .xlsx → POST /upload → ExcelParserService.parseUrls()
→ CertificateService.checkAll() (parallel) → results table + summary chips
→ ExcelExportService.buildEnrichedExcel() → stored in session → available for download
```

### Oracle Inventory Audit
```
POST /audit/run
→ ServiceInventoryRepository.findByActive(1)   [Oracle read]
→ CertificateService.checkAll() (parallel)
→ AuditResultRepository.saveAll()              [PostgreSQL write]
→ audit results table on dashboard
```

### Audit History
```
GET /audit/history
→ AuditResultRepository.findTop100ByOrderByCheckedAtDesc()  [PostgreSQL read]
→ audit results table on dashboard
```

---

## 5. Dual DataSource Configuration

Both datasources are **opt-in** via feature flags. The app starts and runs Phases 1–2 with no DB at all.

```yaml
db:
  oracle:
    enabled: false   # flip to true when Oracle credentials are available
  postgres:
    enabled: false   # flip to true when PostgreSQL credentials are available
```

| Bean | Condition | Scope |
|---|---|---|
| `OracleDataSourceConfig` | `db.oracle.enabled=true` | Primary EMF; `hbm2ddl=none` (schema owned by DBA) |
| `PostgresDataSourceConfig` | `db.postgres.enabled=true` | Secondary EMF; `hbm2ddl=update` (auto-creates `audit_results`) |
| `AuditService` | Both EMFs present | `@ConditionalOnBean` on both factory names |

Each datasource config class uses `@EnableJpaRepositories` with explicit `entityManagerFactoryRef` and `transactionManagerRef` to route its repository package to the correct persistence unit — Oracle repos under `repository.oracle`, PostgreSQL repos under `repository.postgres`.

**Expected Oracle schema (managed externally):**
```sql
CREATE TABLE SERVICE_INVENTORY (
    ID           NUMBER        PRIMARY KEY,
    SERVICE_NAME VARCHAR2(255),
    URL          VARCHAR2(500),
    ACTIVE       NUMBER(1)     DEFAULT 1
);
```

PostgreSQL's `audit_results` table is auto-created by Hibernate on first startup when `db.postgres.enabled=true`.

---

## 6. Security Posture

- **No outbound dependency on third-party scanners.** All TLS handshakes are performed locally by the JVM using the system's default `SSLContext`.
- **Internal network capability.** Because the tool runs as a local Spring Boot process, it has the same network access as the host machine — enabling audits of services behind a VPN or firewall.
- **Credentials** for Oracle and PostgreSQL are externalised to `application.yml` and should be injected via environment variables or a secrets manager in production. No credentials are committed to source control.

---

## 7. Key Design Decisions

| Decision | Rationale |
|---|---|
| Raw `SSLSocket` over HTTP client | Directly mirrors TLS negotiation; works on non-HTTP services; no dependency on URL scheme |
| `parallelStream()` for bulk checks | Each TLS handshake is independent and I/O-bound; parallelism reduces wall-clock time proportionally to thread count |
| Thymeleaf over React/SPA | Zero frontend build tooling; single deployable JAR; appropriate for an internal SRE tool |
| Dual datasource (Oracle + PostgreSQL) | Oracle is the existing enterprise registry (read-only, `hbm2ddl=none`); PostgreSQL is the operational audit store (read-write, `hbm2ddl=update`) |
| `@ConditionalOnProperty` on datasource configs | App starts and all Phase 1–2 features work without any DB; DB features degrade gracefully to a UI message when not configured |
| `Optional<AuditService>` injection in controller | Avoids hard startup failure when DB beans are absent; controller null-checks and shows "not configured" instead of throwing |
| `DataSourceAutoConfiguration` excluded at startup | Prevents Spring from trying to auto-wire a single datasource; manual `LocalContainerEntityManagerFactoryBean` wiring gives full control over both persistence units |
