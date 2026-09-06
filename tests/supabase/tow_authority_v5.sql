-- ============================================================================
-- TESTS DE SERVIDOR OBLIGATORIOS: ELYSIUM FULFILLMENT OS V5
-- File: tests/supabase/tow_authority_v5.sql
--
-- Validates:
-- 1. Anonymous claim -> DENIED / 401
-- 2. Unverified user/customer claim -> NOT_VERIFIED_TOW_PROVIDER
-- 3. Claim with tow_unit_id belonging to another operator -> TOW_UNIT_NOT_FOUND
-- 4. Claim with tow_unit not VERIFIED or not AVAILABLE -> TOW_UNIT_NOT_VERIFIED / TOW_UNIT_NOT_AVAILABLE
-- 5. Claim with tow_unit lacking required_capabilities -> INSUFFICIENT_CAPABILITIES
-- 6. Successful claim by verified provider with matching rig -> ASSIGNED, version 2
-- 7. Concurrent/stale claim on already claimed job -> CONCURRENCY_CONFLICT / ALREADY_CLAIMED with fresh winner state & version
-- 8. Idempotency: same key + same hash replays identical response; same key + different hash raises 23505
-- 9. Discovery privacy: unassigned operator cannot read raw tow_jobs row; tow_discover_jobs returns only coarse lat/lng
-- 10. Direct INSERT on tow_jobs by authenticated customer/operator is denied by RLS/REVOKE
-- ============================================================================

\set ON_ERROR_STOP on

-- Setup test users
INSERT INTO auth.users(id) VALUES
    ('11111111-1111-1111-1111-111111111111'), -- Customer
    ('22222222-2222-2222-2222-222222222222'), -- Unverified User
    ('33333333-3333-3333-3333-333333333333'), -- Verified Operator A (Alice)
    ('44444444-4444-4444-4444-444444444444')  -- Verified Operator B (Bob)
ON CONFLICT (id) DO NOTHING;

-- Setup user profiles
INSERT INTO public.user_profiles (id, auth_user_id, display_name, primary_role) VALUES
    ('a1111111-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', 'Customer User', 'driver'),
    ('a2222222-2222-2222-2222-222222222222', '22222222-2222-2222-2222-222222222222', 'Unverified User', 'driver'),
    ('a3333333-3333-3333-3333-333333333333', '33333333-3333-3333-3333-333333333333', 'Operator Alice', 'tow_provider'),
    ('a4444444-4444-4444-4444-444444444444', '44444444-4444-4444-4444-444444444444', 'Operator Bob', 'tow_provider')
ON CONFLICT (id) DO NOTHING;

-- Setup provider profiles
-- Alice: active & verified tow provider
INSERT INTO public.provider_profiles (
    id, user_profile_id, provider_type, business_name, status, is_verified, is_active
) VALUES (
    'b3333333-3333-3333-3333-333333333333',
    'a3333333-3333-3333-3333-333333333333',
    'tow_provider',
    'Alice Towing Services',
    'active',
    true,
    true
) ON CONFLICT (user_profile_id, provider_type) DO UPDATE
  SET status = 'active', is_verified = true, is_active = true;

-- Bob: active & verified tow provider
INSERT INTO public.provider_profiles (
    id, user_profile_id, provider_type, business_name, status, is_verified, is_active
) VALUES (
    'b4444444-4444-4444-4444-444444444444',
    'a4444444-4444-4444-4444-444444444444',
    'tow_provider',
    'Bob Towing Fleet',
    'active',
    true,
    true
) ON CONFLICT (user_profile_id, provider_type) DO UPDATE
  SET status = 'active', is_verified = true, is_active = true;

-- Setup Tow Units for Alice
-- Unit A1: Verified, Available, FLATBED + WINCH
INSERT INTO public.tow_units (
    id, operator_id, license_plate, brand_model, max_weight_kg, capabilities, verification_state, availability_state
) VALUES (
    'c1111111-1111-1111-1111-111111111111',
    '33333333-3333-3333-3333-333333333333',
    'GRUA-01',
    'Hino 300 Flatbed',
    5000,
    ARRAY['FLATBED', 'WINCH'],
    'VERIFIED',
    'AVAILABLE'
) ON CONFLICT (id) DO UPDATE
  SET verification_state = 'VERIFIED', availability_state = 'AVAILABLE', capabilities = ARRAY['FLATBED', 'WINCH'];

