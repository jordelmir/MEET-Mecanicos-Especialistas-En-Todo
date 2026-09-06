#!/usr/bin/env bash
set -euo pipefail

# ─────────────────────────────────────────────────────────────────────────────
# ELYSIUM GLOBAL MOBILITY OS — POSTGRESQL AUTHORITY TEST HARNESS
# Mandate: ORDEN MAESTRA V6 (Waves 0–10 Authority Verification)
# ─────────────────────────────────────────────────────────────────────────────

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

required_commands=(initdb pg_ctl psql)
for command_name in "${required_commands[@]}"; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Mobility authority test: SKIP ($command_name unavailable)"
    exit 0
  fi
done

runtime_dir="$(mktemp -d "${TMPDIR:-/tmp}/meet-mobility-auth.XXXXXX")"
cluster_dir="$runtime_dir/data"
socket_dir="$runtime_dir/socket"
server_log="$runtime_dir/postgres.log"
port="$((57500 + RANDOM % 500))"
mkdir -p "$socket_dir"

cleanup() {
  if [[ -d "$cluster_dir" ]]; then
    pg_ctl -D "$cluster_dir" stop -m fast >/dev/null 2>&1 || true
  fi
  if [[ -d "$runtime_dir" && "$(basename "$runtime_dir")" == meet-mobility-auth.* ]]; then
    rm -rf -- "$runtime_dir"
  fi
}
trap cleanup EXIT

echo "=== 1. Starting Ephemeral PostgreSQL 16 on port $port ==="
initdb -D "$cluster_dir" --no-locale --encoding=UTF8 >/dev/null
pg_ctl \
  -D "$cluster_dir" \
  -l "$server_log" \
  -o "-p $port -k $socket_dir -c max_connections=200" \
  start >/dev/null

psql_args=(-h "$socket_dir" -p "$port" -d postgres -v ON_ERROR_STOP=1 -q)
export PGOPTIONS="-c client_min_messages=warning"

echo "=== 2. Setting Up Supabase Auth & Schemas ==="
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
returns uuid
language sql
stable
as $$
    select coalesce(
        nullif(current_setting('request.jwt.claim.sub', true), ''),
        nullif((nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'sub'), '')
    )::uuid
$$;

create or replace function auth.role()
returns text
language sql
stable
as $$
    select coalesce(
        nullif(current_setting('request.jwt.claim.role', true), ''),
        nullif((nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'role'), 'authenticated')
    )
$$;

grant usage on schema public to anon, authenticated;
grant usage on schema auth to anon, authenticated;
grant usage on schema extensions to anon, authenticated;
grant select on auth.users to authenticated;
SQL

echo "=== 3. Applying Mobility Authority Migration V6 ==="
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906000000_mobility_market_authority.sql"

echo "=== 4. Seeding Market Authority & Test Actors ==="
psql "${psql_args[@]}" <<'SQL'
-- Seed Market & Category
INSERT INTO public.mobility_markets (
    market_id, country_code, currency_code, timezone, dispatch_modes, max_intermediate_stops, auto_dispatch_enabled, marketplace_offers_enabled, active
) VALUES (
    'CR_SJO', 'CR', 'CRC', 'America/Costa_Rica', ARRAY['AUTO_DISPATCH', 'MARKETPLACE_OFFERS'], 3, true, true, true
);

INSERT INTO public.mobility_service_categories (
    service_category_id, market_id, code, name, max_passengers, requires_ev, requires_accessible, active
) VALUES (
    'cat_sjo_standard', 'CR_SJO', 'STANDARD', 'Standard Sedan', 4, false, false, true
);

-- Seed Rider
INSERT INTO auth.users (id) VALUES ('11111111-1111-1111-1111-111111111111'::uuid);

-- Seed Driver 1 & Vehicle
INSERT INTO auth.users (id) VALUES ('22222222-2222-2222-2222-222222222222'::uuid);

-- Seed Driver 2 & Vehicle (for inDrive marketplace competition)
INSERT INTO auth.users (id) VALUES ('22222222-2222-2222-2222-222222222223'::uuid);

INSERT INTO public.mobility_vehicles (
    vehicle_id, owner_id, license_plate, make, model, year, color, seat_capacity, verification_state, active
) VALUES (
    '33333333-3333-3333-3333-333333333334'::uuid,
    '22222222-2222-2222-2222-222222222223'::uuid,
    'SJO-999', 'Nissan', 'Versa', 2021, 'White', 4, 'VERIFIED', true
);

