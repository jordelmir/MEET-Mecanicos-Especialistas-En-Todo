-- Capture evidence must be complete and event ownership must survive races.
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
    v_event_authorization UUID;
BEGIN
    IF COALESCE(auth.role(), current_user) <> 'service_role' AND current_user <> 'service_role' THEN
        RAISE EXCEPTION 'SERVICE_ROLE_REQUIRED' USING ERRCODE = '42501';
    END IF;

    IF NULLIF(trim(p_provider_capture_ref), '') IS NULL
       OR NULLIF(trim(p_provider_event_id), '') IS NULL THEN
        RAISE EXCEPTION 'PROVIDER_CAPTURE_EVIDENCE_REQUIRED';
    END IF;

    IF p_captured_amount_minor IS NULL OR p_captured_amount_minor <= 0
       OR p_currency_code IS NULL OR p_currency_code !~ '^[A-Z]{3}$'
       OR p_trip_id IS NULL THEN
        RAISE EXCEPTION 'PROVIDER_CAPTURE_AMOUNT_CURRENCY_TRIP_REQUIRED';
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

    -- The unique insert waits for a competing transaction. Re-read after that
    -- wait: the earlier EXISTS check alone cannot prevent concurrent reuse.
    SELECT payment_authorization_id INTO v_event_authorization
    FROM public.payment_provider_events
    WHERE provider = v_auth.provider AND provider_event_id = p_provider_event_id;
    IF v_event_authorization IS DISTINCT FROM v_auth.payment_authorization_id THEN
        RAISE EXCEPTION 'PROVIDER_EVENT_ID_ALREADY_USED_FOR_ANOTHER_PAYMENT';
    END IF;

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

-- V9 exposed the same named arguments in a different positional order.
-- Retaining it makes PostgREST resolution ambiguous and keeps optional evidence.
DROP FUNCTION IF EXISTS public.mobility_confirm_provider_capture(UUID, TEXT, TEXT, BIGINT, TEXT, JSONB, UUID);
REVOKE ALL ON FUNCTION public.mobility_confirm_provider_capture(UUID, UUID, TEXT, TEXT, BIGINT, TEXT, JSONB) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.mobility_confirm_provider_capture(UUID, UUID, TEXT, TEXT, BIGINT, TEXT, JSONB) TO service_role;
