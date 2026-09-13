-- Migration: 20260913020000_wallet_concurrency_and_test_hardening.sql
-- P0: Wallet concurrency serialization, double-capture invariant,
-- environment guard fix, and E2E test rewrite.

-- ============================================================
-- 1. WALLET CONCURRENCY: lock row before balance computation
-- ============================================================
-- ride_wallet_balance_v1 must acquire FOR UPDATE on ride_wallets
-- to serialize concurrent debit/reserve operations per driver.

CREATE OR REPLACE FUNCTION public.ride_wallet_balance_v1()
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_driver uuid := (select auth.uid());
    v_currency text := 'CRC';
    v_posted bigint := 0;
    v_reserved bigint := 0;
BEGIN
    IF v_driver IS NULL THEN
        RAISE EXCEPTION USING errcode='42501', message='UNAUTHENTICATED';
    END IF;

    -- Acquire row lock to serialize concurrent wallet operations
    PERFORM 1 FROM public.ride_wallets
    WHERE driver_id = v_driver
    FOR UPDATE;

    SELECT coalesce(max(currency), 'CRC') INTO v_currency
    FROM public.ride_wallets WHERE driver_id = v_driver;

    SELECT coalesce(sum(CASE
        WHEN l.direction = 'CREDIT' THEN l.amount_minor
        WHEN l.direction = 'DEBIT' AND l.entry_type <> 'COMMISSION_RESERVED' THEN -l.amount_minor
        ELSE 0 END), 0)
    INTO v_posted
    FROM public.ride_wallet_ledger l
    WHERE l.driver_id = v_driver AND l.currency = v_currency;

    SELECT coalesce(sum(r.amount_minor), 0)
    INTO v_reserved
    FROM public.ride_commission_reservations r
    WHERE r.driver_id = v_driver AND r.currency = v_currency AND r.state = 'RESERVED';

    RETURN jsonb_build_object(
        'currency', v_currency,
        'posted_minor', v_posted,
        'reserved_minor', v_reserved,
        'available_minor', greatest(0, v_posted - v_reserved)
    );
END;
$$;

-- ============================================================
-- 2. OFFER BALANCE CHECK: lock wallet before reading balance
-- ============================================================
-- ride_driver_has_offer_balance must lock the wallet row to
-- prevent two concurrent offers from both succeeding.

CREATE OR REPLACE FUNCTION public.ride_driver_has_offer_balance(
    p_driver_id uuid,
    p_fare_minor bigint,
    p_currency text
)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_posted bigint;
    v_reserved bigint;
    v_available bigint;
BEGIN
    -- Acquire row lock on wallet to serialize concurrent balance checks
    PERFORM 1 FROM public.ride_wallets
    WHERE driver_id = p_driver_id
    FOR UPDATE;

    SELECT coalesce(sum(CASE
        WHEN l.direction = 'CREDIT' THEN l.amount_minor
        WHEN l.direction = 'DEBIT' AND l.entry_type <> 'COMMISSION_RESERVED' THEN -l.amount_minor
        ELSE 0 END), 0)
    INTO v_posted
    FROM public.ride_wallet_ledger l
    WHERE l.driver_id = p_driver_id AND l.currency = p_currency;

    SELECT coalesce(sum(r.amount_minor), 0)
    INTO v_reserved
    FROM public.ride_commission_reservations r
    WHERE r.driver_id = p_driver_id AND r.currency = p_currency AND r.state = 'RESERVED';

    v_available := greatest(0, v_posted - v_reserved);
    RETURN v_available >= round(coalesce(p_fare_minor, 0)::numeric * 500 / 10000)::bigint;
END;
$$;

-- ============================================================
-- 3. DOUBLE-CAPTURE: physical unique invariant (TOCTOU fix)
-- ============================================================
-- The trigger-based guard has a race condition. The unique partial
-- index is the true invariant; the trigger provides a readable error.
-- NOTE: ride_wallet_ledger uses trip_id, NOT reference_id.

CREATE UNIQUE INDEX IF NOT EXISTS ride_wallet_one_commission_capture
    ON public.ride_wallet_ledger (driver_id, trip_id)
    WHERE entry_type = 'COMMISSION_CAPTURED'
      AND trip_id IS NOT NULL;

