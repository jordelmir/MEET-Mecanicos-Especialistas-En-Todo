-- ─────────────────────────────────────────────────────────────────────────────
-- ELYSIUM GLOBAL MOBILITY OS — HARDENING, STOPS AUTHORITY & TRIP SHARING (V10)
-- Mandates:
--   1. Regla definitiva de paradas:
--      - MARKETPLACE_OFFERS: paradas al crear OK; paradas dinámicas tras aceptar RECHAZADAS.
--      - AUTO_DISPATCH / METERED: paradas dinámicas permitidas con recálculo autoritativo.
--   2. Endurecimiento de PIN de inicio de viaje:
--      - Rate-limiting y lockout tras 5 intentos fallidos (5 minutos de bloqueo).
--      - Contador de intentos restantes y reinicio tras acierto.
--   3. Primitiva de viaje compartido (trip:read:safe):
--      - Compartir viaje con usuarios MEET específicos con revocación en tiempo real.
-- ─────────────────────────────────────────────────────────────────────────────

SET check_function_bodies = off;

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. EXTEND TRIPS TABLE FOR PIN BRUTE-FORCE RESILIENCE
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE public.trips
    ADD COLUMN IF NOT EXISTS failed_pin_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS pin_locked_until TIMESTAMPTZ DEFAULT NULL;

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. TRIP SHARING PRIMITIVE (trip:read:safe)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.mobility_trip_shares (
    share_id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    trip_id UUID NOT NULL REFERENCES public.trips(trip_id) ON DELETE CASCADE,
    grantor_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    grantee_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    state TEXT NOT NULL CHECK (state IN ('ACTIVE', 'REVOKED', 'EXPIRED')) DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    revoked_at TIMESTAMPTZ DEFAULT NULL,
    CONSTRAINT uq_trip_grantee UNIQUE (trip_id, grantee_id),
    CONSTRAINT chk_no_self_share CHECK (grantor_id <> grantee_id)
);

CREATE INDEX IF NOT EXISTS idx_trip_shares_grantee
    ON public.mobility_trip_shares(grantee_id, state);

CREATE INDEX IF NOT EXISTS idx_trip_shares_trip
    ON public.mobility_trip_shares(trip_id, state);

ALTER TABLE public.mobility_trip_shares ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.mobility_trip_shares FORCE ROW LEVEL SECURITY;

REVOKE ALL ON public.mobility_trip_shares FROM PUBLIC, anon, authenticated;
GRANT SELECT ON public.mobility_trip_shares TO authenticated, service_role;

DROP POLICY IF EXISTS p_trip_shares_select ON public.mobility_trip_shares;
CREATE POLICY p_trip_shares_select ON public.mobility_trip_shares
    FOR SELECT TO authenticated
    USING (grantor_id = auth.uid() OR grantee_id = auth.uid());

-- Enable & Force RLS on public.trips
ALTER TABLE public.trips ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.trips FORCE ROW LEVEL SECURITY;

-- Allow rider and driver to read their trip
DROP POLICY IF EXISTS p_trips_parties_select ON public.trips;
CREATE POLICY p_trips_parties_select ON public.trips
    FOR SELECT TO authenticated
    USING (rider_id = auth.uid() OR driver_id = auth.uid());

-- Allow service_role full access
DROP POLICY IF EXISTS p_trips_service_role ON public.trips;
CREATE POLICY p_trips_service_role ON public.trips
    FOR ALL TO service_role
    USING (true)
    WITH CHECK (true);

