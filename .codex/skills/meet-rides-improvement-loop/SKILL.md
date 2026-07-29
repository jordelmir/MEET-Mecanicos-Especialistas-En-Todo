---
name: meet-rides-improvement-loop
description: Audit, improve, test, document, and version the MEET Elysium Vanguard Viajes experience for passengers and drivers. Use for recurring ride-quality cycles, ride hotspot discovery, map/search/dispatch/fare/safety/profile/wallet changes, ride regression checks, or preparing a ride-focused PR and release without inventing live data.
---

# MEET Rides Improvement Loop

Run one bounded, evidence-gated improvement per cycle. Keep passenger and
driver behavior coherent, but never mix unrelated cleanup into the same PR.

## Cycle

1. Read `AGENTS.md`, product vision, roadmap, and
   `references/quality-contract.md`.
2. Run `scripts/audit-rides.sh` from the repository root. Use its compact
   output instead of repeatedly scanning the tree.
3. Select one high-impact problem supported by code, tests, logs, lint, or
   on-device evidence. Prefer correctness and safety over cosmetics.
4. Write or extend a failing test for business logic before changing it.
5. Implement the smallest coherent improvement. Preserve offline truth and
   honest unavailable/error states.
6. Run `scripts/verify-rides.sh fast`. Fix failures before expanding scope.
7. Update the release note and any affected contract documentation.
8. Run `scripts/verify-rides.sh full`. Inspect the diff for secrets, fabricated
   data, accidental brand references, and unrelated edits.
9. Commit, push, open a focused PR, wait for required checks, and merge only
   when green. Never force-push shared branches.
10. Record what the cycle taught in the next release note. Improve this skill
    only when a repeated command or failure pattern has been proven useful.

## Selection order

Choose the first evidenced issue in this order:

1. trip ownership, idempotency, boarding PIN, money, commission, privacy;
2. passenger/driver safety, cancellation, verification, incident evidence;
3. real place search, route clarity, stops, map state, accessibility;
4. dispatch latency, lifecycle recovery, offline/error handling;
5. profiles, history, earnings/spend truth, support;
6. rendering performance and visual polish.

Do not claim production-grade realtime dispatch, billing, routing, or live
telemetry unless the required backend/provider and real-device evidence exist.

## Efficient evolution

- Keep `SKILL.md` short; put stable business rules in the reference.
- Extend audit patterns only after they found a real defect.
- Extend verification only with deterministic tests or reproducible gates.
- Never let scripts modify product code, merge PRs, or hide failures.
- Validate skill changes with the system `quick_validate.py` and execute both
  scripts after editing them.
