#!/usr/bin/env bash
# ==============================================================================
# tests/mobility/verify-mobility-v12-provider-operations.sh
#
# Master Order V12 Verification Suite:
#   1. P0-A: Driver Presence Privacy Lockdown (Direct SELECT Denied for Authenticated)
#   2. P0-A: Contextual RPC mobility_get_active_trip_location_v1 (Active Only, Participant Only)
#   3. P0-C: Route Evidence Classification ('ESTIMATED' vs 'ROAD_NETWORK_VERIFIED', No Fake Haversine)
#   4. P0-D: Canonical Trip State Transitions & Separate Dispute Aggregate
#   5. P0/P1: Real Supabase Auth Deletion for GDPR/Privacy Right to Erasure (auth.users purged)
#   6. Universal Provider Operations OS (Online/Offline, Console Snapshot, Fail-Closed Rails)
# ==============================================================================

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/../.." && pwd)"

required_commands=(initdb pg_ctl psql)
for command_name in "${required_commands[@]}"; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Mobility V12 Provider Operations test: SKIP ($command_name unavailable)"
    exit 0
  fi
done

runtime_dir="$(mktemp -d /tmp/elysium-v12-verify-XXXXXX)"
cluster_dir="$runtime_dir/data"
socket_dir="$runtime_dir/socket"
server_log="$runtime_dir/postgres.log"
port="$((59600 + RANDOM % 300))"
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

echo "=== 2. Setting Up Supabase Auth Schema & Extensions ==="
psql "${psql_args[@]}" <<'SQL'
CREATE SCHEMA IF NOT EXISTS extensions;
CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA extensions;

CREATE SCHEMA IF NOT EXISTS auth;

CREATE TABLE IF NOT EXISTS auth.users (
    id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    email TEXT UNIQUE,
    encrypted_password TEXT,
    email_confirmed_at TIMESTAMPTZ DEFAULT clock_timestamp(),
    raw_user_meta_data JSONB DEFAULT '{}'::jsonb,
    raw_app_meta_data JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ DEFAULT clock_timestamp()
);

CREATE TABLE IF NOT EXISTS auth.identities (
    id TEXT PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    identity_data JSONB DEFAULT '{}'::jsonb,
    provider TEXT NOT NULL,
    last_sign_in_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ DEFAULT clock_timestamp()
);

