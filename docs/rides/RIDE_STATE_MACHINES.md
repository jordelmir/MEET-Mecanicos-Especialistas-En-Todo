# ELYSIUM MOBILITY OS — FORMAL STATE MACHINES SPECIFICATION
**Status**: AUTHORITATIVE SPECIFICATION V1  
**Baseline SHA**: `2f3b4535d8fc9c82d4d718217afe73ab28b65a2c`  
**Governing Rule**: *Every lifecycle aggregate must have an explicit transition matrix. No free-form status mutations from UI/ViewModel code.*

---

## 1. Ride Lifecycle State Machine

```text
       ┌───────────────┐
       │     DRAFT     │
       └───────┬───────┘
               │ PUBLISH (Passenger, v=0)
               ▼
       ┌───────────────┐  EXPIRE (System)   ┌───────────────┐
       │   SEARCHING   ├───────────────────►│    EXPIRED    │
       └───────┬───────┘                    └───────────────┘
               │
       ┌───────┴───────┬───────────────────────────────────────────┐
       │ SUBMIT_OFFER  │ CLAIM (First-Come-First-Served)           │
       ▼ (Driver)      ▼ (Driver with verified vehicle)            │
 ┌───────────┐   ┌───────────┐                                     │
 │  OFFERED  │   │ ASSIGNED  │◄────────────────────────────────────┘
 └─────┬─────┘   └─────┬─────┘   ACCEPT_OFFER (Passenger, CAS check)
       │               │
       └───────────────┘
               │ DRIVER_EN_ROUTE (Assigned Driver)
               ▼
       ┌───────────────┐
       │DRIVER_EN_ROUTE│
       └───────┬───────┘
               │ DRIVER_ARRIVED (Assigned Driver + GPS Proof)
               ▼
       ┌───────────────┐
       │    ARRIVED    │
       └───────┬───────┘
               │ VERIFY_BOARDING_PIN (Driver input 4-digit PIN matching hash)
               ▼
       ┌───────────────┐
       │PASSENGER_ONBRD│
       └───────┬───────┘
               │ START (Assigned Driver)
               ▼
       ┌───────────────┐
       │  IN_PROGRESS  │
       └───────┬───────┘
               │ COMPLETE (Assigned Driver)
               ▼
       ┌───────────────┐
       │   COMPLETED   │
       └───────────────┘
```

*Cancellation Path*: From any non-terminal state (`DRAFT`, `SEARCHING`, `OFFERED`, `ASSIGNED`, `DRIVER_EN_ROUTE`, `ARRIVED`), either authorized actor can trigger `CANCEL` with a typed reason code, transitioning to `CANCELLED`.

*Dispute Path*: From `COMPLETED` or `CANCELLED`, passenger, driver, or safety operator can trigger `DISPUTE`, transitioning to `DISPUTED`.

---

## 2. Transition Matrix Specification

