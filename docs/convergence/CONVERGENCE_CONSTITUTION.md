# CONVERGENCE CONSTITUTION — ELYSIUM VANGUARD & MEET

**Status:** AUTHORITATIVE & INVIOLABLE
**Effective Date:** 2026-09-25
**Version:** 1.0.0-GENESIS
**Authority:** Jor (Architect & Founder) & Google Antigravity + Mavis + Codex

---

## 1. The Core Law: "Todo en uno. Siempre a más, nunca a menos. Al máximo nivel de la humanidad."

MEET / Elysium Vanguard is **one sovereign product**, spanning diagnostic telemetry, ride hailing, marketplace commerce, technical field services, and human-agent interaction. Every component exists to strengthen the others in a closed, verifiable loop:

$$\text{Onboarding} \to \text{OBD Telemetry} \to \text{Forensic DTCs} \to \text{Certified Guide} \to \text{Marketplace Parts} \to \text{Antifraud Quotes} \to \text{Verified Repair} \to \text{Chained SHA-256 Certificate}$$

---

## 2. Seven Inviolable Truth Invariants

### Article I — Zero Synthetic Truth
Local device state represents **intent only**. Intent is never authoritative reality.
1. The UI and local Room database must never synthesize `status = "ACCEPTED"`, mark rides `IN_PROGRESS`, or simulate boarding PIN verification locally.
2. All state transitions must originate as durable outbox commands (`RideCommandType`), transition through the authoritative server RPC / PostgreSQL worker, and be projected back into Room with `serverVersion > 0` before updating UI state.
3. Enqueueing locally in the command bus is `Enqueued` (*"Solicitud enviada. Esperando confirmación autoritativa del servidor..."*), **never** synthetic server success.

### Article II — Strict Geocoding & Spatial Metrology
No arbitrary coordinates may ever be manufactured or guessed.
1. All unknown, ambiguous, or unresolvable place queries return `PlaceResolutionResult.NotFound`, never fallback coordinates (such as San José default coordinates `9.9333, -84.0833`).
2. When destination cannot be resolved with certainty, quote generation returns `null` and capability returns `PLACE_RESOLUTION_FAILED`.
3. Previews are marked `isHeuristicEstimate = true` with a versioned rate card (`CR_METRO_V3_2026`) and explicit time-to-live (`expiresAtEpochMs`).

### Article III — Production Outbox Infrastructure
1. In production (`ELYSIUM_ENV != "test"`), the server MUST wire `PostgresOutboxRepository` using `FOR UPDATE SKIP LOCKED` row-level locks and atomic lease tokens.
2. `InMemoryOutboxRepository` and no-op publishers are strictly prohibited in production and will trigger fatal startup validation errors.
3. Failed events transition to Dead-Letter Queue (`DLQ`) with full exception traces after retry budgets expire.

### Article IV — Single Audio Authority & Reactive Voice Bus
1. Exactly ONE operating system microphone capture channel is permitted. Duplicate `SpeechRecognizer` instances (`ERROR_RECOGNIZER_BUSY`) are banned.
2. Audio capture is managed centrally by `VoiceCommandManager`. Speech transcripts stream reactively over `VoiceInteractionBus.default.transcripts` to `EvairInteractionOrchestrator`.
3. All voice-driven UI actions execute idempotently. Rapid duplicate audio frames are deduplicated by hash/token, guaranteeing exactly-once side effects.

### Article V — Deterministic Semantic UI Control Plane
1. The user may invoke any visible interactive element using natural language:
   - `"SECCIÓN <nombre>"` / `"ENTRA A LA SECCIÓN <nombre>"`
   - `"SELECCIONA <nombre>"`
2. EVAIR executes the Instant Shift Motion:
   - Shrinks from normal scale (`1.0f`) to quantum micro-avatar (`0.18f`).
   - Emits particle bursts (`EvairQuantumBurstFx`).
   - Teleports to target button coordinates (`centerX`, `centerY`).
   - Pulses with accent glow (`EvairTargetHighlightFx`).
   - Dispatches the verified user callback.
   - Smoothly teleports back home to original pose and scale.
3. Ambiguous targets (`UiTargetResolutionResult.Ambiguous`) prompt a clarification dialog; the agent NEVER arbitrarily selects a sibling control.

### Article VI — Data Sensitivity & Secret Protection
1. UI controls marked `AgentUiSensitivity.SECRET` or `AgentTextFieldRole.SECRET` (PINs, passwords, cryptographic keys, CVV) strictly deny voice agent automated inspection or text injection.
2. Secret fields require physical biometric or explicit touch confirmation from the owner.

### Article VII — Cross-Runtime Parity & Forensic Immutability
1. Any byte-exact contract (reports, telemetry hashes, quote tokens) MUST pass `tests/parity/ci-verify.sh`. TypeScript and Kotlin implementations must yield byte-for-byte identical SHA-256 signatures.
2. Once a certified report is generated, silent edits are impossible. Any change creates a new chained version or transitions to `VOIDED`.
3. Diagnostic telemetry values must declare strict `EvidenceOrigin` (`MEASURED`, `PHYSICS_DERIVED`, `MODEL_ESTIMATED`) and `TruthClass`. An `UNKNOWN` reading must never be coerced to `0`, `PASS`, or `NORMAL`.

---

## 3. Enforcement & Release Gates
No release APK may be built or distributed unless:
1. `GoldenJourneyTruthSuite` passes with 100% success.
2. `AgentUiCoverageReleaseGate` verifies $\ge 90\%$ semantic control coverage and zero critical security violations across all core routes.
3. `ci-verify.sh` confirms cross-runtime parity between TypeScript, Kotlin, and PostgreSQL.
