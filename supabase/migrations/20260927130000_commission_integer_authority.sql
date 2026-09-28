-- Prospective commission authority. Historical settlements and ledger entries remain immutable.
begin;
create or replace function public.elysium_commission_minor_v1(p_base_minor bigint, p_basis_points integer default 500)
returns bigint language plpgsql immutable strict set search_path = public, pg_temp as $$
begin
 if p_base_minor < 0 or p_basis_points < 0 or p_basis_points > 10000 then
  raise exception 'INVALID_COMMISSION_INPUT' using errcode='22023';
 end if;
 return floor((p_base_minor::numeric * p_basis_points + 5000) / 10000)::bigint;
end $$;
revoke all on function public.elysium_commission_minor_v1(bigint,integer) from public;
grant execute on function public.elysium_commission_minor_v1(bigint,integer) to authenticated,service_role;
CREATE OR REPLACE FUNCTION public.mobility_settle_trip(p_trip_id uuid, p_payment_authorization_id uuid, p_quote_id uuid, p_idempotency_key uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
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
        public.elysium_commission_minor_v1(v_gross - v_tax, 500);

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
$function$

commit;
