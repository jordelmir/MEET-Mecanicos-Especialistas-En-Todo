-- Migration: 20260913210000_fix_e2e_test_v3.sql
-- Fix: E2E test v7 — uses session_replication_role to bypass FK chains during cleanup,
-- disables immutable trigger for ledger DELETE, proper METERED_TIME_DISTANCE fare_mode,
-- handles empty array_length returning NULL.

CREATE OR REPLACE FUNCTION public.test_monetization_exactly_once()
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_result jsonb;
    v_errors text[] := '{}';
    v_d uuid := 'e2e00000-0000-0000-0000-000000000001'::uuid;
    v_tenant uuid := 'e2e00000-0000-0000-0000-000000000002'::uuid;
    v_trip uuid := 'e2e00000-0000-0000-0000-000000000003'::uuid;
    v_topup uuid;
    v_balance bigint;
    v_credit_count bigint;
    v_capture_count bigint;
BEGIN
    -- Disable all FK checks and immutable triggers for cleanup
    SET session_replication_role = 'replica';
    ALTER TABLE public.ride_wallet_ledger DISABLE TRIGGER ride_wallet_ledger_immutable;

    DELETE FROM public.ride_wallet_ledger WHERE driver_id = v_d;
    DELETE FROM public.ride_commission_reservations WHERE driver_id = v_d;
    DELETE FROM public.ride_wallet_topups WHERE driver_id = v_d;
    DELETE FROM public.ride_wallets WHERE driver_id = v_d;
    DELETE FROM public.ride_requests WHERE id = v_trip;
    DELETE FROM public.ride_profiles WHERE user_id = v_d;
    DELETE FROM public.principals WHERE principal_id = v_d;
    DELETE FROM auth.users WHERE id = v_d;
    DELETE FROM public.ride_tenants WHERE id = v_tenant;

    -- Restore FK checks
    ALTER TABLE public.ride_wallet_ledger ENABLE TRIGGER ride_wallet_ledger_immutable;
    SET session_replication_role = 'origin';

    INSERT INTO auth.users (id, instance_id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at)
    VALUES (v_d, '00000000-0000-0000-0000-000000000000'::uuid, 'authenticated', 'authenticated', 'e2e-test-e901@meet.test', '', now(), now(), now())
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO public.principals (principal_id, status, principal_type, display_name, capabilities, is_active, metadata, created_at, updated_at)
    VALUES (v_d, 'ACTIVE', 'DRIVER', 'E2E Test Driver', '[]', true, '{}', now(), now())
    ON CONFLICT (principal_id) DO NOTHING;

    INSERT INTO public.ride_tenants (id, tenant_type, legal_name, display_name, country_code, default_currency, dispatch_strategy, status)
    VALUES (v_tenant, 'PLATFORM', 'E2E Test Tenant', 'Test Tenant E2E', 'CR', 'CRC', 'NEAREST_ETA', 'ACTIVE')
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO public.ride_profiles (user_id, mobility_role, country_code, preferred_currency, display_name, environment)
    VALUES (v_d, 'DRIVER', 'CR', 'CRC', 'E2E Test Driver', 'SANDBOX')
    ON CONFLICT (user_id) DO NOTHING;

    INSERT INTO public.ride_requests (
        id, tenant_id, ride_request_id, pickup_address, destination_address, state, version, quote_version,
        fare_breakdown, fare_mode, distance_rate_minor_per_km, time_rate_minor_per_minute,
        estimated_distance_meters, estimated_duration_seconds, estimated_fare_minor,
        fare_rate_card_version, allows_in_trip_stops, dispatch_expires_at, driver_rejection_count,
        route_version, environment
    ) VALUES (
        v_trip, v_tenant, gen_random_uuid(), 'E2E Pickup', 'E2E Dest', 'COMPLETED', 1, 1,
        '{}', 'METERED_TIME_DISTANCE', 300, 60, 1000, 60, 500,
        1, true, now(), 0, 1, 'SANDBOX'
    ) ON CONFLICT (id) DO NOTHING;

    INSERT INTO public.ride_wallets (driver_id, currency, environment)
    VALUES (v_d, 'CRC', 'SANDBOX')
    ON CONFLICT (driver_id) DO NOTHING;

    -- TEST 1: Idempotent topup (duplicate transfer_reference rejected)
    INSERT INTO public.ride_wallet_topups (
        driver_id, amount_minor, sender_phone, transfer_reference,
        proof_storage_path, proof_sha256, proof_byte_count, proof_mime_type,
        idempotency_key, status
    ) VALUES (
        v_d, 10000, '88888888', 'SINPE-E2E-001', '/test/proof1.png',
        'a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2',
        2048, 'image/png', 'e2e-idempotency-001', 'PENDING_REVIEW'
    ) RETURNING id INTO v_topup;

    BEGIN
        INSERT INTO public.ride_wallet_topups (
            driver_id, amount_minor, sender_phone, transfer_reference,
            proof_storage_path, proof_sha256, proof_byte_count, proof_mime_type,
            idempotency_key, status
        ) VALUES (
            v_d, 10000, '88888888', 'SINPE-E2E-001', '/test/proof2.png',
            'b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3',
            3072, 'image/png', 'e2e-idempotency-002', 'PENDING_REVIEW'
        );
        v_errors := array_append(v_errors, 'TEST1_FAIL: duplicate transfer_reference should be rejected');
    EXCEPTION WHEN unique_violation THEN NULL; END;

    INSERT INTO public.ride_wallet_ledger (
        driver_id, idempotency_key, entry_type, amount_minor, currency, direction, withdrawable, metadata
    ) VALUES (v_d, 'e2e-ledger-001', 'TOP_UP_CONFIRMED', 10000, 'CRC', 'CREDIT', true, '{"source":"e2e"}');

    SELECT count(*) INTO v_credit_count FROM public.ride_wallet_ledger
    WHERE driver_id = v_d AND entry_type = 'TOP_UP_CONFIRMED';
    IF v_credit_count <> 1 THEN
        v_errors := array_append(v_errors, format('TEST1_FAIL: expected 1 credit, got %s', v_credit_count));
    END IF;

    -- TEST 2: Single commission capture (double capture rejected)
    INSERT INTO public.ride_commission_reservations (
        trip_id, driver_id, amount_minor, currency, state, reserve_idempotency_key
    ) VALUES (v_trip, v_d, 500, 'CRC', 'RESERVED', 'e2e-reserve-001');

    INSERT INTO public.ride_wallet_ledger (
        driver_id, idempotency_key, entry_type, amount_minor, currency, direction, trip_id, withdrawable, metadata
    ) VALUES (v_d, 'e2e-capture-001', 'COMMISSION_CAPTURED', 500, 'CRC', 'DEBIT', v_trip, false, '{"source":"e2e"}');

    BEGIN
        INSERT INTO public.ride_wallet_ledger (
            driver_id, idempotency_key, entry_type, amount_minor, currency, direction, trip_id, withdrawable, metadata
        ) VALUES (v_d, 'e2e-capture-002', 'COMMISSION_CAPTURED', 500, 'CRC', 'DEBIT', v_trip, false, '{"source":"e2e"}');
        v_errors := array_append(v_errors, 'TEST2_FAIL: double capture should be rejected');
    EXCEPTION WHEN unique_violation OR sqlstate '23505' THEN NULL; END;

    SELECT count(*) INTO v_capture_count FROM public.ride_wallet_ledger
    WHERE driver_id = v_d AND entry_type = 'COMMISSION_CAPTURED' AND trip_id = v_trip;
    IF v_capture_count <> 1 THEN
        v_errors := array_append(v_errors, format('TEST2_FAIL: expected 1 capture, got %s', v_capture_count));
    END IF;

    -- TEST 3: Wallet non-negative
    SELECT coalesce(sum(CASE
        WHEN direction = 'CREDIT' THEN amount_minor
        WHEN direction = 'DEBIT' AND entry_type <> 'COMMISSION_RESERVED' THEN -amount_minor
        ELSE 0
    END), 0) INTO v_balance FROM public.ride_wallet_ledger WHERE driver_id = v_d AND currency = 'CRC';
    IF v_balance < 0 THEN
        v_errors := array_append(v_errors, format('TEST3_FAIL: negative balance: %s', v_balance));
    END IF;

    -- TEST 4: Negative balance blocked
    BEGIN
        INSERT INTO public.ride_wallet_ledger (
            driver_id, idempotency_key, entry_type, amount_minor, currency, direction, withdrawable, metadata
        ) VALUES (v_d, 'e2e-neg', 'COMMISSION_CAPTURED', 999999, 'CRC', 'DEBIT', false, '{"source":"e2e"}');
        v_errors := array_append(v_errors, 'TEST4_FAIL: negative balance should be blocked');
    EXCEPTION WHEN sqlstate 'P0001' THEN NULL; END;

    -- TEST 5: Ledger immutable
    BEGIN
        UPDATE public.ride_wallet_ledger SET amount_minor = 0
        WHERE driver_id = v_d AND idempotency_key = 'e2e-ledger-001';
        v_errors := array_append(v_errors, 'TEST5_FAIL: UPDATE should be blocked');
    EXCEPTION WHEN OTHERS THEN NULL; END;

    -- Cleanup: disable FK checks and immutable trigger, then clean
    SET session_replication_role = 'replica';
    ALTER TABLE public.ride_wallet_ledger DISABLE TRIGGER ride_wallet_ledger_immutable;
    DELETE FROM public.ride_wallet_ledger WHERE driver_id = v_d;
    DELETE FROM public.ride_commission_reservations WHERE driver_id = v_d;
    DELETE FROM public.ride_wallet_topups WHERE driver_id = v_d;
    DELETE FROM public.ride_wallets WHERE driver_id = v_d;
    DELETE FROM public.ride_requests WHERE id = v_trip;
    DELETE FROM public.ride_profiles WHERE user_id = v_d;
    DELETE FROM public.principals WHERE principal_id = v_d;
    DELETE FROM auth.users WHERE id = v_d;
    DELETE FROM public.ride_tenants WHERE id = v_tenant;
    ALTER TABLE public.ride_wallet_ledger ENABLE TRIGGER ride_wallet_ledger_immutable;
    SET session_replication_role = 'origin';

    IF array_length(v_errors, 1) IS NULL OR array_length(v_errors, 1) = 0 THEN
        v_result := jsonb_build_object('status', 'PASS',
            'tests', jsonb_build_array('idempotent_topup','single_commission_capture','wallet_non_negative','negative_balance_blocked','ledger_immutable'),
            'balance_final', v_balance);
    ELSE
        v_result := jsonb_build_object('status', 'FAIL', 'errors', to_jsonb(v_errors), 'balance_final', v_balance);
    END IF;
    RETURN v_result;
END;
$$;

REVOKE ALL ON FUNCTION public.test_monetization_exactly_once() FROM public;
GRANT EXECUTE ON FUNCTION public.test_monetization_exactly_once() TO service_role;
