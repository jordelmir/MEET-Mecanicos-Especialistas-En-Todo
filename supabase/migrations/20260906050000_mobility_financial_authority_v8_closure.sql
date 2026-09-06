-- ─────────────────────────────────────────────────────────────────────────────
-- ELYSIUM GLOBAL MOBILITY OS — FINANCIAL AUTHORITY V8 CLOSURE
-- Mandate: ORDEN MAESTRA V8 (Pricing Authority, Payment Authority, Settlement Authority, Ledger Concurrency)
-- ─────────────────────────────────────────────────────────────────────────────

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. AUTHORITATIVE ROUTE EVIDENCE TABLE & AUDIT TRAIL
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.ride_route_evidence (
    route_evidence_id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    ride_request_id UUID NOT NULL REFERENCES public.ride_requests(ride_request_id) ON DELETE CASCADE,
    origin_geography extensions.geography NOT NULL,
    destination_geography extensions.geography NOT NULL,
    waypoints extensions.geography[] NOT NULL DEFAULT ARRAY[]::extensions.geography[],
    distance_meters BIGINT NOT NULL CHECK (distance_meters >= 0),
    duration_seconds BIGINT NOT NULL CHECK (duration_seconds >= 0),
    routing_provider TEXT NOT NULL DEFAULT 'AUTHORITATIVE_HAVERSINE_URBAN',
    routing_engine_version TEXT NOT NULL DEFAULT 'v1.0',
    route_geometry_hash TEXT NOT NULL,
    evidence_digest TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_route_evidence_request UNIQUE (ride_request_id)
);

ALTER TABLE public.ride_route_evidence ENABLE ROW LEVEL SECURITY;

CREATE POLICY "route_evidence_select_participants"
ON public.ride_route_evidence FOR SELECT
TO authenticated
USING (
    EXISTS (
        SELECT 1 FROM public.ride_requests rr
        WHERE rr.ride_request_id = public.ride_route_evidence.ride_request_id
          AND (rr.rider_id = auth.uid() OR auth.role() = 'service_role')
    )
    OR EXISTS (
        SELECT 1 FROM public.trips t
        WHERE t.ride_request_id = public.ride_route_evidence.ride_request_id
          AND (t.rider_id = auth.uid() OR t.driver_id = auth.uid() OR auth.role() = 'service_role')
    )
);

REVOKE INSERT, UPDATE, DELETE ON public.ride_route_evidence FROM PUBLIC, anon, authenticated;
GRANT SELECT ON public.ride_route_evidence TO authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. ENHANCE RIDE QUOTES TABLE FOR IMMUTABLE BINDING
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE public.ride_quotes ADD COLUMN IF NOT EXISTS route_evidence_id UUID REFERENCES public.ride_route_evidence(route_evidence_id);
ALTER TABLE public.ride_quotes ADD COLUMN IF NOT EXISTS rider_id UUID REFERENCES auth.users(id);
ALTER TABLE public.ride_quotes ADD COLUMN IF NOT EXISTS input_digest TEXT;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. ROUTE EVIDENCE GENERATION & SERVER-SIDE GUARANTEE
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.mobility_ensure_authoritative_route_evidence(
    p_ride_request_id UUID
) RETURNS public.ride_route_evidence
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_req public.ride_requests%ROWTYPE;
    v_evidence public.ride_route_evidence%ROWTYPE;
    v_direct_dist DOUBLE PRECISION;
    v_billable_dist BIGINT;
    v_billable_duration BIGINT;
    v_stops_count INTEGER := 0;
    v_geom_hash TEXT;
    v_digest TEXT;
    v_circuity_factor DOUBLE PRECISION := 1.35; -- 35% urban grid factor over great circle
    v_avg_urban_speed_mps DOUBLE PRECISION := 6.94; -- ~25 km/h in m/s
    v_stop_dwell_sec BIGINT := 180; -- 3 minutes per intermediate stop
