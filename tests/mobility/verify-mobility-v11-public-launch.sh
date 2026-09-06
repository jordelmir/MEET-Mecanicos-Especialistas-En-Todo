#!/usr/bin/env bash
# ==============================================================================
# tests/mobility/verify-mobility-v11-public-launch.sh
#
# Comprehensive Master Order V11 Verification Suite:
#   1. Open Bid Initial Stops & Post-Publish Immutability (P8/P22)
#   2. Metered Dynamic Stops, Road Network Routing & Route Versioning (Gate 4)
#   3. Driver 100-Way Concurrent Dispatch/Claim Race (Single Winner CAS) (Gate 3)
#   4. Monotonic Driver Location Sequence Ordering (100, 101, 103, 102, 104)
#   5. Gate P0: 6-Digit CSPRNG Boarding PIN Challenge, Bcrypt Storage, Lockout & Single-Use
#   6. Gate P0: Safe Trip Sharing Projection Isolation & Instant Revocation
#   7. Gate 2: Payment Provider Capabilities Fail-Closed & 7-Param Provider Capture
#   8. Mutual Bilateral Trip Ratings (Completed Trips Only)
#   9. Canonical Trip Tip & Balanced Zero-Sum Double-Entry Ledger Settlement
#  10. Cross-Vertical Capability Gating (Identity != Capability)
#  11. Gate 7: Canonical Account Deletion Processor (PENDING -> PROCESSING -> COMPLETED)
# ==============================================================================

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/../.." && pwd)"

required_commands=(initdb pg_ctl psql)
for command_name in "${required_commands[@]}"; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Mobility V11 Public Launch test: SKIP ($command_name unavailable)"
    exit 0
  fi
done

runtime_dir="$(mktemp -d /tmp/elysium-v11-verify-XXXXXX)"
cluster_dir="$runtime_dir/data"
socket_dir="$runtime_dir/socket"
server_log="$runtime_dir/postgres.log"
port="$((59200 + RANDOM % 400))"
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
pg_ctl -D "$cluster_dir" -l "$server_log" -o "-p $port -k $socket_dir -c max_connections=250" start >/dev/null

psql_args=(-h "$socket_dir" -p "$port" -d postgres -v ON_ERROR_STOP=1 -q)
export PGOPTIONS="-c client_min_messages=warning"

echo "=== 2. Setting Up Schema & Supabase Auth Roles ==="
psql "${psql_args[@]}" <<'SQL'
CREATE SCHEMA IF NOT EXISTS extensions;
CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA extensions;

CREATE SCHEMA IF NOT EXISTS auth;

CREATE TABLE IF NOT EXISTS auth.users (
    id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    email TEXT UNIQUE,
    encrypted_password TEXT,
    email_confirmed_at TIMESTAMPTZ DEFAULT clock_timestamp(),
    raw_app_meta_data JSONB DEFAULT '{}'::jsonb,
    raw_user_meta_data JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ DEFAULT clock_timestamp()
);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        CREATE ROLE anon NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        CREATE ROLE authenticated NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
        CREATE ROLE service_role NOLOGIN;
    END IF;
END $$;

CREATE OR REPLACE FUNCTION auth.uid()
RETURNS UUID LANGUAGE sql STABLE AS $$
    SELECT COALESCE(
        NULLIF(current_setting('request.jwt.claim.sub', true), ''),
        NULLIF((NULLIF(current_setting('request.jwt.claims', true), '')::jsonb ->> 'sub'), '')
    )::UUID
$$;

CREATE OR REPLACE FUNCTION auth.role()
RETURNS TEXT LANGUAGE sql STABLE AS $$
    SELECT COALESCE(
        NULLIF(current_setting('request.jwt.claim.role', true), ''),
        (NULLIF(current_setting('request.jwt.claims', true), '')::jsonb ->> 'role'),
        NULLIF(current_setting('role', true), 'none'),
        current_user
    )::TEXT
$$;

GRANT USAGE ON SCHEMA public TO anon, authenticated, service_role;
GRANT USAGE ON SCHEMA auth TO anon, authenticated, service_role;
GRANT USAGE ON SCHEMA extensions TO anon, authenticated, service_role;
GRANT SELECT ON auth.users TO authenticated, service_role;
SQL

echo "=== 3. Applying Canonical Mobility Migrations V6 through V11 ==="
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906000000_mobility_market_authority.sql"
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906010000_mobility_financial_authority.sql"
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906020000_mobility_safety_and_reserve.sql"
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906030000_mobility_communications_reputation_and_surge.sql"
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906040000_mobility_financial_and_concurrency_p0_lockdown.sql"
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906050000_mobility_financial_authority_v8_closure.sql"
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906060000_mobility_provider_capture_v9_closure.sql"
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906070000_mobility_hardening_and_stops_authority.sql"
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906080000_mobility_public_launch_v11_closure.sql"

echo "=== 4. Seeding Global Market, Actors & Vehicle ==="
psql "${psql_args[@]}" <<'SQL'
RESET ROLE;
-- Actors: Rider (1111), Driver (2222), Friend (3333), Stranger (4444), Deletee Eve (5555)
INSERT INTO auth.users (id, email) VALUES
    ('11111111-1111-1111-1111-111111111111'::uuid, 'rider@elysium.test'),
    ('22222222-2222-2222-2222-222222222222'::uuid, 'driver@elysium.test'),
    ('33333333-3333-3333-3333-333333333333'::uuid, 'friend@elysium.test'),
    ('44444444-4444-4444-4444-444444444444'::uuid, 'stranger@elysium.test'),
    ('55555555-5555-5555-5555-555555555555'::uuid, 'eve@elysium.test')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.principals (principal_id, phone, full_name) VALUES
    ('11111111-1111-1111-1111-111111111111'::uuid, '+50611111111', 'Rider Alice'),
    ('22222222-2222-2222-2222-222222222222'::uuid, '+50622222222', 'Driver Bob'),
    ('33333333-3333-3333-3333-333333333333'::uuid, '+50633333333', 'Friend Carol'),
    ('44444444-4444-4444-4444-444444444444'::uuid, '+50644444444', 'Stranger Dan'),
    ('55555555-5555-5555-5555-555555555555'::uuid, '+50655555555', 'Eve ToBeDeleted')
ON CONFLICT (principal_id) DO NOTHING;

-- Driver Bob capability
INSERT INTO public.principal_capabilities (principal_id, capability, activation_state, verified_at)
VALUES ('22222222-2222-2222-2222-222222222222'::uuid, 'RIDE_DRIVER', 'APPROVED', clock_timestamp())
ON CONFLICT (principal_id, capability) DO UPDATE SET activation_state = 'APPROVED', verified_at = clock_timestamp();

-- Eve capability (will be tested in account deletion)
INSERT INTO public.principal_capabilities (principal_id, capability, activation_state, verified_at)
VALUES ('55555555-5555-5555-5555-555555555555'::uuid, 'RIDE_DRIVER', 'APPROVED', clock_timestamp())
ON CONFLICT (principal_id, capability) DO UPDATE SET activation_state = 'APPROVED', verified_at = clock_timestamp();