-- Allow grantees with ACTIVE share to read the trip
DROP POLICY IF EXISTS p_trips_share_read ON public.trips;
CREATE POLICY p_trips_share_read ON public.trips
    FOR SELECT TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.mobility_trip_shares s
            WHERE s.trip_id = trips.trip_id
              AND s.grantee_id = auth.uid()
              AND s.state = 'ACTIVE'
        )
    );

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. RPC: MOBILITY SHARE TRIP
-- ─────────────────────────────────────────────────────────────────────────────

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
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING ERRCODE = '42501';
    END IF;

    IF p_grantee_id IS NULL OR p_grantee_id = v_actor THEN
        RAISE EXCEPTION 'INVALID_GRANTEE_ID';
    END IF;

    SELECT * INTO v_trip FROM public.trips WHERE trip_id = p_trip_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'TRIP_NOT_FOUND';
    END IF;

    IF v_trip.rider_id <> v_actor THEN
        RAISE EXCEPTION 'FORBIDDEN: Only the rider can share trip safety access'
            USING ERRCODE = '42501';
    END IF;

    IF v_trip.state IN ('COMPLETED', 'CANCELLED') THEN
        RAISE EXCEPTION 'CANNOT_SHARE_TERMINATED_TRIP: Current state is %', v_trip.state;
    END IF;

    -- Upsert share
    INSERT INTO public.mobility_trip_shares (
        trip_id,
        grantor_id,
        grantee_id,
        state,
        created_at,
        revoked_at
    ) VALUES (
        p_trip_id,
        v_actor,
        p_grantee_id,
        'ACTIVE',
        clock_timestamp(),
        NULL
    )
    ON CONFLICT (trip_id, grantee_id) DO UPDATE
    SET
        state = 'ACTIVE',
        revoked_at = NULL
    RETURNING * INTO v_share;

    RETURN jsonb_build_object(
        'success', TRUE,
        'share', row_to_json(v_share)
    );
END;
$$;

REVOKE ALL ON FUNCTION public.mobility_share_trip(UUID, UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobility_share_trip(UUID, UUID) TO authenticated, service_role;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. RPC: MOBILITY REVOKE TRIP SHARE
-- ─────────────────────────────────────────────────────────────────────────────

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
    v_share public.mobility_trip_shares%ROWTYPE;
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING ERRCODE = '42501';
    END IF;

    SELECT * INTO v_trip FROM public.trips WHERE trip_id = p_trip_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'TRIP_NOT_FOUND';
    END IF;

    IF v_trip.rider_id <> v_actor THEN
        RAISE EXCEPTION 'FORBIDDEN: Only the rider can revoke trip access'
            USING ERRCODE = '42501';
    END IF;

    UPDATE public.mobility_trip_shares
    SET
        state = 'REVOKED',
        revoked_at = clock_timestamp()
    WHERE trip_id = p_trip_id
      AND grantee_id = p_grantee_id
    RETURNING * INTO v_share;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'SHARE_NOT_FOUND';
    END IF;

    RETURN jsonb_build_object(
        'success', TRUE,
        'share', row_to_json(v_share)
    );
END;
$$;

REVOKE ALL ON FUNCTION public.mobility_revoke_trip_share(UUID, UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobility_revoke_trip_share(UUID, UUID) TO authenticated, service_role;

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. RPC: MOBILITY TRANSITION TRIP (HARDENED PIN BRUTE-FORCE LOCKOUT)
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
    v_failed INT;
    v_retry_after INT;
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING ERRCODE = '42501';
    END IF;

    IF p_idempotency_key IS NOT NULL THEN
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

    -- ─────────────────────────────────────────────────────────────
    -- PIN VERIFICATION WITH 5-ATTEMPT LOCKOUT (P4 & P20 HARDENING)
    -- ─────────────────────────────────────────────────────────────
    IF p_target_state = 'RIDER_ONBOARD' AND v_trip.verification_pin_hash IS NOT NULL THEN
        -- Check lockout
        IF v_trip.pin_locked_until IS NOT NULL AND v_trip.pin_locked_until > clock_timestamp() THEN
            v_retry_after := GREATEST(1, EXTRACT(EPOCH FROM (v_trip.pin_locked_until - clock_timestamp()))::INT);
            RETURN jsonb_build_object(
                'success', FALSE,
                'conflict', TRUE,
                'error_code', 'PIN_LOCKED_TOO_MANY_ATTEMPTS',
                'message', 'Demasiados intentos de PIN fallidos. El abordaje está bloqueado temporalmente.',
                'retry_after_seconds', v_retry_after
            );
        END IF;

        IF p_verification_pin IS NULL OR trim(p_verification_pin) = '' THEN
            RETURN jsonb_build_object(
                'success', FALSE,
                'conflict', FALSE,
                'error_code', 'PIN_REQUIRED',
                'message', 'Se requiere el código PIN de verificación para iniciar el abordaje.'
            );
        END IF;

        IF encode(extensions.digest(p_verification_pin, 'sha256'), 'hex') <> v_trip.verification_pin_hash THEN
            v_failed := v_trip.failed_pin_attempts + 1;
            IF v_failed >= 5 THEN
                UPDATE public.trips
                SET
                    failed_pin_attempts = v_failed,
                    pin_locked_until = clock_timestamp() + INTERVAL '5 minutes',
                    updated_at = clock_timestamp()
                WHERE trip_id = p_trip_id;

                RETURN jsonb_build_object(
                    'success', FALSE,
                    'conflict', TRUE,
                    'error_code', 'PIN_LOCKED_TOO_MANY_ATTEMPTS',
                    'message', 'Código PIN incorrecto. Se ha bloqueado por 5 minutos tras 5 intentos fallidos.',
                    'retry_after_seconds', 300
                );
            ELSE
                UPDATE public.trips
                SET
                    failed_pin_attempts = v_failed,
                    updated_at = clock_timestamp()
                WHERE trip_id = p_trip_id;

                RETURN jsonb_build_object(
                    'success', FALSE,
                    'conflict', FALSE,
                    'error_code', 'PIN_INVALID',
                    'message', 'El código PIN ingresado es incorrecto.',
                    'remaining_attempts', (5 - v_failed)
                );
            END IF;
        END IF;

        -- Successful PIN: reset failed attempts
        UPDATE public.trips
        SET
            failed_pin_attempts = 0,
            pin_locked_until = NULL,
            updated_at = clock_timestamp()
        WHERE trip_id = p_trip_id;
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
            'state', v_trip.state,
            'version', v_trip.version
        )
    );

    IF p_idempotency_key IS NOT NULL THEN
        INSERT INTO public.mobility_command_receipts (
            actor_id, command_scope, idempotency_key, request_hash, requested_aggregate_id, aggregate_id, response
        ) VALUES (
            v_actor, 'TRANSITION_TRIP', p_idempotency_key, v_hash, p_trip_id, v_trip.trip_id, v_response
        );
    END IF;

    RETURN v_response;
