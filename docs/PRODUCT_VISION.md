# MEET / ELYSIUM — Visión de Producto

**Status:** Active principle (Jor, 2026-07-04; expanded 2026-09-07)
**One-liner:** *"Todo en uno. Siempre a más, nunca a menos. Al máximo nivel de la humanidad."*

---

## North Star

> **ELYSIUM shall progressively make the world's useful knowledge learnable,
> human capability discoverable and developable, real-world needs computable,
> and economic exchange easier to coordinate, execute and verify.**

```
ANY HUMAN
+
ANY LEGITIMATE NEED
+
ANY LEARNABLE CAPABILITY
+
ANY LAWFUL PRODUCT OR SERVICE
        ↓
     ELYSIUM
        ↓
LEARN / FIND / CREATE / PROVIDE / BUY / SELL / HIRE / WORK
        ↓
VERIFIED REAL-WORLD OUTCOME
```

### The Civilizational Loop

```
LEARN → REMEMBER → ACT → EARN → VERIFY → IMPROVE → LEARN AGAIN
```

---

## Platform Architecture

ELYSIUM is not one app. It is a **Human Operating Platform** with five
conceptual layers:

```
┌───────────────────────────────────────────────┐
│            ELYSIUM COGNITIVE OS               │
├───────────────────────────────────────────────┤
│ KNOWLEDGE │ MEMORY │ LEARNING │ CAPABILITY    │
├───────────────────────────────────────────────┤
│             FORGE REALITY ENGINE              │
├───────────────────────────────────────────────┤
│ SERVICES │ JOBS │ COMMERCE │ MOBILITY │ B2B   │
├───────────────────────────────────────────────┤
│ PAYMENTS │ LEDGER │ IDENTITY │ TRUST │ DATA   │
└───────────────────────────────────────────────┘
```

Three conceptual engines power this:

| Engine | Purpose |
|--------|---------|
| **ELYSIUM** | Cognitive and orchestration control plane: identity, intent, knowledge, memory, context, evidence, state, risk, permissions, history |
| **FORGE** | Spatial representation, visualization, simulation engine for any domain |
| **MEET** | Human/business/economic fulfillment network: services, commerce, mobility, logistics |

---

## Constitutional Invariants

Three invariants that **MUST NEVER be broken**:

### 1. KNOWLEDGE ≠ COMPETENCE ≠ CREDENTIAL

A completed course is not a demonstrated skill.
A demonstrated skill is not a professional license.
A simulation is not a physical verification.

### 2. DEMAND SIGNAL ≠ GUARANTEED INCOME

The platform may show that demand exists for a capability.
It must NEVER promise employment or income.

### 3. PLATFORM COORDINATION ≠ CONTROL OF THE HUMAN

ELYSIUM augments. It does not own. Data portability, explicit consent,
revocable sharing, encrypted sensitive data, user-controlled memory,
auditability, least privilege, deletion controls — by design.

---

## Constitutional Priority Order

```
TRUTH > CORRECTNESS > SAFETY > SECURITY > PRIVACY > DATA INTEGRITY
> FINANCIAL INTEGRITY > USEFUL HUMAN OUTCOME > REAL REVENUE
> RETENTION > MARKETPLACE LIQUIDITY > GROWTH > SCALABILITY
> GLOBALIZATION > COSMETICS
```

A lower priority NEVER overrides a violated higher-order invariant.

---

## Domain Zero — The Automotive Closed Loop

MEET began here and this remains its most hardened domain:

```
Onboarding
   ↓
Vehículo activo + perfil (usuario / mecánico / taller / flota)
   ↓
Scanner OBD → DTCs / telemetría / salud predictiva
   ↓
Guía de reparación → mecánico / taller
   ↓
Repuesto compatible (Parts Marketplace) ← cross-check con VIN/DTC
   ↓
Cotización → antifraude → aceptación
   ↓
Reparación ejecutada → evidencia antes/después
   ↓
Pre-Scan + Post-Scan → Reporte PDF Certificado
   ↓
Historial técnico del vehículo → garantía → verificación con QR + hash
   ↓
Share: cliente / taller / flotilla / compra-venta / aseguradora
   ↓
[loop] DVIR / mantenimiento / siguiente servicio
```

