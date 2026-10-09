# Elysium Safety — Implementation Plan
## Citizen safety, evidence integrity, territorial analysis and public-interest financial intelligence

**Plan status:** Initial implementation baseline; not a production approval.
**Baseline branch:** main
**Baseline commit:** f1eb6de4aeb7d1441b4cf02ec3ef0df5e7216e63
**Plan branch:** feat/elysium-safety-intelligence-plan
**Product rule:** add capabilities; do not remove existing Elysium Vanguard AI OS modules.

## 1. Product boundaries

Elysium Safety is the institutional-facing safety and evidence experience. For the deputies' presentation, show only safety reporting, evidence provenance, timelines, controlled territorial views and the institutional-collaboration proposal. This is a presentation/demo scope, not a mandate to delete automotive, mobility, marketplace, learning or other existing product capabilities.

Global Financial Intelligence is a separate, future investigative domain within the broader Safety product. It must use lawfully accessible sources, source-backed relationships, explainable signals and mandatory human review. The citizen-safety pilot must not depend on an unfinished financial-intelligence subsystem.

Non-negotiable distinctions:
- REPORT != FACT.
- CLAIM != EVENT.
- EVENT != CASE.
- HASH INTEGRITY != TRUTH.
- CORRELATION != CAUSATION.
- ANOMALY != CRIME.
- VISIBLE WEALTH != EVIDENCE OF ILLEGALITY.
- LOCAL INTENT != REMOTE SUCCESS.
- AI OUTPUT != AUTHORITATIVE STATE.

## 2. Baseline already found in the repository

The inspected main commit contains:
- Safety Constitution and Authority Map at docs/safety/SAFETY_CONSTITUTION.md and docs/safety/AUTHORITY_MAP.md.
- A threat model at docs/safety/THREAT_MODEL.md.
- Android Safety surfaces and repositories beneath android/app/src/main/kotlin/com/elysium369/meet/safety/.
- Scientific-forensic models and custody code beneath android/app/src/main/kotlin/com/elysium369/meet/safety/science/.
- Safety-specific PostgreSQL migrations, including scientific-core, custody parity, institutional gateway, public map/projection, publication and retention changes.
- PostgreSQL and Android/TypeScript safety test suites and CI workflows.
- An existing financial aggregate sandbox at tools/safety-financial-sandbox-demo.ts and lib/safety/fi_analytics/FinancialCorrelationSandbox.ts. The demo labels its data SYNTHETIC and productionVerified=false; it is not evidence of a production source adapter.
- A financial-observation safety gate added in this branch at android/app/src/main/kotlin/com/elysium369/meet/safety/science/domain/FinancialObservationReviewPolicy.kt, with negative-invariant tests. This is a domain policy contract, not an end-to-end financial-intelligence feature and is not yet wired to an ingestion/UI workflow.

These are repository observations. They do not prove that live Supabase deployment, every RPC/RLS boundary, external-source ingestion, institutional integration or production-scale behavior has been executed.

## 3. Workstream A — reconcile the source of truth

1. Pin each audit and test result to an exact Git SHA, build artifact hash, test command and environment.
2. Reconcile docs/audits/ELYSIUM-SAFETY-PRODUCTION-ATTESTATION.md with ELYSIUM_SAFETY_STATUS.json and the currently checked-out commit.
3. Do not overwrite status fields with remembered results. Distinguish CODED, UNIT_TESTED, INTEGRATED, DEPLOYED, E2E_VERIFIED, PHYSICALLY_VERIFIED, INDEPENDENTLY_VERIFIED, UNKNOWN and NOT_EXECUTED.
4. Run the actual safety CI, Android unit tests, PostgreSQL migration/RLS tests and device checks from a controlled worktree; record logs and commit identities.
5. Keep production NO-GO wherever mandatory live infrastructure, backup/restore or external delivery evidence remains NOT_EXECUTED.

## 4. Workstream B — institutional safety experience

