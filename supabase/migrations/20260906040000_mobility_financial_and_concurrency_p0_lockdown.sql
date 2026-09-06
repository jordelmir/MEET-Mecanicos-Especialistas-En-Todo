-- ─────────────────────────────────────────────────────────────────────────────
-- ELYSIUM GLOBAL MOBILITY OS — FINANCIAL AUTHORITY, SECURITY & CONCURRENCY LOCKDOWN
-- Mandate: ORDEN MAESTRA V7 (Sections 1–16, 31, 55)
-- ─────────────────────────────────────────────────────────────────────────────

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. P0.1: REVOKE DIRECT ACCOUNTING AND LEDGER AUTHORITY FROM CLIENTS
-- ─────────────────────────────────────────────────────────────────────────────

REVOKE ALL ON FUNCTION public.mobility_post_ledger_transaction(
    TEXT,
    UUID,
    TEXT,
    JSONB
) FROM PUBLIC, anon, authenticated;

REVOKE ALL ON public.ledger_accounts FROM anon, authenticated;
REVOKE ALL ON public.ledger_transactions FROM anon, authenticated;
REVOKE ALL ON public.ledger_entries FROM anon, authenticated;
REVOKE ALL ON public.trip_settlements FROM anon, authenticated;
REVOKE ALL ON public.payment_authorizations FROM anon, authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. P0.2: ENABLE & FORCE ROW LEVEL SECURITY (FAIL-CLOSED)
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE public.payment_authorizations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ledger_accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ledger_transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ledger_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.trip_settlements ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.payment_authorizations FORCE ROW LEVEL SECURITY;
ALTER TABLE public.ledger_accounts FORCE ROW LEVEL SECURITY;
ALTER TABLE public.ledger_transactions FORCE ROW LEVEL SECURITY;
ALTER TABLE public.ledger_entries FORCE ROW LEVEL SECURITY;
ALTER TABLE public.trip_settlements FORCE ROW LEVEL SECURITY;

-- Rider may only read their own payment authorizations
DROP POLICY IF EXISTS payment_authorizations_owner_read ON public.payment_authorizations;
CREATE POLICY payment_authorizations_owner_read ON public.payment_authorizations
FOR SELECT TO authenticated USING (
    rider_id = (SELECT auth.uid())
);
GRANT SELECT ON public.payment_authorizations TO authenticated;

-- Settlements: strictly readable only by trip rider or driver
DROP POLICY IF EXISTS trip_settlements_participant_read ON public.trip_settlements;
CREATE POLICY trip_settlements_participant_read ON public.trip_settlements
FOR SELECT TO authenticated USING (
    EXISTS (
        SELECT 1 FROM public.trips t
        WHERE t.trip_id = trip_settlements.trip_id
          AND (t.rider_id = (SELECT auth.uid()) OR t.driver_id = (SELECT auth.uid()))
    )
);
GRANT SELECT ON public.trip_settlements TO authenticated;

-- Revoke legacy arbitrary client authorization RPC
REVOKE ALL ON FUNCTION public.mobility_authorize_payment(
    UUID,
    TEXT,
    BIGINT,
    TEXT,
    TEXT,
    UUID
) FROM PUBLIC, anon, authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. P0.5: LEDGER ACCOUNT PARTIAL UNIQUE INDEXES (NULL SEMANTICS SAFETY)
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE public.ledger_accounts DROP CONSTRAINT IF EXISTS ledger_accounts_owner_id_account_type_currency_code_key;

