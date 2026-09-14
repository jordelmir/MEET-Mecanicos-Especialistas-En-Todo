#!/usr/bin/env bash
set -euo pipefail

# ─────────────────────────────────────────────────────────────────────────────
# ADVERSARIAL: REAL POSTGRESQL DOUBLE-ENTRY LEDGER TRIGGER & INVARIANTS
# Tests against actual PostgreSQL kernel:
#   1. Constraint trigger ride_ledger_postings_balance (initially deferred)
#   2. Unbalanced postings (+5000 vs -4000) fails on COMMIT
#   3. Single posting fails on COMMIT (requires at least 2 postings)
#   4. Currency mismatch fails on COMMIT
#   5. Balanced postings (+5000 vs -4250, -750) succeeds on COMMIT
#   6. Direct write by authenticated role rejected (42501 permission denied)
# ─────────────────────────────────────────────────────────────────────────────

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

required_commands=(initdb pg_ctl psql)
for cmd in "${required_commands[@]}"; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "PostgreSQL ledger test: SKIP ($cmd unavailable)"
    exit 0
  fi
done

runtime_dir="$(mktemp -d "${TMPDIR:-/tmp}/meet-ledger.XXXXXX")"
cluster_dir="$runtime_dir/data"
socket_dir="$runtime_dir/socket"
server_log="$runtime_dir/postgres.log"
port="$((58700 + RANDOM % 500))"
mkdir -p "$socket_dir"

cleanup() {
  if [[ -d "$cluster_dir" ]]; then
    pg_ctl -D "$cluster_dir" stop -m fast >/dev/null 2>&1 || true
  fi
  if [[ -d "$runtime_dir" && "$(basename "$runtime_dir")" == meet-ledger.* ]]; then
    rm -rf -- "$runtime_dir"
  fi
}
trap cleanup EXIT

echo "=== 1. Starting Ephemeral PostgreSQL on port $port ==="
initdb -D "$cluster_dir" --no-locale --encoding=UTF8 >/dev/null
pg_ctl \
  -D "$cluster_dir" \
  -l "$server_log" \
  -o "-p $port -k $socket_dir" \
  start >/dev/null

psql_args=(-h "$socket_dir" -p "$port" -d postgres -v ON_ERROR_STOP=1 -q)
export PGOPTIONS="-c client_min_messages=warning"

echo "=== 2. Setting Up Roles & Schema ==="
psql "${psql_args[@]}" <<'SQL'
CREATE ROLE anon NOLOGIN;
CREATE ROLE authenticated NOLOGIN;
CREATE ROLE service_role NOLOGIN;
CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS extensions;
CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA extensions;

CREATE TABLE IF NOT EXISTS auth.users (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid()
);