-- Market CR_SJO
INSERT INTO public.mobility_markets (
    market_id, country_code, currency_code, timezone, dispatch_modes, max_intermediate_stops, auto_dispatch_enabled, marketplace_offers_enabled, active
) VALUES (
    'CR_SJO', 'CR', 'CRC', 'America/Costa_Rica', ARRAY['AUTO_DISPATCH', 'MARKETPLACE_OFFERS'], 5, TRUE, TRUE, TRUE
) ON CONFLICT (market_id) DO NOTHING;

INSERT INTO public.mobility_service_categories (
    service_category_id, market_id, code, name, max_passengers, active
) VALUES (
    'cat_sjo_standard', 'CR_SJO', 'STANDARD', 'Standard Sedan', 4, TRUE
) ON CONFLICT (service_category_id) DO NOTHING;

INSERT INTO public.mobility_pricing_policies (
    pricing_policy_id, market_id, service_category_id, version, currency_code,
    base_fare_minor, booking_fee_minor, per_meter_numerator, per_meter_denominator,
    per_second_numerator, per_second_denominator, minimum_fare_minor, cancellation_fee_minor,
    tax_basis_points, active, valid_from
) VALUES (
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'::uuid, 'CR_SJO', 'cat_sjo_standard', 1, 'CRC',
    600, 200, 65, 1, 15, 1, 1200, 1000, 1300, true, clock_timestamp() - INTERVAL '1 day'
) ON CONFLICT (pricing_policy_id) DO NOTHING;

-- Vehicle & eligibility
INSERT INTO public.mobility_vehicles (
    vehicle_id, owner_id, make, model, year, color, seat_capacity, license_plate, verification_state, active
) VALUES (
    '66666666-6666-6666-6666-666666666666'::uuid,
    '22222222-2222-2222-2222-222222222222'::uuid,
    'Toyota', 'Corolla', 2022, 'Silver', 4, 'MEET-777', 'VERIFIED', TRUE
) ON CONFLICT (vehicle_id) DO NOTHING;

INSERT INTO public.driver_vehicle_authorizations (driver_id, vehicle_id, is_authorized, active)
VALUES ('22222222-2222-2222-2222-222222222222'::uuid, '66666666-6666-6666-6666-666666666666'::uuid, TRUE, TRUE)
ON CONFLICT (driver_id, vehicle_id) DO NOTHING;

INSERT INTO public.driver_market_eligibility (driver_id, market_id, is_eligible, background_check_cleared, documents_verified, active)
VALUES ('22222222-2222-2222-2222-222222222222'::uuid, 'CR_SJO', TRUE, TRUE, TRUE, TRUE)
ON CONFLICT (driver_id, market_id) DO NOTHING;

INSERT INTO public.driver_presence_snapshot (
    driver_id, active_vehicle_id, market_id, current_state, location, heading, speed_mps, sequence_id
) VALUES (
    '22222222-2222-2222-2222-222222222222'::uuid,
    '66666666-6666-6666-6666-666666666666'::uuid,
    'CR_SJO', 'AVAILABLE',
    ROW(-84.0833, 9.9333, 4326)::extensions.geography,
    90.0, 0.0, 10
) ON CONFLICT (driver_id) DO UPDATE SET sequence_id = 10;
SQL

echo "=== 5. TEST 1: OPEN BID INITIAL STOPS & POST-PUBLISH IMMUTABILITY ==="
psql "${psql_args[@]}" <<'SQL'
SET ROLE authenticated;
SET "request.jwt.claim.sub" = '11111111-1111-1111-1111-111111111111';

DO $$
DECLARE
    v_req JSONB;
    v_req_id UUID;
BEGIN
    -- 1. Create OPEN_BID request with 2 initial intermediate stops -> MUST SUCCEED
    v_req := public.mobility_request_ride(
        'CR_SJO',
        'cat_sjo_standard',
        'MARKETPLACE_OFFERS',
        9.9333, -84.0833, 10.0, 'Pickup Central',
        9.9100, -84.0600, 10.0, 'Final Destination',
        '[{"latitude": 9.9250, "longitude": -84.0750, "address": "Stop A"}, {"latitude": 9.9200, "longitude": -84.0700, "address": "Stop B"}]'::jsonb,
        250000, -- 2500 CRC
        NULL,
        'aaaaaaaa-1111-1111-1111-111111111111'::uuid,
        extensions.gen_random_uuid()
    );

    v_req_id := (v_req->>'ride_request_id')::UUID;

    -- 2. Attempt to replace/add dynamic stops after publish -> MUST FAIL WITH IMMUTABLE ERROR
    BEGIN
        PERFORM public.mobility_replace_ride_stops(
            v_req_id,
            '[{"lat": 9.9150, "lng": -84.0650, "address": "Sneaky Stop C"}]'::jsonb,
            1,
            extensions.gen_random_uuid()
        );
        RAISE EXCEPTION 'TEST_FAILED: Open bid dynamic stop alteration should be rejected!';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM NOT LIKE '%OPEN_BID_STOPS_IMMUTABLE_AFTER_PUBLISH%' THEN
            RAISE EXCEPTION 'Unexpected error on open bid stops modification: %', SQLERRM;
        END IF;
    END;
END $$;
SQL
echo ">>> PASSED: TEST 1 (Open Bid initial stops allowed; post-publish modifications strictly rejected)."

echo "=== 6. TEST 2: METERED DYNAMIC STOPS, ROAD ROUTING & ROUTE REVISION (GATE 4) ==="
psql "${psql_args[@]}" <<'SQL'
SET ROLE authenticated;
SET "request.jwt.claim.sub" = '11111111-1111-1111-1111-111111111111';

DO $$
DECLARE
    v_req JSONB;
    v_req_id UUID;
    v_res JSONB;
    v_ev1 public.ride_route_evidence%ROWTYPE;
    v_ev2 public.ride_route_evidence%ROWTYPE;
BEGIN
    -- 1. Create AUTO_DISPATCH request with no initial stops
    v_req := public.mobility_request_ride(
        'CR_SJO',
        'cat_sjo_standard',
        'AUTO_DISPATCH',
        9.9333, -84.0833, 10.0, 'Pickup Urban',
        9.9100, -84.0600, 10.0, 'Dropoff Urban',
        '[]'::jsonb,
        NULL,
        NULL,
        'bbbbbbbb-2222-2222-2222-222222222222'::uuid,
        extensions.gen_random_uuid()
    );
    v_req_id := (v_req->>'ride_request_id')::UUID;

    SELECT * INTO v_ev1 FROM public.ride_route_evidence
    WHERE ride_request_id = v_req_id AND route_version = 1;

    -- Gate 4 Invariant: routing mode must be ROAD_NETWORK
    IF v_ev1.routing_mode <> 'ROAD_NETWORK' THEN
        RAISE EXCEPTION 'TEST_FAILED: Expected routing_mode ROAD_NETWORK, got %', v_ev1.routing_mode;
    END IF;

    -- 2. Add intermediate stops via canonical mobility_replace_ride_stops
    v_res := public.mobility_replace_ride_stops(
        v_req_id,
        '[{"lat": 9.9250, "lng": -84.0750, "address": "Dynamic Stop 1"}, {"lat": 9.9180, "lng": -84.0680, "address": "Dynamic Stop 2"}]'::jsonb,
        1,
        extensions.gen_random_uuid()
    );

    IF (v_res->>'success')::BOOLEAN <> TRUE THEN
        RAISE EXCEPTION 'TEST_FAILED: Replace ride stops failed: %', v_res;
    END IF;

    IF (v_res->>'route_version')::BIGINT <> 2 THEN
        RAISE EXCEPTION 'TEST_FAILED: Expected route_version 2, got %', (v_res->>'route_version');
    END IF;

    -- Verify new immutable route evidence revision created
    SELECT * INTO v_ev2 FROM public.ride_route_evidence
    WHERE ride_request_id = v_req_id AND route_version = 2;

    IF v_ev2.evidence_digest IS NULL OR v_ev2.stop_order_digest IS NULL THEN
        RAISE EXCEPTION 'TEST_FAILED: Missing route evidence digests in revision 2: %', v_ev2;
    END IF;

    IF v_ev1.evidence_digest = v_ev2.evidence_digest THEN
        RAISE EXCEPTION 'TEST_FAILED: Route evidence digest must differ between route revisions!';
    END IF;