CREATE UNIQUE INDEX IF NOT EXISTS ux_ledger_global_account
ON public.ledger_accounts (
    account_type,
    currency_code
)
WHERE owner_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_ledger_user_account
ON public.ledger_accounts (
    owner_id,
    account_type,
    currency_code
)
WHERE owner_id IS NOT NULL;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. P0.3: DYNAMIC PRICING POLICIES TABLE
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.mobility_pricing_policies (
    pricing_policy_id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    market_id TEXT NOT NULL REFERENCES public.mobility_markets(market_id),
    service_category_id TEXT NOT NULL REFERENCES public.mobility_service_categories(service_category_id),
    version BIGINT NOT NULL,
    currency_code TEXT NOT NULL CHECK (currency_code ~ '^[A-Z]{3}$'),
    base_fare_minor BIGINT NOT NULL CHECK (base_fare_minor >= 0),
    per_meter_numerator BIGINT NOT NULL,
    per_meter_denominator BIGINT NOT NULL CHECK (per_meter_denominator > 0),
    per_second_numerator BIGINT NOT NULL,
    per_second_denominator BIGINT NOT NULL CHECK (per_second_denominator > 0),
    minimum_fare_minor BIGINT NOT NULL,
    booking_fee_minor BIGINT NOT NULL DEFAULT 0,
    cancellation_fee_minor BIGINT NOT NULL DEFAULT 0,
    tax_basis_points INTEGER NOT NULL DEFAULT 0 CHECK (tax_basis_points BETWEEN 0 AND 10000),
    surge_max_basis_points INTEGER NOT NULL DEFAULT 10000,
    valid_from TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    valid_until TIMESTAMPTZ,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (market_id, service_category_id, version)
);

-- Ensure base markets and service categories exist
INSERT INTO public.mobility_markets (
    market_id, country_code, currency_code, timezone, dispatch_modes, max_intermediate_stops, auto_dispatch_enabled, marketplace_offers_enabled, active
) VALUES
('CR_GAM', 'CR', 'CRC', 'America/Costa_Rica', ARRAY['AUTO_DISPATCH', 'MARKETPLACE_OFFERS'], 3, true, true, true),
('CR_RURAL', 'CR', 'CRC', 'America/Costa_Rica', ARRAY['AUTO_DISPATCH', 'MARKETPLACE_OFFERS'], 3, true, true, true)
ON CONFLICT (market_id) DO UPDATE SET
    active = TRUE,
    currency_code = EXCLUDED.currency_code;

INSERT INTO public.mobility_service_categories (
    service_category_id, market_id, code, name, max_passengers, requires_ev, requires_accessible, active
) VALUES
('STD_RIDE', 'CR_GAM', 'STANDARD', 'Standard Ride', 4, false, false, true),
('COMFORT', 'CR_GAM', 'COMFORT', 'Comfort Ride', 4, false, false, true),
('XL', 'CR_GAM', 'XL', 'Extra Large 6-Passenger', 6, false, false, true),
('TOW_TRUCK', 'CR_GAM', 'TOW', 'Tow Truck Service', 2, false, false, true),
('ROADSIDE_ASSIST', 'CR_GAM', 'ROADSIDE', 'Roadside Assistance', 2, false, false, true),
('STD_RIDE_RURAL', 'CR_RURAL', 'STANDARD', 'Standard Rural Ride', 4, false, false, true)
ON CONFLICT (service_category_id) DO UPDATE SET
    active = TRUE,
    name = EXCLUDED.name;