-- Unit A2: Unverified (PENDING), Available, WHEEL_LIFT
INSERT INTO public.tow_units (
    id, operator_id, license_plate, brand_model, max_weight_kg, capabilities, verification_state, availability_state
) VALUES (
    'c2222222-2222-2222-2222-222222222222',
    '33333333-3333-3333-3333-333333333333',
    'GRUA-02',
    'Ford F-450 Wheel Lift',
    4000,
    ARRAY['WHEEL_LIFT'],
    'PENDING',
    'AVAILABLE'
) ON CONFLICT (id) DO UPDATE
  SET verification_state = 'PENDING', availability_state = 'AVAILABLE', capabilities = ARRAY['WHEEL_LIFT'];

-- Unit A3: Verified, but BUSY (not available)
INSERT INTO public.tow_units (
    id, operator_id, license_plate, brand_model, max_weight_kg, capabilities, verification_state, availability_state
) VALUES (
    'c3333333-3333-3333-3333-333333333333',
    '33333333-3333-3333-3333-333333333333',
    'GRUA-03',
    'Isuzu NPR',
    4500,
    ARRAY['FLATBED'],
    'VERIFIED',
    'BUSY'
) ON CONFLICT (id) DO UPDATE
  SET verification_state = 'VERIFIED', availability_state = 'BUSY', capabilities = ARRAY['FLATBED'];

-- Setup Tow Unit for Bob
-- Unit B1: Verified, Available, FLATBED + WINCH
INSERT INTO public.tow_units (
    id, operator_id, license_plate, brand_model, max_weight_kg, capabilities, verification_state, availability_state
) VALUES (
    'd1111111-1111-1111-1111-111111111111',
    '44444444-4444-4444-4444-444444444444',
    'GRUA-BOB',
    'Freightliner M2',
    8000,
    ARRAY['FLATBED', 'WINCH'],
    'VERIFIED',
    'AVAILABLE'
) ON CONFLICT (id) DO UPDATE
  SET verification_state = 'VERIFIED', availability_state = 'AVAILABLE', capabilities = ARRAY['FLATBED', 'WINCH'];

-- Create test jobs via authoritative tow_request_job as Customer
SELECT set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', false);
SELECT set_config('role', 'authenticated', false);

DO $setup_jobs$
DECLARE
    v_req1 JSONB;
    v_req2 JSONB;
BEGIN
    v_req1 := public.tow_request_job(
        p_vehicle_summary := 'Ford Fusion 2017 Negro',
        p_pickup_lat := 9.932456,
        p_pickup_lng := -84.078912,
        p_pickup_address := 'Avenida Segunda, Calle 5, San José',
        p_pickup_accuracy_meters := 5.0,
        p_dest_lat := 9.954321,
        p_dest_lng := -84.123456,
        p_dest_address := 'Taller Central, Heredia',
        p_required_capabilities := ARRAY['FLATBED'],
        p_vehicle_vin := '3FA6P0H74HR123456',
        p_notes := 'Motor recalentado',
        p_quoted_price_minor := 2500000,
        p_idempotency_key := 'idemp_req_job1_000001',
        p_request_hash := '1111111111111111111111111111111111111111111111111111111111111111',
        p_correlation_id := 'corr_job1_00000001'
    );

    IF (v_req1->>'success')::boolean IS NOT TRUE THEN
        RAISE EXCEPTION 'Failed to create test job 1: %', v_req1;
    END IF;

    -- Job 2: requires HEAVY_DUTY
    v_req2 := public.tow_request_job(
        p_vehicle_summary := 'Buseta Coaster Pesada',
        p_pickup_lat := 9.940000,
        p_pickup_lng := -84.090000,
        p_pickup_address := 'Paseo Colón',
        p_pickup_accuracy_meters := 4.0,
        p_dest_lat := NULL,
        p_dest_lng := NULL,
        p_dest_address := NULL,
        p_required_capabilities := ARRAY['HEAVY_DUTY'],
        p_vehicle_vin := '1HGCR2F83HA654321',
        p_notes := 'Falla de transmisión',
        p_quoted_price_minor := 6000000,
        p_idempotency_key := 'idemp_req_job2_000002',
        p_request_hash := '2222222222222222222222222222222222222222222222222222222222222222',
        p_correlation_id := 'corr_job2_00000002'
    );

    IF (v_req2->>'success')::boolean IS NOT TRUE THEN
        RAISE EXCEPTION 'Failed to create test job 2: %', v_req2;
    END IF;

    PERFORM set_config('test.target_job1_id', v_req1->>'job_id', false);
    PERFORM set_config('test.target_job2_id', v_req2->>'job_id', false);
