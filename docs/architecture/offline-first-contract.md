# Offline-First Data & Synchronization Contract

## 1. Local Authority Principle
Room DB on Android is the authoritative local working copy for all immediate UI interactions, offline diagnostic telemetry, local drafts, and pending command outbox.

## 2. Server Authority & Canonical State
Global business truth (payments, accepted quotes, user roles, trust verifications, canonical work orders) is owned by Elysium Server + PostgreSQL.

## 3. Sync Item Outcomes
```
SUCCESS | RETRYABLE_FAILURE | PERMANENT_FAILURE | CONFLICT | AUTH_REQUIRED
```
- Retryable failures cause WorkManager exponential backoff (`Result.retry()`).
- Permanent failures isolate into local `sync_dead_letters` table for user visibility.
- False successes are strictly forbidden.