-- Seed production-grade pricing policies for Costa Rica GAM
INSERT INTO public.mobility_pricing_policies (
    market_id, service_category_id, version, currency_code,
    base_fare_minor, per_meter_numerator, per_meter_denominator,
    per_second_numerator, per_second_denominator, minimum_fare_minor,
    booking_fee_minor, cancellation_fee_minor, tax_basis_points, surge_max_basis_points
) VALUES
('CR_GAM', 'STD_RIDE', 1, 'CRC', 100000, 650, 1000, 80, 60, 150000, 20000, 100000, 1300, 30000),
('CR_GAM', 'COMFORT', 1, 'CRC', 150000, 850, 1000, 100, 60, 200000, 25000, 150000, 1300, 30000),
('CR_GAM', 'XL', 1, 'CRC', 200000, 1100, 1000, 140, 60, 280000, 30000, 200000, 1300, 30000),
('CR_GAM', 'TOW_TRUCK', 1, 'CRC', 3500000, 2500, 1000, 200, 60, 4500000, 50000, 2000000, 1300, 20000),
('CR_GAM', 'ROADSIDE_ASSIST', 1, 'CRC', 2000000, 1200, 1000, 100, 60, 2500000, 30000, 1000000, 1300, 20000),
('CR_RURAL', 'STD_RIDE_RURAL', 1, 'CRC', 120000, 750, 1000, 90, 60, 180000, 20000, 120000, 1300, 25000)
ON CONFLICT (market_id, service_category_id, version) DO UPDATE SET
    base_fare_minor = EXCLUDED.base_fare_minor,
    per_meter_numerator = EXCLUDED.per_meter_numerator,
    per_meter_denominator = EXCLUDED.per_meter_denominator,
    per_second_numerator = EXCLUDED.per_second_numerator,
    per_second_denominator = EXCLUDED.per_second_denominator,
    minimum_fare_minor = EXCLUDED.minimum_fare_minor,
    tax_basis_points = EXCLUDED.tax_basis_points,
    active = TRUE;

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. RPC: GENERATE UPFRONT QUOTE FROM AUTHORITATIVE PRICING POLICY
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.mobility_generate_quote(
    p_market_id TEXT,
    p_service_category_id TEXT,
    p_distance_meters BIGINT,
    p_duration_seconds BIGINT,
    p_surge_numerator BIGINT DEFAULT 1,
    p_surge_denominator BIGINT DEFAULT 1
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
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
BEGIN
    IF p_distance_meters < 0 OR p_duration_seconds < 0 THEN
        RAISE EXCEPTION 'INVALID_DISTANCE_OR_DURATION';
    END IF;

    IF p_surge_denominator <= 0 OR p_surge_numerator < p_surge_denominator THEN
        RAISE EXCEPTION 'INVALID_SURGE_RATIO';
    END IF;

    -- Lookup active pricing policy
    SELECT * INTO v_policy
    FROM public.mobility_pricing_policies
    WHERE market_id = p_market_id
      AND service_category_id = p_service_category_id
      AND active = TRUE
      AND (valid_until IS NULL OR valid_until > clock_timestamp())
    ORDER BY version DESC
    LIMIT 1;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'PRICING_POLICY_NOT_FOUND_FOR_MARKET_AND_CATEGORY';
    END IF;

    v_base_fare := v_policy.base_fare_minor + v_policy.booking_fee_minor;
    v_dist_fare := (p_distance_meters * v_policy.per_meter_numerator) / v_policy.per_meter_denominator;
    v_time_fare := (p_duration_seconds * v_policy.per_second_numerator) / v_policy.per_second_denominator;

    v_raw_subtotal := v_base_fare + v_dist_fare + v_time_fare;
    IF v_raw_subtotal < v_policy.minimum_fare_minor THEN
        v_raw_subtotal := v_policy.minimum_fare_minor;
    END IF;

    v_surged_subtotal := (v_raw_subtotal * p_surge_numerator) / p_surge_denominator;
    v_surge_adj := v_surged_subtotal - v_raw_subtotal;

    -- Dynamic tax calculated from policy basis points (e.g. 1300 bp = 13%)
    v_tax := (v_surged_subtotal * v_policy.tax_basis_points) / 10000;
    v_total := v_surged_subtotal + v_tax;

    INSERT INTO public.ride_quotes (
        ride_request_id,
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
        expires_at
    ) VALUES (
        NULL,
        p_market_id,
        p_service_category_id,
        v_base_fare,
        v_dist_fare,
        v_time_fare,
        v_surge_adj,
        0,
        v_tax,
        v_total,
        v_policy.currency_code,
        v_policy.version,
        clock_timestamp() + INTERVAL '10 minutes'
    ) RETURNING * INTO v_quote;

    RETURN jsonb_build_object(
        'success', TRUE,
        'quote', row_to_json(v_quote)
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.mobility_generate_quote(TEXT, TEXT, BIGINT, BIGINT, BIGINT, BIGINT) TO authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 6. P0.3: SERVER-SIDE AUTHORIZE PAYMENT BOUND TO CANONICAL QUOTE
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.mobility_authorize_quote_payment(
    p_quote_id UUID,
    p_provider TEXT,
    p_provider_auth_ref TEXT DEFAULT NULL,
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
BEGIN
    IF v_actor IS NULL THEN
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
                    'provider', p_provider,
                    'provider_auth_ref', p_provider_auth_ref
                )::TEXT,
                'UTF8'
            ),
            'sha256'
        ),
        'hex'
    );

    PERFORM pg_advisory_xact_lock(hashtextextended(v_actor::TEXT || ':' || p_idempotency_key::TEXT, 0));

    SELECT * INTO v_receipt FROM public.mobility_command_receipts
    WHERE actor_id = v_actor AND command_scope = 'AUTHORIZE_PAYMENT' AND idempotency_key = p_idempotency_key;

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

    -- Amount and currency are derived strictly from the verified server quote
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
        v_actor,
        p_provider,
        COALESCE(p_provider_auth_ref, 'auth_ref_' || extensions.gen_random_uuid()::text),
        v_quote.total_fare_minor,
        v_quote.currency_code,
        'AUTHORIZED'
    ) RETURNING * INTO v_auth;

    v_response := jsonb_build_object(
        'success', TRUE,
        'authorization', row_to_json(v_auth)
    );

    INSERT INTO public.mobility_command_receipts (
        actor_id, command_scope, idempotency_key, request_hash, requested_aggregate_id, aggregate_id, response
    ) VALUES (
        v_actor, 'AUTHORIZE_PAYMENT', p_idempotency_key, v_hash, NULL, v_auth.payment_authorization_id, v_response
    );

    RETURN v_response;
