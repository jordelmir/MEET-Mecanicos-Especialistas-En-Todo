#!/usr/bin/env bash
set -euo pipefail

# ─────────────────────────────────────────────────────────────────────────────
# ELYSIUM GLOBAL MOBILITY OS — V10 HARDENING & STOPS AUTHORITY TEST SUITE
# Mandate:
#   - P8 & P22: Canonical Stop Invariant (Marketplace rejects dynamic add_stop, Auto-Dispatch accepts)
#   - P4 & P20: Hardened PIN Brute-force Lockout (5 attempts -> 5 min lockout)
#   - P5 & P23-25: Trip Sharing (trip:read:safe) & Realtime Revocation
# ─────────────────────────────────────────────────────────────────────────────

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

required_commands=(initdb pg_ctl psql)
for command_name in "${required_commands[@]}"; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Mobility V10 test: SKIP ($command_name unavailable)"
    exit 0
  fi
done

runtime_dir="$(mktemp -d "${TMPDIR:-/tmp}/meet-mobility-v10.XXXXXX")"
cluster_dir="$runtime_dir/data"
socket_dir="$runtime_dir/socket"
server_log="$runtime_dir/postgres.log"
port="$((59000 + RANDOM % 800))"
mkdir -p "$socket_dir"

cleanup() {
  if [[ -d "$cluster_dir" ]]; then
    pg_ctl -D "$cluster_dir" stop -m fast >/dev/null 2>&1 || true
  fi
  if [[ -d "$runtime_dir" ]]; then
    rm -rf -- "$runtime_dir"
  fi
}
trap cleanup EXIT

echo "=== 1. Starting Ephemeral PostgreSQL 16 on port $port ==="
initdb -D "$cluster_dir" --no-locale --encoding=UTF8 >/dev/null
pg_ctl -D "$cluster_dir" -l "$server_log" -o "-p $port -k $socket_dir -c max_connections=200" start >/dev/null

psql_args=(-h "$socket_dir" -p "$port" -d postgres -v ON_ERROR_STOP=1 -q)
export PGOPTIONS="-c client_min_messages=warning"

echo "=== 2. Setting Up Schema & Supabase Auth Roles ==="
psql "${psql_args[@]}" <<'SQL'
create role anon nologin;
create role authenticated nologin;
create role service_role nologin;
create schema if not exists auth;
create schema if not exists extensions;
create extension if not exists pgcrypto with schema extensions;

create table if not exists auth.users(
    id uuid primary key default extensions.gen_random_uuid()
);

create or replace function auth.uid()
returns uuid language sql stable as $$
    select coalesce(
        nullif(current_setting('request.jwt.claim.sub', true), ''),
        nullif((nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'sub'), '')
    )::uuid
$$;

create or replace function auth.role()
returns text language sql stable as $$
    select coalesce(
        nullif(current_setting('request.jwt.claim.role', true), ''),
        nullif((nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'role'), 'authenticated')
    )
$$;

grant usage on schema public to anon, authenticated, service_role;
grant usage on schema auth to anon, authenticated, service_role;
grant usage on schema extensions to anon, authenticated, service_role;
grant select on auth.users to authenticated, service_role;
SQL

echo "=== 3. Applying Migrations V6 through V10 ==="
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906000000_mobility_market_authority.sql"
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906010000_mobility_financial_authority.sql"
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906020000_mobility_safety_and_reserve.sql"
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906030000_mobility_communications_reputation_and_surge.sql"
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906040000_mobility_financial_and_concurrency_p0_lockdown.sql"
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906050000_mobility_financial_authority_v8_closure.sql"
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906060000_mobility_provider_capture_v9_closure.sql"
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906070000_mobility_hardening_and_stops_authority.sql"

echo "=== 4. Seeding Global Market, Drivers, Riders, and Vehicles ==="
psql "${psql_args[@]}" <<'SQL'
-- Rider A
INSERT INTO auth.users (id) VALUES ('11111111-1111-1111-1111-111111111111'::uuid);
-- Driver B
INSERT INTO auth.users (id) VALUES ('22222222-2222-2222-2222-222222222222'::uuid);
-- Friend C (Share Recipient)
INSERT INTO auth.users (id) VALUES ('33333333-3333-3333-3333-333333333333'::uuid);

