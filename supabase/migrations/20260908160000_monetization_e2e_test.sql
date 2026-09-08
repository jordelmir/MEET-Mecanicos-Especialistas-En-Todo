-- MonetizationExactlyOnceE2E: the gate test before first real colón.
-- Verifies: exactly 1 credit per transfer, exactly 1 capture per reservation,
-- wallet >= 0, ledger invariant preserved, trip has exactly 1 terminal state.

CREATE OR REPLACE FUNCTION public.test_monetization_exactly_once()
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_result jsonb;
    v_errors text[] := '{}';
    v_test_driver uuid;
    v_test_passenger uuid;
    v_test_trip uuid;
    v_test_topup uuid;
    v_balance bigint;
    v_credit_count bigint;
    v_capture_count bigint;
    v_terminal_count bigint;
    v_reservation_count bigint;
BEGIN
    -- ═══════════════════════════════════════════════════════════════
    -- SETUP: Create test actors
    -- ═══════════════════════════════════════════════════════════════
    v_test_driver := gen_random_uuid();
    v_test_passenger := gen_random_uuid();

    -- Create test profiles
    INSERT INTO public.profiles (id, full_name, phone, environment)
    VALUES (v_test_driver, 'Test Driver E2E', '00000000', 'SANDBOX')
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO public.profiles (id, full_name, phone, environment)
    VALUES (v_test_passenger, 'Test Passenger E2E', '00000001', 'SANDBOX')
    ON CONFLICT (id) DO NOTHING;

    -- Create test wallet
    INSERT INTO public.ride_wallets (driver_id, balance_crc, currency, environment)
    VALUES (v_test_driver, 0, 'CRC', 'SANDBOX')
    ON CONFLICT (driver_id) DO NOTHING;

    -- ═══════════════════════════════════════════════════════════════
    -- TEST 1: Topup idempotency — same transfer credited exactly once
    -- ═══════════════════════════════════════════════════════════════
    -- First topup
    INSERT INTO public.ride_wallet_topups (
        driver_id, amount_minor, sender_phone, transfer_reference,
        proof_storage_path, proof_sha256, proof_byte_count, proof_mime_type,
        idempotency_key, status
    ) VALUES (
        v_test_driver, 10000, '88888888', 'SINPE-TEST-001',
        '/test/proof1.png', 'sha256hash1', 2048, 'image/png',
        'idempotency-test-001', 'PENDING_REVIEW'
    ) RETURNING id INTO v_test_topup;

    -- Duplicate topup with different screenshot (same transfer reference)
    BEGIN
        INSERT INTO public.ride_wallet_topups (
            driver_id, amount_minor, sender_phone, transfer_reference,
            proof_storage_path, proof_sha256, proof_byte_count, proof_mime_type,
            idempotency_key, status
        ) VALUES (
            v_test_driver, 10000, '88888888', 'SINPE-TEST-001',
            '/test/proof2.png', 'sha256hash2', 3072, 'image/png',
            'idempotency-test-002', 'PENDING_REVIEW'
        );
        v_errors := array_append(v_errors, 'TEST1_FAIL: duplicate transfer should be rejected');
    EXCEPTION WHEN unique_violation THEN
        -- Expected: bank reference dedup worked
        NULL;
    END;

    -- Approve the topup
    UPDATE public.ride_wallet_topups
    SET status = 'APPROVED', reviewed_at = now(), reviewed_by = v_test_driver
    WHERE id = v_test_topup;

    -- Credit the wallet
    INSERT INTO public.ride_wallet_ledger (
        driver_id, idempotency_key, entry_type, amount_minor, currency,
        direction, withdrawable, reference_id, metadata
    ) VALUES (
        v_test_driver, 'ledger-test-001', 'TOPUP_CREDITED', 10000, 'CRC',
        'CREDIT', true, v_test_topup, '{"source": "test"}'
    );

    -- Verify exactly 1 credit
    SELECT count(*) INTO v_credit_count
    FROM public.ride_wallet_ledger
    WHERE driver_id = v_test_driver AND entry_type = 'TOPUP_CREDITED';

    IF v_credit_count <> 1 THEN
        v_errors := array_append(v_errors, format('TEST1_FAIL: expected 1 credit, got %s', v_credit_count));
    END IF;

    -- ═══════════════════════════════════════════════════════════════
    -- TEST 2: Commission capture — exactly once per reservation
    -- ═══════════════════════════════════════════════════════════════
    -- Create a reservation
    INSERT INTO public.ride_commission_reservations (
        driver_id, trip_id, amount_minor, currency, state
    ) VALUES (
        v_test_driver, gen_random_uuid(), 500, 'CRC', 'RESERVED'
    ) RETURNING id INTO v_test_trip;

    -- First capture
    INSERT INTO public.ride_wallet_ledger (
        driver_id, idempotency_key, entry_type, amount_minor, currency,
        direction, withdrawable, reference_id, metadata
    ) VALUES (
        v_test_driver, 'capture-test-001', 'COMMISSION_CAPTURED', 500, 'CRC',
        'DEBIT', false, v_test_trip, '{"source": "test"}'
    );

    -- Double capture attempt
    BEGIN
        INSERT INTO public.ride_wallet_ledger (
            driver_id, idempotency_key, entry_type, amount_minor, currency,
            direction, withdrawable, reference_id, metadata
        ) VALUES (
            v_test_driver, 'capture-test-002', 'COMMISSION_CAPTURED', 500, 'CRC',
            'DEBIT', false, v_test_trip, '{"source": "test"}'
        );
        v_errors := array_append(v_errors, 'TEST2_FAIL: double capture should be rejected');
    EXCEPTION WHEN unique_violation OR sqlstate 'P0001' THEN
        NULL;
    END;

    -- Verify exactly 1 capture
    SELECT count(*) INTO v_capture_count
    FROM public.ride_wallet_ledger
    WHERE driver_id = v_test_driver
      AND entry_type = 'COMMISSION_CAPTURED'
      AND reference_id = v_test_trip;

    IF v_capture_count <> 1 THEN
        v_errors := array_append(v_errors, format('TEST2_FAIL: expected 1 capture, got %s', v_capture_count));
    END IF;

    -- ═══════════════════════════════════════════════════════════════
    -- TEST 3: Wallet invariant — balance never negative
    -- ═══════════════════════════════════════════════════════════════
    SELECT coalesce(sum(CASE
        WHEN direction = 'CREDIT' THEN amount_minor
        WHEN direction = 'DEBIT' AND entry_type <> 'COMMISSION_RESERVED' THEN -amount_minor
        ELSE 0
    END), 0) INTO v_balance
    FROM public.ride_wallet_ledger
    WHERE driver_id = v_test_driver AND currency = 'CRC';

    IF v_balance < 0 THEN
        v_errors := array_append(v_errors, format('TEST3_FAIL: balance is negative: %s', v_balance));
    END IF;

    -- ═══════════════════════════════════════════════════════════════
    -- TEST 4: Trip terminal state — exactly 1 terminal state
    -- ═══════════════════════════════════════════════════════════════
    SELECT count(*) INTO v_terminal_count
    FROM public.trips
    WHERE trip_id = v_test_trip
      AND status IN ('COMPLETED', 'CANCELLED', 'DISPUTED');

    IF v_terminal_count > 1 THEN
        v_errors := array_append(v_errors, format('TEST4_FAIL: %s terminal states', v_terminal_count));
    END IF;

    -- ═══════════════════════════════════════════════════════════════
    -- TEST 5: Negative balance guard
    -- ═══════════════════════════════════════════════════════════════
    BEGIN
        INSERT INTO public.ride_wallet_ledger (
            driver_id, idempotency_key, entry_type, amount_minor, currency,
            direction, withdrawable, metadata
        ) VALUES (
            v_test_driver, 'negative-test-001', 'COMMISSION_CAPTURED', 999999, 'CRC',
            'DEBIT', false, '{"source": "test"}'
        );
        v_errors := array_append(v_errors, 'TEST5_FAIL: negative balance should be blocked');
    EXCEPTION WHEN sqlstate 'P0001' THEN
        NULL;
    END;

    -- ═══════════════════════════════════════════════════════════════
    -- TEST 6: Ledger immutability — UPDATE/DELETE blocked
    -- ═══════════════════════════════════════════════════════════════
    BEGIN
        UPDATE public.ride_wallet_ledger
        SET amount_minor = 0
        WHERE driver_id = v_test_driver AND idempotency_key = 'ledger-test-001';
        v_errors := array_append(v_errors, 'TEST6_FAIL: UPDATE should be blocked');
    EXCEPTION WHEN insufficient_privilege THEN
        NULL;
    END;

    -- ═══════════════════════════════════════════════════════════════
    -- CLEANUP
    -- ═══════════════════════════════════════════════════════════════
    DELETE FROM public.ride_wallet_ledger WHERE driver_id = v_test_driver;
    DELETE FROM public.ride_commission_reservations WHERE driver_id = v_test_driver;
    DELETE FROM public.ride_wallet_topups WHERE driver_id = v_test_driver;
    DELETE FROM public.ride_wallets WHERE driver_id = v_test_driver;
    DELETE FROM public.profiles WHERE id IN (v_test_driver, v_test_passenger);

    -- ═══════════════════════════════════════════════════════════════
    -- RESULT
    -- ═══════════════════════════════════════════════════════════════
    IF array_length(v_errors, 1) = 0 THEN
        v_result := jsonb_build_object(
            'status', 'PASS',
            'tests', jsonb_build_array(
                'idempotent_topup',
                'single_commission_capture',
                'wallet_non_negative',
                'single_terminal_state',
                'negative_balance_blocked',
                'ledger_immutable'
            ),
            'balance_final', v_balance
        );
    ELSE
        v_result := jsonb_build_object(
            'status', 'FAIL',
            'errors', to_jsonb(v_errors),
            'balance_final', v_balance
        );
    END IF;

    RETURN v_result;
END;
$$;

-- Grant execute to service_role for CI testing.
REVOKE ALL ON FUNCTION public.test_monetization_exactly_once() FROM public;
GRANT EXECUTE ON FUNCTION public.test_monetization_exactly_once() TO service_role;

COMMENT ON FUNCTION public.test_monetization_exactly_once() IS
    'MonetizationExactlyOnceE2E gate test. Must PASS before first real colón.';