END;
$$;

GRANT EXECUTE ON FUNCTION public.mobility_authorize_quote_payment(UUID, TEXT, TEXT, UUID) TO authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 7. P0.4: SETTLEMENT CROSS-VALIDATION PROCEDURE
-- ─────────────────────────────────────────────────────────────────────────────

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
    v_actor UUID := auth.uid();
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
                    'payment_auth_id', p_payment_authorization_id,
                    'quote_id', p_quote_id
                )::TEXT,
                'UTF8'
            ),
            'sha256'
        ),
        'hex'
    );

    PERFORM pg_advisory_xact_lock(hashtextextended(v_actor::TEXT || ':' || p_idempotency_key::TEXT, 0));

    SELECT * INTO v_receipt FROM public.mobility_command_receipts
    WHERE actor_id = v_actor AND command_scope = 'SETTLE_TRIP' AND idempotency_key = p_idempotency_key;

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
    IF NOT FOUND OR v_auth.state <> 'AUTHORIZED' THEN
        RAISE EXCEPTION 'INVALID_PAYMENT_AUTHORIZATION';
    END IF;

    SELECT * INTO v_quote FROM public.ride_quotes WHERE quote_id = p_quote_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'QUOTE_NOT_FOUND';
    END IF;

    -- ─────────────────────────────────────────────────────────────
    -- P0.4 STRICT SETTLEMENT CROSS-VALIDATIONS
    -- ─────────────────────────────────────────────────────────────
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

    IF NOT EXISTS (
        SELECT 1
        FROM public.ride_requests rr
        WHERE rr.ride_request_id = v_trip.ride_request_id
          AND rr.market_id = v_quote.market_id
          AND rr.service_category_id = v_quote.service_category_id
    ) THEN
        RAISE EXCEPTION 'QUOTE_TRIP_MISMATCH';
    END IF;

    v_gross := v_quote.total_fare_minor;
    v_tax := v_quote.tax_minor;
    -- 15% platform commission on net fare
    v_platform_fee := ((v_gross - v_tax) * 15) / 100;
    -- Driver receives remaining 85% of net fare
    v_driver_earnings := v_gross - v_platform_fee - v_tax;

    -- Ensure accounts exist with strict partial unique index compliance
    SELECT account_id INTO v_rider_acc FROM public.ledger_accounts
    WHERE owner_id = v_trip.rider_id AND account_type = 'RIDER_RECEIVABLE' AND currency_code = v_quote.currency_code;
    IF NOT FOUND THEN
        INSERT INTO public.ledger_accounts (owner_id, account_type, currency_code)
        VALUES (v_trip.rider_id, 'RIDER_RECEIVABLE', v_quote.currency_code)
        RETURNING account_id INTO v_rider_acc;
    END IF;

    SELECT account_id INTO v_driver_acc FROM public.ledger_accounts
    WHERE owner_id = v_trip.driver_id AND account_type = 'DRIVER_PAYABLE' AND currency_code = v_quote.currency_code;
    IF NOT FOUND THEN
        INSERT INTO public.ledger_accounts (owner_id, account_type, currency_code)
        VALUES (v_trip.driver_id, 'DRIVER_PAYABLE', v_quote.currency_code)
        RETURNING account_id INTO v_driver_acc;
    END IF;

    SELECT account_id INTO v_platform_acc FROM public.ledger_accounts
    WHERE owner_id IS NULL AND account_type = 'PLATFORM_REVENUE' AND currency_code = v_quote.currency_code;
    IF NOT FOUND THEN
        INSERT INTO public.ledger_accounts (owner_id, account_type, currency_code)
        VALUES (NULL, 'PLATFORM_REVENUE', v_quote.currency_code)
        RETURNING account_id INTO v_platform_acc;
    END IF;

    SELECT account_id INTO v_tax_acc FROM public.ledger_accounts
    WHERE owner_id IS NULL AND account_type = 'TAX_ESCROW' AND currency_code = v_quote.currency_code;
    IF NOT FOUND THEN
        INSERT INTO public.ledger_accounts (owner_id, account_type, currency_code)
        VALUES (NULL, 'TAX_ESCROW', v_quote.currency_code)
        RETURNING account_id INTO v_tax_acc;
    END IF;

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

    INSERT INTO public.ledger_entries (transaction_id, account_id, amount_minor)
    VALUES
        (v_tx_id, v_rider_acc, v_gross),
        (v_tx_id, v_driver_acc, -v_driver_earnings),
        (v_tx_id, v_platform_acc, -v_platform_fee),
        (v_tx_id, v_tax_acc, -v_tax);

    -- Insert immutable trip settlement record
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

    -- Transition authorization to CAPTURED
    UPDATE public.payment_authorizations
    SET state = 'CAPTURED',
        trip_id = p_trip_id,
        updated_at = clock_timestamp()
    WHERE payment_authorization_id = p_payment_authorization_id;

    -- Attach settlement and complete trip
    UPDATE public.trips
    SET settlement_id = v_settlement.settlement_id,
        state = 'COMPLETED',
        completed_at = clock_timestamp(),
        version = version + 1
    WHERE trip_id = p_trip_id;

    v_response := jsonb_build_object(
        'success', TRUE,
        'settlement_id', v_settlement.settlement_id,
        'trip_id', p_trip_id,
        'gross_fare_minor', v_gross,
        'platform_fee_minor', v_platform_fee,
        'driver_earnings_minor', v_driver_earnings,
        'tax_minor', v_tax,
        'currency_code', v_settlement.currency_code,
        'ledger_transaction_id', v_tx_id
    );

    INSERT INTO public.mobility_command_receipts (
        actor_id, command_scope, idempotency_key, request_hash, requested_aggregate_id, aggregate_id, response
    ) VALUES (
        v_actor, 'SETTLE_TRIP', p_idempotency_key, v_hash, p_trip_id, v_settlement.settlement_id, v_response
    );

    RETURN v_response;
