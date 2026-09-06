# Google Play Store: Account Deletion Compliance Evidence

## 1. Regulatory Requirement & Compliance Statement
Google Play requires all apps allowing account creation to provide:
1. An easily discoverable in-app path for users to delete their account and associated data.
2. A publicly accessible web-based resource / URL where users can request account and data deletion without reinstalling the application.

MEET / ELYSIUM provides both mechanisms, backed by a canonical backend deletion processor (`public.process_account_deletion_request`).

---

## 2. In-App Deletion Path
- **Navigation Flow**:
  1. Open App -> Navigate to **Settings** (Ajustes) -> **Security & Privacy** (Seguridad y Privacidad).
  2. Scroll to bottom -> Tap **Delete Account** (Eliminar Cuenta).
  3. Confirmation dialog displays consequences (irreversible action, forfeiture of remaining wallet balance if not withdrawn, immediate revocation of active trips/shares).
  4. User enters their password or completes biometric re-authentication.
  5. The mobile client invokes the authoritative backend RPC:
     ```sql
     SELECT public.request_user_account_deletion(p_reason := 'USER_INITIATED');
     ```
  6. Account status immediately transitions to `PENDING` deletion, active sessions are invalidated, and user is logged out.

---

## 3. Web Deletion Resource
- **Public URL**: `https://meet.app/account/delete`
- **Functionality**:
  - Secure login portal using Supabase Auth (Magic Link or OAuth).
  - One-click deletion request form allowing user to provide reason and confirm deletion.
  - Submits to the same backend pipeline with `source = 'WEB_PORTAL'`.

---

## 4. Backend Processing Engine (`process_account_deletion_request`)
The canonical deletion engine performs the following four automated operations within a single database transaction:
1. **Revocation of Sharing Projections**:
   - Updates `public.mobility_trip_shares` setting `state = 'REVOKED'`, `revoked_at = clock_timestamp()`.
   - Clears `public.mobility_trip_share_projection` immediately terminating all active third-party tracking links.
2. **Purge of Driver Telemetry & Operational State**:
   - Deletes real-time coordinates from `public.driver_presence_snapshot`.
   - Clears capability authorizations from `public.principal_capabilities` preventing future dispatch.
3. **Pseudonymization of Personal Identifiers**:
   - In `public.principals`: `phone` is set to `NULL`, `full_name` is pseudonymized to `'DELETED_USER_' || substr(random_uuid, 1, 8)`, `status` is set to `'DELETED'`.
   - In `public.principal_profiles`: `display_name` is pseudonymized to `'DELETED_USER_' || substr(random_uuid, 1, 8)`.
4. **Finalization & Audit Trail**:
   - `public.account_deletion_requests` is marked with `status = 'COMPLETED'`, recording the exact timestamp while retaining no PII.
   - All steps are verified by automated test: `TEST 11: GATE 7 — CANONICAL ACCOUNT DELETION PROCESSOR` in `tests/mobility/verify-mobility-v11-public-launch.sh`.
