# AGENTS.md — MEET project rules

This file is read by every AI agent (Mavis, Codex, Google Antigravity,
future) that works
on the MEET / Elysium Vanguard repo. It captures the non-negotiable
operating principles for this product.

---

## Operating principle (Jor, 2026-07-04)

> **"Todo en uno. Siempre a más, nunca a menos. Al máximo nivel de la
> humanidad."**

MEET is **one product**, not a catalogue of competing features. Every
section exists because it serves the others:

```
Onboarding → Vehicle → OBD → DTCs → Repair guide → Mechanic
  → Parts (VIN-DTC compatibility) → Quote (antifraud)
  → Repair → Pre/Post Scan → Certified PDF + QR + hash
  → Vehicle history → Warranty
  → Share (client / shop / fleet / pre-purchase / insurance)
```

### What this means in practice

- The two V2 specs (Reports + Parts Marketplace) are **not
  alternatives**. They are complementary and both ship together.
- When a session asks "should we pick A or B?", the answer is
  **"A + B + sync"**, every time.
- When reducing scope, only defer **new** features, never remove
  already-integrated ones.
- The "MVP" bar here is: a forensic inspector can verify a report
  independently with just the QR and SHA-256. If not, it's not done.

### Reference docs (read these before any non-trivial change)

- `docs/PRODUCT_VISION.md` — the full principle + closed-loop diagram.
- `docs/PRODUCT_OS_ROADMAP.md` — product rules (no fake data, guided
  vs dense mode, etc.).
- `docs/architecture/CROSS-RUNTIME-PARITY.md` — TS ≡ Kotlin hash
  contract. **Any byte-exact contract MUST go through the parity
  harness.**

### Cross-agent coordination

- Three agents may work in parallel: **Codex (ChatGPT)**,
  **Mavis (mE)** and **Google Antigravity**, each on its own branch or
  worktree.
- **All advances ship together in one APK.** Never pick one side.
- The `~/.mavis/skills/codex-mavis-sync/scripts/sync.sh --auto`
  script audits active worktrees and merges only when a real union is
  pending and the current tree is clean. Use it before any
  `assembleDebug` if the worktree has dirty files or side branches.
- Conflict resolution: **union, not pick-one**. If multiple agents
  touched the same file, preserve the coherent union of their changes.

### Hard safety rules (apply to every commit)

1. **Never invent data.** Allowed honest phrases:
   - "OBD no disponible"
   - "Dato no capturado"
   - "Pendiente de validación"
   - "Confianza limitada"
   - "Requiere prueba física"

2. **Never mark compatibility `EXACT`** without VIN + OEM evidence
   OR closed tuple (brand, model, year, engine, OEM) OR visual
   confirmation. The proper phrasing is "compatibilidad probable,
   requiere confirmar por VIN/OEM/foto/conector/medidas".

3. **Never allow silent edits** on a signed certified report. Either
   create a new version with a chained hash, or transition to
   `VOIDED`.

4. **Never put full VIN / plate / phone** in a QR. Only the 6-field
   minimal payload (report_id, integrity_hash, vehicle_id,
   generated_at, report_type, verifier_url).

5. **Never force-push to `main`.** No `--force` against shared
   branches. If a rebase is needed, use `--no-ff` merge instead.

6. **Never break the cross-runtime parity** between TS and Kotlin.
   `bash tests/parity/ci-verify.sh` must stay green.

---

## V2 specs (currently in flight, both will ship)

| Spec | Path | Status |
|---|---|---|
| Reports PDF Certificados + Vehicle History | `docs/reports/V2-CERTIFIED-PDF-AND-HISTORY.md` | spec committed, awaiting Codex impl |
| Parts Marketplace (VIN-DTC compatibility) | `docs/parts-marketplace/V2-TECHNICAL-MARKETPLACE.md` | spec committed, awaiting Codex impl |

Neither is "more important" than the other. They are sequential in
implementation order (Reports first, then Parts), but both are in
scope for the next APK.

---

## What to do at the start of any work session

1. Read `docs/PRODUCT_VISION.md` (this principle).
2. Read `docs/PRODUCT_OS_ROADMAP.md` (product rules).
3. Check `git status` and `git branch -a` to see what the other agent
   has touched.
4. If dirty or side branches exist: `bash ~/.mavis/skills/codex-mavis-sync/scripts/sync.sh --auto`
5. Only then start the new task.

If the new task conflicts with the principle ("should I pick A or B?"),
**stop and re-read** this file before continuing.

---

## Skill Orchestration — World-Class Engine

The `world-class-orchestrator` skill is the **master brain**. It coordinates
150+ skills into a unified execution engine. Every agent must load it first.

### Skill Loading Protocol

1. **First load**: `world-class-orchestrator` — decides which skills to activate
2. **Tier 1 (always)**: `elite-engineering`, `elite-debugging`, `elite-compilation`
3. **Tier 2 (domain)**: Load based on task type:
   - Android/Kotlin → `elite-android-dev`, `android-mac-bridge`
   - Web/React → `fullstack-weaver`, `ui-craft-master`
   - API/Backend → `api-architect`, `data-architect`
   - DevOps/Deploy → `system-conductor`, `elite-cicd-deploy`
   - QA/Testing → `elite-qa-testing`, `elite-code-review`
   - Debugging → `elite-debugging`, `systematic-debugging`
   - Refactoring → `elite-refactoring`, `code-architect`
4. **Tier 3 (specialist)**: Load as needed:
   - Security → `security-champion`, `security-hardening`
   - Performance → `performance-tuner`, `performance-budget`
   - Database → `database-optimizer`, `database-designer`
   - API Design → `api-designer`, `api-contract-guardian`
   - CI/CD → `ci-cd-architect`, `ci-cd-pipeline`
   - Observability → `observability-starter`, `monitoring-designer`
   - Frontend → `frontend-design`, `interface-design`, `ux-scientist`
   - Testing → `test-automation`, `test-strategy-master`, `qa-engine`

### Parallel Agent Orchestration

When multiple agents work simultaneously:

1. **Agent A (Primary)**: Loads `world-class-orchestrator` + domain skills
2. **Agent B (Specialist)**: Loads domain-specific skills for focused work
3. **Agent C (Reviewer)**: Loads `elite-code-review` + `security-champion` for cross-review

### Cross-Review Protocol

Before any merge to `main`:
1. Agent writes code with `elite-engineering` workflow
2. Second agent reviews with `elite-code-review` skill
3. Security audit with `security-champion` if touching auth/data
4. Build verification with `elite-compilation` + `elite-qa-testing`
5. Only then: merge via `sync.sh --auto` or manual `--no-ff` merge

### Skill Location Registry

| Location | Purpose |
|---|---|
| `~/.config/opencode/skills/` | Core opencode skills (auto-loaded) |
| `~/.agents/skills/` | Elite skills (150+ domain skills) |
| `~/.mavis/skills/` | Mavis-specific skills |
| `~/.claude/skills/` | Claude-specific skills |
| `~/.gemini/skills/` | Gemini-specific skills |

All skill directories are scanned automatically. Skills are triggered by
task matching (description-based). The orchestrator decides which to load
based on task complexity and domain.
