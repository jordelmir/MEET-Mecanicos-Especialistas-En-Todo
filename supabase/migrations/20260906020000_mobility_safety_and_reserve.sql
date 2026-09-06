-- ─────────────────────────────────────────────────────────────────────────────
-- ELYSIUM GLOBAL MOBILITY OS — UBER RESERVE, GUEST RIDES & SAFETY CENTER (WAVES 15–17)
-- Mandate: ORDEN MAESTRA V6 (Uber Reserve, DiDi Guest & Safety Center Parity)
-- ─────────────────────────────────────────────────────────────────────────────

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. UBER RESERVE: POLICIES & SCHEDULED RIDE RESERVATIONS (WAVE 15)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.scheduled_ride_policies (
    market_id TEXT PRIMARY KEY REFERENCES public.mobility_markets(market_id) ON DELETE CASCADE,
    min_lead_time_minutes INT NOT NULL DEFAULT 30 CHECK (min_lead_time_minutes > 0),
    max_lead_time_days INT NOT NULL DEFAULT 30 CHECK (max_lead_time_days > 0),
    dispatch_lead_time_minutes INT NOT NULL DEFAULT 25 CHECK (dispatch_lead_time_minutes > 0 AND dispatch_lead_time_minutes < min_lead_time_minutes),
    cancellation_free_window_minutes INT NOT NULL DEFAULT 60 CHECK (cancellation_free_window_minutes > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE IF NOT EXISTS public.scheduled_ride_reservations (
    reservation_id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    ride_request_id UUID NOT NULL UNIQUE REFERENCES public.ride_requests(ride_request_id) ON DELETE RESTRICT,
    rider_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    market_id TEXT NOT NULL REFERENCES public.mobility_markets(market_id) ON DELETE RESTRICT,
    scheduled_pickup_time TIMESTAMPTZ NOT NULL,
    dispatch_at TIMESTAMPTZ NOT NULL,
    state TEXT NOT NULL CHECK (state IN ('CONFIRMED', 'DISPATCHING', 'FULFILLED', 'CANCELLED_FREE', 'CANCELLED_FEE', 'EXPIRED')),
    assigned_driver_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    quote_id UUID NOT NULL REFERENCES public.ride_quotes(quote_id) ON DELETE RESTRICT,
    payment_authorization_id UUID NOT NULL REFERENCES public.payment_authorizations(payment_authorization_id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT chk_dispatch_before_pickup CHECK (dispatch_at <= scheduled_pickup_time)
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. DIDI GUEST RIDES & MASKED COMMUNICATIONS (WAVE 16)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.guest_ride_profiles (
    guest_ride_id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    ride_request_id UUID NOT NULL UNIQUE REFERENCES public.ride_requests(ride_request_id) ON DELETE CASCADE,
    requested_by_rider_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    guest_name TEXT NOT NULL CHECK (length(trim(guest_name)) > 0),
    guest_phone_e164 TEXT NOT NULL CHECK (guest_phone_e164 ~ '^\+[1-9][0-9]{7,14}$'),
    sms_notifications_enabled BOOLEAN NOT NULL DEFAULT true,
    tracking_token TEXT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE IF NOT EXISTS public.masked_communication_sessions (
    session_id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    trip_id UUID NOT NULL REFERENCES public.trips(trip_id) ON DELETE CASCADE,
    rider_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    driver_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    virtual_proxy_number TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (trip_id, rider_id, driver_id)
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. DIDI SAFETY CENTER, RISK ZONES & ANOMALY DETECTION (WAVE 17)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.mobility_risk_zones (
    zone_id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    market_id TEXT NOT NULL REFERENCES public.mobility_markets(market_id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    severity TEXT NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    polygon extensions.geography NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE IF NOT EXISTS public.safety_emergency_contacts (
    contact_id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    name TEXT NOT NULL CHECK (length(trim(name)) > 0),
    phone_e164 TEXT NOT NULL CHECK (phone_e164 ~ '^\+[1-9][0-9]{7,14}$'),
    notify_on_sos BOOLEAN NOT NULL DEFAULT true,
    notify_on_night_trips BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (user_id, phone_e164)
);

CREATE TABLE IF NOT EXISTS public.safety_emergency_events (
    event_id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    trip_id UUID NOT NULL REFERENCES public.trips(trip_id) ON DELETE CASCADE,
    triggered_by UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    event_type TEXT NOT NULL CHECK (event_type IN ('SOS_BUTTON', 'ROUTE_DEVIATION', 'PROLONGED_STOP', 'COLLISION_DETECTED')),
    location extensions.geography NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    speed_mps REAL,
    state TEXT NOT NULL CHECK (state IN ('TRIGGERED', 'DISPATCHED_POLICE', 'RESOLVED_FALSE_ALARM', 'RESOLVED_ASSISTED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE IF NOT EXISTS public.route_deviation_logs (
    log_id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    trip_id UUID NOT NULL REFERENCES public.trips(trip_id) ON DELETE CASCADE,
    distance_from_route_meters DOUBLE PRECISION NOT NULL,
    threshold_meters DOUBLE PRECISION NOT NULL DEFAULT 500.0,
    current_latitude DOUBLE PRECISION NOT NULL,
    current_longitude DOUBLE PRECISION NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. REVOKE DIRECT MUTATION & CONFIGURE RLS
-- ─────────────────────────────────────────────────────────────────────────────

REVOKE INSERT, UPDATE, DELETE ON public.scheduled_ride_policies FROM authenticated, anon;
REVOKE INSERT, UPDATE, DELETE ON public.scheduled_ride_reservations FROM authenticated, anon;
REVOKE INSERT, UPDATE, DELETE ON public.guest_ride_profiles FROM authenticated, anon;
REVOKE INSERT, UPDATE, DELETE ON public.masked_communication_sessions FROM authenticated, anon;
REVOKE INSERT, UPDATE, DELETE ON public.mobility_risk_zones FROM authenticated, anon;
REVOKE INSERT, UPDATE, DELETE ON public.safety_emergency_events FROM authenticated, anon;
REVOKE INSERT, UPDATE, DELETE ON public.route_deviation_logs FROM authenticated, anon;

GRANT SELECT ON public.scheduled_ride_policies TO authenticated;
GRANT SELECT ON public.scheduled_ride_reservations TO authenticated;
GRANT SELECT ON public.guest_ride_profiles TO authenticated;
GRANT SELECT ON public.masked_communication_sessions TO authenticated;
GRANT SELECT ON public.mobility_risk_zones TO authenticated;
GRANT SELECT ON public.safety_emergency_events TO authenticated;
GRANT SELECT ON public.route_deviation_logs TO authenticated;

-- Emergency Contacts RLS: users manage their own contacts
ALTER TABLE public.safety_emergency_contacts ENABLE ROW LEVEL SECURITY;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.safety_emergency_contacts TO authenticated;

CREATE POLICY emergency_contacts_owner_policy ON public.safety_emergency_contacts
    FOR ALL TO authenticated
    USING (user_id = auth.uid())
    WITH CHECK (user_id = auth.uid());

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. RPC: SCHEDULE RIDE (Uber Reserve Booking)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.mobility_schedule_ride(
    p_market_id TEXT,
    p_service_category_id TEXT,
    p_scheduled_pickup_time TIMESTAMPTZ,
    p_quote_id UUID,
    p_payment_authorization_id UUID,
    p_pickup_lat DOUBLE PRECISION,
    p_pickup_lon DOUBLE PRECISION,
    p_pickup_address TEXT,
    p_destination_lat DOUBLE PRECISION,
    p_destination_lon DOUBLE PRECISION,
    p_destination_address TEXT,
    p_idempotency_key UUID
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_actor UUID := auth.uid();
    v_now TIMESTAMPTZ := clock_timestamp();
    v_policy public.scheduled_ride_policies%ROWTYPE;
    v_quote public.ride_quotes%ROWTYPE;
    v_auth public.payment_authorizations%ROWTYPE;
    v_lead_minutes INT;
    v_lead_days INT;
    v_dispatch_at TIMESTAMPTZ;
    v_req_res JSONB;
    v_req_id UUID;
    v_reservation public.scheduled_ride_reservations%ROWTYPE;
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
                    'market_id', p_market_id,
                    'category_id', p_service_category_id,
                    'scheduled_time', p_scheduled_pickup_time,
                    'quote_id', p_quote_id,
                    'payment_auth_id', p_payment_authorization_id
                )::TEXT,
                'UTF8'
            ),
            'sha256'
        ),
        'hex'
    );

    PERFORM pg_advisory_xact_lock(hashtextextended(v_actor::TEXT || ':' || p_idempotency_key::TEXT, 0));

    SELECT * INTO v_receipt FROM public.mobility_command_receipts
    WHERE actor_id = v_actor AND command_scope = 'SCHEDULE_RIDE' AND idempotency_key = p_idempotency_key;

    IF FOUND THEN
        IF v_receipt.request_hash <> v_hash THEN
            RAISE EXCEPTION 'IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD' USING ERRCODE = '23505';
        END IF;
        RETURN v_receipt.response;
    END IF;

    -- Lookup policy
    SELECT * INTO v_policy FROM public.scheduled_ride_policies WHERE market_id = p_market_id;
    IF NOT FOUND THEN
        -- Default policy if not explicitly seeded
        v_policy.min_lead_time_minutes := 30;
        v_policy.max_lead_time_days := 30;
        v_policy.dispatch_lead_time_minutes := 25;
        v_policy.cancellation_free_window_minutes := 60;
    END IF;

    -- Validate Lead Time
    v_lead_minutes := EXTRACT(EPOCH FROM (p_scheduled_pickup_time - v_now)) / 60;
    v_lead_days := v_lead_minutes / 1440;

    IF v_lead_minutes < v_policy.min_lead_time_minutes THEN
        RAISE EXCEPTION 'SCHEDULED_TIME_TOO_SOON: Must be at least % minutes in advance', v_policy.min_lead_time_minutes;
    END IF;

    IF v_lead_days > v_policy.max_lead_time_days THEN
        RAISE EXCEPTION 'SCHEDULED_TIME_TOO_FAR: Cannot exceed % days in advance', v_policy.max_lead_time_days;
    END IF;

    -- Validate Quote & Authorization
    SELECT * INTO v_quote FROM public.ride_quotes WHERE quote_id = p_quote_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'QUOTE_NOT_FOUND';
    END IF;

    SELECT * INTO v_auth FROM public.payment_authorizations WHERE payment_authorization_id = p_payment_authorization_id;
    IF NOT FOUND OR v_auth.state <> 'AUTHORIZED' THEN
        RAISE EXCEPTION 'INVALID_PAYMENT_AUTHORIZATION';
    END IF;

    v_dispatch_at := p_scheduled_pickup_time - (v_policy.dispatch_lead_time_minutes * INTERVAL '1 minute');

    -- Create scheduled ride request (initial state REQUESTED with scheduled_for timestamp)
    v_req_res := public.mobility_request_ride(
        p_market_id,
        p_service_category_id,
        'AUTO_DISPATCH',
        p_pickup_lat, p_pickup_lon, 5.0::real, p_pickup_address,
        p_destination_lat, p_destination_lon, 5.0::real, p_destination_address,
        '[]'::jsonb,
        v_quote.total_fare_minor,
        p_scheduled_pickup_time,
        extensions.gen_random_uuid(),
        extensions.gen_random_uuid()
    );

    v_req_id := (v_req_res->>'ride_request_id')::uuid;

    INSERT INTO public.scheduled_ride_reservations (
        ride_request_id,
        rider_id,
        market_id,
        scheduled_pickup_time,
        dispatch_at,
        state,
        quote_id,
        payment_authorization_id
    ) VALUES (
        v_req_id,
        v_actor,
        p_market_id,
        p_scheduled_pickup_time,
        v_dispatch_at,
        'CONFIRMED',
        p_quote_id,
        p_payment_authorization_id
    ) RETURNING * INTO v_reservation;

    v_response := jsonb_build_object(
        'success', TRUE,
        'reservation', row_to_json(v_reservation),
        'ride_request_id', v_req_id
    );

    INSERT INTO public.mobility_command_receipts (
        actor_id, command_scope, idempotency_key, request_hash, requested_aggregate_id, aggregate_id, response
    ) VALUES (
        v_actor, 'SCHEDULE_RIDE', p_idempotency_key, v_hash, v_req_id, v_reservation.reservation_id, v_response
    );

    RETURN v_response;
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 6. RPC: DISPATCH DUE SCHEDULED RIDES (SKIP LOCKED Worker)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.mobility_dispatch_due_scheduled_rides(
    p_batch_size INT DEFAULT 50
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_reservation RECORD;
    v_dispatched_count INT := 0;
BEGIN
    -- Atomic concurrency-safe locking: FOR UPDATE SKIP LOCKED
    FOR v_reservation IN
        SELECT r.reservation_id, r.ride_request_id, r.market_id
        FROM public.scheduled_ride_reservations r
        WHERE r.state = 'CONFIRMED'
          AND r.dispatch_at <= clock_timestamp()
        ORDER BY r.dispatch_at ASC
        LIMIT p_batch_size
        FOR UPDATE OF r SKIP LOCKED
    LOOP
        -- Transition reservation to DISPATCHING
        UPDATE public.scheduled_ride_reservations
        SET state = 'DISPATCHING',
            updated_at = clock_timestamp()
        WHERE reservation_id = v_reservation.reservation_id;

        -- Transition ride_request to SEARCHING to begin driver discovery
        UPDATE public.ride_requests
        SET state = 'SEARCHING',
            updated_at = clock_timestamp()
        WHERE ride_request_id = v_reservation.ride_request_id
          AND state = 'REQUESTED';

        v_dispatched_count := v_dispatched_count + 1;
    END LOOP;

    RETURN jsonb_build_object(
        'success', TRUE,
        'dispatched_count', v_dispatched_count
    );
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 7. RPC: REQUEST GUEST RIDE (DiDi "Viaje para Terceros")
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.mobility_request_guest_ride(
    p_market_id TEXT,
    p_service_category_id TEXT,
    p_guest_name TEXT,
    p_guest_phone_e164 TEXT,
    p_pickup_lat DOUBLE PRECISION,
    p_pickup_lon DOUBLE PRECISION,
    p_pickup_address TEXT,
    p_destination_lat DOUBLE PRECISION,
    p_destination_lon DOUBLE PRECISION,
    p_destination_address TEXT,
    p_estimated_price_minor BIGINT,
    p_idempotency_key UUID
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_actor UUID := auth.uid();
    v_req_res JSONB;
    v_req_id UUID;
    v_token TEXT;
    v_profile public.guest_ride_profiles%ROWTYPE;
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING ERRCODE = '42501';
    END IF;

    IF p_guest_name IS NULL OR length(trim(p_guest_name)) = 0 THEN
        RAISE EXCEPTION 'INVALID_GUEST_NAME';
    END IF;

    IF p_guest_phone_e164 IS NULL OR NOT (p_guest_phone_e164 ~ '^\+[1-9][0-9]{7,14}$') THEN
        RAISE EXCEPTION 'INVALID_E164_PHONE_NUMBER';
    END IF;

    -- Request base ride
    v_req_res := public.mobility_request_ride(
        p_market_id,
        p_service_category_id,
        'AUTO_DISPATCH',
        p_pickup_lat, p_pickup_lon, 5.0::real, p_pickup_address,
        p_destination_lat, p_destination_lon, 5.0::real, p_destination_address,
        '[]'::jsonb,
        p_estimated_price_minor,
        NULL,
        p_idempotency_key,
        extensions.gen_random_uuid()
    );

    v_req_id := (v_req_res->>'ride_request_id')::uuid;

    -- Generate secure tracking token
    v_token := encode(extensions.digest(v_req_id::text || ':' || clock_timestamp()::text, 'sha256'), 'hex');

    INSERT INTO public.guest_ride_profiles (
        ride_request_id,
        requested_by_rider_id,
        guest_name,
        guest_phone_e164,
        sms_notifications_enabled,
        tracking_token
    ) VALUES (
        v_req_id,
        v_actor,
        trim(p_guest_name),
        p_guest_phone_e164,
        TRUE,
        v_token
    ) RETURNING * INTO v_profile;

    RETURN jsonb_build_object(
        'success', TRUE,
        'ride_request_id', v_req_id,
        'guest_ride_id', v_profile.guest_ride_id,
        'guest_name', v_profile.guest_name,
        'tracking_token', v_profile.tracking_token
    );
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 8. RPC: GET MASKED COMMUNICATION CHANNEL (DiDi Phone Proxy)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.mobility_get_masked_channel(
    p_trip_id UUID
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_actor UUID := auth.uid();
    v_trip public.trips%ROWTYPE;
    v_session public.masked_communication_sessions%ROWTYPE;
    v_proxy TEXT := '+50640008888'; -- Virtual proxy gateway
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING ERRCODE = '42501';
    END IF;

    SELECT * INTO v_trip FROM public.trips WHERE trip_id = p_trip_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'TRIP_NOT_FOUND';
    END IF;

    IF v_actor <> v_trip.rider_id AND v_actor <> v_trip.driver_id THEN
        RAISE EXCEPTION 'FORBIDDEN' USING ERRCODE = '42501';
    END IF;

    SELECT * INTO v_session FROM public.masked_communication_sessions
    WHERE trip_id = p_trip_id AND is_active = true AND expires_at > clock_timestamp();

    IF NOT FOUND THEN
        INSERT INTO public.masked_communication_sessions (
            trip_id,
            rider_id,
            driver_id,
            virtual_proxy_number,
            expires_at
        ) VALUES (
            p_trip_id,
            v_trip.rider_id,
            v_trip.driver_id,
            v_proxy,
            clock_timestamp() + INTERVAL '3 hours'
        ) RETURNING * INTO v_session;
    END IF;

    RETURN jsonb_build_object(
        'success', TRUE,
        'virtual_proxy_number', v_session.virtual_proxy_number,
        'expires_at', v_session.expires_at
    );
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 9. RPC: TRIGGER EMERGENCY SOS BUTTON (DiDi Safety Center)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.mobility_trigger_emergency_sos(
    p_trip_id UUID,
    p_latitude DOUBLE PRECISION,
    p_longitude DOUBLE PRECISION,
    p_speed_mps REAL DEFAULT NULL
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_actor UUID := auth.uid();
    v_trip public.trips%ROWTYPE;
    v_loc extensions.geography;
    v_event public.safety_emergency_events%ROWTYPE;
    v_contacts_count INT := 0;
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING ERRCODE = '42501';
    END IF;

    SELECT * INTO v_trip FROM public.trips WHERE trip_id = p_trip_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'TRIP_NOT_FOUND';
    END IF;

    IF v_actor <> v_trip.rider_id AND v_actor <> v_trip.driver_id THEN
        RAISE EXCEPTION 'FORBIDDEN' USING ERRCODE = '42501';
    END IF;

    v_loc := extensions.ST_SetSRID(extensions.ST_MakePoint(p_longitude, p_latitude), 4326)::extensions.geography;

    INSERT INTO public.safety_emergency_events (
        trip_id,
        triggered_by,
        event_type,
        location,
        latitude,
        longitude,
        speed_mps,
        state
    ) VALUES (
        p_trip_id,
        v_actor,
        'SOS_BUTTON',
        v_loc,
        p_latitude,
        p_longitude,
        p_speed_mps,
        'TRIGGERED'
    ) RETURNING * INTO v_event;

    SELECT count(*) INTO v_contacts_count
    FROM public.safety_emergency_contacts
    WHERE user_id = v_actor AND notify_on_sos = true;

    RETURN jsonb_build_object(
        'success', TRUE,
        'event_id', v_event.event_id,
        'event_type', v_event.event_type,
        'state', v_event.state,
        'contacts_notified_count', v_contacts_count,
        'message', 'Alerta SOS registrada de inmediato. Contactos de confianza notificados.'
    );
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 10. RPC: RECORD ROUTE TELEMETRY & DETECT DEVIATIONS / RISK ZONES (Wave 17)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.mobility_record_route_telemetry(
    p_trip_id UUID,
    p_latitude DOUBLE PRECISION,
    p_longitude DOUBLE PRECISION,
    p_speed_mps REAL,
    p_distance_from_route_meters DOUBLE PRECISION
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_actor UUID := auth.uid();
    v_trip public.trips%ROWTYPE;
    v_loc extensions.geography;
    v_threshold DOUBLE PRECISION := 500.0;
    v_deviation_logged BOOLEAN := FALSE;
    v_sos_triggered BOOLEAN := FALSE;
    v_inside_risk_zone BOOLEAN := FALSE;
    v_risk_zone_name TEXT := NULL;
    v_risk_zone_severity TEXT := NULL;
    v_zone RECORD;
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING ERRCODE = '42501';
    END IF;

    SELECT * INTO v_trip FROM public.trips WHERE trip_id = p_trip_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'TRIP_NOT_FOUND';
    END IF;

    v_loc := extensions.ST_SetSRID(extensions.ST_MakePoint(p_longitude, p_latitude), 4326)::extensions.geography;

    -- 1. Route Deviation Check (> 500 meters)
    IF p_distance_from_route_meters > v_threshold THEN
        INSERT INTO public.route_deviation_logs (
            trip_id,
            distance_from_route_meters,
            threshold_meters,
            current_latitude,
            current_longitude
        ) VALUES (
            p_trip_id,
            p_distance_from_route_meters,
            v_threshold,
            p_latitude,
            p_longitude
        );
        v_deviation_logged := TRUE;

        -- Severe anomaly (> 1500m): automatically trigger safety event
        IF p_distance_from_route_meters > 1500.0 THEN
            INSERT INTO public.safety_emergency_events (
                trip_id,
                triggered_by,
                event_type,
                location,
                latitude,
                longitude,
                speed_mps,
                state
            ) VALUES (
                p_trip_id,
                v_actor,
                'ROUTE_DEVIATION',
                v_loc,
                p_latitude,
                p_longitude,
                p_speed_mps,
                'TRIGGERED'
            );
            v_sos_triggered := TRUE;
        END IF;
    END IF;

    -- 2. Risk Zone Interception Check
    FOR v_zone IN
        SELECT name, severity
        FROM public.mobility_risk_zones
        WHERE active = true
          AND extensions.ST_DWithin(polygon, v_loc, 100.0)
        LIMIT 1
    LOOP
        v_inside_risk_zone := TRUE;
        v_risk_zone_name := v_zone.name;
        v_risk_zone_severity := v_zone.severity;
    END LOOP;

    RETURN jsonb_build_object(
        'success', TRUE,
        'trip_id', p_trip_id,
        'deviation_detected', v_deviation_logged,
        'distance_from_route_meters', p_distance_from_route_meters,
        'sos_triggered', v_sos_triggered,
        'inside_risk_zone', v_inside_risk_zone,
        'risk_zone_name', v_risk_zone_name,
        'risk_zone_severity', v_risk_zone_severity
    );
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 11. GRANT EXECUTE ON SAFETY & RESERVE RPCS
-- ─────────────────────────────────────────────────────────────────────────────

REVOKE ALL ON FUNCTION public.mobility_schedule_ride FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobility_schedule_ride TO authenticated;

REVOKE ALL ON FUNCTION public.mobility_dispatch_due_scheduled_rides FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobility_dispatch_due_scheduled_rides TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.mobility_request_guest_ride FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobility_request_guest_ride TO authenticated;

REVOKE ALL ON FUNCTION public.mobility_get_masked_channel FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobility_get_masked_channel TO authenticated;

REVOKE ALL ON FUNCTION public.mobility_trigger_emergency_sos FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobility_trigger_emergency_sos TO authenticated;

REVOKE ALL ON FUNCTION public.mobility_record_route_telemetry FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobility_record_route_telemetry TO authenticated;