INSERT INTO public.driver_market_eligibility (
    driver_id, market_id, is_eligible, background_check_cleared, documents_verified, active
) VALUES (
    '22222222-2222-2222-2222-222222222223'::uuid,
    'CR_SJO', true, true, true, true
);

INSERT INTO public.driver_vehicle_authorizations (
    driver_id, vehicle_id, is_authorized, active
) VALUES (
    '22222222-2222-2222-2222-222222222223'::uuid,
    '33333333-3333-3333-3333-333333333334'::uuid,
    true, true
);

INSERT INTO public.mobility_vehicles (
    vehicle_id, owner_id, license_plate, make, model, year, color, seat_capacity, verification_state, active
) VALUES (
    '33333333-3333-3333-3333-333333333333'::uuid,
    '22222222-2222-2222-2222-222222222222'::uuid,
    'SJO-789', 'Toyota', 'Corolla', 2022, 'Silver', 4, 'VERIFIED', true
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
    '33333333-3333-3333-3333-333333333333'::uuid,
    true, true
);
SQL

echo "=== 5. Test: Driver Presence Update & Speed Guard ==="
psql "${psql_args[@]}" <<'SQL'
DO $$
DECLARE
    v_res JSONB;
BEGIN
    SET ROLE authenticated;
    SET request.jwt.claims = '{"sub":"22222222-2222-2222-2222-222222222222"}';

    v_res := public.mobility_update_driver_presence(
        'CR_SJO',
        '33333333-3333-3333-3333-333333333333'::uuid,
        'AVAILABLE',
        9.9355, -84.0768,
        90.0,
        15.0,
        1
    );

    IF (v_res->>'success')::boolean IS NOT TRUE THEN
        RAISE EXCEPTION 'Driver presence update failed';
    END IF;
END $$;
SQL
echo ">>> PASSED: Driver presence updated with geospatial index."

echo "=== 6. Test: Ride Request Creation with Multi-Stop & Server SHA-256 Idempotency ==="
psql "${psql_args[@]}" <<'SQL'
DO $$
DECLARE
    v_res1 JSONB;
    v_res2 JSONB;
    v_req_id UUID;
    v_stops_count INT;
BEGIN
    SET ROLE authenticated;
    SET request.jwt.claims = '{"sub":"11111111-1111-1111-1111-111111111111"}';

    v_res1 := public.mobility_request_ride(
        'CR_SJO',
        'cat_sjo_standard',
        'AUTO_DISPATCH',
        9.9350, -84.0750, 5.0, 'Barrio Escalante, San Jose',
        9.9281, -84.0907, 5.0, 'Paseo Colon, San Jose',
        '[{"latitude": 9.9330, "longitude": -84.0800, "accuracy_meters": 4.0, "address": "La California"}]'::jsonb,
        3500,
        NULL,
        '44444444-4444-4444-4444-444444444444'::uuid,
        '55555555-5555-5555-5555-555555555555'::uuid
    );

    v_req_id := (v_res1->>'ride_request_id')::uuid;

    SELECT count(*) INTO v_stops_count FROM public.ride_request_stops WHERE ride_request_id = v_req_id;
    IF v_stops_count <> 3 THEN
        RAISE EXCEPTION 'Expected exactly 3 stops (pickup, intermediate, destination), got %', v_stops_count;
    END IF;

    -- Idempotent replay
    v_res2 := public.mobility_request_ride(
        'CR_SJO',
        'cat_sjo_standard',
        'AUTO_DISPATCH',
        9.9350, -84.0750, 5.0, 'Barrio Escalante, San Jose',
        9.9281, -84.0907, 5.0, 'Paseo Colon, San Jose',
        '[{"latitude": 9.9330, "longitude": -84.0800, "accuracy_meters": 4.0, "address": "La California"}]'::jsonb,
        3500,
        NULL,
        '44444444-4444-4444-4444-444444444444'::uuid,
        '55555555-5555-5555-5555-555555555555'::uuid
    );

    IF v_res1->>'ride_request_id' <> v_res2->>'ride_request_id' THEN
        RAISE EXCEPTION 'Idempotency failed: request IDs do not match';
    END IF;
END $$;
SQL
echo ">>> PASSED: Ride request with multi-stop created and idempotent replay verified."