Audit then complete these vertical flows against the current contracts:
1. Citizen creates a structured report with occurrence time, recording time, location accuracy, source type and explicit unknowns.
2. Original media/document is associated with stable IDs, content integrity metadata and provenance.
3. The report is persisted locally as local intent and synchronized through the existing command/outbox path where applicable.
4. UI shows pending/offline/error until the server returns an authoritative receipt.
5. Reviewer can distinguish the reporter's claim from source-verified and authority-confirmed records.
6. Timeline links events, claims, source records and evidence without elevating hypotheses automatically.
7. Map projection respects publication authority and privacy-safe location granularity.
8. Corrections, withdrawals, disputes and new contradictory evidence preserve the required history.
9. Institutional sharing is purpose-limited, permission-checked, auditable and recipient-specific.

Do not infer that a flow is complete because a screen, entity, migration or README entry exists. Trace the full path from UI through repository/outbox, RPC, server authority, database policy and resulting projection.

## 5. Workstream C — evidence integrity and privacy

- Reuse existing canonical hashing, Ed25519, Merkle, source-provenance and cross-runtime contracts where verified.
- Preserve originals and mark derivatives/transcripts/summaries as derived artifacts.
- Treat a hash as a byte-integrity check, not a truth verdict.
- Treat a signature as a verifiable relationship to a key, not proof of the real-world claim.
- Do not claim legal admissibility or court certification without jurisdiction-specific validation.
- Keep reporter identity, exact sensitive locations and private source material out of public projections.
- Audit EXIF, filenames, thumbnails, embedded metadata, exports and AI-generated summaries for leakage.
- Test RLS and authorization at the database/API boundary, not only in Android UI.

## 6. Workstream D — financial intelligence as a separate extension

### Allowed initial source classes
- Public procurement and awards.
- Legally accessible company/corporate registries.
- Official audit findings.
- Published judicial/regulatory records.
- Official sanctions lists.
- Authorized datasets and lawfully submitted source material.

### Initial architecture
Start with existing Safety scientific entities, claim/evidence relations and PostgreSQL relational tables. Do not create duplicate truth-state enums, parallel custody systems or a graph database until benchmarked needs justify it.

Each source record must retain issuer, source URL/identifier when applicable, publication/retrieval times, jurisdiction, collection method, permitted use, raw/derived distinction, content hash where available and known coverage limitations.

Each relationship must be source-backed or explicitly marked as an unresolved hypothesis. Similar names, addresses, surnames or visual wealth must not automatically merge identities.

### Signal policy
Implement deterministic, explainable rules before machine learning. A signal must include rule/version, input references, time window, formula, data-coverage requirements, false-positive modes, alternative explanations and review status. It may be eligible for human review; it must not output a criminality verdict or automatically trigger public disclosure.

The first implemented guard in this branch ensures that visible-wealth observations alone cannot qualify a financial observation for investigative human review. It requires lawful, verified, discrepancy-bearing documentary sources from at least two independent source groups to return the limited disposition ELIGIBLE_FOR_HUMAN_REVIEW. This is only a policy primitive until wired, reviewed and tested end to end.

### AI boundary
AI can extract candidate entities, summarize records, identify contradictions and suggest competing hypotheses. Every factual statement must link to sources. Model output cannot modify original evidence, grant access, approve disclosure or promote epistemic states.

## 7. Workstream E — Costa Rica pilot

Start with a bounded public-procurement case study using lawfully accessible authentic records, not a fabricated corruption case.

Acceptance sequence:
1. Import one real public award with origin and retrieval metadata.
2. Resolve the company using a reliable identifier; ambiguous matches remain unresolved.
3. Link a second public source only when the relationship can be demonstrated.
4. Run one deterministic signal only if required fields and coverage are present.
5. Present source records, missing data, alternative explanations and evidence contradicting the hypothesis.
6. Generate a restricted case bundle with hashes/references and review history.
7. Have an independent second reviewer reproduce the result.
8. Demonstrate that an unauthorized user cannot read or export the restricted case.
9. If no anomaly is supported, report no supported signal; do not invent a finding.

