# ELYSIUM MOBILITY OS — GRADUAL ROLLOUT & CANARY DEPLOYMENT PLAN
**Status**: AUTHORITATIVE SPECIFICATION V1  
**Baseline SHA**: `2f3b4535d8fc9c82d4d718217afe73ab28b65a2c`  
**Governing Rule**: *Every migration requires rollback thinking. If safe rollback is impossible, stop and redesign.*

---

## 1. Controlled Rollout Wave Stages

```text
 Stage 0: SHADOW MODE
    - New routing / matching engines run in background.
    - Outputs compared against existing baseline for latency and fairness.
    - Zero user-visible side effects.
              │
              ▼
 Stage 1: INTERNAL DOGFOODING (Staff & Registered Beta Drivers)
    - 50 trusted drivers, 100 staff riders in San José Central.
    - Physical field execution on real Android devices (HONOR, Xiaomi, Pixel).
    - Daily review of battery, GPS drift, and crash breadcrumbs.
              │
              ▼
 Stage 2: 5% CANARY ROLLOUT
    - 5% of active traffic in Heredia / Alajuela corridors.
    - Automatic rollback if p95 latency $> 500 \text{ ms}$ or error rate $> 0.1\%$.
              │
              ▼
 Stage 3: 25% CONTROLLED ROLLOUT
    - Expanded GAM coverage.
    - Monitored matching acceptance rate, cancellation rates, and driver earnings.
              │
              ▼
 Stage 4: 100% PRODUCTION DEFAULT
    - Full cutover.
    - Legacy code pathways deprecated and scheduled for contraction.
```

---

## 2. Hard Rollback Triggers & Automated Safe Stops

Immediate rollback to previous stable APK / backend RPC occurs if:
1. **Double Assignment Detected**: Any ride enters `ASSIGNED` with multiple distinct driver confirmations.
2. **Ledger Imbalance**: Any journal transaction evaluates to $\sum \text{Debits} \ne \sum \text{Credits}$.
3. **Ghost Rides**: Location telemetry persists after trip transition to `COMPLETED` or `CANCELLED`.
4. **Crash Spike**: App crash-free rate drops below $99.5\%$.
5. **Unauthorized Mutation**: Any RPC succeeds with unauthenticated or mismatched `auth.uid()`.

---

## 3. Rollback Data Contract

- **Backward Compatibility**: All database schema changes use `EXPAND-FIRST`. Older APK versions continue reading from compatible columns.
- **No Data Loss on Rollback**: In-flight rides initiated under the new release are flagged `LEGACY_FALLBACK_DRAIN`, allowing drivers to finish ongoing trips while routing subsequent requests to stable handlers.
