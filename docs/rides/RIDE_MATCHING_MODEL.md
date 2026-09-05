# ELYSIUM MOBILITY OS — MATCHING & DISPATCH MODEL
**Status**: AUTHORITATIVE SPECIFICATION V1  
**Baseline SHA**: `2f3b4535d8fc9c82d4d718217afe73ab28b65a2c`  
**Governing Rule**: *Dispatch waves ensure fair exposure. AI/client recommendations are proposals; server-side atomic CAS is the sole matching authority.*

---

## 1. Matching & Dispatch Lifecycle

```text
RideRequest Published
         │
         ▼
 ┌────────────────┐
 │ DISPATCH WAVES │ (Radius R1: 0-2 km, Wait W1: 15s)
 └───────┬────────┘
         ▼
 Candidate Drivers Filtered (Verified Driver + Eligible Vehicle + Fresh GPS)
         │
         ├─────────────────────────────────────────────────┐
         ▼                                                 ▼
[OPEN MARKET BIDDING]                             [AUTO-MATCH POLICY]
 Drivers receive push/stream                       Client/server evaluates policy:
 Drivers submit offers                             - FASTEST_PICKUP
 Passenger selects winning offer                   - LOWEST_FARE
                                                   - HIGHEST_TRUST
                                                   - BALANCED
                                                           │
                                                           ▼
                                            Server CAS Commit (ride_accept_offer_v2)
```

---

## 2. Dispatch Waves & Exposure Tracking

To prevent starvation and minimize deadhead:
1. **Wave 1 (0 to 15 seconds)**: Radius 2 km. Only drivers with `AVAILABLE` state and Bayesian Trust Tier `GOLD` or `DIAMOND`.
2. **Wave 2 (15 to 30 seconds)**: Radius 5 km. Expands to `SILVER` tier and drivers in `FINISHING_CURRENT_TRIP` state.
3. **Wave 3 (30 to 60 seconds)**: Radius 10 km. All eligible drivers.
4. **Timeout (60 seconds)**: Transitions request to `NO_DRIVER_FOUND` / `EXPIRED` if no driver claimed or offered.

### Exposure Receipts
- The server tracks exposures in `ride_request_exposures`.
- Client acknowledges delivery with `ride_ack_request_delivered_v1(p_request_id, p_driver_id)`.
- Client acknowledges UI render with `ride_ack_request_seen_v1(p_request_id)`.
- Prevents charging drivers for requests they never received due to connectivity dropouts.

---

## 3. Auto-Match Evaluation Strategies

Implemented in pure domain class `RideAutoMatchEvaluator`:

| Strategy | Primary Metric | Secondary Metric | Use Case |
|---|---|---|---|
| `FASTEST_PICKUP` | Minimum `etaMinutes` | Minimum `offeredFareMinor` | Urgent rides, airport pickups |
| `LOWEST_FARE` | Minimum `offeredFareMinor` | Minimum `etaMinutes` | Budget riders, high price sensitivity |
| `HIGHEST_TRUST` | Highest `trustTier` (Diamond > Gold > Silver > Bronze) | Highest `bayesianRating` | VIP, late night, solo passengers |
| `BALANCED` | Multi-attribute utility: `0.4 * ETA + 0.3 * Tier + 0.3 * Rating` | Tie-breaker: lowest fare | Default recommendation |

---

## 4. Exclusive Assignment Concurrency Law

Even if multiple drivers or auto-match algorithms select the same candidate:
- **Only one driver can win**: Protected by PostgreSQL row lock `SELECT * FROM ride_requests WHERE id = p_id FOR UPDATE`.
- First transaction advances `version = version + 1` and sets `assigned_driver_id = p_driver_id`.
- Concurrent contenders fail immediately with typed `ALREADY_ASSIGNED` or `VERSION_CONFLICT`.