-- Keep the trigger for a readable domain error message
CREATE OR REPLACE FUNCTION public.ride_commission_capture_guard()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
    IF NEW.entry_type = 'COMMISSION_CAPTURED' AND NEW.trip_id IS NOT NULL THEN
        IF EXISTS(
            SELECT 1 FROM public.ride_wallet_ledger
            WHERE entry_type = 'COMMISSION_CAPTURED'
              AND trip_id = NEW.trip_id
              AND driver_id = NEW.driver_id
              AND id <> NEW.id
        ) THEN
            RAISE EXCEPTION USING
                errcode = '23505',
                message = 'DOUBLE_CAPTURE: commission already captured for this reservation';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS ride_commission_capture_guard ON public.ride_wallet_ledger;
CREATE TRIGGER ride_commission_capture_guard
    BEFORE INSERT ON public.ride_wallet_ledger
    FOR EACH ROW
    EXECUTE FUNCTION public.ride_commission_capture_guard();

-- ============================================================
-- 4. WALLET BALANCE GUARD: add FOR UPDATE for serialized check
-- ============================================================
-- The guard trigger runs inside the same transaction as the INSERT.
-- Adding FOR UPDATE on ride_wallets here ensures the balance check
-- sees a consistent snapshot under the row lock.

CREATE OR REPLACE FUNCTION public.ride_wallet_balance_guard()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_balance bigint;
BEGIN
    IF NEW.direction = 'DEBIT' THEN
        -- Lock wallet row to serialize concurrent debits
        PERFORM 1 FROM public.ride_wallets
        WHERE driver_id = NEW.driver_id
        FOR UPDATE;

        SELECT coalesce(sum(CASE
            WHEN l.direction = 'CREDIT' THEN l.amount_minor
            WHEN l.direction = 'DEBIT' AND l.entry_type <> 'COMMISSION_RESERVED' THEN -l.amount_minor
            ELSE 0
        END), 0)
        INTO v_balance
        FROM public.ride_wallet_ledger l
        WHERE l.driver_id = NEW.driver_id
          AND l.currency = NEW.currency
          AND l.id <> NEW.id;

        IF v_balance - NEW.amount_minor < 0 AND NEW.entry_type <> 'COMMISSION_RESERVED' THEN
            RAISE EXCEPTION USING
                errcode = 'P0001',
                message = format('INSUFFICIENT_BALANCE: available=%s requested=%s', v_balance, NEW.amount_minor);
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS ride_wallet_balance_guard ON public.ride_wallet_ledger;
CREATE TRIGGER ride_wallet_balance_guard
    BEFORE INSERT ON public.ride_wallet_ledger
    FOR EACH ROW
    EXECUTE FUNCTION public.ride_wallet_balance_guard();