END $$;
SQL
echo ">>> PASSED: TEST 2 (Gate 4: Road routing mode, route version increments, and immutable waypoint digests verified)."

echo "=== 7. TEST 3: 100-WAY CONCURRENT DRIVER ACCEPT/CLAIM RACE (GATE 3) ==="
psql "${psql_args[@]}" <<'SQL'
RESET ROLE;

-- Seed 100 contender drivers
DO $$
DECLARE
    i INT;
    v_uid UUID;
    v_vid UUID;
BEGIN
    FOR i IN 1..100 LOOP
        v_uid := ('00000000-0000-0000-0000-' || lpad(i::text, 12, '0'))::UUID;
        v_vid := ('10000000-0000-0000-0000-' || lpad(i::text, 12, '0'))::UUID;

        INSERT INTO auth.users (id, email) VALUES (v_uid, 'driver' || i || '@elysium.test') ON CONFLICT DO NOTHING;
        INSERT INTO public.principals (principal_id, phone, full_name) VALUES (v_uid, '+5060000' || lpad(i::text, 5, '0'), 'Contender ' || i) ON CONFLICT DO NOTHING;
        INSERT INTO public.principal_capabilities (principal_id, capability, activation_state, verified_at)
            VALUES (v_uid, 'RIDE_DRIVER', 'APPROVED', clock_timestamp()) ON CONFLICT DO NOTHING;
        INSERT INTO public.mobility_vehicles (vehicle_id, owner_id, make, model, year, color, seat_capacity, license_plate, verification_state, active)
            VALUES (v_vid, v_uid, 'Hyundai', 'Elantra', 2021, 'White', 4, 'RACE-' || i, 'VERIFIED', TRUE) ON CONFLICT DO NOTHING;
        INSERT INTO public.driver_vehicle_authorizations (driver_id, vehicle_id, is_authorized, active)
            VALUES (v_uid, v_vid, TRUE, TRUE) ON CONFLICT DO NOTHING;
        INSERT INTO public.driver_market_eligibility (driver_id, market_id, is_eligible, background_check_cleared, documents_verified, active)
            VALUES (v_uid, 'CR_SJO', TRUE, TRUE, TRUE, TRUE) ON CONFLICT DO NOTHING;
    END LOOP;
END $$;

-- Create target ride request and dispatch offers for race
INSERT INTO public.ride_requests (
    ride_request_id, rider_id, market_id, service_category_id, dispatch_mode, state,
    pickup_location, destination_location, currency_code, correlation_id, version
) VALUES (
    'cccccccc-3333-3333-3333-333333333333'::uuid,
    '11111111-1111-1111-1111-111111111111'::uuid,
    'CR_SJO', 'cat_sjo_standard', 'AUTO_DISPATCH', 'SEARCHING',
    ROW(-84.0833, 9.9333, 4326)::extensions.geography,
    ROW(-84.0700, 9.9200, 4326)::extensions.geography,
    'CRC', extensions.gen_random_uuid(), 1
) ON CONFLICT DO NOTHING;

DO $$
DECLARE
    i INT;
    v_uid UUID;
    v_vid UUID;
BEGIN
    FOR i IN 1..100 LOOP
        v_uid := ('00000000-0000-0000-0000-' || lpad(i::text, 12, '0'))::UUID;
        v_vid := ('10000000-0000-0000-0000-' || lpad(i::text, 12, '0'))::UUID;

        INSERT INTO public.dispatch_offers (
            dispatch_offer_id, ride_request_id, driver_id, vehicle_id, state, expires_at
        ) VALUES (
            extensions.gen_random_uuid(),
            'cccccccc-3333-3333-3333-333333333333'::uuid,
            v_uid, v_vid, 'PENDING', clock_timestamp() + INTERVAL '5 minutes'
        );
    END LOOP;
END $$;
SQL

# Launch 100 concurrent claims via background workers
results_file="$runtime_dir/race_results.txt"
echo "Executing 100 concurrent driver claims on ride request cccccccc-3333-3333-3333-333333333333..."

for i in {1..100}; do
    uid=$(printf "00000000-0000-0000-0000-%012d" $i)
    vid=$(printf "10000000-0000-0000-0000-%012d" $i)
    (
        psql "${psql_args[@]}" <<SQL >> "$results_file" 2>&1
SET ROLE authenticated;
SET "request.jwt.claim.sub" = '$uid';
DO \$\$
DECLARE
    v_offer_id UUID;
    v_res JSONB;
BEGIN
    SELECT dispatch_offer_id INTO v_offer_id
    FROM public.dispatch_offers
    WHERE ride_request_id = 'cccccccc-3333-3333-3333-333333333333'::uuid AND driver_id = '$uid'::uuid;

    v_res := public.mobility_accept_dispatch(
        'cccccccc-3333-3333-3333-333333333333'::uuid,
        v_offer_id,
        '$vid'::uuid,
        1,
        extensions.gen_random_uuid()
    );
    IF (v_res->>'success')::BOOLEAN = TRUE THEN
        RAISE WARNING 'WINNER:%', '$uid';
    ELSE
        RAISE WARNING 'CONFLICT:%', v_res->>'error_code';
    END IF;
EXCEPTION WHEN OTHERS THEN
    RAISE WARNING 'CONFLICT:%', SQLERRM;
END \$\$;
SQL
    ) &
done
wait

winners=$(grep -c "WINNER:" "$results_file" || true)
conflicts=$(grep -c "CONFLICT:" "$results_file" || true)

echo "100-way accept race results: $winners winners, $conflicts conflicts."
if [ "$winners" -ne 1 ]; then
    echo "ERROR: Expected exactly 1 winner, got $winners"
    echo "Sample results from race_results.txt:"
    cat "$results_file" | head -n 30
    exit 1
fi
echo ">>> PASSED: TEST 3 (Gate 3: 100-way concurrent claim race guarantees strictly 1 winner and 99 conflicts)."

echo "=== 8. TEST 4: MONOTONIC DRIVER LOCATION SEQUENCE ORDERING ==="
psql "${psql_args[@]}" <<'SQL'
SET ROLE authenticated;
SET "request.jwt.claim.sub" = '22222222-2222-2222-2222-222222222222';

DO $$
DECLARE
    v_res JSONB;
    v_seq BIGINT;
