# Elysium Safety — Architecture Decision Records (ADRs)
## Technical Decisions for Evidence, Territorial Analysis & Financial Intelligence

---

### ADR-001: Relational PostgreSQL Entity Graph over Dedicated Graph Databases

- **Status:** ACCEPTED
- **Context:** Modeling relationships between public institutions, contractors, corporate filings, and tenders requires graph queries (e.g., entity resolution, 1-to-2 hop supplier connections). A graph database (Neo4j, Memgraph) was considered.
- **Decision:** Use relational PostgreSQL tables (`economic_entities`, `entity_relationships`, `financial_observations`) with indexed B-Trees and recursive CTEs instead of introducing a dedicated graph database.
- **Rationale:**
  1. The existing platform relies on PostgreSQL / Supabase with battle-tested Row-Level Security (RLS) policies.
  2. ACID transactionality and cross-table foreign key constraints are mandatory to prevent orphan evidence.
  3. Relational joins and recursive CTEs easily satisfy 1-2 hop queries at initial scale without operational overhead or dual-write synchronization bugs.
  4. Migration to a specialized graph database will only be evaluated if production volume and query latency benchmarks prove necessity.

---

### ADR-002: Zero AI Authority over Evidence Promotion & Epistemic Transitions

- **Status:** ACCEPTED
- **Context:** Generative AI is capable of entity extraction, document summarization, and timeline construction. However, LLM models can hallucinate or be misled by prompt injection embedded in uploaded files.
- **Decision:** AI is strictly an **Analyst Assistant**, with **zero authority** to:
  - Promote an epistemic state (e.g., cannot promote `OBSERVED` to `AUTHORITATIVE`).
  - Grant permissions or authorize disclosure.
  - Modify original documents or custody hashes.
  - Generate a criminality score for any person or entity.
- **Rationale:** In forensic and institutional domains, truth is an empirical and legal determination. Human review and cryptographic proof are irreplaceable.

---

### ADR-003: Isolated Institutional Demo Navigation without Destructive Codebase Deletion

- **Status:** ACCEPTED
- **Context:** For the meeting with Costa Rican deputies, the product must be presented solely as a Citizen Security, Evidence, and Territorial Analysis platform. Showing automotive diagnostics, OBD scanners, or mobility dispatch would confuse institutional stakeholders. However, MEET / Elysium Vanguard OS has a strict rule in `AGENTS.md`: *"Todo en uno. Siempre a más, nunca a menos. Never remove already-integrated modules."*
- **Decision:** Introduce a dedicated **Institutional Presentation Mode** (`PresentationMode.INSTITUTIONAL_DEPUTIES`) in Android navigation. When launched in this mode, the app presents exclusively Elysium Safety (Reporting, Evidence, Timeline, Territorial Map, Integrity Verification) without displaying automotive or rides modules. The complete product codebase, contracts, migrations, and features remain 100% intact and functional.
- **Rationale:** Satisfies the institutional presentation requirement with zero destructive regression or scope loss.

---

### ADR-004: Conservative Deterministic Anomaly Engine with Mandatory Alternative Hypotheses

- **Status:** ACCEPTED
- **Context:** Identifying irregularities in public procurement requires algorithmic checks. Black-box ML models create unexplainable false positives.
- **Decision:** Build deterministic, explainable rules (e.g., `ProcurementConcentrationRule`). Every rule must:
  1. Provide a step-by-step mathematical explanation.
  2. Require a minimum data completeness threshold (otherwise output `INSUFFICIENT_DATA`).
  3. Explicitly evaluate and document **legitimate alternative explanations** (e.g., authorized direct procurement, emergency decree, proprietary supplier).
- **Rationale:** Ensures investigative rigor and prevents defamation or premature conclusions.

---

### ADR-005: Strict Rejection of Personal Wealth as an Indicator of Illegality

- **Status:** ACCEPTED
- **Context:** Citizens or observers frequently report individuals with luxury vehicles, jewelry, or expensive real estate as "suspicious."
- **Decision:** Implement `FinancialObservationReviewPolicy` as an immutable gate: visible wealth observations alone are categorically rejected and result in `INSUFFICIENT_EVIDENCE`. Reaching `ELIGIBLE_FOR_HUMAN_REVIEW` strictly requires at least two independent, verified, documentary sources demonstrating specific lawful discrepancies.
- **Rationale:** Prevents citizen surveillance, privacy violations, and class bias; aligns with the constitutional presumption of innocence.
