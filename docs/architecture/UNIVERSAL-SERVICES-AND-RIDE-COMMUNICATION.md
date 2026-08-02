# Elysium Services + Ride Communication

## Shipped architecture

The physical, digital and hybrid service surface reuses the existing Elysium marketplace aggregate instead of creating a second disconnected product:

`request → offers → atomic acceptance → execution → payment state → evidence/history`

- `UniversalServiceCatalog` is a searchable ontology, not a screen full of hard-coded business logic. “Otro servicio” preserves extensibility.
- Requests record modality, domain, risk tier, currency and price in minor units inside the canonical metadata envelope while the existing local marketplace remains backward compatible.
- Providers register explicitly. New profiles start unverified; elevated/restricted work is labelled for stronger review.
- Acceptance reuses the existing transactional first-valid-claim path. A chat message cannot change the price, assignment or payment state.
- Payment labels are truthful: an accepted offer is not displayed as paid without an authoritative capture.

The Supabase migration introduces canonical `service_definitions`, `universal_service_requests` and `universal_service_offers` tables. The current Android compatibility adapter continues writing the mature local marketplace; switching the adapter to the new canonical cloud aggregate is a controlled follow-up after deploying the migration and its atomic acceptance RPC.

## Ride communication

- Text, presets, AAC audio and selected images are stored locally first.
- Authenticated clients synchronize them through `ride_messages`; media uses the private `ride-media` bucket.
- Row-level security permits only the assigned passenger and driver to read a conversation. The sender must equal `auth.uid()`.
- The UI distinguishes `SYNCED`, `PENDING`, `FAILED` and `LOCAL_ONLY`; it never invents delivery.
- Calls open Android's confirmed system dialer and need no direct-call permission. They are deliberately described as carrier calls, not masked calls.

## Explicit production gaps

The following cannot be truthfully called globally production-ready without external infrastructure and operational contracts:

1. Deploy the new Supabase migration and validate policies in staging with two real accounts plus a non-participant denial test.
2. Add a server-owned atomic RPC for the canonical universal-service offer acceptance and event/outbox ledger.
3. Select a regional masked-calling or WebRTC provider for private in-app calling; define fallback, lawful recording policy, quality telemetry and emergency handling.
4. Connect an authorized payment processor. Google Play Billing must not be represented as payment for physical services where Play policy disallows that use.
5. Add provider KYC/KYB, credential review by jurisdiction, trust-and-safety operations, dispute handling and restricted-category controls.
6. Contract routing/geocoding/media SLAs and monitoring. Free providers remain useful technical fallbacks, not a worldwide commercial guarantee.

These are deployment and operations gates, not hidden simulated features.
