# ADR 0003 — Certified Reports: hash chain + offline-first

**Status:** Accepted
**Date:** 2026-07-04
**Deciders:** Jor, Mavis
**Supersedes:** —

---

## Context

Jor's spec for the certified-reports section is the first deliverable
that *makes money* in MEET. A scanner impresses; a signed, hash-chained,
QR-verifiable report is what a customer (or a fleet, an insurer, a
notary, a workshop) pays for.

The acceptance criteria include:

* Per-vehicle integrity chain (each report embeds the previous one).
* SHA-256 over the report's content, computed client-side.
* No "reparado" claim without a post-snapshot or explicit manual
  confirmation.
* Reports survive offline: we generate the hash, sign, and store the
  PDF locally; sync to Supabase is best-effort.
* Privacy toggles must hide VIN / plate / location.
* No invented data: if OBD is offline, the report MUST say so.

The codebase already has a Kotlin `DiagnosticSnapshot` that computes a
`hashSha256` over the snapshot content. The web side does not — yet.
Without a unified hash, the web and Android would produce different
chain roots for the same vehicle. That breaks the entire value
proposition.

---

## Decision

PR 4 (this ADR's child PR) ships:

1. Five Supabase tables (`certified_reports`, `report_evidence`,
   `diagnostic_snapshots`, `repair_actions`, `report_signatures`)
   plus four enums.
2. A trigger that **blocks silent mutation** of a SIGNED report — any
   edit must re-hash AND move to `VOIDED`/`SHARED`/`EXPORTED`.
3. `lib/reports/types.ts` mirroring the Kotlin contract field-for-field.
4. `lib/reports/hash.ts` with a deterministic canonicalization + Web
   Crypto SHA-256.
5. `lib/reports/generate.ts` with five builders (Pre/Post/Repair/
   Peritaje/DVIR) + `finalizeDraft` + `applySignature` + chain
   verifier.
6. Vitest coverage: 41 tests pinning determinism, chain integrity,
   and the spec's anti-fraud rules.

PRs 5, 6, 7 will land on top.

---

## Trade-offs

### Why not centralize hashing in Supabase (RPC)?

* The spec demands offline-first. If hashing needs a server round trip,
  the moment we lose connectivity we lose the ability to sign.
* Client-side hashing is also the only way to keep the `deviceIdHash`
  out of the network path.
* SHA-256 is a constant-time operation. Even on a mid-range phone, the
  full finalize + sign takes < 5ms.

### Why a custom canonicalization (not `JSON.stringify`)?

Object key order is unspecified in JavaScript. `JSON.stringify({a:1,b:2})`
and `JSON.stringify({b:2,a:1})` produce different bytes. The web and
the Kotlin side would disagree. We canonicalize to sorted-keys /
no-whitespace / explicit-typed shape, pipe that into SHA-256, and
document the format. This is the smallest amount of code that gives
us byte-exact reproducibility.

### Why not commit the PDF to Supabase in PR 4?

PDF generation is a separate concern. PR 5 wires it. PR 4 establishes
the data, the hash, and the signature so PR 5 can focus on layout
without worrying about integrity.

### Why a per-vehicle chain instead of a global chain?

A global chain would force every report to wait for the previous one
to sync. Per-vehicle chains are independently auditable and let the
fleet owner pull *their* vehicle's history offline.

### Why the trigger blocks silent mutation, not the application code?

The trigger runs at the database, where it cannot be bypassed by an
admin tool, an edge function, or a SQL session. The application could
be wrong; the trigger is the last line of defense.

---

## Consequences

* All signature/hash work happens client-side. The server is a store,
  not a co-signer.
* The chain is "trust the device that signed" — the deviceIdHash is
  the disambiguator when a single user signs from multiple devices.
* A future PR can introduce notary-style third-party signing without
  changing the data model.
* When the Android side starts emitting reports, both sides produce
  identical `integrityHash` for identical content. The chain spans
  platforms.

---

## Re-evaluate when

* The hash algorithm needs to change (e.g. post-quantum): revisit the
  canonicalization format and provide a migration path for legacy
  reports.
* Notary / third-party signing becomes a product requirement: add a
  `notarySignatures` table and a separate chain.
* Reports get large (> 1MB of evidence): the integrity hash strategy
  needs Merkle-tree batching.