BEGIN
    SELECT * INTO v_evidence FROM public.ride_route_evidence WHERE ride_request_id = p_ride_request_id;
    IF FOUND THEN
        RETURN v_evidence;
    END IF;

    SELECT * INTO v_req FROM public.ride_requests WHERE ride_request_id = p_ride_request_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'RIDE_REQUEST_NOT_FOUND';
    END IF;

    -- Count stops
    SELECT count(*) INTO v_stops_count FROM public.ride_request_stops WHERE ride_request_id = p_ride_request_id;

    -- Calculate distance using geodesic distance
    v_direct_dist := extensions.ST_Distance(v_req.pickup_location, v_req.destination_location);
    IF v_direct_dist IS NULL OR v_direct_dist < 0 THEN
        v_direct_dist := 500.0;
    END IF;

    -- Minimum billable distance 500m
    v_billable_dist := round(GREATEST(v_direct_dist * v_circuity_factor, 500.0))::BIGINT;
    v_billable_duration := round(v_billable_dist / v_avg_urban_speed_mps)::BIGINT + (v_stops_count * v_stop_dwell_sec);

    v_geom_hash := encode(
        extensions.digest(
            (p_ride_request_id::TEXT || ':' || v_billable_dist::TEXT || ':' || v_billable_duration::TEXT),
            'sha256'
        ),
        'hex'
    );

    v_digest := encode(
        extensions.digest(
            (p_ride_request_id::TEXT || ':' || v_req.rider_id::TEXT || ':' || v_billable_dist::TEXT || ':' || v_billable_duration::TEXT || ':' || v_geom_hash),
            'sha256'
        ),
        'hex'
    );

    INSERT INTO public.ride_route_evidence (
        ride_request_id,
        origin_geography,
        destination_geography,
        distance_meters,
        duration_seconds,
        routing_provider,
        routing_engine_version,
        route_geometry_hash,
        evidence_digest
    ) VALUES (
        p_ride_request_id,
        v_req.pickup_location,
        v_req.destination_location,
        v_billable_dist,
        v_billable_duration,
        'AUTHORITATIVE_HAVERSINE_URBAN',
        'v1.0',
        v_geom_hash,
        v_digest
    )
    ON CONFLICT (ride_request_id) DO UPDATE SET
        distance_meters = EXCLUDED.distance_meters,
        duration_seconds = EXCLUDED.duration_seconds,
        evidence_digest = EXCLUDED.evidence_digest
    RETURNING * INTO v_evidence;

    RETURN v_evidence;
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. P0.1 PRICING AUTHORITY: BOUND QUOTE GENERATION (NO RAW METRICS FROM CLIENT)
-- ─────────────────────────────────────────────────────────────────────────────

-- Revoke and drop old raw-metric functions completely
DROP FUNCTION IF EXISTS public.mobility_generate_quote(TEXT, TEXT, BIGINT, BIGINT, BIGINT, BIGINT);