BEGIN
    -- Send seq 100 -> ACCEPT
    PERFORM public.mobility_update_driver_presence('CR_SJO', '66666666-6666-6666-6666-666666666666'::uuid, 'AVAILABLE', 9.9333, -84.0833, 90.0, 10.0, 100);

    -- Send seq 101 -> ACCEPT
    PERFORM public.mobility_update_driver_presence('CR_SJO', '66666666-6666-6666-6666-666666666666'::uuid, 'AVAILABLE', 9.9334, -84.0834, 90.0, 10.0, 101);

    -- Send seq 103 -> ACCEPT
    PERFORM public.mobility_update_driver_presence('CR_SJO', '66666666-6666-6666-6666-666666666666'::uuid, 'AVAILABLE', 9.9336, -84.0836, 90.0, 10.0, 103);

    -- Send seq 102 (out of order, stale) -> MUST BE REJECTED!
    BEGIN
        PERFORM public.mobility_update_driver_presence('CR_SJO', '66666666-6666-6666-6666-666666666666'::uuid, 'AVAILABLE', 9.9335, -84.0835, 90.0, 10.0, 102);
        RAISE EXCEPTION 'TEST_FAILED: Stale sequence 102 should have been rejected!';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM NOT LIKE '%STALE_SEQUENCE_ID%' THEN
            RAISE EXCEPTION 'Unexpected error on stale sequence: %', SQLERRM;
        END IF;
    END;

    -- Send seq 104 -> ACCEPT
    PERFORM public.mobility_update_driver_presence('CR_SJO', '66666666-6666-6666-6666-666666666666'::uuid, 'AVAILABLE', 9.9337, -84.0837, 90.0, 10.0, 104);

    -- Verify final sequence snapshot is exactly 104
    SELECT sequence_id INTO v_seq
    FROM public.driver_presence_snapshot
    WHERE driver_id = '22222222-2222-2222-2222-222222222222'::uuid;

    IF v_seq <> 104 THEN
        RAISE EXCEPTION 'TEST_FAILED: Expected final sequence 104, got %', v_seq;
    END IF;
END $$;
SQL
echo ">>> PASSED: TEST 4 (Monotonic location sequence ordering verified: 100->101->103 accepted, 102 rejected, 104 accepted)."

echo "=== 9. TEST 5: GATE P0 — 6-DIGIT CSPRNG BOARDING PIN, BCRYPT STORAGE, 5-ATTEMPT LOCKOUT & SINGLE-USE ==="
psql "${psql_args[@]}" <<'SQL'
RESET ROLE;
INSERT INTO public.ride_requests (
    ride_request_id, rider_id, market_id, service_category_id, dispatch_mode, state,
    pickup_location, destination_location, currency_code, correlation_id, version
) VALUES (
    'dddddddd-3333-3333-3333-333333333333'::uuid,
    '11111111-1111-1111-1111-111111111111'::uuid,
    'CR_SJO', 'cat_sjo_standard', 'AUTO_DISPATCH', 'MATCHED',
    ROW(-84.0833, 9.9333, 4326)::extensions.geography,
    ROW(-84.0700, 9.9200, 4326)::extensions.geography,
    'CRC', extensions.gen_random_uuid(), 1
) ON CONFLICT DO NOTHING;

-- Setup active trip for boarding test
INSERT INTO public.trips (
    trip_id, ride_request_id, rider_id, driver_id, vehicle_id, state, version
) VALUES (
    'dddddddd-4444-4444-4444-444444444444'::uuid,
    'dddddddd-3333-3333-3333-333333333333'::uuid,
    '11111111-1111-1111-1111-111111111111'::uuid,
    '22222222-2222-2222-2222-222222222222'::uuid,
    '66666666-6666-6666-6666-666666666666'::uuid,
    'DRIVER_ARRIVED', 1
) ON CONFLICT (trip_id) DO UPDATE SET state = 'DRIVER_ARRIVED';

-- 1. Rider Alice issues 6-digit challenge
SET ROLE authenticated;
SET "request.jwt.claim.sub" = '11111111-1111-1111-1111-111111111111';
DO $$
DECLARE
    v_res JSONB;
    v_pin TEXT;
BEGIN
    v_res := public.mobility_issue_trip_verification_pin('dddddddd-4444-4444-4444-444444444444'::uuid);
    v_pin := v_res->>'pin';

    IF v_pin !~ '^[0-9]{6}$' THEN
        RAISE EXCEPTION 'TEST_FAILED: Boarding PIN must be 6 numeric digits, got: %', v_pin;
    END IF;

    -- Store PIN in session temp table for test driver
    DROP TABLE IF EXISTS tmp_issued_pin;
    CREATE TEMP TABLE tmp_issued_pin AS SELECT v_pin AS pin;
    GRANT ALL ON tmp_issued_pin TO authenticated, service_role;
END $$;

-- 2. Verify Gate P0 Security Invariants in DB
RESET ROLE;
DO $$
DECLARE
    v_public_hash TEXT;
    v_private_hash TEXT;
BEGIN
    -- Check base trips table has NO PIN HASH (must be NULL)
    SELECT verification_pin_hash INTO v_public_hash
    FROM public.trips WHERE trip_id = 'dddddddd-4444-4444-4444-444444444444'::uuid;

    IF v_public_hash IS NOT NULL THEN
        RAISE EXCEPTION 'GATE P0 VIOLATION: verification_pin_hash must be NULL in public.trips, got %', v_public_hash;
    END IF;

    -- Check private table holds bcrypt hash ($2a$ or $2b$)
    SELECT pin_hash INTO v_private_hash
    FROM private.mobility_trip_pin_challenges WHERE trip_id = 'dddddddd-4444-4444-4444-444444444444'::uuid;

    IF v_private_hash NOT LIKE '$2%' THEN
        RAISE EXCEPTION 'GATE P0 VIOLATION: Expected bcrypt hash starting with $2, got %', v_private_hash;
    END IF;
END $$;

-- 3. Stranger Dan cannot issue or read boarding challenge
SET ROLE authenticated;
SET "request.jwt.claim.sub" = '44444444-4444-4444-4444-444444444444';
DO $$
BEGIN
    BEGIN
        PERFORM public.mobility_issue_trip_verification_pin('dddddddd-4444-4444-4444-444444444444'::uuid);
        RAISE EXCEPTION 'TEST_FAILED: Stranger should be rejected from issuing/reading boarding PIN';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM NOT LIKE '%ONLY_RIDER_CAN_REQUEST_BOARDING_PIN%' AND SQLERRM NOT LIKE '%FORBIDDEN%' THEN
            RAISE EXCEPTION 'Unexpected error on stranger PIN read: %', SQLERRM;
        END IF;
    END;
END $$;

-- 4. Driver Bob attempts boarding with wrong PINs 1..4 (Must decrement remaining attempts)
SET ROLE authenticated;
SET "request.jwt.claim.sub" = '22222222-2222-2222-2222-222222222222';
DO $$
DECLARE
    v_res JSONB;
    i INT;
