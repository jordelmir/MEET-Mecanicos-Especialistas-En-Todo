#!/usr/bin/env bash
set -euo pipefail

# ─────────────────────────────────────────────────────────────────────────────
# ELYSIUM GLOBAL MOBILITY OS — COMMS, REPUTATION, SUPPORT & SURGE TEST SUITE
# Mandate: ORDEN MAESTRA V6 (Waves 18–20 In-App Comms, Ratings & Dynamic Surge)
# ─────────────────────────────────────────────────────────────────────────────

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

required_commands=(initdb pg_ctl psql)
for command_name in "${required_commands[@]}"; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Mobility comms test: SKIP ($command_name unavailable)"
    exit 0
  fi
done

runtime_dir="$(mktemp -d "${TMPDIR:-/tmp}/meet-mobility-comms.XXXXXX")"
cluster_dir="$runtime_dir/data"
socket_dir="$runtime_dir/socket"
server_log="$runtime_dir/postgres.log"
port="$((59500 + RANDOM % 400))"
mkdir -p "$socket_dir"

cleanup() {
  if [[ -d "$cluster_dir" ]]; then
    pg_ctl -D "$cluster_dir" stop -m fast >/dev/null 2>&1 || true
  fi
  if [[ -d "$runtime_dir" && "$(basename "$runtime_dir")" == meet-mobility-comms.* ]]; then
    rm -rf -- "$runtime_dir"
  fi
}
trap cleanup EXIT

echo "=== 1. Starting Ephemeral PostgreSQL 16 on port $port ==="
initdb -D "$cluster_dir" --no-locale --encoding=UTF8 >/dev/null
pg_ctl \
  -D "$cluster_dir" \
  -l "$server_log" \
  -o "-p $port -k $socket_dir -c max_connections=250" \
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

grant usage on schema public to anon, authenticated, service_role;
grant usage on schema auth to anon, authenticated, service_role;
grant usage on schema extensions to anon, authenticated, service_role;
grant select on auth.users to authenticated, service_role;
SQL

echo "=== 3. Applying Mobility Authority Migrations V6 (Waves 0–20) ==="
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906000000_mobility_market_authority.sql"
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906010000_mobility_financial_authority.sql"
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906020000_mobility_safety_and_reserve.sql"
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906030000_mobility_communications_reputation_and_surge.sql"

echo "=== 4. Seeding Test Entities ==="
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

-- Seed Driver & Vehicle
INSERT INTO auth.users (id) VALUES ('22222222-2222-2222-2222-222222222222'::uuid);

INSERT INTO public.mobility_vehicles (
    vehicle_id, owner_id, license_plate, make, model, year, color, seat_capacity, verification_state, active
) VALUES (
    '33333333-3333-3333-3333-333333333333'::uuid,
    '22222222-2222-2222-2222-222222222222'::uuid,
    'SJO-789', 'Toyota', 'Corolla', 2022, 'Silver', 4, 'VERIFIED', true
);

-- Seed Third Party (Unauthorized) Actor
INSERT INTO auth.users (id) VALUES ('44444444-4444-4444-4444-444444444444'::uuid);

-- Seed Base Ride Request
INSERT INTO public.ride_requests (
    ride_request_id, rider_id, market_id, service_category_id, dispatch_mode, state,
    pickup_location, pickup_address, destination_location, destination_address, currency_code, correlation_id
) VALUES (
    '77777777-7777-7777-7777-777777777777'::uuid,
    '11111111-1111-1111-1111-111111111111'::uuid,
    'CR_SJO', 'cat_sjo_standard', 'AUTO_DISPATCH', 'MATCHED',
    extensions.ST_SetSRID(extensions.ST_MakePoint(-84.0833, 9.9333), 4326), 'Parque Central',
    extensions.ST_SetSRID(extensions.ST_MakePoint(-84.0907, 9.9281), 4326), 'Hospital San Juan',
    'CRC', extensions.gen_random_uuid()
);