INSERT INTO public.mobility_markets (
    market_id, country_code, currency_code, timezone, dispatch_modes, max_intermediate_stops, auto_dispatch_enabled, marketplace_offers_enabled, active
) VALUES (
    'CR_SJO', 'CR', 'CRC', 'America/Costa_Rica', ARRAY['AUTO_DISPATCH', 'MARKETPLACE_OFFERS'], 3, true, true, true
);

INSERT INTO public.mobility_service_categories (
    service_category_id, market_id, code, name, max_passengers, active
) VALUES (
    'cat_sjo_standard', 'CR_SJO', 'STANDARD', 'Standard Sedan', 4, true
);

INSERT INTO public.mobility_vehicles (
    vehicle_id, owner_id, license_plate, make, model, year, color, seat_capacity, verification_state, active
) VALUES (
    '44444444-4444-4444-4444-444444444444'::uuid,
    '22222222-2222-2222-2222-222222222222'::uuid,
    'SJO-999', 'Toyota', 'Yaris', 2023, 'White', 4, 'VERIFIED', true
);

INSERT INTO public.driver_market_eligibility (
    driver_id, market_id, is_eligible, background_check_cleared, documents_verified, active
) VALUES (
    '22222222-2222-2222-2222-222222222222'::uuid,
    'CR_SJO', true, true, true, true
);

INSERT INTO public.driver_vehicle_authorizations (
    driver_id, vehicle_id, is_authorized, active
) VALUES (
    '22222222-2222-2222-2222-222222222222'::uuid,
    '44444444-4444-4444-4444-444444444444'::uuid,
    true, true
);
SQL

echo "=== 5. TEST 1: PIN BRUTE FORCE RATE LIMITING & 5-ATTEMPT LOCKOUT ==="
psql "${psql_args[@]}" <<'SQL'
-- Create Trip with PIN "4321" (SHA-256 digest)
INSERT INTO public.ride_requests (
    ride_request_id, rider_id, market_id, service_category_id, dispatch_mode, state,
    pickup_location, destination_location, currency_code, correlation_id
) VALUES (
    '55555555-5555-5555-5555-555555555555'::uuid,
    '11111111-1111-1111-1111-111111111111'::uuid,
    'CR_SJO', 'cat_sjo_standard', 'AUTO_DISPATCH', 'MATCHED',
    ROW(-84.0833, 9.9333, 4326)::extensions.geography,
    ROW(-84.0700, 9.9200, 4326)::extensions.geography,
    'CRC', extensions.gen_random_uuid()
);

INSERT INTO public.trips (
    trip_id, ride_request_id, rider_id, driver_id, vehicle_id, state,
    verification_pin_hash, version
) VALUES (
    '66666666-6666-6666-6666-666666666666'::uuid,
    '55555555-5555-5555-5555-555555555555'::uuid,
    '11111111-1111-1111-1111-111111111111'::uuid,
    '22222222-2222-2222-2222-222222222222'::uuid,
    '44444444-4444-4444-4444-444444444444'::uuid,
    'DRIVER_ARRIVED',
    encode(extensions.digest('4321', 'sha256'), 'hex'),
    1
);

-- Driver attempts boarding with wrong PINs 1 through 4
SET ROLE authenticated;
SET "request.jwt.claim.sub" = '22222222-2222-2222-2222-222222222222';
DO $$
DECLARE
    v_res JSONB;
    i INT;
BEGIN
    FOR i IN 1..4 LOOP
        v_res := public.mobility_transition_trip(
            '66666666-6666-6666-6666-666666666666'::uuid,
            'RIDER_ONBOARD',
            1,
            '0000',
            extensions.gen_random_uuid()
        );
        IF (v_res->>'error_code') <> 'PIN_INVALID' THEN
            RAISE EXCEPTION 'TEST_FAILED: Expected PIN_INVALID on attempt %, got %', i, v_res;
        END IF;
        IF (v_res->>'remaining_attempts')::INT <> (5 - i) THEN
            RAISE EXCEPTION 'TEST_FAILED: Wrong remaining attempts % on step %', (v_res->>'remaining_attempts'), i;
        END IF;
    END LOOP;
