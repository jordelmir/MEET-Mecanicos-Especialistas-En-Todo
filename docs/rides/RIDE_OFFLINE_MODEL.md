# ELYSIUM MOBILITY OS — OFFLINE & RESILIENCE MODEL
**Status**: AUTHORITATIVE SPECIFICATION V1  
**Baseline SHA**: `2f3b4535d8fc9c82d4d718217afe73ab28b65a2c`  
**Governing Rule**: *Local mutation != Server commit. Android UI must honestly distinguish local drafts, pending deliveries, confirmed states, and conflicts. Never fabricate success without cloud commit.*

---

## 1. Local State Taxonomy

```text
 ┌──────────────────────┐
 │     LOCAL_DRAFT      │ Created in memory / local SQLite
 └──────────┬───────────┘
            │ User confirms action -> Enqueue in Outbox
            ▼
 ┌──────────────────────┐
 │ PENDING_CONFIRMATION │ Buffered in WorkManager; waiting for network/RPC
 └──────────┬───────────┘
            ├──────────────────────────┬──────────────────────────┐
            │ RPC Accepted             │ Version Mismatch         │ Network Timeout
            ▼                          ▼                          ▼
 ┌──────────────────────┐   ┌──────────────────────┐   ┌──────────────────────┐
 │      CONFIRMED       │   │       CONFLICT       │   │        STALE         │
 └──────────────────────┘   └──────────────────────┘   └──────────────────────┘
```

---

## 2. Process Death & Lifecycle Survival

1. **Active Trip Recovery**:
   - The active ride pointer is persisted in Room `active_ride_selections` keyed by `ownerPrincipalId`.
   - When the Android OS kills the process in the background, `MainActivity` upon recreation queries `active_ride_selections` and restores the exact trip screen without losing active context.
   - It performs an asynchronous `fetchSnapshot(rideId)` to reconcile with server truth.
2. **Foreground Location Continuity**:
   - `RideLocationTrackingService` runs as an independent Android Foreground Service (`START_STICKY`).
   - If UI activities are destroyed or device orientation changes, the tracking service continues sampling and buffering GPS points.
3. **Outbox Guarantee**:
   - Commands are stored in SQLite `ride_command_outbox` inside a Room transaction before any network call.
   - `RideCommandSyncWorker` is triggered using `ExistingWorkPolicy.APPEND_OR_REPLACE`.
   - Crashes or phone restarts automatically re-trigger outbox drain via WorkManager boot receiver.

---

## 3. Reconciliation & Monotonic Versions

```kotlin
@Query("""
    UPDATE ride_requests
    SET status = :legacyStatus,
        serverState = :serverState,
        serverVersion = :serverVersion,
        syncState = 'SYNCED',
        lastSyncedAt = :syncedAt
    WHERE requestId = :requestId
      AND serverVersion <= :serverVersion
""")
suspend fun applyServerProjection(...)
```

**Monotonic Guard**: If a delayed or out-of-order network response arrives with `serverVersion < currentServerVersion`, the SQLite update modifies 0 rows, preventing time-travel bugs.
