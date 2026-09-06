-- ─────────────────────────────────────────────────────────────────────────────
-- ELYSIUM GLOBAL MOBILITY OS — COMMS, REPUTATION, SUPPORT & SURGE (WAVES 18–20)
-- Mandate: ORDEN MAESTRA V6 (Waves 18–20 Core Parity)
-- ─────────────────────────────────────────────────────────────────────────────

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. WAVE 18: IN-APP CHAT & TRIP COMMUNICATIONS
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.trip_messages (
    message_id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    trip_id UUID NOT NULL REFERENCES public.trips(trip_id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    message_type TEXT NOT NULL CHECK (message_type IN ('TEXT', 'LOCATION_SHARE', 'SYSTEM_ALERT')),
    body TEXT NOT NULL CHECK (length(trim(body)) > 0 AND length(body) <= 1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX IF NOT EXISTS trip_messages_trip_idx ON public.trip_messages(trip_id, created_at ASC);

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. WAVE 19: RATINGS, REPUTATION, LOST ITEMS & SUPPORT
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.trip_ratings (
    rating_id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    trip_id UUID NOT NULL REFERENCES public.trips(trip_id) ON DELETE CASCADE,
    reviewer_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    subject_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment TEXT CHECK (comment IS NULL OR length(comment) <= 500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT chk_no_self_rating CHECK (reviewer_id <> subject_id),
    UNIQUE (trip_id, reviewer_id, subject_id)
);

CREATE TABLE IF NOT EXISTS public.lost_item_cases (
    case_id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    trip_id UUID NOT NULL REFERENCES public.trips(trip_id) ON DELETE CASCADE,
    rider_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    driver_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    item_description TEXT NOT NULL CHECK (length(trim(item_description)) > 0),
    state TEXT NOT NULL CHECK (state IN ('OPEN', 'CONTACT_ATTEMPTED', 'RETURN_COORDINATED', 'RETURNED', 'UNRESOLVED', 'CLOSED')) DEFAULT 'OPEN',
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT chk_distinct_rider_driver CHECK (rider_id <> driver_id)
);

CREATE TABLE IF NOT EXISTS public.support_cases (
    support_case_id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    trip_id UUID REFERENCES public.trips(trip_id) ON DELETE SET NULL,
    category TEXT NOT NULL CHECK (length(trim(category)) > 0),
    priority TEXT NOT NULL CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')) DEFAULT 'MEDIUM',
    state TEXT NOT NULL CHECK (state IN ('OPEN', 'INVESTIGATING', 'RESOLVED', 'CLOSED')) DEFAULT 'OPEN',
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. WAVE 20: MARKET POLICY CONFIGURATION & DYNAMIC SURGE
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.market_policy_configurations (
    market_id TEXT PRIMARY KEY REFERENCES public.mobility_markets(market_id) ON DELETE CASCADE,
    driver_location_ttl_seconds INT NOT NULL DEFAULT 30 CHECK (driver_location_ttl_seconds > 0),
    max_search_radius_meters DOUBLE PRECISION NOT NULL DEFAULT 10000.0 CHECK (max_search_radius_meters > 0),
    surge_min_numerator BIGINT NOT NULL DEFAULT 1 CHECK (surge_min_numerator > 0),
    surge_min_denominator BIGINT NOT NULL DEFAULT 1 CHECK (surge_min_denominator > 0),
    surge_max_numerator BIGINT NOT NULL DEFAULT 3 CHECK (surge_max_numerator > 0),
    surge_max_denominator BIGINT NOT NULL DEFAULT 1 CHECK (surge_max_denominator > 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. REVOKE DIRECT MUTATIONS & CONFIGURE SELECT GRANTS
-- ─────────────────────────────────────────────────────────────────────────────

REVOKE INSERT, UPDATE, DELETE ON public.trip_messages FROM authenticated, anon;
REVOKE INSERT, UPDATE, DELETE ON public.trip_ratings FROM authenticated, anon;
REVOKE INSERT, UPDATE, DELETE ON public.lost_item_cases FROM authenticated, anon;
REVOKE INSERT, UPDATE, DELETE ON public.support_cases FROM authenticated, anon;
REVOKE INSERT, UPDATE, DELETE ON public.market_policy_configurations FROM authenticated, anon;

GRANT SELECT ON public.trip_messages TO authenticated;
GRANT SELECT ON public.trip_ratings TO authenticated;
GRANT SELECT ON public.lost_item_cases TO authenticated;
GRANT SELECT ON public.support_cases TO authenticated;
GRANT SELECT ON public.market_policy_configurations TO authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. RPC: SEND TRIP MESSAGE (Wave 18 In-App Chat)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.mobility_send_trip_message(
    p_trip_id UUID,
    p_body TEXT,
    p_message_type TEXT DEFAULT 'TEXT'
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_actor UUID := auth.uid();
    v_trip public.trips%ROWTYPE;
    v_msg public.trip_messages%ROWTYPE;
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING ERRCODE = '42501';
    END IF;

    IF p_body IS NULL OR length(trim(p_body)) = 0 THEN
        RAISE EXCEPTION 'EMPTY_MESSAGE_BODY';
    END IF;

    IF length(p_body) > 1000 THEN
        RAISE EXCEPTION 'MESSAGE_TOO_LONG: Exceeds 1000 characters';
    END IF;

    SELECT * INTO v_trip FROM public.trips WHERE trip_id = p_trip_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'TRIP_NOT_FOUND';
    END IF;

    IF v_actor <> v_trip.rider_id AND v_actor <> v_trip.driver_id THEN
        RAISE EXCEPTION 'FORBIDDEN: Not a participant of this trip' USING ERRCODE = '42501';
    END IF;

    INSERT INTO public.trip_messages (
        trip_id,
        sender_id,
        message_type,
        body
    ) VALUES (
        p_trip_id,
        v_actor,
        p_message_type,
        trim(p_body)
    ) RETURNING * INTO v_msg;

    RETURN jsonb_build_object(
        'success', TRUE,
        'message_id', v_msg.message_id,
        'trip_id', v_msg.trip_id,
        'sender_id', v_msg.sender_id,
        'message_type', v_msg.message_type,
        'created_at', v_msg.created_at
    );
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 6. RPC: RATE TRIP (Wave 19 Bilateral Reviews)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.mobility_rate_trip(
    p_trip_id UUID,
    p_rating SMALLINT,
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
    v_rating public.trip_ratings%ROWTYPE;
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING ERRCODE = '42501';
    END IF;

    IF p_rating < 1 OR p_rating > 5 THEN
        RAISE EXCEPTION 'INVALID_RATING: Must be between 1 and 5 stars';
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
        RAISE EXCEPTION 'FORBIDDEN: Not a participant of this trip' USING ERRCODE = '42501';
    END IF;

    -- Avoid double-rating by the same reviewer for this trip
    IF EXISTS (
        SELECT 1 FROM public.trip_ratings
        WHERE trip_id = p_trip_id AND reviewer_id = v_actor AND subject_id = v_subject
    ) THEN
        RAISE EXCEPTION 'ALREADY_RATED: You have already rated this trip';
    END IF;

    INSERT INTO public.trip_ratings (
        trip_id,
        reviewer_id,
        subject_id,
        rating,
        comment
    ) VALUES (
        p_trip_id,
        v_actor,
        v_subject,
        p_rating,
        trim(p_comment)
    ) RETURNING * INTO v_rating;

    RETURN jsonb_build_object(
        'success', TRUE,
        'rating_id', v_rating.rating_id,
        'trip_id', v_rating.trip_id,
        'reviewer_id', v_rating.reviewer_id,
        'subject_id', v_rating.subject_id,
        'rating', v_rating.rating,
        'created_at', v_rating.created_at
    );
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 7. RPC: REPORT LOST ITEM (Wave 19 Lost Items)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.mobility_report_lost_item(
    p_trip_id UUID,
    p_item_description TEXT
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_actor UUID := auth.uid();
    v_trip public.trips%ROWTYPE;
    v_case public.lost_item_cases%ROWTYPE;
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING ERRCODE = '42501';
    END IF;

    IF p_item_description IS NULL OR length(trim(p_item_description)) = 0 THEN
        RAISE EXCEPTION 'EMPTY_ITEM_DESCRIPTION';
    END IF;

    SELECT * INTO v_trip FROM public.trips WHERE trip_id = p_trip_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'TRIP_NOT_FOUND';
    END IF;

    IF v_actor <> v_trip.rider_id THEN
        RAISE EXCEPTION 'FORBIDDEN: Only the rider can report lost items' USING ERRCODE = '42501';
    END IF;

    INSERT INTO public.lost_item_cases (
        trip_id,
        rider_id,
        driver_id,
        item_description,
        state
    ) VALUES (
        p_trip_id,
        v_trip.rider_id,
        v_trip.driver_id,
        trim(p_item_description),
        'OPEN'
    ) RETURNING * INTO v_case;

    RETURN jsonb_build_object(
        'success', TRUE,
        'case_id', v_case.case_id,
        'trip_id', v_case.trip_id,
        'state', v_case.state,
        'item_description', v_case.item_description,
        'created_at', v_case.created_at
    );
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 8. RPC: CREATE SUPPORT CASE (Wave 19 Support Case Decoupled from Trip)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.mobility_create_support_case(
    p_category TEXT,
    p_priority TEXT DEFAULT 'MEDIUM',
    p_trip_id UUID DEFAULT NULL
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_actor UUID := auth.uid();
    v_case public.support_cases%ROWTYPE;
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING ERRCODE = '42501';
    END IF;

    IF p_category IS NULL OR length(trim(p_category)) = 0 THEN
        RAISE EXCEPTION 'EMPTY_CATEGORY';
    END IF;

    INSERT INTO public.support_cases (
        user_id,
        trip_id,
        category,
        priority,
        state
    ) VALUES (
        v_actor,
        p_trip_id,
        trim(p_category),
        p_priority,
        'OPEN'
    ) RETURNING * INTO v_case;

    RETURN jsonb_build_object(
        'success', TRUE,
        'support_case_id', v_case.support_case_id,
        'user_id', v_case.user_id,
        'category', v_case.category,
        'priority', v_case.priority,
        'state', v_case.state,
        'created_at', v_case.created_at
    );
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 9. RPC: CALCULATE DYNAMIC SURGE (Wave 20 Spatial Supply/Demand Rational Multiplier)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.mobility_calculate_dynamic_surge(
    p_market_id TEXT,
    p_lat DOUBLE PRECISION,
    p_lng DOUBLE PRECISION
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_origin extensions.geography;
    v_policy public.market_policy_configurations%ROWTYPE;
    v_active_demand BIGINT := 0;
    v_available_supply BIGINT := 0;
    v_num BIGINT := 1;
    v_den BIGINT := 1;
BEGIN
    IF p_lat NOT BETWEEN -90.0 AND 90.0 OR p_lng NOT BETWEEN -180.0 AND 180.0 THEN
        RAISE EXCEPTION 'INVALID_COORDINATES';
    END IF;

    v_origin := extensions.ST_SetSRID(extensions.ST_MakePoint(p_lng, p_lat), 4326)::extensions.geography;

    -- Lookup market policy
    SELECT * INTO v_policy FROM public.market_policy_configurations WHERE market_id = p_market_id;
    IF NOT FOUND THEN
        v_policy.driver_location_ttl_seconds := 30;
        v_policy.max_search_radius_meters := 5000.0;
        v_policy.surge_min_numerator := 1;
        v_policy.surge_min_denominator := 1;
        v_policy.surge_max_numerator := 3;
        v_policy.surge_max_denominator := 1;
    END IF;

    -- 1. Active Demand: ride requests in SEARCHING state within radius
    SELECT count(*) INTO v_active_demand
    FROM public.ride_requests r
    WHERE r.market_id = p_market_id
      AND r.state = 'SEARCHING'
      AND extensions.ST_DWithin(r.pickup_location, v_origin, v_policy.max_search_radius_meters);

    -- 2. Available Supply: active verified drivers within radius with fresh location
    SELECT count(*) INTO v_available_supply
    FROM public.driver_presence_snapshot p
    WHERE p.market_id = p_market_id
      AND p.current_state = 'AVAILABLE'
      AND p.updated_at >= clock_timestamp() - (v_policy.driver_location_ttl_seconds * INTERVAL '1 second')
      AND extensions.ST_DWithin(p.location, v_origin, v_policy.max_search_radius_meters);

    -- 3. Surge Calculation (Rational arithmetic)
    IF v_active_demand = 0 OR v_available_supply >= (v_active_demand * 2) THEN
        v_num := v_policy.surge_min_numerator;
        v_den := v_policy.surge_min_denominator;
    ELSIF v_available_supply = 0 THEN
        v_num := v_policy.surge_max_numerator;
        v_den := v_policy.surge_max_denominator;
    ELSE
        -- e.g. Demand=10, Supply=5 -> 2.0x (2/1)
        IF v_active_demand > (v_available_supply * v_policy.surge_max_numerator) THEN
            v_num := v_policy.surge_max_numerator;
            v_den := v_policy.surge_max_denominator;
        ELSE
            v_num := v_active_demand;
            v_den := v_available_supply;
        END IF;
    END IF;

    RETURN jsonb_build_object(
        'success', TRUE,
        'market_id', p_market_id,
        'active_demand', v_active_demand,
        'available_supply', v_available_supply,
        'surge_numerator', v_num,
        'surge_denominator', v_den
    );
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 10. GRANT EXECUTE ON ALL NEW RPCS
-- ─────────────────────────────────────────────────────────────────────────────

REVOKE ALL ON FUNCTION public.mobility_send_trip_message FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobility_send_trip_message TO authenticated;

REVOKE ALL ON FUNCTION public.mobility_rate_trip FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobility_rate_trip TO authenticated;

REVOKE ALL ON FUNCTION public.mobility_report_lost_item FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobility_report_lost_item TO authenticated;

REVOKE ALL ON FUNCTION public.mobility_create_support_case FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobility_create_support_case TO authenticated;

REVOKE ALL ON FUNCTION public.mobility_calculate_dynamic_surge FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobility_calculate_dynamic_surge TO authenticated;