END $$;

-- Attempt 5 with wrong PIN -> MUST TRIGGER LOCKOUT
DO $$
DECLARE
    v_res JSONB;
BEGIN
    v_res := public.mobility_transition_trip(
        '66666666-6666-6666-6666-666666666666'::uuid,
        'RIDER_ONBOARD',
        1,
        '0000',
        extensions.gen_random_uuid()
    );
    IF (v_res->>'error_code') <> 'PIN_LOCKED_TOO_MANY_ATTEMPTS' THEN
        RAISE EXCEPTION 'TEST_FAILED: Expected PIN_LOCKED_TOO_MANY_ATTEMPTS on 5th attempt, got %', v_res;
    END IF;
END $$;

-- Attempt during lockout even with CORRECT PIN -> MUST BE REJECTED!
DO $$
DECLARE
    v_res JSONB;
BEGIN
    v_res := public.mobility_transition_trip(
        '66666666-6666-6666-6666-666666666666'::uuid,
        'RIDER_ONBOARD',
        1,
        '4321',
        extensions.gen_random_uuid()
    );
    IF (v_res->>'error_code') <> 'PIN_LOCKED_TOO_MANY_ATTEMPTS' THEN
        RAISE EXCEPTION 'TEST_FAILED: Should reject correct PIN during active lockout, got %', v_res;
    END IF;
END $$;

-- Reset lockout time directly in DB to simulate lockout expiry
RESET ROLE;
UPDATE public.trips
SET pin_locked_until = clock_timestamp() - INTERVAL '1 second'
WHERE trip_id = '66666666-6666-6666-6666-666666666666'::uuid;

-- Driver attempts with CORRECT PIN -> SUCCEEDS and resets failed_pin_attempts
SET ROLE authenticated;
SET "request.jwt.claim.sub" = '22222222-2222-2222-2222-222222222222';
DO $$
DECLARE
    v_res JSONB;
    v_failed INT;
BEGIN
    v_res := public.mobility_transition_trip(
        '66666666-6666-6666-6666-666666666666'::uuid,
        'RIDER_ONBOARD',
        1,
        '4321',
        extensions.gen_random_uuid()
    );
    IF (v_res->>'success')::BOOLEAN <> TRUE THEN
        RAISE EXCEPTION 'TEST_FAILED: Boarding with correct PIN should succeed, got %', v_res;
    END IF;

    SELECT failed_pin_attempts INTO v_failed
    FROM public.trips WHERE trip_id = '66666666-6666-6666-6666-666666666666'::uuid;
    IF v_failed <> 0 THEN
        RAISE EXCEPTION 'TEST_FAILED: failed_pin_attempts should be reset to 0, got %', v_failed;
    END IF;
END $$;
SQL
echo ">>> PASSED: TEST 1 (PIN brute-force lockout, attempt counting, and lockout enforcement verified)."

echo "=== 6. TEST 2: CANONICAL STOP INVARIANT (P8 & P22) ==="
psql "${psql_args[@]}" <<'SQL'
RESET ROLE;
-- Request 1: MARKETPLACE_OFFERS (inDrive style)
INSERT INTO public.ride_requests (
    ride_request_id, rider_id, market_id, service_category_id, dispatch_mode, state,
    pickup_location, destination_location, currency_code, correlation_id
) VALUES (
    '77777777-7777-7777-7777-777777777777'::uuid,
    '11111111-1111-1111-1111-111111111111'::uuid,
    'CR_SJO', 'cat_sjo_standard', 'MARKETPLACE_OFFERS', 'MATCHED',
    ROW(-84.0833, 9.9333, 4326)::extensions.geography,
    ROW(-84.0700, 9.9200, 4326)::extensions.geography,
    'CRC', extensions.gen_random_uuid()
);

