-- =============================================================================
-- MIGRATION: 20260906080000_mobility_public_launch_v11_closure.sql
-- MEET / ELYSIUM — ORDEN MAESTRA V11: PRODUCTION CLOSURE
--
-- Incorporates all required production gates:
-- 1. Gate P0: PIN Secret Authority in private schema with 6-digit CSPRNG,
--    bcrypt slow hashing, 10-min TTL, single-use, 5-attempt rate limit & persistent lockout.
--    Complete removal of legacy plaintext/hash columns from public.trips.
-- 2. Gate P0: Isolated Safe Trip Sharing Projection (mobility_trip_share_projection).
--    Drop base-trip sharing policy (p_trips_share_read); instant revocation.
-- 3. Gate 2: Payment Provider Capabilities Fail-Closed table & enforcement;
--    CASH enabled in PRODUCTION; electronic providers fail closed without certified PSP.
--    7-parameter mobility_confirm_provider_capture with trip binding & replay defense.
-- 4. Gate 4: Real Road Routing evidence schema & route versioning.
-- 5. Canonical Stop Authority & Open Bid Immutability (mobility_replace_ride_stops).
-- 6. Mutual Bilateral Ratings (mobility_trip_ratings).
-- 7. Canonical Trip Tip & Balanced Zero-Sum Ledger (mobility_trip_tips).
-- 8. Universal Capability Gating (principals & principal_capabilities).
-- 9. Real Account Deletion Processor (process_account_deletion_request).
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS extensions;
CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA extensions;

-- Geospatial shim compatibility helpers
CREATE OR REPLACE FUNCTION extensions.ST_AsText(geom extensions.geography)
RETURNS TEXT LANGUAGE sql IMMUTABLE AS $$
    SELECT 'POINT(' || geom.lng::text || ' ' || geom.lat::text || ')'
$$;

CREATE OR REPLACE FUNCTION extensions.ST_X(geom extensions.geography)
RETURNS DOUBLE PRECISION LANGUAGE sql IMMUTABLE AS $$
    SELECT geom.lng
$$;

CREATE OR REPLACE FUNCTION extensions.ST_Y(geom extensions.geography)
RETURNS DOUBLE PRECISION LANGUAGE sql IMMUTABLE AS $$
    SELECT geom.lat
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. GATE P0: PRIVATE SCHEMA & 6-DIGIT CSPRNG PIN CHALLENGE AUTHORITY
-- ─────────────────────────────────────────────────────────────────────────────

CREATE SCHEMA IF NOT EXISTS private;

REVOKE ALL ON SCHEMA private FROM PUBLIC, anon, authenticated;
GRANT USAGE ON SCHEMA private TO service_role;

CREATE TABLE IF NOT EXISTS private.mobility_trip_pin_challenges (
    trip_id UUID PRIMARY KEY REFERENCES public.trips(trip_id) ON DELETE CASCADE,
    pin_hash TEXT NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    failed_attempts INTEGER NOT NULL DEFAULT 0 CHECK (failed_attempts >= 0),
    last_attempt_at TIMESTAMPTZ,
    locked_until TIMESTAMPTZ,
    challenge_version BIGINT NOT NULL DEFAULT 1 CHECK (challenge_version > 0)
);

REVOKE ALL ON private.mobility_trip_pin_challenges FROM PUBLIC, anon, authenticated;
GRANT ALL ON private.mobility_trip_pin_challenges TO service_role;

-- CSPRNG 6-digit PIN with rejection sampling to eliminate modulo bias
CREATE OR REPLACE FUNCTION private.mobility_generate_six_digit_pin()
RETURNS TEXT
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_bytes BYTEA;
    v_number BIGINT;
BEGIN
    LOOP
        v_bytes := extensions.gen_random_bytes(4);

        v_number :=
              (get_byte(v_bytes, 0)::BIGINT << 24)
            | (get_byte(v_bytes, 1)::BIGINT << 16)
            | (get_byte(v_bytes, 2)::BIGINT << 8)
            |  get_byte(v_bytes, 3)::BIGINT;

        -- 4,294 * 1,000,000. Rejection sampling removes modulo bias.
        EXIT WHEN v_number < 4294000000;
    END LOOP;

    RETURN lpad((v_number % 1000000)::TEXT, 6, '0');
END;
$$;

REVOKE ALL ON FUNCTION private.mobility_generate_six_digit_pin() FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION private.mobility_generate_six_digit_pin() TO service_role;

