#!/usr/bin/env bash
set -euo pipefail

# ─────────────────────────────────────────────────────────────────────────────
# ELYSIUM GLOBAL MOBILITY OS — SAFETY CENTER, RESERVE & GUEST RIDES TEST SUITE
# Mandate: ORDEN MAESTRA V6 (Waves 15–17 Authority & Anomaly Verification)
# ─────────────────────────────────────────────────────────────────────────────

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

required_commands=(initdb pg_ctl psql)
for command_name in "${required_commands[@]}"; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Mobility safety & reserve test: SKIP ($command_name unavailable)"
    exit 0
  fi
done

runtime_dir="$(mktemp -d "${TMPDIR:-/tmp}/meet-mobility-safe.XXXXXX")"
cluster_dir="$runtime_dir/data"
socket_dir="$runtime_dir/socket"
server_log="$runtime_dir/postgres.log"
port="$((59000 + RANDOM % 500))"
mkdir -p "$socket_dir"

cleanup() {
  if [[ -d "$cluster_dir" ]]; then
    pg_ctl -D "$cluster_dir" stop -m fast >/dev/null 2>&1 || true
  fi
  if [[ -d "$runtime_dir" && "$(basename "$runtime_dir")" == meet-mobility-safe.* ]]; then
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

echo "=== 3. Applying Mobility Authority Migrations V6 (Waves 0–17) ==="
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906000000_mobility_market_authority.sql"
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906010000_mobility_financial_authority.sql"
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906020000_mobility_safety_and_reserve.sql"

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

-- Seed Policy for Scheduled Rides
INSERT INTO public.scheduled_ride_policies (
    market_id, min_lead_time_minutes, max_lead_time_days, dispatch_lead_time_minutes, cancellation_free_window_minutes
) VALUES (
    'CR_SJO', 30, 30, 25, 60
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
SQL

echo "=== Test 1: Uber Reserve Lead Time Validation ==="
# Test A: Lead time too short (< 30 minutes)
psql "${psql_args[@]}" <<'SQL'
DO $$
DECLARE
    v_err_caught BOOLEAN := FALSE;
BEGIN
    SET ROLE authenticated;
    PERFORM set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', true);

    BEGIN
        PERFORM public.mobility_schedule_ride(
            'CR_SJO', 'cat_sjo_standard',
            clock_timestamp() + INTERVAL '10 minutes',
            '00000000-0000-0000-0000-000000000001'::uuid,
            '00000000-0000-0000-0000-000000000002'::uuid,
            9.9333, -84.0833, 'San José Centro',
            9.9981, -84.2041, 'Aeropuerto SJO',
            'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'::uuid
        );
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM LIKE '%SCHEDULED_TIME_TOO_SOON%' THEN
            v_err_caught := TRUE;
        ELSE
            RAISE EXCEPTION 'Unexpected error: %', SQLERRM;
        END IF;
    END;

    IF NOT v_err_caught THEN
        RAISE EXCEPTION 'Test 1A Failed: Scheduled ride too soon should have been rejected';
    END IF;
END $$;
SQL
echo "  ✓ SCHEDULED_TIME_TOO_SOON rejected successfully (< 30 mins)"

# Test B: Lead time too far (> 30 days)
psql "${psql_args[@]}" <<'SQL'
DO $$
DECLARE
    v_err_caught BOOLEAN := FALSE;
BEGIN
    SET ROLE authenticated;
    PERFORM set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', true);

    BEGIN
        PERFORM public.mobility_schedule_ride(
            'CR_SJO', 'cat_sjo_standard',
            clock_timestamp() + INTERVAL '35 days',
            '00000000-0000-0000-0000-000000000001'::uuid,
            '00000000-0000-0000-0000-000000000002'::uuid,
            9.9333, -84.0833, 'San José Centro',
            9.9981, -84.2041, 'Aeropuerto SJO',
            'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'::uuid
        );
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM LIKE '%SCHEDULED_TIME_TOO_FAR%' THEN
            v_err_caught := TRUE;
        ELSE
            RAISE EXCEPTION 'Unexpected error: %', SQLERRM;
        END IF;
    END;

    IF NOT v_err_caught THEN
        RAISE EXCEPTION 'Test 1B Failed: Scheduled ride too far should have been rejected';
    END IF;
END $$;
SQL
echo "  ✓ SCHEDULED_TIME_TOO_FAR rejected successfully (> 30 days)"

echo "=== Test 2: Uber Reserve Booking & Idempotency ==="
psql "${psql_args[@]}" <<'SQL'
RESET ROLE;
DO $$
DECLARE
    v_quote_res JSONB;
    v_quote_id UUID;
    v_auth_res JSONB;
    v_auth_id UUID;
    v_sched_res JSONB;
    v_sched_id UUID;
    v_req_id UUID;
    v_cached_res JSONB;
    v_pickup_time TIMESTAMPTZ := clock_timestamp() + INTERVAL '2 hours';
    v_idemp UUID := 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'::uuid;
    v_err_caught BOOLEAN := FALSE;
BEGIN
    SET ROLE authenticated;
    PERFORM set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', true);

    -- 1. Generate Quote
    v_quote_res := public.mobility_generate_quote(
        'CR_SJO', 'cat_sjo_standard',
        15000, 1800
    );
    v_quote_id := (v_quote_res->'quote'->>'quote_id')::uuid;

    -- 2. Authorize Payment
    v_auth_res := public.mobility_authorize_payment(
        '11111111-1111-1111-1111-111111111111'::uuid,
        'CARD_TOKEN',
        (v_quote_res->'quote'->>'total_fare_minor')::bigint,
        'CRC',
        'auth_ref_reserve_test'
    );
    v_auth_id := (v_auth_res->'authorization'->>'payment_authorization_id')::uuid;

    -- 3. Schedule Ride
    v_sched_res := public.mobility_schedule_ride(
        'CR_SJO', 'cat_sjo_standard',
        v_pickup_time,
        v_quote_id,
        v_auth_id,
        9.9333, -84.0833, 'San José Centro',
        9.9981, -84.2041, 'Aeropuerto SJO',
        v_idemp
    );
    v_sched_id := (v_sched_res->'reservation'->>'reservation_id')::uuid;
    v_req_id := (v_sched_res->>'ride_request_id')::uuid;

    IF v_sched_id IS NULL OR v_req_id IS NULL THEN
        RAISE EXCEPTION 'Scheduling failed: null reservation or request ID';
    END IF;

    -- 4. Verify Idempotency replay returns same response
    v_cached_res := public.mobility_schedule_ride(
        'CR_SJO', 'cat_sjo_standard',
        v_pickup_time,
        v_quote_id,
        v_auth_id,
        9.9333, -84.0833, 'San José Centro',
        9.9981, -84.2041, 'Aeropuerto SJO',
        v_idemp
    );

    IF (v_cached_res->'reservation'->>'reservation_id')::uuid <> v_sched_id THEN
        RAISE EXCEPTION 'Idempotency replay failed: expected %, got %', v_sched_id, (v_cached_res->'reservation'->>'reservation_id');
    END IF;

    -- 5. Verify Idempotency key reuse with different payload fails
    BEGIN
        PERFORM public.mobility_schedule_ride(
            'CR_SJO', 'cat_sjo_standard',
            v_pickup_time + INTERVAL '1 hour',
            v_quote_id,
            v_auth_id,
            9.9333, -84.0833, 'San José Centro',
            9.9981, -84.2041, 'Aeropuerto SJO',
            v_idemp
        );
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM LIKE '%IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD%' THEN
            v_err_caught := TRUE;
        ELSE
            RAISE EXCEPTION 'Unexpected error on payload mismatch: %', SQLERRM;
        END IF;
    END;

    IF NOT v_err_caught THEN
        RAISE EXCEPTION 'Test 2 Failed: Idempotency conflict not raised for different payload';
    END IF;
END $$;
SQL
echo "  ✓ Scheduled ride booked, confirmed, and idempotency verified"

echo "=== Test 3: Background Scheduled Rides Worker (SKIP LOCKED) ==="
psql "${psql_args[@]}" <<'SQL'
RESET ROLE;
DO $$
DECLARE
    v_worker_res JSONB;
    v_dispatched INT;
    v_req_state TEXT;
    v_res_state TEXT;
BEGIN
    -- Backdate dispatch_at on confirmed reservation to make it due
    UPDATE public.scheduled_ride_reservations
    SET dispatch_at = clock_timestamp() - INTERVAL '5 minutes'
    WHERE state = 'CONFIRMED';

    -- Call worker
    v_worker_res := public.mobility_dispatch_due_scheduled_rides(10);
    v_dispatched := (v_worker_res->>'dispatched_count')::int;

    IF v_dispatched <> 1 THEN
        RAISE EXCEPTION 'Expected 1 dispatched reservation, got %', v_dispatched;
    END IF;

    -- Verify states
    SELECT state INTO v_res_state FROM public.scheduled_ride_reservations LIMIT 1;
    IF v_res_state <> 'DISPATCHING' THEN
        RAISE EXCEPTION 'Expected reservation state DISPATCHING, got %', v_res_state;
    END IF;

    SELECT state INTO v_req_state FROM public.ride_requests WHERE scheduled_for IS NOT NULL LIMIT 1;
    IF v_req_state <> 'SEARCHING' THEN
        RAISE EXCEPTION 'Expected request state SEARCHING, got %', v_req_state;
    END IF;

    -- Calling worker again should find 0 due reservations
    v_worker_res := public.mobility_dispatch_due_scheduled_rides(10);
    v_dispatched := (v_worker_res->>'dispatched_count')::int;
    IF v_dispatched <> 0 THEN
        RAISE EXCEPTION 'Expected 0 dispatched reservations on second pass, got %', v_dispatched;
    END IF;
END $$;
SQL
echo "  ✓ Scheduled rides worker processed due reservation atomically"

echo "=== Test 4: DiDi Guest Ride (Viaje para Terceros) ==="
# Test A: Invalid Name and Phone
psql "${psql_args[@]}" <<'SQL'
RESET ROLE;
DO $$
DECLARE
    v_err_caught BOOLEAN := FALSE;
BEGIN
    SET ROLE authenticated;
    PERFORM set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', true);

    -- Blank name
    BEGIN
        PERFORM public.mobility_request_guest_ride(
            'CR_SJO', 'cat_sjo_standard', '   ', '+50688889999',
            9.9333, -84.0833, 'Parque Central', 9.9281, -84.0907, 'Hospital',
            250000, extensions.gen_random_uuid()
        );
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM LIKE '%INVALID_GUEST_NAME%' THEN
            v_err_caught := TRUE;
        END IF;
    END;

    IF NOT v_err_caught THEN
        RAISE EXCEPTION 'Blank guest name was not rejected';
    END IF;

    -- Invalid phone format
    v_err_caught := FALSE;
    BEGIN
        PERFORM public.mobility_request_guest_ride(
            'CR_SJO', 'cat_sjo_standard', 'Carlos', '88889999', -- Missing leading '+'
            9.9333, -84.0833, 'Parque Central', 9.9281, -84.0907, 'Hospital',
            250000, extensions.gen_random_uuid()
        );
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM LIKE '%INVALID_E164_PHONE_NUMBER%' THEN
            v_err_caught := TRUE;
        END IF;
    END;

    IF NOT v_err_caught THEN
        RAISE EXCEPTION 'Non-E164 phone number was not rejected';
    END IF;
END $$;
SQL
echo "  ✓ Guest name and E.164 phone validation enforced"

# Test B: Valid Guest Ride Request
psql "${psql_args[@]}" <<'SQL'
RESET ROLE;
DO $$
DECLARE
    v_guest_res JSONB;
    v_token TEXT;
    v_guest_id UUID;
    v_count INT;
BEGIN
    SET ROLE authenticated;
    PERFORM set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', true);

    v_guest_res := public.mobility_request_guest_ride(
        'CR_SJO', 'cat_sjo_standard', 'Doña Elena Rodríguez', '+50687654321',
        9.9333, -84.0833, 'Residencia Elena', 9.9281, -84.0907, 'Clinica Biblica',
        300000, 'cccccccc-cccc-cccc-cccc-cccccccccccc'::uuid
    );

    v_token := v_guest_res->>'tracking_token';
    v_guest_id := (v_guest_res->>'guest_ride_id')::uuid;

    IF v_token IS NULL OR length(v_token) <> 64 THEN
        RAISE EXCEPTION 'Expected 64-char SHA256 tracking token, got %', v_token;
    END IF;

    SELECT count(*) INTO v_count FROM public.guest_ride_profiles WHERE guest_ride_id = v_guest_id;
    IF v_count <> 1 THEN
        RAISE EXCEPTION 'Guest ride profile not persisted in database';
    END IF;
END $$;
SQL
echo "  ✓ Guest ride requested, persisted, and secure tracking token generated"

echo "=== Test 5: DiDi Phone Masking / Virtual Proxy Channel ==="
psql "${psql_args[@]}" <<'SQL'
RESET ROLE;
DO $$
DECLARE
    v_trip_id UUID;
    v_req_id UUID;
    v_chan_res1 JSONB;
    v_chan_res2 JSONB;
    v_proxy1 TEXT;
    v_proxy2 TEXT;
    v_err_caught BOOLEAN := FALSE;
BEGIN
    SELECT ride_request_id INTO v_req_id FROM public.guest_ride_profiles LIMIT 1;

    -- 1. Create a trip under admin role for testing
    INSERT INTO public.trips (
        trip_id, ride_request_id, rider_id, driver_id, vehicle_id, state
    ) VALUES (
        '55555555-5555-5555-5555-555555555555'::uuid,
        v_req_id,
        '11111111-1111-1111-1111-111111111111'::uuid,
        '22222222-2222-2222-2222-222222222222'::uuid,
        '33333333-3333-3333-3333-333333333333'::uuid,
        'IN_PROGRESS'
    );

    -- 2. Unauthorized 3rd party tries to get masked channel -> FORBIDDEN
    SET ROLE authenticated;
    PERFORM set_config('request.jwt.claim.sub', '44444444-4444-4444-4444-444444444444', true);
    BEGIN
        PERFORM public.mobility_get_masked_channel('55555555-5555-5555-5555-555555555555'::uuid);
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM LIKE '%FORBIDDEN%' THEN
            v_err_caught := TRUE;
        END IF;
    END;

    IF NOT v_err_caught THEN
        RAISE EXCEPTION 'Unauthorized third party was not forbidden from masked channel';
    END IF;

    -- 3. Rider requests masked channel
    PERFORM set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', true);
    v_chan_res1 := public.mobility_get_masked_channel('55555555-5555-5555-5555-555555555555'::uuid);
    v_proxy1 := v_chan_res1->>'virtual_proxy_number';

    -- 4. Driver requests masked channel -> receives identical proxy channel
    PERFORM set_config('request.jwt.claim.sub', '22222222-2222-2222-2222-222222222222', true);
    v_chan_res2 := public.mobility_get_masked_channel('55555555-5555-5555-5555-555555555555'::uuid);
    v_proxy2 := v_chan_res2->>'virtual_proxy_number';

    IF v_proxy1 <> v_proxy2 THEN
        RAISE EXCEPTION 'Mismatch in proxy numbers: % vs %', v_proxy1, v_proxy2;
    END IF;
END $$;
SQL
echo "  ✓ Masked channel authorization verified and virtual proxy shared"

echo "=== Test 6: DiDi Safety Center SOS Button & Emergency Contacts ==="
psql "${psql_args[@]}" <<'SQL'
RESET ROLE;
DO $$
DECLARE
    v_sos_res JSONB;
    v_notified_count INT;
    v_event_id UUID;
    v_err_caught BOOLEAN := FALSE;
BEGIN
    SET ROLE authenticated;
    PERFORM set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', true);

    -- 1. Rider configures 2 emergency contacts (1 with notify_on_sos = true, 1 with false)
    INSERT INTO public.safety_emergency_contacts (user_id, name, phone_e164, notify_on_sos)
    VALUES ('11111111-1111-1111-1111-111111111111'::uuid, 'Mama', '+50688880001', true);

    INSERT INTO public.safety_emergency_contacts (user_id, name, phone_e164, notify_on_sos)
    VALUES ('11111111-1111-1111-1111-111111111111'::uuid, 'Compañero Trabajo', '+50688880002', false);

    -- 2. Unauthorized third party tries to trigger SOS -> FORBIDDEN
    PERFORM set_config('request.jwt.claim.sub', '44444444-4444-4444-4444-444444444444', true);
    BEGIN
        PERFORM public.mobility_trigger_emergency_sos('55555555-5555-5555-5555-555555555555'::uuid, 9.9333, -84.0833, 12.5);
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM LIKE '%FORBIDDEN%' THEN
            v_err_caught := TRUE;
        END IF;
    END;

    IF NOT v_err_caught THEN
        RAISE EXCEPTION 'Third party was not forbidden from triggering SOS';
    END IF;

    -- 3. Rider triggers SOS
    PERFORM set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', true);
    v_sos_res := public.mobility_trigger_emergency_sos('55555555-5555-5555-5555-555555555555'::uuid, 9.9333, -84.0833, 12.5);
    v_notified_count := (v_sos_res->>'contacts_notified_count')::int;
    v_event_id := (v_sos_res->>'event_id')::uuid;

    IF v_notified_count <> 1 THEN
        RAISE EXCEPTION 'Expected 1 contact notified, got %', v_notified_count;
    END IF;

    IF v_event_id IS NULL THEN
        RAISE EXCEPTION 'SOS Event ID is null';
    END IF;
END $$;
SQL
echo "  ✓ Emergency SOS triggered, contacts counted, and event persisted"

echo "=== Test 7: DiDi Route Telemetry & Anomaly / Risk Zone Detection ==="
psql "${psql_args[@]}" <<'SQL'
RESET ROLE;
-- Seed Risk Zone (Barrio Peligroso)
INSERT INTO public.mobility_risk_zones (
    zone_id, market_id, name, severity, polygon, active
) VALUES (
    '66666666-6666-6666-6666-666666666666'::uuid,
    'CR_SJO',
    'Zona Roja San José Sur',
    'CRITICAL',
    extensions.ST_SetSRID(extensions.ST_MakePoint(-84.0833, 9.9333), 4326),
    true
);

DO $$
DECLARE
    v_tel_res JSONB;
    v_dev_logged BOOLEAN;
    v_sos_triggered BOOLEAN;
    v_in_risk_zone BOOLEAN;
    v_zone_name TEXT;
    v_zone_sev TEXT;
    v_log_count INT;
    v_sos_count INT;
BEGIN
    SET ROLE authenticated;
    PERFORM set_config('request.jwt.claim.sub', '22222222-2222-2222-2222-222222222222', true);

    -- 1. Normal route position (distance = 120m, far from risk zone at 9.980, -84.150)
    v_tel_res := public.mobility_record_route_telemetry(
        '55555555-5555-5555-5555-555555555555'::uuid,
        9.9800, -84.1500, 15.0, 120.0
    );

    IF (v_tel_res->>'deviation_detected')::boolean <> FALSE OR
       (v_tel_res->>'sos_triggered')::boolean <> FALSE OR
       (v_tel_res->>'inside_risk_zone')::boolean <> FALSE THEN
        RAISE EXCEPTION 'Normal telemetry failed expected clean result: %', v_tel_res;
    END IF;

    -- 2. Mild deviation (distance = 650m, threshold is 500m)
    v_tel_res := public.mobility_record_route_telemetry(
        '55555555-5555-5555-5555-555555555555'::uuid,
        9.9800, -84.1500, 15.0, 650.0
    );

    IF (v_tel_res->>'deviation_detected')::boolean <> TRUE OR
       (v_tel_res->>'sos_triggered')::boolean <> FALSE THEN
        RAISE EXCEPTION 'Mild deviation failed expected deviation_detected=true, sos_triggered=false: %', v_tel_res;
    END IF;

    SELECT count(*) INTO v_log_count FROM public.route_deviation_logs WHERE trip_id = '55555555-5555-5555-5555-555555555555'::uuid;
    IF v_log_count <> 1 THEN
        RAISE EXCEPTION 'Expected 1 route deviation log, got %', v_log_count;
    END IF;

    -- 3. Severe deviation anomaly (distance = 1800m, triggers auto SOS)
    v_tel_res := public.mobility_record_route_telemetry(
        '55555555-5555-5555-5555-555555555555'::uuid,
        9.9800, -84.1500, 15.0, 1800.0
    );

    IF (v_tel_res->>'deviation_detected')::boolean <> TRUE OR
       (v_tel_res->>'sos_triggered')::boolean <> TRUE THEN
        RAISE EXCEPTION 'Severe anomaly failed expected deviation_detected=true, sos_triggered=true: %', v_tel_res;
    END IF;

    SELECT count(*) INTO v_sos_count FROM public.safety_emergency_events
    WHERE trip_id = '55555555-5555-5555-5555-555555555555'::uuid AND event_type = 'ROUTE_DEVIATION';
    IF v_sos_count <> 1 THEN
        RAISE EXCEPTION 'Expected 1 automated ROUTE_DEVIATION SOS event, got %', v_sos_count;
    END IF;

    -- 4. Location inside Critical Risk Zone (lat 9.9333, lon -84.0833)
    v_tel_res := public.mobility_record_route_telemetry(
        '55555555-5555-5555-5555-555555555555'::uuid,
        9.9333, -84.0833, 10.0, 50.0
    );

    IF (v_tel_res->>'inside_risk_zone')::boolean <> TRUE THEN
        RAISE EXCEPTION 'Failed to detect risk zone: %', v_tel_res;
    END IF;

    v_zone_name := v_tel_res->>'risk_zone_name';
    v_zone_sev := v_tel_res->>'risk_zone_severity';

    IF v_zone_name <> 'Zona Roja San José Sur' OR v_zone_sev <> 'CRITICAL' THEN
        RAISE EXCEPTION 'Unexpected risk zone details: % / %', v_zone_name, v_zone_sev;
    END IF;
END $$;
SQL
echo "  ✓ Telemetry deviation logs, automated severe SOS, and risk zone interception verified"

echo "=== Test 8: Direct Mutation Revocation & Emergency Contacts RLS ==="
psql "${psql_args[@]}" <<'SQL'
RESET ROLE;
DO $$
DECLARE
    v_err_caught BOOLEAN := FALSE;
    v_visible_count INT;
BEGIN
    SET ROLE authenticated;
    PERFORM set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', true);

    -- 1. Direct INSERT into scheduled_ride_reservations must fail
    BEGIN
        INSERT INTO public.scheduled_ride_reservations (
            ride_request_id, rider_id, market_id, scheduled_pickup_time, dispatch_at, state, quote_id, payment_authorization_id
        ) VALUES (
            extensions.gen_random_uuid(), '11111111-1111-1111-1111-111111111111'::uuid, 'CR_SJO',
            clock_timestamp(), clock_timestamp(), 'CONFIRMED', extensions.gen_random_uuid(), extensions.gen_random_uuid()
        );
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM LIKE '%permission denied%' THEN
            v_err_caught := TRUE;
        END IF;
    END;

    IF NOT v_err_caught THEN
        RAISE EXCEPTION 'Direct INSERT on scheduled_ride_reservations was not denied';
    END IF;

    -- 2. Direct INSERT into safety_emergency_events must fail
    v_err_caught := FALSE;
    BEGIN
        INSERT INTO public.safety_emergency_events (
            trip_id, triggered_by, event_type, location, latitude, longitude, state
        ) VALUES (
            '55555555-5555-5555-5555-555555555555'::uuid, '11111111-1111-1111-1111-111111111111'::uuid,
            'SOS_BUTTON', extensions.ST_SetSRID(extensions.ST_MakePoint(0,0), 4326), 0, 0, 'TRIGGERED'
        );
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM LIKE '%permission denied%' THEN
            v_err_caught := TRUE;
        END IF;
    END;

    IF NOT v_err_caught THEN
        RAISE EXCEPTION 'Direct INSERT on safety_emergency_events was not denied';
    END IF;

    -- 3. RLS Isolation on safety_emergency_contacts: User 4444... should NOT see User 1111...'s contacts
    PERFORM set_config('request.jwt.claim.sub', '44444444-4444-4444-4444-444444444444', true);
    SELECT count(*) INTO v_visible_count FROM public.safety_emergency_contacts;

    IF v_visible_count <> 0 THEN
        RAISE EXCEPTION 'RLS Leak: User 4444 saw % contacts belonging to User 1111', v_visible_count;
    END IF;
END $$;
SQL
echo "  ✓ Direct table mutations denied and Emergency Contacts RLS strictly isolated"

echo "================================================================="
echo "ALL MOBILITY SAFETY, RESERVE & GUEST RIDES TESTS PASSED (8/8)"
echo "================================================================="