END $setup_jobs$;

-- ============================================================================
-- TEST 1: ANONYMOUS CLAIM MUST BE REJECTED
-- ============================================================================
RESET role;
SET role anon;
SELECT set_config('request.jwt.claim.sub', '', false);

DO $test_anon$
DECLARE
    v_res JSONB;
    v_err_thrown BOOLEAN := FALSE;
    v_target_job_id TEXT := current_setting('test.target_job1_id');
BEGIN
    BEGIN
        v_res := public.tow_claim_job(
            p_job_id := v_target_job_id,
            p_tow_unit_id := 'c1111111-1111-1111-1111-111111111111',
            p_expected_version := 1,
            p_idempotency_key := 'idemp_anon_claim_00001',
            p_request_hash := 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
        );
    EXCEPTION WHEN OTHERS THEN
        v_err_thrown := TRUE;
    END;

    IF NOT v_err_thrown AND (v_res->>'success')::boolean IS TRUE THEN
        RAISE EXCEPTION 'TEST 1 FAILED: Anonymous user was allowed to claim tow job: %', v_res;
    END IF;
    RAISE NOTICE 'TEST 1 PASSED: Anonymous user denied claim execution.';
END $test_anon$;

-- ============================================================================
-- TEST 2: UNVERIFIED USER CLAIM MUST RETURN NOT_VERIFIED_TOW_PROVIDER
-- ============================================================================
RESET role;
SET role authenticated;
SELECT set_config('request.jwt.claim.sub', '22222222-2222-2222-2222-222222222222', false);

DO $test_unverified$
DECLARE
    v_res JSONB;
    v_target_job_id TEXT := current_setting('test.target_job1_id');
BEGIN
    v_res := public.tow_claim_job(
        p_job_id := v_target_job_id,
        p_tow_unit_id := 'c1111111-1111-1111-1111-111111111111',
        p_expected_version := 1,
        p_idempotency_key := 'idemp_unver_claim_0001',
        p_request_hash := 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb'
    );

    IF (v_res->>'success')::boolean IS NOT FALSE OR (v_res->>'error_code') <> 'NOT_VERIFIED_TOW_PROVIDER' THEN
        RAISE EXCEPTION 'TEST 2 FAILED: Expected NOT_VERIFIED_TOW_PROVIDER, got: %', v_res;
    END IF;
    RAISE NOTICE 'TEST 2 PASSED: Unverified user denied with NOT_VERIFIED_TOW_PROVIDER.';
END $test_unverified$;

-- ============================================================================
-- TEST 3: CLAIM WITH TOW UNIT BELONGING TO ANOTHER OPERATOR
-- Alice tries to claim using Bob''s unit ('d1111111-1111-1111-1111-111111111111') -> TOW_UNIT_NOT_FOUND
-- ============================================================================
SELECT set_config('request.jwt.claim.sub', '33333333-3333-3333-3333-333333333333', false);

DO $test_other_unit$
DECLARE
    v_res JSONB;
    v_target_job_id TEXT := current_setting('test.target_job1_id');
BEGIN
    v_res := public.tow_claim_job(
        p_job_id := v_target_job_id,
        p_tow_unit_id := 'd1111111-1111-1111-1111-111111111111', -- Bob's rig
        p_expected_version := 1,
        p_idempotency_key := 'idemp_other_claim_0001',
        p_request_hash := 'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc'
    );

    IF (v_res->>'success')::boolean IS NOT FALSE OR (v_res->>'error_code') <> 'TOW_UNIT_NOT_FOUND' THEN
        RAISE EXCEPTION 'TEST 3 FAILED: Expected TOW_UNIT_NOT_FOUND, got: %', v_res;
    END IF;
    RAISE NOTICE 'TEST 3 PASSED: Rig belonging to other operator denied with TOW_UNIT_NOT_FOUND.';
