# Safety Authority Map

Every data point and action in MEET Safety has a defined authority.

| Data / Action | Authority | Notes |
|---|---|---|
| Draft local report | Android (Room) | Local intent only |
| Queued command | Room outbox | Durable local intent |
| Remote received | Server RPC | Authoritative receipt |
| Claim created | Server / reviewer | Not auto-created |
| Claim corroborated | Trust / reviewer process | Never auto-corroborated |
| Evidence digest | Server + stored original | Client hash is advisory only |
| Public projection | Publication authority | Server-side firewall |
| Public map point | Publication authority | Read-only projection |
| Correction applied | Moderation authority | Appended, never silent |
| Case milestone | Evidence-backed server event | Requires provenance |
| Exact reporter identity | Privacy domain | Never in public tables |
| Public map data | Read-only projection | From safety_public_points only |
| Independent source count | Trust methodology | ≠ account count |
| Publication decision | Publication firewall | Never automated by count |
| Case lifecycle | Reviewer authority | INTAKE → TRIAGE → ... |
| Institutional allegation | Reviewer + trust process | Requires corroboration |

## Principle

Every state transition must originate from an authoritative source.
Local state represents intent only. Intent is never truth.