## 8. Workstream F — acceptance tests

Required negative and integration tests:
- Wealth/lifestyle-only observation cannot create a criminal finding, public alert or illicit-wealth inference.
- Missing, unauthorized or unverified source material cannot satisfy the evidence gate.
- One source copied by multiple outlets is not multiple independent corroboration.
- Entity name similarity never silently merges two legal entities.
- Every graph edge resolves to a source or is explicitly hypothetical.
- A model-generated relationship cannot bypass human review.
- An offline report cannot show a remote receipt.
- Failed RPCs cannot mutate authoritative UI state.
- Unauthorized tenants cannot read, modify, enumerate or export another tenant's private case.
- Public maps/exports do not leak exact sensitive locations or source identity.
- Corrections and contradictions remain auditable.
- Hash mismatch is detected, while a matching hash is never described as proving truth.
- Existing mobility, automotive, marketplace, learning, parity, migrations and Safety regression checks remain intact.

## 9. Phased execution and release gates

| Phase | Deliverable | Exit condition |
|---|---|---|
| 0. Baseline | Exact-head audit and reconciled status | Evidence linked to current SHA |
| 1. Policy contracts | Truth/provenance/wealth-only negative gate | Unit tests pass in CI |
| 2. Safety vertical slice | Report → evidence → server receipt → timeline/map projection | Integration tests prove authority and privacy |
| 3. Institutional review | Restricted sharing, audit, correction/withdrawal | Authorization and RLS tests pass |
| 4. One source adapter | Real authorized procurement records | Reproducible imports and provenance tests |
| 5. Relationship model | Temporal, source-backed entity links | Identity-resolution adversarial tests pass |
| 6. One deterministic signal | Explainable result and alternative hypotheses | Rule tests and independent replication pass |
| 7. Android workspace | Case/source/timeline/review screens | Device tests; offline and failure states verified |
| 8. AI support | Source-grounded summaries and contradiction discovery | Prompt-injection and human-review tests pass |
| 9. Pilot | Limited institutional/journalistic evaluation | Participants, legal basis, metrics and incidents documented |
| 10. Production | Operations, monitoring, backups, recovery, release attestation | No mandatory gate remains NOT_EXECUTED |

Do not skip a phase by relaxing prior gates. Do not run live destructive tests against production. Use staging, explicitly authorized test accounts, and synthetic fixtures for adversarial cases.

## 10. Monetization and governance

Validate buyer demand before forecasting revenue. Candidates:
- Team subscriptions for investigative newsrooms.
- Licenses for auditors, integrity organizations and eligible public institutions.
- Corporate due-diligence workspaces using lawful source data.
- Procurement monitoring and authorized source/API integrations.

Never sell private source identities, restricted case files, unverified accusations or paid suppression of findings. Contracts must state purpose limitation, access boundaries, retention, incident response, correction process and data-processing responsibilities.

## 11. Institutional presentation boundary

The deputies' demo and one-page brief should expose only Elysium Safety functions relevant to incident reports, evidence, provenance, timeline, privacy-preserving territorial views and the proposed pilot. Do not navigate into automotive, mobility, marketplace or other unrelated modules. This is not a destructive product scope reduction.

Show each claim using one of three categories:
- DEMONSTRATED NOW — accompanied by reproducible evidence.
- REQUIRES PILOT/INSTITUTIONAL INTEGRATION — not yet confirmed in the target environment.
- FUTURE EXTENSION — including global financial intelligence unless a real source-backed workflow is implemented and verified.

## 12. Change-management requirements

- Work on a feature branch; never push directly to protected main.
- Review AGENTS.md, product vision, Safety Constitution, Authority Map and threat model.
- Keep migrations additive, versioned and compatible; do not reimplement existing tables without schema evidence.
- Include tests with each code change.
- Provide changed-file summary, exact test commands, results, unverified gates and residual risks.
- A written plan or a successful compile is not proof of production readiness.