-- Destination stop for Request 1
INSERT INTO public.ride_request_stops (
    ride_request_id, sequence, stop_type, location
) VALUES (
    '77777777-7777-7777-7777-777777777777'::uuid, 1, 'DESTINATION',
    extensions.ST_SetSRID(extensions.ST_MakePoint(-84.0700, 9.9200), 4326)
);

-- Trip for Marketplace Request
INSERT INTO public.trips (
    trip_id, ride_request_id, rider_id, driver_id, vehicle_id, state
) VALUES (
    '88888888-8888-8888-8888-888888888888'::uuid,
    '77777777-7777-7777-7777-777777777777'::uuid,
    '11111111-1111-1111-1111-111111111111'::uuid,
    '22222222-2222-2222-2222-222222222222'::uuid,
    '44444444-4444-4444-4444-444444444444'::uuid,
    'IN_PROGRESS'
);

-- Rider attempts to add dynamic intermediate stop on negotiated MARKETPLACE trip -> MUST FAIL!
SET ROLE authenticated;
SET "request.jwt.claim.sub" = '11111111-1111-1111-1111-111111111111';
DO $$
BEGIN
    BEGIN
        PERFORM public.mobility_add_trip_stop(
            '88888888-8888-8888-8888-888888888888'::uuid,
            9.9250, -84.0750
        );
        RAISE EXCEPTION 'TEST_FAILED: Dynamic stop addition on MARKETPLACE_OFFERS should have been rejected!';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM NOT LIKE '%ADD_STOP_NOT_ALLOWED_IN_MARKETPLACE_OFFERS%' THEN
            RAISE EXCEPTION 'Unexpected error on marketplace stop add: %', SQLERRM;
        END IF;
    END;
END $$;

-- Request 2: AUTO_DISPATCH (Taximeter / Metered compatible)
RESET ROLE;
INSERT INTO public.ride_requests (
    ride_request_id, rider_id, market_id, service_category_id, dispatch_mode, state,
    pickup_location, destination_location, currency_code, correlation_id
) VALUES (
    '99999999-9999-9999-9999-999999999999'::uuid,
    '11111111-1111-1111-1111-111111111111'::uuid,
    'CR_SJO', 'cat_sjo_standard', 'AUTO_DISPATCH', 'MATCHED',
    ROW(-84.0833, 9.9333, 4326)::extensions.geography,
    ROW(-84.0700, 9.9200, 4326)::extensions.geography,
    'CRC', extensions.gen_random_uuid()
);

-- Destination stop for Request 2 (initially sequence 1)
INSERT INTO public.ride_request_stops (
    ride_request_id, sequence, stop_type, location
) VALUES (
    '99999999-9999-9999-9999-999999999999'::uuid, 1, 'DESTINATION',
    extensions.ST_SetSRID(extensions.ST_MakePoint(-84.0700, 9.9200), 4326)
);

-- Trip for Auto Dispatch Request
INSERT INTO public.trips (
    trip_id, ride_request_id, rider_id, driver_id, vehicle_id, state
) VALUES (
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'::uuid,
    '99999999-9999-9999-9999-999999999999'::uuid,
    '11111111-1111-1111-1111-111111111111'::uuid,
    '22222222-2222-2222-2222-222222222222'::uuid,
    '44444444-4444-4444-4444-444444444444'::uuid,
    'IN_PROGRESS'
);

-- Rider adds dynamic intermediate stop on AUTO_DISPATCH trip -> SUCCEEDS!
SET ROLE authenticated;
SET "request.jwt.claim.sub" = '11111111-1111-1111-1111-111111111111';
DO $$
DECLARE
    v_res JSONB;
    v_dest_seq INT;
    v_inter_count INT;
