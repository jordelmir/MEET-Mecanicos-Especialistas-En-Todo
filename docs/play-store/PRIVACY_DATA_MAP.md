# MEET / ELYSIUM — Internal Privacy Data Map & RLS Isolation Matrix

## 1. Overview & Data Flow Topology
This document maps every personal and sensitive data field collected in the MEET / ELYSIUM platform to its PostgreSQL storage table, authorization model, RLS policy, and retention lifecycle.

---

## 2. Table-by-Table Privacy & RLS Matrix

### `public.principals` & `public.principal_profiles`
- **Fields**: `principal_id` (UUID), `phone`, `full_name`, `status`, `display_name`, `locale`.
- **Classification**: Personally Identifiable Information (PII).
- **RLS Access**:
  - `SELECT`: Only the authenticated user (`principal_id = auth.uid()`) or `service_role`.
  - `INSERT/UPDATE`: Gated by strictly controlled RPCs or user self-profile updates.
- **Counterparty Exposure**: Matched counterparty sees ONLY `display_name` via public projection/RPC; phone number and raw email are NEVER exposed directly in table reads.
- **Deletion Behavior**: Upon account deletion, `phone` set to `NULL`, `full_name` and `display_name` pseudonymized to `'DELETED_USER_' || random_hash`, and `status` set to `'DELETED'`.

### `public.driver_location_history` & `public.driver_presence_snapshot`
- **Fields**: `driver_id`, `latitude`, `longitude`, `bearing`, `speed_mps`, `accuracy_meters`, `sequence_number`.
- **Classification**: Sensitive Geolocation.
- **RLS Access**:
  - `driver_presence_snapshot`: Only active, verified drivers within search radius; monotonic sequence ordering enforced by DB trigger.
  - `driver_location_history`: Append-only; accessible only by `service_role` and the driver themselves.
- **Deletion Behavior**: Snapshots purged immediately upon account deletion.

### `public.trips` & `private.mobility_trip_pin_challenges`
- **Fields**:
  - `public.trips`: `trip_id`, `rider_id`, `driver_id`, `state`, `verification_pin_hash` (MUST BE NULL in V11), route metadata.
  - `private.mobility_trip_pin_challenges`: `trip_id`, `pin_hash` (bcrypt salt 12), `expires_at`, `failed_attempts`, `locked_until`, `is_used`.
- **Classification**: Sensitive Trip & Authentication Credentials.
- **RLS Access**:
  - `public.trips`: Rider and driver only (`auth.uid() IN (rider_id, driver_id)`). Third-party readers (trusted contacts) CANNOT read `public.trips`.
  - `private.mobility_trip_pin_challenges`: In `private` schema. ZERO grants to `authenticated`, `anon`, or `public`. Accessed solely via `SECURITY DEFINER` functions `mobility_issue_trip_verification_pin` and `mobility_transition_trip`.
  - `public.mobility_trip_share_projection`: Dedicated safe view for trusted contacts with minimal fields (`trip_id`, `status`, `vehicle_label`, `last_known_lat/lng`). Excludes pricing, rider PII, driver phone, and verification PIN.

### `public.payment_authorizations` & `public.mobility_ledger_lines`
- **Fields**: `amount_minor`, `currency`, `provider`, `provider_capture_ref`, `provider_event_id`, account balances.
- **Classification**: Financial Records.
- **RLS Access**:
  - Authorizations accessible only by the authorizing rider and `service_role`.
  - Ledger lines are append-only and accessible only by `service_role` and authenticated account holders for their own accounts.
- **Regulatory Retention**: Retained for 7 years per statutory tax and anti-money laundering (AML) requirements; decoupled from user identity upon deletion.