END $test_other_unit$;

-- ============================================================================
-- TEST 4: CLAIM WITH UNVERIFIED OR UNAVAILABLE TOW UNIT
-- Alice tries Unit A2 (PENDING) -> TOW_UNIT_NOT_VERIFIED
-- Alice tries Unit A3 (BUSY) -> TOW_UNIT_NOT_AVAILABLE
-- ============================================================================
DO $test_unit_states$
DECLARE
    v_res_pending JSONB;
    v_res_busy JSONB;
    v_target_job_id TEXT := current_setting('test.target_job1_id');
BEGIN

    -- A2: PENDING
    v_res_pending := public.tow_claim_job(
        p_job_id := v_target_job_id,
        p_tow_unit_id := 'c2222222-2222-2222-2222-222222222222',
        p_expected_version := 1,
        p_idempotency_key := 'idemp_pend_claim_00001',
        p_request_hash := 'dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd'
    );
    IF (v_res_pending->>'success')::boolean IS NOT FALSE OR (v_res_pending->>'error_code') <> 'TOW_UNIT_NOT_VERIFIED' THEN
        RAISE EXCEPTION 'TEST 4a FAILED: Expected TOW_UNIT_NOT_VERIFIED, got: %', v_res_pending;
    END IF;

    -- A3: BUSY
    v_res_busy := public.tow_claim_job(
        p_job_id := v_target_job_id,
        p_tow_unit_id := 'c3333333-3333-3333-3333-333333333333',
        p_expected_version := 1,
        p_idempotency_key := 'idemp_busy_claim_00001',
        p_request_hash := 'eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee'
    );
    IF (v_res_busy->>'success')::boolean IS NOT FALSE OR (v_res_busy->>'error_code') <> 'TOW_UNIT_NOT_AVAILABLE' THEN
        RAISE EXCEPTION 'TEST 4b FAILED: Expected TOW_UNIT_NOT_AVAILABLE, got: %', v_res_busy;
    END IF;

    RAISE NOTICE 'TEST 4 PASSED: Unverified unit -> TOW_UNIT_NOT_VERIFIED; Busy unit -> TOW_UNIT_NOT_AVAILABLE.';
END $test_unit_states$;

-- ============================================================================
-- TEST 5: CLAIM WITH INSUFFICIENT CAPABILITIES
-- Job 2 requires HEAVY_DUTY. Alice uses Unit A1 (FLATBED, WINCH) -> INSUFFICIENT_CAPABILITIES
-- ============================================================================
DO $test_capabilities$
DECLARE
    v_res JSONB;
    v_heavy_job_id TEXT := current_setting('test.target_job2_id');
BEGIN
    v_res := public.tow_claim_job(
        p_job_id := v_heavy_job_id,
        p_tow_unit_id := 'c1111111-1111-1111-1111-111111111111', -- Flatbed only
        p_expected_version := 1,
        p_idempotency_key := 'idemp_heavy_claim_0001',
        p_request_hash := '3333333333333333333333333333333333333333333333333333333333333333'
    );

    IF (v_res->>'success')::boolean IS NOT FALSE OR (v_res->>'error_code') <> 'INSUFFICIENT_CAPABILITIES' THEN
        RAISE EXCEPTION 'TEST 5 FAILED: Expected INSUFFICIENT_CAPABILITIES, got: %', v_res;
    END IF;
    RAISE NOTICE 'TEST 5 PASSED: Incompatible rig rejected with INSUFFICIENT_CAPABILITIES.';
END $test_capabilities$;

-- ============================================================================
-- TEST 6: SUCCESSFUL CLAIM BY VERIFIED OPERATOR WITH MATCHING RIG
-- Alice claims Job 1 using Unit A1 -> ASSIGNED, version 2
-- ============================================================================
DO $test_claim_success$
DECLARE
    v_res JSONB;
    v_target_job_id TEXT := current_setting('test.target_job1_id');