BEGIN
    v_res := public.mobility_add_trip_stop(
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'::uuid,
        9.9250, -84.0750, 10.0, 'Paso intermedio en cafetería', 'Café Central', 'place_cafe_123'
    );

    IF (v_res->>'success')::BOOLEAN <> TRUE THEN
        RAISE EXCEPTION 'TEST_FAILED: Dynamic stop addition on AUTO_DISPATCH failed: %', v_res;
    END IF;

    -- Verify intermediate stop count and destination sequence shifted to 2
    SELECT count(*) INTO v_inter_count
    FROM public.ride_request_stops
    WHERE ride_request_id = '99999999-9999-9999-9999-999999999999'::uuid AND stop_type = 'INTERMEDIATE';

    SELECT sequence INTO v_dest_seq
    FROM public.ride_request_stops
    WHERE ride_request_id = '99999999-9999-9999-9999-999999999999'::uuid AND stop_type = 'DESTINATION';

    IF v_inter_count <> 1 THEN
        RAISE EXCEPTION 'TEST_FAILED: Expected 1 intermediate stop, got %', v_inter_count;
    END IF;

    IF v_dest_seq <> 2 THEN
        RAISE EXCEPTION 'TEST_FAILED: Destination sequence should be shifted to 2, got %', v_dest_seq;
    END IF;
END $$;
SQL
echo ">>> PASSED: TEST 2 (Canonical Stop Invariant: Marketplace rejects dynamic additions, Auto-Dispatch accepts and shifts sequence)."

echo "=== 7. TEST 3: TRIP SHARING (trip:read:safe) & REALTIME REVOCATION ==="
psql "${psql_args[@]}" <<'SQL'
-- Rider A shares trip with Friend C (33333333-3333-3333-3333-333333333333)
SET ROLE authenticated;
SET "request.jwt.claim.sub" = '11111111-1111-1111-1111-111111111111';
DO $$
DECLARE
    v_res JSONB;
BEGIN
    v_res := public.mobility_share_trip(
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'::uuid,
        '33333333-3333-3333-3333-333333333333'::uuid
    );
    IF (v_res->>'success')::BOOLEAN <> TRUE THEN
        RAISE EXCEPTION 'TEST_FAILED: Sharing trip failed: %', v_res;
    END IF;
END $$;

-- Friend C queries trips via RLS -> MUST SEE THE SHARED TRIP!
SET ROLE authenticated;
SET "request.jwt.claim.sub" = '33333333-3333-3333-3333-333333333333';
DO $$
DECLARE
    v_count INT;
BEGIN
    SELECT count(*) INTO v_count
    FROM public.trips
    WHERE trip_id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'::uuid;

    IF v_count <> 1 THEN
        RAISE EXCEPTION 'TEST_FAILED: Friend C should be able to read shared trip via RLS, count: %', v_count;
    END IF;
END $$;

-- Rider A revokes sharing access from Friend C
SET ROLE authenticated;
SET "request.jwt.claim.sub" = '11111111-1111-1111-1111-111111111111';
DO $$
DECLARE
    v_res JSONB;
BEGIN
    v_res := public.mobility_revoke_trip_share(
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'::uuid,
        '33333333-3333-3333-3333-333333333333'::uuid
    );
    IF (v_res->>'success')::BOOLEAN <> TRUE THEN
        RAISE EXCEPTION 'TEST_FAILED: Revoking trip share failed: %', v_res;
    END IF;
END $$;

-- Friend C queries trips again -> MUST BE DENIED / RETURN 0 ROWS!
SET ROLE authenticated;
SET "request.jwt.claim.sub" = '33333333-3333-3333-3333-333333333333';
DO $$
DECLARE
    v_count INT;
BEGIN
    SELECT count(*) INTO v_count
    FROM public.trips
    WHERE trip_id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'::uuid;

    IF v_count <> 0 THEN
        RAISE EXCEPTION 'TEST_FAILED: Revoked user should NOT be able to see trip, got % rows', v_count;
    END IF;
END $$;
SQL
echo ">>> PASSED: TEST 3 (Trip sharing primitive, RLS read-access, and real-time revocation verified)."

echo "=== ALL MOBILITY V10 HARDENING TESTS PASSED (100% GREEN) ==="
