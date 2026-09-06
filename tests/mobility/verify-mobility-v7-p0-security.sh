#!/usr/bin/env bash
set -euo pipefail

# ─────────────────────────────────────────────────────────────────────────────
# ELYSIUM GLOBAL MOBILITY OS — V7 P0 SECURITY & HARDENING VERIFICATION SUITE
# Mandate: ORDEN MAESTRA V7 (P0/P1 Lockdown Verification)
# ─────────────────────────────────────────────────────────────────────────────

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

required_commands=(initdb pg_ctl psql)
for command_name in "${required_commands[@]}"; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Mobility V7 P0 security test: SKIP ($command_name unavailable)"
    exit 0
  fi
done

runtime_dir="$(mktemp -d "${TMPDIR:-/tmp}/meet-mobility-v7-p0.XXXXXX")"
cluster_dir="$runtime_dir/data"
socket_dir="$runtime_dir/socket"
server_log="$runtime_dir/postgres.log"
port="$((58700 + RANDOM % 500))"
mkdir -p "$socket_dir"

cleanup() {
  if [[ -d "$cluster_dir" ]]; then
    pg_ctl -D "$cluster_dir" stop -m fast >/dev/null 2>&1 || true
  fi
  if [[ -d "$runtime_dir" && "$(basename "$runtime_dir")" == meet-mobility-v7-p0.* ]]; then
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

grant usage on schema public to anon, authenticated;
grant usage on schema auth to anon, authenticated;
grant usage on schema extensions to anon, authenticated;
grant select on auth.users to authenticated;
SQL

echo "=== 3. Applying Migrations: V6 Market, Finance, Safety, Comms + V7 P0 Lockdown ==="
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906000000_mobility_market_authority.sql"
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906010000_mobility_financial_authority.sql"
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906020000_mobility_safety_and_reserve.sql"
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906030000_mobility_communications_reputation_and_surge.sql"
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906040000_mobility_financial_and_concurrency_p0_lockdown.sql"

echo "=== 4. Seeding Test Entities ==="
psql "${psql_args[@]}" <<'SQL'
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

INSERT INTO public.driver_market_eligibility (
    driver_id, market_id, is_eligible, background_check_cleared, documents_verified, active
) VALUES (
    '22222222-2222-2222-2222-222222222222'::uuid,
    'CR_GAM', true, true, true, true
);

INSERT INTO public.driver_vehicle_authorizations (
    driver_id, vehicle_id, is_authorized, active
) VALUES (
    '22222222-2222-2222-2222-222222222222'::uuid,
    '33333333-3333-3333-3333-333333333333'::uuid,
    true, true
);
SQL

echo "=== 5. Test: Client Attempt to Post Ledger Transaction is Strictly Revoked ==="
psql "${psql_args[@]}" <<'SQL'
DO $$
DECLARE
    v_attacker uuid := '11111111-1111-1111-1111-111111111111'::uuid;
    v_blocked boolean := false;
BEGIN
    PERFORM set_config('role', 'authenticated', true);
    PERFORM set_config('request.jwt.claim.sub', v_attacker::text, true);
    PERFORM set_config('request.jwt.claim.role', 'authenticated', true);

    BEGIN
        PERFORM public.mobility_post_ledger_transaction(
            p_reference_type := 'FRAUD',
            p_reference_id := gen_random_uuid(),
            p_currency_code := 'CRC',
            p_entries := '[]'::jsonb
        );
    EXCEPTION
        WHEN insufficient_privilege THEN
            v_blocked := true;
    END;

    IF NOT v_blocked THEN
        RAISE EXCEPTION 'SECURITY BREACH: authenticated user was able to execute mobility_post_ledger_transaction!';
    END IF;
END $$;
SQL
echo ">>> PASSED: mobility_post_ledger_transaction strictly revoked from authenticated users (42501)."

echo "=== 6. Test: Direct Mutation on Financial Tables is Blocked by RLS/Grants ==="
psql "${psql_args[@]}" <<'SQL'
DO $$
DECLARE
    v_attacker uuid := '11111111-1111-1111-1111-111111111111'::uuid;
    v_acc_blocked boolean := false;
    v_tx_blocked boolean := false;
    v_settle_blocked boolean := false;
BEGIN
    PERFORM set_config('role', 'authenticated', true);
    PERFORM set_config('request.jwt.claim.sub', v_attacker::text, true);
    PERFORM set_config('request.jwt.claim.role', 'authenticated', true);

    BEGIN
        INSERT INTO public.ledger_accounts (account_id, account_type, currency_code, owner_id)
        VALUES (gen_random_uuid(), 'USER_WALLET', 'CRC', v_attacker);
    EXCEPTION
        WHEN insufficient_privilege THEN
            v_acc_blocked := true;
    END;

    BEGIN
        INSERT INTO public.ledger_transactions (transaction_id, reference_type, reference_id, currency_code)
        VALUES (gen_random_uuid(), 'TRIP_SETTLEMENT', gen_random_uuid(), 'CRC');
    EXCEPTION
        WHEN insufficient_privilege THEN
            v_tx_blocked := true;
    END;

    BEGIN
        INSERT INTO public.trip_settlements (settlement_id, trip_id, gross_fare_minor, platform_fee_minor, driver_earnings_minor, currency_code, ledger_transaction_id)
        VALUES (gen_random_uuid(), gen_random_uuid(), 100000, 20000, 80000, 'CRC', gen_random_uuid());
    EXCEPTION
        WHEN insufficient_privilege THEN
            v_settle_blocked := true;
    END;

    IF NOT (v_acc_blocked AND v_tx_blocked AND v_settle_blocked) THEN
        RAISE EXCEPTION 'SECURITY BREACH: Direct table mutations were not rejected for authenticated users! acc=% tx=% settle=%',
            v_acc_blocked, v_tx_blocked, v_settle_blocked;
    END IF;
END $$;
SQL
echo ">>> PASSED: Direct INSERT on ledger_accounts, ledger_transactions, trip_settlements rejected."

echo "=== 7. Test: Server Payment Authorization RPC binds strictly to Quote ==="
psql "${psql_args[@]}" <<'SQL'
DO $$
DECLARE
    v_rider uuid := '11111111-1111-1111-1111-111111111111'::uuid;
    v_quote jsonb;
    v_quote_id uuid;
    v_auth_res jsonb;
    v_quote_amount bigint;
    v_quote_currency text;
    v_auth_amount bigint;
    v_auth_currency text;
BEGIN
    PERFORM set_config('role', 'authenticated', true);
    PERFORM set_config('request.jwt.claim.sub', v_rider::text, true);
    PERFORM set_config('request.jwt.claim.role', 'authenticated', true);

    v_quote := public.mobility_generate_quote(
        p_market_id := 'CR_GAM',
        p_service_category_id := 'STD_RIDE',
        p_distance_meters := 8500,
        p_duration_seconds := 720
    );

    v_quote_id := (v_quote -> 'quote' ->> 'quote_id')::uuid;
    v_quote_amount := (v_quote -> 'quote' ->> 'total_fare_minor')::bigint;
    v_quote_currency := v_quote -> 'quote' ->> 'currency_code';

    v_auth_res := public.mobility_authorize_quote_payment(
        p_quote_id := v_quote_id,
        p_provider := 'CARD_TOKEN',
        p_provider_auth_ref := 'pm_visa_4242',
        p_idempotency_key := gen_random_uuid()
    );

    v_auth_amount := (v_auth_res -> 'authorization' ->> 'amount_minor')::bigint;
    v_auth_currency := v_auth_res -> 'authorization' ->> 'currency_code';

    IF v_auth_amount IS NULL OR v_auth_amount <> v_quote_amount OR v_auth_currency <> v_quote_currency THEN
        RAISE EXCEPTION 'PAYMENT AUTHORIZATION DISCREPANCY: quote=(% %) vs auth=(% %)',
            v_quote_amount, v_quote_currency, v_auth_amount, v_auth_currency;
    END IF;

    IF (v_auth_res -> 'authorization' ->> 'state') <> 'AUTHORIZED' THEN
        RAISE EXCEPTION 'PAYMENT AUTHORIZATION FAILED: state=%', v_auth_res -> 'authorization' ->> 'state';
    END IF;
END $$;
SQL
echo ">>> PASSED: mobility_authorize_quote_payment derived amount/currency strictly from server quote."

echo "=== 8. Test: Settlement Cross-Validation Prevents Fraudulent Claims ==="
psql "${psql_args[@]}" <<'SQL'
DO $$
DECLARE
    v_rider uuid := '11111111-1111-1111-1111-111111111111'::uuid;
    v_driver uuid := '22222222-2222-2222-2222-222222222222'::uuid;
    v_quote jsonb;
    v_quote_id uuid;
    v_auth jsonb;
    v_auth_id uuid;
    v_req jsonb;
    v_request_id uuid;
    v_trip_id uuid;
    v_quote_amount bigint;
    v_settle_res jsonb;
BEGIN
    PERFORM set_config('role', 'authenticated', true);
    PERFORM set_config('request.jwt.claim.sub', v_rider::text, true);
    PERFORM set_config('request.jwt.claim.role', 'authenticated', true);

    v_quote := public.mobility_generate_quote(
        p_market_id := 'CR_GAM',
        p_service_category_id := 'STD_RIDE',
        p_distance_meters := 5000,
        p_duration_seconds := 600
    );
    v_quote_id := (v_quote -> 'quote' ->> 'quote_id')::uuid;
    v_quote_amount := (v_quote -> 'quote' ->> 'total_fare_minor')::bigint;

    v_auth := public.mobility_authorize_quote_payment(
        p_quote_id := v_quote_id,
        p_provider := 'CARD_TOKEN',
        p_provider_auth_ref := 'pm_tok_123',
        p_idempotency_key := gen_random_uuid()
    );
    v_auth_id := (v_auth -> 'authorization' ->> 'payment_authorization_id')::uuid;

    v_req := public.mobility_request_ride(
        p_market_id := 'CR_GAM',
        p_service_category_id := 'STD_RIDE',
        p_dispatch_mode := 'AUTO_DISPATCH',
        p_pickup_lat := 9.9333,
        p_pickup_lng := -84.0833,
        p_pickup_accuracy := 5.0,
        p_pickup_address := 'San Jose Central',
        p_destination_lat := 9.9400,
        p_destination_lng := -84.0900,
        p_destination_accuracy := 5.0,
        p_destination_address := 'San Jose North',
        p_idempotency_key := gen_random_uuid()
    );
    v_request_id := (v_req ->> 'ride_request_id')::uuid;

    -- Switch to superuser to seed trip and cross-check records
    PERFORM set_config('role', session_user, true);

    INSERT INTO public.trips (
        trip_id, ride_request_id, rider_id, driver_id, vehicle_id,
        state, quote_id, payment_authorization_id, assigned_at, started_at, completed_at
    ) VALUES (
        gen_random_uuid(), v_request_id, v_rider, v_driver,
        '33333333-3333-3333-3333-333333333333'::uuid,
        'ARRIVED_DESTINATION', v_quote_id, v_auth_id,
        now() - interval '30 minutes', now() - interval '20 minutes', now()
    ) RETURNING trip_id INTO v_trip_id;

    -- Create another user & payment authorization to test rider mismatch
    INSERT INTO auth.users (id) VALUES ('99999999-9999-9999-9999-999999999999'::uuid);
    INSERT INTO public.payment_authorizations (
        payment_authorization_id, rider_id, provider, amount_minor, currency_code, state
    ) VALUES (
        '88888888-8888-8888-8888-888888888888'::uuid,
        '99999999-9999-9999-9999-999999999999'::uuid,
        'CARD_TOKEN', v_quote_amount, 'CRC', 'AUTHORIZED'
    );

    -- Switch to driver
    PERFORM set_config('role', 'authenticated', true);
    PERFORM set_config('request.jwt.claim.sub', v_driver::text, true);
    PERFORM set_config('request.jwt.claim.role', 'authenticated', true);

    -- Case A: Mismatched rider authorization vs trip rider
    DECLARE
        v_rider_mismatch_blocked boolean := false;
    BEGIN
        BEGIN
            PERFORM public.mobility_settle_trip(
                p_trip_id := v_trip_id,
                p_payment_authorization_id := '88888888-8888-8888-8888-888888888888'::uuid,
                p_quote_id := v_quote_id,
                p_idempotency_key := gen_random_uuid()
            );
        EXCEPTION
            WHEN OTHERS THEN
                IF SQLERRM LIKE '%PAYMENT_RIDER_MISMATCH%' THEN
                    v_rider_mismatch_blocked := true;
                END IF;
        END;

        IF NOT v_rider_mismatch_blocked THEN
            RAISE EXCEPTION 'Expected PAYMENT_RIDER_MISMATCH exception!';
        END IF;
    END;

    -- Case B: Valid settlement completes successfully
    v_settle_res := public.mobility_settle_trip(
        p_trip_id := v_trip_id,
        p_payment_authorization_id := v_auth_id,
        p_quote_id := v_quote_id,
        p_idempotency_key := gen_random_uuid()
    );

    IF (v_settle_res ->> 'success') <> 'true' THEN
        RAISE EXCEPTION 'Expected successful settlement, got: %', v_settle_res;
    END IF;
END $$;
SQL
echo ">>> PASSED: mobility_settle_trip rejected mismatched rider and accepted valid quote-backed settlement."

echo "=== 9. Test: Partial Unique Index Prevents Duplicate Global Accounts ==="
psql "${psql_args[@]}" <<'SQL'
DO $$
DECLARE
    v_dup_blocked boolean := false;
BEGIN
    BEGIN
        INSERT INTO public.ledger_accounts (account_id, account_type, currency_code, owner_id)
        VALUES (gen_random_uuid(), 'PLATFORM_REVENUE', 'CRC', NULL);
    EXCEPTION
        WHEN unique_violation THEN
            v_dup_blocked := true;
    END;

    IF NOT v_dup_blocked THEN
        RAISE EXCEPTION 'SECURITY ERROR: Duplicate global account was allowed without owner_id!';
    END IF;
END $$;
SQL
echo ">>> PASSED: ux_ledger_global_account partial unique index strictly prevents duplicate global accounts."

echo "=== 10. Test: Atomic Driver Presence CAS (Highest Sequence Wins) ==="
psql "${psql_args[@]}" <<'SQL'
DO $$
DECLARE
    v_driver uuid := '22222222-2222-2222-2222-222222222222'::uuid;
    v_res jsonb;
    v_latest_seq bigint;
BEGIN
    -- Switch to driver
    PERFORM set_config('role', 'authenticated', true);
    PERFORM set_config('request.jwt.claim.sub', v_driver::text, true);
    PERFORM set_config('request.jwt.claim.role', 'authenticated', true);

    v_res := public.mobility_update_driver_presence(
        p_market_id := 'CR_GAM',
        p_vehicle_id := '33333333-3333-3333-3333-333333333333'::uuid,
        p_state := 'AVAILABLE',
        p_lat := 9.9333,
        p_lng := -84.0833,
        p_heading := 90.0::real,
        p_speed_mps := 12.5::real,
        p_sequence_id := 100
    );

    IF (v_res ->> 'success') <> 'true' OR (v_res ->> 'sequence_id')::bigint <> 100 THEN
        RAISE EXCEPTION 'Expected successful update on seq 100, got: %', v_res;
    END IF;

    -- Out of order update seq=50 (older) must raise STALE_SEQUENCE_ID
    DECLARE
        v_stale_caught boolean := false;
    BEGIN
        BEGIN
            PERFORM public.mobility_update_driver_presence(
                p_market_id := 'CR_GAM',
                p_vehicle_id := '33333333-3333-3333-3333-333333333333'::uuid,
                p_state := 'AVAILABLE',
                p_lat := 9.9350,
                p_lng := -84.0850,
                p_heading := 180.0::real,
                p_speed_mps := 10.0::real,
                p_sequence_id := 50
            );
        EXCEPTION
            WHEN OTHERS THEN
                IF SQLERRM LIKE '%STALE_SEQUENCE_ID%' THEN
                    v_stale_caught := true;
                END IF;
        END;

        IF NOT v_stale_caught THEN
            RAISE EXCEPTION 'Expected STALE_SEQUENCE_ID exception on seq 50!';
        END IF;
    END;

    -- Verify stored snapshot still has sequence 100
    PERFORM set_config('role', session_user, true);
    SELECT sequence_id INTO v_latest_seq FROM public.driver_presence_snapshot WHERE driver_id = v_driver;
    IF v_latest_seq <> 100 THEN
        RAISE EXCEPTION 'Stored sequence was overwritten! Expected 100, got: %', v_latest_seq;
    END IF;

    -- Switch back to driver for update seq=150
    PERFORM set_config('role', 'authenticated', true);
    PERFORM set_config('request.jwt.claim.sub', v_driver::text, true);
    PERFORM set_config('request.jwt.claim.role', 'authenticated', true);

    -- Update with newer seq=150
    v_res := public.mobility_update_driver_presence(
        p_market_id := 'CR_GAM',
        p_vehicle_id := '33333333-3333-3333-3333-333333333333'::uuid,
        p_state := 'AVAILABLE',
        p_lat := 9.9380,
        p_lng := -84.0880,
        p_heading := 270.0::real,
        p_speed_mps := 15.0::real,
        p_sequence_id := 150
    );

    IF (v_res ->> 'success') <> 'true' OR (v_res ->> 'sequence_id')::bigint <> 150 THEN
        RAISE EXCEPTION 'Expected successful update on seq 150, got: %', v_res;
    END IF;

    PERFORM set_config('role', session_user, true);
    SELECT sequence_id INTO v_latest_seq FROM public.driver_presence_snapshot WHERE driver_id = v_driver;
    IF v_latest_seq <> 150 THEN
        RAISE EXCEPTION 'Expected stored sequence 150, got: %', v_latest_seq;
    END IF;
END $$;
SQL
echo ">>> PASSED: Atomic driver presence CAS guarantees monotonic sequence ordering."

echo "=== 11. Test: Idempotency Digest Detects Modified Intermediate Stops ==="
psql "${psql_args[@]}" <<'SQL'
DO $$
DECLARE
    v_rider uuid := '11111111-1111-1111-1111-111111111111'::uuid;
    v_idem_key uuid := gen_random_uuid();
    v_req1 jsonb;
    v_req_conflict boolean := false;
BEGIN
    PERFORM set_config('role', 'authenticated', true);
    PERFORM set_config('request.jwt.claim.sub', v_rider::text, true);
    PERFORM set_config('request.jwt.claim.role', 'authenticated', true);

    v_req1 := public.mobility_request_ride(
        p_market_id := 'CR_GAM',
        p_service_category_id := 'STD_RIDE',
        p_dispatch_mode := 'AUTO_DISPATCH',
        p_pickup_lat := 9.9300,
        p_pickup_lng := -84.0800,
        p_pickup_accuracy := 5.0,
        p_pickup_address := 'Pickup A',
        p_destination_lat := 9.9500,
        p_destination_lng := -84.1000,
        p_destination_accuracy := 5.0,
        p_destination_address := 'Dropoff A',
        p_intermediate_stops := '[{"latitude": 9.935, "longitude": -84.085, "address": "Stop 1"}]'::jsonb,
        p_idempotency_key := v_idem_key
    );

    IF (v_req1 ->> 'ride_request_id') IS NULL THEN
        RAISE EXCEPTION 'Request 1 failed: %', v_req1;
    END IF;

    BEGIN
        PERFORM public.mobility_request_ride(
            p_market_id := 'CR_GAM',
            p_service_category_id := 'STD_RIDE',
            p_dispatch_mode := 'AUTO_DISPATCH',
            p_pickup_lat := 9.9300,
            p_pickup_lng := -84.0800,
            p_pickup_accuracy := 5.0,
            p_pickup_address := 'Pickup A',
            p_destination_lat := 9.9500,
            p_destination_lng := -84.1000,
            p_destination_accuracy := 5.0,
            p_destination_address := 'Dropoff A',
            p_intermediate_stops := '[{"latitude": 9.939, "longitude": -84.089, "address": "TAMPERED Stop 2"}]'::jsonb,
            p_idempotency_key := v_idem_key
        );
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLERRM LIKE '%IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD%' THEN
                v_req_conflict := true;
            ELSE
                RAISE;
            END IF;
    END;

    IF NOT v_req_conflict THEN
        RAISE EXCEPTION 'SECURITY ERROR: Reused idempotency key with tampered stops was not rejected!';
    END IF;
END $$;
SQL
echo ">>> PASSED: Request digest detects altered intermediate stops on reused idempotency key."

echo "=== ALL MOBILITY V7 P0 SECURITY & CONCURRENCY TESTS PASSED! ==="
