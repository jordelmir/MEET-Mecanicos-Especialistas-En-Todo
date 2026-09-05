# ELYSIUM MOBILITY OS — CANONICAL DATA MODEL SPECIFICATION
**Status**: AUTHORITATIVE SPECIFICATION V1  
**Baseline SHA**: `2f3b4535d8fc9c82d4d718217afe73ab28b65a2c`  
**Governing Rule**: *One Business Fact → One Durable Authority. Expand-first migration doctrine. Database constraints enforce domain invariants concurrently.*

---

## 1. Cloud PostgreSQL Schemas

### Table: `public.ride_requests`
Canonical authority for all trips and requests.

```sql
CREATE TABLE public.ride_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    passenger_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE RESTRICT,
    state TEXT NOT NULL CHECK (
        state IN (
            'DRAFT', 'SEARCHING', 'OFFERED', 'ASSIGNED',
            'DRIVER_EN_ROUTE', 'ARRIVED', 'PASSENGER_ONBOARD',
            'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'EXPIRED', 'DISPUTED'
        )
    ),
    version BIGINT NOT NULL DEFAULT 1 CHECK (version >= 1),
    currency TEXT NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    offered_fare_minor BIGINT NOT NULL CHECK (offered_fare_minor >= 0),
    final_fare_minor BIGINT CHECK (final_fare_minor IS NULL OR final_fare_minor >= 0),
    fare_mode TEXT NOT NULL DEFAULT 'OPEN_BID' CHECK (fare_mode IN ('OPEN_BID', 'METERED', 'FIXED')),
    
    pickup_point GEOGRAPHY(POINT, 4326) NOT NULL,
    pickup_address TEXT NOT NULL,
    destination_point GEOGRAPHY(POINT, 4326) NOT NULL,
    destination_address TEXT NOT NULL,
    stops_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    
    assigned_driver_id UUID REFERENCES auth.users(id) ON DELETE RESTRICT,
    assigned_vehicle_id UUID REFERENCES public.ride_driver_vehicles(id) ON DELETE RESTRICT,
    
    boarding_pin_hash TEXT,
    boarding_pin_expires_at TIMESTAMPTZ,
    
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    
    -- Invariants: Assigned vehicle requires assigned driver
    CONSTRAINT ride_assigned_pair_check CHECK (
        (assigned_driver_id IS NULL AND assigned_vehicle_id IS NULL) OR
        (assigned_driver_id IS NOT NULL AND assigned_vehicle_id IS NOT NULL)
    )
);

CREATE INDEX idx_ride_requests_state ON public.ride_requests(state);
CREATE INDEX idx_ride_requests_passenger ON public.ride_requests(passenger_id, created_at DESC);
CREATE INDEX idx_ride_requests_driver ON public.ride_requests(assigned_driver_id, created_at DESC);
CREATE INDEX idx_ride_requests_pickup_geo ON public.ride_requests USING GIST(pickup_point);
```

### Table: `public.ride_offers`
Driver bids on open requests.

```sql
CREATE TABLE public.ride_offers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID NOT NULL REFERENCES public.ride_requests(id) ON DELETE CASCADE,
    driver_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE RESTRICT,
    vehicle_id UUID NOT NULL REFERENCES public.ride_driver_vehicles(id) ON DELETE RESTRICT,
    fare_minor BIGINT NOT NULL CHECK (fare_minor > 0),
    currency TEXT NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    estimated_arrival_minutes INT NOT NULL CHECK (estimated_arrival_minutes >= 0),
    state TEXT NOT NULL CHECK (state IN ('PENDING', 'ACCEPTED', 'DECLINED', 'EXPIRED', 'WITHDRAWN')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(request_id, driver_id) -- At most one active offer per driver per request
);

CREATE INDEX idx_ride_offers_request ON public.ride_offers(request_id, state);
```

### Table: `public.ride_command_receipts`
Deduplication and idempotent replay engine.

