# Elysium Safety — Data Protection, Whistleblower Safety & Disclosure Policy
## Governance & Institutional Export Controls

> **Non-negotiable Invariant:**  
> **A citizen report or investigative dossier is never a public conviction.**  
> **Private residential addresses, informant identities, and uncorroborated allegations must never leak into public projections.**

---

## 1. Information Classification Tiers

| Tier | Classification | Storage & Encryption | Access Scope | Public Exposure |
|---|---|---|---|---|
| **Tier 0** | Whistleblower Identity & Source Metadata | Client-side encrypted, isolated identity vault | Air-gapped from analysts; requires multi-key quorum | **STRICTLY ZERO** |
| **Tier 1** | Unverified Reports & Raw Submissions | Server private bucket, RLS tenant-isolated | Reporting user + designated intake officer | **STRICTLY ZERO** |
| **Tier 2** | Active Investigative Workspaces | PostgreSQL RLS organization-scoped, encrypted at rest | Assigned investigators & verified newsroom team | Restricted export only |
| **Tier 3** | Redacted Case Briefs & Evidence Packs | Two-person signed manifest (Ed25519) | Competent public authority or editorial board | Controlled release |
| **Tier 4** | Public Territorial Aggregations | Read-only projection (`safety_public_points`) | Open public map (no authentication required) | Spatial jitter; aggregated counts only |

---

## 2. Whistleblower Protection & Metadata Stripping

To guarantee source safety and comply with international human rights standards:
1. **EXIF & Geolocation Stripping:** When multimedia evidence (images, audio, video) is ingested:
   - EXIF tags (camera model, serial number, focal length, software version) are stripped immediately.
   - Exact GPS coordinates embedded in media files are removed before storage.
   - File modification timestamps are normalized to the ingestion date.
2. **Spatial Jitter in Public Map:**
   - Any public point shown on the map applies a minimum radius blur ($250\text{ m} - 500\text{ m}$).
   - No marker is ever placed directly over a private residence or confidential source location.
3. **No Network Fingerprinting:**
   - Client IP addresses are not stored with report content; logs are dissociated via cryptographic hashing and short retention windows ($7\text{ days}$).

---

## 3. Two-Person Rule for Sensitive Disclosures

No single investigator, analyst, or administrator can authorize external disclosure of an investigative case file:
- **Requirement:** Minimum of two distinct authenticated roles must sign the disclosure:
  $$\text{Signer}_A (\text{Lead Investigator}) \neq \text{Signer}_B (\text{Editorial / Legal Reviewer})$$
- **Digital Signatures:** Both signatures must be cryptographically verifiable Ed25519 signatures bound to the case manifest hash.
- **Audit Receipt:** A tamper-evident `AccessAuditEvent` is recorded with the legal disclosure justification.

---

## 4. Retraction, Correction & Contestability Workflow

Elysium Safety enforces epistemic humility and right-of-reply:
1. **Contested Claims:** Any named entity or party may submit contradictory evidence.
2. **Never Silent Deletion:** If a claim or hypothesis is refuted by official evidence, the record is transitioned to `REFUTED` or `SUPERSEDED`. The contradictory evidence is permanently attached to the audit trail.
3. **Right to Rectification:** Where factual error is proven, updates are propagated with explicit change manifests.