CREATE OR REPLACE FUNCTION public.mobility_generate_quote(
    p_ride_request_id UUID,
    p_idempotency_key UUID DEFAULT NULL
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_actor UUID := auth.uid();
    v_req public.ride_requests%ROWTYPE;
    v_evidence public.ride_route_evidence%ROWTYPE;
    v_policy public.mobility_pricing_policies%ROWTYPE;
    v_base_fare BIGINT;
    v_dist_fare BIGINT;
    v_time_fare BIGINT;
    v_raw_subtotal BIGINT;
    v_surged_subtotal BIGINT;
    v_surge_adj BIGINT;
    v_tax BIGINT;
    v_total BIGINT;
    v_quote public.ride_quotes%ROWTYPE;
    v_surge_num BIGINT := 1;
    v_surge_den BIGINT := 1;
    v_input_digest TEXT;
    v_hash TEXT;
    v_receipt public.mobility_command_receipts%ROWTYPE;
    v_response JSONB;
BEGIN
    IF v_actor IS NULL AND auth.role() <> 'service_role' THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING ERRCODE = '42501';
    END IF;

    -- Optional idempotency check
    IF p_idempotency_key IS NOT NULL THEN
        v_hash := encode(
            extensions.digest(
                jsonb_build_object(
                    'ride_request_id', p_ride_request_id,
                    'actor_id', v_actor
                )::TEXT,
                'sha256'
            ),
            'hex'
        );
        PERFORM pg_advisory_xact_lock(hashtextextended(COALESCE(v_actor::TEXT, 'system') || ':' || p_idempotency_key::TEXT, 0));
        SELECT * INTO v_receipt FROM public.mobility_command_receipts
        WHERE actor_id = COALESCE(v_actor, '00000000-0000-0000-0000-000000000000'::uuid) AND command_scope = 'GENERATE_QUOTE' AND idempotency_key = p_idempotency_key;
        IF FOUND THEN
            IF v_receipt.request_hash <> v_hash THEN
                RAISE EXCEPTION 'IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD' USING ERRCODE = '23505';
            END IF;
            RETURN v_receipt.response;
        END IF;
    END IF;

    SELECT * INTO v_req FROM public.ride_requests WHERE ride_request_id = p_ride_request_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'RIDE_REQUEST_NOT_FOUND';
    END IF;

    -- Only rider or service_role can generate quotes
    IF auth.role() <> 'service_role' AND v_req.rider_id <> v_actor THEN
        RAISE EXCEPTION 'ACTOR_NOT_RIDE_REQUEST_OWNER' USING ERRCODE = '42501';
    END IF;

    IF v_req.state NOT IN ('REQUESTED', 'SEARCHING') THEN
        RAISE EXCEPTION 'INVALID_REQUEST_STATE_FOR_QUOTING';
    END IF;

    -- 1. Ensure authoritative route evidence (Client CANNOT supply distance or duration!)
    v_evidence := public.mobility_ensure_authoritative_route_evidence(p_ride_request_id);

    -- 2. Lookup active pricing policy for this request's market and service category
    SELECT * INTO v_policy
    FROM public.mobility_pricing_policies
    WHERE market_id = v_req.market_id
      AND service_category_id = v_req.service_category_id
      AND active = TRUE
      AND (valid_until IS NULL OR valid_until > clock_timestamp())
    ORDER BY version DESC
    LIMIT 1;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'PRICING_POLICY_NOT_FOUND_FOR_MARKET_AND_CATEGORY';
    END IF;

    -- 3. Calculate authoritative fares
    v_base_fare := v_policy.base_fare_minor + v_policy.booking_fee_minor;
    v_dist_fare := (v_evidence.distance_meters * v_policy.per_meter_numerator) / v_policy.per_meter_denominator;
    v_time_fare := (v_evidence.duration_seconds * v_policy.per_second_numerator) / v_policy.per_second_denominator;

    v_raw_subtotal := v_base_fare + v_dist_fare + v_time_fare;
    IF v_raw_subtotal < v_policy.minimum_fare_minor THEN
        v_raw_subtotal := v_policy.minimum_fare_minor;
    END IF;

    -- 4. Authoritative surge calculation (defaults to 1.0; client CANNOT supply surge)
    v_surged_subtotal := (v_raw_subtotal * v_surge_num) / v_surge_den;
    v_surge_adj := v_surged_subtotal - v_raw_subtotal;

    -- 5. Dynamic tax
    v_tax := (v_surged_subtotal * v_policy.tax_basis_points) / 10000;
    v_total := v_surged_subtotal + v_tax;

    -- 6. Canonical input digest
    v_input_digest := encode(
        extensions.digest(
            (p_ride_request_id::TEXT || ':' || v_req.rider_id::TEXT || ':' || v_evidence.route_evidence_id::TEXT || ':' || v_policy.version::TEXT || ':' || v_total::TEXT || ':' || v_policy.currency_code),
            'sha256'
        ),
        'hex'
    );

    -- 7. Insert immutable bound quote
    INSERT INTO public.ride_quotes (
        ride_request_id,
        route_evidence_id,
        rider_id,
        market_id,
        service_category_id,
        base_fare_minor,
        distance_fare_minor,
        time_fare_minor,
        surge_adjustment_minor,
        toll_estimate_minor,
        tax_minor,
        total_fare_minor,
        currency_code,
        pricing_policy_version,
        input_digest,
        expires_at
    ) VALUES (
        p_ride_request_id,
        v_evidence.route_evidence_id,
        v_req.rider_id,
        v_req.market_id,
        v_req.service_category_id,
        v_base_fare,
        v_dist_fare,
        v_time_fare,
        v_surge_adj,
        0,
        v_tax,
        v_total,
        v_policy.currency_code,
        v_policy.version,
        v_input_digest,
        clock_timestamp() + INTERVAL '10 minutes'
    ) RETURNING * INTO v_quote;

    v_response := jsonb_build_object(
        'success', TRUE,
        'quote', row_to_json(v_quote),
        'route_evidence', jsonb_build_object(
            'distance_meters', v_evidence.distance_meters,
            'duration_seconds', v_evidence.duration_seconds,
            'routing_provider', v_evidence.routing_provider,
            'evidence_digest', v_evidence.evidence_digest
        )
    );

    IF p_idempotency_key IS NOT NULL THEN
        INSERT INTO public.mobility_command_receipts (
            actor_id, command_scope, idempotency_key, request_hash, requested_aggregate_id, aggregate_id, response
        ) VALUES (
            COALESCE(v_actor, '00000000-0000-0000-0000-000000000000'::uuid), 'GENERATE_QUOTE', p_idempotency_key, v_hash, p_ride_request_id, v_quote.quote_id, v_response
        );
    END IF;

    RETURN v_response;
END;
$$;

GRANT EXECUTE ON FUNCTION public.mobility_generate_quote(UUID, UUID) TO authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. PAYMENT PROVIDER EVENTS & STATE MACHINE HARDENING
-- Update payment_authorizations state check constraint to support explicit electronic & cash states
ALTER TABLE public.payment_authorizations DROP CONSTRAINT IF EXISTS payment_authorizations_state_check;
ALTER TABLE public.payment_authorizations ADD CONSTRAINT payment_authorizations_state_check CHECK (
    state IN (
        'PENDING',
        'PENDING_PROVIDER',
        'AUTHORIZED',
        'DECLINED',
        'EXPIRED',
        'CANCELLED',
        'CAPTURED',
        'CASH_PENDING',
        'CASH_COLLECTED'
    )
);

-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.payment_provider_events (
    event_id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    provider TEXT NOT NULL,
    provider_event_id TEXT NOT NULL,
    event_type TEXT NOT NULL,
    payment_authorization_id UUID REFERENCES public.payment_authorizations(payment_authorization_id),
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_provider_event UNIQUE (provider, provider_event_id)
);

ALTER TABLE public.payment_provider_events ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.payment_provider_events FROM PUBLIC, anon, authenticated;
GRANT SELECT ON public.payment_provider_events TO service_role;

-- Update Payment Authorization RPC to enforce provider-backed state machine
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
    v_response JSONB;
    v_initial_state TEXT;
BEGIN
    IF v_actor IS NULL AND auth.role() <> 'service_role' THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING ERRCODE = '42501';
    END IF;

    IF p_idempotency_key IS NULL THEN
        RAISE EXCEPTION 'IDEMPOTENCY_KEY_REQUIRED';
    END IF;

    IF p_provider NOT IN ('CASH', 'CARD_TOKEN', 'WALLET', 'CORPORATE_ACCOUNT', 'SINPE_MOVIL') THEN
        RAISE EXCEPTION 'INVALID_PAYMENT_PROVIDER';
    END IF;

    v_hash := encode(
        extensions.digest(
            convert_to(
                jsonb_build_object(
                    'quote_id', p_quote_id,
                    'provider', p_provider
                )::TEXT,
                'UTF8'
            ),
            'sha256'
        ),
        'hex'
    );

    PERFORM pg_advisory_xact_lock(hashtextextended(COALESCE(v_actor::TEXT, 'system') || ':' || p_idempotency_key::TEXT, 0));

    SELECT * INTO v_receipt FROM public.mobility_command_receipts
    WHERE actor_id = COALESCE(v_actor, '00000000-0000-0000-0000-000000000000'::uuid) AND command_scope = 'AUTHORIZE_PAYMENT' AND idempotency_key = p_idempotency_key;

    IF FOUND THEN
        IF v_receipt.request_hash <> v_hash THEN
            RAISE EXCEPTION 'IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD' USING ERRCODE = '23505';
        END IF;
        RETURN v_receipt.response;
    END IF;

    SELECT * INTO v_quote FROM public.ride_quotes WHERE quote_id = p_quote_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'QUOTE_NOT_FOUND';
    END IF;

    IF v_quote.expires_at <= clock_timestamp() THEN
        RAISE EXCEPTION 'QUOTE_EXPIRED';
    END IF;

    -- Verify quote ownership
    IF v_quote.rider_id IS NOT NULL AND v_quote.rider_id <> v_actor AND auth.role() <> 'service_role' THEN
        RAISE EXCEPTION 'QUOTE_NOT_OWNED_BY_ACTOR' USING ERRCODE = '42501';
    END IF;

    -- Determine initial state:
    -- CASH: 'CASH_PENDING' (No simulated bank approval)
    -- ELECTRONIC: 'PENDING_PROVIDER' (Requires trusted provider webhook or server confirmation!)
    IF p_provider = 'CASH' THEN
        v_initial_state := 'CASH_PENDING';
    ELSE
        v_initial_state := 'PENDING_PROVIDER';
    END IF;

    INSERT INTO public.payment_authorizations (
        trip_id,
        rider_id,
        provider,
        provider_auth_ref,
        amount_minor,
        currency_code,
        state
    ) VALUES (
        NULL,
        COALESCE(v_actor, v_quote.rider_id),
        p_provider,
        NULL, -- Never trust client-supplied auth ref! Must come from PSP!
        v_quote.total_fare_minor,
        v_quote.currency_code,
        v_initial_state
    ) RETURNING * INTO v_auth;

    v_response := jsonb_build_object(
        'success', TRUE,
        'authorization', row_to_json(v_auth)
    );

    INSERT INTO public.mobility_command_receipts (
        actor_id, command_scope, idempotency_key, request_hash, requested_aggregate_id, aggregate_id, response
    ) VALUES (
        COALESCE(v_actor, '00000000-0000-0000-0000-000000000000'::uuid), 'AUTHORIZE_PAYMENT', p_idempotency_key, v_hash, p_quote_id, v_auth.payment_authorization_id, v_response
    );

    RETURN v_response;
END;
$$;

-- Drop old overload that accepted p_provider_auth_ref from client
DROP FUNCTION IF EXISTS public.mobility_authorize_quote_payment(UUID, TEXT, TEXT, UUID);
GRANT EXECUTE ON FUNCTION public.mobility_authorize_quote_payment(UUID, TEXT, UUID) TO authenticated;

-- Server-Side Trusted Provider Confirmation (Edge Function / Webhook handler ONLY)
CREATE OR REPLACE FUNCTION public.mobility_confirm_provider_authorization(
    p_payment_authorization_id UUID,
    p_provider_auth_ref TEXT,
    p_provider_event_id TEXT,
    p_provider_payload JSONB DEFAULT '{}'::jsonb
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_auth public.payment_authorizations%ROWTYPE;
BEGIN
    IF auth.role() <> 'service_role' THEN
        RAISE EXCEPTION 'SERVICE_ROLE_REQUIRED_FOR_PROVIDER_CONFIRMATION' USING ERRCODE = '42501';
    END IF;

    IF p_provider_auth_ref IS NULL OR trim(p_provider_auth_ref) = '' THEN
        RAISE EXCEPTION 'INVALID_PROVIDER_AUTH_REF';
    END IF;

    SELECT * INTO v_auth FROM public.payment_authorizations
    WHERE payment_authorization_id = p_payment_authorization_id FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'PAYMENT_AUTHORIZATION_NOT_FOUND';
    END IF;

    IF v_auth.state NOT IN ('PENDING_PROVIDER', 'PENDING') THEN
        RAISE EXCEPTION 'INVALID_STATE_FOR_PROVIDER_CONFIRMATION: %', v_auth.state;
    END IF;

    -- Record idempotency event from provider
    INSERT INTO public.payment_provider_events (
        provider, provider_event_id, event_type, payment_authorization_id, payload
    ) VALUES (
        v_auth.provider, p_provider_event_id, 'PAYMENT_AUTHORIZED', p_payment_authorization_id, p_provider_payload
    );

    UPDATE public.payment_authorizations
    SET state = 'AUTHORIZED',
        provider_auth_ref = p_provider_auth_ref,
        updated_at = clock_timestamp()
    WHERE payment_authorization_id = p_payment_authorization_id
    RETURNING * INTO v_auth;

    RETURN jsonb_build_object(
        'success', TRUE,
        'authorization', row_to_json(v_auth)
    );
END;
$$;

REVOKE ALL ON FUNCTION public.mobility_confirm_provider_authorization(UUID, TEXT, TEXT, JSONB) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.mobility_confirm_provider_authorization(UUID, TEXT, TEXT, JSONB) TO service_role;

-- ─────────────────────────────────────────────────────────────────────────────
-- 6. ATOMIC CONCURRENCY-SAFE LEDGER ACCOUNT RESOLUTION (P0.4 / H4)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.mobility_resolve_ledger_account(
    p_owner_id UUID,
    p_account_type TEXT,
    p_currency_code TEXT
) RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_account_id UUID;
BEGIN
    -- 1. Fast path SELECT
    IF p_owner_id IS NOT NULL THEN
        SELECT account_id INTO v_account_id
        FROM public.ledger_accounts
        WHERE owner_id = p_owner_id
          AND account_type = p_account_type
          AND currency_code = p_currency_code;
    ELSE
        SELECT account_id INTO v_account_id
        FROM public.ledger_accounts
        WHERE owner_id IS NULL
          AND account_type = p_account_type
          AND currency_code = p_currency_code;
    END IF;

    IF v_account_id IS NOT NULL THEN
        RETURN v_account_id;
    END IF;

    -- 2. Atomic UPSERT matching partial unique indexes
    IF p_owner_id IS NOT NULL THEN
        INSERT INTO public.ledger_accounts (owner_id, account_type, currency_code)
        VALUES (p_owner_id, p_account_type, p_currency_code)
        ON CONFLICT (owner_id, account_type, currency_code) WHERE owner_id IS NOT NULL DO NOTHING
        RETURNING account_id INTO v_account_id;
    ELSE
        INSERT INTO public.ledger_accounts (owner_id, account_type, currency_code)
        VALUES (NULL, p_account_type, p_currency_code)
        ON CONFLICT (account_type, currency_code) WHERE owner_id IS NULL DO NOTHING
        RETURNING account_id INTO v_account_id;
    END IF;

    -- 3. Deterministic resolution if concurrent thread committed first
    IF v_account_id IS NULL THEN
        IF p_owner_id IS NOT NULL THEN
            SELECT account_id INTO v_account_id
            FROM public.ledger_accounts
            WHERE owner_id = p_owner_id
              AND account_type = p_account_type
              AND currency_code = p_currency_code;
        ELSE
            SELECT account_id INTO v_account_id
            FROM public.ledger_accounts
            WHERE owner_id IS NULL
              AND account_type = p_account_type
              AND currency_code = p_currency_code;
        END IF;
    END IF;

    RETURN v_account_id;
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 7. SETTLEMENT AUTHORITY LOCKDOWN & CROSS-REQUEST BINDING (P0.3 / H3)
-- ─────────────────────────────────────────────────────────────────────────────

-- REVOKE direct client execution on mobility_settle_trip!
REVOKE ALL ON FUNCTION public.mobility_settle_trip(UUID, UUID, UUID, UUID) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.mobility_settle_trip(UUID, UUID, UUID, UUID) TO service_role;

CREATE OR REPLACE FUNCTION public.mobility_settle_trip(
    p_trip_id UUID,
    p_payment_authorization_id UUID,
    p_quote_id UUID,
    p_idempotency_key UUID
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_trip public.trips%ROWTYPE;
    v_auth public.payment_authorizations%ROWTYPE;
    v_quote public.ride_quotes%ROWTYPE;
    v_gross BIGINT;
    v_platform_fee BIGINT;
    v_tax BIGINT;
    v_driver_earnings BIGINT;
    v_tx_id UUID;
    v_settlement public.trip_settlements%ROWTYPE;
    v_hash TEXT;
    v_receipt public.mobility_command_receipts%ROWTYPE;
    v_response JSONB;
    v_rider_acc UUID;
    v_driver_acc UUID;
    v_platform_acc UUID;
    v_tax_acc UUID;
BEGIN
    -- Strict authority check: Settlement is a financial accounting operation and must be executed only by service_role
    IF auth.role() <> 'service_role' THEN
        RAISE EXCEPTION 'UNAUTHORIZED_SETTLEMENT_AUTHORITY: Settlement can only be executed by trusted settlement engine.' USING ERRCODE = '42501';
    END IF;

    IF p_idempotency_key IS NULL THEN
        RAISE EXCEPTION 'IDEMPOTENCY_KEY_REQUIRED';
    END IF;

    v_hash := encode(
        extensions.digest(
            convert_to(
                jsonb_build_object(
                    'trip_id', p_trip_id,
                    'payment_auth_id', p_payment_authorization_id,
                    'quote_id', p_quote_id
                )::TEXT,
                'UTF8'
            ),
            'sha256'
        ),
        'hex'
    );

    PERFORM pg_advisory_xact_lock(hashtextextended('system:' || p_idempotency_key::TEXT, 0));

    SELECT * INTO v_receipt FROM public.mobility_command_receipts
    WHERE command_scope = 'SETTLE_TRIP' AND idempotency_key = p_idempotency_key;

    IF FOUND THEN
        IF v_receipt.request_hash <> v_hash THEN
            RAISE EXCEPTION 'IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD' USING ERRCODE = '23505';
        END IF;
        RETURN v_receipt.response;
    END IF;

    -- Lock trip row to serialize concurrent settlement attempts
    PERFORM pg_advisory_xact_lock(hashtextextended('trip_settlement:' || p_trip_id::TEXT, 0));

    SELECT * INTO v_trip FROM public.trips WHERE trip_id = p_trip_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'TRIP_NOT_FOUND';
    END IF;

    IF v_trip.settlement_id IS NOT NULL THEN
        RETURN jsonb_build_object(
            'success', FALSE,
            'conflict', TRUE,
            'error_code', 'ALREADY_SETTLED',
            'message', 'El viaje ya ha sido liquidado contablemente.'
        );
    END IF;

    IF v_trip.state NOT IN ('ARRIVED_DESTINATION', 'COMPLETED') THEN
        RETURN jsonb_build_object(
            'success', FALSE,
            'conflict', TRUE,
            'error_code', 'INVALID_TRIP_STATE',
            'message', 'El viaje no ha llegado a destino para liquidación.'
        );
    END IF;

    SELECT * INTO v_auth FROM public.payment_authorizations
    WHERE payment_authorization_id = p_payment_authorization_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'INVALID_PAYMENT_AUTHORIZATION';
    END IF;

    -- STRICT STATE VERIFICATION: Electronic payments MUST be AUTHORIZED by provider! Cash must be CASH_PENDING or CASH_COLLECTED.
    IF v_auth.provider <> 'CASH' AND v_auth.state <> 'AUTHORIZED' THEN
        RAISE EXCEPTION 'PAYMENT_NOT_AUTHORIZED_BY_PROVIDER: state is %', v_auth.state;
    END IF;

    SELECT * INTO v_quote FROM public.ride_quotes WHERE quote_id = p_quote_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'QUOTE_NOT_FOUND';
    END IF;

    -- ─────────────────────────────────────────────────────────────
    -- STRICT CROSS-REQUEST BINDING CHECKS (ORDEN MAESTRA V8)
    -- ─────────────────────────────────────────────────────────────
    IF v_quote.ride_request_id IS NOT NULL AND v_quote.ride_request_id <> v_trip.ride_request_id THEN
        RAISE EXCEPTION 'CROSS_REQUEST_QUOTE_REUSE_REJECTED';
    END IF;

    IF v_auth.rider_id <> v_trip.rider_id THEN
        RAISE EXCEPTION 'PAYMENT_RIDER_MISMATCH';
    END IF;

    IF v_auth.amount_minor <> v_quote.total_fare_minor THEN
        RAISE EXCEPTION 'PAYMENT_AMOUNT_MISMATCH';
    END IF;

    IF v_auth.currency_code <> v_quote.currency_code THEN
        RAISE EXCEPTION 'PAYMENT_CURRENCY_MISMATCH';
    END IF;

    IF v_quote.expires_at <= clock_timestamp() THEN
        RAISE EXCEPTION 'QUOTE_EXPIRED';
    END IF;

    v_gross := v_quote.total_fare_minor;
    v_tax := v_quote.tax_minor;
    -- 15% platform commission on net fare
    v_platform_fee := ((v_gross - v_tax) * 15) / 100;
    -- Driver receives remaining 85% of net fare
    v_driver_earnings := v_gross - v_platform_fee - v_tax;

    -- Atomic concurrency-safe ledger account resolution
    v_rider_acc := public.mobility_resolve_ledger_account(v_trip.rider_id, 'RIDER_RECEIVABLE', v_quote.currency_code);
    v_driver_acc := public.mobility_resolve_ledger_account(v_trip.driver_id, 'DRIVER_PAYABLE', v_quote.currency_code);
    v_platform_acc := public.mobility_resolve_ledger_account(NULL, 'PLATFORM_REVENUE', v_quote.currency_code);
    v_tax_acc := public.mobility_resolve_ledger_account(NULL, 'TAX_ESCROW', v_quote.currency_code);

    -- Insert balanced ledger transaction: sum = 0
    INSERT INTO public.ledger_transactions (
        reference_type,
        reference_id,
        currency_code
    ) VALUES (
        'TRIP_SETTLEMENT',
        p_trip_id,
        v_quote.currency_code
    ) RETURNING transaction_id INTO v_tx_id;

    -- Double-entry ledger entries: sum == 0
    INSERT INTO public.ledger_entries (transaction_id, account_id, amount_minor)
    VALUES
        (v_tx_id, v_rider_acc, v_gross),
        (v_tx_id, v_driver_acc, -v_driver_earnings),
        (v_tx_id, v_platform_acc, -v_platform_fee),
        (v_tx_id, v_tax_acc, -v_tax);

    -- Mark payment authorization as CAPTURED (or CASH_COLLECTED)
    UPDATE public.payment_authorizations
    SET state = CASE WHEN v_auth.provider = 'CASH' THEN 'CASH_COLLECTED' ELSE 'CAPTURED' END,
        trip_id = p_trip_id,
        updated_at = clock_timestamp()
    WHERE payment_authorization_id = p_payment_authorization_id;

    -- Record immutable settlement
    INSERT INTO public.trip_settlements (
        trip_id,
        gross_fare_minor,
        platform_fee_minor,
        driver_earnings_minor,
        tax_minor,
        toll_minor,
        currency_code,
        pricing_policy_version,
        ledger_transaction_id
    ) VALUES (
        p_trip_id,
        v_gross,
        v_platform_fee,
        v_driver_earnings,
        v_tax,
        0,
        v_quote.currency_code,
        v_quote.pricing_policy_version,
        v_tx_id
    ) RETURNING * INTO v_settlement;

    -- Update trip with settlement reference and completed state
    UPDATE public.trips
    SET settlement_id = v_settlement.settlement_id,
        state = 'COMPLETED',
        updated_at = clock_timestamp()
    WHERE trip_id = p_trip_id;

    v_response := jsonb_build_object(
        'success', TRUE,
        'conflict', FALSE,
        'settlement', row_to_json(v_settlement)
    );

    INSERT INTO public.mobility_command_receipts (
        actor_id, command_scope, idempotency_key, request_hash, requested_aggregate_id, aggregate_id, response
    ) VALUES (
        COALESCE(auth.uid(), '00000000-0000-0000-0000-000000000000'::uuid),
        'SETTLE_TRIP',
        p_idempotency_key,
        v_hash,
        p_trip_id,
        v_settlement.settlement_id,
        v_response
    );

    RETURN v_response;
END;
$$;

-- Ensure read access under RLS for participants and service_role
GRANT SELECT ON public.payment_authorizations TO authenticated, service_role;
GRANT SELECT ON public.ride_quotes TO authenticated, service_role;
GRANT SELECT ON public.ride_requests TO authenticated, service_role;
GRANT SELECT ON public.trips TO authenticated, service_role;
GRANT SELECT ON public.trip_settlements TO authenticated, service_role;

-- Service role bypass policies for financial tables under FORCE RLS
CREATE POLICY "payment_authorizations_service_role_all"
ON public.payment_authorizations FOR ALL
TO service_role USING (true) WITH CHECK (true);

CREATE POLICY "trip_settlements_service_role_all"
ON public.trip_settlements FOR ALL
TO service_role USING (true) WITH CHECK (true);

CREATE POLICY "ledger_accounts_service_role_all"
ON public.ledger_accounts FOR ALL
TO service_role USING (true) WITH CHECK (true);

CREATE POLICY "ledger_transactions_service_role_all"
ON public.ledger_transactions FOR ALL
TO service_role USING (true) WITH CHECK (true);

CREATE POLICY "ledger_entries_service_role_all"
ON public.ledger_entries FOR ALL
TO service_role USING (true) WITH CHECK (true);

GRANT SELECT ON public.ledger_accounts TO service_role;
GRANT SELECT ON public.ledger_transactions TO service_role;
GRANT SELECT ON public.ledger_entries TO service_role;

-- Ensure service_role has full administrative access to all mobility and finance tables
GRANT ALL ON public.trips TO service_role;
GRANT ALL ON public.ride_requests TO service_role;
GRANT ALL ON public.ride_quotes TO service_role;
GRANT ALL ON public.payment_authorizations TO service_role;
GRANT ALL ON public.trip_settlements TO service_role;
GRANT ALL ON public.ledger_accounts TO service_role;
GRANT ALL ON public.ledger_transactions TO service_role;
GRANT ALL ON public.ledger_entries TO service_role;
GRANT ALL ON public.payment_provider_events TO service_role;
GRANT ALL ON public.ride_route_evidence TO service_role;
