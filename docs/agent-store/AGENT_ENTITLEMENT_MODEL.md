# Agent Entitlement Model & Authority Verification

**Standard:** Master Order Omega (§37, §38, §39, §91)  
**Status:** Implemented & Verified (v4.26.1)

---

## 1. Entitlement Hierarchy & Verification

In Elysium, capability execution is partitioned by risk and domain authority:

```
                  ┌──────────────────────┐
                  │    USER PRINCIPAL    │
                  └──────────┬───────────┘
                             │
                             ▼
                  ┌──────────────────────┐
                  │ AGENT POLICY ENGINE  │
                  └──────────┬───────────┘
                             │
            ┌────────────────┴────────────────┐
            ▼                                 ▼
   [Free Capabilities]               [Premium Capabilities]
   • EVAIR Core                      • Master Mechanic
   • Navigation                      • Emissions Specialist
   • Teach Mode                      • SRE / Fleet Commander
            │                                 │
     (Auto-allowed)                 (Entitlement Verified)
                                              │
                              ┌───────────────┴───────────────┐
                              ▼                               ▼
                      [Active License]              [Missing / Unlicensed]
                      • Capability runs              • Denied by Policy
                      • Real domain truth            • Redirect to Agent Store
```

---

## 2. Invariants

1. **Local State Cannot Forge Entitlements**:
   Tampering with local shared preferences to spoof entitlements fails authoritative server RPC validation.
2. **Deterministic Fallback**:
   Missing entitlements yield an explicit `AgentResult.EntitlementRequired(entitlement, storeDeepLink)` response. The system explains what specialized knowledge is needed rather than executing degraded actions silently.
3. **Audit Trail**:
   All capability validations and policy decisions record cryptographic timestamps and execution context generations for auditing and tamper prevention.
