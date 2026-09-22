# Elysium Vanguard AI OS 4.26.1 — authority and privacy hardening

**Android:** `versionName 4.26.1`, `versionCode 60`.

This patch closes production-blocking ride gaps. The server rejects a passenger attempting to claim their own ride. A successful completion atomically writes exactly one immutable `TRIP_COMPLETED` event with the same idempotency key as its command receipt and ledger capture. The migration backfills missing completion evidence without altering completed rides.

Lost-and-found uses the protected trip chat and no longer exposes phone numbers or locally invented compensation amounts. Wallet instructions render only when the server provides the payment policy; the UI contains no personal recipient fallback.

The Trust Center and executive Command Center are fail-closed for the confirmed
`jordelmir@gmail.com` account at the database boundary. Client visibility never
grants this access. Specialist wallet caches no longer create starter balances,
commissions, completed-job earnings, or approved top-ups locally: Supabase's
ledger receipt is required before a balance can be displayed as available.

Room is schema version 80. The complete 78→79 public Safety projection
migration and 79→80 encrypted Safety evidence migration are registered with
exported schemas. Supabase is deployed through
`20260920110000_ride_authority_and_completion_audit.sql`, following the
exclusive-owner boundary migration `20260920083000`.

Validation: PostgreSQL ride-authority integration, including the self-claim and one-event completion regressions; exclusive-owner Trust Center PostgreSQL integration; wallet authority contract test; Room schema verification; and Android device launch. The Android release artifact must be signed with the configured production key; debug artifacts are not release artifacts.
