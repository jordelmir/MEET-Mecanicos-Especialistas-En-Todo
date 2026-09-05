# ADR 0014: Tow Vehicle Custody & Canonical Evidence Attestation

- **Status**: Accepted & Implemented (Commit `48a4056c`)
- **Date**: 2026-09-05
- **Deciders**: Principal Software Architect, Staff Android Engineer, Security & Legal Systems Lead

---

## Context

Towing operations involve taking physical custody of a customer vehicle. Disputes regarding pre-existing damage versus transit damage present a significant operational and legal liability.

Previous implementations lacked a verified custody checkpoint pipeline or relied on arbitrary unverified string hashes.

## Decision

1. **Custody Checkpoints**:
   - Introduce `TowCustodyCheckpoint` with distinct lifecycle steps: `PRE_LOAD_INSPECTION`, `LOADED_SECURED`, `DESTINATION_ARRIVAL`, `POST_UNLOAD`, `DELIVERED`.
2. **Canonical Evidence Authority**:
   - Rather than creating a redundant `TowEvidence` aggregate, tow custody directly references canonical `EvidenceAttestation` instances managed by the global `evidence` authority.
   - Transitioning `TowJob` to `LOADED` strictly requires valid cryptographic SHA-256 evidence digests (`requiredEvidenceSha256`).
3. **Multi-Angle Inspection**:
   - Mandatory capture of front, rear, left, right photos, odometer reading, and customer sign-off prior to winching or flatbed loading.

## Consequences

- Full forensic defensibility and chain of custody.
- Conforms to the core law: *MORE CAPABILITY, FEWER COMPETING AUTHORITIES*.