```sql
CREATE TABLE public.ride_command_receipts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE RESTRICT,
    trip_id UUID REFERENCES public.ride_requests(id) ON DELETE RESTRICT,
    command_type TEXT NOT NULL,
    idempotency_key TEXT NOT NULL CHECK (char_length(idempotency_key) BETWEEN 16 AND 128),
    request_hash TEXT NOT NULL CHECK (request_hash ~ '^[a-f0-9]{64}$'),
    response JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (actor_id, idempotency_key)
);

CREATE INDEX idx_ride_receipts_trip ON public.ride_command_receipts(trip_id, created_at DESC);
```

### Table: `public.ride_wallet_ledger`
Double-entry balanced financial ledger.

```sql
CREATE TABLE public.ride_wallet_ledger (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL,
    trip_id UUID REFERENCES public.ride_requests(id) ON DELETE RESTRICT,
    driver_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE RESTRICT,
    account_kind TEXT NOT NULL CHECK (
        account_kind IN (
            'DRIVER_AVAILABLE', 'DRIVER_RESERVED',
            'PLATFORM_COMMISSION', 'TENANT_FEE', 'COOPERATIVE_FUND'
        )
    ),
    direction TEXT NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency TEXT NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    entry_type TEXT NOT NULL,
    idempotency_key TEXT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ride_ledger_driver ON public.ride_wallet_ledger(driver_id, currency, created_at DESC);
```

### Table: `public.ride_location_breadcrumbs`
Encrypted, post-processed geospatial forensic tracks.

```sql
CREATE TABLE public.ride_location_breadcrumbs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id UUID NOT NULL REFERENCES public.ride_requests(id) ON DELETE RESTRICT,
    driver_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE RESTRICT,
    sequence BIGINT NOT NULL CHECK (sequence >= 0),
    location GEOGRAPHY(POINT, 4326) NOT NULL,
    accuracy_meters FLOAT NOT NULL CHECK (accuracy_meters >= 0),
    speed_mps FLOAT CHECK (speed_mps IS NULL OR speed_mps >= 0),
    bearing_degrees FLOAT CHECK (bearing_degrees IS NULL OR (bearing_degrees >= 0 AND bearing_degrees < 360)),
    captured_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL DEFAULT (now() + interval '90 days'),
    legal_hold_id UUID REFERENCES public.ride_location_legal_holds(id) ON DELETE SET NULL,
    UNIQUE(trip_id, driver_id, sequence)
);

CREATE INDEX idx_ride_breadcrumbs_trip ON public.ride_location_breadcrumbs(trip_id, sequence ASC);
```

---

## 2. Local Android Room Schemas (DB Version 70)

- **`RideRequestEntity`**: Local projection cached in SQLite table `ride_requests`.
  - Fields: `requestId`, `passengerId`, `passengerName`, `pickupLatitude`, `pickupLongitude`, `destLatitude`, `destLongitude`, `priceOfferMinor`, `currency`, `status`, `serverState`, `serverVersion`, `syncState`, `boardingPin`.
  - Invariant constraint: Updates must satisfy `serverVersion <= :serverVersion` to prevent stale overwrite.
- **`RideOfferEntity`**: Cached table `ride_offers`.
- **`ActiveRideSelectionEntity`**: Local-only pointer to the active ride for the authenticated `ownerPrincipalId`.
- **`RideCommandOutboxEntity`**: Table `ride_command_outbox` guaranteeing reliable queueing across process death.
  - Fields: `idempotencyKey` (PK), `rideId`, `actorSessionUserId`, `commandType`, `expectedVersion`, `payloadJson`, `status`, `attemptCount`, `nextAttemptAt`.
- **`ActiveOperationEntity`**: Table `active_operations` tracking background foreground services.

---

## 3. Database Migration Doctrine
1. **Expand-First**: Add new nullable columns or additive tables first.
2. **Dual-Read / Dual-Write**: Projections read new structure with fallback to old.
3. **Backfill & Verify**: Safely backfill historical records and run deterministic verification scripts.
4. **Contract Later**: Retire legacy fields only after all client versions are upgraded.
5. **No Blind Renames**: Never drop or rename columns in production without deprecation cycles.
