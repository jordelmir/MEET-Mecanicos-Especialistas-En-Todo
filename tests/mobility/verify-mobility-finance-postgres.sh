#!/usr/bin/env bash
set -euo pipefail

# ─────────────────────────────────────────────────────────────────────────────
# ELYSIUM GLOBAL MOBILITY OS — FINANCIAL AUTHORITY & BALANCED LEDGER TEST SUITE
# Mandate: ORDEN MAESTRA V6 (Waves 11–14 Financial Verification)
# ─────────────────────────────────────────────────────────────────────────────

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

required_commands=(initdb pg_ctl psql)
for command_name in "${required_commands[@]}"; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Mobility finance test: SKIP ($command_name unavailable)"
    exit 0
  fi
done

runtime_dir="$(mktemp -d "${TMPDIR:-/tmp}/meet-mobility-fin.XXXXXX")"
cluster_dir="$runtime_dir/data"
socket_dir="$runtime_dir/socket"
server_log="$runtime_dir/postgres.log"
port="$((58500 + RANDOM % 500))"
mkdir -p "$socket_dir"

cleanup() {
  if [[ -d "$cluster_dir" ]]; then
    pg_ctl -D "$cluster_dir" stop -m fast >/dev/null 2>&1 || true
  fi
  if [[ -d "$runtime_dir" && "$(basename "$runtime_dir")" == meet-mobility-fin.* ]]; then
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

echo "=== 3. Applying Mobility Authority Migrations V6 ==="
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906000000_mobility_market_authority.sql"
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906010000_mobility_financial_authority.sql"

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

echo "=== 5. Test: Upfront Quote Generation with Rational Surge Multiplier ==="
psql "${psql_args[@]}" <<'SQL'
DO $$
DECLARE
    v_res JSONB;
    v_quote JSONB;
    v_total BIGINT;
    v_base BIGINT;
    v_dist BIGINT;
    v_time BIGINT;
    v_surge BIGINT;
    v_tax BIGINT;
BEGIN
    SET ROLE authenticated;
    SET request.jwt.claims = '{"sub":"11111111-1111-1111-1111-111111111111"}';

    -- 8.5 km, 900 seconds (15 min), surge 1.5 (3/2)
    v_res := public.mobility_generate_quote(
        'CR_SJO',
        'cat_sjo_standard',
        8500,
        900,
        3,
        2
    );

    IF (v_res->>'success')::boolean IS NOT TRUE THEN
        RAISE EXCEPTION 'Quote generation failed: %', v_res;
    END IF;

    v_quote := v_res->'quote';
    v_total := (v_quote->>'total_fare_minor')::BIGINT;
    v_base := (v_quote->>'base_fare_minor')::BIGINT;
    v_dist := (v_quote->>'distance_fare_minor')::BIGINT;
    v_time := (v_quote->>'time_fare_minor')::BIGINT;
    v_surge := (v_quote->>'surge_adjustment_minor')::BIGINT;
    v_tax := (v_quote->>'tax_minor')::BIGINT;

    -- Verify non-zero and positive
    IF v_base <= 0 OR v_dist <= 0 OR v_time <= 0 OR v_surge <= 0 OR v_tax <= 0 THEN
        RAISE EXCEPTION 'Fare breakdown has invalid zero/negative fields';
    END IF;

    -- Verify formula: total == base + dist + time + surge + tax
    IF v_total <> (v_base + v_dist + v_time + v_surge + v_tax) THEN
        RAISE EXCEPTION 'Total fare % does not match sum of components %', v_total, (v_base + v_dist + v_time + v_surge + v_tax);
    END IF;

    -- Verify currency
    IF v_quote->>'currency_code' <> 'CRC' THEN
        RAISE EXCEPTION 'Unexpected currency code %', v_quote->>'currency_code';
    END IF;
END $$;
SQL
echo ">>> PASSED: Upfront quote generated with rational surge and exact breakdown."

echo "=== 6. Test: Payment Authorization & Strict Guardrails ==="
psql "${psql_args[@]}" <<'SQL'
DO $$
DECLARE
    v_res JSONB;
    v_auth JSONB;
BEGIN
    SET ROLE authenticated;
    SET request.jwt.claims = '{"sub":"11111111-1111-1111-1111-111111111111"}';

    v_res := public.mobility_authorize_payment(
        '11111111-1111-1111-1111-111111111111'::uuid,
        'CARD_TOKEN',
        6500,
        'CRC',
        'tok_visa_4242'
    );

    IF (v_res->>'success')::boolean IS NOT TRUE THEN
        RAISE EXCEPTION 'Payment authorization failed: %', v_res;
    END IF;

    v_auth := v_res->'authorization';
    IF v_auth->>'state' <> 'AUTHORIZED' THEN
        RAISE EXCEPTION 'Expected AUTHORIZED state, got %', v_auth->>'state';
    END IF;
END $$;
SQL

# Rejection test: Rider cannot authorize payment under another user's ID
forbidden_err=$(psql "${psql_args[@]}" -c "
SET ROLE authenticated;
SET request.jwt.claims = '{\"sub\":\"11111111-1111-1111-1111-111111111111\"}';
SELECT public.mobility_authorize_payment(
    '22222222-2222-2222-2222-222222222222'::uuid,
    'CARD_TOKEN',
    5000,
    'CRC'
);
" 2>&1 || true)

if [[ "$forbidden_err" != *"FORBIDDEN"* ]]; then
  echo "FAIL: Expected FORBIDDEN when rider authorizes for another user. Got: $forbidden_err"
  exit 1
fi
echo ">>> PASSED: Payment authorization created and cross-user authorization rejected."

echo "=== 7. Test: Double-Entry Balanced Ledger Invariant (Rejection of Unbalanced Transaction) ==="
# Setup ledger accounts with administrative authority
psql "${psql_args[@]}" <<'SQL'
INSERT INTO public.ledger_accounts (owner_id, account_type, currency_code)
VALUES ('11111111-1111-1111-1111-111111111111'::uuid, 'RIDER_RECEIVABLE', 'CRC')
ON CONFLICT DO NOTHING;

INSERT INTO public.ledger_accounts (owner_id, account_type, currency_code)
VALUES ('22222222-2222-2222-2222-222222222222'::uuid, 'DRIVER_PAYABLE', 'CRC')
ON CONFLICT DO NOTHING;

INSERT INTO public.ledger_accounts (owner_id, account_type, currency_code)
VALUES (NULL, 'PLATFORM_REVENUE', 'CRC')
ON CONFLICT DO NOTHING;
SQL

# Attempt unbalanced transaction: +5000 and -4000 (Sum = +1000 != 0)
unbalanced_err=$(psql "${psql_args[@]}" -c "
SET ROLE authenticated;
SET request.jwt.claims = '{\"sub\":\"11111111-1111-1111-1111-111111111111\"}';
DO \$\$
DECLARE
    v_acc1 UUID;
    v_acc2 UUID;
BEGIN
    SELECT account_id INTO v_acc1 FROM public.ledger_accounts WHERE owner_id = '11111111-1111-1111-1111-111111111111'::uuid;
    SELECT account_id INTO v_acc2 FROM public.ledger_accounts WHERE owner_id = '22222222-2222-2222-2222-222222222222'::uuid;

    PERFORM public.mobility_post_ledger_transaction(
        'DISPUTE_ADJUSTMENT',
        '88888888-8888-8888-8888-888888888888'::uuid,
        'CRC',
        jsonb_build_array(
            jsonb_build_object('account_id', v_acc1, 'amount_minor', 5000),
            jsonb_build_object('account_id', v_acc2, 'amount_minor', -4000)
        )
    );
END \$\$;
" 2>&1 || true)

if [[ "$unbalanced_err" != *"UNBALANCED_LEDGER_TRANSACTION"* && "$unbalanced_err" != *"23514"* ]]; then
  echo "FAIL: Expected UNBALANCED_LEDGER_TRANSACTION (23514). Got: $unbalanced_err"
  exit 1
fi
echo ">>> PASSED: Unbalanced ledger transaction strictly rejected with error code 23514."

echo "=== 8. Test: Posting Strictly Balanced Ledger Transaction ==="
psql "${psql_args[@]}" <<'SQL'
DO $$
DECLARE
    v_acc1 UUID;
    v_acc2 UUID;
    v_acc3 UUID;
    v_res JSONB;
BEGIN
    SET ROLE authenticated;
    SET request.jwt.claims = '{"sub":"11111111-1111-1111-1111-111111111111"}';

    SELECT account_id INTO v_acc1 FROM public.ledger_accounts WHERE owner_id = '11111111-1111-1111-1111-111111111111'::uuid;
    SELECT account_id INTO v_acc2 FROM public.ledger_accounts WHERE owner_id = '22222222-2222-2222-2222-222222222222'::uuid;
    SELECT account_id INTO v_acc3 FROM public.ledger_accounts WHERE owner_id IS NULL AND account_type = 'PLATFORM_REVENUE';

    -- Balanced: +5000 (Rider) - 4250 (Driver) - 750 (Platform) = 0
    v_res := public.mobility_post_ledger_transaction(
        'DISPUTE_ADJUSTMENT',
        '88888888-8888-8888-8888-888888888888'::uuid,
        'CRC',
        jsonb_build_array(
            jsonb_build_object('account_id', v_acc1, 'amount_minor', 5000),
            jsonb_build_object('account_id', v_acc2, 'amount_minor', -4250),
            jsonb_build_object('account_id', v_acc3, 'amount_minor', -750)
        )
    );

    IF (v_res->>'success')::boolean IS NOT TRUE THEN
        RAISE EXCEPTION 'Failed to post balanced transaction: %', v_res;
    END IF;
END $$;
SQL
echo ">>> PASSED: Balanced double-entry transaction successfully committed."

echo "=== 9. Test: End-to-End Trip Lifecycle & Atomic Financial Settlement CAS ==="
psql "${psql_args[@]}" <<'SQL'
-- 1. Rider requests quote, authorizes payment, requests ride
DO $$
DECLARE
    v_quote_res JSONB;
    v_quote_id UUID;
    v_auth_res JSONB;
    v_auth_id UUID;
    v_req_res JSONB;
    v_req_id UUID;
BEGIN
    SET ROLE authenticated;
    SET request.jwt.claims = '{"sub":"11111111-1111-1111-1111-111111111111"}';

    v_quote_res := public.mobility_generate_quote('CR_SJO', 'cat_sjo_standard', 5000, 600, 1, 1);
    v_quote_id := (v_quote_res->'quote'->>'quote_id')::uuid;

    v_auth_res := public.mobility_authorize_payment(
        '11111111-1111-1111-1111-111111111111'::uuid,
        'CARD_TOKEN',
        (v_quote_res->'quote'->>'total_fare_minor')::BIGINT,
        'CRC',
        'auth_ref_live_test_1'
    );
    v_auth_id := (v_auth_res->'authorization'->>'payment_authorization_id')::uuid;

    v_req_res := public.mobility_request_ride(
        'CR_SJO',
        'cat_sjo_standard',
        'AUTO_DISPATCH',
        9.9350, -84.0750, 5.0, 'Paseo Colon',
        9.9281, -84.0907, 5.0, 'San Pedro',
        '[]'::jsonb,
        (v_quote_res->'quote'->>'total_fare_minor')::BIGINT,
        NULL,
        '77777777-7777-7777-7777-777777777771'::uuid,
        '77777777-7777-7777-7777-777777777772'::uuid
    );
END $$;
RESET ROLE;

-- 2. Dispatch Engine emits dispatch offer (Server Authority)
INSERT INTO public.dispatch_offers (
    dispatch_offer_id, ride_request_id, driver_id, vehicle_id, state, expires_at
)
SELECT
    '77777777-7777-7777-7777-777777777770'::uuid,
    ride_request_id,
    '22222222-2222-2222-2222-222222222222'::uuid,
    '33333333-3333-3333-3333-333333333333'::uuid,
    'PENDING', clock_timestamp() + INTERVAL '30 seconds'
FROM public.ride_requests
WHERE correlation_id = '77777777-7777-7777-7777-777777777772'::uuid;

-- 3. Driver accepts dispatch offer
DO $$
DECLARE
    v_req_id UUID;
    v_claim_res JSONB;
BEGIN
    SELECT ride_request_id INTO v_req_id FROM public.ride_requests WHERE correlation_id = '77777777-7777-7777-7777-777777777772'::uuid;

    SET ROLE authenticated;
    SET request.jwt.claims = '{"sub":"22222222-2222-2222-2222-222222222222"}';

    v_claim_res := public.mobility_accept_dispatch(
        v_req_id,
        '77777777-7777-7777-7777-777777777770'::uuid,
        '33333333-3333-3333-3333-333333333333'::uuid,
        1,
        '77777777-7777-7777-7777-777777777773'::uuid
    );
END $$;
RESET ROLE;

-- 4. Server provisions predictable PIN hash for test
UPDATE public.trips
SET verification_pin_hash = encode(extensions.digest('1234', 'sha256'), 'hex')
WHERE ride_request_id = (SELECT ride_request_id FROM public.ride_requests WHERE correlation_id = '77777777-7777-7777-7777-777777777772'::uuid);

-- 5. Driver fulfills trip with PIN and executes atomic settlement CAS
DO $$
DECLARE
    v_trip_id UUID;
    v_quote_id UUID;
    v_auth_id UUID;
    v_trans_res JSONB;
    v_settle_res JSONB;
    v_settlement JSONB;
    v_tx_id UUID;
    v_ledger_sum BIGINT;
BEGIN
    SELECT trip_id INTO v_trip_id FROM public.trips WHERE ride_request_id = (SELECT ride_request_id FROM public.ride_requests WHERE correlation_id = '77777777-7777-7777-7777-777777777772'::uuid);
    SELECT quote_id INTO v_quote_id FROM public.ride_quotes LIMIT 1;
    SELECT payment_authorization_id INTO v_auth_id FROM public.payment_authorizations WHERE provider_auth_ref = 'auth_ref_live_test_1' LIMIT 1;

    SET ROLE authenticated;
    SET request.jwt.claims = '{"sub":"22222222-2222-2222-2222-222222222222"}';

    v_trans_res := public.mobility_transition_trip(v_trip_id, 'DRIVER_EN_ROUTE', 1, NULL, '77777777-7777-7777-7777-777777777774'::uuid);
    IF (v_trans_res->>'success')::boolean IS NOT TRUE THEN RAISE EXCEPTION 'DRIVER_EN_ROUTE failed: %', v_trans_res; END IF;

    v_trans_res := public.mobility_transition_trip(v_trip_id, 'DRIVER_ARRIVED', 2, NULL, '77777777-7777-7777-7777-777777777775'::uuid);
    IF (v_trans_res->>'success')::boolean IS NOT TRUE THEN RAISE EXCEPTION 'DRIVER_ARRIVED failed: %', v_trans_res; END IF;

    v_trans_res := public.mobility_transition_trip(v_trip_id, 'RIDER_ONBOARD', 3, '1234', '77777777-7777-7777-7777-777777777776'::uuid);
    IF (v_trans_res->>'success')::boolean IS NOT TRUE THEN RAISE EXCEPTION 'RIDER_ONBOARD failed: %', v_trans_res; END IF;

    v_trans_res := public.mobility_transition_trip(v_trip_id, 'IN_PROGRESS', 4, NULL, '77777777-7777-7777-7777-777777777777'::uuid);
    IF (v_trans_res->>'success')::boolean IS NOT TRUE THEN RAISE EXCEPTION 'IN_PROGRESS failed: %', v_trans_res; END IF;

    v_trans_res := public.mobility_transition_trip(v_trip_id, 'ARRIVED_DESTINATION', 5, NULL, '77777777-7777-7777-7777-777777777778'::uuid);
    IF (v_trans_res->>'success')::boolean IS NOT TRUE THEN RAISE EXCEPTION 'ARRIVED_DESTINATION failed: %', v_trans_res; END IF;

    v_settle_res := public.mobility_settle_trip(
        v_trip_id,
        v_auth_id,
        v_quote_id,
        '77777777-7777-7777-7777-777777777779'::uuid
    );

    IF (v_settle_res->>'success')::boolean IS NOT TRUE THEN
        RAISE EXCEPTION 'Trip settlement failed: %', v_settle_res;
    END IF;

    v_settlement := v_settle_res->'settlement';
    v_tx_id := (v_settlement->>'ledger_transaction_id')::uuid;

    SELECT sum(amount_minor) INTO v_ledger_sum
    FROM public.ledger_entries
    WHERE transaction_id = v_tx_id;

    IF v_ledger_sum <> 0 THEN
        RAISE EXCEPTION 'CRITICAL: Ledger entries sum is % (expected strictly 0)', v_ledger_sum;
    END IF;

    IF (SELECT state FROM public.payment_authorizations WHERE payment_authorization_id = v_auth_id) <> 'CAPTURED' THEN
        RAISE EXCEPTION 'Expected payment authorization to be CAPTURED';
    END IF;

    IF (SELECT state FROM public.trips WHERE trip_id = v_trip_id) <> 'COMPLETED' THEN
        RAISE EXCEPTION 'Expected trip to be COMPLETED';
    END IF;
END $$;
SQL
echo ">>> PASSED: Trip progressed to ARRIVED_DESTINATION and settled with zero-sum ledger balance."

echo "=== 10. Test: Idempotent Settlement Replay & Tampered Key Rejection ==="
psql "${psql_args[@]}" <<'SQL'
DO $$
DECLARE
    v_trip_id UUID;
    v_auth_id UUID;
    v_quote_id UUID;
    v_res1 JSONB;
BEGIN
    SET ROLE authenticated;
    SET request.jwt.claims = '{"sub":"22222222-2222-2222-2222-222222222222"}';

    SELECT trip_id, payment_authorization_id, quote_id
    INTO v_trip_id, v_auth_id, v_quote_id
    FROM public.trips WHERE settlement_id IS NOT NULL LIMIT 1;

    -- Idempotent replay with same key and payload
    v_res1 := public.mobility_settle_trip(
        v_trip_id,
        v_auth_id,
        v_quote_id,
        '77777777-7777-7777-7777-777777777779'::uuid
    );

    IF (v_res1->>'success')::boolean IS NOT TRUE THEN
        RAISE EXCEPTION 'Idempotent replay failed';
    END IF;
END $$;
SQL

# Replay with same key but different quote (tampered payload)
tamper_fin_err=$(psql "${psql_args[@]}" -c "
SET ROLE authenticated;
SET request.jwt.claims = '{\"sub\":\"22222222-2222-2222-2222-222222222222\"}';
DO \$\$
DECLARE
    v_trip_id UUID;
    v_auth_id UUID;
BEGIN
    SELECT trip_id, payment_authorization_id
    INTO v_trip_id, v_auth_id
    FROM public.trips WHERE settlement_id IS NOT NULL LIMIT 1;

    PERFORM public.mobility_settle_trip(
        v_trip_id,
        v_auth_id,
        '99999999-9999-9999-9999-999999999999'::uuid,
        '77777777-7777-7777-7777-777777777779'::uuid
    );
END \$\$;
" 2>&1 || true)

if [[ "$tamper_fin_err" != *"IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD"* && "$tamper_fin_err" != *"23505"* ]]; then
  echo "FAIL: Expected 23505 IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD. Got: $tamper_fin_err"
  exit 1
fi
echo ">>> PASSED: Replay returned cached settlement and tampered payload was rejected (23505)."

echo "=== 11. Test: Conflict on Already Settled Trip (New Key) ==="
psql "${psql_args[@]}" <<'SQL'
DO $$
DECLARE
    v_trip_id UUID;
    v_auth_id UUID;
    v_quote_id UUID;
    v_res JSONB;
BEGIN
    SET ROLE authenticated;
    SET request.jwt.claims = '{"sub":"22222222-2222-2222-2222-222222222222"}';

    SELECT trip_id, payment_authorization_id, quote_id
    INTO v_trip_id, v_auth_id, v_quote_id
    FROM public.trips WHERE settlement_id IS NOT NULL LIMIT 1;

    v_res := public.mobility_settle_trip(
        v_trip_id,
        v_auth_id,
        v_quote_id,
        '77777777-7777-7777-7777-777777777799'::uuid
    );

    IF (v_res->>'conflict')::boolean IS NOT TRUE OR v_res->>'error_code' <> 'ALREADY_SETTLED' THEN
        RAISE EXCEPTION 'Expected conflict ALREADY_SETTLED, got %', v_res;
    END IF;
END $$;
SQL
echo ">>> PASSED: Settle attempt on already settled trip cleanly returned ALREADY_SETTLED conflict."

echo "=== 12. Test: 100-Way Concurrent Settlement Race (Single Winner Guarantee) ==="
# 1. Rider creates quote, auth, ride
psql "${psql_args[@]}" <<'SQL'
DO $$
DECLARE
    v_quote_res JSONB;
    v_quote_id UUID;
    v_auth_res JSONB;
BEGIN
    SET ROLE authenticated;
    SET request.jwt.claims = '{"sub":"11111111-1111-1111-1111-111111111111"}';

    v_quote_res := public.mobility_generate_quote('CR_SJO', 'cat_sjo_standard', 3000, 400, 1, 1);
    v_quote_id := (v_quote_res->'quote'->>'quote_id')::uuid;

    v_auth_res := public.mobility_authorize_payment(
        '11111111-1111-1111-1111-111111111111'::uuid,
        'CARD_TOKEN',
        (v_quote_res->'quote'->>'total_fare_minor')::BIGINT,
        'CRC',
        'auth_ref_race_test'
    );

    PERFORM public.mobility_request_ride(
        'CR_SJO', 'cat_sjo_standard', 'AUTO_DISPATCH',
        9.9350, -84.0750, 5.0, 'Start',
        9.9281, -84.0907, 5.0, 'End',
        '[]'::jsonb,
        (v_quote_res->'quote'->>'total_fare_minor')::BIGINT,
        NULL,
        '66666666-6666-6666-6666-666666666601'::uuid,
        '66666666-6666-6666-6666-666666666602'::uuid
    );
END $$;
RESET ROLE;

-- 2. Server emits dispatch offer
INSERT INTO public.dispatch_offers (
    dispatch_offer_id, ride_request_id, driver_id, vehicle_id, state, expires_at
)
SELECT
    '66666666-6666-6666-6666-666666666600'::uuid,
    ride_request_id,
    '22222222-2222-2222-2222-222222222222'::uuid,
    '33333333-3333-3333-3333-333333333333'::uuid,
    'PENDING', clock_timestamp() + INTERVAL '30 seconds'
FROM public.ride_requests
WHERE correlation_id = '66666666-6666-6666-6666-666666666602'::uuid;
SQL

# 3. Driver accepts dispatch
psql "${psql_args[@]}" <<'SQL'
DO $$
DECLARE
    v_req_id UUID;
    v_claim_res JSONB;
BEGIN
    SELECT ride_request_id INTO v_req_id FROM public.ride_requests WHERE correlation_id = '66666666-6666-6666-6666-666666666602'::uuid;

    SET ROLE authenticated;
    SET request.jwt.claims = '{"sub":"22222222-2222-2222-2222-222222222222"}';

    v_claim_res := public.mobility_accept_dispatch(
        v_req_id,
        '66666666-6666-6666-6666-666666666600'::uuid,
        '33333333-3333-3333-3333-333333333333'::uuid,
        1,
        '66666666-6666-6666-6666-666666666603'::uuid
    );
END $$;
RESET ROLE;

UPDATE public.trips
SET verification_pin_hash = encode(extensions.digest('1234', 'sha256'), 'hex')
WHERE ride_request_id = (SELECT ride_request_id FROM public.ride_requests WHERE correlation_id = '66666666-6666-6666-6666-666666666602'::uuid);
SQL

# 4. Driver advances trip to ARRIVED_DESTINATION
psql "${psql_args[@]}" <<'SQL'
DO $$
DECLARE
    v_trip_id UUID;
    v_auth_id UUID;
    v_quote_id UUID;
    v_trans_res JSONB;
BEGIN
    SELECT trip_id INTO v_trip_id FROM public.trips WHERE ride_request_id = (SELECT ride_request_id FROM public.ride_requests WHERE correlation_id = '66666666-6666-6666-6666-666666666602'::uuid);
    SELECT payment_authorization_id INTO v_auth_id FROM public.payment_authorizations WHERE provider_auth_ref = 'auth_ref_race_test' LIMIT 1;
    SELECT quote_id INTO v_quote_id FROM public.ride_quotes ORDER BY created_at DESC LIMIT 1;

    SET ROLE authenticated;
    SET request.jwt.claims = '{"sub":"22222222-2222-2222-2222-222222222222"}';

    v_trans_res := public.mobility_transition_trip(v_trip_id, 'DRIVER_EN_ROUTE', 1, NULL, '66666666-6666-6666-6666-666666666604'::uuid);
    IF (v_trans_res->>'success')::boolean IS NOT TRUE THEN RAISE EXCEPTION 'DRIVER_EN_ROUTE failed: %', v_trans_res; END IF;

    v_trans_res := public.mobility_transition_trip(v_trip_id, 'DRIVER_ARRIVED', 2, NULL, '66666666-6666-6666-6666-666666666605'::uuid);
    IF (v_trans_res->>'success')::boolean IS NOT TRUE THEN RAISE EXCEPTION 'DRIVER_ARRIVED failed: %', v_trans_res; END IF;

    v_trans_res := public.mobility_transition_trip(v_trip_id, 'RIDER_ONBOARD', 3, '1234', '66666666-6666-6666-6666-666666666606'::uuid);
    IF (v_trans_res->>'success')::boolean IS NOT TRUE THEN RAISE EXCEPTION 'RIDER_ONBOARD failed: %', v_trans_res; END IF;

    v_trans_res := public.mobility_transition_trip(v_trip_id, 'IN_PROGRESS', 4, NULL, '66666666-6666-6666-6666-666666666607'::uuid);
    IF (v_trans_res->>'success')::boolean IS NOT TRUE THEN RAISE EXCEPTION 'IN_PROGRESS failed: %', v_trans_res; END IF;

    v_trans_res := public.mobility_transition_trip(v_trip_id, 'ARRIVED_DESTINATION', 5, NULL, '66666666-6666-6666-6666-666666666608'::uuid);
    IF (v_trans_res->>'success')::boolean IS NOT TRUE THEN RAISE EXCEPTION 'ARRIVED_DESTINATION failed: %', v_trans_res; END IF;

END $$;
SQL

race_trip_id=$(psql "${psql_args[@]}" -t -A -c "SELECT trip_id FROM public.trips WHERE state = 'ARRIVED_DESTINATION' ORDER BY created_at DESC LIMIT 1;")
race_auth_id=$(psql "${psql_args[@]}" -t -A -c "SELECT payment_authorization_id FROM public.payment_authorizations WHERE provider_auth_ref = 'auth_ref_race_test' LIMIT 1;")
race_quote_id=$(psql "${psql_args[@]}" -t -A -c "SELECT quote_id FROM public.ride_quotes ORDER BY created_at DESC LIMIT 1;")

echo "Executing 100 concurrent settlements on trip $race_trip_id..."
results_dir="$runtime_dir/race_results"
mkdir -p "$results_dir"

for i in $(seq 1 100); do
  (
    key=$(printf "88888888-8888-8888-8888-%012d" "$i")
    psql "${psql_args[@]}" -t -A -c "
      SET ROLE authenticated;
      SET request.jwt.claims = '{\"sub\":\"22222222-2222-2222-2222-222222222222\"}';
      SELECT public.mobility_settle_trip(
        '$race_trip_id'::uuid,
        '$race_auth_id'::uuid,
        '$race_quote_id'::uuid,
        '$key'::uuid
      );
    " > "$results_dir/res_$i.json" 2>&1 || echo "ERROR" > "$results_dir/res_$i.json"
  ) &
done

wait

# Tally results
winners=0
conflicts=0
errors=0

for i in $(seq 1 100); do
  res=$(cat "$results_dir/res_$i.json")
  if [[ "$res" == *"\"success\": true"* ]]; then
    winners=$((winners + 1))
  elif [[ "$res" == *"ALREADY_SETTLED"* ]]; then
    conflicts=$((conflicts + 1))
  else
    errors=$((errors + 1))
    echo "Unexpected worker $i result: $res"
  fi
done

echo "Concurrency results: $winners winners, $conflicts conflicts, $errors errors"

if [[ "$winners" -ne 1 || "$conflicts" -ne 99 || "$errors" -ne 0 ]]; then
  echo "FAIL: Expected exactly 1 winner, 99 conflicts, 0 errors"
  exit 1
fi

# Verify in DB: exactly 1 settlement row, 1 ledger transaction row, sum(amount_minor) == 0
settle_count=$(psql "${psql_args[@]}" -t -A -c "SELECT count(*) FROM public.trip_settlements WHERE trip_id = '$race_trip_id';")
if [[ "$settle_count" -ne 1 ]]; then
  echo "FAIL: Expected exactly 1 trip settlement row, found $settle_count"
  exit 1
fi

ledger_zero_sum=$(psql "${psql_args[@]}" -t -A -c "
SELECT sum(amount_minor) FROM public.ledger_entries e
JOIN public.ledger_transactions t ON e.transaction_id = t.transaction_id
WHERE t.reference_id = '$race_trip_id';
")

if [[ "$ledger_zero_sum" -ne 0 ]]; then
  echo "FAIL: Expected ledger entries sum 0, found $ledger_zero_sum"
  exit 1
fi

echo ">>> PASSED: 100-way concurrent settlement race proved exactly 1 winner and perfect zero-sum ledger balance."
echo "=== ALL MOBILITY FINANCIAL & BALANCED LEDGER TESTS PASSED (WAVES 11–14) ==="
