# EVAIR & Elysium Agent OS — Architecture Specification

**Standard:** Master Order Omega (§5–§26, §30–§33, §86–§87)  
**Status:** Implemented & Verified (v4.26.1)

---

## 1. Architectural Overview

EVAIR is the embodied digital intelligence of the Elysium platform. Rather than acting as a disconnected chatbot or synthesizing invisible fake taps, EVAIR is an embodied guide that operates strictly within verified domain boundaries:

```
┌────────────────────────────────────────────────────────┐
│                   HUMAN USER (Voice / Text)            │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│               JEV INTENT RESOLUTION ENGINE             │
│   • Calibrated confidence scoring (not raw LLM score)  │
│   • Prompt injection defense (sanitization & inertness)│
│   • Semantic entity resolution (e.g. "mi casa" → HOME) │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│                 AGENT PLAN COMPILER                    │
│   • Schema-validated typed steps                       │
│   • Strict rejection of unknown / arbitrary code       │
│   • Context generation tagging                         │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│                 AGENT POLICY ENGINE                    │
│   • Risk classification: READ_ONLY ... COMMITTING      │
│   • Confirmation policy: user approval for mutations   │
│   • Concurrency: abort if contextGeneration is stale   │
│   • Entitlement check: enforce licenses before action   │
│   • Idempotency: require 16-128 char stable tokens     │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│                    CAPABILITY OS                       │
│   • RideRequestCapability (RideApplicationService)     │
│   • MasterMechanicCapability (DTCs + Physical Proof)   │
│   • EmissionsSpecialistCapability (Strict Provenance)  │
│   • GuideCapability (Living Guide & Teach Mode)        │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│              AUTHORITATIVE DOMAIN GATEWAYS             │
│   • RideCommandBus (Room Outbox + Server RPC)          │
│   • EmissionsEngine / WaveformAutoDiagnostician        │
│   • Pre-ITV / DEKRA State Machines                     │
└────────────────────────────────────────────────────────┘
```

---

## 2. Core Invariants

1. **Constitutional Law**:
   > *"AI interprets. Policy authorizes. Domains enforce. Servers confirm. Evidence proves."*
2. **Zero Synthetic Business Truth**:
   Projections and dashboards never fabricate revenue, users, or metrics. Missing telemetries default strictly to honest zeros and `UNAVAILABLE`.
3. **Never Execute via Synthetic Taps**:
   Accessibility automation must never bypass domain authorization. In Teach Mode, EVAIR points, touches, and guides the human eye, but the human finger taps the screen.
4. **Stale Context Invalidation**:
   Every plan binds to a `contextGeneration` token. If the screen changes or user inputs modify state, the plan immediately aborts.
5. **Exactly-Once Committed Operations**:
   All financial or committing actions require stable idempotency keys. Duplicate callbacks or network retries converge to a single authoritative side effect.

---

## 3. Capabilities & Specialized Agents

| Agent | Tier | Entitlement | Primary Capabilities |
|---|---|---|---|
| **EVAIR Core** | Free | *None* | `guide.start_tutorial`, `ride.request` |
| **Master Mechanic** | Premium | `agent.master_mechanic` | `vehicle.read_dtc`, `vehicle.mode06`, `vehicle.fuel_trim` |
| **Emissions Specialist** | Premium | `agent.emissions_specialist` | `emissions.analyze_o2`, `emissions.pre_itv`, `emissions.catalyst` |

---

## 4. Verification & Gates

- `AgentCoreUnitTest`: Tests registry discovery, intent resolution, prompt injection defense, policy confirmation, stale context aborts, and entitlement enforcement.
- `EvairRideExactlyOnceE2ETest`: End-to-end integration test verifying "mi casa" place resolution, preview quote generation, explicit user confirmation, and exactly-once ride creation under duplicate callback storms.
- `NoSyntheticAuthorityTest`: Enforces zero synthetic metrics in command center and all role-based projections.
