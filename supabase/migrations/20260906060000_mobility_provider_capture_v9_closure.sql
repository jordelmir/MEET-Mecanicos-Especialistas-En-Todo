-- =============================================================================
-- ELYSIUM GLOBAL MOBILITY OS
-- FINANCIAL AUTHORITY V9 — PROVIDER CAPTURE AUTHORITY CLOSURE
--
-- Invariant:
--   Electronic CAPTURED state may only be produced after trusted provider
--   capture evidence has been received and validated by service-side code.
--
--   Settlement NEVER creates CAPTURED truth.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. STRONG PAYMENT -> QUOTE -> CAPTURE EVIDENCE BINDING
-- ---------------------------------------------------------------------------

ALTER TABLE public.payment_authorizations
    ADD COLUMN IF NOT EXISTS quote_id UUID
        REFERENCES public.ride_quotes(quote_id) ON DELETE RESTRICT;

ALTER TABLE public.payment_authorizations
    ADD COLUMN IF NOT EXISTS provider_capture_ref TEXT;

ALTER TABLE public.payment_authorizations
    ADD COLUMN IF NOT EXISTS provider_capture_event_id TEXT;

ALTER TABLE public.payment_authorizations
    ADD COLUMN IF NOT EXISTS provider_captured_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_payment_authorizations_quote_id
    ON public.payment_authorizations(quote_id);

CREATE INDEX IF NOT EXISTS idx_payment_authorizations_trip_id
    ON public.payment_authorizations(trip_id);

-- ---------------------------------------------------------------------------
-- 2. REDEFINE PAYMENT AUTHORIZATION
--
-- Important:
--   - quote determines amount/currency
--   - payment remains explicitly bound to quote
--   - electronic money starts PENDING_PROVIDER
--   - CASH starts CASH_PENDING
-- ---------------------------------------------------------------------------

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
        RAISE EXCEPTION 'UNAUTHENTICATED'
            USING ERRCODE = '42501';
    END IF;

    IF p_idempotency_key IS NULL THEN
        RAISE EXCEPTION 'IDEMPOTENCY_KEY_REQUIRED';
    END IF;

    IF p_provider NOT IN (
        'CASH',
        'CARD_TOKEN',
        'WALLET',
        'CORPORATE_ACCOUNT',
        'SINPE_MOVIL'
    ) THEN
        RAISE EXCEPTION 'INVALID_PAYMENT_PROVIDER';
    END IF;

    SELECT *
    INTO v_quote
    FROM public.ride_quotes
    WHERE quote_id = p_quote_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'QUOTE_NOT_FOUND';
    END IF;

    IF v_quote.expires_at <= clock_timestamp() THEN
        RAISE EXCEPTION 'QUOTE_EXPIRED';
    END IF;

    IF auth.role() <> 'service_role'
       AND v_quote.rider_id <> v_actor THEN
        RAISE EXCEPTION 'QUOTE_NOT_OWNED_BY_ACTOR'
            USING ERRCODE = '42501';
    END IF;

    v_hash := encode(
        extensions.digest(
            convert_to(
                jsonb_build_object(
                    'quote_id', p_quote_id,
                    'provider', p_provider,
                    'actor_id', COALESCE(v_actor, v_quote.rider_id)
                )::TEXT,
                'UTF8'
            ),
            'sha256'
        ),
        'hex'
    );

    PERFORM pg_advisory_xact_lock(
        hashtextextended(
            COALESCE(v_actor::TEXT, 'system')
            || ':AUTHORIZE_PAYMENT:'
            || p_idempotency_key::TEXT,
            0
        )
    );

    SELECT *
    INTO v_receipt
    FROM public.mobility_command_receipts
    WHERE actor_id =
            COALESCE(
                v_actor,
                '00000000-0000-0000-0000-000000000000'::UUID
            )
      AND command_scope = 'AUTHORIZE_PAYMENT'
      AND idempotency_key = p_idempotency_key;

    IF FOUND THEN
        IF v_receipt.request_hash <> v_hash THEN
            RAISE EXCEPTION
                'IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD'
                USING ERRCODE = '23505';
        END IF;

        RETURN v_receipt.response;
    END IF;

    IF p_provider = 'CASH' THEN
        v_initial_state := 'CASH_PENDING';
    ELSE
        v_initial_state := 'PENDING_PROVIDER';
    END IF;

    INSERT INTO public.payment_authorizations (
        quote_id,
        trip_id,
        rider_id,
        provider,
        provider_auth_ref,
        provider_capture_ref,
        provider_capture_event_id,
        provider_captured_at,
        amount_minor,
        currency_code,
        state
    ) VALUES (
        p_quote_id,
        NULL,
        v_quote.rider_id,
        p_provider,
        NULL,
        NULL,
        NULL,
        NULL,
        v_quote.total_fare_minor,
        v_quote.currency_code,
        v_initial_state
    )
    RETURNING *
    INTO v_auth;

    v_response := jsonb_build_object(
        'success', TRUE,
        'authorization', row_to_json(v_auth)
    );

    INSERT INTO public.mobility_command_receipts (
        actor_id,
        command_scope,
        idempotency_key,
        request_hash,
        requested_aggregate_id,
        aggregate_id,
        response
    ) VALUES (
        COALESCE(
            v_actor,
            '00000000-0000-0000-0000-000000000000'::UUID
        ),
        'AUTHORIZE_PAYMENT',
        p_idempotency_key,
        v_hash,
        p_quote_id,
        v_auth.payment_authorization_id,
        v_response
    );

    RETURN v_response;
