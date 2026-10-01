# Safety privacy impact assessment

Status: engineering assessment and proposed controls, 2026-09-30. Not legal
approval. The controller, jurisdiction-specific lawful basis, contact channels,
processor contracts and cross-border safeguards require owner/counsel sign-off
before a government or public production pilot.

## Purpose and data flow

Citizen intent → encrypted local staging → authenticated private intake →
private original storage → server byte hash and custody → source/claim review →
independent reviewer/publisher → minimized public projection. Intake is an
allegation, not an established crime. A verified hash proves byte integrity,
not truth, consent, origin or guilt.

## Inventory and access

| Data | Purpose | Boundary | Primary risk |
|---|---|---|---|
| Narrative, exact GPS, source relationship | Private intake/review | Owner RLS and restricted server functions | Retaliation, identification |
| Original media, declared/server hash | Evidence and custody | Private bucket, immutable object/verification ledgers | Victim/minor disclosure |
| Authentication principal and device verdict | Ownership, abuse resistance | Private trust records, bounded challenge | Re-identification, false trust inference |
| Institutional evidence/reference digest | Documented accountability | Private events, two-person publication | Unsupported accusation of inaction |
| Public cell, delayed counts | Public research | Coarse area ≥25km, small-cell suppression | Singling out, differencing |
| Machine identity, resource scope, export audit | Institutional interoperability | OAuth issuer/audience/client grants, optional gateway mTLS | Bulk or purpose-incompatible access |

## Mitigations and residual risks

Public cases are independently reviewed; withdrawn projections are removed from
Room and archived privately. Reporter IDs, raw narrative and exact GPS never
enter public projections. Byte mismatch is sticky quarantine, revokes supporting
publication and requests reevaluation. Anonymous repeated reports cannot become
independent source clusters. Institutional access has scope and resource checks
and append-only audits; clients never receive database/service credentials.

Coarsening alone cannot eliminate re-identification. Review combinations of
dates, public titles and source URLs; prevent rare cells and repeated narrow
queries from exposing victims. Mesh advertises voluntary device presence and
can expose traffic metadata; it does not claim anonymity, forward secrecy or
production E2EE before independent protocol review.

## Required approval record

Record named controller, lawful basis per data class, consent exceptions,
retention periods, victim/minor procedure, subject-rights response process,
incident owner and processor agreements. Run an external privacy/security
assessment and a backup/restore drill with deletion/hold behavior. Public gates
stay closed until this record and deployment validation exist.
