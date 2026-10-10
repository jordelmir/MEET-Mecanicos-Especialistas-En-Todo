# Elysium Safety — Threat Model & Privacy Perimeter
## Global Financial Intelligence & Citizen Security Infrastructure

> **Non-negotiable Engineering Invariant:**  
> **Evidence ≠ Guilt. Claim ≠ Conviction. Anomaly ≠ Crime. A report is not proof.**  
> **Visible personal wealth alone is never evidence of illegality.**

---

## 1. Product Scope & Moral Red Lines

Elysium Safety is an investigative evidence, citizen security, and territorial analysis platform. It is **not** a civilian surveillance database, a neighborhood espionage tool, or an automated accusation generator.

### Prohibited System Behaviors (Architectural Blockers)
1. **No Wealth-Based Criminalization:** The system will never infer illicit enrichment, money laundering, or criminality solely from visible wealth (e.g., luxury vehicles, jewelry, real estate, lifestyle observations).
2. **No Neighbor Dossiers:** Citizens cannot compile private investigative dossiers on neighbors or arbitrary private citizens.
3. **No Uncorroborated Public Accusations:** A citizen report or private submission can never be published automatically as a confirmed crime or public alert.
4. **No Residential Targeting:** Exact residential coordinates of private citizens or reporters will never be projected onto public maps or shared exports.
5. **No AI Authority over Truth:** AI models cannot elevate an epistemic state, verify a claim, grant permissions, or alter original evidence.
6. **No Silent History Rewriting:** No administrator or user can silently edit or delete historical audit entries or evidentiary records.

---

## 2. Adversary Classes & Attack Scenarios

| Adversary | Capability | Primary Objective | Defense Mechanism |
|---|---|---|---|
| **Fabricated Accuser** | Submits fabricated documents or reports | Defame targets, manufacture fake scandals | Independent multi-source corroboration requirement; `INSUFFICIENT_EVIDENCE` gate; evidence hash check |
| **Coordinated Sybil Network** | Multiple colluding accounts submitting duplicate claims | Bypass single-source restrictions | Source group clustering; independent authority verification; account count $\neq$ source independence count |
| **Retaliatory Actor** | Correlates metadata to identify whistleblowers | Intimidate or harm informants | Strict separation of reporter identity; EXIF/GPS metadata stripping; spatial/temporal jitter |
| **Document Prompt Injector** | Injects adversarial instructions in PDFs/contracts | Hijack AI analysis to elevate claims or exfiltrate data | AI sandboxing; read-only prompt isolation; zero tool execution from document content; strict JSON schemas |
| **Compromised Insider / Rogue Admin** | Has administrative database access | Alter evidence, delete records, suppress findings | No admin god-mode; database append-only triggers; Ed25519 signatures; cryptographic Merkle audit logs |
| **Evidence Tamperer** | Modifies document bytes after submission | Invalidate or forge evidence in court | Canonical byte-level SHA-256 hashing; cross-runtime TS $\equiv$ Kotlin parity; hash mismatch detection |
| **Tenant Exfiltrator** | Malicious user in another organization | Access restricted case workspaces | Server-enforced Row-Level Security (RLS); zero client-side role assertion; tenant ID cryptographic binding |

---

## 3. Privacy & Whistleblower Protection Perimeter

### Ingestion vs Investigation vs Public Boundaries
```text
[ PRIVATE INGESTION ]
  - Whistleblower / Citizen Submission
  - Identity encrypted and isolated in privacy vault
  - Metadata stripped: EXIF, camera serials, precise micro-timestamps
       ↓
[ RESTRICTED WORKSPACE ]
  - Access restricted to authorized analysts / investigators
  - Case file requires explicit lawful purpose
  - Two-person rule required for sensitive disclosures
       ↓
[ PUBLIC / EXPORT PROJECTION ]
  - Filtered by Publication Firewall
  - Geographic points aggregated or rounded (no residential pins)
  - Zero private identities or raw whistleblower documents exposed
```

### Protection Against Document-Level Prompt Injection
Any document ingested into the financial intelligence or evidence engine (e.g., procurement PDFs, audit reports, scanned text) is treated as **hostile untrusted data**:
1. Ingested text is wrapped in immutable delimiter boundaries.
2. AI agents analyzing documents operate with zero write privileges.
3. Model outputs cannot trigger database mutations, role promotions, or state transitions without deterministic human validation.

---

## 4. Verification & Audit Posture

Every security-sensitive event produces an immutable `AccessAuditEvent`:
- **Actor ID** (authenticated server identity, never client-claimed).
- **Action** (`READ_RESTRICTED`, `EXPORT_CASE`, `STATE_TRANSITION`, `REDACTION_APPLIED`).
- **Target Record** & **Purpose Rationale**.
- **Cryptographic Receipt** (timestamp + SHA-256 parent hash chain).