END;
$$;

REVOKE ALL
ON FUNCTION public.mobility_authorize_quote_payment(UUID, TEXT, UUID)
FROM PUBLIC, anon;

GRANT EXECUTE
ON FUNCTION public.mobility_authorize_quote_payment(UUID, TEXT, UUID)
TO authenticated, service_role;


-- ---------------------------------------------------------------------------
-- 3. PROVIDER CAPTURE AUTHORITY
--
-- IMPORTANT:
-- This function is NOT the PSP signature verifier itself.
--
-- The Edge Function/backend/webhook handler MUST:
--   1. verify PSP signature/API response
--   2. verify provider account
--   3. verify anti-replay timestamp
--   4. extract canonical amount/currency/capture ID
--   5. only THEN call this RPC with service_role
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.mobility_confirm_provider_capture(
    p_payment_authorization_id UUID,
    p_provider_capture_ref TEXT,
    p_provider_event_id TEXT,
    p_captured_amount_minor BIGINT DEFAULT NULL,
    p_currency_code TEXT DEFAULT NULL,
    p_provider_payload JSONB DEFAULT '{}'::JSONB,
    p_trip_id UUID DEFAULT NULL
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_auth public.payment_authorizations%ROWTYPE;
    v_quote public.ride_quotes%ROWTYPE;
    v_trip public.trips%ROWTYPE;
    v_existing_event public.payment_provider_events%ROWTYPE;
BEGIN
    IF auth.role() <> 'service_role' THEN
        RAISE EXCEPTION 'SERVICE_ROLE_REQUIRED_FOR_PROVIDER_CAPTURE'
            USING ERRCODE = '42501';
    END IF;

    IF p_provider_capture_ref IS NULL
       OR trim(p_provider_capture_ref) = '' THEN
        RAISE EXCEPTION 'INVALID_PROVIDER_CAPTURE_REF';
    END IF;

    IF p_provider_event_id IS NULL
       OR trim(p_provider_event_id) = '' THEN
        RAISE EXCEPTION 'INVALID_PROVIDER_EVENT_ID';
    END IF;

    SELECT *
    INTO v_auth
    FROM public.payment_authorizations
    WHERE payment_authorization_id = p_payment_authorization_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'PAYMENT_AUTHORIZATION_NOT_FOUND';
    END IF;

    IF v_auth.provider = 'CASH' THEN
        RAISE EXCEPTION 'CASH_DOES_NOT_USE_PROVIDER_CAPTURE';
    END IF;

    -- Idempotent retry after successful capture.
    IF v_auth.state = 'CAPTURED' THEN
        IF v_auth.provider_capture_ref = p_provider_capture_ref
           AND v_auth.provider_capture_event_id = p_provider_event_id
           AND (p_captured_amount_minor IS NULL OR v_auth.amount_minor = p_captured_amount_minor)
           AND (p_currency_code IS NULL OR v_auth.currency_code = p_currency_code) THEN

            RETURN jsonb_build_object(
                'success', TRUE,
                'already_processed', TRUE,
                'authorization', row_to_json(v_auth)
            );
        END IF;

        RAISE EXCEPTION
            'PAYMENT_ALREADY_CAPTURED_WITH_DIFFERENT_EVIDENCE';
    END IF;

    IF v_auth.state <> 'AUTHORIZED' THEN
        RAISE EXCEPTION
            'PAYMENT_NOT_READY_FOR_CAPTURE: state=%',
            v_auth.state;
    END IF;

    IF v_auth.quote_id IS NULL THEN
        RAISE EXCEPTION 'PAYMENT_QUOTE_BINDING_MISSING';
    END IF;

    IF p_captured_amount_minor IS NOT NULL AND p_captured_amount_minor <> v_auth.amount_minor THEN
        RAISE EXCEPTION
            'PROVIDER_CAPTURE_AMOUNT_MISMATCH: expected %, got %',
            v_auth.amount_minor,
            p_captured_amount_minor;
    END IF;

    IF p_currency_code IS NOT NULL AND p_currency_code <> v_auth.currency_code THEN
        RAISE EXCEPTION
            'PROVIDER_CAPTURE_CURRENCY_MISMATCH: expected %, got %',
            v_auth.currency_code,
            p_currency_code;
    END IF;

    SELECT *
    INTO v_quote
    FROM public.ride_quotes
    WHERE quote_id = v_auth.quote_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'BOUND_QUOTE_NOT_FOUND';
    END IF;

    IF v_quote.total_fare_minor <> v_auth.amount_minor THEN
        RAISE EXCEPTION 'PAYMENT_QUOTE_AMOUNT_MISMATCH';
    END IF;

    IF v_quote.currency_code <> v_auth.currency_code THEN
        RAISE EXCEPTION 'PAYMENT_QUOTE_CURRENCY_MISMATCH';
    END IF;

    IF p_trip_id IS NOT NULL THEN
        SELECT *
        INTO v_trip
        FROM public.trips
        WHERE trip_id = p_trip_id
        FOR UPDATE;

        IF NOT FOUND THEN
            RAISE EXCEPTION 'TRIP_NOT_FOUND';
        END IF;

        IF v_trip.rider_id <> v_auth.rider_id THEN
            RAISE EXCEPTION 'PAYMENT_RIDER_MISMATCH';
        END IF;

        IF v_quote.rider_id <> v_trip.rider_id THEN
            RAISE EXCEPTION 'QUOTE_RIDER_MISMATCH';
        END IF;

        IF v_quote.ride_request_id <> v_trip.ride_request_id THEN
            RAISE EXCEPTION 'PAYMENT_QUOTE_TRIP_BINDING_MISMATCH';
        END IF;
    END IF;

    -- Reject replay of the same provider event against another payment.
    SELECT *
    INTO v_existing_event
    FROM public.payment_provider_events
    WHERE provider = v_auth.provider
      AND provider_event_id = p_provider_event_id;

    IF FOUND THEN
        IF v_existing_event.payment_authorization_id
                = p_payment_authorization_id
           AND v_existing_event.event_type = 'PAYMENT_CAPTURED' THEN

            RETURN jsonb_build_object(
                'success', TRUE,
                'already_processed', TRUE,
                'authorization', row_to_json(v_auth)
            );
        END IF;

        RAISE EXCEPTION
            'PROVIDER_EVENT_REPLAY_OR_CROSS_PAYMENT_REUSE';
    END IF;

    INSERT INTO public.payment_provider_events (
        provider,
        provider_event_id,
        event_type,
        payment_authorization_id,
        payload
    ) VALUES (
        v_auth.provider,
        p_provider_event_id,
        'PAYMENT_CAPTURED',
        p_payment_authorization_id,
        p_provider_payload
    );

    UPDATE public.payment_authorizations
    SET
        state = 'CAPTURED',
        trip_id = COALESCE(p_trip_id, v_auth.trip_id),
        provider_capture_ref = p_provider_capture_ref,
        provider_capture_event_id = p_provider_event_id,
        provider_captured_at = clock_timestamp(),
        updated_at = clock_timestamp()
    WHERE payment_authorization_id = p_payment_authorization_id
    RETURNING *
    INTO v_auth;

    RETURN jsonb_build_object(
        'success', TRUE,
        'already_processed', FALSE,
        'authorization', row_to_json(v_auth)
    );
END;
$$;

REVOKE ALL
ON FUNCTION public.mobility_confirm_provider_capture(
    UUID,
    TEXT,
    TEXT,
    BIGINT,
    TEXT,
    JSONB,
    UUID
)
FROM PUBLIC, anon, authenticated;

GRANT EXECUTE
ON FUNCTION public.mobility_confirm_provider_capture(
    UUID,
    TEXT,
    TEXT,
    BIGINT,
    TEXT,
    JSONB,
    UUID
)
TO service_role;


-- ---------------------------------------------------------------------------
-- 4. CASH COLLECTION IS A SEPARATE DOMAIN EVENT
--
-- Driver attestation is explicitly different from PSP evidence.
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.mobility_confirm_cash_collected(
    p_payment_authorization_id UUID,
    p_trip_id UUID,
    p_idempotency_key UUID
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_actor UUID := auth.uid();
    v_auth public.payment_authorizations%ROWTYPE;
    v_quote public.ride_quotes%ROWTYPE;
    v_trip public.trips%ROWTYPE;
BEGIN
    IF p_idempotency_key IS NULL THEN
        RAISE EXCEPTION 'IDEMPOTENCY_KEY_REQUIRED';
    END IF;

    PERFORM pg_advisory_xact_lock(
        hashtextextended(
            'cash_collection:'
            || p_payment_authorization_id::TEXT
            || ':'
            || p_trip_id::TEXT,
            0
        )
    );

    SELECT *
    INTO v_auth
    FROM public.payment_authorizations
    WHERE payment_authorization_id = p_payment_authorization_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'PAYMENT_AUTHORIZATION_NOT_FOUND';
    END IF;

    IF v_auth.provider <> 'CASH' THEN
        RAISE EXCEPTION 'PAYMENT_IS_NOT_CASH';
    END IF;

    SELECT *
    INTO v_trip
    FROM public.trips
    WHERE trip_id = p_trip_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'TRIP_NOT_FOUND';
    END IF;

    -- Only assigned driver or trusted server may attest cash collection.
    IF auth.role() <> 'service_role'
       AND (v_actor IS NULL OR v_trip.driver_id <> v_actor) THEN
        RAISE EXCEPTION 'ONLY_ASSIGNED_DRIVER_CAN_CONFIRM_CASH_COLLECTION'
            USING ERRCODE = '42501';
    END IF;

    IF v_auth.rider_id <> v_trip.rider_id THEN
        RAISE EXCEPTION 'PAYMENT_RIDER_MISMATCH';
    END IF;

    IF v_auth.quote_id IS NULL THEN
        RAISE EXCEPTION 'PAYMENT_QUOTE_BINDING_MISSING';
    END IF;

    SELECT *
    INTO v_quote
    FROM public.ride_quotes
    WHERE quote_id = v_auth.quote_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'BOUND_QUOTE_NOT_FOUND';
    END IF;

    IF v_quote.ride_request_id <> v_trip.ride_request_id THEN
        RAISE EXCEPTION 'PAYMENT_QUOTE_TRIP_BINDING_MISMATCH';
    END IF;

    IF v_auth.state = 'CASH_COLLECTED' THEN
        IF v_auth.trip_id = p_trip_id THEN
            RETURN jsonb_build_object(
                'success', TRUE,
                'already_processed', TRUE,
                'authorization', row_to_json(v_auth)
            );
        END IF;

        RAISE EXCEPTION 'CASH_ALREADY_BOUND_TO_DIFFERENT_TRIP';
    END IF;

    IF v_auth.state <> 'CASH_PENDING' THEN
        RAISE EXCEPTION
            'INVALID_CASH_STATE_FOR_COLLECTION: %',
            v_auth.state;
    END IF;

    IF v_trip.state NOT IN ('ARRIVED_DESTINATION', 'COMPLETED') THEN
        RAISE EXCEPTION
            'TRIP_NOT_READY_FOR_CASH_COLLECTION';
    END IF;

    UPDATE public.payment_authorizations
    SET
        state = 'CASH_COLLECTED',
        trip_id = p_trip_id,
        updated_at = clock_timestamp()
    WHERE payment_authorization_id = p_payment_authorization_id
    RETURNING *
    INTO v_auth;

    RETURN jsonb_build_object(
        'success', TRUE,
        'already_processed', FALSE,
        'authorization', row_to_json(v_auth)
    );
END;
$$;

REVOKE ALL
ON FUNCTION public.mobility_confirm_cash_collected(UUID, UUID, UUID)
FROM PUBLIC, anon;

GRANT EXECUTE
ON FUNCTION public.mobility_confirm_cash_collected(UUID, UUID, UUID)
TO authenticated, service_role;


-- ---------------------------------------------------------------------------
-- 5. SETTLEMENT V9
--
-- Settlement consumes already-established payment truth.
-- Settlement NEVER invents capture truth.
-- ---------------------------------------------------------------------------

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
    IF auth.role() <> 'service_role' THEN
        RAISE EXCEPTION
            'UNAUTHORIZED_SETTLEMENT_AUTHORITY'
            USING ERRCODE = '42501';
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

    PERFORM pg_advisory_xact_lock(
        hashtextextended(
            'SETTLE_TRIP:' || p_idempotency_key::TEXT,
            0
        )
    );

    SELECT *
    INTO v_receipt
    FROM public.mobility_command_receipts
    WHERE command_scope = 'SETTLE_TRIP'
      AND idempotency_key = p_idempotency_key;

    IF FOUND THEN
        IF v_receipt.request_hash <> v_hash THEN
            RAISE EXCEPTION
                'IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD'
                USING ERRCODE = '23505';
        END IF;

        RETURN v_receipt.response;
    END IF;

    -- Serialize every possible settlement for this aggregate.
    PERFORM pg_advisory_xact_lock(
        hashtextextended(
            'trip_settlement:' || p_trip_id::TEXT,
            0
        )
    );

    SELECT *
    INTO v_trip
    FROM public.trips
    WHERE trip_id = p_trip_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'TRIP_NOT_FOUND';
    END IF;

    IF v_trip.settlement_id IS NOT NULL THEN
        RETURN jsonb_build_object(
            'success', FALSE,
            'conflict', TRUE,
            'error_code', 'ALREADY_SETTLED'
        );
    END IF;

    IF v_trip.state NOT IN (
        'ARRIVED_DESTINATION',
        'COMPLETED'
    ) THEN
        RAISE EXCEPTION 'INVALID_TRIP_STATE_FOR_SETTLEMENT';
    END IF;

    SELECT *
    INTO v_auth
    FROM public.payment_authorizations
    WHERE payment_authorization_id =
        p_payment_authorization_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'PAYMENT_AUTHORIZATION_NOT_FOUND';
    END IF;

    SELECT *
    INTO v_quote
    FROM public.ride_quotes
    WHERE quote_id = p_quote_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'QUOTE_NOT_FOUND';
    END IF;

    -- Strongest possible direct binding.
    IF v_auth.quote_id IS NULL THEN
        RAISE EXCEPTION 'PAYMENT_QUOTE_BINDING_MISSING';
    END IF;

    IF v_auth.quote_id <> p_quote_id THEN
        RAISE EXCEPTION 'PAYMENT_QUOTE_BINDING_MISMATCH';
    END IF;

    IF v_quote.ride_request_id <> v_trip.ride_request_id THEN
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

    IF v_auth.trip_id IS NOT NULL AND v_auth.trip_id <> p_trip_id THEN
        RAISE EXCEPTION 'PAYMENT_TRIP_BINDING_MISMATCH';
    END IF;

    -- Critical V9 invariant.
    IF v_auth.provider = 'CASH' THEN
        IF v_auth.state <> 'CASH_COLLECTED' THEN
            RAISE EXCEPTION
                'CASH_NOT_CONFIRMED_COLLECTED: state=%',
                v_auth.state;
        END IF;
    ELSE
        IF v_auth.state <> 'CAPTURED' THEN
            RAISE EXCEPTION
                'PAYMENT_NOT_CAPTURED_BY_PROVIDER: state=%',
                v_auth.state;
        END IF;

        IF v_auth.provider_capture_ref IS NULL
           OR v_auth.provider_capture_event_id IS NULL
           OR v_auth.provider_captured_at IS NULL THEN
            RAISE EXCEPTION
                'CAPTURED_STATE_MISSING_PROVIDER_EVIDENCE';
        END IF;
    END IF;

    v_gross := v_quote.total_fare_minor;
    v_tax := v_quote.tax_minor;

    v_platform_fee :=
        ((v_gross - v_tax) * 15) / 100;

    v_driver_earnings :=
        v_gross - v_platform_fee - v_tax;

    v_rider_acc :=
        public.mobility_resolve_ledger_account(
            v_trip.rider_id,
            'RIDER_RECEIVABLE',
            v_quote.currency_code
        );

    v_driver_acc :=
        public.mobility_resolve_ledger_account(
            v_trip.driver_id,
            'DRIVER_PAYABLE',
            v_quote.currency_code
        );

    v_platform_acc :=
        public.mobility_resolve_ledger_account(
            NULL,
            'PLATFORM_REVENUE',
            v_quote.currency_code
        );

    v_tax_acc :=
        public.mobility_resolve_ledger_account(
            NULL,
            'TAX_ESCROW',
            v_quote.currency_code
        );

    INSERT INTO public.ledger_transactions (
        reference_type,
        reference_id,
        currency_code
    ) VALUES (
        'TRIP_SETTLEMENT',
        p_trip_id,
        v_quote.currency_code
    )
    RETURNING transaction_id
    INTO v_tx_id;

    INSERT INTO public.ledger_entries (
        transaction_id,
        account_id,
        amount_minor
    ) VALUES
        (v_tx_id, v_rider_acc, v_gross),
        (v_tx_id, v_driver_acc, -v_driver_earnings),
        (v_tx_id, v_platform_acc, -v_platform_fee),
        (v_tx_id, v_tax_acc, -v_tax);

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
    )
    RETURNING *
    INTO v_settlement;

    UPDATE public.trips
    SET
        settlement_id = v_settlement.settlement_id,
        state = 'COMPLETED',
        updated_at = clock_timestamp()
    WHERE trip_id = p_trip_id;

    -- Permanently bind trip_id to authorization upon settlement
    IF v_auth.trip_id IS NULL THEN
        UPDATE public.payment_authorizations
        SET trip_id = p_trip_id
        WHERE payment_authorization_id = v_auth.payment_authorization_id;
    END IF;

    -- IMPORTANT:
    -- NO UPDATE payment_authorizations SET state='CAPTURED' HERE.

    v_response := jsonb_build_object(
        'success', TRUE,
        'conflict', FALSE,
        'settlement', row_to_json(v_settlement)
    );

    INSERT INTO public.mobility_command_receipts (
        actor_id,
        command_scope,
        idempotency_key,
        request_hash,
        requested_aggregate_id,
        aggregate_id,
        response
    ) VALUES (
        COALESCE(
            auth.uid(),
            '00000000-0000-0000-0000-000000000000'::UUID
        ),
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

REVOKE ALL
ON FUNCTION public.mobility_settle_trip(UUID, UUID, UUID, UUID)
FROM PUBLIC, anon, authenticated;

GRANT EXECUTE
ON FUNCTION public.mobility_settle_trip(UUID, UUID, UUID, UUID)
TO service_role;
