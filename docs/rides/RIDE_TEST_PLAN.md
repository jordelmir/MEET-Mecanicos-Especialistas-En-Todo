# ELYSIUM MOBILITY OS — MASTER TEST PLAN & VERIFICATION MATRIX
**Status**: AUTHORITATIVE SPECIFICATION V1  
**Baseline SHA**: `2f3b4535d8fc9c82d4d718217afe73ab28b65a2c`  
**Governing Rule**: *No blind implementation. Test-first defect protocol. Never report a test that was not actually executed.*

---

## 1. Verification Hierarchy

```text
Level 1: PURE DOMAIN & INVARIANT TESTS (JVM Unit Tests, 0 I/O)
   ├── RideMoneyTest, RideCommissionPolicyTest, RideLifecycleTest
   ├── RideAutoMatchEvaluatorTest, RideFareEngineTest, RideBoardingPinTest
   └── RideEligibilityPolicyTest (Driver/Vehicle separation)

Level 2: CONCURRENCY & BARRIER RACES (Deterministic Multi-Threaded Tests)
   └── RideAcceptanceConcurrencyRaceTest (N=10 threads, CyclicBarrier, CAS verification)

Level 3: INTEGRATION & REPOSITORY TESTS (Room SQLite In-Memory + Outbox)
   ├── RideLifecyclePersistenceContractTest (CAS updates, actor predicates)
   ├── RideOfferAcceptanceContractTest (RPC routing, zero direct DAO acceptance)
   ├── RideActorOwnershipContractTest (Session-bound vs device-bound identity)
   └── PlatformTrustCenterDeliveryContractTest (Evidence download, private storage)

Level 4: RUNTIME PARITY HARNESS (Cross-Runtime Exact Hashing)
   └── tests/parity/ci-verify.sh (TS ≡ Kotlin SHA-256 byte-for-byte exactness)

Level 5: STATIC & HYGIENE GATES (Audit Scripts & Lint)
   ├── .codex/skills/meet-rides-improvement-loop/scripts/audit-rides.sh
   ├── :app:verifyNoSecretsInSource
   └── :app:lintDebug
```

---

## 2. Core Invariant Properties Tested

| Invariant Property | Test Suite | Verification Method |
|---|---|---|
| **At Most One Winning Driver** | `RideAcceptanceConcurrencyRaceTest` | Barrier race across $N=10$ concurrent threads over 25 iterations. Asserts exactly 1 winner and $N-1$ conflicts. |
| **Double-Entry Ledger Balance** | `RideDoubleEntryLedgerTest` | Verifies $\sum \text{Debits} == \sum \text{Credits}$ for all reservations, captures, and refunds. |
| **5% Platform Commission Policy** | `RideCommissionPolicyTest` | Asserts exact 5% calculation without rounding drift in CRC and USD. |
| **Boarding PIN Inviolability** | `RideBoardingPinTest` | Verifies that ride cannot transition to `PASSENGER_ONBOARD` without valid 4-digit PIN verification. |
| **Actor-Bound Mutations** | `RideActorOwnershipContractTest` | Proves passenger cannot execute driver commands and driver cannot accept passenger offers. |
| **Fail-Closed Anonymous Sessions** | `RideLifecycleTest` | Missing or blank principal ID causes immediate rejection; no fallback to `"SYSTEM"`. |
| **Monotonic Version Protection** | `RideLifecyclePersistenceContractTest` | Stale remote version updates cannot overwrite newer local states. |

---

## 3. Automated Verification Execution Guide

### Fast Gate (< 2 minutes)
```bash
bash .codex/skills/meet-rides-improvement-loop/scripts/verify-rides.sh fast
```
Executes all 68 unit and concurrency test suites in `com.elysium369.meet.ride.*`.

### Parity Gate (< 30 seconds)
```bash
bash tests/parity/ci-verify.sh
```
Verifies exact cross-runtime hash equality between TypeScript and Kotlin diagnostic/report engines.

### Audit Gate (< 10 seconds)
```bash
bash .codex/skills/meet-rides-improvement-loop/scripts/audit-rides.sh
```
Scans for non-atomic offer acceptance, unguarded mutations, brand leaks, and LOC churn.
