# MEET / ELYSIUM — GLOBAL VEHICLE ECONOMY PLATFORM MASTER IMPLEMENTATION

## Architecture & Revenue Engine Specification

---

## 1. Executive Summary & Vision

MEET / Elysium Vanguard is the unified operating system for the global vehicle economy. Guided by the non-negotiable principle:
> **"Todo en uno. Siempre a más, nunca a menos. Al máximo nivel de la humanidad."**

Every feature, subsystem, screen, RPC, and data pipeline exists as part of a single, closed-loop economic flywheel:
```
Onboarding → Vehicle Passport → OBD / Telemetry → DTCs & Live Stream → 
AI Diagnostic Truth Engine → Repair Intent → Service Marketplace & Towing → 
Parts Matching (VIN-DTC-OEM) → Counter-Offers & CAS Assignment → 
Physical Verification → Certified Report (SHA-256 + QR) → 
Double-Entry Ledger Settlement → Vehicle Lifetime Provenance Moat → 
Warranty & Enterprise Fleet Retention
```

No capability is ever removed, degraded, hidden, or isolated. The platform unites consumer vehicle owners, mobile mechanics, specialized workshops, parts distributors, tow operators, fleet managers, and forensic certifiers into a self-reinforcing network.

---

## 2. The 11 Economic Engines

The platform operates across 11 synchronized economic engines:

1. **Acquisition Engine (`ACQUISITION`)**:
   - Universal onboarding, instant VIN barcode/OCR decoding, zero-barrier quick diagnostics, driver and technician pilot enrollment.
2. **Activation Engine (`ACTIVATION`)**:
   - ELM327 Bluetooth/BLE automated negotiation, real-time PID stream, interactive 3D digital vehicle twin (`VehicleTwinViewport`), HUD night projector.
3. **Value Engine (`VALUE`)**:
   - Real-time diagnostic reasoner, verified DTC code library, OEM repair procedure matching, live sensor freeze-frame capture.
4. **Retention Engine (`RETENTION`)**:
   - Immutable Vehicle Passport (`public.vehicle_events`), automated DVIR safety inspections, periodic maintenance reminders, service milestones.
5. **Subscription Engine (`SUBSCRIPTION`)**:
   - Tiered consumer and technician plans (`meet_free`, `meet_pro_monthly`, `meet_pro_yearly`, `meet_workshop_monthly`), monotonic entitlement authority via `billing_private.apply_verified_entitlement`.
6. **Marketplace Engine (`MARKETPLACE`)**:
   - Universal service requests (`public.universal_service_requests`), competitive mechanic bidding (`public.universal_service_offers`), VIN-DTC OEM parts compatibility search, tow dispatch.
7. **Transaction Engine (`TRANSACTION`)**:
   - Server-side authoritative pricing (`market_product_prices`), fail-closed electronic payment gates, 7-parameter provider capture, compare-and-swap (CAS) offer acceptance.
8. **Enterprise Engine (`ENTERPRISE`)**:
   - Multi-tenant fleet management (`public.organizations`, `public.organization_memberships`), role-based access control (`OWNER`, `ADMIN`, `DISPATCHER`, `DRIVER`, `AUDITOR`), pooled vehicle passports.
9. **Data Moat Engine (`DATA MOAT`)**:
   - Cryptographic provenance chain, cross-vehicle fault pattern learning, telemetry anomaly detection, forensic inspection records.
10. **Trust Engine (`TRUST`)**:
    - CSPRNG 6-digit boarding verification PINs with bcrypt storage, safe trip sharing projections, bilateral double-blind ratings, internal risk decision engine (`public.risk_decisions`).
11. **Platform Engine (`PLATFORM`)**:
    - MarketOS runtime (`public.mobility_markets`), fail-closed kill switches (`public.platform_kill_switches`), GDPR zero-trace account erasure, cross-runtime parity (TypeScript ≡ Kotlin).

---

## 3. Financial & Accounting Architecture

### Double-Entry Balanced Ledger Invariant
- All monetary movements are recorded as discrete debits and credits across `public.ledger_accounts` within an atomic `public.ledger_transactions`.
- **Zero-Sum Rule**: For every transaction $T$:
  $$\sum_{e \in \text{entries}(T)} \text{amount\_minor}(e) = 0$$
- **Integer Arithmetic Only**: All monetary values are represented as signed 64-bit integers (`BIGINT`) representing minor currency units (e.g. cents, centavos, céntimos). Floating-point representations (IEEE-754) are strictly prohibited.
- **Authority Separation**: Ordinary authenticated clients cannot write to `ledger_transactions` or `ledger_entries`. All settlements are executed server-side via `service_role` security definer procedures.

---

## 4. Diagnostic Truth Classification System

Under Section 50 of the platform standard, data claims are classified into 5 strict epistemic levels:

1. `PHYSICALLY_VERIFIED`: Confirmed by an authorized, verified physical inspector or certified sensor calibration with non-repudiable actor ID. **AI engines are strictly prohibited from generating this classification.**
2. `OBSERVED`: Directly measured by OBD-II hardware, telemetry dongles, or physical sensor bus.
3. `ESTIMATED`: Derived via validated mathematical or statistical models (e.g. urban routing heuristics, battery degradation curves).
4. `DERIVED`: Synthesized by rule engines, expert systems, or AI diagnostic reasoners.
5. `HYPOTHESIS`: Unconfirmed diagnostic possibilities or user-submitted symptom hypotheses awaiting physical verification.

If an AI engine or unverified actor attempts to submit a `PHYSICALLY_VERIFIED` claim, the system automatically sanitizes the claim to `DERIVED`.

---

## 5. Security & Isolation Model

- **Private Schemas**: All provider webhook ingestion, raw cryptographic secrets, and idempotency states reside in `billing_private` and `private_api`, inaccessible to `anon` and `authenticated`.
- **Row-Level Security (RLS)**: Enforced across 100% of public platform tables with zero-bypass policies for untrusted roles.
- **Tenant Isolation**: Organizations and fleet memberships enforce strict boundary checks via security definer helpers to prevent multi-tenant data leakage and RLS policy recursion.
- **Granular Kill Switches**: `platform_kill_switches` allows instantaneous fail-closed deactivation of markets, payment rails, services, or features without database migrations or code redeployment.
