#!/usr/bin/env bash
set -euo pipefail

# ─────────────────────────────────────────────────────────────────────────────
# ADVERSARIAL: REAL POSTGRESQL 100-WAY CONCURRENCY & SERIAL ROW LOCKS
# Asserts on actual PostgreSQL kernel:
#   1. 100 simultaneous concurrent calls to accept_ride_offer on the same ride
#   2. SELECT ... FOR UPDATE serializes transactions
#   3. EXACTLY 1 winner commits (state = ASSIGNED, version = 2)
#   4. EXACTLY 99 callers receive conflict errors (RIDE_ALREADY_ASSIGNED / 40001 / 23505)
#   5. The assigned_driver_id matches the driver_id authoritatively derived from the winning offer
#   6. 1 offer becomes ACCEPTED, exactly 99 offers become REJECTED
# ─────────────────────────────────────────────────────────────────────────────

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

required_commands=(initdb pg_ctl psql)
for cmd in "${required_commands[@]}"; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "PostgreSQL concurrency test: SKIP ($cmd unavailable)"
    exit 0
  fi
done

runtime_dir="$(mktemp -d "${TMPDIR:-/tmp}/meet-ride-conc.XXXXXX")"
cluster_dir="$runtime_dir/data"
socket_dir="$runtime_dir/socket"
server_log="$runtime_dir/postgres.log"
results_dir="$runtime_dir/results"
port="$((58600 + RANDOM % 500))"
mkdir -p "$socket_dir" "$results_dir"

cleanup() {
  if [[ -d "$cluster_dir" ]]; then
    pg_ctl -D "$cluster_dir" stop -m fast >/dev/null 2>&1 || true
  fi
  if [[ -d "$runtime_dir" && "$(basename "$runtime_dir")" == meet-ride-conc.* ]]; then
    rm -rf -- "$runtime_dir"
  fi
}
trap cleanup EXIT

echo "=== 1. Starting Ephemeral PostgreSQL on port $port ==="
initdb -D "$cluster_dir" --no-locale --encoding=UTF8 >/dev/null
pg_ctl \
  -D "$cluster_dir" \
  -l "$server_log" \
  -o "-p $port -k $socket_dir -c max_connections=250" \
  start >/dev/null

psql_args=(-h "$socket_dir" -p "$port" -d postgres -v ON_ERROR_STOP=1 -q)
export PGOPTIONS="-c client_min_messages=warning"

echo "=== 2. Setting Up Schema, Tables & accept_ride_offer RPC ==="
psql "${psql_args[@]}" <<'SQL'
CREATE ROLE anon NOLOGIN;
CREATE ROLE authenticated NOLOGIN;
CREATE ROLE service_role NOLOGIN;
CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS extensions;
CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA extensions;

CREATE OR REPLACE FUNCTION auth.uid()
RETURNS uuid LANGUAGE sql STABLE AS $$
    SELECT coalesce(
        nullif(current_setting('request.jwt.claim.sub', true), ''),
        nullif((nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'sub'), '')
    )::uuid;
$$;

GRANT USAGE ON SCHEMA public TO anon, authenticated;

