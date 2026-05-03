# CertificateManager Architecture & Design Document

## 1. Vision & Scaling
In a modern enterprise, certificate outages are rarely caused by a single domain. They are caused by "hidden" webservices and internal APIs. CertificateManager is designed to bridge the gap between static Excel inventories and live service reality.

## 2. Data Flow Architecture

### A. The "Source of Truth" (Oracle)
The system connects to an **Oracle SQL Developer** environment (the Enterprise Registry). It queries a list of active webservices, endpoints, and internal load balancers.

### B. The Processing Engine (Spring Boot)
1. **Fetch:** Pulls URL targets from Oracle or a user-uploaded Excel sheet.
2. **Execute:** The `CertificateService` initiates a TLS handshake. It bypasses the HTTP layer entirely, working directly at the Socket layer to extract the `Certificate[]` chain.
3. **Parse:** Extracts the `NotAfter` date, `SubjectAlternativeNames`, and `IssuerDN`.
4. **Compare:** Calculates the delta between `currentDate` and `expiryDate`.

## 3. Security Posture
By running this locally within a VM or on a private network, CertificateManager can audit internal-only services that public tools (like SSL Labs) cannot see.
