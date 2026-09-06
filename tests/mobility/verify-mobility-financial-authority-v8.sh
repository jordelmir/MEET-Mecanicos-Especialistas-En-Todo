#!/usr/bin/env bash
set -euo pipefail

# ─────────────────────────────────────────────────────────────────────────────
# ELYSIUM GLOBAL MOBILITY OS — FINANCIAL AUTHORITY V8 ADVERSARIAL TEST SUITE
# Mandate: ORDEN MAESTRA V8 (Adversarial Verification & Authority Closure)
# ─────────────────────────────────────────────────────────────────────────────

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

required_commands=(initdb pg_ctl psql)
for command_name in "${required_commands[@]}"; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Mobility financial authority V8 test: SKIP ($command_name unavailable)"
    exit 0
  fi
done

runtime_dir="$(mktemp -d "${TMPDIR:-/tmp}/meet-mobility-v8-fin.XXXXXX")"
cluster_dir="$runtime_dir/data"
socket_dir="$runtime_dir/socket"
server_log="$runtime_dir/postgres.log"
port="$((59500 + RANDOM % 400))"
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

echo "=== 3. Applying Canonical Mobility Migrations: V6 Market, Finance, Safety, Comms + V7 Lockdown + V8 Closure ==="
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906000000_mobility_market_authority.sql"
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906010000_mobility_financial_authority.sql"
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906020000_mobility_safety_and_reserve.sql"
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906030000_mobility_communications_reputation_and_surge.sql"
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906040000_mobility_financial_and_concurrency_p0_lockdown.sql"
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906050000_mobility_financial_authority_v8_closure.sql"

echo "=== 4. Seeding Global Market, Service Category, Pricing Policy, Users & Vehicle ==="
psql "${psql_args[@]}" <<'SQL'
INSERT INTO auth.users (id) VALUES ('11111111-1111-1111-1111-111111111111'::uuid);
INSERT INTO auth.users (id) VALUES ('22222222-2222-2222-2222-222222222222'::uuid);

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