-- Seed Base Active Trip
INSERT INTO public.trips (
    trip_id, ride_request_id, rider_id, driver_id, vehicle_id, state
) VALUES (
    '88888888-8888-8888-8888-888888888888'::uuid,
    '77777777-7777-7777-7777-777777777777'::uuid,
    '11111111-1111-1111-1111-111111111111'::uuid,
    '22222222-2222-2222-2222-222222222222'::uuid,
    '33333333-3333-3333-3333-333333333333'::uuid,
    'IN_PROGRESS'
);
SQL

echo "=== Test 1: In-App Chat Messages (Wave 18) ==="
psql "${psql_args[@]}" <<'SQL'
RESET ROLE;
DO $$
DECLARE
    v_err_caught BOOLEAN := FALSE;
    v_msg1 JSONB;
    v_msg2 JSONB;
    v_count INT;
BEGIN
    -- 1. Unauthorized third party tries to send message -> FORBIDDEN
    SET ROLE authenticated;
    PERFORM set_config('request.jwt.claim.sub', '44444444-4444-4444-4444-444444444444', true);
    BEGIN
        PERFORM public.mobility_send_trip_message('88888888-8888-8888-8888-888888888888'::uuid, 'Hola');
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM LIKE '%FORBIDDEN%' THEN
            v_err_caught := TRUE;
        END IF;
    END;

    IF NOT v_err_caught THEN
        RAISE EXCEPTION 'Test 1A Failed: Unauthorized user was not forbidden from sending message';
    END IF;

    -- 2. Rider sends message
    PERFORM set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', true);
    v_msg1 := public.mobility_send_trip_message(
        '88888888-8888-8888-8888-888888888888'::uuid,
        'Voy saliendo con un paraguas rojo'
    );
    IF (v_msg1->>'success')::boolean <> TRUE THEN
        RAISE EXCEPTION 'Rider send message failed: %', v_msg1;
    END IF;

    -- 3. Driver replies
    PERFORM set_config('request.jwt.claim.sub', '22222222-2222-2222-2222-222222222222', true);
    v_msg2 := public.mobility_send_trip_message(
        '88888888-8888-8888-8888-888888888888'::uuid,
        'Perfecto, estoy al frente de la farmacia con intermitentes'
    );
    IF (v_msg2->>'success')::boolean <> TRUE THEN
        RAISE EXCEPTION 'Driver reply message failed: %', v_msg2;
    END IF;

    -- 4. Blank message rejected
    v_err_caught := FALSE;
    BEGIN
        PERFORM public.mobility_send_trip_message('88888888-8888-8888-8888-888888888888'::uuid, '   ');
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM LIKE '%EMPTY_MESSAGE_BODY%' THEN
            v_err_caught := TRUE;
        END IF;
    END;
    IF NOT v_err_caught THEN
        RAISE EXCEPTION 'Blank message was not rejected';
    END IF;

    SELECT count(*) INTO v_count FROM public.trip_messages WHERE trip_id = '88888888-8888-8888-8888-888888888888'::uuid;
    IF v_count <> 2 THEN
        RAISE EXCEPTION 'Expected 2 messages in trip_messages, found %', v_count;
    END IF;
END $$;
SQL
echo "  ✓ In-app chat authorization, message exchange, and bounds enforced"

echo "=== Test 2: Bilateral Ratings & Reviews (Wave 19) ==="
psql "${psql_args[@]}" <<'SQL'
RESET ROLE;
DO $$
DECLARE
    v_err_caught BOOLEAN := FALSE;
    v_res JSONB;
