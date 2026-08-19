# MEET 4.20 Architecture Baseline — Proof-Carrying Automotive Service Authority Kernel

**Date**: 2026-08-19  
**Target Architecture**: MEET 4.20  
**Runtime Baseline**: `versionName = "4.17.0"`, `versionCode = 45`  
**Room Database Version**: `57`  
**Supabase Migration Head**: `20260819000000_repair_v2_authoritative_state_machine.sql`  
**Cross-Runtime Parity**: TS ≡ Kotlin Cryptographic Hash Verified (100% OK)  
**Verification Level**: `ARCHITECTURE_CANDIDATE_UNVERIFIED_HARDWARE`

---

## 1. Operating Charter & Invariant Principles
1. **ONE FACT → ONE AUTHORITY**: Every domain truth (repair resolution, provider status, transaction validity) has exactly one authoritative engine.
2. **ONE STATE MACHINE → ONE CONTRACT**: State transitions between client and server are derived from single-source deterministic schemas.
3. **ONE MONEY MODEL → ONE CURRENCY**: All financials strictly operate in ISO-4217 minor units via canonical `Money`.
4. **ONE PHYSICAL LINK → ONE OWNER**: OBD transports are coordinated under a single lifecycle manager with generational leases.
5. **UNKNOWN → DENY**: Any unrecognized enum, unverified credential, or missing proof fails closed.

---

## 2. Multi-Dimensional Verification Matrix

| Dimension | Verification Level | Gate / Authority |
| :--- | :---: | :--- |
| **Source Integrity** | `SOURCE_VERIFIED` | CI Static Guards & Git Secret Auditing |
| **Software Suite** | `SOFTWARE_VERIFIED` | 840+ Unit Tests & Cross-Runtime Parity Suite |
| **Device Execution** | `DEVICE_VERIFIED` | Physical Android ADB Runtime & Interactive Navigation |
| **Vehicle Hardware** | `PENDING_PHYSICAL_CORPUS` | Live ECU Fixtures & Commercial Scanner Benchmarks |
| **Calibration Trust** | `PENDING_SIGNED_REVIEWED_DATASET` | Signed Capability Dataset Manifests |
| **Overall State** | `ARCHITECTURE_CANDIDATE` | Full Production Validation Pending Hardware Conformance |

---

## 3. P0 / P1 Ledger & Status
- **Vanguard Events SQL Schema**: `RESOLVED` (Canonical `meet_emit_vanguard_event_v2` helper + exact column bindings).
- **Supabase Migration Conformance CI**: `RESOLVED` (`.github/workflows/supabase-conformance.yml`).
- **Repair Verification Authority**: `RESOLVED` (`RepairStateEngine` unified with strict partition invariants `required == cleared ∪ remaining` & `cleared ∩ remaining == ∅`).
- **Fail-Closed Enum Defaults**: `RESOLVED` (`RepairState.UNKNOWN`, `ProviderType.UNKNOWN`, `ServiceVertical.UNKNOWN`).
- **Optimistic Concurrency & Idempotency Equivocation**: `RESOLVED` (`p_expected_version` & duplicate key detection in `transition_repair_work_order_v1`).
