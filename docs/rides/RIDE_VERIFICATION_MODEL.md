# ELYSIUM MOBILITY OS — VERIFICATION & ELIGIBILITY MODEL
**Status**: AUTHORITATIVE SPECIFICATION V1  
**Baseline SHA**: `2f3b4535d8fc9c82d4d718217afe73ab28b65a2c`  
**Governing Rule**: *Driver verified != Vehicle eligible. Never collapse them into a single boolean. Verification is reviewable, document-backed, and subject to expiration.*

---

## 1. Separation of Verification Truths

```text
       IDENTITY VERIFIED (Cédula / Passport / Biometric)
              │
              ▼
       DRIVER APPROVED (Background check + License + Trust Center Review)
              │
              ▼
       VEHICLE ASSIGNED (Vehicle linked to driver in ride_driver_vehicles)
              │
              ▼
       VEHICLE ELIGIBLE (Dekra Inspection valid + SOAT/Insurance valid)
              │
              ▼
       DRIVER AVAILABLE (Driver taps GO_ONLINE + Fresh GPS telemetry)
```

**Cardinal Invariant**: A driver with 100% verified identity and 5.0 star rating CANNOT go online if their vehicle's technical inspection (Dekra) or mandatory insurance (Marchamo/SOAT) is expired.

---

## 2. Driver Onboarding Evidence Requirements

Enforced by `PlatformTrustCenterGateway` and Supabase private bucket `trust-verification-evidence`:

| Evidence Item | Requirement | Validation Method | Expiration Handling |
|---|---|---|---|
| **Identity Document (Cédula/DIMEX)** | Front & Back photo | OCR + Operator Manual Review | Bound to document expiry date |
| **Driver's License (Licencia B1/B2)** | Front & Back photo | License class verification | Hard cutoff at expiration |
| **Hoja de Delincuencia** | Official Costa Rican Judicial Certificate | SHA-256 PDF checksum verification | Max 90 days validity from issue date |
| **Profile Photo (Selfie)** | Real-time camera capture (no gallery upload) | Liveness challenge | Annual re-verification |
| **Vehicle Title / Circulation Card** | Official Riteve/Dekra title | VIN + Plate match with Vehicle Domain | Annual verification |
| **Dekra Technical Inspection** | Official Dekra sticker & sheet | QR / Plate / Inspection certificate | Strict monthly expiration alert |
| **Mandatory Insurance (Marchamo/SOAT)** | Annual receipt | INS policy number check | Expiration on Dec 31 annually |

---

## 3. Passenger Trust Verification

To ensure driver physical safety:
1. **Phone Number Verification**: SMS OTP bound to carrier SIM.
2. **Passenger Identity Attestation**: Optional biometric / ID upload granting `TRUSTED_PASSENGER` tier.
3. **Behavioral Trust Score**: Derived from completion rate, punctuality, and ratings received from drivers.
4. **Safety Restrictions**: Unverified new passengers cannot request late-night rides (11 PM - 5 AM) in flagged high-risk zones without electronic payment pre-authorization.

---

## 4. Verification Lifecycle State Machine

```text
 ┌──────────────┐
 │    DRAFT     │ (User uploads documents)
 └──────┬───────┘
        │ SUBMIT (PlatformTrustCenterGateway.submit)
        ▼
 ┌──────────────┐
 │ UNDER_REVIEW │ (Operator reviews documents in Trust Center)
 └──────┬───────┘
        ├─────────────────────────────────┐
        │ APPROVE (MFA Admin)             │ REJECT (Missing/Blurry/Expired)
        ▼                                 ▼
 ┌──────────────┐                  ┌──────────────┐
 │   VERIFIED   │                  │   REJECTED   │
 └──────┬───────┘                  └──────────────┘
        │ Expiry reached / Incident triggered
        ▼
 ┌──────────────┐
 │   EXPIRED    │ / SUSPENDED
 └──────────────┘
```
