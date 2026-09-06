# MEET / ELYSIUM — Production Runbook: Routing & Map Provider Outage

## 1. Trigger Conditions & Severity
- **Severity**: SEV-2 (High)
- **Trigger**: Mapbox / Google Maps Directions API error rate > 5% over 5 minutes, latency > 3000ms, or quota exhaustion.
- **Fail-Safe Principle**: Under V11 Gate 4, quotes and trips require `routing_mode = 'ROAD_NETWORK'` with immutable waypoint digests and encoded polylines. When the upstream road routing API is degraded, systems fall back to offline topology graphs or secondary road network routing APIs.

## 2. Immediate Triage & Mitigation
1. **Verify Routing API Status**:
   - Test external endpoint connectivity:
     ```bash
     curl -sI "https://api.mapbox.com/directions/v5/mapbox/driving?access_token=${MAPBOX_ACCESS_TOKEN}" | head -n 5
     ```
   - Check mobile telemetry for routing timeouts.

2. **Engage Routing Fallover**:
   - Switch primary routing provider in configuration / Remote Config from `MAPBOX` to `GOOGLE_MAPS` or `OSRM_FALLBACK`:
     ```bash
     supabase secrets set ROUTING_PROVIDER=GOOGLE_MAPS
     ```
   - For open-bid requests, the app fallback engine estimates road distances using local cached road graph network topology with a conservative 1.35x curvature multiplier over Haversine distance, ensuring safe price boundaries.

3. **Waypoint Digest Integrity**:
   - Ensure all route revisions continue generating valid SHA-256 digests in `ride_route_evidence`:
     ```sql
     SELECT request_id, route_version, routing_mode, waypoints_digest 
     FROM public.ride_route_evidence 
     ORDER BY created_at DESC LIMIT 10;
     ```

## 3. Post-Incident Recovery
1. Revert to primary provider when upstream error rates drop below 0.1%.
2. Audit quotes issued during the incident to ensure fare accuracy and driver earnings equity.
