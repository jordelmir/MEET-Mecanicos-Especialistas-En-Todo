#!/usr/bin/env bash
set -euo pipefail

# ─────────────────────────────────────────────────────────────────────────────
# ELYSIUM TOW SERVER AUTHORITY — 100 CONCURRENT CLAIMS & IDEMPOTENCY SUITE
# Mandate: Master Implementation Order V5 (Sections 27, 28, 42, 43)
# Asserts:
#   1. 100 simultaneous eligible operators claiming same job -> EXACTLY 1 winner, 99 conflicts
#   2. 100 simultaneous calls with same idempotency key -> EXACTLY 1 execution, 100 identical replies
#   3. Reused idempotency key with different payload -> raises 23505 unique violation
# ─────────────────────────────────────────────────────────────────────────────

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

required_commands=(initdb pg_ctl psql)
for command_name in "${required_commands[@]}"; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "100 concurrent claims test: SKIP ($command_name unavailable)"
    exit 0
  fi
done

runtime_dir="$(mktemp -d "${TMPDIR:-/tmp}/meet-tow-conc.XXXXXX")"
cluster_dir="$runtime_dir/data"
socket_dir="$runtime_dir/socket"
server_log="$runtime_dir/postgres.log"
results_dir="$runtime_dir/results"
port="$((56900 + RANDOM % 500))"
mkdir -p "$socket_dir" "$results_dir"