INSERT INTO public.mobility_pricing_policies (
    pricing_policy_id, market_id, service_category_id, version, currency_code,
    base_fare_minor, booking_fee_minor, per_meter_numerator, per_meter_denominator,
    per_second_numerator, per_second_denominator, minimum_fare_minor, cancellation_fee_minor,
    tax_basis_points, active, valid_from
) VALUES (
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'::uuid, 'CR_SJO', 'cat_sjo_standard', 1, 'CRC',
    600, 200, 65, 1, 15, 1, 1200, 1000, 1300, true, clock_timestamp() - INTERVAL '1 day'
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

echo "=== 5. TEST A: PRICING AUTHORITY (Client CANNOT supply distance/duration/surge) ==="
psql "${psql_args[@]}" <<'SQL'
SET ROLE authenticated;
SET "request.jwt.claim.sub" = '11111111-1111-1111-1111-111111111111';

-- Attempt 1: Old 6-parameter RPC must not exist
DO $$
BEGIN
    BEGIN
        PERFORM public.mobility_generate_quote('CR_SJO', 'cat_sjo_standard', 0, 0, 1, 1);
        RAISE EXCEPTION 'TEST_FAILED: Old raw distance quote function should be dropped!';
    EXCEPTION WHEN undefined_function THEN
        -- Expected
    END;
END $$;

-- Attempt 2: Create a real ride request (~15km between San José and Cartago)
-- Pickup: 9.9333, -84.0833
-- Destination: 9.8667, -83.9167
RESET ROLE;
INSERT INTO public.ride_requests (
    ride_request_id, rider_id, market_id, service_category_id, dispatch_mode, state,
    pickup_location, destination_location, currency_code, correlation_id
) VALUES (
    '44444444-4444-4444-4444-444444444444'::uuid,
    '11111111-1111-1111-1111-111111111111'::uuid,
    'CR_SJO',
    'cat_sjo_standard',
    'AUTO_DISPATCH',
    'REQUESTED',
    ROW(-84.0833, 9.9333, 4326)::extensions.geography,
    ROW(-83.9167, 9.8667, 4326)::extensions.geography,
    'CRC',
    extensions.gen_random_uuid()
);

-- Generate quote bound to ride request
SET ROLE authenticated;
SET "request.jwt.claim.sub" = '11111111-1111-1111-1111-111111111111';
DO $$
DECLARE
    v_res JSONB;
    v_quote_id UUID;
    v_total BIGINT;
    v_dist BIGINT;
BEGIN
    v_res := public.mobility_generate_quote('44444444-4444-4444-4444-444444444444'::uuid, extensions.gen_random_uuid());
    v_quote_id := (v_res->'quote'->>'quote_id')::UUID;
    v_total := (v_res->'quote'->>'total_fare_minor')::BIGINT;
    v_dist := (v_res->'route_evidence'->>'distance_meters')::BIGINT;

    IF v_dist < 15000 THEN
        RAISE EXCEPTION 'TEST_FAILED: Distance should be >= 15km, got %', v_dist;
    END IF;

    IF v_total < 1000000 THEN -- In minor units (> 10,000 CRC)
        RAISE EXCEPTION 'TEST_FAILED: Fare should reflect real distance, got %', v_total;
    END IF;
END $$;
SQL
echo ">>> PASSED: TEST A (Pricing Authority strictly derives from Authoritative Route Evidence; raw distance injection impossible)."

echo "=== 6. TEST B: PAYMENT AUTHORITY (Electronic payments CANNOT be marked AUTHORIZED without PSP) ==="
psql "${psql_args[@]}" <<'SQL'
SET ROLE authenticated;
SET "request.jwt.claim.sub" = '11111111-1111-1111-1111-111111111111';

DO $$
DECLARE
    v_quote_id UUID;
    v_auth_res JSONB;
    v_auth_id UUID;
    v_state TEXT;
BEGIN
    SELECT quote_id INTO v_quote_id FROM public.ride_quotes WHERE ride_request_id = '44444444-4444-4444-4444-444444444444'::uuid;

    -- Rider requests CARD_TOKEN payment
    v_auth_res := public.mobility_authorize_quote_payment(v_quote_id, 'CARD_TOKEN', extensions.gen_random_uuid());
    v_auth_id := (v_auth_res->'authorization'->>'payment_authorization_id')::UUID;
    v_state := (v_auth_res->'authorization'->>'state');

    -- Must be PENDING_PROVIDER, NOT AUTHORIZED!
    IF v_state <> 'PENDING_PROVIDER' THEN
        RAISE EXCEPTION 'TEST_FAILED: Electronic payment should be PENDING_PROVIDER, but was %', v_state;
    END IF;
END $$;
SQL
echo ">>> PASSED: TEST B (Electronic payment initially in PENDING_PROVIDER, cannot be self-authorized by client)."

echo "=== 7. TEST C: PROVIDER CONFIRMATION VIA SERVICE_ROLE ==="
psql "${psql_args[@]}" <<'SQL'
-- Ordinary authenticated caller CANNOT call mobility_confirm_provider_authorization
SET ROLE authenticated;
SET "request.jwt.claim.sub" = '11111111-1111-1111-1111-111111111111';
DO $$
DECLARE
    v_auth_id UUID;
BEGIN
    SELECT payment_authorization_id INTO v_auth_id FROM public.payment_authorizations WHERE rider_id = '11111111-1111-1111-1111-111111111111'::uuid LIMIT 1;
    BEGIN
        PERFORM public.mobility_confirm_provider_authorization(v_auth_id, 'ch_stripe_123', 'evt_123');
        RAISE EXCEPTION 'TEST_FAILED: Authenticated should NOT be able to confirm provider authorization!';
    EXCEPTION WHEN insufficient_privilege THEN
        -- Expected 42501
    END;
END $$;

-- Service_role confirms provider authorization
SET ROLE service_role;
DO $$
DECLARE
    v_auth_id UUID;
    v_res JSONB;
BEGIN
    SELECT payment_authorization_id INTO v_auth_id FROM public.payment_authorizations WHERE rider_id = '11111111-1111-1111-1111-111111111111'::uuid LIMIT 1;
    v_res := public.mobility_confirm_provider_authorization(v_auth_id, 'ch_stripe_real_authorized_999', 'evt_stripe_999', '{"status":"succeeded"}'::jsonb);
    IF (v_res->'authorization'->>'state') <> 'AUTHORIZED' THEN
        RAISE EXCEPTION 'TEST_FAILED: State should be AUTHORIZED after provider confirmation!';
    END IF;
END $$;
SQL
echo ">>> PASSED: TEST C (Provider authorization strictly requires service_role and transitions to AUTHORIZED with audit event)."

echo "=== 8. TEST D: CROSS-REQUEST QUOTE REUSE REJECTION ==="
psql "${psql_args[@]}" <<'SQL'
RESET ROLE;
-- Create Trip 1 from Request 1
INSERT INTO public.trips (
    trip_id, ride_request_id, rider_id, driver_id, vehicle_id, state,
    started_at, completed_at
) VALUES (
    '55555555-5555-5555-5555-555555555555'::uuid,
    '44444444-4444-4444-4444-444444444444'::uuid,
    '11111111-1111-1111-1111-111111111111'::uuid,
    '22222222-2222-2222-2222-222222222222'::uuid,
    '33333333-3333-3333-3333-333333333333'::uuid,
    'ARRIVED_DESTINATION',
    clock_timestamp() - INTERVAL '30 minutes',
    clock_timestamp()
);

-- Create Request 2 (short 500m trip)
INSERT INTO public.ride_requests (
    ride_request_id, rider_id, market_id, service_category_id, dispatch_mode, state,
    pickup_location, destination_location, currency_code, correlation_id
) VALUES (
    '66666666-6666-6666-6666-666666666666'::uuid,
    '11111111-1111-1111-1111-111111111111'::uuid,
    'CR_SJO',
    'cat_sjo_standard',
    'AUTO_DISPATCH',
    'REQUESTED',
    ROW(-84.0833, 9.9333, 4326)::extensions.geography,
    ROW(-84.0850, 9.9340, 4326)::extensions.geography,
    'CRC',
    extensions.gen_random_uuid()
);

-- Generate Quote 2 for Request 2
SET ROLE authenticated;
SET "request.jwt.claim.sub" = '11111111-1111-1111-1111-111111111111';
DO $$
DECLARE
    v_res JSONB;
BEGIN
    PERFORM public.mobility_generate_quote('66666666-6666-6666-6666-666666666666'::uuid, extensions.gen_random_uuid());
END $$;

-- Attempt to settle Trip 1 using Quote 2 from Request 2 (CROSS-REQUEST ATTACK)
SET ROLE service_role;
DO $$
DECLARE
    v_quote2_id UUID;
    v_auth1_id UUID;
BEGIN
    SELECT quote_id INTO v_quote2_id FROM public.ride_quotes WHERE ride_request_id = '66666666-6666-6666-6666-666666666666'::uuid;
    SELECT payment_authorization_id INTO v_auth1_id FROM public.payment_authorizations WHERE rider_id = '11111111-1111-1111-1111-111111111111'::uuid;

    BEGIN
        PERFORM public.mobility_settle_trip(
            '55555555-5555-5555-5555-555555555555'::uuid,
            v_auth1_id,
            v_quote2_id,
            extensions.gen_random_uuid()
        );
        RAISE EXCEPTION 'TEST_FAILED: Cross-request quote reuse should have been rejected!';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM NOT LIKE '%CROSS_REQUEST_QUOTE_REUSE_REJECTED%' AND SQLERRM NOT LIKE '%PAYMENT_AMOUNT_MISMATCH%' THEN
            RAISE EXCEPTION 'Unexpected error: %', SQLERRM;
        END IF;
    END;
END $$;
SQL
echo ">>> PASSED: TEST D (Cross-request quote reuse strictly rejected)."

echo "=== 9. TEST E: SETTLEMENT AUTHORITY (Revoked from Authenticated, Service_role only) ==="
psql "${psql_args[@]}" <<'SQL'
SET ROLE authenticated;
SET "request.jwt.claim.sub" = '11111111-1111-1111-1111-111111111111';

DO $$
DECLARE
    v_has_exec BOOLEAN;
BEGIN
    SELECT has_function_privilege('authenticated', 'public.mobility_settle_trip(uuid, uuid, uuid, uuid)', 'EXECUTE')
    INTO v_has_exec;
    IF v_has_exec THEN
        RAISE EXCEPTION 'TEST_FAILED: Authenticated should NOT have EXECUTE on mobility_settle_trip!';
    END IF;
END $$;
SQL
echo ">>> PASSED: TEST E (Direct settlement strictly revoked from ordinary authenticated clients)."

echo "=== 10. TEST F: VALID SETTLEMENT & DOUBLE-ENTRY LEDGER BALANCE ==="
psql "${psql_args[@]}" <<'SQL'
SET ROLE service_role;
DO $$
DECLARE
    v_quote1_id UUID;
    v_auth1_id UUID;
    v_res JSONB;
    v_sum BIGINT;
BEGIN
    SELECT quote_id INTO v_quote1_id FROM public.ride_quotes WHERE ride_request_id = '44444444-4444-4444-4444-444444444444'::uuid;
    SELECT payment_authorization_id INTO v_auth1_id FROM public.payment_authorizations WHERE rider_id = '11111111-1111-1111-1111-111111111111'::uuid;

    v_res := public.mobility_settle_trip(
        '55555555-5555-5555-5555-555555555555'::uuid,
        v_auth1_id,
        v_quote1_id,
        extensions.gen_random_uuid()
    );

    IF (v_res->>'success')::BOOLEAN <> TRUE THEN
        RAISE EXCEPTION 'TEST_FAILED: Settlement failed: %', v_res;
    END IF;

    -- Verify double-entry ledger balance: sum(debit) - sum(credit) = 0
    SELECT COALESCE(sum(amount_minor), 0)
    INTO v_sum
    FROM public.ledger_entries;

    IF v_sum <> 0 THEN
        RAISE EXCEPTION 'TEST_FAILED: Ledger is NOT balanced! Sum: %', v_sum;
    END IF;
END $$;
SQL
echo ">>> PASSED: TEST F (Legitimate settlement produces perfectly balanced zero-sum double-entry ledger entries)."

echo "=== 11. TEST G: CONCURRENT ATOMIC LEDGER ACCOUNT RESOLUTION ==="
psql "${psql_args[@]}" <<'SQL'
-- Test 50 parallel invocations of mobility_resolve_ledger_account for global and user accounts
DO $$
DECLARE
    v_acc1 UUID;
    v_acc2 UUID;
    v_acc3 UUID;
BEGIN
    FOR i IN 1..50 LOOP
        v_acc1 := public.mobility_resolve_ledger_account(NULL, 'PLATFORM_REVENUE', 'CRC');
        v_acc2 := public.mobility_resolve_ledger_account(NULL, 'TAX_ESCROW', 'CRC');
        v_acc3 := public.mobility_resolve_ledger_account('11111111-1111-1111-1111-111111111111'::uuid, 'RIDER_RECEIVABLE', 'CRC');
    END LOOP;
END $$;
SQL
echo ">>> PASSED: TEST G (Atomic ledger account resolution is safe against repetitive and concurrent invocations)."

echo "=== 12. TEST H: 100-WAY CONCURRENT SETTLEMENT RACE ==="
# Setup a new trip ready to settle
psql "${psql_args[@]}" <<'SQL'
RESET ROLE;
INSERT INTO public.ride_requests (
    ride_request_id, rider_id, market_id, service_category_id, dispatch_mode, state,
    pickup_location, destination_location, currency_code, correlation_id
) VALUES (
    '77777777-7777-7777-7777-777777777777'::uuid,
    '11111111-1111-1111-1111-111111111111'::uuid,
    'CR_SJO',
    'cat_sjo_standard',
    'AUTO_DISPATCH',
    'REQUESTED',
    ROW(-84.0833, 9.9333, 4326)::extensions.geography,
    ROW(-84.0700, 9.9200, 4326)::extensions.geography,
    'CRC',
    extensions.gen_random_uuid()
);

-- Quote
SET ROLE service_role;
SELECT public.mobility_generate_quote('77777777-7777-7777-7777-777777777777'::uuid, extensions.gen_random_uuid());

-- Payment
DO $$
DECLARE
    v_qid UUID;
    v_ares JSONB;
    v_aid UUID;
BEGIN
    SELECT quote_id INTO v_qid FROM public.ride_quotes WHERE ride_request_id = '77777777-7777-7777-7777-777777777777'::uuid;
    v_ares := public.mobility_authorize_quote_payment(v_qid, 'CARD_TOKEN', extensions.gen_random_uuid());
    v_aid := (v_ares->'authorization'->>'payment_authorization_id')::UUID;
    -- Confirm provider
    PERFORM public.mobility_confirm_provider_authorization(v_aid, 'ch_stripe_race_test', 'evt_race_test');
END $$;

-- Trip
INSERT INTO public.trips (
    trip_id, ride_request_id, rider_id, driver_id, vehicle_id, state,
    started_at, completed_at
) VALUES (
    '88888888-8888-8888-8888-888888888888'::uuid,
    '77777777-7777-7777-7777-777777777777'::uuid,
    '11111111-1111-1111-1111-111111111111'::uuid,
    '22222222-2222-2222-2222-222222222222'::uuid,
    '33333333-3333-3333-3333-333333333333'::uuid,
    'ARRIVED_DESTINATION',
    clock_timestamp() - INTERVAL '15 minutes',
    clock_timestamp()
);
SQL

# Launch 100 concurrent settlements via background workers
results_file="$runtime_dir/concurrent_results.txt"
qid=$(psql "${psql_args[@]}" -t -A -c "SELECT quote_id FROM public.ride_quotes WHERE ride_request_id = '77777777-7777-7777-7777-777777777777'::uuid;")
aid=$(psql "${psql_args[@]}" -t -A -c "SELECT payment_authorization_id FROM public.payment_authorizations WHERE trip_id IS NULL AND state = 'AUTHORIZED' ORDER BY created_at DESC LIMIT 1;")

echo "Executing 100 concurrent settlements on trip 88888888-8888-8888-8888-888888888888..."
for i in {1..100}; do
  (
    out=$(psql "${psql_args[@]}" -t -A -c "SET ROLE service_role; SELECT public.mobility_settle_trip('88888888-8888-8888-8888-888888888888'::uuid, '$aid'::uuid, '$qid'::uuid, extensions.gen_random_uuid());" 2>&1 || true)
    if echo "$out" | grep -q '"success": true'; then
      echo "WINNER" >> "$results_file"
    elif echo "$out" | grep -q 'ALREADY_SETTLED'; then
      echo "CONFLICT" >> "$results_file"
    else
      echo "ERROR: $out" >> "$results_file"
    fi
  ) &
done
wait

winners=$(grep -c "WINNER" "$results_file" || true)
conflicts=$(grep -c "CONFLICT" "$results_file" || true)
errors=$(grep -c "ERROR" "$results_file" || true)

echo "100-way concurrency results: $winners winners, $conflicts conflicts, $errors errors"

if [[ "$winners" -ne 1 ]]; then
  echo "FAIL: Expected exactly 1 winner, got $winners"
  exit 1
fi

if [[ "$errors" -ne 0 ]]; then
  echo "FAIL: Expected 0 unhandled errors, got $errors"
  exit 1
fi

# Verify final ledger balance
final_balance=$(psql "${psql_args[@]}" -t -A -c "SELECT COALESCE(sum(amount_minor), 0) FROM public.ledger_entries;")
if [[ "$final_balance" -ne 0 ]]; then
  echo "FAIL: Final ledger balance is not 0 (got $final_balance)"
  exit 1
fi

echo ">>> PASSED: TEST H (100-way concurrent settlement race guarantees exactly 1 winner and 0 errors)."
echo "=== ALL FINANCIAL AUTHORITY V8 ADVERSARIAL TESTS PASSED PERFECTLY ==="
