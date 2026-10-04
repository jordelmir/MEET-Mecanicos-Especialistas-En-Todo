# Elysium Safety Scientific Evidence Platform

## Architecture

```
Existing Safety Foundation (NOT TOUCHED)
        │
        ▼
  Safety Science Core (NEW — com.elysium369.meet.safety.science)
        │
        ├── domain/       — Value objects, enums, state machine
        ├── application/  — AI boundary, feature gates
        ├── data/         — Room entities + DAO
        ├── analysis/     — Hypotheses, falsification, causal engine
        ├── replication/  — Datasets, research runs, peer review
        ├── publication/  — Publication lifecycle
        └── provenance/   — Custody chain, Merkle tree, signatures
```

## Mandato Absoluto

**Elysium Safety jamás decidirá que una persona es culpable.**

```
EVIDENCE ≠ GUILT
CLAIM ≠ CONVICTION
CORRELATION ≠ CAUSATION
OMISSION ≠ CRIMINAL LIABILITY
AI OUTPUT ≠ FACT
PUBLICATION ≠ COURT JUDGMENT
```

## Four-Layer Epistemological Separation

```
FACT → SCIENTIFIC_INFERENCE → LEGAL_QUALIFICATION → JUDICIAL_DETERMINATION
```

These MUST NEVER be fused.

## P0 — Integrity Core

| Component | File | Status |
|---|---|---|
| EvidenceAssertionState | `domain/EvidenceAssertionState.kt` | ✅ |
| TruthState → Scientific mapping | `domain/TruthStateMapping.kt` | ✅ |
| AssertionStateMachine | `domain/AssertionStateMachine.kt` | ✅ |
| Safety Science Enums | `domain/SafetyScienceEnums.kt` | ✅ |
| Provenance Graph | `provenance/ProvenanceGraph.kt` | ✅ |
| Custody Chain + Verifier | `provenance/CustodyChain.kt` | ✅ |
| Merkle Tree + Signer | `provenance/MerkleTree.kt` | ✅ |
| Tests: CustodyVerifier | `test/.../CustodyVerifierTest.kt` | ✅ 43/43 |
| Tests: MerkleTree | `test/.../MerkleTreeTest.kt` | ✅ |
| Tests: StateMachine | `test/.../AssertionStateMachineTest.kt` | ✅ |
| Tests: TruthStateMapping | `test/.../TruthStateMappingTest.kt` | ✅ |

## P1 — Knowledge Graph

| Component | File | Status |
|---|---|---|
| Scientific Entity + Relations | `domain/ScientificEntity.kt` | ✅ |
| Scientific Claim + Temporal Scope | `domain/ScientificClaim.kt` | ✅ |
| Knowledge Events + Authority + Duty | `domain/KnowledgeGraph.kt` | ✅ |
| Accountability (Action/NonAction) | `domain/KnowledgeGraph.kt` | ✅ |
| Scientific Events (4 timestamps) | `domain/KnowledgeGraph.kt` | ✅ |

## P2 — Scientific Reasoning

| Component | File | Status |
|---|---|---|
| Hypotheses + Falsification | `analysis/ScientificAnalysis.kt` | ✅ |
| Source Independence Analyzer | `analysis/ScientificAnalysis.kt` | ✅ |
| Causal Engine | `analysis/ScientificAnalysis.kt` | ✅ |
| Bias Assessment | `analysis/ScientificAnalysis.kt` | ✅ |
| Counterfactual Scenarios | `analysis/ScientificAnalysis.kt` | ✅ |

## P3 — Research Engine

| Component | File | Status |
|---|---|---|
| Research Datasets (immutable) | `replication/ResearchEngine.kt` | ✅ |
| Research Runs (reproducibility) | `replication/ResearchEngine.kt` | ✅ |
| Replication Studies | `replication/ResearchEngine.kt` | ✅ |
| Peer Review (non-elevating) | `replication/ResearchEngine.kt` | ✅ |
| Research Publication lifecycle | `replication/ResearchEngine.kt` | ✅ |
| External Audit | `replication/ResearchEngine.kt` | ✅ |

## Data Layer

| Component | File | Status |
|---|---|---|
| Room Entities (19 tables) | `data/SafetyScienceEntities.kt` | ✅ |
| Room DAO | `data/SafetyScienceDao.kt` | ✅ |
| MeetDatabase registration | `MeetDatabase.kt` (v89) | ✅ |
| Supabase migration (21 tables) | `20261004130000_safety_scientific_core_v1.sql` | ✅ |

## Application Layer

| Component | File | Status |
|---|---|---|
| AI Boundary Interface | `application/ScientificAiAssistant.kt` | ✅ |
| Feature Gates (all false) | `application/SafetyScienceFeatureGates.kt` | ✅ |

## AI Rules

- AI produces `ClaimCandidate`, never `ScientificClaim`
- AI may transition to DISPUTED/CONTRADICTED/INSUFFICIENT_EVIDENCE only
- AI may NEVER elevate epistemic state
- System prompt is versioned in code

## Test Results

```
43 tests completed, 0 failed ✅
```