BEGIN
    SET ROLE authenticated;
    PERFORM set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', true);

    -- 1. Attempt to rate while trip is IN_PROGRESS -> rejected
    BEGIN
        PERFORM public.mobility_rate_trip('88888888-8888-8888-8888-888888888888'::uuid, 5::smallint);
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM LIKE '%CANNOT_RATE_INCOMPLETE_TRIP%' THEN
            v_err_caught := TRUE;
        END IF;
    END;

    IF NOT v_err_caught THEN
        RAISE EXCEPTION 'Rating incomplete trip was not rejected';
    END IF;

    -- Transition trip to COMPLETED (as admin)
    RESET ROLE;
    UPDATE public.trips SET state = 'COMPLETED', completed_at = clock_timestamp()
    WHERE trip_id = '88888888-8888-8888-8888-888888888888'::uuid;

    SET ROLE authenticated;
    -- 2. Unauthorized third party tries to rate -> FORBIDDEN
    PERFORM set_config('request.jwt.claim.sub', '44444444-4444-4444-4444-444444444444', true);
    v_err_caught := FALSE;
    BEGIN
        PERFORM public.mobility_rate_trip('88888888-8888-8888-8888-888888888888'::uuid, 5::smallint);
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM LIKE '%FORBIDDEN%' THEN
            v_err_caught := TRUE;
        END IF;
    END;
    IF NOT v_err_caught THEN
        RAISE EXCEPTION 'Unauthorized third party was not forbidden from rating';
    END IF;

    -- 3. Invalid rating (6 stars) -> INVALID_RATING
    PERFORM set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', true);
    v_err_caught := FALSE;
    BEGIN
        PERFORM public.mobility_rate_trip('88888888-8888-8888-8888-888888888888'::uuid, 6::smallint);
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM LIKE '%INVALID_RATING%' THEN
            v_err_caught := TRUE;
        END IF;
    END;
    IF NOT v_err_caught THEN
        RAISE EXCEPTION '6-star rating was not rejected';
    END IF;

    -- 4. Rider rates Driver 5 stars
    v_res := public.mobility_rate_trip('88888888-8888-8888-8888-888888888888'::uuid, 5::smallint, 'Excelente conductor, auto muy limpio');
    IF (v_res->>'rating')::int <> 5 OR (v_res->>'subject_id')::uuid <> '22222222-2222-2222-2222-222222222222'::uuid THEN
        RAISE EXCEPTION 'Rider rating result unexpected: %', v_res;
    END IF;

    -- 5. Rider attempts to rate again -> ALREADY_RATED
    v_err_caught := FALSE;
    BEGIN
        PERFORM public.mobility_rate_trip('88888888-8888-8888-8888-888888888888'::uuid, 4::smallint);
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM LIKE '%ALREADY_RATED%' THEN
            v_err_caught := TRUE;
        END IF;
    END;
    IF NOT v_err_caught THEN
        RAISE EXCEPTION 'Duplicate rating was not rejected';
    END IF;

    -- 6. Driver rates Rider 5 stars
    PERFORM set_config('request.jwt.claim.sub', '22222222-2222-2222-2222-222222222222', true);
    v_res := public.mobility_rate_trip('88888888-8888-8888-8888-888888888888'::uuid, 5::smallint, 'Excelente pasajero, puntual');
    IF (v_res->>'rating')::int <> 5 OR (v_res->>'subject_id')::uuid <> '11111111-1111-1111-1111-111111111111'::uuid THEN
        RAISE EXCEPTION 'Driver rating result unexpected: %', v_res;
    END IF;
END $$;
SQL
echo "  ✓ Bilateral ratings validated, state-checked, and duplicate-proof"

echo "=== Test 3: Lost Item Reporting (Wave 19) ==="
psql "${psql_args[@]}" <<'SQL'
RESET ROLE;
DO $$
DECLARE
    v_err_caught BOOLEAN := FALSE;
    v_res JSONB;
