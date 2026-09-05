# FULFILLMENT AUTHORITY MAP V2

**Law:** *"ONE FACT → ONE CANONICAL AUTHORITY → MULTIPLE SAFE PROJECTIONS"*  
**Principle:** *"MORE CAPABILITY, FEWER COMPETING AUTHORITIES"*

---

## 1. Authority Matrix

| Fact / Aggregate | Canonical Authority | Single Authoritative Writer | Safe Readers | Local Cache / Outbox |
|---|---|---|---|---|
| **Tow State & Lifecycle** | Tow Aggregate Authority | `TowCommandRepository` (Client / Offline) → Tow Backend RPC (Server) | Customer app, Provider app, Admin console, Activity screen | `tow_jobs` Room Table |
| **Tow Unit Verification** | Provider/Identity Authority | Server Provider Verification Service | Tow Dispatch, Customer UI | Local `TowUnit` snapshot (read-only) |
| **Tow Unit Capabilities** | Provider Registry Authority | Certified Rig Inspection Authority | Tow Dispatch Engine | Read-only cache in `tow_units` |
| **Ride State & Fare** | Ride Domain Authority | Ride Command Handler / State Engine | Passenger app, Driver app, Dispatch | `ride_requests` Room Table |
| **Driver Availability** | Driver Session Authority | Provider Availability Service | Dispatch Engine, Driver UI | `DriverAvailabilityState` StateFlow |
| **Custody Evidence** | Cryptographic Evidence Authority | Forensic Evidence Ingestion Service | Legal Audits, Insurers, Tow Job Timeline | Room Evidence Tables / SHA-256 metadata |
| **Financial Quote** | Pricing Engine Authority | Server Pricing Service / `RideFareEngine` | Customer UI, Driver UI | Read-only `FareQuote` / `TowQuote` |
| **Payment Authorization** | Payment Gateway Authority | Payment Ingestion / Ledger Service | Fulfillment Projections | `FulfillmentPricing.AuthorizedAmount` |
| **Financial Settlement** | Financial Ledger Authority | ERP / Double-Entry Ledger Service | History, Invoices, Tax Authority | `FulfillmentPricing.FinalSettlement` |
| **Vehicle Identity & Telemetry** | Vehicle Twin / OBD Authority | Dongle Ingestion Pipeline + Active Selection | Diagnostics, Tow, Repair, RIR Reports | `vehicles`, `active_vehicle_selections` |
| **Principal Identity** | Identity Authority (`ActivePrincipalKernel`) | Auth Service / Biometric Token Service | All Modules (read-only scopes) | `ActivePrincipalProvider` |
| **Operational Geo Location** | Live Telemetry / GPS Authority | Device Location Hardware via `LocationClient` | Active Trip Partner (Scoped during active state ONLY) | Transient `StateFlow<LocationSample?>` (NEVER (0,0)) |

---

## 2. Invariant Rules Against Split Authorities

1. **Zero Dual Mutators on Tow**:
   - `ObdViewModel` MUST NOT write directly to `TowTruckDao`.
   - All mutations pass strictly through `TowCommandRepository.executeAction(...)` with atomic DB CAS.
2. **Zero Inferred Capabilities**:
   - A `TowUnit` NEVER acquires capabilities from the customer's `requiredCapabilities`. Capabilities are strictly queried from verified provider inventory.
3. **Zero Inferred Verification**:
   - Rehydrated units or assigned operators do not automatically become `isVerified = true`. Verification is bound to active cryptographic attestations.
4. **Zero Fallback Financial Authorization**:
   - `authorizationId` is NEVER generated as a synthetic string (`"AUTH_..."`). If a payment authorization has not been granted by Payments, `pricing` projects `Quote` or `EstimatedRange`, NOT `AuthorizedAmount`.
5. **Zero Custody Hash as Ledger Attestation**:
   - Vehicle custody hashes attest to physical condition, NEVER to monetary ledger balance.