| Transition ID | Current State | Command | Authorized Actor | Preconditions | Expected Version | Resulting State | Side Effects & Outbox Events | Idempotency Behavior | Failure Codes | Audit Evidence |
|---|---|---|---|---|---|---|---|---|---|---|
| **TR-01** | `DRAFT` | `PUBLISH` | `PASSENGER` | Pickup & destination coords valid, currency CRC/USD, non-zero fare | `0` | `SEARCHING` | Inserts `public.ride_requests`, initializes fare quote, broadcasts to dispatch waves | Unique idempotency key per client draft | `UNAUTHENTICATED`, `VALIDATION_ERROR`, `VERSION_CONFLICT` | Postgrest insert audit log |
| **TR-02** | `SEARCHING` | `SUBMIT_OFFER` | `DRIVER` | Driver verified, vehicle active & eligible, ETA within bounds | `current >= 1` | `OFFERED` | Inserts `public.ride_offers`, emits realtime offer event to passenger | Unique idempotency key per driver offer | `DRIVER_NOT_VERIFIED`, `VEHICLE_NOT_ELIGIBLE`, `OFFER_EXISTS` | Offer row created with driver ID |
| **TR-03** | `SEARCHING` / `OFFERED` | `ACCEPT_OFFER` | `PASSENGER` | Offer pending, vehicle verified, driver wallet balance sufficient for 5% commission | `request.version` | `ASSIGNED` | Row locked `FOR UPDATE`, reserves 5% commission in `ride_commission_reservations`, declines all other offers, increments version | Replay returns identical assignment result | `FORBIDDEN`, `ALREADY_ASSIGNED`, `VERSION_CONFLICT`, `OFFER_NOT_AVAILABLE`, `INSUFFICIENT_COMMISSION` | Receipt in `ride_command_receipts` |
| **TR-04** | `SEARCHING` / `OFFERED` | `CLAIM` | `DRIVER` | First-come claim mode, driver verified, vehicle active & eligible, driver wallet covers 5% commission | `request.version` | `ASSIGNED` | Row locked `FOR UPDATE`, sets `assigned_driver_id`, reserves commission, increments version | Replay returns identical assignment result | `ALREADY_ASSIGNED`, `VERSION_CONFLICT`, `VEHICLE_NOT_VERIFIED`, `INSUFFICIENT_COMMISSION` | Receipt in `ride_command_receipts` |
| **TR-05** | `ASSIGNED` | `DRIVER_EN_ROUTE` | `DRIVER` | Actor matches `assigned_driver_id` | `request.version` | `DRIVER_EN_ROUTE` | Emits navigation update to passenger, initiates location broadcast | Replay returns identical state | `FORBIDDEN`, `VERSION_CONFLICT`, `INVALID_TRANSITION` | Event log in `ride_event_log` |
| **TR-06** | `DRIVER_EN_ROUTE` | `DRIVER_ARRIVED` | `DRIVER` | Actor matches `assigned_driver_id`, driver GPS within arrival radius (<= 150m of pickup) or overrides with warning | `request.version` | `ARRIVED` | Generates boarding PIN for passenger, notifies passenger of arrival, starts wait timer | Replay returns identical state | `FORBIDDEN`, `VERSION_CONFLICT`, `GPS_PROOF_REQUIRED` | Geofence arrival observation |
| **TR-07** | `ARRIVED` | `VERIFY_BOARDING_PIN` | `DRIVER` | 4-digit PIN matches cryptographic hash of issued PIN | `request.version` | `PASSENGER_ONBOARD` | Validates passenger presence, clears pending boarding challenge | Replay returns identical state | `INVALID_PIN`, `PIN_EXPIRED`, `VERSION_CONFLICT` | PIN verification attempt log |
| **TR-08** | `PASSENGER_ONBOARD` | `START` | `DRIVER` | Boarding PIN verified, active foreground tracking running | `request.version` | `IN_PROGRESS` | Starts trip telemetry recording, locks stops, starts metered fare calculation | Replay returns identical state | `PIN_NOT_VERIFIED`, `VERSION_CONFLICT` | Telemetry session start event |
| **TR-09** | `IN_PROGRESS` | `COMPLETE` | `DRIVER` | Actor matches `assigned_driver_id`, destination reached | `request.version` | `COMPLETED` | Calculates final metered fare, captures 5% platform commission from reservation into `ride_wallet_ledger`, stops tracking | Replay returns identical state | `FORBIDDEN`, `VERSION_CONFLICT`, `PAYMENT_FAILURE` | Financial ledger entry |
| **TR-10** | `SEARCHING`..`ARRIVED` | `CANCEL` | `PASSENGER` / `DRIVER` | Ride not completed, valid typed reason code provided | `request.version` | `CANCELLED` | Releases commission reservation, records cancellation fee if applicable, marks trip terminal | Replay returns identical cancellation | `FORBIDDEN`, `TERMINAL_STATE`, `REASON_REQUIRED` | Cancellation event with actor & reason |
| **TR-11** | `SEARCHING` / `OFFERED` | `EXPIRE` | `SYSTEM` | Search timeout elapsed without accepted offer | `request.version` | `EXPIRED` | Releases any held reservations, notifies passenger of no driver found | Idempotent system sweep | `TERMINAL_STATE` | Expiration audit record |
| **TR-12** | `COMPLETED` / `CANCELLED` | `DISPUTE` | `PASSENGER` / `DRIVER` / `SAFETY_OPERATOR` | Within 7 days of trip conclusion, typed dispute category | `request.version` | `DISPUTED` | Creates `ride_support_cases` record, places operational hold if pending payout | Replay returns existing dispute case | `DISPUTE_WINDOW_EXPIRED`, `ALREADY_DISPUTED` | Support case record & evidence blob |

---

## 3. Payment & Settlement State Machine

```text
   ┌──────────────────┐
   │   NOT_REQUIRED   │ (for pure cash rides)
   └──────────────────┘
            ▲
            │
   ┌────────┴─────────┐
   │  CASH_EXPECTED   │
   └────────┬─────────┘
            │ Driver reports collection
            ▼
   ┌──────────────────┐       Discrepancy Reported       ┌──────────────────┐
   │  CASH_REPORTED   ├─────────────────────────────────►│ CASH_DISCREPANCY │
   └──────────────────┘                                  └──────────────────┘

   ┌──────────────────┐
   │ PREAUTH_PENDING  │ (for card / digital payments)
   └────────┬─────────┘
            │ Preauthorization succeeded
            ▼
   ┌──────────────────┐
   │    AUTHORIZED    │
   └────────┬─────────┘
            │ Trip completed
            ▼
   ┌──────────────────┐
   │ CAPTURE_PENDING  │
   └────────┬─────────┘
            │ Processor webhook confirmed
            ▼
   ┌──────────────────┐        Refund Triggered          ┌──────────────────┐
   │     CAPTURED     ├─────────────────────────────────►│  REFUND_PENDING  │
   └──────────────────┘                                  └────────┬─────────┘
                                                                  │ Processor webhook confirmed
                                                                  ▼
                                                         ┌──────────────────┐
                                                         │     REFUNDED     │
                                                         └──────────────────┘
```

---

## 4. Driver Availability State Machine

```text
 ┌──────────────┐
 │   OFFLINE    │
 └──────┬───────┘
        │ GO_ONLINE (Driver active + verified + vehicle eligible + fresh GPS)
        ▼
 ┌──────────────┐       Trip Assigned       ┌──────────────┐
 │  AVAILABLE   ├──────────────────────────►│   IN_TRIP    │
 └──────▲───────┘                           └──────┬───────┘
        │                                          │ Last leg reached
        │ Return to online                         ▼
        │                              ┌──────────────────────┐
        └──────────────────────────────┤FINISHING_CURRENT_TRIP│
                                       └──────────────────────┘
```

Invariant: A driver cannot transition to `AVAILABLE` unless:
1. Driver identity is verified (`service_verifications.status == 'VERIFIED'`).
2. Driver background review is approved.
3. Assigned vehicle is active and eligible (valid inspection, SOAT, Dekra).
4. GPS sample is fresh (captured within the last 5 minutes).
5. No active safety hold or account suspension exists.
