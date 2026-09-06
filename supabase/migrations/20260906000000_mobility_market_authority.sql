-- ─────────────────────────────────────────────────────────────────────────────
-- ELYSIUM GLOBAL MOBILITY OS — CANONICAL AUTHORITY MIGRATION V6
-- Mandate: ORDEN MAESTRA V6 (Waves 2 through 9)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE SCHEMA IF NOT EXISTS extensions;
CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA extensions;

DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS postgis WITH SCHEMA extensions;
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'PostGIS extension not available; activating geospatial compatibility shim';

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'geography') THEN
        CREATE TYPE extensions.geography AS (
            lng DOUBLE PRECISION,
            lat DOUBLE PRECISION,
            srid INTEGER
        );
    END IF;

    CREATE OR REPLACE FUNCTION extensions.ST_MakePoint(x DOUBLE PRECISION, y DOUBLE PRECISION)
    RETURNS extensions.geography
    LANGUAGE sql IMMUTABLE AS $f$
        SELECT ROW(x, y, 4326)::extensions.geography;
    $f$;

    CREATE OR REPLACE FUNCTION extensions.ST_SetSRID(geom extensions.geography, srid INTEGER)
    RETURNS extensions.geography
    LANGUAGE sql IMMUTABLE AS $f$
        SELECT ROW(geom.lng, geom.lat, srid)::extensions.geography;
    $f$;

    CREATE OR REPLACE FUNCTION extensions.ST_Distance(p1 extensions.geography, p2 extensions.geography)
    RETURNS DOUBLE PRECISION
    LANGUAGE plpgsql IMMUTABLE AS $f$
    DECLARE
        dlat DOUBLE PRECISION;
        dlng DOUBLE PRECISION;
        a DOUBLE PRECISION;
        c DOUBLE PRECISION;
    BEGIN
        dlat := radians(p2.lat - p1.lat);
        dlng := radians(p2.lng - p1.lng);
        a := sin(dlat / 2.0)^2 + cos(radians(p1.lat)) * cos(radians(p2.lat)) * sin(dlng / 2.0)^2;
        c := 2.0 * atan2(sqrt(a), sqrt(1.0 - a));
        RETURN 6371000.0 * c;
    END;
    $f$;

    CREATE OR REPLACE FUNCTION extensions.ST_DWithin(p1 extensions.geography, p2 extensions.geography, radius_m DOUBLE PRECISION)
    RETURNS BOOLEAN
    LANGUAGE sql IMMUTABLE AS $f$
        SELECT extensions.ST_Distance(p1, p2) <= radius_m;
    $f$;