BEGIN
    SET ROLE authenticated;

    -- 1. Driver tries to report lost item -> FORBIDDEN
    PERFORM set_config('request.jwt.claim.sub', '22222222-2222-2222-2222-222222222222', true);
    BEGIN
        PERFORM public.mobility_report_lost_item('88888888-8888-8888-8888-888888888888'::uuid, 'Olvidé mi celular');
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM LIKE '%FORBIDDEN%' THEN
            v_err_caught := TRUE;
        END IF;
    END;
    IF NOT v_err_caught THEN
        RAISE EXCEPTION 'Driver reporting lost item was not forbidden';
    END IF;

    -- 2. Rider reports lost item
    PERFORM set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', true);
    v_res := public.mobility_report_lost_item(
        '88888888-8888-8888-8888-888888888888'::uuid,
        'Sombrilla azul con mango de madera olvidada en el asiento trasero'
    );
    IF (v_res->>'state') <> 'OPEN' THEN
        RAISE EXCEPTION 'Expected state OPEN, got %', v_res;
    END IF;
END $$;
SQL
echo "  ✓ Lost item case reported and persisted with OPEN state"

echo "=== Test 4: Support Cases Decoupled from Trip (Wave 19) ==="
psql "${psql_args[@]}" <<'SQL'
RESET ROLE;
DO $$
DECLARE
    v_case1 JSONB;
    v_case2 JSONB;
BEGIN
    SET ROLE authenticated;
    PERFORM set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', true);

    -- 1. Case linked to trip
    v_case1 := public.mobility_create_support_case(
        'BILLING_INQUIRY',
        'HIGH',
        '88888888-8888-8888-8888-888888888888'::uuid
    );
    IF (v_case1->>'priority') <> 'HIGH' OR (v_case1->>'state') <> 'OPEN' THEN
        RAISE EXCEPTION 'Case 1 unexpected: %', v_case1;
    END IF;

    -- 2. General case without trip
    v_case2 := public.mobility_create_support_case(
        'APP_FEEDBACK',
        'LOW',
        NULL
    );
    IF (v_case2->>'category') <> 'APP_FEEDBACK' THEN
        RAISE EXCEPTION 'Case 2 unexpected: %', v_case2;
    END IF;
END $$;
SQL
echo "  ✓ Support cases created with priority and optional trip binding"

echo "=== Test 5: Dynamic Surge Calculation (Wave 20) ==="
psql "${psql_args[@]}" <<'SQL'
RESET ROLE;
DO $$
DECLARE
    v_surge JSONB;
    v_num BIGINT;
    v_den BIGINT;
    v_did UUID;
BEGIN
    -- 1. Zero demand, zero supply -> 1 / 1 (1.0x baseline)
    v_surge := public.mobility_calculate_dynamic_surge('CR_SJO', 9.9333, -84.0833);
    v_num := (v_surge->>'surge_numerator')::bigint;
    v_den := (v_surge->>'surge_denominator')::bigint;
    IF v_num <> 1 OR v_den <> 1 THEN
        RAISE EXCEPTION 'Expected 1/1 surge on 0 demand, got %/%', v_num, v_den;
    END IF;

    -- 2. Insert 10 searching ride requests nearby (lat 9.933, lon -84.083) and 0 supply -> max surge (3/1)
    FOR i IN 1..10 LOOP
        INSERT INTO public.ride_requests (
            ride_request_id, rider_id, market_id, service_category_id, dispatch_mode, state,
            pickup_location, pickup_address, destination_location, destination_address, currency_code, correlation_id
        ) VALUES (
            extensions.gen_random_uuid(),
            '11111111-1111-1111-1111-111111111111'::uuid,
            'CR_SJO', 'cat_sjo_standard', 'AUTO_DISPATCH', 'SEARCHING',
            extensions.ST_SetSRID(extensions.ST_MakePoint(-84.0833, 9.9333), 4326), 'Pickup',
            extensions.ST_SetSRID(extensions.ST_MakePoint(-84.0900, 9.9300), 4326), 'Dest',
            'CRC', extensions.gen_random_uuid()
        );
    END LOOP;

    v_surge := public.mobility_calculate_dynamic_surge('CR_SJO', 9.9333, -84.0833);
    v_num := (v_surge->>'surge_numerator')::bigint;
    v_den := (v_surge->>'surge_denominator')::bigint;
    IF v_num <> 3 OR v_den <> 1 THEN
        RAISE EXCEPTION 'Expected max 3/1 surge on 10 demand / 0 supply, got %/%', v_num, v_den;
    END IF;

    -- 3. Insert 5 available drivers with fresh location -> ratio 10 demand / 5 supply = 2.0x (10/5)
    FOR i IN 1..5 LOOP
        v_did := extensions.gen_random_uuid();
        INSERT INTO auth.users (id) VALUES (v_did);
        INSERT INTO public.driver_presence_snapshot (
            driver_id, active_vehicle_id, market_id, current_state,
            location, heading, speed_mps, sequence_id, updated_at
        ) VALUES (
            v_did, '33333333-3333-3333-3333-333333333333'::uuid,
            'CR_SJO', 'AVAILABLE',
            extensions.ST_SetSRID(extensions.ST_MakePoint(-84.0833, 9.9333), 4326),
            0.0, 0.0, 1, clock_timestamp()
        );
    END LOOP;

    v_surge := public.mobility_calculate_dynamic_surge('CR_SJO', 9.9333, -84.0833);
    v_num := (v_surge->>'surge_numerator')::bigint;
    v_den := (v_surge->>'surge_denominator')::bigint;
    IF v_num <> 10 OR v_den <> 5 THEN
        RAISE EXCEPTION 'Expected 10/5 surge on 10 demand / 5 supply, got %/%', v_num, v_den;
    END IF;