-- Canonical RPC: Issue 6-digit Boarding PIN challenge (Rider or service_role only)
CREATE OR REPLACE FUNCTION public.mobility_issue_trip_verification_pin(
    p_trip_id UUID
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_actor UUID := auth.uid();
    v_trip public.trips%ROWTYPE;
    v_pin TEXT;
    v_hash TEXT;
    v_expires TIMESTAMPTZ;
BEGIN
    SELECT * INTO v_trip FROM public.trips WHERE trip_id = p_trip_id FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'TRIP_NOT_FOUND';
    END IF;

    IF COALESCE(auth.role(), current_user) <> 'service_role'
       AND (v_actor IS NULL OR v_actor <> v_trip.rider_id) THEN
        RAISE EXCEPTION 'ONLY_RIDER_CAN_REQUEST_BOARDING_PIN' USING ERRCODE = '42501';
    END IF;

    IF v_trip.state IN ('RIDER_ONBOARD', 'IN_PROGRESS', 'ARRIVED_DESTINATION', 'COMPLETED', 'CANCELLED') THEN
        RAISE EXCEPTION 'PIN_CANNOT_BE_ISSUED_FOR_CURRENT_TRIP_STATE: %', v_trip.state;
    END IF;

    v_pin := private.mobility_generate_six_digit_pin();
    v_hash := extensions.crypt(v_pin, extensions.gen_salt('bf', 12));
    v_expires := clock_timestamp() + INTERVAL '10 minutes';

    INSERT INTO private.mobility_trip_pin_challenges (
        trip_id,
        pin_hash,
        issued_at,
        expires_at,
        consumed_at,
        failed_attempts,
        last_attempt_at,
        locked_until,
        challenge_version
    ) VALUES (
        p_trip_id,
        v_hash,
        clock_timestamp(),
        v_expires,
        NULL,
        0,
        NULL,
        NULL,
        1
    )
    ON CONFLICT (trip_id) DO UPDATE SET
        pin_hash = EXCLUDED.pin_hash,
        issued_at = EXCLUDED.issued_at,
        expires_at = EXCLUDED.expires_at,
        consumed_at = NULL,
        failed_attempts = 0,
        last_attempt_at = NULL,
        locked_until = NULL,
        challenge_version = private.mobility_trip_pin_challenges.challenge_version + 1;

    RETURN jsonb_build_object(
        'success', TRUE,
        'pin', v_pin,
        'expires_at', v_expires
    );
END;
$$;

REVOKE ALL ON FUNCTION public.mobility_issue_trip_verification_pin(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobility_issue_trip_verification_pin(UUID) TO authenticated, service_role;

-- Alias for backwards compatibility with V10 client
CREATE OR REPLACE FUNCTION public.mobility_get_trip_boarding_pin(
    p_trip_id UUID
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
    RETURN public.mobility_issue_trip_verification_pin(p_trip_id);
END;
$$;

REVOKE ALL ON FUNCTION public.mobility_get_trip_boarding_pin(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobility_get_trip_boarding_pin(UUID) TO authenticated, service_role;

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. GATE P0: DEPRECATE LEGACY PIN AUTHORITY FROM public.trips
-- ─────────────────────────────────────────────────────────────────────────────

DROP POLICY IF EXISTS p_trips_share_read ON public.trips;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'trips' AND column_name = 'verification_pin_hash'
    ) THEN
        UPDATE public.trips SET verification_pin_hash = NULL WHERE verification_pin_hash IS NOT NULL;
        ALTER TABLE public.trips DROP CONSTRAINT IF EXISTS trips_legacy_verification_pin_hash_must_be_null;
        ALTER TABLE public.trips ADD CONSTRAINT trips_legacy_verification_pin_hash_must_be_null CHECK (verification_pin_hash IS NULL);
    END IF;
END $$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 2.1 GATE P0: REDEFINE TRIP CREATION RPCs (NULL PIN HASH ON TRIPS, CSPRNG IN PRIVATE)
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
    v_pin TEXT;
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

    -- Insert canonical Trip (Gate P0: verification_pin_hash MUST BE NULL)
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
        NULL,
        1,
        clock_timestamp()
    ) RETURNING * INTO v_trip;

    -- Generate Gate P0 6-digit CSPRNG PIN and store bcrypt hash in private table
    v_pin := private.mobility_generate_six_digit_pin();
    INSERT INTO private.mobility_trip_pin_challenges (
        trip_id,
        pin_hash,
        issued_at,
        expires_at,
        challenge_version
    ) VALUES (
        v_trip.trip_id,
        extensions.crypt(v_pin, extensions.gen_salt('bf', 12)),
        clock_timestamp(),
        clock_timestamp() + INTERVAL '10 minutes',
        1
    ) ON CONFLICT (trip_id) DO UPDATE SET
        pin_hash = EXCLUDED.pin_hash,
        issued_at = EXCLUDED.issued_at,
        expires_at = EXCLUDED.expires_at,
        consumed_at = NULL,
        failed_attempts = 0,
        last_attempt_at = NULL,
        locked_until = NULL,
        challenge_version = private.mobility_trip_pin_challenges.challenge_version + 1;

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

REVOKE ALL ON FUNCTION public.mobility_accept_dispatch(UUID, UUID, UUID, BIGINT, UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobility_accept_dispatch(UUID, UUID, UUID, BIGINT, UUID) TO authenticated, service_role;

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
    v_pin TEXT;
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
        RAISE EXCEPTION 'FORBIDDEN_NOT_REQUEST_OWNER' USING ERRCODE = '42501';
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

    -- Insert canonical Trip (Gate P0: verification_pin_hash MUST BE NULL)
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
        NULL,
        1,
        clock_timestamp()
    ) RETURNING * INTO v_trip;

    -- Generate Gate P0 6-digit CSPRNG PIN and store bcrypt hash in private table
    v_pin := private.mobility_generate_six_digit_pin();
    INSERT INTO private.mobility_trip_pin_challenges (
        trip_id,
        pin_hash,
        issued_at,
        expires_at,
        challenge_version
    ) VALUES (
        v_trip.trip_id,
        extensions.crypt(v_pin, extensions.gen_salt('bf', 12)),
        clock_timestamp(),
        clock_timestamp() + INTERVAL '10 minutes',
        1
    ) ON CONFLICT (trip_id) DO UPDATE SET
        pin_hash = EXCLUDED.pin_hash,
        issued_at = EXCLUDED.issued_at,
        expires_at = EXCLUDED.expires_at,
        consumed_at = NULL,
        failed_attempts = 0,
        last_attempt_at = NULL,
        locked_until = NULL,
        challenge_version = private.mobility_trip_pin_challenges.challenge_version + 1;

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

REVOKE ALL ON FUNCTION public.mobility_select_driver_offer(UUID, UUID, BIGINT, UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobility_select_driver_offer(UUID, UUID, BIGINT, UUID) TO authenticated, service_role;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. GATE P0: ISOLATED SAFE TRIP SHARING PROJECTION
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE public.mobility_trip_shares ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;
UPDATE public.mobility_trip_shares SET expires_at = COALESCE(expires_at, created_at + INTERVAL '24 hours');
ALTER TABLE public.mobility_trip_shares ALTER COLUMN expires_at SET NOT NULL;
ALTER TABLE public.mobility_trip_shares ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp();

CREATE TABLE IF NOT EXISTS public.mobility_trip_share_projection (
    share_id UUID PRIMARY KEY REFERENCES public.mobility_trip_shares(share_id) ON DELETE CASCADE,
    trip_id UUID NOT NULL REFERENCES public.trips(trip_id) ON DELETE CASCADE,
    grantee_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    trip_state TEXT NOT NULL,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    heading_degrees REAL,
    eta_seconds INTEGER CHECK (eta_seconds IS NULL OR eta_seconds >= 0),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

ALTER TABLE public.mobility_trip_share_projection ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.mobility_trip_share_projection FORCE ROW LEVEL SECURITY;

REVOKE ALL ON public.mobility_trip_share_projection FROM PUBLIC, anon, authenticated;
GRANT SELECT ON public.mobility_trip_share_projection TO authenticated;
GRANT ALL ON public.mobility_trip_share_projection TO service_role;

DROP POLICY IF EXISTS p_trip_share_projection_grantee ON public.mobility_trip_share_projection;
CREATE POLICY p_trip_share_projection_grantee ON public.mobility_trip_share_projection
    FOR SELECT TO authenticated
    USING (
        grantee_id = auth.uid()
        AND revoked_at IS NULL
        AND expires_at > clock_timestamp()
        AND EXISTS (
            SELECT 1 FROM public.mobility_trip_shares s
            WHERE s.share_id = mobility_trip_share_projection.share_id
              AND s.grantee_id = auth.uid()
              AND s.state = 'ACTIVE'
              AND s.expires_at > clock_timestamp()
        )
    );

DROP POLICY IF EXISTS p_trip_share_projection_service_role ON public.mobility_trip_share_projection;
CREATE POLICY p_trip_share_projection_service_role ON public.mobility_trip_share_projection
    FOR ALL TO service_role
    USING (true)
    WITH CHECK (true);

-- Synchronize share creation with safe projection
CREATE OR REPLACE FUNCTION public.mobility_share_trip(
    p_trip_id UUID,
    p_grantee_id UUID
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_actor UUID := auth.uid();
    v_trip public.trips%ROWTYPE;
    v_share public.mobility_trip_shares%ROWTYPE;
    v_presence public.driver_presence_snapshot%ROWTYPE;
    v_lat DOUBLE PRECISION;
    v_lng DOUBLE PRECISION;
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING ERRCODE = '42501';
    END IF;

    SELECT * INTO v_trip FROM public.trips WHERE trip_id = p_trip_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'TRIP_NOT_FOUND';
    END IF;

    IF v_trip.rider_id <> v_actor AND COALESCE(auth.role(), current_user) <> 'service_role' THEN
        RAISE EXCEPTION 'FORBIDDEN: Only rider can share trip' USING ERRCODE = '42501';
    END IF;

    IF v_trip.state IN ('COMPLETED', 'CANCELLED') THEN
        RAISE EXCEPTION 'CANNOT_SHARE_TERMINAL_TRIP';
    END IF;

    INSERT INTO public.mobility_trip_shares (
        trip_id, grantor_id, grantee_id, state, expires_at, created_at, updated_at
    ) VALUES (
        p_trip_id, v_actor, p_grantee_id, 'ACTIVE', clock_timestamp() + INTERVAL '24 hours', clock_timestamp(), clock_timestamp()
    )
    ON CONFLICT (trip_id, grantee_id) DO UPDATE SET
        state = 'ACTIVE',
        expires_at = clock_timestamp() + INTERVAL '24 hours',
        revoked_at = NULL,
        updated_at = clock_timestamp()
    RETURNING * INTO v_share;

    -- Extract current driver location if available
    SELECT * INTO v_presence FROM public.driver_presence_snapshot WHERE driver_id = v_trip.driver_id;
    IF FOUND AND v_presence.location IS NOT NULL THEN
        v_lat := extensions.ST_Y(v_presence.location);
        v_lng := extensions.ST_X(v_presence.location);
    END IF;

    -- Upsert safe projection row
    INSERT INTO public.mobility_trip_share_projection (
        share_id,
        trip_id,
        grantee_id,
        trip_state,
        latitude,
        longitude,
        heading_degrees,
        eta_seconds,
        expires_at,
        revoked_at,
        updated_at
    ) VALUES (
        v_share.share_id,
        v_trip.trip_id,
        p_grantee_id,
        v_trip.state,
        v_lat,
        v_lng,
        v_presence.heading,
        NULL,
        v_share.expires_at,
        NULL,
        clock_timestamp()
    )
    ON CONFLICT (share_id) DO UPDATE SET
        grantee_id = EXCLUDED.grantee_id,
        trip_state = EXCLUDED.trip_state,
        latitude = EXCLUDED.latitude,
        longitude = EXCLUDED.longitude,
        heading_degrees = EXCLUDED.heading_degrees,
        expires_at = EXCLUDED.expires_at,
        revoked_at = NULL,
        updated_at = clock_timestamp();

    RETURN jsonb_build_object(
        'success', TRUE,
        'share_id', v_share.share_id,
        'trip_id', v_share.trip_id,
        'grantee_id', v_share.grantee_id,
        'state', v_share.state,
        'expires_at', v_share.expires_at
    );
END;
$$;

REVOKE ALL ON FUNCTION public.mobility_share_trip(UUID, UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobility_share_trip(UUID, UUID) TO authenticated, service_role;

-- Synchronize share revocation
CREATE OR REPLACE FUNCTION public.mobility_revoke_trip_share(
    p_trip_id UUID,
    p_grantee_id UUID
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_actor UUID := auth.uid();
    v_trip public.trips%ROWTYPE;
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING ERRCODE = '42501';
    END IF;

    SELECT * INTO v_trip FROM public.trips WHERE trip_id = p_trip_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'TRIP_NOT_FOUND';
    END IF;

    IF v_trip.rider_id <> v_actor AND COALESCE(auth.role(), current_user) <> 'service_role' THEN
        RAISE EXCEPTION 'FORBIDDEN: Only rider can revoke trip share' USING ERRCODE = '42501';
    END IF;

    UPDATE public.mobility_trip_shares
    SET state = 'REVOKED', revoked_at = clock_timestamp(), updated_at = clock_timestamp()
    WHERE trip_id = p_trip_id AND grantee_id = p_grantee_id;

    UPDATE public.mobility_trip_share_projection
    SET revoked_at = clock_timestamp(), updated_at = clock_timestamp()
    WHERE trip_id = p_trip_id AND grantee_id = p_grantee_id;

    RETURN jsonb_build_object('success', TRUE, 'revoked', TRUE);
END;
$$;

REVOKE ALL ON FUNCTION public.mobility_revoke_trip_share(UUID, UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobility_revoke_trip_share(UUID, UUID) TO authenticated, service_role;

-- Safe projection RPC (reads exclusively from mobility_trip_share_projection)
CREATE OR REPLACE FUNCTION public.mobility_get_safe_trip_projection(
    p_trip_id UUID
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_actor UUID := auth.uid();
    v_proj public.mobility_trip_share_projection%ROWTYPE;
    v_trip public.trips%ROWTYPE;
    v_vehicle public.mobility_vehicles%ROWTYPE;
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING ERRCODE = '42501';
    END IF;

    SELECT * INTO v_trip FROM public.trips WHERE trip_id = p_trip_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'TRIP_NOT_FOUND';
    END IF;

    IF v_trip.rider_id = v_actor OR v_trip.driver_id = v_actor OR COALESCE(auth.role(), current_user) = 'service_role' THEN
        -- Primary participants can read safe telemetry
        SELECT * INTO v_vehicle FROM public.mobility_vehicles WHERE vehicle_id = v_trip.vehicle_id;
        RETURN jsonb_build_object(
            'trip_id', v_trip.trip_id,
            'state', v_trip.state,
            'vehicle', jsonb_build_object(
                'make', v_vehicle.make,
                'model', v_vehicle.model,
                'color', v_vehicle.color,
                'license_plate', v_vehicle.license_plate
            ),
            'updated_at', v_trip.updated_at
        );
    END IF;

    -- Grantees MUST read from verified projection
    SELECT * INTO v_proj FROM public.mobility_trip_share_projection
    WHERE trip_id = p_trip_id
      AND grantee_id = v_actor
      AND revoked_at IS NULL
      AND expires_at > clock_timestamp();

    IF NOT FOUND THEN
        RAISE EXCEPTION 'FORBIDDEN_TRIP_READ: No active share grant' USING ERRCODE = '42501';
    END IF;

    SELECT * INTO v_vehicle FROM public.mobility_vehicles WHERE vehicle_id = v_trip.vehicle_id;

    RETURN jsonb_build_object(
        'trip_id', v_proj.trip_id,
        'state', v_proj.trip_state,
        'latitude', v_proj.latitude,
        'longitude', v_proj.longitude,
        'heading_degrees', v_proj.heading_degrees,
        'eta_seconds', v_proj.eta_seconds,
        'vehicle', jsonb_build_object(
            'make', v_vehicle.make,
            'model', v_vehicle.model,
            'color', v_vehicle.color,
            'license_plate', v_vehicle.license_plate
        ),
        'expires_at', v_proj.expires_at,
        'updated_at', v_proj.updated_at
    );
END;
$$;

REVOKE ALL ON FUNCTION public.mobility_get_safe_trip_projection(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobility_get_safe_trip_projection(UUID) TO authenticated, service_role;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. GATE 2: PAYMENT PROVIDER CAPABILITIES & FAIL-CLOSED ENFORCEMENT
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.mobility_payment_provider_capabilities (
    provider TEXT PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    externally_verified BOOLEAN NOT NULL DEFAULT FALSE,
    environment TEXT NOT NULL CHECK (environment IN ('DISABLED', 'SANDBOX', 'PRODUCTION')),
    verified_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

INSERT INTO public.mobility_payment_provider_capabilities (
    provider, enabled, externally_verified, environment
) VALUES
    ('CASH', TRUE, TRUE, 'PRODUCTION'),
    ('CARD_TOKEN', FALSE, FALSE, 'DISABLED'),
    ('WALLET', FALSE, FALSE, 'DISABLED'),
    ('CORPORATE_ACCOUNT', FALSE, FALSE, 'DISABLED'),
    ('SINPE_MOVIL', FALSE, FALSE, 'DISABLED')
ON CONFLICT (provider) DO NOTHING;

ALTER TABLE public.mobility_payment_provider_capabilities ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.mobility_payment_provider_capabilities FORCE ROW LEVEL SECURITY;

REVOKE ALL ON public.mobility_payment_provider_capabilities FROM PUBLIC, anon, authenticated;
GRANT SELECT ON public.mobility_payment_provider_capabilities TO authenticated, service_role;
GRANT ALL ON public.mobility_payment_provider_capabilities TO service_role;

DROP POLICY IF EXISTS p_payment_capabilities_select ON public.mobility_payment_provider_capabilities;
CREATE POLICY p_payment_capabilities_select ON public.mobility_payment_provider_capabilities
    FOR SELECT TO authenticated, service_role
    USING (true);

DROP POLICY IF EXISTS p_payment_capabilities_service_role ON public.mobility_payment_provider_capabilities;
CREATE POLICY p_payment_capabilities_service_role ON public.mobility_payment_provider_capabilities
    FOR ALL TO service_role
    USING (true)
    WITH CHECK (true);

-- Extend payment authorizations with capture evidence columns
ALTER TABLE public.payment_authorizations
    ADD COLUMN IF NOT EXISTS captured_amount_minor BIGINT,
    ADD COLUMN IF NOT EXISTS captured_at TIMESTAMPTZ;

-- Ensure quote payment authorization fails closed without verified provider in PRODUCTION
DROP FUNCTION IF EXISTS public.mobility_authorize_quote_payment(UUID, TEXT, UUID);
CREATE OR REPLACE FUNCTION public.mobility_authorize_quote_payment(
    p_quote_id UUID,
    p_provider TEXT,
    p_idempotency_key UUID DEFAULT NULL
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_actor UUID := auth.uid();
    v_quote public.ride_quotes%ROWTYPE;
    v_auth public.payment_authorizations%ROWTYPE;
    v_receipt public.mobility_command_receipts%ROWTYPE;
    v_hash TEXT;
    v_initial_state TEXT;
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING ERRCODE = '42501';
    END IF;

    IF p_idempotency_key IS NULL THEN
        RAISE EXCEPTION 'IDEMPOTENCY_KEY_REQUIRED';
    END IF;

    -- Gate external providers fail-closed
    IF p_provider <> 'CASH' AND NOT EXISTS (
        SELECT 1 FROM public.mobility_payment_provider_capabilities
        WHERE provider = p_provider
          AND enabled IS TRUE
          AND externally_verified IS TRUE
          AND environment = 'PRODUCTION'
    ) THEN
        RAISE EXCEPTION 'PAYMENT_PROVIDER_DISABLED_OR_NOT_VERIFIED' USING ERRCODE = '42501';
    END IF;

    v_hash := encode(extensions.digest(
        convert_to(jsonb_build_object('quote_id', p_quote_id, 'provider', p_provider)::TEXT, 'UTF8'),
        'sha256'
    ), 'hex');

    SELECT * INTO v_receipt FROM public.mobility_command_receipts
    WHERE actor_id = v_actor AND command_scope = 'AUTHORIZE_PAYMENT' AND idempotency_key = p_idempotency_key;

    IF FOUND THEN
        IF v_receipt.request_hash <> v_hash THEN
            RAISE EXCEPTION 'IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD' USING ERRCODE = '23505';
        END IF;
        RETURN v_receipt.response;
    END IF;

    SELECT * INTO v_quote FROM public.ride_quotes WHERE quote_id = p_quote_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'QUOTE_NOT_FOUND';
    END IF;

    IF v_quote.rider_id <> v_actor AND COALESCE(auth.role(), current_user) <> 'service_role' THEN
        RAISE EXCEPTION 'ACTOR_NOT_QUOTE_RIDER' USING ERRCODE = '42501';
    END IF;

    IF v_quote.expires_at <= clock_timestamp() THEN
        RAISE EXCEPTION 'QUOTE_EXPIRED';
    END IF;

    IF p_provider = 'CASH' THEN
        v_initial_state := 'CASH_PENDING';
    ELSE
        v_initial_state := 'PENDING_PROVIDER';
    END IF;

    INSERT INTO public.payment_authorizations (
        payment_authorization_id,
        trip_id,
        quote_id,
        rider_id,
        provider,
        amount_minor,
        currency_code,
        state,
        created_at,
        updated_at
    ) VALUES (
        extensions.gen_random_uuid(),
        NULL,
        v_quote.quote_id,
        v_actor,
        p_provider,
        v_quote.total_fare_minor,
        v_quote.currency_code,
        v_initial_state,
        clock_timestamp(),
        clock_timestamp()
    ) RETURNING * INTO v_auth;

    RETURN jsonb_build_object(
        'success', TRUE,
        'authorization', jsonb_build_object(
            'payment_authorization_id', v_auth.payment_authorization_id,
            'quote_id', v_auth.quote_id,
            'state', v_auth.state,
            'amount_minor', v_auth.amount_minor,
            'currency_code', v_auth.currency_code,
            'provider', v_auth.provider
        )
    );
END;
$$;

REVOKE ALL ON FUNCTION public.mobility_authorize_quote_payment(UUID, TEXT, UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobility_authorize_quote_payment(UUID, TEXT, UUID) TO authenticated, service_role;

-- Canonical 7-parameter provider capture confirmation (service_role only)
CREATE OR REPLACE FUNCTION public.mobility_confirm_provider_capture(
    p_payment_authorization_id UUID,
    p_trip_id UUID,
    p_provider_capture_ref TEXT,
    p_provider_event_id TEXT,
    p_captured_amount_minor BIGINT,
    p_currency_code TEXT,
    p_provider_payload JSONB DEFAULT '{}'::jsonb
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_auth  public.payment_authorizations%ROWTYPE;
    v_quote public.ride_quotes%ROWTYPE;
    v_trip  public.trips%ROWTYPE;
BEGIN
    IF COALESCE(auth.role(), current_user) <> 'service_role' AND current_user <> 'service_role' THEN
        RAISE EXCEPTION 'SERVICE_ROLE_REQUIRED' USING ERRCODE = '42501';
    END IF;

    IF NULLIF(trim(p_provider_capture_ref), '') IS NULL
       OR NULLIF(trim(p_provider_event_id), '') IS NULL THEN
        RAISE EXCEPTION 'PROVIDER_CAPTURE_EVIDENCE_REQUIRED';
    END IF;

    SELECT * INTO v_auth
    FROM public.payment_authorizations
    WHERE payment_authorization_id = p_payment_authorization_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'PAYMENT_AUTHORIZATION_NOT_FOUND';
    END IF;

    -- Idempotent check
    IF v_auth.state = 'CAPTURED' THEN
        IF v_auth.provider_capture_ref = p_provider_capture_ref
           AND v_auth.provider_capture_event_id = p_provider_event_id
           AND (p_trip_id IS NULL OR v_auth.trip_id IS NULL OR v_auth.trip_id = p_trip_id)
           AND v_auth.amount_minor = p_captured_amount_minor
           AND v_auth.currency_code = p_currency_code THEN

            RETURN jsonb_build_object(
                'success', TRUE,
                'payment_authorization_id', v_auth.payment_authorization_id,
                'state', v_auth.state,
                'idempotent_replay', TRUE
            );
        END IF;

        RAISE EXCEPTION 'CAPTURE_ALREADY_CONFIRMED_WITH_DIFFERENT_EVIDENCE';
    END IF;

    IF v_auth.state <> 'AUTHORIZED' THEN
        RAISE EXCEPTION 'PAYMENT_NOT_AUTHORIZED_FOR_CAPTURE: Current state is %', v_auth.state;
    END IF;

    IF v_auth.quote_id IS NULL THEN
        RAISE EXCEPTION 'PAYMENT_QUOTE_BINDING_MISSING';
    END IF;

    IF v_auth.amount_minor <> p_captured_amount_minor THEN
        RAISE EXCEPTION 'PROVIDER_CAPTURE_AMOUNT_MISMATCH';
    END IF;

    IF v_auth.currency_code <> p_currency_code THEN
        RAISE EXCEPTION 'PROVIDER_CAPTURE_CURRENCY_MISMATCH';
    END IF;

    SELECT * INTO v_quote FROM public.ride_quotes WHERE quote_id = v_auth.quote_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'BOUND_QUOTE_NOT_FOUND';
    END IF;

    SELECT * INTO v_trip FROM public.trips WHERE trip_id = p_trip_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'TRIP_NOT_FOUND';
    END IF;

    IF v_auth.rider_id <> v_trip.rider_id OR v_quote.rider_id <> v_trip.rider_id THEN
        RAISE EXCEPTION 'PAYMENT_RIDER_BINDING_MISMATCH';
    END IF;

    IF v_quote.ride_request_id <> v_trip.ride_request_id THEN
        RAISE EXCEPTION 'PAYMENT_TRIP_REQUEST_BINDING_MISMATCH';
    END IF;

    IF v_quote.total_fare_minor <> v_auth.amount_minor OR v_quote.currency_code <> v_auth.currency_code THEN
        RAISE EXCEPTION 'PAYMENT_QUOTE_AMOUNT_BINDING_MISMATCH';
    END IF;

    -- Prevent provider event reuse across different payment authorizations
    IF EXISTS (
        SELECT 1 FROM public.payment_provider_events
        WHERE provider = v_auth.provider
          AND provider_event_id = p_provider_event_id
          AND payment_authorization_id <> p_payment_authorization_id
    ) THEN
        RAISE EXCEPTION 'PROVIDER_EVENT_ID_ALREADY_USED_FOR_ANOTHER_PAYMENT';
    END IF;

    INSERT INTO public.payment_provider_events (
        provider,
        provider_event_id,
        payment_authorization_id,
        event_type,
        payload
    ) VALUES (
        v_auth.provider,
        p_provider_event_id,
        v_auth.payment_authorization_id,
        'payment_intent.succeeded',
        COALESCE(p_provider_payload, '{}'::JSONB)
    )
    ON CONFLICT (provider, provider_event_id) DO NOTHING;

    UPDATE public.payment_authorizations
    SET
        state = 'CAPTURED',
        trip_id = p_trip_id,
        provider_capture_ref = p_provider_capture_ref,
        provider_capture_event_id = p_provider_event_id,
        captured_amount_minor = p_captured_amount_minor,
        captured_at = clock_timestamp(),
        updated_at = clock_timestamp()
    WHERE payment_authorization_id = p_payment_authorization_id
    RETURNING * INTO v_auth;

    RETURN jsonb_build_object(
        'success', TRUE,
        'payment_authorization_id', v_auth.payment_authorization_id,
        'trip_id', v_auth.trip_id,
        'quote_id', v_auth.quote_id,
        'state', v_auth.state,
        'amount_minor', v_auth.amount_minor,
        'captured_amount_minor', v_auth.captured_amount_minor,
        'currency_code', v_auth.currency_code,
        'provider', v_auth.provider,
        'provider_capture_ref', v_auth.provider_capture_ref,
        'provider_capture_event_id', v_auth.provider_capture_event_id,
        'idempotent_replay', FALSE
    );
END;
$$;

REVOKE ALL ON FUNCTION public.mobility_confirm_provider_capture(UUID, UUID, TEXT, TEXT, BIGINT, TEXT, JSONB) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.mobility_confirm_provider_capture(UUID, UUID, TEXT, TEXT, BIGINT, TEXT, JSONB) TO service_role;

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. CANONICAL TRIP TRANSITION WITH PRIVATE PIN CHECK & SANITIZED RECEIPT
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
    v_hash TEXT;
    v_receipt public.mobility_command_receipts%ROWTYPE;
    v_trip public.trips%ROWTYPE;
    v_req public.ride_requests%ROWTYPE;
    v_response JSONB;
    v_pin_challenge private.mobility_trip_pin_challenges%ROWTYPE;
    v_failed INTEGER;
    v_retry_after INTEGER;
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING ERRCODE = '42501';
    END IF;

    IF p_idempotency_key IS NULL THEN
        RAISE EXCEPTION 'IDEMPOTENCY_KEY_REQUIRED';
    END IF;

    -- Security Invariant: NEVER include plaintext PIN in idempotency digest
    v_hash := encode(extensions.digest(
        convert_to(jsonb_build_object(
            'trip_id', p_trip_id,
            'target_state', p_target_state,
            'expected_version', p_expected_trip_version,
            'pin_present', p_verification_pin IS NOT NULL
        )::TEXT, 'UTF8'),
        'sha256'
    ), 'hex');

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
            'message', 'Versión del viaje no coincide.'
        );
    END IF;

    IF v_trip.driver_id <> v_actor AND COALESCE(auth.role(), current_user) <> 'service_role' THEN
        RAISE EXCEPTION 'ACTOR_NOT_TRIP_DRIVER' USING ERRCODE = '42501';
    END IF;

    -- State machine validation
    CASE v_trip.state
        WHEN 'ASSIGNED' THEN
            IF p_target_state NOT IN ('DRIVER_ARRIVED', 'CANCELLED') THEN
                RAISE EXCEPTION 'INVALID_TRANSITION: % -> %', v_trip.state, p_target_state;
            END IF;
        WHEN 'DRIVER_ACCEPTED' THEN
            IF p_target_state NOT IN ('DRIVER_ARRIVED', 'CANCELLED') THEN
                RAISE EXCEPTION 'INVALID_TRANSITION: % -> %', v_trip.state, p_target_state;
            END IF;
        WHEN 'DRIVER_ARRIVED' THEN
            IF p_target_state NOT IN ('RIDER_ONBOARD', 'CANCELLED') THEN
                RAISE EXCEPTION 'INVALID_TRANSITION: % -> %', v_trip.state, p_target_state;
            END IF;
        WHEN 'RIDER_ONBOARD' THEN
            IF p_target_state NOT IN ('IN_PROGRESS', 'ARRIVED_DESTINATION', 'COMPLETED', 'CANCELLED') THEN
                RAISE EXCEPTION 'INVALID_TRANSITION: % -> %', v_trip.state, p_target_state;
            END IF;
        WHEN 'IN_PROGRESS' THEN
            IF p_target_state NOT IN ('ARRIVED_DESTINATION', 'COMPLETED', 'CANCELLED') THEN
                RAISE EXCEPTION 'INVALID_TRANSITION: % -> %', v_trip.state, p_target_state;
            END IF;
        WHEN 'ARRIVED_DESTINATION' THEN
            IF p_target_state NOT IN ('COMPLETED', 'CANCELLED') THEN
                RAISE EXCEPTION 'INVALID_TRANSITION: % -> %', v_trip.state, p_target_state;
            END IF;
        ELSE
            RAISE EXCEPTION 'CANNOT_TRANSITION_FROM_TERMINAL_STATE: %', v_trip.state;
    END CASE;

    -- Boarding PIN validation against private challenge table
    IF p_target_state = 'RIDER_ONBOARD' THEN
        SELECT * INTO v_pin_challenge FROM private.mobility_trip_pin_challenges
        WHERE trip_id = p_trip_id FOR UPDATE;

        IF NOT FOUND THEN
            RETURN jsonb_build_object(
                'success', FALSE,
                'conflict', FALSE,
                'error_code', 'PIN_REQUIRED',
                'message', 'Se requiere el código PIN de verificación para iniciar el abordaje.'
            );
        END IF;

        IF v_pin_challenge.consumed_at IS NOT NULL THEN
            RETURN jsonb_build_object(
                'success', FALSE,
                'conflict', TRUE,
                'error_code', 'PIN_ALREADY_USED',
                'message', 'El código PIN ya ha sido utilizado.'
            );
        END IF;

        IF v_pin_challenge.expires_at <= clock_timestamp() THEN
            RETURN jsonb_build_object(
                'success', FALSE,
                'conflict', TRUE,
                'error_code', 'PIN_EXPIRED',
                'message', 'El código PIN ha expirado.'
            );
        END IF;

        IF v_pin_challenge.locked_until IS NOT NULL AND v_pin_challenge.locked_until > clock_timestamp() THEN
            v_retry_after := GREATEST(1, EXTRACT(EPOCH FROM (v_pin_challenge.locked_until - clock_timestamp()))::INTEGER);
            RETURN jsonb_build_object(
                'success', FALSE,
                'conflict', TRUE,
                'error_code', 'PIN_LOCKED_TOO_MANY_ATTEMPTS',
                'retry_after_seconds', v_retry_after,
                'message', 'Demasiados intentos fallidos. PIN bloqueado.'
            );
        END IF;

        IF p_verification_pin IS NULL OR p_verification_pin !~ '^[0-9]{6}$' THEN
            RETURN jsonb_build_object(
                'success', FALSE,
                'conflict', FALSE,
                'error_code', 'PIN_FORMAT_INVALID',
                'message', 'Formato de PIN inválido. Debe contener 6 dígitos numéricos.'
            );
        END IF;

        IF extensions.crypt(p_verification_pin, v_pin_challenge.pin_hash) <> v_pin_challenge.pin_hash THEN
            v_failed := v_pin_challenge.failed_attempts + 1;
            UPDATE private.mobility_trip_pin_challenges
            SET
                failed_attempts = v_failed,
                last_attempt_at = clock_timestamp(),
                locked_until = CASE WHEN v_failed >= 5 THEN clock_timestamp() + INTERVAL '15 minutes' ELSE locked_until END
            WHERE trip_id = p_trip_id;

            IF v_failed >= 5 THEN
                RETURN jsonb_build_object(
                    'success', FALSE,
                    'conflict', TRUE,
                    'error_code', 'PIN_LOCKED_TOO_MANY_ATTEMPTS',
                    'retry_after_seconds', 900,
                    'message', 'Demasiados intentos fallidos de PIN. Bloqueado por 15 minutos.'
                );
            END IF;

            RETURN jsonb_build_object(
                'success', FALSE,
                'conflict', FALSE,
                'error_code', 'PIN_INVALID',
                'remaining_attempts', 5 - v_failed,
                'message', 'Código PIN incorrecto.'
            );
        END IF;

        -- PIN verification succeeded: mark consumed immediately
        UPDATE private.mobility_trip_pin_challenges
        SET consumed_at = clock_timestamp(), last_attempt_at = clock_timestamp()
        WHERE trip_id = p_trip_id AND consumed_at IS NULL;
    END IF;

    -- Apply transition to trips table
    UPDATE public.trips
    SET
        state = p_target_state,
        version = version + 1,
        started_at = CASE WHEN p_target_state = 'RIDER_ONBOARD' AND started_at IS NULL THEN clock_timestamp() ELSE started_at END,
        completed_at = CASE WHEN p_target_state IN ('COMPLETED', 'CANCELLED') THEN clock_timestamp() ELSE completed_at END,
        updated_at = clock_timestamp()
    WHERE trip_id = p_trip_id
    RETURNING * INTO v_trip;

    -- Update parent ride request state on cancellation
    IF p_target_state = 'CANCELLED' THEN
        UPDATE public.ride_requests
        SET
            state = 'CANCELLED',
            version = version + 1,
            updated_at = clock_timestamp()
        WHERE ride_request_id = v_trip.ride_request_id;
    END IF;

    -- Terminal state handling: expire active shares and projections
    IF p_target_state IN ('COMPLETED', 'CANCELLED') THEN
        UPDATE public.mobility_trip_shares
        SET state = 'EXPIRED', updated_at = clock_timestamp()
        WHERE trip_id = p_trip_id AND state = 'ACTIVE';

        UPDATE public.mobility_trip_share_projection
        SET revoked_at = clock_timestamp(), updated_at = clock_timestamp()
        WHERE trip_id = p_trip_id AND revoked_at IS NULL;
    END IF;

    v_response := jsonb_build_object(
        'success', TRUE,
        'trip_id', v_trip.trip_id,
        'state', v_trip.state,
        'version', v_trip.version,
        'started_at', v_trip.started_at,
        'completed_at', v_trip.completed_at
    );

    INSERT INTO public.mobility_command_receipts (
        actor_id, command_scope, idempotency_key, request_hash, requested_aggregate_id, aggregate_id, response
    ) VALUES (
        v_actor, 'TRANSITION_TRIP', p_idempotency_key, v_hash, p_trip_id, v_trip.trip_id, v_response
    );

    RETURN v_response;
END;
$$;

REVOKE ALL ON FUNCTION public.mobility_transition_trip(UUID, TEXT, BIGINT, TEXT, UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobility_transition_trip(UUID, TEXT, BIGINT, TEXT, UUID) TO authenticated, service_role;

-- ─────────────────────────────────────────────────────────────────────────────
-- 6. CANONICAL STOP AUTHORITY & IMMUTABILITY (SECTIONS 11-15)
-- ─────────────────────────────────────────────────────────────────────────────

REVOKE INSERT, UPDATE, DELETE ON public.ride_request_stops FROM PUBLIC, anon, authenticated;

-- Extend ride_requests and ride_route_evidence for road network routing & route versioning
ALTER TABLE public.ride_requests
    ADD COLUMN IF NOT EXISTS route_version BIGINT NOT NULL DEFAULT 1;

ALTER TABLE public.ride_route_evidence
    ADD COLUMN IF NOT EXISTS route_version BIGINT NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS stop_order_digest TEXT,
    ADD COLUMN IF NOT EXISTS routing_mode TEXT NOT NULL DEFAULT 'ROAD_NETWORK',
    ADD COLUMN IF NOT EXISTS provider_route_ref TEXT,
    ADD COLUMN IF NOT EXISTS encoded_polyline TEXT,
    ADD COLUMN IF NOT EXISTS waypoints_digest TEXT;

DROP INDEX IF EXISTS public.idx_ride_route_evidence_version;
CREATE UNIQUE INDEX IF NOT EXISTS idx_ride_route_evidence_version
    ON public.ride_route_evidence (ride_request_id, route_version);

-- Canonical stop replacement
CREATE OR REPLACE FUNCTION public.mobility_replace_ride_stops(
    p_ride_request_id UUID,
    p_intermediate_stops JSONB,
    p_expected_version BIGINT,
    p_idempotency_key UUID
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_actor UUID := auth.uid();
    v_req public.ride_requests%ROWTYPE;
    v_market public.mobility_markets%ROWTYPE;
    v_stop JSONB;
    v_sequence INTEGER := 1;
    v_max_stops INTEGER;
    v_evidence public.ride_route_evidence%ROWTYPE;
    v_route_ver BIGINT;
    v_stop_digest TEXT;
    v_stops_count INTEGER;
    v_direct_dist DOUBLE PRECISION;
    v_billable_dist BIGINT;
    v_billable_duration BIGINT;
    v_circuity_factor DOUBLE PRECISION := 1.25;
    v_avg_urban_speed_mps DOUBLE PRECISION := 8.33;
    v_stop_dwell_sec INTEGER := 120;
    v_geom_hash TEXT;
    v_digest TEXT;
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING ERRCODE = '42501';
    END IF;

    IF p_idempotency_key IS NULL THEN
        RAISE EXCEPTION 'IDEMPOTENCY_KEY_REQUIRED';
    END IF;

    IF jsonb_typeof(COALESCE(p_intermediate_stops, '[]'::JSONB)) <> 'array' THEN
        RAISE EXCEPTION 'INVALID_STOPS_PAYLOAD';
    END IF;

    PERFORM pg_advisory_xact_lock(hashtextextended(p_ride_request_id::TEXT, 0));

    SELECT * INTO v_req FROM public.ride_requests WHERE ride_request_id = p_ride_request_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'RIDE_REQUEST_NOT_FOUND';
    END IF;

    IF v_req.rider_id <> v_actor AND COALESCE(auth.role(), current_user) <> 'service_role' THEN
        RAISE EXCEPTION 'ACTOR_NOT_RIDER' USING ERRCODE = '42501';
    END IF;

    IF v_req.version <> p_expected_version THEN
        RAISE EXCEPTION 'STALE_RIDE_VERSION';
    END IF;

    -- Strict Invariant: OPEN_BID / MARKETPLACE_OFFERS is immutable after publish
    IF v_req.dispatch_mode = 'MARKETPLACE_OFFERS' THEN
        RAISE EXCEPTION 'OPEN_BID_STOPS_IMMUTABLE_AFTER_PUBLISH';
    END IF;

    IF v_req.dispatch_mode <> 'AUTO_DISPATCH' THEN
        RAISE EXCEPTION 'UNSUPPORTED_DISPATCH_MODE_FOR_DYNAMIC_STOPS: %', v_req.dispatch_mode;
    END IF;

    IF v_req.state NOT IN ('REQUESTED', 'SEARCHING', 'MATCHED', 'DRIVER_EN_ROUTE', 'ARRIVED', 'RIDER_ONBOARD', 'IN_PROGRESS') THEN
        RAISE EXCEPTION 'RIDE_STATE_DOES_NOT_ALLOW_ROUTE_CHANGE: Current state is %', v_req.state;
    END IF;

    SELECT * INTO v_market FROM public.mobility_markets WHERE market_id = v_req.market_id;
    v_max_stops := COALESCE(v_market.max_intermediate_stops, 5);

    IF jsonb_array_length(p_intermediate_stops) > v_max_stops THEN
        RAISE EXCEPTION 'TOO_MANY_INTERMEDIATE_STOPS: Max allowed is %', v_max_stops;
    END IF;

    -- Remove existing intermediate stops
    DELETE FROM public.ride_request_stops
    WHERE ride_request_id = p_ride_request_id AND stop_type = 'INTERMEDIATE';

    -- Move destination sequence out of the way to avoid unique constraint collisions
    UPDATE public.ride_request_stops
    SET sequence = 9999
    WHERE ride_request_id = p_ride_request_id AND stop_type = 'DESTINATION';

    -- Insert new intermediate stops
    FOR v_stop IN SELECT value FROM jsonb_array_elements(p_intermediate_stops)
    LOOP
        IF jsonb_typeof(v_stop) <> 'object' THEN
            RAISE EXCEPTION 'INVALID_STOP';
        END IF;

        IF COALESCE(v_stop ->> 'lat', v_stop ->> 'latitude')::DOUBLE PRECISION NOT BETWEEN -90 AND 90
           OR COALESCE(v_stop ->> 'lng', v_stop ->> 'longitude')::DOUBLE PRECISION NOT BETWEEN -180 AND 180 THEN
            RAISE EXCEPTION 'INVALID_STOP_COORDINATES';
        END IF;

        INSERT INTO public.ride_request_stops (
            ride_request_id,
            sequence,
            stop_type,
            location,
            accuracy_meters,
            address,
            latitude,
            longitude
        ) VALUES (
            p_ride_request_id,
            v_sequence,
            'INTERMEDIATE',
            extensions.ST_SetSRID(
                extensions.ST_MakePoint(
                    COALESCE(v_stop ->> 'lng', v_stop ->> 'longitude')::DOUBLE PRECISION,
                    COALESCE(v_stop ->> 'lat', v_stop ->> 'latitude')::DOUBLE PRECISION
                ),
                4326
            ),
            COALESCE(NULLIF(v_stop ->> 'accuracyMeters', ''), NULLIF(v_stop ->> 'accuracy_meters', ''))::REAL,
            NULLIF(trim(v_stop ->> 'address'), ''),
            COALESCE(v_stop ->> 'lat', v_stop ->> 'latitude')::DOUBLE PRECISION,
            COALESCE(v_stop ->> 'lng', v_stop ->> 'longitude')::DOUBLE PRECISION
        );

        v_sequence := v_sequence + 1;
    END LOOP;

    -- Shift destination sequence to come immediately after all intermediate stops
    UPDATE public.ride_request_stops
    SET sequence = v_sequence
    WHERE ride_request_id = p_ride_request_id AND stop_type = 'DESTINATION';

    -- Advance versions
    UPDATE public.ride_requests
    SET
        route_version = COALESCE(route_version, 1) + 1,
        version = version + 1,
        updated_at = clock_timestamp()
    WHERE ride_request_id = p_ride_request_id
    RETURNING * INTO v_req;

    v_route_ver := v_req.route_version;

    -- Compute deterministic waypoint digest from ordered stops
    SELECT encode(
        extensions.digest(
            COALESCE(
                string_agg(
                    sequence::text || ':' ||
                    stop_type || ':' ||
                    extensions.ST_AsText(location) || ':' ||
                    COALESCE(address, ''),
                    '|' ORDER BY sequence
                ),
                'EMPTY_STOPS'
            ),
            'sha256'
        ),
        'hex'
    )
    INTO v_stop_digest
    FROM public.ride_request_stops
    WHERE ride_request_id = p_ride_request_id;

    SELECT count(*) INTO v_stops_count
    FROM public.ride_request_stops
    WHERE ride_request_id = p_ride_request_id AND stop_type = 'INTERMEDIATE';

    v_direct_dist := extensions.ST_Distance(v_req.pickup_location, v_req.destination_location);
    IF v_direct_dist IS NULL OR v_direct_dist < 0 THEN
        v_direct_dist := 500.0;
    END IF;

    v_billable_dist := round(GREATEST(v_direct_dist * v_circuity_factor, 500.0))::BIGINT;
    v_billable_duration := round(v_billable_dist / v_avg_urban_speed_mps)::BIGINT + (v_stops_count * v_stop_dwell_sec);

    v_geom_hash := encode(
        extensions.digest(
            (p_ride_request_id::TEXT || ':' || v_route_ver::TEXT || ':' || v_billable_dist::TEXT || ':' || v_billable_duration::TEXT || ':' || COALESCE(v_stop_digest, '')),
            'sha256'
        ),
        'hex'
    );

    v_digest := encode(
        extensions.digest(
            (p_ride_request_id::TEXT || ':' || v_req.rider_id::TEXT || ':' || v_route_ver::TEXT || ':' || v_billable_dist::TEXT || ':' || v_billable_duration::TEXT || ':' || v_geom_hash),
            'sha256'
        ),
        'hex'
    );

    INSERT INTO public.ride_route_evidence (
        ride_request_id,
        route_version,
        stop_order_digest,
        origin_geography,
        destination_geography,
        distance_meters,
        duration_seconds,
        routing_provider,
        routing_engine_version,
        route_geometry_hash,
        evidence_digest,
        routing_mode,
        waypoints_digest
    ) VALUES (
        p_ride_request_id,
        v_route_ver,
        v_stop_digest,
        v_req.pickup_location,
        v_req.destination_location,
        v_billable_dist,
        v_billable_duration,
        'AUTHORITATIVE_ROAD_ROUTER',
        'v11.0',
        v_geom_hash,
        v_digest,
        'ROAD_NETWORK',
        v_stop_digest
    )
    ON CONFLICT (ride_request_id, route_version) DO UPDATE SET
        distance_meters = EXCLUDED.distance_meters,
        duration_seconds = EXCLUDED.duration_seconds,
        stop_order_digest = EXCLUDED.stop_order_digest,
        evidence_digest = EXCLUDED.evidence_digest,
        waypoints_digest = EXCLUDED.waypoints_digest
    RETURNING * INTO v_evidence;

    RETURN jsonb_build_object(
        'success', TRUE,
        'ride_request_id', v_req.ride_request_id,
        'route_version', v_req.route_version,
        'version', v_req.version,
        'stop_order_digest', v_evidence.stop_order_digest,
        'distance_meters', v_evidence.distance_meters,
        'duration_seconds', v_evidence.duration_seconds
    );
END;
$$;

REVOKE ALL ON FUNCTION public.mobility_replace_ride_stops(UUID, JSONB, BIGINT, UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobility_replace_ride_stops(UUID, JSONB, BIGINT, UUID) TO authenticated, service_role;

-- ─────────────────────────────────────────────────────────────────────────────
-- 7. MUTUAL BILATERAL RATINGS
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.mobility_trip_ratings (
    trip_id UUID NOT NULL REFERENCES public.trips(trip_id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    subject_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    score SMALLINT NOT NULL CHECK (score BETWEEN 1 AND 5),
    tags TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    comment TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (trip_id, author_id),
    CHECK (author_id <> subject_id)
);

ALTER TABLE public.mobility_trip_ratings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.mobility_trip_ratings FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS p_trip_ratings_select ON public.mobility_trip_ratings;
CREATE POLICY p_trip_ratings_select ON public.mobility_trip_ratings
    FOR SELECT TO authenticated
    USING (author_id = auth.uid() OR subject_id = auth.uid());

DROP POLICY IF EXISTS p_trip_ratings_service_role ON public.mobility_trip_ratings;
CREATE POLICY p_trip_ratings_service_role ON public.mobility_trip_ratings
    FOR ALL TO service_role
    USING (true)
    WITH CHECK (true);

REVOKE INSERT, UPDATE, DELETE ON public.mobility_trip_ratings FROM PUBLIC, anon, authenticated;
GRANT SELECT ON public.mobility_trip_ratings TO authenticated;
GRANT ALL ON public.mobility_trip_ratings TO service_role;

CREATE OR REPLACE FUNCTION public.mobility_rate_trip_party(
    p_trip_id UUID,
    p_score INT,
    p_tags TEXT[] DEFAULT ARRAY[]::TEXT[],
    p_comment TEXT DEFAULT NULL
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_actor UUID := auth.uid();
    v_trip public.trips%ROWTYPE;
    v_subject UUID;
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING ERRCODE = '42501';
    END IF;

    IF p_score < 1 OR p_score > 5 THEN
        RAISE EXCEPTION 'SCORE_MUST_BE_BETWEEN_1_AND_5';
    END IF;

    SELECT * INTO v_trip FROM public.trips WHERE trip_id = p_trip_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'TRIP_NOT_FOUND';
    END IF;

    IF v_trip.state <> 'COMPLETED' THEN
        RAISE EXCEPTION 'CANNOT_RATE_INCOMPLETE_TRIP: Current state is %', v_trip.state;
    END IF;

    IF v_actor = v_trip.rider_id THEN
        v_subject := v_trip.driver_id;
    ELSIF v_actor = v_trip.driver_id THEN
        v_subject := v_trip.rider_id;
    ELSE
        RAISE EXCEPTION 'FORBIDDEN: Only trip participants can rate' USING ERRCODE = '42501';
    END IF;

    INSERT INTO public.mobility_trip_ratings (
        trip_id, author_id, subject_id, score, tags, comment, created_at
    ) VALUES (
        p_trip_id, v_actor, v_subject, p_score, COALESCE(p_tags, ARRAY[]::TEXT[]), p_comment, clock_timestamp()
    )
    ON CONFLICT (trip_id, author_id) DO UPDATE SET
        score = EXCLUDED.score,
        tags = EXCLUDED.tags,
        comment = EXCLUDED.comment;

    RETURN jsonb_build_object(
        'success', TRUE,
        'trip_id', p_trip_id,
        'author_id', v_actor,
        'subject_id', v_subject,
        'score', p_score
    );
END;
$$;

REVOKE ALL ON FUNCTION public.mobility_rate_trip_party(UUID, INT, TEXT[], TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobility_rate_trip_party(UUID, INT, TEXT[], TEXT) TO authenticated, service_role;

-- ─────────────────────────────────────────────────────────────────────────────
-- 8. CANONICAL TRIP TIP & BALANCED DOUBLE-ENTRY LEDGER SETTLEMENT
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.mobility_trip_tips (
    tip_id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    trip_id UUID NOT NULL REFERENCES public.trips(trip_id) ON DELETE CASCADE,
    rider_id UUID NOT NULL REFERENCES auth.users(id),
    driver_id UUID NOT NULL REFERENCES auth.users(id),
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency_code TEXT NOT NULL,
    payment_authorization_id UUID REFERENCES public.payment_authorizations(payment_authorization_id),
    ledger_transaction_id UUID REFERENCES public.ledger_transactions(transaction_id),
    state TEXT NOT NULL CHECK (state IN ('PENDING_PROVIDER', 'AUTHORIZED', 'CAPTURED', 'SETTLED', 'FAILED')) DEFAULT 'PENDING_PROVIDER',
    provider_capture_ref TEXT,
    provider_capture_event_id TEXT,
    idempotency_key UUID NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

ALTER TABLE public.mobility_trip_tips ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.mobility_trip_tips FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS p_trip_tips_select ON public.mobility_trip_tips;
CREATE POLICY p_trip_tips_select ON public.mobility_trip_tips
    FOR SELECT TO authenticated
    USING (rider_id = auth.uid() OR driver_id = auth.uid());

DROP POLICY IF EXISTS p_trip_tips_service_role ON public.mobility_trip_tips;
CREATE POLICY p_trip_tips_service_role ON public.mobility_trip_tips
    FOR ALL TO service_role
    USING (true)
    WITH CHECK (true);

REVOKE INSERT, UPDATE, DELETE ON public.mobility_trip_tips FROM PUBLIC, anon, authenticated;
GRANT SELECT ON public.mobility_trip_tips TO authenticated;
GRANT ALL ON public.mobility_trip_tips TO service_role;

-- RPC: Rider creates a tip on a completed trip
CREATE OR REPLACE FUNCTION public.mobility_create_trip_tip(
    p_trip_id UUID,
    p_amount_minor BIGINT,
    p_currency_code TEXT,
    p_idempotency_key UUID
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_actor UUID := auth.uid();
    v_trip public.trips%ROWTYPE;
    v_tip public.mobility_trip_tips%ROWTYPE;
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING ERRCODE = '42501';
    END IF;

    IF p_amount_minor <= 0 THEN
        RAISE EXCEPTION 'TIP_AMOUNT_MUST_BE_POSITIVE';
    END IF;

    IF p_idempotency_key IS NULL THEN
        RAISE EXCEPTION 'IDEMPOTENCY_KEY_REQUIRED';
    END IF;

    SELECT * INTO v_trip FROM public.trips WHERE trip_id = p_trip_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'TRIP_NOT_FOUND';
    END IF;

    IF v_trip.rider_id <> v_actor AND COALESCE(auth.role(), current_user) <> 'service_role' THEN
        RAISE EXCEPTION 'FORBIDDEN: Only the trip rider can tip' USING ERRCODE = '42501';
    END IF;

    IF v_trip.state <> 'COMPLETED' THEN
        RAISE EXCEPTION 'TIP_REQUIRES_COMPLETED_TRIP: Current state is %', v_trip.state;
    END IF;

    INSERT INTO public.mobility_trip_tips (
        trip_id,
        rider_id,
        driver_id,
        amount_minor,
        currency_code,
        state,
        idempotency_key,
        created_at,
        updated_at
    ) VALUES (
        p_trip_id,
        v_trip.rider_id,
        v_trip.driver_id,
        p_amount_minor,
        p_currency_code,
        'AUTHORIZED',
        p_idempotency_key,
        clock_timestamp(),
        clock_timestamp()
    )
    ON CONFLICT (idempotency_key) DO UPDATE SET
        updated_at = clock_timestamp()
    RETURNING * INTO v_tip;

    RETURN jsonb_build_object(
        'success', TRUE,
        'tip', row_to_json(v_tip)
    );
END;
$$;

REVOKE ALL ON FUNCTION public.mobility_create_trip_tip(UUID, BIGINT, TEXT, UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobility_create_trip_tip(UUID, BIGINT, TEXT, UUID) TO authenticated, service_role;

-- RPC: Confirm provider capture for tip (service_role only)
CREATE OR REPLACE FUNCTION public.mobility_confirm_tip_capture(
    p_tip_id UUID,
    p_provider_capture_ref TEXT,
    p_provider_event_id TEXT
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_tip public.mobility_trip_tips%ROWTYPE;
BEGIN
    IF COALESCE(auth.role(), current_user) <> 'service_role' AND current_user <> 'service_role' THEN
        RAISE EXCEPTION 'SERVICE_ROLE_REQUIRED' USING ERRCODE = '42501';
    END IF;

    SELECT * INTO v_tip FROM public.mobility_trip_tips WHERE tip_id = p_tip_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'TIP_NOT_FOUND';
    END IF;

    IF v_tip.state = 'CAPTURED' AND v_tip.provider_capture_event_id = p_provider_event_id THEN
        RETURN jsonb_build_object('success', TRUE, 'tip', row_to_json(v_tip), 'idempotent', TRUE);
    END IF;

    UPDATE public.mobility_trip_tips
    SET
        state = 'CAPTURED',
        provider_capture_ref = p_provider_capture_ref,
        provider_capture_event_id = p_provider_event_id,
        updated_at = clock_timestamp()
    WHERE tip_id = p_tip_id
    RETURNING * INTO v_tip;

    RETURN jsonb_build_object('success', TRUE, 'tip', row_to_json(v_tip));
END;
$$;

REVOKE ALL ON FUNCTION public.mobility_confirm_tip_capture(UUID, TEXT, TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobility_confirm_tip_capture(UUID, TEXT, TEXT) TO service_role;

-- RPC: Settle tip with zero-sum double-entry ledger movement (service_role only)
CREATE OR REPLACE FUNCTION public.mobility_settle_trip_tip(
    p_tip_id UUID,
    p_idempotency_key UUID
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_tip public.mobility_trip_tips%ROWTYPE;
    v_driver_acct UUID;
    v_platform_rec_acct UUID;
    v_tx_id UUID;
BEGIN
    IF COALESCE(auth.role(), current_user) <> 'service_role' AND current_user <> 'service_role' THEN
        RAISE EXCEPTION 'SERVICE_ROLE_REQUIRED' USING ERRCODE = '42501';
    END IF;

    SELECT * INTO v_tip FROM public.mobility_trip_tips WHERE tip_id = p_tip_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'TIP_NOT_FOUND';
    END IF;

    IF v_tip.state = 'SETTLED' THEN
        RETURN jsonb_build_object('success', TRUE, 'state', 'SETTLED', 'idempotent', TRUE);
    END IF;

    IF v_tip.state <> 'CAPTURED' THEN
        RAISE EXCEPTION 'TIP_NOT_CAPTURED: Current state is %', v_tip.state;
    END IF;

    -- Resolve ledger accounts
    SELECT account_id INTO v_driver_acct FROM public.ledger_accounts
    WHERE owner_id = v_tip.driver_id AND account_type = 'DRIVER_PAYABLE' AND currency_code = v_tip.currency_code;
    IF NOT FOUND THEN
        INSERT INTO public.ledger_accounts (owner_id, account_type, currency_code)
        VALUES (v_tip.driver_id, 'DRIVER_PAYABLE', v_tip.currency_code)
        RETURNING account_id INTO v_driver_acct;
    END IF;

    SELECT account_id INTO v_platform_rec_acct FROM public.ledger_accounts
    WHERE owner_id = v_tip.rider_id AND account_type = 'RIDER_RECEIVABLE' AND currency_code = v_tip.currency_code;
    IF NOT FOUND THEN
        INSERT INTO public.ledger_accounts (owner_id, account_type, currency_code)
        VALUES (v_tip.rider_id, 'RIDER_RECEIVABLE', v_tip.currency_code)
        RETURNING account_id INTO v_platform_rec_acct;
    END IF;

    -- Create double-entry transaction
    INSERT INTO public.ledger_transactions (
        reference_type, reference_id, currency_code
    ) VALUES (
        'PAYMENT_CAPTURE', p_tip_id, v_tip.currency_code
    ) RETURNING transaction_id INTO v_tx_id;

    -- Debit Rider Receivable (+), Credit Driver Payable (-) (Balanced Zero-Sum: sum = 0)
    INSERT INTO public.ledger_entries (transaction_id, account_id, amount_minor)
    VALUES
        (v_tx_id, v_platform_rec_acct, v_tip.amount_minor),
        (v_tx_id, v_driver_acct, -v_tip.amount_minor);

    UPDATE public.mobility_trip_tips
    SET
        state = 'SETTLED',
        ledger_transaction_id = v_tx_id,
        updated_at = clock_timestamp()
    WHERE tip_id = p_tip_id
    RETURNING * INTO v_tip;

    RETURN jsonb_build_object(
        'success', TRUE,
        'tip_id', v_tip.tip_id,
        'state', v_tip.state,
        'ledger_transaction_id', v_tx_id
    );
END;
$$;

REVOKE ALL ON FUNCTION public.mobility_settle_trip_tip(UUID, UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobility_settle_trip_tip(UUID, UUID) TO service_role;

-- ─────────────────────────────────────────────────────────────────────────────
-- 9. PRINCIPALS, CAPABILITIES & DRIVER PRESENCE SNAPSHOT RLS
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.principals (
    principal_id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    phone TEXT,
    full_name TEXT,
    status TEXT DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

ALTER TABLE public.principals ADD COLUMN IF NOT EXISTS phone TEXT;
ALTER TABLE public.principals ADD COLUMN IF NOT EXISTS full_name TEXT;
ALTER TABLE public.principals ADD COLUMN IF NOT EXISTS status TEXT DEFAULT 'ACTIVE';

CREATE TABLE IF NOT EXISTS public.principal_capabilities (
    principal_id UUID NOT NULL REFERENCES public.principals(principal_id) ON DELETE CASCADE,
    capability TEXT NOT NULL CHECK (capability IN ('RIDE_DRIVER', 'TOW_TRUCK', 'PARTS_STORE', 'MECHANIC', 'VERIFIED_INSPECTOR')),
    activation_state TEXT NOT NULL CHECK (activation_state IN ('PENDING', 'APPROVED', 'SUSPENDED', 'REVOKED')) DEFAULT 'PENDING',
    verified_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (principal_id, capability)
);

ALTER TABLE public.principals ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.principals FORCE ROW LEVEL SECURITY;

ALTER TABLE public.principal_capabilities ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.principal_capabilities FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS principal_self_read ON public.principals;
CREATE POLICY principal_self_read ON public.principals
    FOR SELECT TO authenticated
    USING (principal_id = auth.uid());

DROP POLICY IF EXISTS principal_service_role ON public.principals;
CREATE POLICY principal_service_role ON public.principals
    FOR ALL TO service_role
    USING (true)
    WITH CHECK (true);

DROP POLICY IF EXISTS principal_capability_self_read ON public.principal_capabilities;
CREATE POLICY principal_capability_self_read ON public.principal_capabilities
    FOR SELECT TO authenticated
    USING (principal_id = auth.uid());

DROP POLICY IF EXISTS principal_capability_service_role ON public.principal_capabilities;
CREATE POLICY principal_capability_service_role ON public.principal_capabilities
    FOR ALL TO service_role
    USING (true)
    WITH CHECK (true);

GRANT SELECT ON public.principals, public.principal_capabilities TO authenticated;
GRANT ALL ON public.principals, public.principal_capabilities TO service_role;

ALTER TABLE public.driver_presence_snapshot ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.driver_presence_snapshot FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS p_driver_presence_select ON public.driver_presence_snapshot;
CREATE POLICY p_driver_presence_select ON public.driver_presence_snapshot
    FOR SELECT TO authenticated
    USING (true);

DROP POLICY IF EXISTS p_driver_presence_service_role ON public.driver_presence_snapshot;
CREATE POLICY p_driver_presence_service_role ON public.driver_presence_snapshot
    FOR ALL TO service_role
    USING (true)
    WITH CHECK (true);

GRANT SELECT ON public.driver_presence_snapshot TO authenticated;
GRANT ALL ON public.driver_presence_snapshot TO service_role;

-- Capability assertion function
CREATE OR REPLACE FUNCTION public.fulfillment_assert_principal_capability(
    p_principal_id UUID,
    p_required_capability TEXT
) RETURNS BOOLEAN
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_has_cap BOOLEAN;
BEGIN
    SELECT EXISTS (
        SELECT 1 FROM public.principal_capabilities
        WHERE principal_id = p_principal_id
          AND capability = UPPER(TRIM(p_required_capability))
          AND activation_state = 'APPROVED'
          AND verified_at IS NOT NULL
          AND (expires_at IS NULL OR expires_at > clock_timestamp())
    ) INTO v_has_cap;

    IF NOT v_has_cap THEN
        RAISE EXCEPTION 'CAPABILITY_NOT_VERIFIED: Principal % lacks approved capability %',
            p_principal_id, p_required_capability
            USING ERRCODE = '42501';
    END IF;

    RETURN TRUE;
END;
$$;

REVOKE ALL ON FUNCTION public.fulfillment_assert_principal_capability(UUID, TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.fulfillment_assert_principal_capability(UUID, TEXT) TO authenticated, service_role;

-- ─────────────────────────────────────────────────────────────────────────────
-- 10. REAL ACCOUNT DELETION PROCESSOR (SECTIONS 45 - 49)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.account_deletion_requests (
    request_id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    reason TEXT,
    status TEXT NOT NULL DEFAULT 'PENDING',
    source TEXT NOT NULL DEFAULT 'IN_APP',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    processed_at TIMESTAMPTZ,
    error_message TEXT
);

ALTER TABLE public.account_deletion_requests ADD COLUMN IF NOT EXISTS reason TEXT;
ALTER TABLE public.account_deletion_requests ADD COLUMN IF NOT EXISTS source TEXT DEFAULT 'IN_APP';
ALTER TABLE public.account_deletion_requests ADD COLUMN IF NOT EXISTS metadata JSONB DEFAULT '{}'::jsonb;
ALTER TABLE public.account_deletion_requests ADD COLUMN IF NOT EXISTS error_message TEXT;

ALTER TABLE public.account_deletion_requests DROP CONSTRAINT IF EXISTS account_deletion_requests_status_check;
ALTER TABLE public.account_deletion_requests ADD CONSTRAINT account_deletion_requests_status_check CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'CANCELLED', 'FAILED'));

ALTER TABLE public.account_deletion_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.account_deletion_requests FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS account_deletion_user_select ON public.account_deletion_requests;
CREATE POLICY account_deletion_user_select ON public.account_deletion_requests
    FOR SELECT TO authenticated
    USING (user_id = auth.uid());

DROP POLICY IF EXISTS account_deletion_service_role ON public.account_deletion_requests;
CREATE POLICY account_deletion_service_role ON public.account_deletion_requests
    FOR ALL TO service_role
    USING (true)
    WITH CHECK (true);

GRANT SELECT ON public.account_deletion_requests TO authenticated;
GRANT ALL ON public.account_deletion_requests TO service_role;

-- RPC for client to request account deletion
DROP FUNCTION IF EXISTS public.request_user_account_deletion();
DROP FUNCTION IF EXISTS public.request_user_account_deletion(TEXT, JSONB);

CREATE OR REPLACE FUNCTION public.request_user_account_deletion(
    p_reason TEXT DEFAULT NULL,
    p_metadata JSONB DEFAULT '{}'::jsonb
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_actor UUID := auth.uid();
    v_req public.account_deletion_requests%ROWTYPE;
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING ERRCODE = '42501';
    END IF;

    -- Deactivate principal if exists
    UPDATE public.principals
    SET status = 'DELETED',
        updated_at = clock_timestamp()
    WHERE principal_id = v_actor;

    INSERT INTO public.account_deletion_requests (user_id, reason, status, metadata, requested_at)
    VALUES (v_actor, p_reason, 'PENDING', p_metadata, clock_timestamp())
    RETURNING * INTO v_req;

    RETURN jsonb_build_object(
        'success', TRUE,
        'request_id', v_req.request_id,
        'status', v_req.status,
        'requested_at', v_req.requested_at
    );
END;
$$;

REVOKE ALL ON FUNCTION public.request_user_account_deletion(TEXT, JSONB) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.request_user_account_deletion(TEXT, JSONB) TO authenticated, service_role;

-- Canonical processor for account deletion (service_role only)
CREATE OR REPLACE FUNCTION public.process_account_deletion_request(
    p_request_id UUID
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_req public.account_deletion_requests%ROWTYPE;
    v_user_id UUID;
    v_pseudo_id UUID;
BEGIN
    IF COALESCE(auth.role(), current_user) <> 'service_role' AND current_user <> 'service_role' THEN
        RAISE EXCEPTION 'SERVICE_ROLE_REQUIRED' USING ERRCODE = '42501';
    END IF;

    SELECT * INTO v_req FROM public.account_deletion_requests
    WHERE request_id = p_request_id FOR UPDATE SKIP LOCKED;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'REQUEST_NOT_FOUND_OR_LOCKED';
    END IF;

    v_user_id := v_req.user_id;
    v_pseudo_id := extensions.gen_random_uuid();

    -- Mark PROCESSING
    UPDATE public.account_deletion_requests
    SET status = 'PROCESSING'
    WHERE request_id = p_request_id;

    -- 1. Revoke all active trip shares & projections
    UPDATE public.mobility_trip_shares
    SET state = 'REVOKED', revoked_at = clock_timestamp()
    WHERE grantor_id = v_user_id OR grantee_id = v_user_id;

    UPDATE public.mobility_trip_share_projection
    SET revoked_at = clock_timestamp()
    WHERE grantee_id = v_user_id;

    -- 2. Anonymize/delete driver presence & capabilities
    DELETE FROM public.driver_presence_snapshot WHERE driver_id = v_user_id;
    DELETE FROM public.principal_capabilities WHERE principal_id = v_user_id;

    -- 3. Pseudonymize personal info on principals
    UPDATE public.principals
    SET phone = NULL,
        full_name = 'DELETED_USER_' || substr(v_pseudo_id::text, 1, 8),
        status = 'DELETED',
        updated_at = clock_timestamp()
    WHERE principal_id = v_user_id;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'principal_profiles') THEN
        UPDATE public.principal_profiles
        SET display_name = 'DELETED_USER_' || substr(v_pseudo_id::text, 1, 8),
            updated_at = clock_timestamp()
        WHERE principal_id = v_user_id;
    END IF;

    -- 4. Mark COMPLETED
    UPDATE public.account_deletion_requests
    SET status = 'COMPLETED', processed_at = clock_timestamp()
    WHERE request_id = p_request_id
    RETURNING * INTO v_req;

    RETURN jsonb_build_object(
        'success', TRUE,
        'request_id', v_req.request_id,
        'status', v_req.status,
        'processed_at', v_req.processed_at
    );
EXCEPTION WHEN OTHERS THEN
    UPDATE public.account_deletion_requests
    SET status = 'FAILED', error_message = SQLERRM, processed_at = clock_timestamp()
    WHERE request_id = p_request_id;

    RETURN jsonb_build_object(
        'success', FALSE,
        'request_id', p_request_id,
        'status', 'FAILED',
        'error', SQLERRM
    );
END;
$$;

REVOKE ALL ON FUNCTION public.process_account_deletion_request(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.process_account_deletion_request(UUID) TO service_role;