**Cada paso bloquea al siguiente si le falta evidencia.** No se puede
pedir un repuesto sin DTC o sin pieza identificada. No se puede firmar
un Post-Scan sin foto-antes/foto-después. No se puede exportar un PDF
sin hash verificable.

---

## Domain One — Learning OS (Costa Rica 2026)

Costa Rica's 2026 MEP national curriculum is the **first authoritative
dataset and proving ground** for the Universal Learning Engine.

```
AUTHORITATIVE CURRICULUM
        ↓
CURRICULUM COMPILER
        ↓
KNOWLEDGE GRAPH
        ↓
LEARNER DIGITAL TWIN
        ↓
PERSONAL LEARNING FRONTIER
        ↓
ADAPTIVE TEACHING (Socratic AI)
        ↓
FORGE EXPERIENCES
        ↓
MASTERY ENGINE (FSRS)
        ↓
VERIFIED SKILL EVIDENCE
```

Course Zero: **Matemática 1.º — Costa Rica 2026** (official MEP monthly
distribution).

Economic Bridge: **7.º Artes Industriales — Fontanería** (LEARN → FORGE →
SKILL → Elysium Services).

---

## Universal Economic OS

The same fulfillment kernel that powers mechanics, towing, and mobility
generalizes to:

```
HUMAN NEED → INTENT → SERVICE TYPE → PROVIDER MATCH →
QUOTE → JOB → EXECUTION → EVIDENCE →
PAYMENT → VERIFIED OUTCOME → HISTORY / REPUTATION
```

35+ service verticals across 12 domains are cataloged. The architecture
must prove universality by running at least TWO materially different
domains (automotive + plumbing) through the same `ServiceIntent →
ServiceJob → Outcome` pipeline without destroying domain-specific
invariants.

---

## "Todo en uno" means

1. **All specs coexist.** Reports + Parts + Services + Learning +
   Mobility + Property — they are not alternatives. They are
   complementary and all ship together.

2. **Siempre a más.** When a new section arrives, it adds to the tree.
   Never replaces what already works.

3. **Nunca a menos.** When reducing scope, only defer NEW features.
   Never remove already-integrated ones.

4. **Al máximo nivel de la humanidad.** If a forensic inspector can
   verify a report independently with just the QR and SHA-256, it's
   at the right level. If not, it's not done.

---

## How "máximo nivel" looks concretely

- **Antifraude**: used part without photo → rejected. EXACT quote
  without VIN → downgraded to HIGH with warning.
- **Compatibilidad**: never "guaranteed compatible", always
  "compatibilidad probable, requiere confirmación por VIN/OEM/foto".
- **Offline-first**: mechanic in a basement with no signal can sign
  a report. Syncs when connectivity returns.
- **Trazabilidad**: every DTC has a complete journey from scan to
  part purchase to post-scan confirmation.
- **Independencia verificable**: external inspector with QR can verify
  hash without MEET account, internet, or installation.
- **Education truth**: AI-generated explanation → GENERATED.
  OBD measurement → MEASURED. MEP curriculum → AUTHORITATIVE.
  Student self-report → REPORTED. Simulation → SIMULATED.

---

## Refs

- `docs/ASTRA_V6_PROTOCOL.md` — the complete 85-section engineering
  protocol governing the platform's evolution.
- `docs/PRODUCT_OS_ROADMAP.md` — product rules (no fake data, guided
  vs dense mode, etc.).
- `docs/reports/V2-CERTIFIED-PDF-AND-HISTORY.md`
- `docs/parts-marketplace/V2-TECHNICAL-MARKETPLACE.md`
- `AGENTS.md` — cross-agent coordination rules.