END $$;
SQL
echo "  ✓ Dynamic rational surge multiplier calculated from live spatial supply/demand"

echo "=== Test 6: Direct Mutation Revocation & Grants ==="
psql "${psql_args[@]}" <<'SQL'
RESET ROLE;
DO $$
DECLARE
    v_err_caught BOOLEAN := FALSE;
BEGIN
    SET ROLE authenticated;
    PERFORM set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', true);

    -- 1. Direct INSERT into trip_messages must fail
    BEGIN
        INSERT INTO public.trip_messages (trip_id, sender_id, message_type, body)
        VALUES ('88888888-8888-8888-8888-888888888888'::uuid, '11111111-1111-1111-1111-111111111111'::uuid, 'TEXT', 'Hack');
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM LIKE '%permission denied%' THEN
            v_err_caught := TRUE;
        END IF;
    END;
    IF NOT v_err_caught THEN
        RAISE EXCEPTION 'Direct INSERT on trip_messages was not denied';
    END IF;

    -- 2. Direct INSERT into trip_ratings must fail
    v_err_caught := FALSE;
    BEGIN
        INSERT INTO public.trip_ratings (trip_id, reviewer_id, subject_id, rating)
        VALUES ('88888888-8888-8888-8888-888888888888'::uuid, '11111111-1111-1111-1111-111111111111'::uuid, '22222222-2222-2222-2222-222222222222'::uuid, 5);
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM LIKE '%permission denied%' THEN
            v_err_caught := TRUE;
        END IF;
    END;
    IF NOT v_err_caught THEN
        RAISE EXCEPTION 'Direct INSERT on trip_ratings was not denied';
    END IF;

    -- 3. Direct INSERT into market_policy_configurations must fail
    v_err_caught := FALSE;
    BEGIN
        INSERT INTO public.market_policy_configurations (market_id) VALUES ('CR_SJO');
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM LIKE '%permission denied%' THEN
            v_err_caught := TRUE;
        END IF;
    END;
    IF NOT v_err_caught THEN
        RAISE EXCEPTION 'Direct INSERT on market_policy_configurations was not denied';
    END IF;
END $$;
SQL
echo "  ✓ Direct table mutations strictly denied on all Wave 18–20 tables"

echo "================================================================="
echo "ALL MOBILITY COMMS, REPUTATION & SURGE TESTS PASSED (6/6)"
echo "================================================================="