END $$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. MARKETS & SERVICE CATEGORIES
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.mobility_markets (
    market_id TEXT PRIMARY KEY,
    country_code TEXT NOT NULL CHECK (country_code ~ '^[A-Z]{2}$'),
    currency_code TEXT NOT NULL CHECK (currency_code ~ '^[A-Z]{3}$'),
    timezone TEXT NOT NULL,
    dispatch_modes TEXT[] NOT NULL DEFAULT ARRAY['AUTO_DISPATCH', 'MARKETPLACE_OFFERS'],
    max_intermediate_stops INTEGER NOT NULL DEFAULT 3 CHECK (max_intermediate_stops >= 0 AND max_intermediate_stops <= 5),
    auto_dispatch_enabled BOOLEAN NOT NULL DEFAULT true,
    marketplace_offers_enabled BOOLEAN NOT NULL DEFAULT true,
    scheduled_rides_enabled BOOLEAN NOT NULL DEFAULT true,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE IF NOT EXISTS public.mobility_service_categories (
    service_category_id TEXT PRIMARY KEY,
    market_id TEXT NOT NULL REFERENCES public.mobility_markets(market_id) ON DELETE RESTRICT,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    max_passengers INTEGER NOT NULL CHECK (max_passengers >= 1),
    requires_ev BOOLEAN NOT NULL DEFAULT false,
    requires_accessible BOOLEAN NOT NULL DEFAULT false,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (market_id, code)
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. VEHICLES & DRIVER ELIGIBILITY
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.mobility_vehicles (
    vehicle_id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    license_plate TEXT NOT NULL,
    make TEXT NOT NULL,
    model TEXT NOT NULL,
    year INTEGER NOT NULL CHECK (year >= 1990 AND year <= 2035),
    color TEXT NOT NULL,
    seat_capacity INTEGER NOT NULL CHECK (seat_capacity >= 1),
    is_ev BOOLEAN NOT NULL DEFAULT false,
    is_accessible BOOLEAN NOT NULL DEFAULT false,
    verification_state TEXT NOT NULL CHECK (verification_state IN ('PENDING', 'VERIFIED', 'REJECTED', 'SUSPENDED')),
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (owner_id, license_plate)
);

CREATE TABLE IF NOT EXISTS public.driver_market_eligibility (
    driver_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    market_id TEXT NOT NULL REFERENCES public.mobility_markets(market_id) ON DELETE RESTRICT,
    is_eligible BOOLEAN NOT NULL DEFAULT false,
    background_check_cleared BOOLEAN NOT NULL DEFAULT false,
    documents_verified BOOLEAN NOT NULL DEFAULT false,
    active BOOLEAN NOT NULL DEFAULT true,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (driver_id, market_id)
);

CREATE TABLE IF NOT EXISTS public.driver_vehicle_authorizations (
    driver_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    vehicle_id UUID NOT NULL REFERENCES public.mobility_vehicles(vehicle_id) ON DELETE CASCADE,
    is_authorized BOOLEAN NOT NULL DEFAULT true,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (driver_id, vehicle_id)
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. MUTUAL PAIR BLOCKS (DiDi / Uber Safety Parity)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.mobility_pair_blocks (
    block_id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    blocker_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    blocked_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    reason TEXT,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (blocker_id, blocked_id)
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. DRIVER PRESENCE & GEOSPATIAL PLANE
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.driver_presence_snapshot (
    driver_id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    active_vehicle_id UUID REFERENCES public.mobility_vehicles(vehicle_id) ON DELETE SET NULL,
    market_id TEXT NOT NULL REFERENCES public.mobility_markets(market_id) ON DELETE RESTRICT,
    current_state TEXT NOT NULL CHECK (current_state IN ('OFFLINE', 'AVAILABLE', 'OFFERING', 'RESERVED', 'EN_ROUTE_TO_PICKUP', 'IN_TRIP', 'PAUSED', 'STALE')),
    location extensions.geography NOT NULL,
    heading REAL,
    speed_mps REAL,
    sequence_id BIGINT NOT NULL DEFAULT 1,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

DO $$
BEGIN
    CREATE INDEX idx_mobility_driver_presence_location ON public.driver_presence_snapshot USING gist(location);
EXCEPTION WHEN OTHERS THEN
    CREATE INDEX idx_mobility_driver_presence_location ON public.driver_presence_snapshot (location);
END $$;

CREATE INDEX IF NOT EXISTS idx_mobility_driver_presence_state ON public.driver_presence_snapshot (market_id, current_state);

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. RIDE REQUESTS & STOPS
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.ride_requests (
    ride_request_id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    rider_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE RESTRICT,
    market_id TEXT NOT NULL REFERENCES public.mobility_markets(market_id) ON DELETE RESTRICT,
    service_category_id TEXT NOT NULL REFERENCES public.mobility_service_categories(service_category_id) ON DELETE RESTRICT,
    dispatch_mode TEXT NOT NULL CHECK (dispatch_mode IN ('AUTO_DISPATCH', 'MARKETPLACE_OFFERS')),
    state TEXT NOT NULL CHECK (state IN ('REQUESTED', 'SEARCHING', 'MATCHED', 'EXPIRED', 'CANCELLED')),
    pickup_location extensions.geography NOT NULL,
    pickup_accuracy_meters REAL,
    pickup_address TEXT,
    destination_location extensions.geography NOT NULL,
    destination_accuracy_meters REAL,
    destination_address TEXT,
    requested_price_minor BIGINT CHECK (requested_price_minor IS NULL OR requested_price_minor >= 0),
    currency_code TEXT NOT NULL CHECK (currency_code ~ '^[A-Z]{3}$'),
    scheduled_for TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version >= 1),
    correlation_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE IF NOT EXISTS public.ride_request_stops (
    stop_id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    ride_request_id UUID NOT NULL REFERENCES public.ride_requests(ride_request_id) ON DELETE CASCADE,
    sequence INTEGER NOT NULL CHECK (sequence >= 0),
    stop_type TEXT NOT NULL CHECK (stop_type IN ('PICKUP', 'INTERMEDIATE', 'DESTINATION')),
    location extensions.geography NOT NULL,
    accuracy_meters REAL,
    address TEXT,
    place_id TEXT,
    display_name TEXT,
    UNIQUE (ride_request_id, sequence)
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 6. DISPATCH OFFERS & IN-DRIVE MARKETPLACE OFFERS
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.dispatch_offers (
    dispatch_offer_id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    ride_request_id UUID NOT NULL REFERENCES public.ride_requests(ride_request_id) ON DELETE CASCADE,
    driver_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    vehicle_id UUID NOT NULL REFERENCES public.mobility_vehicles(vehicle_id) ON DELETE CASCADE,
    state TEXT NOT NULL CHECK (state IN ('PENDING', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'SUPERSEDED')),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE IF NOT EXISTS public.ride_driver_offers (
    offer_id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    ride_request_id UUID NOT NULL REFERENCES public.ride_requests(ride_request_id) ON DELETE CASCADE,
    driver_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    vehicle_id UUID NOT NULL REFERENCES public.mobility_vehicles(vehicle_id) ON DELETE CASCADE,
    offered_price_minor BIGINT NOT NULL CHECK (offered_price_minor >= 0),
    currency_code TEXT NOT NULL CHECK (currency_code ~ '^[A-Z]{3}$'),
    pickup_eta_seconds BIGINT,
    state TEXT NOT NULL CHECK (state IN ('OPEN', 'SELECTED', 'REJECTED', 'WITHDRAWN', 'EXPIRED')),
    expires_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version >= 1),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 7. CANONICAL TRIPS
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.trips (
    trip_id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    ride_request_id UUID NOT NULL UNIQUE REFERENCES public.ride_requests(ride_request_id) ON DELETE RESTRICT,
    rider_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE RESTRICT,
    driver_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE RESTRICT,
    vehicle_id UUID NOT NULL REFERENCES public.mobility_vehicles(vehicle_id) ON DELETE RESTRICT,
    state TEXT NOT NULL CHECK (state IN (
        'ASSIGNED',
        'DRIVER_EN_ROUTE',
        'DRIVER_ARRIVED',
        'WAITING_FOR_RIDER',
        'RIDER_ONBOARD',
        'IN_PROGRESS',
        'ARRIVED_DESTINATION',
        'COMPLETED',
        'CANCELLED',
        'DISPUTED'
    )),
    verification_pin_hash TEXT,
    quote_id UUID,
    payment_authorization_id UUID,
    settlement_id UUID,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version >= 1),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 8. COMMAND RECEIPTS & REALTIME MEMBERSHIPS
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.mobility_command_receipts (
    receipt_id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    actor_id UUID NOT NULL,
    command_scope TEXT NOT NULL,
    idempotency_key UUID NOT NULL,
    request_hash TEXT NOT NULL,
    requested_aggregate_id UUID,
    aggregate_id UUID,
    response JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (actor_id, command_scope, idempotency_key)
);

CREATE TABLE IF NOT EXISTS public.mobility_realtime_memberships (
    topic TEXT NOT NULL,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    role TEXT NOT NULL,
    expires_at TIMESTAMPTZ,
    PRIMARY KEY (topic, user_id)
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 9. REVOKE DIRECT MUTATION
-- ─────────────────────────────────────────────────────────────────────────────

REVOKE INSERT, UPDATE, DELETE ON public.mobility_markets FROM authenticated, anon;
REVOKE INSERT, UPDATE, DELETE ON public.mobility_service_categories FROM authenticated, anon;
REVOKE INSERT, UPDATE, DELETE ON public.ride_requests FROM authenticated, anon;
REVOKE INSERT, UPDATE, DELETE ON public.ride_request_stops FROM authenticated, anon;
REVOKE INSERT, UPDATE, DELETE ON public.dispatch_offers FROM authenticated, anon;
REVOKE INSERT, UPDATE, DELETE ON public.ride_driver_offers FROM authenticated, anon;
REVOKE INSERT, UPDATE, DELETE ON public.trips FROM authenticated, anon;
REVOKE INSERT, UPDATE, DELETE ON public.mobility_command_receipts FROM authenticated, anon;
REVOKE INSERT, UPDATE, DELETE ON public.mobility_realtime_memberships FROM authenticated, anon;

GRANT SELECT ON public.mobility_markets TO authenticated;
GRANT SELECT ON public.mobility_service_categories TO authenticated;
GRANT SELECT ON public.ride_requests TO authenticated;
GRANT SELECT ON public.ride_request_stops TO authenticated;
GRANT SELECT ON public.dispatch_offers TO authenticated;
GRANT SELECT ON public.ride_driver_offers TO authenticated;
GRANT SELECT ON public.trips TO authenticated;
GRANT SELECT ON public.mobility_realtime_memberships TO authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 10. RPC: REQUEST RIDE
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.mobility_request_ride(
    p_market_id TEXT,
    p_service_category_id TEXT,
    p_dispatch_mode TEXT,
    p_pickup_lat DOUBLE PRECISION,
    p_pickup_lng DOUBLE PRECISION,
    p_pickup_accuracy REAL,
    p_pickup_address TEXT,
    p_destination_lat DOUBLE PRECISION,
    p_destination_lng DOUBLE PRECISION,
    p_destination_accuracy REAL,
    p_destination_address TEXT,
    p_intermediate_stops JSONB DEFAULT '[]'::jsonb,
    p_requested_price_minor BIGINT DEFAULT NULL,
    p_scheduled_for TIMESTAMPTZ DEFAULT NULL,
    p_idempotency_key UUID DEFAULT NULL,
    p_correlation_id UUID DEFAULT NULL
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_actor UUID := auth.uid();
    v_market public.mobility_markets%ROWTYPE;
    v_category public.mobility_service_categories%ROWTYPE;
    v_hash TEXT;
    v_receipt public.mobility_command_receipts%ROWTYPE;
    v_request public.ride_requests%ROWTYPE;
    v_response JSONB;
    v_stop JSONB;
    v_seq INT := 1;
    v_stop_lat DOUBLE PRECISION;
    v_stop_lng DOUBLE PRECISION;
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING ERRCODE = '42501';
    END IF;

    IF p_idempotency_key IS NULL THEN
        RAISE EXCEPTION 'IDEMPOTENCY_KEY_REQUIRED';
    END IF;

    IF p_pickup_lat NOT BETWEEN -90 AND 90 OR p_pickup_lng NOT BETWEEN -180 AND 180 OR
       p_destination_lat NOT BETWEEN -90 AND 90 OR p_destination_lng NOT BETWEEN -180 AND 180 THEN
        RAISE EXCEPTION 'INVALID_COORDINATES';
    END IF;

    SELECT * INTO v_market FROM public.mobility_markets WHERE market_id = p_market_id AND active = TRUE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'MARKET_NOT_AVAILABLE';
    END IF;

    SELECT * INTO v_category FROM public.mobility_service_categories
    WHERE service_category_id = p_service_category_id AND market_id = p_market_id AND active = TRUE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'SERVICE_NOT_AVAILABLE';
    END IF;

    IF p_dispatch_mode = 'MARKETPLACE_OFFERS' AND NOT v_market.marketplace_offers_enabled THEN
        RAISE EXCEPTION 'MARKETPLACE_NOT_AVAILABLE';
    END IF;

    IF p_scheduled_for IS NOT NULL AND NOT v_market.scheduled_rides_enabled THEN
        RAISE EXCEPTION 'SCHEDULED_RIDE_NOT_AVAILABLE';
    END IF;

    -- Canonical server-computed SHA-256 hash
    v_hash := encode(
        extensions.digest(
            convert_to(
                jsonb_build_object(
                    'market', p_market_id,
                    'category', p_service_category_id,
                    'dispatch', p_dispatch_mode,
                    'pickup_lat', p_pickup_lat,
                    'pickup_lng', p_pickup_lng,
                    'dest_lat', p_destination_lat,
                    'dest_lng', p_destination_lng,
                    'price', p_requested_price_minor,
                    'scheduled_for', p_scheduled_for
                )::TEXT,
                'UTF8'
            ),
            'sha256'
        ),
        'hex'
    );

    PERFORM pg_advisory_xact_lock(hashtextextended(v_actor::TEXT || ':' || p_idempotency_key::TEXT, 0));

    SELECT * INTO v_receipt FROM public.mobility_command_receipts
    WHERE actor_id = v_actor AND command_scope = 'REQUEST_RIDE' AND idempotency_key = p_idempotency_key;

    IF FOUND THEN
        IF v_receipt.request_hash <> v_hash THEN
            RAISE EXCEPTION 'IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD' USING ERRCODE = '23505';
        END IF;
        RETURN v_receipt.response;
    END IF;

    INSERT INTO public.ride_requests (
        rider_id,
        market_id,
        service_category_id,
        dispatch_mode,
        state,
        pickup_location,
        pickup_accuracy_meters,
        pickup_address,
        destination_location,
        destination_accuracy_meters,
        destination_address,
        requested_price_minor,
        currency_code,
        scheduled_for,
        version,
        correlation_id
    ) VALUES (
        v_actor,
        p_market_id,
        p_service_category_id,
        p_dispatch_mode,
        CASE WHEN p_scheduled_for IS NULL THEN 'SEARCHING' ELSE 'REQUESTED' END,
        extensions.ST_SetSRID(extensions.ST_MakePoint(p_pickup_lng, p_pickup_lat), 4326),
        p_pickup_accuracy,
        p_pickup_address,
        extensions.ST_SetSRID(extensions.ST_MakePoint(p_destination_lng, p_destination_lat), 4326),
        p_destination_accuracy,
        p_destination_address,
        p_requested_price_minor,
        v_market.currency_code,
        p_scheduled_for,
        1,
        COALESCE(p_correlation_id, extensions.gen_random_uuid())
    ) RETURNING * INTO v_request;

    -- Pickup stop
    INSERT INTO public.ride_request_stops (
        ride_request_id, sequence, stop_type, location, accuracy_meters, address
    ) VALUES (
        v_request.ride_request_id, 0, 'PICKUP',
        v_request.pickup_location, p_pickup_accuracy, p_pickup_address
    );

    -- Intermediate stops
    IF p_intermediate_stops IS NOT NULL AND jsonb_array_length(p_intermediate_stops) > 0 THEN
        IF jsonb_array_length(p_intermediate_stops) > v_market.max_intermediate_stops THEN
            RAISE EXCEPTION 'TOO_MANY_INTERMEDIATE_STOPS';
        END IF;

        FOR v_stop IN SELECT * FROM jsonb_array_elements(p_intermediate_stops) LOOP
            v_stop_lat := (v_stop->>'latitude')::DOUBLE PRECISION;
            v_stop_lng := (v_stop->>'longitude')::DOUBLE PRECISION;
            INSERT INTO public.ride_request_stops (
                ride_request_id, sequence, stop_type, location, accuracy_meters, address, display_name, place_id
            ) VALUES (
                v_request.ride_request_id,
                v_seq,
                'INTERMEDIATE',
                extensions.ST_SetSRID(extensions.ST_MakePoint(v_stop_lng, v_stop_lat), 4326),
                (v_stop->>'accuracy_meters')::REAL,
                v_stop->>'address',
                v_stop->>'display_name',
                v_stop->>'place_id'
            );
            v_seq := v_seq + 1;
        END LOOP;
    END IF;

    -- Destination stop
    INSERT INTO public.ride_request_stops (
        ride_request_id, sequence, stop_type, location, accuracy_meters, address
    ) VALUES (
        v_request.ride_request_id, v_seq, 'DESTINATION',
        v_request.destination_location, p_destination_accuracy, p_destination_address
    );

    v_response := jsonb_build_object(
        'success', TRUE,
        'ride_request_id', v_request.ride_request_id,
        'state', v_request.state,
        'version', v_request.version
    );

    INSERT INTO public.mobility_command_receipts (
        actor_id, command_scope, idempotency_key, request_hash, requested_aggregate_id, aggregate_id, response
    ) VALUES (
        v_actor, 'REQUEST_RIDE', p_idempotency_key, v_hash, NULL, v_request.ride_request_id, v_response
    );

    RETURN v_response;
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 11. RPC: UPDATE DRIVER PRESENCE
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.mobility_update_driver_presence(
    p_market_id TEXT,
    p_vehicle_id UUID,
    p_state TEXT,
    p_lat DOUBLE PRECISION,
    p_lng DOUBLE PRECISION,
    p_heading REAL DEFAULT NULL,
    p_speed_mps REAL DEFAULT NULL,
    p_sequence_id BIGINT DEFAULT 1
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_driver UUID := auth.uid();
    v_existing public.driver_presence_snapshot%ROWTYPE;
    v_eligible BOOLEAN;
BEGIN
    IF v_driver IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING ERRCODE = '42501';
    END IF;

    IF p_lat NOT BETWEEN -90 AND 90 OR p_lng NOT BETWEEN -180 AND 180 THEN
        RAISE EXCEPTION 'INVALID_COORDINATES';
    END IF;

    IF p_speed_mps IS NOT NULL AND p_speed_mps > 83.33 THEN
        RAISE EXCEPTION 'PHYSICALLY_IMPOSSIBLE_SPEED';
    END IF;

    SELECT is_eligible INTO v_eligible FROM public.driver_market_eligibility
    WHERE driver_id = v_driver AND market_id = p_market_id AND active = TRUE;

    IF NOT COALESCE(v_eligible, FALSE) THEN
        RAISE EXCEPTION 'DRIVER_NOT_ELIGIBLE';
    END IF;

    IF p_vehicle_id IS NOT NULL THEN
        IF NOT EXISTS (
            SELECT 1 FROM public.driver_vehicle_authorizations a
            JOIN public.mobility_vehicles v ON v.vehicle_id = a.vehicle_id
            WHERE a.driver_id = v_driver AND a.vehicle_id = p_vehicle_id AND a.active = TRUE
              AND v.verification_state = 'VERIFIED' AND v.active = TRUE
        ) THEN
            RAISE EXCEPTION 'VEHICLE_NOT_ELIGIBLE';
        END IF;
    END IF;

    SELECT * INTO v_existing FROM public.driver_presence_snapshot WHERE driver_id = v_driver;
    IF FOUND AND p_sequence_id <= v_existing.sequence_id THEN
        RAISE EXCEPTION 'STALE_SEQUENCE_ID';
    END IF;

    INSERT INTO public.driver_presence_snapshot (
        driver_id, active_vehicle_id, market_id, current_state, location, heading, speed_mps, sequence_id, updated_at
    ) VALUES (
        v_driver,
        p_vehicle_id,
        p_market_id,
        p_state,
        extensions.ST_SetSRID(extensions.ST_MakePoint(p_lng, p_lat), 4326),
        p_heading,
        p_speed_mps,
        p_sequence_id,
        clock_timestamp()
    ) ON CONFLICT (driver_id) DO UPDATE SET
        active_vehicle_id = EXCLUDED.active_vehicle_id,
        market_id = EXCLUDED.market_id,
        current_state = EXCLUDED.current_state,
        location = EXCLUDED.location,
        heading = EXCLUDED.heading,
        speed_mps = EXCLUDED.speed_mps,
        sequence_id = EXCLUDED.sequence_id,
        updated_at = EXCLUDED.updated_at;

    RETURN jsonb_build_object(
        'success', TRUE,
        'driver_id', v_driver,
        'state', p_state,
        'sequence_id', p_sequence_id
    );
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 12. RPC: SEARCH DISPATCH CANDIDATES
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.mobility_search_dispatch_candidates(
    p_ride_request_id UUID,
    p_radius_meters DOUBLE PRECISION DEFAULT 5000.0,
    p_limit INTEGER DEFAULT 10
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_req public.ride_requests%ROWTYPE;
    v_candidates JSONB;
BEGIN
    SELECT * INTO v_req FROM public.ride_requests WHERE ride_request_id = p_ride_request_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'RIDE_REQUEST_NOT_FOUND';
    END IF;

    SELECT jsonb_agg(cand) INTO v_candidates FROM (
        SELECT
            p.driver_id,
            p.active_vehicle_id AS vehicle_id,
            extensions.ST_Distance(p.location, v_req.pickup_location) AS distance_meters,
            p.heading,
            p.speed_mps
        FROM public.driver_presence_snapshot p
        WHERE p.market_id = v_req.market_id
          AND p.current_state = 'AVAILABLE'
          AND p.active_vehicle_id IS NOT NULL
          AND extensions.ST_DWithin(p.location, v_req.pickup_location, p_radius_meters)
          AND NOT EXISTS (
              SELECT 1 FROM public.mobility_pair_blocks b
              WHERE ((b.blocker_id = v_req.rider_id AND b.blocked_id = p.driver_id)
                 OR (b.blocker_id = p.driver_id AND b.blocked_id = v_req.rider_id))
                AND b.active = TRUE
          )
        ORDER BY distance_meters ASC
        LIMIT p_limit
    ) cand;

    RETURN jsonb_build_object(
        'ride_request_id', p_ride_request_id,
        'candidates', COALESCE(v_candidates, '[]'::jsonb)
    );
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 13. RPC: ACCEPT DISPATCH (CAS Procedure)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.mobility_accept_dispatch(
    p_ride_request_id UUID,
    p_dispatch_offer_id UUID,
    p_vehicle_id UUID,
    p_expected_ride_version BIGINT,
    p_idempotency_key UUID
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_driver UUID := auth.uid();
    v_hash TEXT;
    v_receipt public.mobility_command_receipts%ROWTYPE;
    v_req public.ride_requests%ROWTYPE;
    v_offer public.dispatch_offers%ROWTYPE;
    v_trip public.trips%ROWTYPE;
    v_response JSONB;
BEGIN
    IF v_driver IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING ERRCODE = '42501';
    END IF;

    IF p_idempotency_key IS NULL THEN
        RAISE EXCEPTION 'IDEMPOTENCY_KEY_REQUIRED';
    END IF;

    v_hash := encode(
        extensions.digest(
            convert_to(
                jsonb_build_object(
                    'ride_request_id', p_ride_request_id,
                    'dispatch_offer_id', p_dispatch_offer_id,
                    'vehicle_id', p_vehicle_id,
                    'expected_version', p_expected_ride_version
                )::TEXT,
                'UTF8'
            ),
            'sha256'
        ),
        'hex'
    );

    PERFORM pg_advisory_xact_lock(hashtextextended(v_driver::TEXT || ':' || p_idempotency_key::TEXT, 0));

    SELECT * INTO v_receipt FROM public.mobility_command_receipts
    WHERE actor_id = v_driver AND command_scope = 'ACCEPT_DISPATCH' AND idempotency_key = p_idempotency_key;

    IF FOUND THEN
        IF v_receipt.request_hash <> v_hash THEN
            RAISE EXCEPTION 'IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD' USING ERRCODE = '23505';
        END IF;
        RETURN v_receipt.response;
    END IF;

    -- Lock ride request to serialize concurrent claims
    PERFORM pg_advisory_xact_lock(hashtextextended('ride_request:' || p_ride_request_id::TEXT, 0));

    SELECT * INTO v_req FROM public.ride_requests
    WHERE ride_request_id = p_ride_request_id FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'RIDE_REQUEST_NOT_FOUND';
    END IF;

    IF v_req.state <> 'SEARCHING' THEN
        RETURN jsonb_build_object(
            'success', FALSE,
            'conflict', TRUE,
            'current_version', v_req.version,
            'error_code', 'ALREADY_MATCHED',
            'message', 'El viaje ya no está disponible para asignación.'
        );
    END IF;

    IF v_req.version <> p_expected_ride_version THEN
        RETURN jsonb_build_object(
            'success', FALSE,
            'conflict', TRUE,
            'current_version', v_req.version,
            'error_code', 'CONCURRENCY_CONFLICT',
            'message', 'Versión del viaje no coincide.'
        );
    END IF;

    SELECT * INTO v_offer FROM public.dispatch_offers
    WHERE dispatch_offer_id = p_dispatch_offer_id AND ride_request_id = p_ride_request_id FOR UPDATE;

    IF NOT FOUND OR v_offer.driver_id <> v_driver OR v_offer.state <> 'PENDING' OR v_offer.expires_at <= clock_timestamp() THEN
        RETURN jsonb_build_object(
            'success', FALSE,
            'conflict', FALSE,
            'error_code', 'OFFER_EXPIRED',
            'message', 'La oferta de despacho no es válida o ha expirado.'
        );
    END IF;

    -- Verify driver and vehicle authorizations
    IF NOT EXISTS (
        SELECT 1 FROM public.driver_market_eligibility
        WHERE driver_id = v_driver AND market_id = v_req.market_id AND is_eligible = TRUE AND active = TRUE
    ) THEN
        RAISE EXCEPTION 'DRIVER_NOT_ELIGIBLE';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM public.driver_vehicle_authorizations a
        JOIN public.mobility_vehicles v ON v.vehicle_id = a.vehicle_id
        WHERE a.driver_id = v_driver AND a.vehicle_id = p_vehicle_id AND a.active = TRUE
          AND v.verification_state = 'VERIFIED' AND v.active = TRUE
    ) THEN
        RAISE EXCEPTION 'VEHICLE_NOT_ELIGIBLE';
    END IF;

    -- CAS Update Ride Request
    UPDATE public.ride_requests
    SET state = 'MATCHED',
        version = version + 1,
        updated_at = clock_timestamp()
    WHERE ride_request_id = p_ride_request_id;

    -- Update offer
    UPDATE public.dispatch_offers
    SET state = 'ACCEPTED'
    WHERE dispatch_offer_id = p_dispatch_offer_id;

    -- Supersede remaining pending offers
    UPDATE public.dispatch_offers
    SET state = 'SUPERSEDED'
    WHERE ride_request_id = p_ride_request_id AND dispatch_offer_id <> p_dispatch_offer_id AND state = 'PENDING';

    -- Insert canonical Trip
    INSERT INTO public.trips (
        ride_request_id,
        rider_id,
        driver_id,
        vehicle_id,
        state,
        verification_pin_hash,
        version,
        assigned_at
    ) VALUES (
        p_ride_request_id,
        v_req.rider_id,
        v_driver,
        p_vehicle_id,
        'ASSIGNED',
        encode(extensions.digest(lpad((floor(random() * 10000)::int)::text, 4, '0'), 'sha256'), 'hex'),
        1,
        clock_timestamp()
    ) RETURNING * INTO v_trip;

    -- Provision realtime memberships
    INSERT INTO public.mobility_realtime_memberships (topic, user_id, role)
    VALUES
        ('trip:' || v_trip.trip_id::TEXT, v_req.rider_id, 'rider'),
        ('trip:' || v_trip.trip_id::TEXT, v_driver, 'driver')
    ON CONFLICT DO NOTHING;

    v_response := jsonb_build_object(
        'success', TRUE,
        'trip', jsonb_build_object(
            'trip_id', v_trip.trip_id,
            'ride_request_id', v_trip.ride_request_id,
            'rider_id', v_trip.rider_id,
            'driver_id', v_trip.driver_id,
            'vehicle_id', v_trip.vehicle_id,
            'state', v_trip.state,
            'version', v_trip.version,
            'assigned_at', v_trip.assigned_at,
            'started_at', v_trip.started_at,
            'completed_at', v_trip.completed_at,
            'created_at', v_trip.created_at,
            'updated_at', v_trip.updated_at
        )
    );

    INSERT INTO public.mobility_command_receipts (
        actor_id, command_scope, idempotency_key, request_hash, requested_aggregate_id, aggregate_id, response
    ) VALUES (
        v_driver, 'ACCEPT_DISPATCH', p_idempotency_key, v_hash, p_ride_request_id, v_trip.trip_id, v_response
    );

    RETURN v_response;
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 14. RPC: DISCOVER REQUESTS (Safe inDrive Discovery)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.mobility_discover_requests(
    p_market_id TEXT,
    p_radius_meters DOUBLE PRECISION DEFAULT 10000.0,
    p_limit INTEGER DEFAULT 20
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_driver UUID := auth.uid();
    v_presence public.driver_presence_snapshot%ROWTYPE;
    v_results JSONB;
BEGIN
    IF v_driver IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING ERRCODE = '42501';
    END IF;

    SELECT * INTO v_presence FROM public.driver_presence_snapshot WHERE driver_id = v_driver;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('requests', '[]'::jsonb);
    END IF;

    SELECT jsonb_agg(req) INTO v_results FROM (
        SELECT
            r.ride_request_id,
            r.dispatch_mode,
            r.service_category_id,
            r.requested_price_minor,
            r.currency_code,
            r.pickup_address,
            r.destination_address,
            round(extensions.ST_Distance(r.pickup_location, v_presence.location)::numeric, 0) AS distance_meters,
            r.version,
            r.created_at
        FROM public.ride_requests r
        WHERE r.market_id = p_market_id
          AND r.state = 'SEARCHING'
          AND extensions.ST_DWithin(r.pickup_location, v_presence.location, p_radius_meters)
          AND NOT EXISTS (
              SELECT 1 FROM public.mobility_pair_blocks b
              WHERE ((b.blocker_id = r.rider_id AND b.blocked_id = v_driver)
                 OR (b.blocker_id = v_driver AND b.blocked_id = r.rider_id))
                AND b.active = TRUE
          )
        ORDER BY distance_meters ASC
        LIMIT p_limit
    ) req;

    RETURN jsonb_build_object('requests', COALESCE(v_results, '[]'::jsonb));
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 15. RPC: SUBMIT DRIVER OFFER (inDrive Bidding)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.mobility_submit_driver_offer(
    p_ride_request_id UUID,
    p_vehicle_id UUID,
    p_offered_price_minor BIGINT,
    p_currency_code TEXT,
    p_pickup_eta_seconds BIGINT DEFAULT NULL,
    p_expected_ride_version BIGINT DEFAULT 1,
    p_idempotency_key UUID DEFAULT NULL
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_driver UUID := auth.uid();
    v_req public.ride_requests%ROWTYPE;
    v_offer public.ride_driver_offers%ROWTYPE;
    v_hash TEXT;
    v_receipt public.mobility_command_receipts%ROWTYPE;
    v_response JSONB;
BEGIN
    IF v_driver IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING ERRCODE = '42501';
    END IF;

    IF p_idempotency_key IS NULL THEN
        RAISE EXCEPTION 'IDEMPOTENCY_KEY_REQUIRED';
    END IF;

    SELECT * INTO v_req FROM public.ride_requests WHERE ride_request_id = p_ride_request_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'RIDE_REQUEST_NOT_FOUND';
    END IF;

    IF v_req.state <> 'SEARCHING' THEN
        RETURN jsonb_build_object('success', FALSE, 'error_code', 'ALREADY_MATCHED', 'message', 'El viaje ya no está en búsqueda.');
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.mobility_pair_blocks b
        WHERE ((b.blocker_id = v_req.rider_id AND b.blocked_id = v_driver)
           OR (b.blocker_id = v_driver AND b.blocked_id = v_req.rider_id))
          AND b.active = TRUE
    ) THEN
        RAISE EXCEPTION 'PAIR_BLOCKED';
    END IF;

    v_hash := encode(
        extensions.digest(
            convert_to(
                jsonb_build_object(
                    'ride_request_id', p_ride_request_id,
                    'vehicle_id', p_vehicle_id,
                    'price', p_offered_price_minor,
                    'currency', p_currency_code,
                    'eta', p_pickup_eta_seconds
                )::TEXT,
                'UTF8'
            ),
            'sha256'
        ),
        'hex'
    );

    PERFORM pg_advisory_xact_lock(hashtextextended(v_driver::TEXT || ':' || p_idempotency_key::TEXT, 0));

    SELECT * INTO v_receipt FROM public.mobility_command_receipts
    WHERE actor_id = v_driver AND command_scope = 'SUBMIT_OFFER' AND idempotency_key = p_idempotency_key;

    IF FOUND THEN
        IF v_receipt.request_hash <> v_hash THEN
            RAISE EXCEPTION 'IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD' USING ERRCODE = '23505';
        END IF;
        RETURN v_receipt.response;
    END IF;

    INSERT INTO public.ride_driver_offers (
        ride_request_id,
        driver_id,
        vehicle_id,
        offered_price_minor,
        currency_code,
        pickup_eta_seconds,
        state,
        expires_at,
        version
    ) VALUES (
        p_ride_request_id,
        v_driver,
        p_vehicle_id,
        p_offered_price_minor,
        p_currency_code,
        p_pickup_eta_seconds,
        'OPEN',
        clock_timestamp() + INTERVAL '3 minutes',
        1
    ) RETURNING * INTO v_offer;

    v_response := jsonb_build_object(
        'success', TRUE,
        'offer_id', v_offer.offer_id,
        'state', v_offer.state,
        'version', v_offer.version,
        'expires_at', v_offer.expires_at
    );

    INSERT INTO public.mobility_command_receipts (
        actor_id, command_scope, idempotency_key, request_hash, requested_aggregate_id, aggregate_id, response
    ) VALUES (
        v_driver, 'SUBMIT_OFFER', p_idempotency_key, v_hash, p_ride_request_id, v_offer.offer_id, v_response
    );

    RETURN v_response;
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 16. RPC: SELECT DRIVER OFFER (inDrive Rider Acceptance CAS)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.mobility_select_driver_offer(
    p_ride_request_id UUID,
    p_offer_id UUID,
    p_expected_ride_version BIGINT,
    p_idempotency_key UUID
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_rider UUID := auth.uid();
    v_hash TEXT;
    v_receipt public.mobility_command_receipts%ROWTYPE;
    v_req public.ride_requests%ROWTYPE;
    v_offer public.ride_driver_offers%ROWTYPE;
    v_trip public.trips%ROWTYPE;
    v_response JSONB;
BEGIN
    IF v_rider IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING ERRCODE = '42501';
    END IF;

    IF p_idempotency_key IS NULL THEN
        RAISE EXCEPTION 'IDEMPOTENCY_KEY_REQUIRED';
    END IF;

    v_hash := encode(
        extensions.digest(
            convert_to(
                jsonb_build_object(
                    'ride_request_id', p_ride_request_id,
                    'offer_id', p_offer_id,
                    'expected_version', p_expected_ride_version
                )::TEXT,
                'UTF8'
            ),
            'sha256'
        ),
        'hex'
    );

    PERFORM pg_advisory_xact_lock(hashtextextended(v_rider::TEXT || ':' || p_idempotency_key::TEXT, 0));

    SELECT * INTO v_receipt FROM public.mobility_command_receipts
    WHERE actor_id = v_rider AND command_scope = 'SELECT_OFFER' AND idempotency_key = p_idempotency_key;

    IF FOUND THEN
        IF v_receipt.request_hash <> v_hash THEN
            RAISE EXCEPTION 'IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD' USING ERRCODE = '23505';
        END IF;
        RETURN v_receipt.response;
    END IF;

    -- Lock ride request
    PERFORM pg_advisory_xact_lock(hashtextextended('ride_request:' || p_ride_request_id::TEXT, 0));

    SELECT * INTO v_req FROM public.ride_requests
    WHERE ride_request_id = p_ride_request_id FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'RIDE_REQUEST_NOT_FOUND';
    END IF;

    IF v_req.rider_id <> v_rider THEN
        RAISE EXCEPTION 'FORBIDDEN' USING ERRCODE = '42501';
    END IF;

    IF v_req.state <> 'SEARCHING' THEN
        RETURN jsonb_build_object(
            'success', FALSE,
            'conflict', TRUE,
            'current_version', v_req.version,
            'error_code', 'ALREADY_MATCHED',
            'message', 'La solicitud ya ha sido emparejada o completada.'
        );
    END IF;

    IF v_req.version <> p_expected_ride_version THEN
        RETURN jsonb_build_object(
            'success', FALSE,
            'conflict', TRUE,
            'current_version', v_req.version,
            'error_code', 'CONCURRENCY_CONFLICT',
            'message', 'Versión de solicitud desactualizada.'
        );
    END IF;

    SELECT * INTO v_offer FROM public.ride_driver_offers
    WHERE offer_id = p_offer_id AND ride_request_id = p_ride_request_id FOR UPDATE;

    IF NOT FOUND OR v_offer.state <> 'OPEN' OR v_offer.expires_at <= clock_timestamp() THEN
        RETURN jsonb_build_object(
            'success', FALSE,
            'conflict', FALSE,
            'error_code', 'OFFER_EXPIRED',
            'message', 'La oferta del conductor ha expirado o ya fue retirada.'
        );
    END IF;

    -- CAS Update Ride Request
    UPDATE public.ride_requests
    SET state = 'MATCHED',
        version = version + 1,
        updated_at = clock_timestamp()
    WHERE ride_request_id = p_ride_request_id;

    -- Update selected offer
    UPDATE public.ride_driver_offers
    SET state = 'SELECTED'
    WHERE offer_id = p_offer_id;

    -- Reject losing offers
    UPDATE public.ride_driver_offers
    SET state = 'REJECTED'
    WHERE ride_request_id = p_ride_request_id AND offer_id <> p_offer_id AND state = 'OPEN';

    -- Insert canonical Trip
    INSERT INTO public.trips (
        ride_request_id,
        rider_id,
        driver_id,
        vehicle_id,
        state,
        verification_pin_hash,
        version,
        assigned_at
    ) VALUES (
        p_ride_request_id,
        v_rider,
        v_offer.driver_id,
        v_offer.vehicle_id,
        'ASSIGNED',
        encode(extensions.digest(lpad((floor(random() * 10000)::int)::text, 4, '0'), 'sha256'), 'hex'),
        1,
        clock_timestamp()
    ) RETURNING * INTO v_trip;

    -- Provision realtime memberships
    INSERT INTO public.mobility_realtime_memberships (topic, user_id, role)
    VALUES
        ('trip:' || v_trip.trip_id::TEXT, v_rider, 'rider'),
        ('trip:' || v_trip.trip_id::TEXT, v_offer.driver_id, 'driver')
    ON CONFLICT DO NOTHING;

    v_response := jsonb_build_object(
        'success', TRUE,
        'trip', jsonb_build_object(
            'trip_id', v_trip.trip_id,
            'ride_request_id', v_trip.ride_request_id,
            'rider_id', v_trip.rider_id,
            'driver_id', v_trip.driver_id,
            'vehicle_id', v_trip.vehicle_id,
            'state', v_trip.state,
            'version', v_trip.version,
            'assigned_at', v_trip.assigned_at,
            'started_at', v_trip.started_at,
            'completed_at', v_trip.completed_at,
            'created_at', v_trip.created_at,
            'updated_at', v_trip.updated_at
        )
    );

    INSERT INTO public.mobility_command_receipts (
        actor_id, command_scope, idempotency_key, request_hash, requested_aggregate_id, aggregate_id, response
    ) VALUES (
        v_rider, 'SELECT_OFFER', p_idempotency_key, v_hash, p_ride_request_id, v_trip.trip_id, v_response
    );

    RETURN v_response;
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 17. RPC: TRANSITION TRIP
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.mobility_transition_trip(
    p_trip_id UUID,
    p_target_state TEXT,
    p_expected_trip_version BIGINT,
    p_verification_pin TEXT DEFAULT NULL,
    p_idempotency_key UUID DEFAULT NULL
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_actor UUID := auth.uid();
    v_trip public.trips%ROWTYPE;
    v_valid_transition BOOLEAN := FALSE;
    v_hash TEXT;
    v_receipt public.mobility_command_receipts%ROWTYPE;
    v_response JSONB;
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING ERRCODE = '42501';
    END IF;

    IF p_idempotency_key IS NULL THEN
        RAISE EXCEPTION 'IDEMPOTENCY_KEY_REQUIRED';
    END IF;

    v_hash := encode(
        extensions.digest(
            convert_to(
                jsonb_build_object(
                    'trip_id', p_trip_id,
                    'target_state', p_target_state,
                    'expected_version', p_expected_trip_version,
                    'pin', p_verification_pin
                )::TEXT,
                'UTF8'
            ),
            'sha256'
        ),
        'hex'
    );

    PERFORM pg_advisory_xact_lock(hashtextextended(v_actor::TEXT || ':' || p_idempotency_key::TEXT, 0));

    SELECT * INTO v_receipt FROM public.mobility_command_receipts
    WHERE actor_id = v_actor AND command_scope = 'TRANSITION_TRIP' AND idempotency_key = p_idempotency_key;

    IF FOUND THEN
        IF v_receipt.request_hash <> v_hash THEN
            RAISE EXCEPTION 'IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD' USING ERRCODE = '23505';
        END IF;
        RETURN v_receipt.response;
    END IF;

    PERFORM pg_advisory_xact_lock(hashtextextended('trip:' || p_trip_id::TEXT, 0));

    SELECT * INTO v_trip FROM public.trips WHERE trip_id = p_trip_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'TRIP_NOT_FOUND';
    END IF;

    IF v_trip.version <> p_expected_trip_version THEN
        RETURN jsonb_build_object(
            'success', FALSE,
            'conflict', TRUE,
            'current_version', v_trip.version,
            'error_code', 'CONCURRENCY_CONFLICT',
            'message', 'Versión del viaje desactualizada.'
        );
    END IF;

    -- Validate actor authorization
    IF v_actor <> v_trip.driver_id AND v_actor <> v_trip.rider_id THEN
        RAISE EXCEPTION 'FORBIDDEN' USING ERRCODE = '42501';
    END IF;

    -- State transition validation
    v_valid_transition := CASE
        WHEN v_trip.state = 'ASSIGNED' AND p_target_state = 'DRIVER_EN_ROUTE' AND v_actor = v_trip.driver_id THEN TRUE
        WHEN v_trip.state = 'DRIVER_EN_ROUTE' AND p_target_state = 'DRIVER_ARRIVED' AND v_actor = v_trip.driver_id THEN TRUE
        WHEN v_trip.state = 'DRIVER_ARRIVED' AND p_target_state = 'WAITING_FOR_RIDER' AND v_actor = v_trip.driver_id THEN TRUE
        WHEN v_trip.state IN ('DRIVER_ARRIVED', 'WAITING_FOR_RIDER') AND p_target_state = 'RIDER_ONBOARD' AND v_actor = v_trip.driver_id THEN TRUE
        WHEN v_trip.state = 'RIDER_ONBOARD' AND p_target_state = 'IN_PROGRESS' AND v_actor = v_trip.driver_id THEN TRUE
        WHEN v_trip.state = 'IN_PROGRESS' AND p_target_state = 'ARRIVED_DESTINATION' AND v_actor = v_trip.driver_id THEN TRUE
        WHEN v_trip.state = 'ARRIVED_DESTINATION' AND p_target_state = 'COMPLETED' AND v_actor = v_trip.driver_id THEN TRUE
        WHEN v_trip.state NOT IN ('COMPLETED', 'CANCELLED', 'DISPUTED') AND p_target_state = 'CANCELLED' THEN TRUE
        WHEN v_trip.state IN ('COMPLETED', 'CANCELLED') AND p_target_state = 'DISPUTED' THEN TRUE
        ELSE FALSE
    END;

    IF NOT v_valid_transition THEN
        RETURN jsonb_build_object(
            'success', FALSE,
            'conflict', FALSE,
            'error_code', 'INVALID_TRANSITION',
            'message', 'Transición de estado no permitida para el rol actual.'
        );
    END IF;

    -- PIN verification on boarding
    IF p_target_state = 'RIDER_ONBOARD' AND v_trip.verification_pin_hash IS NOT NULL THEN
        IF p_verification_pin IS NULL OR p_verification_pin = '' THEN
            RETURN jsonb_build_object(
                'success', FALSE,
                'conflict', FALSE,
                'error_code', 'PIN_REQUIRED',
                'message', 'Se requiere el código PIN de verificación para iniciar el abordaje.'
            );
        END IF;

        IF encode(extensions.digest(p_verification_pin, 'sha256'), 'hex') <> v_trip.verification_pin_hash THEN
            RETURN jsonb_build_object(
                'success', FALSE,
                'conflict', FALSE,
                'error_code', 'PIN_INVALID',
                'message', 'El código PIN ingresado es incorrecto.'
            );
        END IF;
    END IF;

    UPDATE public.trips
    SET state = p_target_state,
        version = version + 1,
        started_at = CASE WHEN p_target_state = 'IN_PROGRESS' AND started_at IS NULL THEN clock_timestamp() ELSE started_at END,
        completed_at = CASE WHEN p_target_state = 'COMPLETED' AND completed_at IS NULL THEN clock_timestamp() ELSE completed_at END,
        updated_at = clock_timestamp()
    WHERE trip_id = p_trip_id
    RETURNING * INTO v_trip;

    v_response := jsonb_build_object(
        'success', TRUE,
        'trip', jsonb_build_object(
            'trip_id', v_trip.trip_id,
            'ride_request_id', v_trip.ride_request_id,
            'rider_id', v_trip.rider_id,
            'driver_id', v_trip.driver_id,
            'vehicle_id', v_trip.vehicle_id,
            'state', v_trip.state,
            'version', v_trip.version,
            'assigned_at', v_trip.assigned_at,
            'started_at', v_trip.started_at,
            'completed_at', v_trip.completed_at,
            'created_at', v_trip.created_at,
            'updated_at', v_trip.updated_at
        )
    );

    INSERT INTO public.mobility_command_receipts (
        actor_id, command_scope, idempotency_key, request_hash, requested_aggregate_id, aggregate_id, response
    ) VALUES (
        v_actor, 'TRANSITION_TRIP', p_idempotency_key, v_hash, p_trip_id, v_trip.trip_id, v_response
    );

    RETURN v_response;
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 18. RPC: CANCEL RIDE
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.mobility_cancel_ride(
    p_ride_request_id UUID,
    p_reason TEXT,
    p_idempotency_key UUID
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_actor UUID := auth.uid();
    v_req public.ride_requests%ROWTYPE;
    v_hash TEXT;
    v_receipt public.mobility_command_receipts%ROWTYPE;
    v_response JSONB;
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING ERRCODE = '42501';
    END IF;

    IF p_idempotency_key IS NULL THEN
        RAISE EXCEPTION 'IDEMPOTENCY_KEY_REQUIRED';
    END IF;

    v_hash := encode(
        extensions.digest(
            convert_to(
                jsonb_build_object('ride_request_id', p_ride_request_id, 'reason', p_reason)::TEXT,
                'UTF8'
            ),
            'sha256'
        ),
        'hex'
    );

    PERFORM pg_advisory_xact_lock(hashtextextended(v_actor::TEXT || ':' || p_idempotency_key::TEXT, 0));

    SELECT * INTO v_receipt FROM public.mobility_command_receipts
    WHERE actor_id = v_actor AND command_scope = 'CANCEL_RIDE' AND idempotency_key = p_idempotency_key;

    IF FOUND THEN
        IF v_receipt.request_hash <> v_hash THEN
            RAISE EXCEPTION 'IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD' USING ERRCODE = '23505';
        END IF;
        RETURN v_receipt.response;
    END IF;

    PERFORM pg_advisory_xact_lock(hashtextextended('ride_request:' || p_ride_request_id::TEXT, 0));

    SELECT * INTO v_req FROM public.ride_requests WHERE ride_request_id = p_ride_request_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'RIDE_REQUEST_NOT_FOUND';
    END IF;

    IF v_actor <> v_req.rider_id THEN
        RAISE EXCEPTION 'FORBIDDEN' USING ERRCODE = '42501';
    END IF;

    IF v_req.state IN ('CANCELLED', 'EXPIRED') THEN
        RETURN jsonb_build_object('success', TRUE, 'ride_request', row_to_json(v_req));
    END IF;

    UPDATE public.ride_requests
    SET state = 'CANCELLED',
        version = version + 1,
        updated_at = clock_timestamp()
    WHERE ride_request_id = p_ride_request_id
    RETURNING * INTO v_req;

    -- Cancel associated trip if not started
    UPDATE public.trips
    SET state = 'CANCELLED',
        version = version + 1,
        updated_at = clock_timestamp()
    WHERE ride_request_id = p_ride_request_id AND state IN ('ASSIGNED', 'DRIVER_EN_ROUTE', 'DRIVER_ARRIVED', 'WAITING_FOR_RIDER');

    v_response := jsonb_build_object(
        'success', TRUE,
        'ride_request', row_to_json(v_req)
    );

    INSERT INTO public.mobility_command_receipts (
        actor_id, command_scope, idempotency_key, request_hash, requested_aggregate_id, aggregate_id, response
    ) VALUES (
        v_actor, 'CANCEL_RIDE', p_idempotency_key, v_hash, p_ride_request_id, v_req.ride_request_id, v_response
    );

    RETURN v_response;
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 19. GRANT EXECUTE ON ALL MOBILITY RPCS TO AUTHENTICATED
-- ─────────────────────────────────────────────────────────────────────────────

REVOKE ALL ON FUNCTION public.mobility_request_ride FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobility_request_ride TO authenticated;

REVOKE ALL ON FUNCTION public.mobility_update_driver_presence FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobility_update_driver_presence TO authenticated;

REVOKE ALL ON FUNCTION public.mobility_search_dispatch_candidates FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobility_search_dispatch_candidates TO authenticated;

REVOKE ALL ON FUNCTION public.mobility_accept_dispatch FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobility_accept_dispatch TO authenticated;

REVOKE ALL ON FUNCTION public.mobility_discover_requests FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobility_discover_requests TO authenticated;

REVOKE ALL ON FUNCTION public.mobility_submit_driver_offer FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobility_submit_driver_offer TO authenticated;

REVOKE ALL ON FUNCTION public.mobility_select_driver_offer FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobility_select_driver_offer TO authenticated;

REVOKE ALL ON FUNCTION public.mobility_transition_trip FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobility_transition_trip TO authenticated;

REVOKE ALL ON FUNCTION public.mobility_cancel_ride FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobility_cancel_ride TO authenticated;

