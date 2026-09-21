# Elysium Vanguard AI OS 4.26.1 — authority and privacy hardening

**Android:** `versionName 4.26.1`, `versionCode 60`.

This patch closes production-blocking ride gaps. The server rejects a passenger attempting to claim their own ride. A successful completion atomically writes exactly one immutable `TRIP_COMPLETED` event with the same idempotency key as its command receipt and ledger capture. The migration backfills missing completion evidence without altering completed rides.

Lost-and-found uses the protected trip chat and no longer exposes phone numbers or locally invented compensation amounts. Wallet instructions render only when the server provides the payment policy; the UI contains no personal recipient fallback.

Validation: PostgreSQL ride-authority integration, including the self-claim and one-event completion regressions. The Android release artifact must be signed with the configured production key; debug artifacts are not release artifacts.