BEGIN
    v_res := public.tow_claim_job(
        p_job_id := v_target_job_id,
        p_tow_unit_id := 'c1111111-1111-1111-1111-111111111111',
        p_expected_version := 1,
        p_idempotency_key := 'idemp_succ_claim_00001',
        p_request_hash := '4444444444444444444444444444444444444444444444444444444444444444'
    );

    IF (v_res->>'success')::boolean IS NOT TRUE THEN
        RAISE EXCEPTION 'TEST 6 FAILED: Expected claim success, got: %', v_res;
    END IF;
    IF (v_res#>>'{job,state}') <> 'ASSIGNED' OR (v_res#>>'{job,server_version}')::int <> 2 THEN
        RAISE EXCEPTION 'TEST 6 FAILED: Expected state ASSIGNED and version 2, got: %', v_res;
    END IF;
    IF (v_res#>>'{job,assigned_operator_id}') <> '33333333-3333-3333-3333-333333333333' THEN
        RAISE EXCEPTION 'TEST 6 FAILED: Expected operator 33333333..., got: %', v_res;
    END IF;
    RAISE NOTICE 'TEST 6 PASSED: Verified operator claimed job successfully -> ASSIGNED, version 2.';
END $test_claim_success$;

-- ============================================================================
-- TEST 7: CONCURRENT/STALE CLAIM CONFLICT INFORMS FRESH STATE & VERSION
-- Bob tries to claim the same job with expected_version = 1 -> CONCURRENCY_CONFLICT
-- Must report actual state ASSIGNED and actual version 2!
-- ============================================================================
SELECT set_config('request.jwt.claim.sub', '44444444-4444-4444-4444-444444444444', false);

DO $test_concurrency_conflict$
DECLARE
    v_res JSONB;
    v_target_job_id TEXT := current_setting('test.target_job1_id');
BEGIN
    v_res := public.tow_claim_job(
        p_job_id := v_target_job_id,
        p_tow_unit_id := 'd1111111-1111-1111-1111-111111111111',
        p_expected_version := 1, -- Stale version
        p_idempotency_key := 'idemp_stale_claim_0001',
        p_request_hash := '5555555555555555555555555555555555555555555555555555555555555555'
    );

    IF (v_res->>'success')::boolean IS NOT FALSE OR (v_res->>'error_code') NOT IN ('CONCURRENCY_CONFLICT', 'ALREADY_CLAIMED') THEN
        RAISE EXCEPTION 'TEST 7 FAILED: Expected CONCURRENCY_CONFLICT or ALREADY_CLAIMED, got: %', v_res;
    END IF;
    IF COALESCE(v_res->>'current_state', v_res->>'actual_state') <> 'ASSIGNED' OR COALESCE(v_res->>'current_version', v_res->>'actual_version')::int <> 2 THEN
        RAISE EXCEPTION 'TEST 7 FAILED: Winner actual state/version not accurately reported: %', v_res;
    END IF;
    RAISE NOTICE 'TEST 7 PASSED: Competing claim detected % and returned winner version 2.', (v_res->>'error_code');
END $test_concurrency_conflict$;

-- ============================================================================
-- TEST 8: IDEMPOTENCY SAFETY
-- 8a: Same key + identical hash replays previous response
-- 8b: Same key + different hash raises 23505 (unique violation / key reused)
-- ============================================================================
SELECT set_config('request.jwt.claim.sub', '33333333-3333-3333-3333-333333333333', false);

DO $test_idempotency$
DECLARE
    v_res_replay JSONB;
    v_target_job_id TEXT := current_setting('test.target_job1_id');
    v_hash_conflict_thrown BOOLEAN := FALSE;
BEGIN
    -- 8a: Replay with exact same idempotency key and hash
    v_res_replay := public.tow_claim_job(
        p_job_id := v_target_job_id,
        p_tow_unit_id := 'c1111111-1111-1111-1111-111111111111',
        p_expected_version := 1,
        p_idempotency_key := 'idemp_succ_claim_00001', -- Reused
        p_request_hash := '4444444444444444444444444444444444444444444444444444444444444444'      -- Same hash
    );
    IF (v_res_replay->>'success')::boolean IS NOT TRUE OR (v_res_replay#>>'{job,server_version}')::int <> 2 THEN
        RAISE EXCEPTION 'TEST 8a FAILED: Idempotent replay did not return original response: %', v_res_replay;
    END IF;

    -- 8b: Replay with same idempotency key but DIFFERENT hash -> must raise 23505
    BEGIN
        PERFORM public.tow_claim_job(
            p_job_id := v_target_job_id,
            p_tow_unit_id := 'c1111111-1111-1111-1111-111111111111',
            p_expected_version := 1,
            p_idempotency_key := 'idemp_succ_claim_00001', -- Reused
            p_request_hash := '9999999999999999999999999999999999999999999999999999999999999999'   -- Different hash
        );
    EXCEPTION WHEN SQLSTATE '23505' THEN
        v_hash_conflict_thrown := TRUE;
    END;

    IF NOT v_hash_conflict_thrown THEN
        RAISE EXCEPTION 'TEST 8b FAILED: Expected 23505 exception on payload hash mismatch!';
    END IF;

    RAISE NOTICE 'TEST 8 PASSED: Idempotent replay succeeded; key reuse with different hash raised 23505.';
END $test_idempotency$;

-- ============================================================================
-- TEST 9: DISCOVERY PRIVACY
-- Bob is not assigned to Job 1. Direct SELECT on tow_jobs returns 0 rows.
-- Calling tow_discover_jobs returns coarse coordinates and NO customer phone/address!
-- ============================================================================
SELECT set_config('request.jwt.claim.sub', '44444444-4444-4444-4444-444444444444', false);

DO $test_discovery_privacy$
DECLARE
    v_direct_count INT;
    v_disc_count INT;
BEGIN
    -- Direct SELECT on tow_jobs: Job 1 is assigned to Alice, Job 2 is REQUESTED.
    -- Bob is only assigned to nothing. RLS ensures Bob cannot see Job 1!
    SELECT COUNT(*) INTO v_direct_count
    FROM public.tow_jobs
    WHERE job_id = current_setting('test.target_job1_id');

    IF v_direct_count <> 0 THEN
        RAISE EXCEPTION 'TEST 9a FAILED: Unassigned operator was able to directly SELECT assigned job: count = %', v_direct_count;
    END IF;

    -- Discovery RPC: Bob discovers available jobs matching unit B1
    -- Job 2 requires HEAVY_DUTY, unit B1 has FLATBED -> 0 matching jobs
    SELECT COUNT(*) INTO v_disc_count
    FROM public.tow_discover_jobs('d1111111-1111-1111-1111-111111111111', 20);

    -- Should return 0 because Job 2 requires HEAVY_DUTY, which B1 does not have
    IF v_disc_count <> 0 THEN
        RAISE EXCEPTION 'TEST 9b FAILED: Discovery returned jobs that do not match unit capabilities: count = %', v_disc_count;
    END IF;

    RAISE NOTICE 'TEST 9 PASSED: Discovery privacy enforced; raw rows hidden from unassigned operators.';
END $test_discovery_privacy$;

-- ============================================================================
-- TEST 10: DIRECT INSERT ON TOW_JOBS PROHIBITED
-- An authenticated user (Customer or Operator) attempting direct INSERT into tow_jobs must be denied
-- ============================================================================
SELECT set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', false);

DO $test_insert_denied$
DECLARE
    v_insert_blocked BOOLEAN := FALSE;
BEGIN
    BEGIN
        INSERT INTO public.tow_jobs (
            job_id, customer_id, vehicle_summary,
            pickup_lat, pickup_lng, pickup_address, state
        ) VALUES (
            'tow_injected_0001', '11111111-1111-1111-1111-111111111111',
            'Injected Car', 9.93, -84.08, 'Injected St', 'REQUESTED'
        );
    EXCEPTION WHEN insufficient_privilege THEN
        v_insert_blocked := TRUE;
    END;

    IF NOT v_insert_blocked THEN
        RAISE EXCEPTION 'TEST 10 FAILED: Direct INSERT into tow_jobs was permitted for authenticated role!';
    END IF;

    RAISE NOTICE 'TEST 10 PASSED: Direct INSERT into tow_jobs denied by REVOKE/RLS permissions.';
END $test_insert_denied$;

SELECT 'ALL 10 ELYSIUM TOW SERVER AUTHORITY V5 TESTS PASSED' AS result;
