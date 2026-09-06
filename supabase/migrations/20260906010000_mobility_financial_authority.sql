-- ─────────────────────────────────────────────────────────────────────────────
-- ELYSIUM GLOBAL MOBILITY OS — FINANCIAL AUTHORITY & BALANCED LEDGER (WAVES 11–14)
-- Mandate: ORDEN MAESTRA V6 (Sections 63–73)
-- ─────────────────────────────────────────────────────────────────────────────

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. UPFRONT RIDE QUOTES
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.ride_quotes (
    quote_id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    ride_request_id UUID REFERENCES public.ride_requests(ride_request_id) ON DELETE SET NULL,
    market_id TEXT NOT NULL REFERENCES public.mobility_markets(market_id) ON DELETE RESTRICT,
    service_category_id TEXT NOT NULL REFERENCES public.mobility_service_categories(service_category_id) ON DELETE RESTRICT,
    base_fare_minor BIGINT NOT NULL CHECK (base_fare_minor >= 0),
    distance_fare_minor BIGINT NOT NULL CHECK (distance_fare_minor >= 0),
    time_fare_minor BIGINT NOT NULL CHECK (time_fare_minor >= 0),
    surge_adjustment_minor BIGINT NOT NULL DEFAULT 0 CHECK (surge_adjustment_minor >= 0),
    toll_estimate_minor BIGINT NOT NULL DEFAULT 0 CHECK (toll_estimate_minor >= 0),
    tax_minor BIGINT NOT NULL DEFAULT 0 CHECK (tax_minor >= 0),
    total_fare_minor BIGINT NOT NULL CHECK (total_fare_minor >= 0),
    currency_code TEXT NOT NULL CHECK (currency_code ~ '^[A-Z]{3}$'),
    pricing_policy_version BIGINT NOT NULL DEFAULT 1 CHECK (pricing_policy_version >= 1),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. PAYMENT AUTHORIZATIONS
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.payment_authorizations (
    payment_authorization_id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    trip_id UUID REFERENCES public.trips(trip_id) ON DELETE SET NULL,
    rider_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    provider TEXT NOT NULL CHECK (provider IN ('CASH', 'CARD_TOKEN', 'WALLET', 'CORPORATE_ACCOUNT', 'SINPE_MOVIL')),
    provider_auth_ref TEXT,
    amount_minor BIGINT NOT NULL CHECK (amount_minor >= 0),
    currency_code TEXT NOT NULL CHECK (currency_code ~ '^[A-Z]{3}$'),
    state TEXT NOT NULL CHECK (state IN ('PENDING', 'AUTHORIZED', 'DECLINED', 'EXPIRED', 'CANCELLED', 'CAPTURED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. DOUBLE-ENTRY BALANCED LEDGER
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.ledger_accounts (
    account_id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    owner_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    account_type TEXT NOT NULL CHECK (account_type IN (
        'RIDER_RECEIVABLE',
        'DRIVER_PAYABLE',
        'PLATFORM_REVENUE',
        'TAX_ESCROW',
        'TOLL_ESCROW'
    )),
    currency_code TEXT NOT NULL CHECK (currency_code ~ '^[A-Z]{3}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (owner_id, account_type, currency_code)
);

CREATE TABLE IF NOT EXISTS public.ledger_transactions (
    transaction_id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    reference_type TEXT NOT NULL CHECK (reference_type IN (
        'TRIP_SETTLEMENT',
        'PAYMENT_CAPTURE',
        'PAYMENT_REFUND',
        'DISPUTE_ADJUSTMENT'
    )),
    reference_id UUID NOT NULL,
    currency_code TEXT NOT NULL CHECK (currency_code ~ '^[A-Z]{3}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE IF NOT EXISTS public.ledger_entries (
    entry_id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES public.ledger_transactions(transaction_id) ON DELETE RESTRICT,
    account_id UUID NOT NULL REFERENCES public.ledger_accounts(account_id) ON DELETE RESTRICT,
    amount_minor BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. TRIP SETTLEMENTS
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.trip_settlements (
    settlement_id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    trip_id UUID NOT NULL UNIQUE REFERENCES public.trips(trip_id) ON DELETE RESTRICT,
    gross_fare_minor BIGINT NOT NULL CHECK (gross_fare_minor >= 0),
    platform_fee_minor BIGINT NOT NULL CHECK (platform_fee_minor >= 0),
    driver_earnings_minor BIGINT NOT NULL CHECK (driver_earnings_minor >= 0),
    tax_minor BIGINT NOT NULL DEFAULT 0 CHECK (tax_minor >= 0),
    toll_minor BIGINT NOT NULL DEFAULT 0 CHECK (toll_minor >= 0),
    currency_code TEXT NOT NULL CHECK (currency_code ~ '^[A-Z]{3}$'),
    pricing_policy_version BIGINT NOT NULL DEFAULT 1 CHECK (pricing_policy_version >= 1),
    ledger_transaction_id UUID NOT NULL REFERENCES public.ledger_transactions(transaction_id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT chk_gross_equals_components CHECK (gross_fare_minor = platform_fee_minor + driver_earnings_minor + tax_minor + toll_minor)
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. REVOKE DIRECT MUTATION
-- ─────────────────────────────────────────────────────────────────────────────

REVOKE INSERT, UPDATE, DELETE ON public.ride_quotes FROM authenticated, anon;
REVOKE INSERT, UPDATE, DELETE ON public.payment_authorizations FROM authenticated, anon;
REVOKE INSERT, UPDATE, DELETE ON public.ledger_accounts FROM authenticated, anon;
REVOKE INSERT, UPDATE, DELETE ON public.ledger_transactions FROM authenticated, anon;
REVOKE INSERT, UPDATE, DELETE ON public.ledger_entries FROM authenticated, anon;
REVOKE INSERT, UPDATE, DELETE ON public.trip_settlements FROM authenticated, anon;

GRANT SELECT ON public.ride_quotes TO authenticated;
GRANT SELECT ON public.payment_authorizations TO authenticated;
GRANT SELECT ON public.ledger_accounts TO authenticated;
GRANT SELECT ON public.ledger_transactions TO authenticated;
GRANT SELECT ON public.ledger_entries TO authenticated;
GRANT SELECT ON public.trip_settlements TO authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- 6. RPC: GENERATE UPFRONT QUOTE
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
    v_market public.mobility_markets%ROWTYPE;
    v_category public.mobility_service_categories%ROWTYPE;
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
    IF p_surge_denominator <= 0 OR p_surge_numerator < p_surge_denominator THEN
        RAISE EXCEPTION 'INVALID_SURGE_RATIO';
    END IF;

    IF p_distance_meters < 0 OR p_duration_seconds < 0 THEN
        RAISE EXCEPTION 'INVALID_TRIP_METRICS';
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

    -- Tariffs in minor units:
    -- Base: ₡1.000 (100000 minor)
    -- Per km: ₡650 (65000 minor per 1000m -> 65 per meter)
    -- Per minute: ₡80 (8000 minor per 60s)
    v_base_fare := 100000;
    v_dist_fare := (p_distance_meters * 65);
    v_time_fare := ((p_duration_seconds * 8000) / 60);

    v_raw_subtotal := v_base_fare + v_dist_fare + v_time_fare;
    v_surged_subtotal := (v_raw_subtotal * p_surge_numerator) / p_surge_denominator;
    v_surge_adj := v_surged_subtotal - v_raw_subtotal;

    -- 13% tax (Costa Rica VAT example)
    v_tax := (v_surged_subtotal * 13) / 100;
    v_total := v_surged_subtotal + v_tax;

    INSERT INTO public.ride_quotes (
        market_id,
        service_category_id,
        base_fare_minor,
        distance_fare_minor,
        time_fare_minor,
        surge_adjustment_minor,
        tax_minor,
        total_fare_minor,
        currency_code,
        pricing_policy_version,
        expires_at
    ) VALUES (
        p_market_id,
        p_service_category_id,
        v_base_fare,
        v_dist_fare,
        v_time_fare,
        v_surge_adj,
        v_tax,
        v_total,
        v_market.currency_code,
        1,
        clock_timestamp() + INTERVAL '10 minutes'
    ) RETURNING * INTO v_quote;

    RETURN jsonb_build_object(
        'success', TRUE,
        'quote', row_to_json(v_quote)
    );
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 7. RPC: AUTHORIZE PAYMENT
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.mobility_authorize_payment(
    p_rider_id UUID,
    p_provider TEXT,
    p_amount_minor BIGINT,
    p_currency_code TEXT,
    p_provider_auth_ref TEXT DEFAULT NULL,
    p_trip_id UUID DEFAULT NULL
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_actor UUID := auth.uid();
    v_auth public.payment_authorizations%ROWTYPE;
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED' USING ERRCODE = '42501';
    END IF;

    IF v_actor <> p_rider_id THEN
        RAISE EXCEPTION 'FORBIDDEN' USING ERRCODE = '42501';
    END IF;

    IF p_amount_minor < 0 THEN
        RAISE EXCEPTION 'INVALID_PAYMENT_AMOUNT';
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
        p_trip_id,
        p_rider_id,
        p_provider,
        COALESCE(p_provider_auth_ref, 'auth_ref_' || extensions.gen_random_uuid()::text),
        p_amount_minor,
        p_currency_code,
        'AUTHORIZED'
    ) RETURNING * INTO v_auth;

    RETURN jsonb_build_object(
        'success', TRUE,
        'authorization', row_to_json(v_auth)
    );
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 8. RPC: POST BALANCED LEDGER TRANSACTION
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.mobility_post_ledger_transaction(
    p_reference_type TEXT,
    p_reference_id UUID,
    p_currency_code TEXT,
    p_entries JSONB
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_sum BIGINT := 0;
    v_entry JSONB;
    v_tx public.ledger_transactions%ROWTYPE;
BEGIN
    IF p_entries IS NULL OR jsonb_array_length(p_entries) < 2 THEN
        RAISE EXCEPTION 'DOUBLE_ENTRY_REQUIRES_AT_LEAST_TWO_ENTRIES';
    END IF;

    -- Strict Invariant Check: SUM(amount_minor) == 0
    SELECT COALESCE(SUM((entry->>'amount_minor')::BIGINT), 0)
    INTO v_sum
    FROM jsonb_array_elements(p_entries) entry;

    IF v_sum <> 0 THEN
        RAISE EXCEPTION 'UNBALANCED_LEDGER_TRANSACTION' USING ERRCODE = '23514';
    END IF;

    INSERT INTO public.ledger_transactions (
        reference_type,
        reference_id,
        currency_code
    ) VALUES (
        p_reference_type,
        p_reference_id,
        p_currency_code
    ) RETURNING * INTO v_tx;

    FOR v_entry IN SELECT * FROM jsonb_array_elements(p_entries) LOOP
        INSERT INTO public.ledger_entries (
            transaction_id,
            account_id,
            amount_minor
        ) VALUES (
            v_tx.transaction_id,
            (v_entry->>'account_id')::UUID,
            (v_entry->>'amount_minor')::BIGINT
        );
    END LOOP;

    RETURN jsonb_build_object(
        'success', TRUE,
        'transaction_id', v_tx.transaction_id,
        'reference_type', v_tx.reference_type,
        'reference_id', v_tx.reference_id,
        'entries_count', jsonb_array_length(p_entries)
    );
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 9. RPC: SETTLE TRIP (Atomic Financial CAS Procedure)
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

    v_gross := v_quote.total_fare_minor;
    v_tax := v_quote.tax_minor;
    -- 15% platform commission on net fare
    v_platform_fee := ((v_gross - v_tax) * 15) / 100;
    -- Driver receives remaining 85% of net fare
    v_driver_earnings := v_gross - v_platform_fee - v_tax;

    -- Capture payment authorization
    UPDATE public.payment_authorizations
    SET state = 'CAPTURED',
        trip_id = p_trip_id,
        updated_at = clock_timestamp()
    WHERE payment_authorization_id = p_payment_authorization_id;

    -- Ensure ledger accounts exist
    -- Rider Account
    INSERT INTO public.ledger_accounts (owner_id, account_type, currency_code)
    VALUES (v_trip.rider_id, 'RIDER_RECEIVABLE', v_quote.currency_code)
    ON CONFLICT (owner_id, account_type, currency_code) DO UPDATE SET currency_code = EXCLUDED.currency_code
    RETURNING account_id INTO v_rider_acc;

    -- Driver Account
    INSERT INTO public.ledger_accounts (owner_id, account_type, currency_code)
    VALUES (v_trip.driver_id, 'DRIVER_PAYABLE', v_quote.currency_code)
    ON CONFLICT (owner_id, account_type, currency_code) DO UPDATE SET currency_code = EXCLUDED.currency_code
    RETURNING account_id INTO v_driver_acc;

    -- Platform Revenue Account
    INSERT INTO public.ledger_accounts (owner_id, account_type, currency_code)
    VALUES (NULL, 'PLATFORM_REVENUE', v_quote.currency_code)
    ON CONFLICT (owner_id, account_type, currency_code) DO UPDATE SET currency_code = EXCLUDED.currency_code
    RETURNING account_id INTO v_platform_acc;

    -- Tax Escrow Account
    INSERT INTO public.ledger_accounts (owner_id, account_type, currency_code)
    VALUES (NULL, 'TAX_ESCROW', v_quote.currency_code)
    ON CONFLICT (owner_id, account_type, currency_code) DO UPDATE SET currency_code = EXCLUDED.currency_code
    RETURNING account_id INTO v_tax_acc;

    -- Post Balanced Double-Entry Transaction:
    -- Rider: +Gross
    -- Driver: -Earnings
    -- Platform: -Fee
    -- Tax: -Tax
    -- Sum = Gross - Earnings - Fee - Tax = 0
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

    -- Insert Trip Settlement
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

    -- Update Trip row
    UPDATE public.trips
    SET state = 'COMPLETED',
        settlement_id = v_settlement.settlement_id,
        payment_authorization_id = p_payment_authorization_id,
        quote_id = p_quote_id,
        version = version + 1,
        completed_at = COALESCE(completed_at, clock_timestamp()),
        updated_at = clock_timestamp()
    WHERE trip_id = p_trip_id;

    v_response := jsonb_build_object(
        'success', TRUE,
        'settlement', row_to_json(v_settlement)
    );

    INSERT INTO public.mobility_command_receipts (
        actor_id, command_scope, idempotency_key, request_hash, requested_aggregate_id, aggregate_id, response
    ) VALUES (
        v_actor, 'SETTLE_TRIP', p_idempotency_key, v_hash, p_trip_id, v_settlement.settlement_id, v_response
    );

    RETURN v_response;
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 10. GRANT EXECUTE ON FINANCIAL RPCS TO AUTHENTICATED
-- ─────────────────────────────────────────────────────────────────────────────

REVOKE ALL ON FUNCTION public.mobility_generate_quote FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobility_generate_quote TO authenticated;

REVOKE ALL ON FUNCTION public.mobility_authorize_payment FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobility_authorize_payment TO authenticated;

REVOKE ALL ON FUNCTION public.mobility_post_ledger_transaction FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobility_post_ledger_transaction TO authenticated;

REVOKE ALL ON FUNCTION public.mobility_settle_trip FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mobility_settle_trip TO authenticated;

