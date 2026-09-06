# MEET / ELYSIUM — V11 CURRENT AUTHORITY MAP

Baseline: `main@0fb3eb0e224c63aff707f2fda2d5b7253e55e583`
Latest Committed Migration: `20260906070000_mobility_hardening_and_stops_authority.sql`

This document maps all authoritative boundaries in MEET/Elysium across Mobility, Towing, Mechanics, Parts, Payments, and Identity as required by Master Order V11.

---

## 1. Domain Authority & RPC Inventory

| Domain | Actor | Command / Action | Authoritative Table | Remote RPC | Allowed Caller | External Authority | Integration Status | Test Verification |
|---|---|---|---|---|---|---|---|---|
| **Ride Request** | Rider | Request Ride | `public.ride_requests`, `public.ride_request_stops` | `mobility_request_ride` | `authenticated` (Rider) | None (Server author) | `INTEGRATED` | `PASS` (tests/mobility) |
| **Stop Authority** | Rider | Replace Stops (Metered only) | `public.ride_request_stops`, `public.ride_route_evidence` | `mobility_replace_ride_stops` | `authenticated` (Rider) | Road Router | `INTEGRATED` | `PASS` (Test 1 & 2) |
| **Stop Immutability** | Rider / Driver | Modify Open Bid Stops | `public.ride_request_stops` | Direct DML (REVOKED) | None (Revoked) | None | `ENFORCED` | `PASS` (Test 1 rejects) |
| **Dispatch Offer** | Driver | Accept Dispatch | `public.dispatch_offers`, `public.trips` | `mobility_accept_dispatch` | `authenticated` (Eligible Driver) | None (Server serialized) | `INTEGRATED` | `PASS` (100-way race) |
| **Driver Offer** | Driver | Submit Bid | `public.driver_ride_offers` | `mobility_submit_driver_offer` | `authenticated` (Eligible Driver) | None | `INTEGRATED` | `PASS` |
| **Offer Selection** | Rider | Accept Bid | `public.driver_ride_offers`, `public.trips` | `mobility_select_driver_offer` | `authenticated` (Rider) | None | `INTEGRATED` | `PASS` |
| **Boarding PIN Issue** | Rider | Issue 6-digit PIN | `private.mobility_trip_pin_challenges` | `mobility_issue_trip_verification_pin` | `authenticated` (Rider only), `service_role` | CSPRNG (private schema) | `INTEGRATED` | `PASS` (6-digit, bcrypt) |
| **Trip Transition** | Driver | Boarding (`RIDER_ONBOARD`) | `public.trips`, `private.mobility_trip_pin_challenges` | `mobility_transition_trip` | `authenticated` (Assigned Driver), `service_role` | None | `INTEGRATED` | `PASS` (Lockout on 5 fails) |
| **Trip Sharing** | Rider | Share Active Trip | `public.mobility_trip_shares`, `public.mobility_trip_share_projection` | `mobility_share_trip` | `authenticated` (Rider) | None | `INTEGRATED` | `PASS` (Safe projection) |
| **Sharing Read** | Grantee | Read Shared Trip | `public.mobility_trip_share_projection` | Direct SELECT (RLS) | `authenticated` (Grantee only) | None | `INTEGRATED` | `PASS` (No base trip access) |
| **Share Revocation** | Rider | Revoke Share | `public.mobility_trip_shares`, `public.mobility_trip_share_projection` | `mobility_revoke_trip_share` | `authenticated` (Rider) | None | `INTEGRATED` | `PASS` (Instant block) |
| **Driver Location** | Driver | Update Presence & GPS | `public.driver_presence_snapshot` | `mobility_update_driver_presence` | `authenticated` (Driver) | Device GPS | `INTEGRATED` | `PASS` (Monotonic seq) |
| **Route Evidence** | Server / Router | Compute Authoritative Route | `public.ride_route_evidence` | Server function / Road Router | `service_role` / Server | OSRM / Google Routes | `INTEGRATED` | `PASS` (Fail-closed in prod) |
| **Quote Authority** | Server | Generate Pricing Quote | `public.ride_quotes` | `mobility_generate_quote` | `authenticated` (Rider) | Route Evidence | `INTEGRATED` | `PASS` |
| **Payment Capability**| System | Provider Gating | `public.mobility_payment_provider_capabilities` | DB constraint & check | DB / Admin | None | `INTEGRATED` | `PASS` (CASH only in prod) |
| **Payment Auth** | Rider | Authorize Quote Payment | `public.payment_authorizations` | `mobility_authorize_quote_payment` | `authenticated` (Rider) | Provider (CASH/PSP) | `INTEGRATED` | `PASS` (Fail-closed) |
| **PSP Capture** | PSP Webhook | Confirm Provider Capture | `public.payment_authorizations`, `public.payment_provider_events` | `mobility_confirm_provider_capture` | `service_role` strictly | Real PSP (Stripe/SINPE) | `INTEGRATED` | `PASS` (7 params + replay guard) |
| **Trip Settlement** | Server | Settle Trip to Ledger | `public.trip_settlements`, `public.ledger_transactions`, `public.ledger_entries` | `mobility_settle_trip` | `service_role` strictly | None (Double-entry balanced) | `INTEGRATED` | `PASS` (Zero-sum verified) |
| **Mutual Ratings** | Rider / Driver | Rate Trip Counterpart | `public.mobility_trip_ratings` | `mobility_rate_trip_party` | `authenticated` (Rider or Driver) | None | `INTEGRATED` | `PASS` (Completed only) |
| **Trip Tip** | Rider | Create & Authorize Tip | `public.mobility_trip_tips` | `mobility_create_trip_tip` | `authenticated` (Rider) | None | `INTEGRATED` | `PASS` |
| **Tip Settlement** | Server | Settle Tip | `public.mobility_trip_tips`, `public.ledger_entries` | `mobility_settle_trip_tip` | `service_role` strictly | None | `INTEGRATED` | `PASS` (Zero-sum balance) |
| **Capabilities** | Trust Center | Verify Human Capability | `public.principal_capabilities` | `fulfillment_assert_principal_capability` | `authenticated`, `service_role` | Identity vs Capability | `INTEGRATED` | `PASS` (RIDE, TOW, PART, MECH) |
| **Account Deletion** | User | Request Deletion | `public.account_deletion_requests` | `request_user_account_deletion` | `authenticated` (Self) | None | `INTEGRATED` | `PASS` |
| **Deletion Worker** | Server Worker | Process Deletion & Anonymize | Multiple domain tables, `auth.users` | `process_account_deletion_request` | `service_role` strictly | Compliance | `INTEGRATED` | `PASS` (COMPLETED state) |

---

## 2. Invariants & Security Boundaries

1. **PIN Secret Isolation**: Plaintext PIN exists exclusively during CSPRNG generation and return to rider. `private.mobility_trip_pin_challenges` stores only bcrypt hash (`crypt(pin, gen_salt('bf', 12))`). RLS completely revokes client access. Base `public.trips` has zero PIN columns or forced NULL constraint.
2. **Trip Sharing Boundary**: Grantees never query `public.trips`. They query `public.mobility_trip_share_projection` containing only safe telemetry (latitude, longitude, heading, ETA, status). Instant revocation invalidates access.
3. **External Money Boundary**: Electronic payments fail closed unless `public.mobility_payment_provider_capabilities` has `enabled = true` and `externally_verified = true` in `PRODUCTION`. Without certified external PSP, system runs purely on `CASH`.
4. **Zero-Sum Ledger**: Invariant `sum(amount_minor) == 0` is strictly enforced across all transactions (`TRIP_SETTLEMENT`, `TIP_SETTLEMENT`, `PAYMENT_CAPTURE`).
5. **Concurrency Serialization**: 100 concurrent driver claims on a single dispatch offer or ride request result in exactly 1 winner and 99 conflicts.
