# MEET / ELYSIUM — Production Runbook: Realtime Cluster Outage

## 1. Trigger Conditions & Severity
- **Severity**: SEV-2 (High)
- **Trigger**: Supabase Realtime channel disconnection rate > 10%, message delivery latency > 2500ms, or mobile driver location broadcast failures.

## 2. System Behavior & Architectural Redundancy
Under ELYSIUM architecture:
1. **Database is Authoritative**: Realtime broadcasts are projections and notifications; the source of truth is always PostgreSQL rows (`trips`, `driver_presence_snapshot`, `driver_location_history`, `ride_offers`).
2. **Exponential Backoff**: Mobile clients back off connection attempts using jittered exponential backoff (1s, 2s, 4s, 8s, up to 30s).
3. **HTTP Polling Fallback**: When Realtime is disconnected for > 15s during an active trip, the Android client automatically switches to REST polling on `public.trips` and `public.mobility_trip_share_projection` every 4 seconds.

## 3. Immediate Diagnostic & Mitigation Actions
1. **Inspect Realtime Node Health**:
   - Check Supabase Dashboard -> Realtime metrics.
   - Verify PostgreSQL replication slot:
     ```sql
     SELECT slot_name, plugin, active, active_pid, restart_lsn 
     FROM pg_replication_slots 
     WHERE slot_name LIKE '%realtime%';
     ```
2. **Clean Stale Replication Slots if Lagging**:
   - If replication lag in WAL is causing DB disk growth:
     ```sql
     SELECT pg_size_pretty(pg_wal_lsn_diff(pg_current_wal_lsn(), restart_lsn)) AS replication_lag
     FROM pg_replication_slots;
     ```
3. **Client Polling Verification**:
   - Verify that drivers and riders continue to receive updates via PostgREST fallback.