END;
$$;

GRANT EXECUTE ON FUNCTION public.mobility_settle_trip(UUID, UUID, UUID, UUID) TO authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 8. P0.11: ATOMIC DRIVER PRESENCE CAS (NO READ-BEFORE-WRITE RACE)
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
    v_eligible BOOLEAN;
    v_accepted_sequence BIGINT;
BEGIN
    IF v_driver IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING ERRCODE = '42501';
    END IF;

    IF p_lat NOT BETWEEN -90 AND 90 OR p_lng NOT BETWEEN -180 AND 180 THEN
        RAISE EXCEPTION 'INVALID_COORDINATES';
    END IF;

    IF p_heading IS NOT NULL AND (p_heading < 0 OR p_heading >= 360) THEN
        RAISE EXCEPTION 'INVALID_HEADING';
    END IF;

    IF p_speed_mps IS NOT NULL AND (p_speed_mps < 0 OR p_speed_mps > 83.33) THEN
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

    -- Atomic CAS within ON CONFLICT (strictly serialized, eliminates read-before-write race condition)
    INSERT INTO public.driver_presence_snapshot (
        driver_id,
        active_vehicle_id,
        market_id,
        current_state,
        location,
        heading,
        speed_mps,
        sequence_id,
        updated_at
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
    ) ON CONFLICT (driver_id)
    DO UPDATE SET
        active_vehicle_id = EXCLUDED.active_vehicle_id,
        market_id = EXCLUDED.market_id,
        current_state = EXCLUDED.current_state,
        location = EXCLUDED.location,
        heading = EXCLUDED.heading,
        speed_mps = EXCLUDED.speed_mps,
        sequence_id = EXCLUDED.sequence_id,
        updated_at = EXCLUDED.updated_at
    WHERE EXCLUDED.sequence_id > public.driver_presence_snapshot.sequence_id
    RETURNING sequence_id INTO v_accepted_sequence;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'STALE_SEQUENCE_ID';
    END IF;

    RETURN jsonb_build_object(
        'success', TRUE,
        'driver_id', v_driver,
        'state', p_state,
        'sequence_id', v_accepted_sequence
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.mobility_update_driver_presence(TEXT, UUID, TEXT, DOUBLE PRECISION, DOUBLE PRECISION, REAL, REAL, BIGINT) TO authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 9. P0.10: COMPREHENSIVE IDEMPOTENCY REQUEST DIGEST
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.mobility_request_digest(
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
    p_intermediate_stops JSONB,
    p_requested_price_minor BIGINT,
    p_scheduled_for TIMESTAMPTZ
) RETURNS TEXT
LANGUAGE sql
IMMUTABLE
SET search_path = ''
AS $$
    SELECT encode(
        extensions.digest(
            convert_to(
                jsonb_build_object(
                    'market_id', p_market_id,
                    'service_category_id', p_service_category_id,
                    'dispatch_mode', p_dispatch_mode,
                    'pickup', jsonb_build_object(
                        'latitude', p_pickup_lat,
                        'longitude', p_pickup_lng,
                        'accuracy', p_pickup_accuracy,
                        'address', p_pickup_address
                    ),
                    'destination', jsonb_build_object(
                        'latitude', p_destination_lat,
                        'longitude', p_destination_lng,
                        'accuracy', p_destination_accuracy,
                        'address', p_destination_address
                    ),
                    'intermediate_stops', COALESCE(p_intermediate_stops, '[]'::jsonb),
                    'requested_price_minor', p_requested_price_minor,
                    'scheduled_for', p_scheduled_for
                )::TEXT,
                'UTF8'
            ),
            'sha256'
        ),
        'hex'
    );
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 10. P0.9: AUTHORITATIVE REQUEST RIDE WITH CANONICAL DB STOPS IN RESPONSE
-- Ensure stops have portable numeric coordinates
ALTER TABLE public.ride_request_stops ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION;
ALTER TABLE public.ride_request_stops ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION;

DROP FUNCTION IF EXISTS public.mobility_request_ride;

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
    v_seq INTEGER := 1;
    v_stop JSONB;
    v_stop_lat DOUBLE PRECISION;
    v_stop_lng DOUBLE PRECISION;
    v_stops_json JSONB;
    v_response JSONB;
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

    IF p_dispatch_mode NOT IN ('AUTO_DISPATCH', 'MARKETPLACE_OFFERS') THEN
        RAISE EXCEPTION 'INVALID_DISPATCH_MODE';
    END IF;

    SELECT * INTO v_market FROM public.mobility_markets WHERE market_id = p_market_id AND active = TRUE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'MARKET_NOT_FOUND_OR_INACTIVE';
    END IF;

    SELECT * INTO v_category FROM public.mobility_service_categories
    WHERE service_category_id = p_service_category_id AND active = TRUE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'SERVICE_CATEGORY_NOT_FOUND_OR_INACTIVE';
    END IF;

    IF p_dispatch_mode = 'MARKETPLACE_OFFERS' AND NOT v_market.marketplace_offers_enabled THEN
        RAISE EXCEPTION 'MARKETPLACE_NOT_AVAILABLE';
    END IF;

    IF p_scheduled_for IS NOT NULL AND NOT v_market.scheduled_rides_enabled THEN
        RAISE EXCEPTION 'SCHEDULED_RIDE_NOT_AVAILABLE';
    END IF;

    -- Canonical server-computed SHA-256 hash using comprehensive digest
    v_hash := public.mobility_request_digest(
        p_market_id,
        p_service_category_id,
        p_dispatch_mode,
        p_pickup_lat,
        p_pickup_lng,
        p_pickup_accuracy,
        p_pickup_address,
        p_destination_lat,
        p_destination_lng,
        p_destination_accuracy,
        p_destination_address,
        p_intermediate_stops,
        p_requested_price_minor,
        p_scheduled_for
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

    -- Pickup stop (Sequence 0)
    INSERT INTO public.ride_request_stops (
        ride_request_id, sequence, stop_type, location, accuracy_meters, address, latitude, longitude
    ) VALUES (
        v_request.ride_request_id, 0, 'PICKUP',
        v_request.pickup_location, p_pickup_accuracy, p_pickup_address, p_pickup_lat, p_pickup_lng
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
                ride_request_id, sequence, stop_type, location, accuracy_meters, address, display_name, place_id, latitude, longitude
            ) VALUES (
                v_request.ride_request_id,
                v_seq,
                'INTERMEDIATE',
                extensions.ST_SetSRID(extensions.ST_MakePoint(v_stop_lng, v_stop_lat), 4326),
                (v_stop->>'accuracy_meters')::REAL,
                v_stop->>'address',
                v_stop->>'display_name',
                v_stop->>'place_id',
                v_stop_lat,
                v_stop_lng
            );
            v_seq := v_seq + 1;
        END LOOP;
    END IF;

    -- Destination stop (Last Sequence)
    INSERT INTO public.ride_request_stops (
        ride_request_id, sequence, stop_type, location, accuracy_meters, address, latitude, longitude
    ) VALUES (
        v_request.ride_request_id, v_seq, 'DESTINATION',
        v_request.destination_location, p_destination_accuracy, p_destination_address, p_destination_lat, p_destination_lng
    );

    -- Canonical Stops Aggregation with Real Database IDs
    SELECT jsonb_agg(
        jsonb_build_object(
            'stop_id', s.stop_id,
            'sequence', s.sequence,
            'stop_type', s.stop_type,
            'latitude', s.latitude,
            'longitude', s.longitude,
            'accuracy_meters', s.accuracy_meters,
            'address', s.address,
            'display_name', s.display_name,
            'place_id', s.place_id
        ) ORDER BY s.sequence ASC
    ) INTO v_stops_json
    FROM public.ride_request_stops s
    WHERE s.ride_request_id = v_request.ride_request_id;

    v_response := jsonb_build_object(
        'success', TRUE,
        'ride_request_id', v_request.ride_request_id,
        'state', v_request.state,
        'version', v_request.version,
        'stops', COALESCE(v_stops_json, '[]'::jsonb)
    );

    INSERT INTO public.mobility_command_receipts (
        actor_id, command_scope, idempotency_key, request_hash, requested_aggregate_id, aggregate_id, response
    ) VALUES (
        v_actor, 'REQUEST_RIDE', p_idempotency_key, v_hash, NULL, v_request.ride_request_id, v_response
    );

    RETURN v_response;
END;
$$;

GRANT EXECUTE ON FUNCTION public.mobility_request_ride(TEXT, TEXT, TEXT, DOUBLE PRECISION, DOUBLE PRECISION, REAL, TEXT, DOUBLE PRECISION, DOUBLE PRECISION, REAL, TEXT, JSONB, BIGINT, TIMESTAMPTZ, UUID, UUID) TO authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 11. WEBHOOK RECEIPTS & SERVICE AREAS TABLES (V7 SECTIONS 31, 55)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.payment_webhook_receipts (
    provider TEXT NOT NULL,
    provider_event_id TEXT NOT NULL,
    payload_hash TEXT NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (provider, provider_event_id)
);

CREATE TABLE IF NOT EXISTS public.mobility_service_areas (
    service_area_id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    market_id TEXT NOT NULL REFERENCES public.mobility_markets(market_id),
    name TEXT NOT NULL,
    boundary_geojson JSONB NOT NULL DEFAULT '{}'::jsonb,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_mobility_service_area_market
ON public.mobility_service_areas (market_id, active);

ALTER TABLE public.payment_webhook_receipts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.payment_webhook_receipts FORCE ROW LEVEL SECURITY;
ALTER TABLE public.mobility_service_areas ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.mobility_service_areas FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS service_areas_read_all ON public.mobility_service_areas;
CREATE POLICY service_areas_read_all ON public.mobility_service_areas
FOR SELECT TO authenticated USING (active = TRUE);
GRANT SELECT ON public.mobility_service_areas TO authenticated;