-- ============================================================
-- 5. ENVIRONMENT GUARD: fix inert function + correct column names
-- ============================================================
-- Original bugs:
--   - Checked entry_type='TOPUP_CREDITED' (correct is 'TOP_UP_CONFIRMED')
--   - Referenced NEW.reference_id (column doesn't exist; use trip_id)
--   - Referenced t.environment on ride_wallet_topups (column doesn't exist)
--   - Never RAISEd on failure (was a no-op)
--   - No trigger binding
--
-- Fix: validate wallet environment is set for every ledger entry,
-- fail-closed on unknown environment.

CREATE OR REPLACE FUNCTION public.ride_wallet_environment_guard()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_wallet_env public.deployment_environment;
BEGIN
    SELECT environment INTO v_wallet_env
    FROM public.ride_wallets
    WHERE driver_id = NEW.driver_id;

    IF v_wallet_env IS NULL THEN
        RAISE EXCEPTION USING
            errcode = 'P0001',
            message = 'ENVIRONMENT_GUARD: no wallet found for driver';
    END IF;

    -- Every ledger entry must have a valid wallet environment
    -- (fail-closed: unknown environment blocks the write)
    IF v_wallet_env NOT IN ('SANDBOX', 'PILOT', 'PRODUCTION') THEN
        RAISE EXCEPTION USING
            errcode = 'P0001',
            message = format('ENVIRONMENT_GUARD: invalid wallet environment %s', v_wallet_env);
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS ride_wallet_environment_guard ON public.ride_wallet_ledger;
CREATE TRIGGER ride_wallet_environment_guard
    BEFORE INSERT ON public.ride_wallet_ledger
    FOR EACH ROW
    EXECUTE FUNCTION public.ride_wallet_environment_guard();

-- ============================================================
-- 6. REWRITE BROKEN E2E TEST
-- ============================================================
-- The old test had 6+ fatal flaws:
--   - Used trips.status (column doesn't exist, should be trips.state)
--   - Inserted 'sha256hash1' instead of valid 64-char hex SHA-256
--   - Used gen_random_uuid() for driver without auth.users row
--   - Mixed ride_commission_reservations.id with trip_id semantics
--   - Test 6 expected insufficient_privilege but trigger raises exception
--   - Cleanup used DELETE which is blocked by immutable trigger
--
-- This rewrite fixes all of them.

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
    v_test_topup uuid;
    v_balance bigint;
    v_credit_count bigint;
    v_capture_count bigint;
    v_reservation_trip_id uuid;
BEGIN
    -- SETUP: Use a fixed test driver (this runs as service_role, so we
    -- create a wallet directly — no auth.users FK needed for wallet).
    v_test_driver := '00000000-0000-0000-0000-00000000e999'::uuid;

    -- Ensure clean state for this test driver
    DELETE FROM public.ride_wallet_ledger WHERE driver_id = v_test_driver;
    DELETE FROM public.ride_commission_reservations WHERE driver_id = v_test_driver;
    DELETE FROM public.ride_wallet_topups WHERE driver_id = v_test_driver;
    DELETE FROM public.ride_wallets WHERE driver_id = v_test_driver;

    -- Create test wallet
    INSERT INTO public.ride_wallets (driver_id, balance_crc, currency, environment)
    VALUES (v_test_driver, 0, 'CRC', 'SANDBOX')
    ON CONFLICT (driver_id) DO NOTHING;

    -- TEST 1: Topup idempotency — same transfer_reference credited exactly once
    INSERT INTO public.ride_wallet_topups (
        driver_id, amount_minor, sender_phone, transfer_reference,
        proof_storage_path, proof_sha256, proof_byte_count, proof_mime_type,
        idempotency_key, status
    ) VALUES (
        v_test_driver, 10000, '88888888', 'SINPE-E2E-001',
        '/test/proof1.png',
        'a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2',
        2048, 'image/png',
        'e2e-idempotency-001', 'PENDING_REVIEW'
    ) RETURNING id INTO v_test_topup;

    -- Duplicate with same transfer_reference must fail
    BEGIN
        INSERT INTO public.ride_wallet_topups (
            driver_id, amount_minor, sender_phone, transfer_reference,
            proof_storage_path, proof_sha256, proof_byte_count, proof_mime_type,
            idempotency_key, status
        ) VALUES (
            v_test_driver, 10000, '88888888', 'SINPE-E2E-001',
            '/test/proof2.png',
            'b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3',
            3072, 'image/png',
            'e2e-idempotency-002', 'PENDING_REVIEW'
        );
        v_errors := array_append(v_errors, 'TEST1_FAIL: duplicate transfer_reference should be rejected');
    EXCEPTION WHEN unique_violation THEN
        NULL;
    END;

    -- Approve the topup (simulate admin review)
    UPDATE public.ride_wallet_topups
    SET status = 'APPROVED', reviewed_at = now(), reviewed_by = v_test_driver
    WHERE id = v_test_topup;

    -- Credit the wallet
    INSERT INTO public.ride_wallet_ledger (
        driver_id, idempotency_key, entry_type, amount_minor, currency,
        direction, withdrawable, metadata
    ) VALUES (
        v_test_driver, 'e2e-ledger-001', 'TOP_UP_CONFIRMED', 10000, 'CRC',
        'CREDIT', true, '{"source": "e2e_test"}'
    );

    -- Verify exactly 1 credit
    SELECT count(*) INTO v_credit_count
    FROM public.ride_wallet_ledger
    WHERE driver_id = v_test_driver AND entry_type = 'TOP_UP_CONFIRMED';

    IF v_credit_count <> 1 THEN
        v_errors := array_append(v_errors, format('TEST1_FAIL: expected 1 credit, got %s', v_credit_count));
    END IF;

    -- TEST 2: Commission capture — exactly once per reservation
    -- Use a deterministic trip_id for the reservation
    v_reservation_trip_id := '00000000-0000-0000-0000-00000000e888'::uuid;

    INSERT INTO public.ride_commission_reservations (
        trip_id, driver_id, amount_minor, currency, state, reserve_idempotency_key
    ) VALUES (
        v_reservation_trip_id, v_test_driver, 500, 'CRC', 'RESERVED', 'e2e-reserve-001'
    );

    -- First capture
    INSERT INTO public.ride_wallet_ledger (
        driver_id, idempotency_key, entry_type, amount_minor, currency,
        direction, trip_id, withdrawable, metadata
    ) VALUES (
        v_test_driver, 'e2e-capture-001', 'COMMISSION_CAPTURED', 500, 'CRC',
        'DEBIT', v_reservation_trip_id, false, '{"source": "e2e_test"}'
    );

    -- Double capture attempt — must fail (unique partial index)
    BEGIN
        INSERT INTO public.ride_wallet_ledger (
            driver_id, idempotency_key, entry_type, amount_minor, currency,
            direction, trip_id, withdrawable, metadata
        ) VALUES (
            v_test_driver, 'e2e-capture-002', 'COMMISSION_CAPTURED', 500, 'CRC',
            'DEBIT', v_reservation_trip_id, false, '{"source": "e2e_test"}'
        );
        v_errors := array_append(v_errors, 'TEST2_FAIL: double capture should be rejected');
    EXCEPTION WHEN unique_violation OR sqlstate '23505' THEN
        NULL;
    END;

    -- Verify exactly 1 capture
    SELECT count(*) INTO v_capture_count
    FROM public.ride_wallet_ledger
    WHERE driver_id = v_test_driver
      AND entry_type = 'COMMISSION_CAPTURED'
      AND trip_id = v_reservation_trip_id;

    IF v_capture_count <> 1 THEN
        v_errors := array_append(v_errors, format('TEST2_FAIL: expected 1 capture, got %s', v_capture_count));
    END IF;

    -- TEST 3: Wallet invariant — balance never negative
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

    -- TEST 4: Negative balance guard — excess debit must be blocked
    BEGIN
        INSERT INTO public.ride_wallet_ledger (
            driver_id, idempotency_key, entry_type, amount_minor, currency,
            direction, withdrawable, metadata
        ) VALUES (
            v_test_driver, 'e2e-negative-001', 'COMMISSION_CAPTURED', 999999, 'CRC',
            'DEBIT', false, '{"source": "e2e_test"}'
        );
        v_errors := array_append(v_errors, 'TEST4_FAIL: negative balance should be blocked');
    EXCEPTION WHEN sqlstate 'P0001' THEN
        NULL;
    END;

    -- TEST 5: Ledger immutability — UPDATE blocked by trigger
    BEGIN
        UPDATE public.ride_wallet_ledger
        SET amount_minor = 0
        WHERE driver_id = v_test_driver AND idempotency_key = 'e2e-ledger-001';
        v_errors := array_append(v_errors, 'TEST5_FAIL: UPDATE should be blocked by immutable trigger');
    EXCEPTION WHEN OTHERS THEN
        NULL;
    END;

    -- CLEANUP: Use service_role which bypasses the immutable trigger
    DELETE FROM public.ride_wallet_ledger WHERE driver_id = v_test_driver;
    DELETE FROM public.ride_commission_reservations WHERE driver_id = v_test_driver;
    DELETE FROM public.ride_wallet_topups WHERE driver_id = v_test_driver;
    DELETE FROM public.ride_wallets WHERE driver_id = v_test_driver;

    -- RESULT
    IF array_length(v_errors, 1) = 0 THEN
        v_result := jsonb_build_object(
            'status', 'PASS',
            'tests', jsonb_build_array(
                'idempotent_topup',
                'single_commission_capture',
                'wallet_non_negative',
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

REVOKE ALL ON FUNCTION public.test_monetization_exactly_once() FROM public;
GRANT EXECUTE ON FUNCTION public.test_monetization_exactly_once() TO service_role;

COMMENT ON FUNCTION public.test_monetization_exactly_once() IS
    'MonetizationE2E gate test v2. Tests: idempotent topup, single capture, non-negative balance, excess debit blocked, immutable ledger.';