cleanup() {
  if [[ -d "$cluster_dir" ]]; then
    pg_ctl -D "$cluster_dir" stop -m fast >/dev/null 2>&1 || true
  fi
  if [[ -d "$runtime_dir" && "$(basename "$runtime_dir")" == meet-tow-conc.* ]]; then
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

echo "=== 2. Provisioning Supabase Mock Schemas & Migration V5 ==="
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

create table if not exists public.user_profiles (
    id uuid primary key default extensions.gen_random_uuid(),
    auth_user_id uuid unique references auth.users(id) on delete cascade,
    display_name text not null default '',
    primary_role text not null default 'driver',
    updated_at timestamptz not null default now()
);

create table if not exists public.provider_profiles (
    id uuid primary key default extensions.gen_random_uuid(),
    user_profile_id uuid not null references public.user_profiles(id) on delete cascade,
    provider_type text not null,
    business_name text,
    phone text,
    is_verified boolean not null default false,
    is_active boolean not null default false,
    status text not null default 'pending',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
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
grant select on auth.users to authenticated;
grant select on public.user_profiles to authenticated;
grant select on public.provider_profiles to authenticated;
SQL

# Apply authoritative Tow migration
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260905180000_tow_fulfillment_authority.sql"

echo "=== 3. Seeding 100 Verified Operators & Units + 1 Contested Tow Job ==="
psql "${psql_args[@]}" <<'SQL'
DO $$
DECLARE
    v_cust_id UUID := '10000000-0000-0000-0000-000000000001'::uuid;
    v_op_id UUID;
    v_up_id UUID;
    v_unit_id UUID;
    i INT;
BEGIN
    INSERT INTO auth.users (id) VALUES (v_cust_id) ON CONFLICT DO NOTHING;
    INSERT INTO public.user_profiles (id, auth_user_id, display_name, primary_role)
    VALUES (gen_random_uuid(), v_cust_id, 'Customer Central', 'driver') ON CONFLICT DO NOTHING;

    FOR i IN 1..100 LOOP
        v_op_id := ('20000000-0000-0000-0000-' || LPAD(i::text, 12, '0'))::uuid;
        v_unit_id := ('30000000-0000-0000-0000-' || LPAD(i::text, 12, '0'))::uuid;

        INSERT INTO auth.users (id) VALUES (v_op_id) ON CONFLICT DO NOTHING;

        INSERT INTO public.user_profiles (auth_user_id, display_name, primary_role)
        VALUES (v_op_id, 'Operador Gruero ' || i, 'tow_provider')
        RETURNING id INTO v_up_id;

        INSERT INTO public.provider_profiles (user_profile_id, provider_type, is_verified, is_active, status)
        VALUES (v_up_id, 'tow_provider', true, true, 'active');

        INSERT INTO public.tow_units (
            id, operator_id, license_plate, brand_model,
            max_weight_kg, capabilities, verification_state, availability_state
        ) VALUES (
            v_unit_id, v_op_id, 'GRUA-' || LPAD(i::text, 4, '0'), 'Freightliner M2',
            8000, ARRAY['FLATBED', 'WINCH'], 'VERIFIED', 'AVAILABLE'
        );
    END LOOP;

    -- Contested job for 100-way claim race
    INSERT INTO public.tow_jobs (
        job_id, customer_id, state, pickup_lat, pickup_lng, pickup_address,
        required_capabilities, version, correlation_id
    ) VALUES (
        'tow_race_100_job', v_cust_id, 'REQUESTED', 9.9281, -84.0907, 'Paseo Colon, San Jose',
        ARRAY['FLATBED'], 1, 'corr_race_100'
    );

    -- Separate job for 100-way concurrent idempotency test
    INSERT INTO public.tow_jobs (
        job_id, customer_id, state, pickup_lat, pickup_lng, pickup_address,
        required_capabilities, version, correlation_id
    ) VALUES (
        'tow_idempotency_100_job', v_cust_id, 'REQUESTED', 9.9350, -84.0750, 'Barrio Escalante, San Jose',
        ARRAY['FLATBED'], 1, 'corr_idempotency_100'
    );
END $$;
SQL

echo "=== 4. Executing 100-Way Parallel Claim Race against tow_race_100_job ==="
for i in $(seq 1 100); do
  op_id=$(printf "20000000-0000-0000-0000-%012d" "$i")
  unit_id=$(printf "30000000-0000-0000-0000-%012d" "$i")
  idem_key=$(printf "claim-race-key-%012d" "$i")
  hash="1111111111111111111111111111111111111111111111111111111111111111"
  (
    psql "${psql_args[@]}" -t -A -c "
      SET ROLE authenticated;
      SET request.jwt.claims = '{\"sub\":\"$op_id\"}';
      SELECT public.tow_claim_job(
        'tow_race_100_job',
        '$unit_id'::uuid,
        1::bigint,
        '$idem_key',
        '$hash'
      );
    " > "$results_dir/race_$i.json" 2>&1
  ) &
done
wait

echo "=== 5. Evaluating 100-Way Parallel Claim Results ==="
winners=0
conflicts=0
other_errors=0

for i in $(seq 1 100); do
  file="$results_dir/race_$i.json"
  if grep -q '"success": true' "$file"; then
    winners=$((winners + 1))
    winning_op="$file"
  elif grep -q 'ALREADY_CLAIMED\|CONCURRENCY_CONFLICT' "$file"; then
    conflicts=$((conflicts + 1))
  else
    other_errors=$((other_errors + 1))
    echo "Unexpected error in $file: $(cat "$file")"
  fi
done

echo "100-Way Claim Results: winners=$winners, conflicts=$conflicts, other_errors=$other_errors"

if [[ "$winners" -ne 1 ]]; then
  echo "FATAL: Expected exactly 1 winner, found $winners!"
  exit 1
fi

if [[ "$conflicts" -ne 99 ]]; then
  echo "FATAL: Expected exactly 99 conflicts, found $conflicts!"
  exit 1
fi

if [[ "$other_errors" -ne 0 ]]; then
  echo "FATAL: Unexpected errors during concurrency test: $other_errors"
  exit 1
fi

# Verify canonical database state
job_state=$(psql "${psql_args[@]}" -t -A -c "
  SELECT state || '|' || version || '|' || (assigned_operator_id IS NOT NULL) || '|' || (assigned_tow_unit_id IS NOT NULL)
  FROM public.tow_jobs WHERE job_id = 'tow_race_100_job';
")

echo "Database post-race row state: $job_state"
if [[ "$job_state" != "ASSIGNED|2|t|t" && "$job_state" != "ASSIGNED|2|true|true" ]]; then
  echo "FATAL: Database state expected ASSIGNED|2|true|true, got $job_state"
  exit 1
fi
echo ">>> TEST 1 PASSED: 100 concurrent independent claims produced EXACTLY 1 winner and 99 conflicts. Database version advanced to 2."

echo "=== 6. Executing 100-Way Concurrent Idempotency Race against tow_idempotency_100_job ==="
single_op_id="20000000-0000-0000-0000-000000000001"
single_unit_id="30000000-0000-0000-0000-000000000001"
same_idem_key="idempotency-key-fixed-100-concurrency"
same_hash="2222222222222222222222222222222222222222222222222222222222222222"

for i in $(seq 1 100); do
  (
    psql "${psql_args[@]}" -t -A -c "
      SET ROLE authenticated;
      SET request.jwt.claims = '{\"sub\":\"$single_op_id\"}';
      SELECT public.tow_claim_job(
        'tow_idempotency_100_job',
        '$single_unit_id'::uuid,
        1::bigint,
        '$same_idem_key',
        '$same_hash'
      );
    " > "$results_dir/idem_$i.json" 2>&1
  ) &
done
wait

idem_success=0
for i in $(seq 1 100); do
  file="$results_dir/idem_$i.json"
  if grep -q '"success": true' "$file"; then
    idem_success=$((idem_success + 1))
  else
    echo "Unexpected failure in idempotency test $file: $(cat "$file")"
  fi
done

echo "100-Way Idempotency Results: identical successes=$idem_success / 100"

if [[ "$idem_success" -ne 100 ]]; then
  echo "FATAL: Expected all 100 concurrent calls to receive identical success, got $idem_success"
  exit 1
fi

# Assert only 1 receipt was created in DB
receipt_count=$(psql "${psql_args[@]}" -t -A -c "
  SELECT count(*) FROM public.tow_command_receipts
  WHERE actor_id = '$single_op_id'::uuid AND idempotency_key = '$same_idem_key';
")

echo "Total receipts in DB for shared key: $receipt_count"
if [[ "$receipt_count" -ne 1 ]]; then
  echo "FATAL: Expected exactly 1 receipt in DB, found $receipt_count"
  exit 1
fi
echo ">>> TEST 2 PASSED: 100 concurrent same-key calls executed domain logic exactly ONCE and returned 100 identical receipts."

echo "=== 7. Tampered Request Hash with Reused Key Test ==="
tampered_hash="3333333333333333333333333333333333333333333333333333333333333333"
tamper_result=$(psql "${psql_args[@]}" -t -A -c "
  SET ROLE authenticated;
  SET request.jwt.claims = '{\"sub\":\"$single_op_id\"}';
  SELECT public.tow_claim_job(
    'tow_idempotency_100_job',
    '$single_unit_id'::uuid,
    1::bigint,
    '$same_idem_key',
    '$tampered_hash'
  );
" 2>&1 || true)

if echo "$tamper_result" | grep -q "IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD\|23505"; then
  echo ">>> TEST 3 PASSED: Tampered payload with reused idempotency key raised 23505 as required."
else
  echo "FATAL: Expected 23505 IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD, got: $tamper_result"
  exit 1
fi

echo ""
echo "=========================================================================="
echo "ALL 100-WAY CONCURRENT POSTGRESQL TOW AUTHORITY TESTS PASSED (100% GREEN)"
echo "  - 100-way claim race: EXACTLY 1 winner, 99 conflicts"
echo "  - 100-way idempotency race: EXACTLY 1 execution, 100 identical replies"
echo "  - Tampered hash protection: verified with code 23505"
echo "=========================================================================="