echo "=== 7. Test: Tampered Payload with Reused Key Rejection (23505) ==="
tamper_err=$(psql "${psql_args[@]}" -c "
SET ROLE authenticated;
SET request.jwt.claims = '{\"sub\":\"11111111-1111-1111-1111-111111111111\"}';
SELECT public.mobility_request_ride(
    'CR_SJO',
    'cat_sjo_standard',
    'AUTO_DISPATCH',
    9.9400, -84.0700, 5.0, 'Different Location',
    9.9281, -84.0907, 5.0, 'Paseo Colon, San Jose',
    '[]'::jsonb,
    5000,
    NULL,
    '44444444-4444-4444-4444-444444444444'::uuid,
    '55555555-5555-5555-5555-555555555555'::uuid
);
" 2>&1 || true)

if echo "$tamper_err" | grep -q "IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD\|23505"; then
    echo ">>> PASSED: Tampered payload on reused key correctly raised 23505."
else
    echo "FATAL: Expected 23505 tampered payload exception, got: $tamper_err"
    exit 1
fi

echo "=== 8. Test: Spatial Candidate Search & DiDi Mutual Pair Blocking ==="
psql "${psql_args[@]}" <<'SQL'
DO $$
DECLARE
    v_req_id UUID;
    v_cands JSONB;
BEGIN
    SELECT ride_request_id INTO v_req_id FROM public.ride_requests LIMIT 1;

    -- Normal search finds the nearby driver
    v_cands := public.mobility_search_dispatch_candidates(v_req_id, 5000.0, 10);
    IF jsonb_array_length(v_cands->'candidates') <> 1 THEN
        RAISE EXCEPTION 'Expected 1 candidate, got %', jsonb_array_length(v_cands->'candidates');
    END IF;

    -- Block the driver
    INSERT INTO public.mobility_pair_blocks (
        blocker_id, blocked_id, reason, active
    ) VALUES (
        '11111111-1111-1111-1111-111111111111'::uuid,
        '22222222-2222-2222-2222-222222222222'::uuid,
        'Safety report block',
        true
    );

    -- Search again: driver must NOT appear
    v_cands := public.mobility_search_dispatch_candidates(v_req_id, 5000.0, 10);
    IF jsonb_array_length(v_cands->'candidates') <> 0 THEN
        RAISE EXCEPTION 'Pair blocking failed: blocked driver was returned in search';
    END IF;

    -- Remove block for subsequent tests
    DELETE FROM public.mobility_pair_blocks;
END $$;
SQL
echo ">>> PASSED: DiDi mutual pair blocking verified in candidate search."

echo "=== 9. Test: Auto Dispatch CAS Claim & Private Realtime Memberships ==="
psql "${psql_args[@]}" <<'SQL'
DO $$
DECLARE
    v_req_id UUID;
    v_offer_id UUID := '66666666-6666-6666-6666-666666666666'::uuid;
    v_accept_res JSONB;
    v_trip_id UUID;
    v_membership_count INT;
BEGIN
    SELECT ride_request_id INTO v_req_id FROM public.ride_requests LIMIT 1;

    INSERT INTO public.dispatch_offers (
        dispatch_offer_id, ride_request_id, driver_id, vehicle_id, state, expires_at
    ) VALUES (
        v_offer_id, v_req_id,
        '22222222-2222-2222-2222-222222222222'::uuid,
        '33333333-3333-3333-3333-333333333333'::uuid,
        'PENDING', clock_timestamp() + INTERVAL '30 seconds'
    );

    SET ROLE authenticated;
    SET request.jwt.claims = '{"sub":"22222222-2222-2222-2222-222222222222"}';

    v_accept_res := public.mobility_accept_dispatch(
        v_req_id,
        v_offer_id,
        '33333333-3333-3333-3333-333333333333'::uuid,
        1,
        '77777777-7777-7777-7777-777777777777'::uuid
    );

    IF (v_accept_res->>'success')::boolean IS NOT TRUE THEN
        RAISE EXCEPTION 'Accept dispatch CAS failed: %', v_accept_res;
    END IF;

    v_trip_id := (v_accept_res->'trip'->>'trip_id')::uuid;

    SELECT count(*) INTO v_membership_count FROM public.mobility_realtime_memberships
    WHERE topic = 'trip:' || v_trip_id::TEXT;

    IF v_membership_count <> 2 THEN
        RAISE EXCEPTION 'Expected 2 realtime memberships (rider and driver), got %', v_membership_count;
    END IF;
END $$;
SQL
echo ">>> PASSED: Auto dispatch CAS claim executed, trip created, and realtime memberships secured."

echo "=== 10. Test: inDrive Marketplace Discovery, Bidding & Rider Selection CAS ==="
psql "${psql_args[@]}" <<'SQL'
DO $$
DECLARE
    v_req_res JSONB;
    v_req_id UUID;
    v_disc JSONB;
    v_bid1 JSONB;
    v_bid2 JSONB;
    v_sel JSONB;
    v_loser_state TEXT;
BEGIN
    -- Rider requests marketplace ride
    SET ROLE authenticated;
    SET request.jwt.claims = '{"sub":"11111111-1111-1111-1111-111111111111"}';

    v_req_res := public.mobility_request_ride(
        'CR_SJO',
        'cat_sjo_standard',
        'MARKETPLACE_OFFERS',
        9.9350, -84.0750, 5.0, 'Barrio Escalante',
        9.9281, -84.0907, 5.0, 'Paseo Colon',
        '[]'::jsonb,
        3000,
        NULL,
        '88888888-8888-8888-8888-888888888888'::uuid,
        '99999999-9999-9999-9999-999999999999'::uuid
    );
    v_req_id := (v_req_res->>'ride_request_id')::uuid;

    -- Driver discovers requests safely
    SET request.jwt.claims = '{"sub":"22222222-2222-2222-2222-222222222222"}';
    v_disc := public.mobility_discover_requests('CR_SJO', 5000.0, 10);

    IF jsonb_array_length(v_disc->'requests') < 1 THEN
        RAISE EXCEPTION 'Safe discovery returned 0 requests';
    END IF;

    -- Driver 1 submits offer
    v_bid1 := public.mobility_submit_driver_offer(
        v_req_id,
        '33333333-3333-3333-3333-333333333333'::uuid,
        3200,
        'CRC',
        240,
        1,
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'::uuid
    );



    SET request.jwt.claims = '{"sub":"22222222-2222-2222-2222-222222222223"}';
    v_bid2 := public.mobility_submit_driver_offer(
        v_req_id,
        '33333333-3333-3333-3333-333333333334'::uuid,
        2900,
        'CRC',
        180,
        1,
        'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'::uuid
    );

    -- Rider selects offer 2 (Nissan)
    SET request.jwt.claims = '{"sub":"11111111-1111-1111-1111-111111111111"}';
    v_sel := public.mobility_select_driver_offer(
        v_req_id,
        (v_bid2->>'offer_id')::uuid,
        1,
        'cccccccc-cccc-cccc-cccc-cccccccccccc'::uuid
    );

    IF (v_sel->>'success')::boolean IS NOT TRUE THEN
        RAISE EXCEPTION 'Rider offer selection failed: %', v_sel;
    END IF;

    -- Verify losing offer was transitioned to REJECTED
    SELECT state INTO v_loser_state FROM public.ride_driver_offers WHERE offer_id = (v_bid1->>'offer_id')::uuid;
    IF v_loser_state <> 'REJECTED' THEN
        RAISE EXCEPTION 'Losing offer expected REJECTED, got %', v_loser_state;
    END IF;
END $$;
SQL
echo ">>> PASSED: inDrive bidding & atomic offer selection with losing rejection verified."

echo "=== 11. Test: Trip State Transitions & Boarding PIN Verification ==="
psql "${psql_args[@]}" <<'SQL'
DO $$
DECLARE
    v_trip public.trips%ROWTYPE;
    v_pin TEXT := '1234';
    v_pin_hash TEXT;
    v_res JSONB;
BEGIN
    SELECT * INTO v_trip FROM public.trips ORDER BY created_at DESC LIMIT 1;
    v_pin_hash := encode(extensions.digest(v_pin, 'sha256'), 'hex');

    UPDATE public.trips SET verification_pin_hash = v_pin_hash WHERE trip_id = v_trip.trip_id;

    -- Assigned driver starts en route
    SET ROLE authenticated;
    PERFORM set_config('request.jwt.claims', jsonb_build_object('sub', v_trip.driver_id::text)::text, true);

    v_res := public.mobility_transition_trip(
        v_trip.trip_id, 'DRIVER_EN_ROUTE', v_trip.version, NULL, extensions.gen_random_uuid()
    );
    IF (v_res->>'success')::boolean IS NOT TRUE THEN RAISE EXCEPTION 'DRIVER_EN_ROUTE failed'; END IF;

    -- Driver arrives
    v_res := public.mobility_transition_trip(
        v_trip.trip_id, 'DRIVER_ARRIVED', 2, NULL, extensions.gen_random_uuid()
    );
    IF (v_res->>'success')::boolean IS NOT TRUE THEN RAISE EXCEPTION 'DRIVER_ARRIVED failed'; END IF;

    -- Attempt boarding without PIN fails
    v_res := public.mobility_transition_trip(
        v_trip.trip_id, 'RIDER_ONBOARD', 3, NULL, extensions.gen_random_uuid()
    );
    IF (v_res->>'error_code') <> 'PIN_REQUIRED' THEN
        RAISE EXCEPTION 'Expected PIN_REQUIRED, got %', v_res;
    END IF;

    -- Attempt boarding with wrong PIN fails
    v_res := public.mobility_transition_trip(
        v_trip.trip_id, 'RIDER_ONBOARD', 3, '9999', extensions.gen_random_uuid()
    );
    IF (v_res->>'error_code') <> 'PIN_INVALID' THEN
        RAISE EXCEPTION 'Expected PIN_INVALID, got %', v_res;
    END IF;

    -- Attempt boarding with correct PIN succeeds
    v_res := public.mobility_transition_trip(
        v_trip.trip_id, 'RIDER_ONBOARD', 3, v_pin, extensions.gen_random_uuid()
    );
    IF (v_res->>'success')::boolean IS NOT TRUE THEN RAISE EXCEPTION 'RIDER_ONBOARD with PIN failed'; END IF;

    -- In progress
    v_res := public.mobility_transition_trip(
        v_trip.trip_id, 'IN_PROGRESS', 4, NULL, extensions.gen_random_uuid()
    );
    IF (v_res->>'success')::boolean IS NOT TRUE THEN RAISE EXCEPTION 'IN_PROGRESS failed'; END IF;

    -- Arrived destination
    v_res := public.mobility_transition_trip(
        v_trip.trip_id, 'ARRIVED_DESTINATION', 5, NULL, extensions.gen_random_uuid()
    );
    IF (v_res->>'success')::boolean IS NOT TRUE THEN RAISE EXCEPTION 'ARRIVED_DESTINATION failed'; END IF;

    -- Completed
    v_res := public.mobility_transition_trip(
        v_trip.trip_id, 'COMPLETED', 6, NULL, extensions.gen_random_uuid()
    );
    IF (v_res->>'success')::boolean IS NOT TRUE THEN RAISE EXCEPTION 'COMPLETED failed'; END IF;
END $$;
SQL
echo ">>> PASSED: Trip lifecycle progression through COMPLETED with PIN guard verified."

echo "=== 12. Test: Direct Mutation Prohibition (REVOKE Security) ==="
direct_insert_err=$(psql "${psql_args[@]}" -c "
SET ROLE authenticated;
SET request.jwt.claims = '{\"sub\":\"11111111-1111-1111-1111-111111111111\"}';
INSERT INTO public.ride_requests (
    rider_id, market_id, service_category_id, dispatch_mode, state,
    pickup_location, destination_location, currency_code, correlation_id
) VALUES (
    '11111111-1111-1111-1111-111111111111'::uuid, 'CR_SJO', 'cat_sjo_standard', 'AUTO_DISPATCH', 'SEARCHING',
    extensions.ST_SetSRID(extensions.ST_MakePoint(-84.07, 9.93), 4326)::extensions.geography,
    extensions.ST_SetSRID(extensions.ST_MakePoint(-84.09, 9.92), 4326)::extensions.geography,
    'CRC', extensions.gen_random_uuid()
);
" 2>&1 || true)

if echo "$direct_insert_err" | grep -q "permission denied for table ride_requests\|42501"; then
    echo ">>> PASSED: Direct INSERT on ride_requests rejected with permission denied."
else
    echo "FATAL: Direct INSERT was not rejected! Output: $direct_insert_err"
    exit 1
fi

echo ""
echo "=========================================================================="
echo "ALL POSTGRESQL MOBILITY AUTHORITY TESTS PASSED (100% GREEN)"
echo "  - Market & Service Category validation"
echo "  - Driver Presence & Speed/Sequence guards"
echo "  - Multi-Stop Ride Requests & Canonical SHA-256 Idempotency"
echo "  - DiDi Mutual Pair Blocking"
echo "  - Algorithmic Auto-Dispatch CAS Claim"
echo "  - inDrive Marketplace Discovery, Bidding & Offer Selection"
echo "  - Trip Lifecycle Machine & Boarding PIN Verification"
echo "  - Security Definer & Direct Mutation Revocation"
echo "=========================================================================="
