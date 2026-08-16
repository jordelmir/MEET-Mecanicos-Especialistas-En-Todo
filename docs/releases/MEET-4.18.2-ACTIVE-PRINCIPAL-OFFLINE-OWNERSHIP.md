# MEET 4.18.2 — Active Principal and Offline Ownership

## Outcome

MEET now decides ownership when diagnostic evidence or a trip is created,
instead of assigning pending records to whichever account happens to be signed
in when connectivity returns.

## Security and integrity changes

- Added one reactive `ActivePrincipalKernel` backed by Supabase auth state, with
  a stable device-local principal when signed out.
- Garage vehicle projections switch through `flatMapLatest` when the principal
  changes.
- Account transitions clear the selected vehicle, physical vehicle binding,
  provider roles, polling and ride remote projections before loading the new
  account.
- Diagnostic sessions, DTC events and trips now persist immutable
  `ownerPrincipalId`, `tenantId`, `originDeviceId` and `createdOffline` fields.
- Pending Room queries and acknowledgement updates require the exact owner.
- `SyncWorker` uploads only rows whose immutable owner equals the authenticated
  principal. Local and unknown legacy principals are never uploaded.
- Removed the unused alternate `SupabaseSyncWorker`, which bypassed Hilt and
  could attribute records using the retry-time account.
- Diagnostic session `vehicleId` is now a canonical vehicle identifier. The
  separately stored `observedVin` is the only field mapped to the remote VIN.

## Room migration 56 to 57

Rows created before immutable ownership cannot be attributed safely. Migration
56 to 57 therefore marks them `OWNER_UNKNOWN_LEGACY`; no later login silently
adopts them. Legacy diagnostic-session VIN values are preserved as
`observedVin`, while their ambiguous `vehicleId` is reset to `LEGACY_UNKNOWN`.
Explicit, consented adoption can be implemented later as an audited command.

## Verification

- Focused JVM behavior and wiring contracts.
- Full Android debug unit suite.
- Android instrumentation migration suite compiles against schema 57; execution
  on physical hardware remains required because this project does not use an
  emulator for evidence.
- Android lint, debug APK assembly, release secret scans and TS/Kotlin parity.
