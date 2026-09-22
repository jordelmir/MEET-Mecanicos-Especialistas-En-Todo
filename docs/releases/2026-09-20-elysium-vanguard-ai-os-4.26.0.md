# Elysium Vanguard AI OS 4.26.0 — Safety, activity and brand delivery

**Date:** 2026-09-20
**Android package:** `com.elysium369.meet`
**Version:** `4.26.0` (`versionCode 59`)
**Delivery branch:** `sync/codex-mavis-antigravity-20260919-160346`

## Delivered scope

- The visible product name is **Elysium Vanguard AI OS** across Android resources, home and Pro headers, experience labels, onboarding, and the scanner visual asset.
- Elysium Safety expands the citizen-report path with location capture, staged evidence and SHA-256 custody data, an outbox upload worker, report withdrawal, public-projection map data, historical search, geographic fields, filters, case detail, accountability and observatory views.
- Safety map data follows the authority boundary: local records represent report intent; public map points, cases, timelines and claims originate from server projections. Private report coordinates and narrative are not published through the public projection.
- The Supabase migrations in this delivery add public Safety projection and search capability, reporter anonymity controls, authority integrity, trusted-owner boundaries, and the personal financial activity projection.
- **Mi actividad** now presents confirmed income, expenses, top-ups and balance grouped by account-owned activity and time period. Server financial projections remain authoritative.
- Rides retain durable command/outbox behavior and account ownership boundaries introduced with the integrated work.

## Security and privacy boundaries

- A report does not establish a fact, corroboration, publication or institutional response. Those states require authoritative Safety projections.
- The reporter remains anonymous in public Safety projections. Exact private GPS, contact information and private narrative are excluded from the public map contract.
- Withdrawal is an account-owned command. The client does not independently declare a public state transition.
- Sensitive/command-center access remains server-gated; client UI visibility is not treated as authorization.

## Database delivery

The following migrations are included and must be applied in their existing timestamp order:

- `20260918100000_safety_public_views.sql`
- `20260919053000_safety_operational_v2.sql`
- `20260919110000_safety_public_accountability_projection.sql`
- `20260919160821_safety_authority_integrity.sql`
- `20260920010000_safety_global_citizen_map_and_withdrawal.sql`
- `20260920020000_safety_geographic_timeline_search.sql`
- `20260920021000_safety_reporter_anonymity_boundary.sql`
- `20260920083000_exclusive_platform_owner_boundary.sql`
- `20260920090000_personal_financial_activity_v1.sql`

## Verification performed locally

- `npm run check:versions` — passed: Android/web 4.26.0, versionCode 59.
- `./gradlew --no-daemon --max-workers=1 :app:compileDebugKotlin` — passed.
- `./gradlew --no-daemon --max-workers=1 :app:assembleDebug` — passed.
- Android debug APK installed and launched on the connected device; `com.elysium369.meet/.MainActivity` resumed without a fatal-exception match in the inspected log window.
- `bash tests/parity/ci-verify.sh` is part of this delivery verification; its final result is recorded by the release commit/check workflow.

## Artifact

`Elysium-Vanguard-AI-OS-v4.26.0-debug.apk` is the installable debug artifact published with the GitHub release. It is intended for direct testing and is not represented as a Play-production artifact.
