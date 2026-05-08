# CertificateManager — Architecture & Design Document

## 1. Vision

In a modern enterprise, certificate outages are rarely caused by a single public-facing domain. They are caused by "hidden" internal webservices, private APIs, and internal load balancers that live behind a firewall. Public scanners (e.g. SSL Labs) cannot reach these services.

CertificateManager is designed to run **on-premise or inside a private network**, bridging the gap between a static service inventory and the live certificate reality across that inventory.

---

## 2. System Overview

```
┌─────────────────────────────────────────────────────────┐
│                     Input Sources                       │
│                                                         │
│   [Manual URL]   [Excel Upload]*   [Oracle DB]*         │
│        │               │                │               │
└────────┼───────────────┼────────────────┼───────────────┘
         │               │                │
         └───────────────▼────────────────┘
                         │
               ┌─────────▼──────────┐
               │  CertificateService │
               │  (Spring Boot)      │
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
          ▼
┌─────────────────────┐        ┌─────────────────────┐
│  Thymeleaf Dashboard│        │   PostgreSQL (audit) │*
│  (Web UI)           │        │   Historical records │
└─────────────────────┘        └─────────────────────┘
```

`*` = planned in a future phase

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

### 3.2 CertificateController

A standard Spring MVC `@Controller` with two endpoints:

| Method | Path | Description |
|---|---|---|
| `GET` | `/` | Renders the empty dashboard |
| `POST` | `/check` | Invokes `CertificateService.check()`, attaches result to model, re-renders dashboard |

### 3.3 Dashboard (Thymeleaf)

Server-side rendered HTML using Thymeleaf and Bootstrap 5. No separate frontend build process or Node.js toolchain required. The template conditionally renders the result card only when a check has been performed.

---

## 4. Data Flow — Phase by Phase

### Phase 1 (Current) — Manual Check
```
User enters URL → POST /check → CertificateService → result displayed in dashboard
```

### Phase 2 (Planned) — Excel Bulk Processing
```
User uploads .xlsx → Apache POI parses rows → CertificateService processes each URL
→ Results table displayed → Optional export
```

### Phase 3 (Planned) — Enterprise Database Integration
```
Oracle DB (service registry) → fetch active webservice list
→ CertificateService processes each endpoint
→ Results written to PostgreSQL (audit log, history, alerting)
```

---

## 5. Security Posture

- **No outbound dependency on third-party scanners.** All TLS handshakes are performed locally by the JVM using the system's default `SSLContext`.
- **Internal network capability.** Because the tool runs as a local Spring Boot process, it has the same network access as the host machine — enabling audits of services behind a VPN or firewall.
- **Credentials** for Oracle and PostgreSQL are externalised to `application.yml` and should be injected via environment variables or a secrets manager in production. No credentials are committed to source control.

---

## 6. Key Design Decisions

| Decision | Rationale |
|---|---|
| Raw `SSLSocket` over HTTP client | Directly mirrors TLS negotiation; works on non-HTTP services; no dependency on URL scheme |
| Thymeleaf over React/SPA | Zero frontend build tooling; single deployable JAR; appropriate for an internal SRE tool |
| Dual datasource (Oracle + PostgreSQL) | Oracle is the existing enterprise registry (read-only); PostgreSQL is the operational audit store (read-write) |
| `DataSourceAutoConfiguration` excluded at startup | Prevents startup failure until datasource beans are manually wired in the `config/` package |