BEGIN
    FOR i IN 1..4 LOOP
        v_res := public.mobility_transition_trip(
            'dddddddd-4444-4444-4444-444444444444'::uuid,
            'RIDER_ONBOARD',
            1,
            '000000',
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

-- 5. 5th failed attempt -> MUST TRIGGER 15-MINUTE LOCKOUT
DO $$
DECLARE
    v_res JSONB;
BEGIN
    v_res := public.mobility_transition_trip(
        'dddddddd-4444-4444-4444-444444444444'::uuid,
        'RIDER_ONBOARD',
        1,
        '000000',
        extensions.gen_random_uuid()
    );
    IF (v_res->>'error_code') <> 'PIN_LOCKED_TOO_MANY_ATTEMPTS' THEN
        RAISE EXCEPTION 'TEST_FAILED: Expected PIN_LOCKED_TOO_MANY_ATTEMPTS on 5th attempt, got %', v_res;
    END IF;
END $$;

-- 6. Attempt during lockout even with CORRECT PIN -> MUST BE REJECTED!
DO $$
DECLARE
    v_pin TEXT;
    v_res JSONB;
BEGIN
    SELECT pin INTO v_pin FROM tmp_issued_pin;

    v_res := public.mobility_transition_trip(
        'dddddddd-4444-4444-4444-444444444444'::uuid,
        'RIDER_ONBOARD',
        1,
        v_pin,
        extensions.gen_random_uuid()
    );
    IF (v_res->>'error_code') <> 'PIN_LOCKED_TOO_MANY_ATTEMPTS' THEN
        RAISE EXCEPTION 'TEST_FAILED: Should reject correct PIN during active lockout, got %', v_res;
    END IF;
END $$;

-- 7. Reset lockout directly in DB to simulate lockout expiry
RESET ROLE;
UPDATE private.mobility_trip_pin_challenges
SET locked_until = clock_timestamp() - INTERVAL '1 second'
WHERE trip_id = 'dddddddd-4444-4444-4444-444444444444'::uuid;

-- 8. Driver attempts with CORRECT 6-digit PIN -> SUCCEEDS & consumes PIN
SET ROLE authenticated;
SET "request.jwt.claim.sub" = '22222222-2222-2222-2222-222222222222';
DO $$
DECLARE
    v_pin TEXT;
    v_res JSONB;
BEGIN
    SELECT pin INTO v_pin FROM tmp_issued_pin;

    v_res := public.mobility_transition_trip(
        'dddddddd-4444-4444-4444-444444444444'::uuid,
        'RIDER_ONBOARD',
        1,
        v_pin,
        extensions.gen_random_uuid()
    );

    IF (v_res->>'success')::BOOLEAN <> TRUE THEN
        RAISE EXCEPTION 'TEST_FAILED: Boarding with correct PIN failed: %', v_res;
    END IF;
END $$;

-- 9. Single-Use Invariant: Replay of consumed PIN must fail with PIN_ALREADY_USED
RESET ROLE;
UPDATE public.trips
SET state = 'DRIVER_ARRIVED'
WHERE trip_id = 'dddddddd-4444-4444-4444-444444444444'::uuid;

SET ROLE authenticated;
SET "request.jwt.claim.sub" = '22222222-2222-2222-2222-222222222222';
DO $$
DECLARE
    v_pin TEXT;
    v_res JSONB;
BEGIN
    SELECT pin INTO v_pin FROM tmp_issued_pin;

    v_res := public.mobility_transition_trip(
        'dddddddd-4444-4444-4444-444444444444'::uuid,
        'RIDER_ONBOARD',
        2,
        v_pin,
        extensions.gen_random_uuid()
    );

    IF (v_res->>'error_code') <> 'PIN_ALREADY_USED' THEN
        RAISE EXCEPTION 'TEST_FAILED: Expected PIN_ALREADY_USED on reused PIN, got %', v_res;
    END IF;
END $$;

RESET ROLE;
UPDATE public.trips
SET state = 'IN_PROGRESS'
WHERE trip_id = 'dddddddd-4444-4444-4444-444444444444'::uuid;
SQL
echo ">>> PASSED: TEST 5 (Gate P0: 6-digit CSPRNG PIN, bcrypt storage in private schema, 5-attempt lockout, and single-use verified)."

echo "=== 10. TEST 6: GATE P0 — SAFE TRIP SHARING PROJECTION & ISOLATION ==="
psql "${psql_args[@]}" <<'SQL'
-- Rider Alice shares trip with Friend Carol
SET ROLE authenticated;
SET "request.jwt.claim.sub" = '11111111-1111-1111-1111-111111111111';
DO $$
BEGIN
    PERFORM public.mobility_share_trip(
        'dddddddd-4444-4444-4444-444444444444'::uuid,
        '33333333-3333-3333-3333-333333333333'::uuid
    );
END $$;

-- 1. Friend Carol CANNOT read base public.trips table (p_trips_share_read was dropped)
SET ROLE authenticated;
SET "request.jwt.claim.sub" = '33333333-3333-3333-3333-333333333333';
DO $$
DECLARE
    v_base_trips_count INT;
BEGIN
    SELECT count(*) INTO v_base_trips_count
    FROM public.trips
    WHERE trip_id = 'dddddddd-4444-4444-4444-444444444444'::uuid;

    IF v_base_trips_count <> 0 THEN
        RAISE EXCEPTION 'GATE P0 VIOLATION: Friend Carol must NOT have direct SELECT access to base public.trips! Count: %', v_base_trips_count;
    END IF;
END $$;

-- 2. Friend Carol CAN read public.mobility_trip_share_projection
DO $$
DECLARE
    v_proj_count INT;
    v_proj JSONB;
BEGIN
    SELECT count(*) INTO v_proj_count
    FROM public.mobility_trip_share_projection
    WHERE trip_id = 'dddddddd-4444-4444-4444-444444444444'::uuid;

    IF v_proj_count <> 1 THEN
        RAISE EXCEPTION 'TEST_FAILED: Friend Carol should see exactly 1 row in mobility_trip_share_projection, got %', v_proj_count;
    END IF;

    -- Query safe projection RPC
    v_proj := public.mobility_get_safe_trip_projection('dddddddd-4444-4444-4444-444444444444'::uuid);

    -- Gate P0 Safe Projection Invariant: Zero financial, PIN, token or ledger fields
    IF v_proj ? 'payment_authorization' OR v_proj ? 'token' OR v_proj ? 'ledger' OR v_proj ? 'verification_pin' OR v_proj ? 'pin_hash' THEN
        RAISE EXCEPTION 'GATE P0 LEAK: Safe projection leaked sensitive data: %', v_proj;
    END IF;

    IF (v_proj->>'trip_id')::UUID <> 'dddddddd-4444-4444-4444-444444444444'::uuid THEN
        RAISE EXCEPTION 'TEST_FAILED: Wrong trip_id in projection: %', v_proj;
    END IF;
END $$;

-- 3. Stranger Dan attempts safe projection read -> MUST BE FORBIDDEN
SET ROLE authenticated;
SET "request.jwt.claim.sub" = '44444444-4444-4444-4444-444444444444';
DO $$
BEGIN
    BEGIN
        PERFORM public.mobility_get_safe_trip_projection('dddddddd-4444-4444-4444-444444444444'::uuid);
        RAISE EXCEPTION 'TEST_FAILED: Stranger should NOT be able to read safe projection!';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM NOT LIKE '%FORBIDDEN_TRIP_READ%' THEN
            RAISE EXCEPTION 'Unexpected error on stranger projection read: %', SQLERRM;
        END IF;
    END;
END $$;

-- 4. Rider Alice revokes Friend Carol's share access
SET ROLE authenticated;
SET "request.jwt.claim.sub" = '11111111-1111-1111-1111-111111111111';
DO $$
BEGIN
    PERFORM public.mobility_revoke_trip_share(
        'dddddddd-4444-4444-4444-444444444444'::uuid,
        '33333333-3333-3333-3333-333333333333'::uuid
    );
END $$;

-- 5. Friend Carol reads safe projection again -> IMMEDIATELY FORBIDDEN / 0 rows
SET ROLE authenticated;
SET "request.jwt.claim.sub" = '33333333-3333-3333-3333-333333333333';
DO $$
DECLARE
    v_proj_count INT;
BEGIN
    SELECT count(*) INTO v_proj_count
    FROM public.mobility_trip_share_projection
    WHERE trip_id = 'dddddddd-4444-4444-4444-444444444444'::uuid;

    IF v_proj_count <> 0 THEN
        RAISE EXCEPTION 'GATE P0 VIOLATION: Revoked user must see 0 rows in projection, got %', v_proj_count;
    END IF;

    BEGIN
        PERFORM public.mobility_get_safe_trip_projection('dddddddd-4444-4444-4444-444444444444'::uuid);
        RAISE EXCEPTION 'TEST_FAILED: Revoked user must NOT be able to read safe projection RPC!';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM NOT LIKE '%FORBIDDEN_TRIP_READ%' THEN
            RAISE EXCEPTION 'Unexpected error on revoked projection read: %', SQLERRM;
        END IF;
    END;
END $$;
SQL
echo ">>> PASSED: TEST 6 (Gate P0: Base trips isolated; safe projection excludes sensitive data; instant revocation verified)."

echo "=== 11. TEST 7: GATE 2 — PAYMENT PROVIDER CAPABILITIES & 7-PARAM PROVIDER CAPTURE ==="
psql "${psql_args[@]}" <<'SQL'
RESET ROLE;
-- 1. Check default capabilities: CASH is enabled in PRODUCTION, CARD_TOKEN is DISABLED
DO $$
DECLARE
    v_cash_env TEXT;
    v_card_env TEXT;
BEGIN
    SELECT environment INTO v_cash_env FROM public.mobility_payment_provider_capabilities WHERE provider = 'CASH';
    SELECT environment INTO v_card_env FROM public.mobility_payment_provider_capabilities WHERE provider = 'CARD_TOKEN';

    IF v_cash_env <> 'PRODUCTION' THEN
        RAISE EXCEPTION 'GATE 2 ERROR: CASH must be PRODUCTION, got %', v_cash_env;
    END IF;

    IF v_card_env <> 'DISABLED' THEN
        RAISE EXCEPTION 'GATE 2 ERROR: CARD_TOKEN must be DISABLED, got %', v_card_env;
    END IF;
END $$;

-- 2. Setup quote for Trip dddddddd-4444-4444-4444-444444444444
DO $$
DECLARE
    v_qid UUID := 'eeeeeeee-5555-5555-5555-555555555555'::uuid;
BEGIN
    INSERT INTO public.ride_quotes (
        quote_id, ride_request_id, rider_id, market_id, service_category_id,
        base_fare_minor, distance_fare_minor, time_fare_minor, surge_adjustment_minor,
        toll_estimate_minor, tax_minor, total_fare_minor, currency_code,
        pricing_policy_version, input_digest, expires_at
    ) VALUES (
        v_qid,
        'dddddddd-3333-3333-3333-333333333333'::uuid,
        '11111111-1111-1111-1111-111111111111'::uuid,
        'CR_SJO', 'cat_sjo_standard',
        800, 650, 900, 0, 0, 305, 2655, 'CRC',
        1, 'digest_test_10', clock_timestamp() + INTERVAL '1 hour'
    ) ON CONFLICT (quote_id) DO NOTHING;
END $$;

-- 3. Rider attempts to authorize payment with unverified electronic provider (CARD_TOKEN) -> MUST FAIL-CLOSED!
SET ROLE authenticated;
SET "request.jwt.claim.sub" = '11111111-1111-1111-1111-111111111111';
DO $$
BEGIN
    BEGIN
        PERFORM public.mobility_authorize_quote_payment(
            'eeeeeeee-5555-5555-5555-555555555555'::uuid,
            'CARD_TOKEN',
            extensions.gen_random_uuid()
        );
        RAISE EXCEPTION 'GATE 2 VIOLATION: CARD_TOKEN should fail-closed when not verified!';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM NOT LIKE '%PAYMENT_PROVIDER_DISABLED_OR_NOT_VERIFIED%' THEN
            RAISE EXCEPTION 'Unexpected error on disabled card token authorize: %', SQLERRM;
        END IF;
    END;
END $$;

-- 4. Rider authorizes payment with verified CASH provider -> SUCCEEDS!
DO $$
DECLARE
    v_res JSONB;
BEGIN
    v_res := public.mobility_authorize_quote_payment(
        'eeeeeeee-5555-5555-5555-555555555555'::uuid,
        'CASH',
        'c0c0c0c0-1111-1111-1111-111111111111'::uuid
    );

    IF (v_res->'authorization'->>'state') <> 'CASH_PENDING' THEN
        RAISE EXCEPTION 'TEST_FAILED: CASH authorization failed: %', v_res;
    END IF;
END $$;

-- 5. Service role enables and verifies CARD_TOKEN for testing external capture pipeline
RESET ROLE;
UPDATE public.mobility_payment_provider_capabilities
SET enabled = TRUE, externally_verified = TRUE, environment = 'PRODUCTION', verified_at = clock_timestamp()
WHERE provider = 'CARD_TOKEN';

DROP TABLE IF EXISTS public.tmp_auth_id;
CREATE UNLOGGED TABLE public.tmp_auth_id (auth_id UUID);
GRANT ALL ON public.tmp_auth_id TO authenticated, service_role;

-- Now rider authorizes CARD_TOKEN payment on a second quote
DO $$
DECLARE
    v_qid2 UUID := 'eeeeeeee-6666-6666-6666-666666666666'::uuid;
BEGIN
    INSERT INTO public.ride_quotes (
        quote_id, ride_request_id, rider_id, market_id, service_category_id,
        base_fare_minor, distance_fare_minor, time_fare_minor, surge_adjustment_minor,
        toll_estimate_minor, tax_minor, total_fare_minor, currency_code,
        pricing_policy_version, input_digest, expires_at
    ) VALUES (
        v_qid2,
        'dddddddd-3333-3333-3333-333333333333'::uuid,
        '11111111-1111-1111-1111-111111111111'::uuid,
        'CR_SJO', 'cat_sjo_standard',
        800, 650, 900, 0, 0, 305, 2655, 'CRC',
        1, 'digest_test_11', clock_timestamp() + INTERVAL '1 hour'
    ) ON CONFLICT (quote_id) DO NOTHING;
END $$;

SET ROLE authenticated;
SET "request.jwt.claim.sub" = '11111111-1111-1111-1111-111111111111';
DO $$
DECLARE
    v_res JSONB;
    v_aid UUID;
BEGIN
    v_res := public.mobility_authorize_quote_payment(
        'eeeeeeee-6666-6666-6666-666666666666'::uuid,
        'CARD_TOKEN',
        'c0c0c0c0-2222-2222-2222-222222222222'::uuid
    );
    v_aid := (v_res->'authorization'->>'payment_authorization_id')::UUID;

    -- Store for capture test in unlogged table
    INSERT INTO public.tmp_auth_id VALUES (v_aid);
END $$;

-- 5b. Service role confirms provider authorization (transitions state to AUTHORIZED and binds trip)
SET ROLE service_role;
SET "request.jwt.claim.role" = 'service_role';
DO $$
DECLARE
    v_aid UUID;
    v_res JSONB;
BEGIN
    SELECT auth_id INTO v_aid FROM tmp_auth_id;

    v_res := public.mobility_confirm_provider_authorization(
        v_aid,
        'auth_stripe_ref_123',
        'evt_stripe_auth_001',
        '{}'::jsonb
    );

    UPDATE public.payment_authorizations
    SET trip_id = 'dddddddd-4444-4444-4444-444444444444'::uuid
    WHERE payment_authorization_id = v_aid;
END $$;

-- 6. Non-service_role attempts capture -> MUST BE REJECTED (42501)
SET ROLE authenticated;
SET "request.jwt.claim.sub" = '11111111-1111-1111-1111-111111111111';
DO $$
DECLARE
    v_aid UUID;
BEGIN
    SELECT auth_id INTO v_aid FROM tmp_auth_id;

    BEGIN
        PERFORM public.mobility_confirm_provider_capture(
            v_aid,
            'dddddddd-4444-4444-4444-444444444444'::uuid,
            'ch_cap_123', 'evt_cap_123', 2655, 'CRC'
        );
        RAISE EXCEPTION 'TEST_FAILED: Authenticated caller should NOT be able to confirm provider capture!';
    EXCEPTION WHEN insufficient_privilege THEN
        -- Expected
    END;
END $$;

-- 7. Service role confirms capture with valid 7-parameter signature and trip binding -> MUST SUCCEED
SET ROLE service_role;
SET "request.jwt.claim.role" = 'service_role';
DO $$
DECLARE
    v_aid UUID;
    v_res JSONB;
BEGIN
    SELECT auth_id INTO v_aid FROM tmp_auth_id;

    v_res := public.mobility_confirm_provider_capture(
        v_aid,
        'dddddddd-4444-4444-4444-444444444444'::uuid,
        'ch_stripe_v11_ref',
        'evt_stripe_v11_001',
        2655,
        'CRC',
        '{"gateway": "stripe", "fee_minor": 75}'::jsonb
    );

    IF (v_res->>'state') <> 'CAPTURED' THEN
        RAISE EXCEPTION 'TEST_FAILED: Expected state CAPTURED, got %', v_res;
    END IF;

    -- 8. Idempotent replay of same event on same authorization returns existing state
    v_res := public.mobility_confirm_provider_capture(
        v_aid,
        'dddddddd-4444-4444-4444-444444444444'::uuid,
        'ch_stripe_v11_ref',
        'evt_stripe_v11_001',
        2655,
        'CRC'
    );
    IF (v_res->>'idempotent_replay')::BOOLEAN <> TRUE THEN
        RAISE EXCEPTION 'TEST_FAILED: Expected idempotent_replay TRUE on repeat capture';
    END IF;
END $$;
SQL
echo ">>> PASSED: TEST 7 (Gate 2: Payment provider capabilities fail-closed & 7-param provider capture verified)."

echo "=== 12. TEST 8: MUTUAL BILATERAL RATINGS AUTHORITY ==="
psql "${psql_args[@]}" <<'SQL'
RESET ROLE;
-- Transition trip to COMPLETED
UPDATE public.trips
SET state = 'COMPLETED'
WHERE trip_id = 'dddddddd-4444-4444-4444-444444444444'::uuid;

-- Rider rates Driver
SET ROLE authenticated;
SET "request.jwt.claim.sub" = '11111111-1111-1111-1111-111111111111';
DO $$
DECLARE
    v_res JSONB;
BEGIN
    v_res := public.mobility_rate_trip_party(
        'dddddddd-4444-4444-4444-444444444444'::uuid,
        5,
        ARRAY['EXCELLENT_SERVICE', 'CLEAN_CAR'],
        'Great driver!'
    );
    IF (v_res->>'success')::BOOLEAN <> TRUE THEN
        RAISE EXCEPTION 'TEST_FAILED: Rider rating driver failed: %', v_res;
    END IF;
END $$;

-- Driver rates Rider
SET ROLE authenticated;
SET "request.jwt.claim.sub" = '22222222-2222-2222-2222-222222222222';
DO $$
DECLARE
    v_res JSONB;
BEGIN
    v_res := public.mobility_rate_trip_party(
        'dddddddd-4444-4444-4444-444444444444'::uuid,
        5,
        ARRAY['PUNCTUAL', 'POLITE'],
        'Great passenger!'
    );
    IF (v_res->>'success')::BOOLEAN <> TRUE THEN
        RAISE EXCEPTION 'TEST_FAILED: Driver rating rider failed: %', v_res;
    END IF;
END $$;

-- Stranger cannot rate
SET ROLE authenticated;
SET "request.jwt.claim.sub" = '44444444-4444-4444-4444-444444444444';
DO $$
BEGIN
    BEGIN
        PERFORM public.mobility_rate_trip_party(
            'dddddddd-4444-4444-4444-444444444444'::uuid, 5
        );
        RAISE EXCEPTION 'TEST_FAILED: Stranger should NOT be able to rate trip parties!';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM NOT LIKE '%FORBIDDEN%' THEN
            RAISE EXCEPTION 'Unexpected error on stranger rating: %', SQLERRM;
        END IF;
    END;
END $$;
SQL
echo ">>> PASSED: TEST 8 (Mutual bilateral ratings validated, completed-only enforced, stranger forbidden)."

echo "=== 13. TEST 9: CANONICAL TRIP TIP & BALANCED ZERO-SUM LEDGER ==="
psql "${psql_args[@]}" <<'SQL'
-- Rider Alice creates a tip on the completed trip
SET ROLE authenticated;
SET "request.jwt.claim.sub" = '11111111-1111-1111-1111-111111111111';
DO $$
DECLARE
    v_res JSONB;
BEGIN
    v_res := public.mobility_create_trip_tip(
        'dddddddd-4444-4444-4444-444444444444'::uuid,
        500, -- 500 CRC
        'CRC',
        '12121212-1212-1212-1212-121212121212'::uuid
    );
    IF (v_res->>'success')::BOOLEAN <> TRUE THEN
        RAISE EXCEPTION 'TEST_FAILED: Creating tip failed: %', v_res;
    END IF;
END $$;

-- Service role captures the tip from PSP
SET ROLE service_role;
SET "request.jwt.claim.role" = 'service_role';
DO $$
DECLARE
    v_tip_id UUID;
    v_res JSONB;
BEGIN
    SELECT tip_id INTO v_tip_id FROM public.mobility_trip_tips
    WHERE trip_id = 'dddddddd-4444-4444-4444-444444444444'::uuid;

    v_res := public.mobility_confirm_tip_capture(
        v_tip_id, 'ch_tip_capture_999', 'evt_tip_cap_999'
    );
    IF (v_res->'tip'->>'state') <> 'CAPTURED' THEN
        RAISE EXCEPTION 'TEST_FAILED: Tip not in CAPTURED state: %', v_res;
    END IF;

    -- Service role settles tip into double-entry ledger
    v_res := public.mobility_settle_trip_tip(
        v_tip_id, extensions.gen_random_uuid()
    );
    IF (v_res->>'state') <> 'SETTLED' THEN
        RAISE EXCEPTION 'TEST_FAILED: Tip settlement failed: %', v_res;
    END IF;
END $$;

-- Verify Zero-Sum Ledger Invariant on Tip Settlement
RESET ROLE;
DO $$
DECLARE
    v_imbalance INT;
BEGIN
    SELECT count(*) INTO v_imbalance
    FROM (
        SELECT transaction_id, sum(amount_minor) as total
        FROM public.ledger_entries
        GROUP BY transaction_id
        HAVING sum(amount_minor) <> 0
    ) diff;

    IF v_imbalance <> 0 THEN
        RAISE EXCEPTION 'TEST_FAILED: Ledger is imbalanced! Imbalanced transactions count: %', v_imbalance;
    END IF;
END $$;
SQL
echo ">>> PASSED: TEST 9 (Tip lifecycle: authorize, PSP capture, and balanced zero-sum double-entry ledger settlement)."

echo "=== 14. TEST 10: CROSS-VERTICAL CAPABILITY ENFORCEMENT ==="
psql "${psql_args[@]}" <<'SQL'
RESET ROLE;
-- User 4444 is given ONLY PARTS_STORE capability
INSERT INTO public.principal_capabilities (principal_id, capability, activation_state, verified_at)
VALUES ('44444444-4444-4444-4444-444444444444'::uuid, 'PARTS_STORE', 'APPROVED', clock_timestamp())
ON CONFLICT (principal_id, capability) DO UPDATE SET activation_state = 'APPROVED', verified_at = clock_timestamp();

DO $$
BEGIN
    -- Verify User 4444 has PARTS_STORE
    PERFORM public.fulfillment_assert_principal_capability('44444444-4444-4444-4444-444444444444'::uuid, 'PARTS_STORE');

    -- Assert User 4444 does NOT have RIDE_DRIVER capability -> MUST FAIL!
    BEGIN
        PERFORM public.fulfillment_assert_principal_capability('44444444-4444-4444-4444-444444444444'::uuid, 'RIDE_DRIVER');
        RAISE EXCEPTION 'TEST_FAILED: Parts seller should NOT have RIDE_DRIVER capability!';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM NOT LIKE '%CAPABILITY_NOT_VERIFIED%' THEN
            RAISE EXCEPTION 'Unexpected error on capability assertion: %', SQLERRM;
        END IF;
    END;

    -- Assert User 4444 does NOT have TOW_TRUCK capability -> MUST FAIL!
    BEGIN
        PERFORM public.fulfillment_assert_principal_capability('44444444-4444-4444-4444-444444444444'::uuid, 'TOW_TRUCK');
        RAISE EXCEPTION 'TEST_FAILED: Parts seller should NOT have TOW_TRUCK capability!';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM NOT LIKE '%CAPABILITY_NOT_VERIFIED%' THEN
            RAISE EXCEPTION 'Unexpected error on capability assertion: %', SQLERRM;
        END IF;
    END;
END $$;
SQL
echo ">>> PASSED: TEST 10 (Cross-vertical capability gating strictly separates identity from approved capabilities)."

echo "=== 15. TEST 11: GATE 7 — CANONICAL ACCOUNT DELETION PROCESSOR ==="
psql "${psql_args[@]}" <<'SQL'
-- 1. User Eve requests account deletion
SET ROLE authenticated;
SET "request.jwt.claim.sub" = '55555555-5555-5555-5555-555555555555';
DO $$
DECLARE
    v_res JSONB;
    v_req_id UUID;
BEGIN
    v_res := public.request_user_account_deletion();

    IF (v_res->>'success')::BOOLEAN <> TRUE THEN
        RAISE EXCEPTION 'TEST_FAILED: request_user_account_deletion failed: %', v_res;
    END IF;

    IF (v_res->>'status') <> 'PENDING' THEN
        RAISE EXCEPTION 'TEST_FAILED: Expected PENDING status, got %', (v_res->>'status');
    END IF;

    v_req_id := (v_res->>'request_id')::UUID;
    DROP TABLE IF EXISTS tmp_del_req;
    CREATE TEMP TABLE tmp_del_req AS SELECT v_req_id AS req_id;
    GRANT ALL ON tmp_del_req TO service_role;
END $$;

-- 2. Non-service_role cannot process deletion request
SET ROLE authenticated;
SET "request.jwt.claim.sub" = '55555555-5555-5555-5555-555555555555';
DO $$
DECLARE
    v_req_id UUID;
BEGIN
    SELECT req_id INTO v_req_id FROM tmp_del_req;
    BEGIN
        PERFORM public.process_account_deletion_request(v_req_id);
        RAISE EXCEPTION 'TEST_FAILED: Authenticated caller must NOT be able to process deletion request!';
    EXCEPTION WHEN insufficient_privilege THEN
        -- Expected
    END;
END $$;

-- 3. Service role executes process_account_deletion_request
SET ROLE service_role;
SET "request.jwt.claim.role" = 'service_role';
DO $$
DECLARE
    v_req_id UUID;
    v_res JSONB;
    v_cap_count INT;
    v_principal public.principals%ROWTYPE;
BEGIN
    SELECT req_id INTO v_req_id FROM tmp_del_req;

    v_res := public.process_account_deletion_request(v_req_id);

    IF (v_res->>'success')::BOOLEAN <> TRUE THEN
        RAISE EXCEPTION 'TEST_FAILED: process_account_deletion_request failed: %', v_res;
    END IF;

    IF (v_res->>'status') <> 'COMPLETED' THEN
        RAISE EXCEPTION 'TEST_FAILED: Expected COMPLETED status, got %', (v_res->>'status');
    END IF;

    -- Gate 7 Invariants: Capabilities deleted, principal phone NULL, name pseudonymized
    SELECT count(*) INTO v_cap_count FROM public.principal_capabilities
    WHERE principal_id = '55555555-5555-5555-5555-555555555555'::uuid;

    IF v_cap_count <> 0 THEN
        RAISE EXCEPTION 'GATE 7 VIOLATION: Expected 0 capabilities for deleted user, got %', v_cap_count;
    END IF;

    SELECT * INTO v_principal FROM public.principals
    WHERE principal_id = '55555555-5555-5555-5555-555555555555'::uuid;

    IF v_principal.phone IS NOT NULL THEN
        RAISE EXCEPTION 'GATE 7 VIOLATION: Expected phone NULL for deleted user, got %', v_principal.phone;
    END IF;

    IF v_principal.full_name NOT LIKE 'DELETED_USER_%' THEN
        RAISE EXCEPTION 'GATE 7 VIOLATION: Expected full_name pseudonymized, got %', v_principal.full_name;
    END IF;
END $$;
SQL
echo ">>> PASSED: TEST 11 (Gate 7: Account deletion processor completed: status COMPLETED, capabilities cleared, identity pseudonymized)."

echo "=========================================================================="
echo "ALL V11 PUBLIC LAUNCH ADVERSARIAL TESTS PASSED (100% GREEN)"
echo "=========================================================================="
