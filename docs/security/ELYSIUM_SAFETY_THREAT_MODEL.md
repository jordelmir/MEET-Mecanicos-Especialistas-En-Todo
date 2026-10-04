# Elysium Safety — Threat Model (§80)

> **Version:** 1.0 · **Author:** System · **Review:** Required before production activation

---

## Scope

This threat model covers the Safety Scientific Evidence Platform.
It applies to ALL actors: citizens, researchers, operators, institutions,
government bodies, judiciary, law enforcement, and Elysium itself.

---

## Threat Registry

| ID | Threat | Likelihood | Impact | Mitigation | Status |
|---|---|---|---|---|---|
| **T1** | Evidence fabrication | HIGH | CRITICAL | SHA-256 content hash at capture, custody chain, Merkle checkpoints, Ed25519 signatures. Hash mismatch = INVALID. | ✅ Implemented |
| **T2** | Evidence alteration | HIGH | CRITICAL | Immutable custody chain with `previousEventHash` linking. `CustodyVerifier` detects any byte change. Adversarial tests in `CustodyVerifierTest.kt`. | ✅ Implemented |
| **T3** | Evidence deletion | HIGH | CRITICAL | `EvidenceRetentionState` — no delete, only TOMBSTONED/WITHDRAWN/DESTROYED_WITH_AUDIT. Destruction requires who/when/why/policy/authorization/hash. | ✅ Implemented |
| **T4** | Metadata manipulation | MEDIUM | HIGH | Canonical custody format with version prefix (`ELYSIUM-SAFETY-CUSTODY-V1`). Timestamp from device + server. Cross-verification via Merkle root. | ✅ Implemented |
| **T5** | Identity spoofing | MEDIUM | HIGH | Entity resolution requires explicit `IDENTITY_MATCH` evidence. No automatic name→person_id resolution. `ScientificEntity.kt` invariant. | ✅ Implemented |
| **T6** | Source collusion | MEDIUM | HIGH | `SourceIndependenceAnalyzer` groups sources by provenance. 10 copies of same document = 1 source. `IndependenceReason` enum requires explicit justification. | ✅ Implemented |
| **T7** | AI hallucination | HIGH | CRITICAL | AI produces `ClaimCandidate`, never `ScientificClaim`. `AssertionStateMachine` blocks AI from upward epistemic transitions. System prompt versioned in code. | ✅ Implemented |
| **T8** | Correlation abuse | MEDIUM | HIGH | `CausalEngine` separates temporal association from causality. `CausalStatus` enum prevents conflation. Tests in `ScientificAnalysisTest.kt`. | ✅ Implemented |
| **T9** | False accusation | HIGH | CRITICAL | Mandato absoluto: `EVIDENCE ≠ GUILT`. `AccountabilityAction.NonAction ≠ IllegalOmission`. Four-layer separation: FACT→INFERENCE→LEGAL→JUDICIAL. No `declareGuilt()` API exists. | ✅ Implemented |
| **T10** | Government abuse | MEDIUM | CRITICAL | All actors (including government, judiciary, police) subject to same RLS, audit trail, and state transition rules. Authority graph (`AuthorityRelation`) is evidence-based, not presumed. No special bypass for institutional actors. | ✅ Architecture enforced |
| **T11** | Researcher abuse | MEDIUM | HIGH | `HIGH_IMPACT` sensitivity requires dual review. `ConflictOfInterestDeclaration` mandatory. `PeerReview` cannot auto-elevate to AUTHORITATIVE. `BiasAssessment` required before publication. | ✅ Implemented |
| **T12** | Insider attack | LOW | CRITICAL | RLS: all scientific tables locked to `service_role`. No `anon`/`authenticated` access. Publication only via projection views. Feature gates all `false` by default. | ✅ Implemented |
| **T13** | Account compromise | LOW | HIGH | Per-user keys (future). Service-role isolation. No private keys in Supabase. Audit log for all state transitions (`SciStateTransitionEntity`). | ⚠️ Partial |
| **T14** | Database compromise | LOW | CRITICAL | Merkle checkpoints with Ed25519 signatures. Offline verification via `EvidenceCheckpointVerifier`. Witness checkpoint architecture (table created, logic pending). | ⚠️ Partial |
| **T15** | Publication manipulation | MEDIUM | HIGH | Publication lifecycle: DRAFT→PUBLISHED→CORRECTED|RETRACTED. No delete. `supersedesPublicationId` preserves editorial history. `ResearchPackageVerifier` (pending). | ⚠️ Partial |

---

## Universal Auditability (§81)

**Every actor class is subject to the same audit trail:**

```
citizen
researcher
Elysium operator
administrator
police
prosecutor
judiciary
government institution
```

The platform NEVER assumes institutional actors are trustworthy by default.
Authority is evidence-based (`AuthorityRelation` with `sourceEvidenceIds`).

---

## Mitigation Architecture

```
Evidence Capture
    → SHA-256 hash
    → Custody chain (previousEventHash linking)
    → Merkle checkpoint (periodic batch verification)
    → Ed25519 signature

State Transitions
    → AssertionStateMachine (formal allowed transitions)
    → AI blocked from upward transitions
    → Audit log (append-only, SciStateTransitionEntity)

Access Control
    → RLS (service_role only for scientific tables)
    → Public access ONLY via projection views
    → Feature gates (all false in production)

Scientific Integrity
    → Source independence analysis
    → Bias assessment
    → Falsification engine
    → Peer review (non-elevating)
    → Conflict of interest declarations
```

---

## Residual Risks

| Risk | Mitigation Gap | Priority |
|---|---|---|
| Device clock manipulation | Server timestamp comparison not enforced | P2 |
| Key rotation for signing | Not yet implemented | P2 |
| Witness checkpoint delivery | Tables exist, delivery logic pending | P1 |
| Offline verification CLI | `ResearchPackageVerifier` class pending | P2 |
| Per-user encryption keys | Architecture planned, not implemented | P3 |