END;
$$;

REVOKE ALL ON FUNCTION public.mobility_transition_trip(UUID, TEXT, BIGINT, TEXT, UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobility_transition_trip(UUID, TEXT, BIGINT, TEXT, UUID) TO authenticated, service_role;

-- ─────────────────────────────────────────────────────────────────────────────
-- 6. RPC: MOBILITY ADD TRIP STOP (CANONICAL STOP INVARIANT: P8 & P22)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.mobility_add_trip_stop(
    p_trip_id UUID,
    p_latitude DOUBLE PRECISION,
    p_longitude DOUBLE PRECISION,
    p_accuracy_meters REAL DEFAULT NULL,
    p_address TEXT DEFAULT NULL,
    p_display_name TEXT DEFAULT NULL,
    p_place_id TEXT DEFAULT NULL,
    p_idempotency_key UUID DEFAULT NULL
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_actor UUID := auth.uid();
    v_trip public.trips%ROWTYPE;
    v_request public.ride_requests%ROWTYPE;
    v_market public.mobility_markets%ROWTYPE;
    v_hash TEXT;
    v_receipt public.mobility_command_receipts%ROWTYPE;
    v_response JSONB;
    v_current_stops INT;
    v_dest_stop public.ride_request_stops%ROWTYPE;
    v_new_seq INT;
    v_new_stop public.ride_request_stops%ROWTYPE;
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING ERRCODE = '42501';
    END IF;

    IF p_latitude IS NULL OR p_longitude IS NULL OR
       p_latitude < -90.0 OR p_latitude > 90.0 OR
       p_longitude < -180.0 OR p_longitude > 180.0 THEN
        RAISE EXCEPTION 'INVALID_COORDINATES';
    END IF;

    IF p_idempotency_key IS NOT NULL THEN
        v_hash := encode(
            extensions.digest(
                convert_to(
                    jsonb_build_object(
                        'trip_id', p_trip_id,
                        'lat', p_latitude,
                        'lng', p_longitude,
                        'idempotency_key', p_idempotency_key
                    )::text,
                    'UTF8'
                ),
                'sha256'
            ),
            'hex'
        );

        PERFORM pg_advisory_xact_lock(hashtextextended('ADD_STOP:' || p_idempotency_key::text, 0));

        SELECT * INTO v_receipt FROM public.mobility_command_receipts
        WHERE actor_id = v_actor AND command_scope = 'ADD_STOP' AND idempotency_key = p_idempotency_key;

        IF FOUND THEN
            IF v_receipt.request_hash <> v_hash THEN
                RAISE EXCEPTION 'IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD' USING ERRCODE = '23505';
            END IF;
            RETURN v_receipt.response;
        END IF;
    END IF;

    SELECT * INTO v_trip FROM public.trips WHERE trip_id = p_trip_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'TRIP_NOT_FOUND';
    END IF;

    IF v_trip.rider_id <> v_actor THEN
        RAISE EXCEPTION 'FORBIDDEN: Only the passenger can add intermediate stops'
            USING ERRCODE = '42501';
    END IF;

    IF v_trip.state NOT IN ('ASSIGNED', 'DRIVER_EN_ROUTE', 'DRIVER_ARRIVED', 'WAITING_FOR_RIDER', 'RIDER_ONBOARD', 'IN_PROGRESS') THEN
        RAISE EXCEPTION 'INVALID_TRIP_STATE_FOR_ADDING_STOP: Current state is %', v_trip.state;
    END IF;

    SELECT * INTO v_request FROM public.ride_requests
    WHERE ride_request_id = v_trip.ride_request_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'RIDE_REQUEST_NOT_FOUND';
    END IF;

    -- ─────────────────────────────────────────────────────────────
    -- THE CANONICAL BUSINESS INVARIANT: P8 & P22
    -- MARKETPLACE_OFFERS: Fixed price negotiated contract cannot add stops!
    -- ─────────────────────────────────────────────────────────────
    IF v_request.dispatch_mode = 'MARKETPLACE_OFFERS' THEN
        RAISE EXCEPTION 'ADD_STOP_NOT_ALLOWED_IN_MARKETPLACE_OFFERS: Los viajes con tarifa fija negociada no permiten agregar paradas despues de aceptar la oferta.'
            USING ERRCODE = '42804';
    END IF;

    -- AUTO_DISPATCH / METERED: Check max allowed intermediate stops
    SELECT * INTO v_market FROM public.mobility_markets WHERE market_id = v_request.market_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'MARKET_NOT_FOUND';
    END IF;

    SELECT count(*) INTO v_current_stops
    FROM public.ride_request_stops
    WHERE ride_request_id = v_trip.ride_request_id AND stop_type = 'INTERMEDIATE';

    IF v_current_stops >= v_market.max_intermediate_stops THEN
        RAISE EXCEPTION 'TOO_MANY_INTERMEDIATE_STOPS: El mercado permite un maximo de % paradas intermedias', v_market.max_intermediate_stops;
    END IF;

    -- Find current destination stop and shift it forward
    SELECT * INTO v_dest_stop
    FROM public.ride_request_stops
    WHERE ride_request_id = v_trip.ride_request_id AND stop_type = 'DESTINATION'
    ORDER BY sequence DESC LIMIT 1 FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'DESTINATION_STOP_NOT_FOUND';
    END IF;

    v_new_seq := v_dest_stop.sequence;

    -- Push destination sequence up by 1
    UPDATE public.ride_request_stops
    SET sequence = sequence + 1
    WHERE stop_id = v_dest_stop.stop_id;

    -- Insert new intermediate stop at sequence
    INSERT INTO public.ride_request_stops (
        ride_request_id,
        sequence,
        stop_type,
        location,
        accuracy_meters,
        address,
        display_name,
        place_id
    ) VALUES (
        v_trip.ride_request_id,
        v_new_seq,
        'INTERMEDIATE',
        extensions.ST_SetSRID(extensions.ST_MakePoint(p_longitude, p_latitude), 4326),
        p_accuracy_meters,
        p_address,
        p_display_name,
        p_place_id
    ) RETURNING * INTO v_new_stop;

    v_response := jsonb_build_object(
        'success', TRUE,
        'stop_added', row_to_json(v_new_stop),
        'total_intermediate_stops', (v_current_stops + 1)
    );

    IF p_idempotency_key IS NOT NULL THEN
        INSERT INTO public.mobility_command_receipts (
            actor_id, command_scope, idempotency_key, request_hash, requested_aggregate_id, aggregate_id, response
        ) VALUES (
            v_actor, 'ADD_STOP', p_idempotency_key, v_hash, p_trip_id, v_new_stop.stop_id, v_response
        );
    END IF;

    RETURN v_response;
END;
$$;

REVOKE ALL ON FUNCTION public.mobility_add_trip_stop(UUID, DOUBLE PRECISION, DOUBLE PRECISION, REAL, TEXT, TEXT, TEXT, UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobility_add_trip_stop(UUID, DOUBLE PRECISION, DOUBLE PRECISION, REAL, TEXT, TEXT, TEXT, UUID) TO authenticated, service_role;