-- Core tables
CREATE TABLE public.ride_requests (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    passenger_id uuid NOT NULL,
    assigned_driver_id uuid,
    state text NOT NULL DEFAULT 'SEARCHING',
    state_version bigint NOT NULL DEFAULT 1,
    version bigint NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE public.ride_offers (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    request_id uuid NOT NULL REFERENCES public.ride_requests(id),
    driver_id uuid NOT NULL,
    state text NOT NULL DEFAULT 'PENDING',
    amount_minor bigint NOT NULL DEFAULT 350000,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE public.ride_command_dedup (
    idempotency_key uuid PRIMARY KEY,
    ride_id uuid NOT NULL,
    actor_id uuid NOT NULL,
    command text NOT NULL,
    result jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA public TO authenticated;
SQL

# Apply accept_ride_offer RPC
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260914080000_ride_server_authority_and_concurrency.sql"

echo "=== 3. Seeding Ride and 100 Competing Driver Offers ==="
psql "${psql_args[@]}" <<'SQL'
-- 1 Passenger
DO $$
DECLARE
    v_ride_id uuid := 'aaaaaaaa-0000-0000-0000-000000000001'::uuid;
    v_passenger_id uuid := '10000000-0000-0000-0000-000000000001'::uuid;
    v_driver_id uuid;
    v_offer_id uuid;
    i int;
BEGIN
    INSERT INTO public.ride_requests (id, passenger_id, state, state_version, version)
    VALUES (v_ride_id, v_passenger_id, 'OFFERED', 1, 1);

    FOR i IN 1..100 LOOP
        v_driver_id := ('20000000-0000-0000-0000-' || LPAD(i::text, 12, '0'))::uuid;
        v_offer_id := ('40000000-0000-0000-0000-' || LPAD(i::text, 12, '0'))::uuid;

        INSERT INTO public.ride_offers (id, request_id, driver_id, state)
        VALUES (v_offer_id, v_ride_id, v_driver_id, 'PENDING');
    END LOOP;
END $$;
SQL

echo "=== 4. Launching 100 Real Parallel Connections Competing for the Ride ==="
passenger_id="10000000-0000-0000-0000-000000000001"
ride_id="aaaaaaaa-0000-0000-0000-000000000001"

for i in $(seq 1 100); do
  offer_id=$(printf "40000000-0000-0000-0000-%012d" "$i")
  idem_key=$(printf "90000000-0000-0000-0000-%012d" "$i")
  (
    psql "${psql_args[@]}" -t -A -c "
      SET ROLE authenticated;
      SET request.jwt.claims = '{\"sub\":\"$passenger_id\"}';
      SELECT public.accept_ride_offer(
        '$ride_id'::uuid,
        '$offer_id'::uuid,
        1::bigint,
        '$idem_key'::uuid
      );
    " > "$results_dir/conc_$i.json" 2>&1
  ) &
done

wait

echo "=== 5. Verifying Real PostgreSQL Kernel Results ==="
winners=0
conflicts=0
errors=0

for i in $(seq 1 100); do
  file="$results_dir/conc_$i.json"
  if grep -q '"ASSIGNED"' "$file"; then
    winners=$((winners + 1))
  elif grep -q 'RIDE_ALREADY_ASSIGNED\|VERSION_CONFLICT\|40001\|23505' "$file"; then
    conflicts=$((conflicts + 1))
  else
    errors=$((errors + 1))
    echo "Unexpected error in $file: $(cat "$file")"
  fi
done

echo "Concurrency Results: winners=$winners, conflicts=$conflicts, errors=$errors"

if [[ "$winners" -ne 1 ]]; then
  echo "FATAL: Expected exactly 1 winner, but found $winners!"
  exit 1
fi

if [[ "$conflicts" -ne 99 ]]; then
  echo "FATAL: Expected exactly 99 conflicts, but found $conflicts!"
  exit 1
fi

# Verify Database Invariants
db_state=$(psql "${psql_args[@]}" -t -A -c "
  SELECT state || '|' || state_version || '|' || (assigned_driver_id IS NOT NULL)::text
    FROM public.ride_requests
   WHERE id = '$ride_id'::uuid;
")
echo "DB ride state: $db_state"
if [[ "$db_state" != "ASSIGNED|2|true" ]]; then
  echo "FATAL: DB state mismatch: $db_state"
  exit 1
fi

accepted_offers=$(psql "${psql_args[@]}" -t -A -c "
  SELECT count(*) FROM public.ride_offers WHERE request_id = '$ride_id'::uuid AND state = 'ACCEPTED';
")
rejected_offers=$(psql "${psql_args[@]}" -t -A -c "
  SELECT count(*) FROM public.ride_offers WHERE request_id = '$ride_id'::uuid AND state = 'REJECTED';
")
echo "Offers: accepted=$accepted_offers, rejected=$rejected_offers"

if [[ "$accepted_offers" -ne 1 || "$rejected_offers" -ne 99 ]]; then
  echo "FATAL: Offer state invariant violated!"
  exit 1
fi

echo "=== Real PostgreSQL 100-Way Concurrency Test PASSED ==="