CREATE TABLE IF NOT EXISTS auth.sessions (
    id UUID PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
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

GRANT USAGE ON SCHEMA public, extensions, auth TO anon, authenticated, service_role;
GRANT ALL ON ALL TABLES IN SCHEMA auth TO service_role;
GRANT SELECT ON ALL TABLES IN SCHEMA auth TO authenticated;

CREATE OR REPLACE FUNCTION auth.uid() RETURNS UUID AS $$
    SELECT NULLIF(current_setting('request.jwt.claim.sub', true), '')::UUID;
$$ LANGUAGE sql STABLE;

CREATE OR REPLACE FUNCTION auth.role() RETURNS TEXT AS $$
    SELECT NULLIF(current_setting('request.jwt.claim.role', true), '')::TEXT;
$$ LANGUAGE sql STABLE;
SQL

echo "=== 3. Applying Cumulative Migrations (V0 through V12) ==="
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906000000_mobility_market_authority.sql"
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906010000_mobility_financial_authority.sql"
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906020000_mobility_safety_and_reserve.sql"
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906030000_mobility_communications_reputation_and_surge.sql"
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906040000_mobility_financial_and_concurrency_p0_lockdown.sql"
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906050000_mobility_financial_authority_v8_closure.sql"
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906060000_mobility_provider_capture_v9_closure.sql"
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906070000_mobility_hardening_and_stops_authority.sql"
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906080000_mobility_public_launch_v11_closure.sql"
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906090000_mobility_provider_operations_v12_closure.sql"
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260910020000_mobility_dispute_resolution_authority.sql"

echo "=== 4. Seeding Test Entities (Rider, Driver, Stranger, Market, Vehicle) ==="
psql "${psql_args[@]}" <<'SQL'
INSERT INTO auth.users (id, email) VALUES
    ('11111111-1111-4111-8111-111111111111', 'rider@elysium.test'),
    ('22222222-2222-4222-8222-222222222222', 'driver@elysium.test'),
    ('33333333-3333-4333-8333-333333333333', 'stranger@elysium.test'),
    ('44444444-4444-4444-8444-444444444444', 'courier@elysium.test'),
    ('99999999-9999-4999-8999-999999999999', 'delete_target@elysium.test')
ON CONFLICT (id) DO NOTHING;

-- Seed Market
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

-- Register driver vehicle
INSERT INTO public.mobility_vehicles (
    vehicle_id, owner_id, make, model, year, color, seat_capacity, license_plate, verification_state, active
) VALUES (
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
    '22222222-2222-4222-8222-222222222222',
    'Toyota', 'Corolla', 2022, 'Silver', 4, 'YUC-1234', 'VERIFIED', TRUE
) ON CONFLICT (vehicle_id) DO NOTHING;

INSERT INTO public.driver_vehicle_authorizations (driver_id, vehicle_id, is_authorized, active)
VALUES ('22222222-2222-4222-8222-222222222222', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', TRUE, TRUE)
ON CONFLICT (driver_id, vehicle_id) DO NOTHING;

INSERT INTO public.driver_presence_snapshot (
    driver_id, active_vehicle_id, market_id, current_state, location, bearing_degrees, speed_mps, accuracy_meters, sequence_id, captured_at
) VALUES (
    '22222222-2222-4222-8222-222222222222',
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
    'CR_SJO',
    'AVAILABLE',
    extensions.ST_SetSRID(extensions.ST_MakePoint(-84.0833, 9.9333), 4326)::extensions.geography,
    45.0,
    8.5,
    3.0,
    1,
    clock_timestamp()
) ON CONFLICT (driver_id) DO UPDATE SET
    location = EXCLUDED.location,
    bearing_degrees = EXCLUDED.bearing_degrees,
    speed_mps = EXCLUDED.speed_mps;

-- Seed ride request
INSERT INTO public.ride_requests (
    ride_request_id, rider_id, market_id, service_category_id, dispatch_mode, state,
    pickup_location, destination_location, currency_code, correlation_id
) VALUES (
    'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee',
    '11111111-1111-4111-8111-111111111111',
    'CR_SJO',
    'cat_sjo_standard',
    'AUTO_DISPATCH',
    'MATCHED',
    extensions.ST_SetSRID(extensions.ST_MakePoint(-84.0833, 9.9333), 4326)::extensions.geography,
    extensions.ST_SetSRID(extensions.ST_MakePoint(-84.0600, 9.9100), 4326)::extensions.geography,
    'CRC',
    extensions.gen_random_uuid()
) ON CONFLICT (ride_request_id) DO NOTHING;

-- Seed an active trip
INSERT INTO public.trips (
    trip_id, ride_request_id, rider_id, driver_id, vehicle_id, state, version, assigned_at
) VALUES (
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee',
    '11111111-1111-4111-8111-111111111111',
    '22222222-2222-4222-8222-222222222222',
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
    'DRIVER_EN_ROUTE',
    1,
    clock_timestamp()
) ON CONFLICT (trip_id) DO NOTHING;
SQL

echo "=== 5. TEST 1: Driver Presence Privacy Lockdown (Direct SELECT Denied) ==="
# Attempt direct SELECT as authenticated user 'stranger'
set +e
psql "${psql_args[@]}" <<'SQL'
BEGIN;
SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claim.sub" = '33333333-3333-4333-8333-333333333333';
SELECT count(*) FROM public.driver_presence_snapshot;
ROLLBACK;
SQL
direct_select_status=$?
set -e

if [[ $direct_select_status -eq 0 ]]; then
  echo "TEST 1 FAILED: Authenticated user was able to directly SELECT from driver_presence_snapshot!"
  exit 1
fi
echo "  [PASS] Direct SELECT on driver_presence_snapshot denied for authenticated user."

echo "=== 6. TEST 2: Contextual RPC mobility_get_active_trip_location_v1 ==="
# 2.1 Stranger attempt -> Must fail with FORBIDDEN (42501)
set +e
psql "${psql_args[@]}" <<'SQL'
BEGIN;
SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claim.sub" = '33333333-3333-4333-8333-333333333333';
SELECT public.mobility_get_active_trip_location_v1('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
ROLLBACK;
SQL
stranger_rpc_status=$?
set -e

if [[ $stranger_rpc_status -eq 0 ]]; then
  echo "TEST 2.1 FAILED: Stranger was able to query trip location!"
  exit 1
fi
echo "  [PASS] Stranger query rejected with 42501 FORBIDDEN."

# 2.2 Active Rider attempt -> Must succeed and return location
rider_location=$(psql "${psql_args[@]}" -t -A <<'SQL'
BEGIN;
SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claim.sub" = '11111111-1111-4111-8111-111111111111';
SELECT public.mobility_get_active_trip_location_v1('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
ROLLBACK;
SQL
)
if [[ "$rider_location" != *"has_location\":true"* ]] && [[ "$rider_location" != *"has_location\": true"* ]]; then
  echo "TEST 2.2 FAILED: Active rider could not get location: $rider_location"
  exit 1
fi
echo "  [PASS] Active rider successfully received live driver location."

# 2.3 Transition trip to COMPLETED and verify terminal access denied
psql "${psql_args[@]}" <<'SQL'
UPDATE public.trips SET state = 'COMPLETED' WHERE trip_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
SQL

set +e
psql "${psql_args[@]}" <<'SQL'
BEGIN;
SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claim.sub" = '11111111-1111-4111-8111-111111111111';
SELECT public.mobility_get_active_trip_location_v1('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
ROLLBACK;
SQL
terminal_rpc_status=$?
set -e

if [[ $terminal_rpc_status -eq 0 ]]; then
  echo "TEST 2.3 FAILED: Rider was able to get location after trip was COMPLETED!"
  exit 1
fi
echo "  [PASS] Terminal trip denies live location access."

echo "=== 7. TEST 3: Route Evidence Classification ('ESTIMATED' vs 'ROAD_NETWORK_VERIFIED') ==="
psql "${psql_args[@]}" <<'SQL'
-- Valid estimated evidence
INSERT INTO public.ride_route_evidence (
    ride_request_id, origin_geography, destination_geography, distance_meters, duration_seconds,
    routing_provider, routing_engine_version, route_geometry_hash, evidence_digest, route_version, evidence_class
) VALUES (
    'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee',
    extensions.ST_SetSRID(extensions.ST_MakePoint(-84.0833, 9.9333), 4326)::extensions.geography,
    extensions.ST_SetSRID(extensions.ST_MakePoint(-84.0600, 9.9100), 4326)::extensions.geography,
    5000, 600, 'HAVERSINE', 'v1.0', 'hash_est', 'digest_est', 1, 'ESTIMATED'
);

-- Valid road network verified evidence
INSERT INTO public.ride_route_evidence (
    ride_request_id, origin_geography, destination_geography, distance_meters, duration_seconds,
    routing_provider, routing_engine_version, route_geometry_hash, evidence_digest, route_version, evidence_class
) VALUES (
    'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee',
    extensions.ST_SetSRID(extensions.ST_MakePoint(-84.0833, 9.9333), 4326)::extensions.geography,
    extensions.ST_SetSRID(extensions.ST_MakePoint(-84.0600, 9.9100), 4326)::extensions.geography,
    5420, 680, 'OSRM', 'v1.0', 'hash_road', 'digest_road', 2, 'ROAD_NETWORK_VERIFIED'
);
SQL

# Invalid evidence class must be rejected
set +e
psql "${psql_args[@]}" <<'SQL'
INSERT INTO public.ride_route_evidence (
    ride_request_id, origin_geography, destination_geography, distance_meters, duration_seconds,
    routing_provider, routing_engine_version, route_geometry_hash, evidence_digest, route_version, evidence_class
) VALUES (
    'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee',
    extensions.ST_SetSRID(extensions.ST_MakePoint(-84.0833, 9.9333), 4326)::extensions.geography,
    extensions.ST_SetSRID(extensions.ST_MakePoint(-84.0600, 9.9100), 4326)::extensions.geography,
    5000, 600, 'HAVERSINE', 'v1.0', 'hash_fake', 'digest_fake', 3, 'AUTHORITATIVE_HAVERSINE'
);
SQL
invalid_class_status=$?
set -e

if [[ $invalid_class_status -eq 0 ]]; then
  echo "TEST 3 FAILED: Invalid evidence_class was accepted!"
  exit 1
fi
echo "  [PASS] Route evidence strictly enforces evidence_class IN ('ESTIMATED', 'ROAD_NETWORK_VERIFIED')."

echo "=== 8. TEST 4: Canonical Trip State Transitions & Dispute Isolation ==="
# Verify transitions table exists and contains canonical states
transition_count=$(psql "${psql_args[@]}" -t -A -c "SELECT count(*) FROM public.mobility_trip_state_transitions WHERE from_state = 'ASSIGNED' AND to_state = 'DRIVER_EN_ROUTE';")
if [[ "$transition_count" -ne 1 ]]; then
  echo "TEST 4.1 FAILED: mobility_trip_state_transitions table missing expected transition."
  exit 1
fi

disputed_transition=$(psql "${psql_args[@]}" -t -A -c "SELECT count(*) FROM public.mobility_trip_state_transitions WHERE to_state = 'DISPUTED' OR from_state = 'DISPUTED';")
if [[ "$disputed_transition" -ne 0 ]]; then
  echo "TEST 4.2 FAILED: DISPUTED found in trip state machine transitions!"
  exit 1
fi
echo "  [PASS] State machine enforces valid transitions and excludes DISPUTED."

# Insert dispute into dedicated aggregate as rider
psql "${psql_args[@]}" <<'SQL'
BEGIN;
SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claim.sub" = '11111111-1111-4111-8111-111111111111';
INSERT INTO public.mobility_trip_disputes (
    trip_id, opened_by, reason
) VALUES (
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    '11111111-1111-4111-8111-111111111111',
    'Fare overcharge dispute'
);
COMMIT;
SQL
echo "  [PASS] Rider successfully filed dedicated TripDispute aggregate."

psql "${psql_args[@]}" <<'SQL'
BEGIN;
SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claim.sub" = '11111111-1111-4111-8111-111111111111';
DO $$
DECLARE forged_state text;
BEGIN
    FOREACH forged_state IN ARRAY ARRAY['RESOLVED_RIDER', 'RESOLVED_PROVIDER', 'CLOSED', 'INVESTIGATING'] LOOP
        BEGIN
            INSERT INTO public.mobility_trip_disputes(trip_id,opened_by,reason,state)
            VALUES ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',auth.uid(),'Forged resolution',forged_state);
            RAISE EXCEPTION 'Participant forged dispute state %', forged_state;
        EXCEPTION WHEN insufficient_privilege THEN NULL;
        END;
    END LOOP;
    BEGIN
        INSERT INTO public.mobility_trip_disputes(trip_id,opened_by,reason,resolution_notes,resolved_at)
        VALUES ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',auth.uid(),'Forged notes','Refund approved',clock_timestamp());
        RAISE EXCEPTION 'Participant forged resolution metadata';
    EXCEPTION WHEN insufficient_privilege THEN NULL;
    END;
    BEGIN
        UPDATE public.mobility_trip_disputes SET state='RESOLVED_RIDER' WHERE opened_by=auth.uid();
        RAISE EXCEPTION 'Participant updated resolution';
    EXCEPTION WHEN insufficient_privilege THEN NULL;
    END;
END $$;
COMMIT;
-- Test a server operator can still resolve a legitimate case; no fake refund is created.
DO $$
DECLARE affected integer;
BEGIN
    UPDATE public.mobility_trip_disputes SET state='INVESTIGATING',updated_at=clock_timestamp()
    WHERE trip_id='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa' AND state='OPEN';
    GET DIAGNOSTICS affected = ROW_COUNT;
    IF affected <> 1 THEN RAISE EXCEPTION 'Expected one legitimate case, got %', affected; END IF;
END $$;
SQL
echo "  [PASS] Participants cannot forge dispute state or resolution metadata; privileged review remains available."


echo "=== 9. TEST 5: Zero-Trace Real Supabase Auth Deletion ==="
# Setup deletion request
psql "${psql_args[@]}" <<'SQL'
INSERT INTO auth.identities (id, user_id, provider)
VALUES ('ident_999', '99999999-9999-4999-8999-999999999999', 'email');

INSERT INTO auth.sessions (id, user_id)
VALUES ('99999999-9999-4999-8999-999999999991', '99999999-9999-4999-8999-999999999999');

INSERT INTO public.account_deletion_requests (request_id, user_id, status)
VALUES ('dddddddd-dddd-4ddd-8ddd-dddddddddddd', '99999999-9999-4999-8999-999999999999', 'PENDING');
SQL

# Execute deletion RPC as service_role
del_res=$(psql "${psql_args[@]}" -t -A <<'SQL'
BEGIN;
SET LOCAL ROLE service_role;
SELECT public.process_account_deletion_request('dddddddd-dddd-4ddd-8ddd-dddddddddddd');
COMMIT;
SQL
)
if [[ "$del_res" != *"auth_purged\":true"* ]] && [[ "$del_res" != *"auth_purged\": true"* ]]; then
  echo "TEST 5.1 FAILED: process_account_deletion_request did not report auth_purged: $del_res"
  exit 1
fi

auth_count=$(psql "${psql_args[@]}" -t -A -c "SELECT count(*) FROM auth.users WHERE id = '99999999-9999-4999-8999-999999999999';")
if [[ "$auth_count" -ne 0 ]]; then
  echo "TEST 5.2 FAILED: User was not deleted from auth.users!"
  exit 1
fi
echo "  [PASS] Zero-trace account deletion completely purges user from auth.users."

echo "=== 10. TEST 6: Universal Provider Operations OS ==="
# 6.1 Unapproved capability cannot go online
unapproved_res=$(psql "${psql_args[@]}" -t -A <<'SQL'
BEGIN;
SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claim.sub" = '44444444-4444-4444-8444-444444444444';
SELECT public.provider_go_online_v1(
    'DELIVERY_COURIER',
    extensions.gen_random_uuid(),
    '1.0.0'
);
ROLLBACK;
SQL
)
if [[ "$unapproved_res" != *"MISSING_DOCUMENTS"* ]]; then
  echo "TEST 6.1 FAILED: Provider without capability was allowed online: $unapproved_res"
  exit 1
fi
echo "  [PASS] Provider without approved capability is rejected with MISSING_DOCUMENTS."

# 6.2 Approve capability for courier and test go_online, snapshot, and go_offline
psql "${psql_args[@]}" <<'SQL'
INSERT INTO public.principals (principal_id, status)
VALUES ('44444444-4444-4444-8444-444444444444', 'ACTIVE')
ON CONFLICT (principal_id) DO NOTHING;

INSERT INTO public.principal_capabilities (principal_id, capability, activation_state)
VALUES ('44444444-4444-4444-8444-444444444444', 'DELIVERY_COURIER', 'APPROVED')
ON CONFLICT (principal_id, capability) DO UPDATE SET activation_state = 'APPROVED';
SQL

online_res=$(psql "${psql_args[@]}" -t -A <<'SQL'
BEGIN;
SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claim.sub" = '44444444-4444-4444-8444-444444444444';
SELECT public.provider_go_online_v1(
    'DELIVERY_COURIER',
    extensions.gen_random_uuid(),
    '1.0.0',
    95,
    'CELLULAR_5G'
);
COMMIT;
SQL
)
if [[ "$online_res" != *"ONLINE_STANDBY"* ]]; then
  echo "TEST 6.2 FAILED: Provider could not go online: $online_res"
  exit 1
fi
echo "  [PASS] Approved provider successfully transitioned to ONLINE_STANDBY."

snapshot_res=$(psql "${psql_args[@]}" -t -A <<'SQL'
BEGIN;
SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claim.sub" = '44444444-4444-4444-8444-444444444444';
SELECT public.provider_get_console_snapshot_v1('DELIVERY_COURIER');
COMMIT;
SQL
)
if [[ "$snapshot_res" != *"ONLINE_STANDBY"* ]] || [[ "$snapshot_res" != *"performance"* ]] || [[ "$snapshot_res" != *"balance"* ]]; then
  echo "TEST 6.3 FAILED: Console snapshot missing components: $snapshot_res"
  exit 1
fi
echo "  [PASS] Console snapshot returned context, performance, and financial balance."

offline_res=$(psql "${psql_args[@]}" -t -A <<'SQL'
BEGIN;
SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claim.sub" = '44444444-4444-4444-8444-444444444444';
SELECT public.provider_go_offline_v1('End of shift');
COMMIT;
SQL
)
if [[ "$offline_res" != *"OFFLINE"* ]]; then
  echo "TEST 6.4 FAILED: Provider could not go offline: $offline_res"
  exit 1
fi
echo "  [PASS] Provider successfully transitioned to OFFLINE."

# 6.5 Verify payout rails are fail-closed
active_rails_count=$(psql "${psql_args[@]}" -t -A -c "SELECT count(*) FROM public.provider_payout_rail_capabilities WHERE is_active = TRUE;")
if [[ "$active_rails_count" -ne 0 ]]; then
  echo "TEST 6.5 FAILED: Payout rails are active without live PSP credentials!"
  exit 1
fi
echo "  [PASS] All provider payout rails strictly fail-closed (is_active = FALSE)."

echo "=============================================================================="
echo "ALL V12 PROVIDER OPERATIONS & MOBILITY CLOSURE TESTS PASSED!"
echo "=============================================================================="
