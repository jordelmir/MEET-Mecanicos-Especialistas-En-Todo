# ELYSIUM MOBILITY OS — LOCATION & GEOSPATIAL MODEL
**Status**: AUTHORITATIVE SPECIFICATION V1  
**Baseline SHA**: `2f3b4535d8fc9c82d4d718217afe73ab28b65a2c`  
**Governing Rule**: *Every location sample must be attributable, validated, sequenced, and privacy-shielded. Old offline backfills never become LIVE.*

---

## 1. Location Attribution & Ingestion Envelope

Every ingested location point carries:
```json
{
  "principal_id": "uuid-of-driver",
  "device_id": "stable-android-id-or-installation-id",
  "trip_id": "uuid-of-active-assigned-trip",
  "sequence": 142,
  "captured_at": "2026-09-05T18:45:00.120Z",
  "received_at": "2026-09-05T18:45:01.050Z",
  "latitude": 9.93245,
  "longitude": -84.07982,
  "accuracy_meters": 4.8,
  "speed_mps": 11.2,
  "bearing_degrees": 215.0,
  "provider": "fused_gps"
}
```

### Ingestion Validation Filters
The server rejects or downgrades samples if:
1. **Coordinate Validity**: `latitude` outside `[-90, 90]` or `longitude` outside `[-180, 180]`.
2. **Actor Authorization**: Derived from `auth.uid()`. Rejects if caller is not the `assigned_driver_id` of the specified `trip_id`.
3. **Trip State**: Rejects if trip state is not active (`DRIVER_EN_ROUTE`, `ARRIVED`, `PASSENGER_ONBOARD`, `IN_PROGRESS`).
4. **Monotonic Sequence**: `sequence <= last_seen_sequence` on the active trip is discarded as a duplicate or out-of-order frame.
5. **Clock Skew Bounds**: `captured_at > now() + interval '30 seconds'` is rejected as impossible future telemetry.
6. **Impossible Velocity Filter**: Speed $> 200 \text{ km/h}$ ($55.5 \text{ m/s}$) triggers anomaly flag and excludes the point from routing/ETA updates.

---

## 2. Location Freshness Taxonomy

```text
[0s --------- 15s]  --> LIVE    (Active real-time tracking, driving dispatch)
(15s -------- 60s]  --> RECENT  (Degraded network; visible with warning)
(60s ------- 300s]  --> STALE   (Driver disconnected; dispatch disabled)
(> 300s ---------]  --> UNKNOWN (Driver marked OFFLINE)
```

**Rule**: An offline batch upload backfilling historic breadcrumbs is marked `HISTORICAL_RECORD`, never promoted to `LIVE`.

---

## 3. Geospatial Privacy & Data Minimization

1. **Log & Telemetry Hygiene**:
   - Exact coordinates MUST NEVER appear in `Log.d/Log.e`, Timber, Sentry breadcrumbs, Datadog metric tags, URL query parameters, or push notifications.
   - Sentry breadcrumbs record coarse zones: `"Driver location updated: San José GAM zone"`.
2. **Passenger Privacy**:
   - Passengers NEVER transmit continuous GPS to the server. The passenger location is sampled exactly once at pickup time (`pickup_point`).
   - Active trip tracking tracks the DRIVER vehicle only.
3. **Retention Policy**:
   - **Live Presence Cache**: Retained in Redis/memory for 30 seconds, discarded when lease expires.
   - **Active Trip Breadcrumbs**: Stored in PostGIS `ride_location_breadcrumbs` with default TTL of 90 days.
   - **Automated Purge**: Daily cron job `ride_purge_expired_location_breadcrumbs_v1()` executes `DELETE WHERE expires_at < now() AND legal_hold_id IS NULL`.
   - **Legal Hold Override**: Platform safety operators can issue an active legal hold (`ride_create_location_legal_hold_v1`) extending retention for forensic review. Every access logs an entry in `ride_location_disclosure_audit`.