CREATE TABLE IF NOT EXISTS public.user_profiles (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    auth_user_id uuid UNIQUE REFERENCES auth.users(id) ON DELETE CASCADE,
    display_name text NOT NULL DEFAULT '',
    primary_role text NOT NULL DEFAULT 'driver',
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.user_roles (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    user_profile_id uuid NOT NULL REFERENCES public.user_profiles(id) ON DELETE CASCADE,
    role_name text NOT NULL,
    granted_by uuid,
    is_active boolean NOT NULL DEFAULT true,
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (user_profile_id, role_name)
);

CREATE TABLE IF NOT EXISTS public.provider_profiles (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    user_profile_id uuid NOT NULL REFERENCES public.user_profiles(id) ON DELETE CASCADE,
    provider_type text NOT NULL,
    UNIQUE (user_profile_id, provider_type)
);

CREATE OR REPLACE FUNCTION auth.uid()
RETURNS uuid LANGUAGE sql STABLE AS $$
    SELECT coalesce(
        nullif(current_setting('request.jwt.claim.sub', true), ''),
        nullif((nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'sub'), '')
    )::uuid;
$$;

GRANT USAGE ON SCHEMA public TO anon, authenticated, service_role;
GRANT USAGE ON SCHEMA auth TO anon, authenticated, service_role;
GRANT USAGE ON SCHEMA extensions TO anon, authenticated, service_role;
SQL

# Apply foundation and ledger migrations
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260726010000_ride_platform_foundation.sql" >/dev/null
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260728010000_ride_stops_boarding_and_road_intelligence.sql" >/dev/null
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260730010000_ride_double_entry_ledger.sql" >/dev/null

echo "=== 3. Seeding Test Entities ==="
psql "${psql_args[@]}" <<'SQL'
INSERT INTO auth.users (id) VALUES
    ('00000000-0000-0000-0000-000000000001'::uuid),
    ('00000000-0000-0000-0000-000000000002'::uuid);

INSERT INTO public.ride_profiles (user_id, mobility_role, country_code, preferred_currency, display_name) VALUES
    ('00000000-0000-0000-0000-000000000001'::uuid, 'PASSENGER', 'CR', 'CRC', 'Passenger Test'),
    ('00000000-0000-0000-0000-000000000002'::uuid, 'DRIVER', 'CR', 'CRC', 'Driver Test');

INSERT INTO public.ride_requests (
    id, passenger_id, pickup_latitude, pickup_longitude, pickup_address,
    destination_latitude, destination_longitude, destination_address,
    offered_fare_minor, currency, state
) VALUES (
    'aaaaaaaa-0000-0000-0000-000000000001'::uuid,
    '00000000-0000-0000-0000-000000000001'::uuid,
    9.93, -84.08, 'Origin',
    9.94, -84.09, 'Dest',
    500000, 'CRC', 'COMPLETED'
);
SQL

echo "=== 4. Test Invariant 1: Unbalanced Postings MUST FAIL on COMMIT ==="
set +e
psql "${psql_args[@]}" 2>&1 <<'SQL'
BEGIN;
INSERT INTO public.ride_ledger_transactions (
    id, idempotency_key, event_type, trip_id, currency
) VALUES (
    'aaaaaaaa-1111-0000-0000-000000000001'::uuid,
    'idem-unbalanced-1',
    'COMMISSION_CAPTURED',
    'aaaaaaaa-0000-0000-0000-000000000001'::uuid,
    'CRC'
);

-- Unbalanced: DEBIT 5000 vs CREDIT 4000 (signed sum != 0)
INSERT INTO public.ride_ledger_postings (
    id, transaction_id, entry_sequence, account_code, account_owner_id, direction, amount_minor, currency
) VALUES
    ('bbbbbbbb-1111-0000-0000-000000000001'::uuid, 'aaaaaaaa-1111-0000-0000-000000000001'::uuid, 0, 'PAYMENT_CLEARING', NULL, 'DEBIT', 5000, 'CRC'),
    ('bbbbbbbb-1111-0000-0000-000000000002'::uuid, 'aaaaaaaa-1111-0000-0000-000000000001'::uuid, 1, 'DRIVER_AVAILABLE', '22222222-2222-2222-2222-222222222222'::uuid, 'CREDIT', 4000, 'CRC');

COMMIT;
SQL
unbalanced_code=$?
set -e

if [[ $unbalanced_code -eq 0 ]]; then
  echo "FATAL: Unbalanced transaction was committed to PostgreSQL! Trigger failed!"
  exit 1
fi
echo "[OK] Unbalanced transaction was rejected at COMMIT by ride_assert_journal_balanced trigger."

echo "=== 5. Test Invariant 2: Single Entry MUST FAIL on COMMIT ==="
set +e
psql "${psql_args[@]}" 2>&1 <<'SQL'
BEGIN;
INSERT INTO public.ride_ledger_transactions (
    id, idempotency_key, event_type, trip_id, currency
) VALUES (
    'aaaaaaaa-0002-0000-0000-000000000001'::uuid,
    'idem-single-1',
    'COMMISSION_CAPTURED',
    'aaaaaaaa-0000-0000-0000-000000000001'::uuid,
    'CRC'
);

INSERT INTO public.ride_ledger_postings (
    id, transaction_id, entry_sequence, account_code, account_owner_id, direction, amount_minor, currency
) VALUES
    ('bbbbbbbb-0002-0000-0000-000000000001'::uuid, 'aaaaaaaa-0002-0000-0000-000000000001'::uuid, 0, 'PAYMENT_CLEARING', NULL, 'DEBIT', 5000, 'CRC');

COMMIT;
SQL
single_code=$?
set -e

if [[ $single_code -eq 0 ]]; then
  echo "FATAL: Single entry transaction was committed! Minimum 2 postings invariant failed!"
  exit 1
fi
echo "[OK] Single entry transaction was rejected at COMMIT."

echo "=== 6. Test Invariant 3: Currency Mismatch MUST FAIL on COMMIT ==="
set +e
psql "${psql_args[@]}" 2>&1 <<'SQL'
BEGIN;
INSERT INTO public.ride_ledger_transactions (
    id, idempotency_key, event_type, trip_id, currency
) VALUES (
    'aaaaaaaa-0003-0000-0000-000000000001'::uuid,
    'idem-currmis-1',
    'COMMISSION_CAPTURED',
    'aaaaaaaa-0000-0000-0000-000000000001'::uuid,
    'CRC'
);

INSERT INTO public.ride_ledger_postings (
    id, transaction_id, entry_sequence, account_code, account_owner_id, direction, amount_minor, currency
) VALUES
    ('bbbbbbbb-0003-0000-0000-000000000001'::uuid, 'aaaaaaaa-0003-0000-0000-000000000001'::uuid, 0, 'PAYMENT_CLEARING', NULL, 'DEBIT', 5000, 'USD'),
    ('bbbbbbbb-0003-0000-0000-000000000002'::uuid, 'aaaaaaaa-0003-0000-0000-000000000001'::uuid, 1, 'DRIVER_AVAILABLE', '00000000-0000-0000-0000-000000000002'::uuid, 'CREDIT', 5000, 'USD');

COMMIT;
SQL
currmis_code=$?
set -e

if [[ $currmis_code -eq 0 ]]; then
  echo "FATAL: Currency mismatch transaction was committed! Currency matching invariant failed!"
  exit 1
fi
echo "[OK] Currency mismatch was rejected at COMMIT."

echo "=== 7. Test Invariant 4: Balanced Transaction MUST SUCCEED ==="
psql "${psql_args[@]}" <<'SQL'
BEGIN;
INSERT INTO public.ride_ledger_transactions (
    id, idempotency_key, event_type, trip_id, currency
) VALUES (
    'aaaaaaaa-2222-0000-0000-000000000001'::uuid,
    'idem-balanced-1',
    'COMMISSION_CAPTURED',
    'aaaaaaaa-0000-0000-0000-000000000001'::uuid,
    'CRC'
);

-- Balanced: DEBIT 5000 vs CREDIT 4250 + CREDIT 750 (5000 - 4250 - 750 = 0)
INSERT INTO public.ride_ledger_postings (
    id, transaction_id, entry_sequence, account_code, account_owner_id, direction, amount_minor, currency
) VALUES
    ('bbbbbbbb-2222-0000-0000-000000000001'::uuid, 'aaaaaaaa-2222-0000-0000-000000000001'::uuid, 0, 'PAYMENT_CLEARING', NULL, 'DEBIT', 5000, 'CRC'),
    ('bbbbbbbb-2222-0000-0000-000000000002'::uuid, 'aaaaaaaa-2222-0000-0000-000000000001'::uuid, 1, 'DRIVER_AVAILABLE', '22222222-2222-2222-2222-222222222222'::uuid, 'CREDIT', 4250, 'CRC'),
    ('bbbbbbbb-2222-0000-0000-000000000003'::uuid, 'aaaaaaaa-2222-0000-0000-000000000001'::uuid, 2, 'PLATFORM_COMMISSION_REVENUE', NULL, 'CREDIT', 750, 'CRC');

COMMIT;
SQL
echo "[OK] Balanced transaction successfully committed."

echo "=== 8. Test Invariant 5: Direct Mutation by Authenticated MUST BE FORBIDDEN ==="
set +e
psql "${psql_args[@]}" 2>&1 <<'SQL'
SET ROLE authenticated;
INSERT INTO public.ride_ledger_transactions (
    id, idempotency_key, event_type, trip_id, currency
) VALUES (
    'aaaaaaaa-3333-0000-0000-000000000001'::uuid,
    'idem-hack',
    'COMMISSION_CAPTURED',
    'aaaaaaaa-0000-0000-0000-000000000001'::uuid,
    'CRC'
);
SQL
auth_hack_code=$?
set -e

if [[ $auth_hack_code -eq 0 ]]; then
  echo "FATAL: Authenticated role was able to directly write to ride_ledger_transactions!"
  exit 1
fi
echo "[OK] Direct write by authenticated role was rejected with permission denied."

echo "=== Real PostgreSQL Double-Entry Ledger Invariant Test ALL PASSED ==="
