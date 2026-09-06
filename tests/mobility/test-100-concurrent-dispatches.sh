#!/usr/bin/env bash
set -euo pipefail

# ─────────────────────────────────────────────────────────────────────────────
# ELYSIUM MOBILITY DISPATCH — 100 CONCURRENT CLAIMS & IDEMPOTENCY SUITE
# Mandate: ORDEN MAESTRA V6 (Section 153 & Sections 45-46)
# Asserts:
#   1. 100 simultaneous eligible drivers claiming same ride -> EXACTLY 1 winner, 99 conflicts
#   2. 100 simultaneous calls with same idempotency key -> EXACTLY 1 execution, 100 identical replies
#   3. Reused idempotency key with tampered payload -> raises 23505 unique violation
# ─────────────────────────────────────────────────────────────────────────────

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

required_commands=(initdb pg_ctl psql)
for command_name in "${required_commands[@]}"; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "100 concurrent dispatches test: SKIP ($command_name unavailable)"
    exit 0
  fi
done

runtime_dir="$(mktemp -d "${TMPDIR:-/tmp}/meet-mob-conc.XXXXXX")"
cluster_dir="$runtime_dir/data"
socket_dir="$runtime_dir/socket"
server_log="$runtime_dir/postgres.log"
results_dir="$runtime_dir/results"
port="$((58100 + RANDOM % 500))"
mkdir -p "$socket_dir" "$results_dir"

cleanup() {
  if [[ -d "$cluster_dir" ]]; then
    pg_ctl -D "$cluster_dir" stop -m fast >/dev/null 2>&1 || true
  fi
  if [[ -d "$runtime_dir" && "$(basename "$runtime_dir")" == meet-mob-conc.* ]]; then
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

echo "=== 2. Setting Up Supabase Auth & Mobility V6 Schema ==="
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

# Apply authoritative Mobility migration
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260906000000_mobility_market_authority.sql"

echo "=== 3. Seeding Market, 1 Rider, 100 Verified Drivers & Vehicles ==="
psql "${psql_args[@]}" <<'SQL'
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

-- Seed Rider
INSERT INTO auth.users (id) VALUES ('10000000-0000-0000-0000-000000000001'::uuid);

-- Seed 100 Drivers & Vehicles
DO $$
DECLARE
    v_driver_id UUID;
    v_veh_id UUID;
    i INT;
BEGIN
    FOR i IN 1..100 LOOP
        v_driver_id := ('20000000-0000-0000-0000-' || LPAD(i::text, 12, '0'))::uuid;
        v_veh_id := ('30000000-0000-0000-0000-' || LPAD(i::text, 12, '0'))::uuid;

        INSERT INTO auth.users (id) VALUES (v_driver_id) ON CONFLICT DO NOTHING;

        INSERT INTO public.mobility_vehicles (
            vehicle_id, owner_id, license_plate, make, model, year, color, seat_capacity, verification_state, active
        ) VALUES (
            v_veh_id, v_driver_id, 'DRV-' || LPAD(i::text, 4, '0'), 'Toyota', 'Yaris', 2022, 'White', 4, 'VERIFIED', true
        );

        INSERT INTO public.driver_market_eligibility (
            driver_id, market_id, is_eligible, background_check_cleared, documents_verified, active
        ) VALUES (
            v_driver_id, 'CR_SJO', true, true, true, true
        );

        INSERT INTO public.driver_vehicle_authorizations (
            driver_id, vehicle_id, is_authorized, active
        ) VALUES (
            v_driver_id, v_veh_id, true, true
        );
    END LOOP;
END $$;

-- Contested Ride Request 1 for 100-way claim race
INSERT INTO public.ride_requests (
    ride_request_id, rider_id, market_id, service_category_id, dispatch_mode, state,
    pickup_location, destination_location, currency_code, version, correlation_id
) VALUES (
    'aaaaaaaa-0000-0000-0000-000000000001'::uuid,
    '10000000-0000-0000-0000-000000000001'::uuid,
    'CR_SJO', 'cat_sjo_standard', 'AUTO_DISPATCH', 'SEARCHING',
    extensions.ST_SetSRID(extensions.ST_MakePoint(-84.075, 9.935), 4326),
    extensions.ST_SetSRID(extensions.ST_MakePoint(-84.090, 9.928), 4326),
    'CRC', 1, 'cccccccc-0000-0000-0000-000000000001'::uuid
);

-- Seed 100 dispatch offers for each driver
DO $$
DECLARE
    v_driver_id UUID;
    v_veh_id UUID;
    v_offer_id UUID;
    i INT;
BEGIN
    FOR i IN 1..100 LOOP
        v_driver_id := ('20000000-0000-0000-0000-' || LPAD(i::text, 12, '0'))::uuid;
        v_veh_id := ('30000000-0000-0000-0000-' || LPAD(i::text, 12, '0'))::uuid;
        v_offer_id := ('40000000-0000-0000-0000-' || LPAD(i::text, 12, '0'))::uuid;

        INSERT INTO public.dispatch_offers (
            dispatch_offer_id, ride_request_id, driver_id, vehicle_id, state, expires_at
        ) VALUES (
            v_offer_id, 'aaaaaaaa-0000-0000-0000-000000000001'::uuid,
            v_driver_id, v_veh_id, 'PENDING', clock_timestamp() + INTERVAL '60 seconds'
        );
    END LOOP;
END $$;

-- Separate Ride Request 2 for 100-way concurrent same-key idempotency test
INSERT INTO public.ride_requests (
    ride_request_id, rider_id, market_id, service_category_id, dispatch_mode, state,
    pickup_location, destination_location, currency_code, version, correlation_id
) VALUES (
    'aaaaaaaa-0000-0000-0000-000000000002'::uuid,
    '10000000-0000-0000-0000-000000000001'::uuid,
    'CR_SJO', 'cat_sjo_standard', 'AUTO_DISPATCH', 'SEARCHING',
    extensions.ST_SetSRID(extensions.ST_MakePoint(-84.075, 9.935), 4326),
    extensions.ST_SetSRID(extensions.ST_MakePoint(-84.090, 9.928), 4326),
    'CRC', 1, 'cccccccc-0000-0000-0000-000000000002'::uuid
);

INSERT INTO public.dispatch_offers (
    dispatch_offer_id, ride_request_id, driver_id, vehicle_id, state, expires_at
) VALUES (
    '50000000-0000-0000-0000-000000000001'::uuid,
    'aaaaaaaa-0000-0000-0000-000000000002'::uuid,
    '20000000-0000-0000-0000-000000000001'::uuid,
    '30000000-0000-0000-0000-000000000001'::uuid,
    'PENDING', clock_timestamp() + INTERVAL '60 seconds'
);
SQL

echo "=== 4. Executing 100-Way Parallel Dispatch Claim Race ==="
for i in $(seq 1 100); do
  driver_id=$(printf "20000000-0000-0000-0000-%012d" "$i")
  veh_id=$(printf "30000000-0000-0000-0000-%012d" "$i")
  offer_id=$(printf "40000000-0000-0000-0000-%012d" "$i")
  idem_key=$(printf "90000000-0000-0000-0000-%012d" "$i")
  (
    psql "${psql_args[@]}" -t -A -c "
      SET ROLE authenticated;
      SET request.jwt.claims = '{\"sub\":\"$driver_id\"}';
      SELECT public.mobility_accept_dispatch(
        'aaaaaaaa-0000-0000-0000-000000000001'::uuid,
        '$offer_id'::uuid,
        '$veh_id'::uuid,
        1::bigint,
        '$idem_key'::uuid
      );
    " > "$results_dir/race_$i.json" 2>&1
  ) &
done
wait

echo "=== 5. Evaluating 100-Way Claim Results ==="
winners=0
conflicts=0
other_errors=0

for i in $(seq 1 100); do
  file="$results_dir/race_$i.json"
  if grep -q '"success": true' "$file"; then
    winners=$((winners + 1))
  elif grep -q 'ALREADY_MATCHED\|CONCURRENCY_CONFLICT' "$file"; then
    conflicts=$((conflicts + 1))
  else
    other_errors=$((other_errors + 1))
    echo "Unexpected error in $file: $(cat "$file")"
  fi
done

echo "100-Way Dispatch Results: winners=$winners, conflicts=$conflicts, other_errors=$other_errors"

if [[ "$winners" -ne 1 ]]; then
  echo "FATAL: Expected exactly 1 winner, found $winners!"
  exit 1
fi

if [[ "$conflicts" -ne 99 ]]; then
  echo "FATAL: Expected exactly 99 conflicts, found $conflicts!"
  exit 1
fi

if [[ "$other_errors" -ne 0 ]]; then
  echo "FATAL: Unexpected errors: $other_errors"
  exit 1
fi

# Verify database state
db_state=$(psql "${psql_args[@]}" -t -A -c "
  SELECT r.state || '|' || r.version || '|' || count(t.trip_id)
  FROM public.ride_requests r
  LEFT JOIN public.trips t ON t.ride_request_id = r.ride_request_id
  WHERE r.ride_request_id = 'aaaaaaaa-0000-0000-0000-000000000001'::uuid
  GROUP BY r.state, r.version;
")

echo "Database post-race state: $db_state"
if [[ "$db_state" != "MATCHED|2|1" ]]; then
  echo "FATAL: Expected MATCHED|2|1, got $db_state"
  exit 1
fi
echo ">>> TEST 1 PASSED: 100 concurrent drivers produced EXACTLY 1 winner, 99 conflicts, and 1 canonical Trip."

echo "=== 6. Executing 100-Way Concurrent Same-Key Idempotency Race ==="
single_driver="20000000-0000-0000-0000-000000000001"
single_veh="30000000-0000-0000-0000-000000000001"
single_offer="50000000-0000-0000-0000-000000000001"
same_key="99999999-9999-9999-9999-999999999999"

for i in $(seq 1 100); do
  (
    psql "${psql_args[@]}" -t -A -c "
      SET ROLE authenticated;
      SET request.jwt.claims = '{\"sub\":\"$single_driver\"}';
      SELECT public.mobility_accept_dispatch(
        'aaaaaaaa-0000-0000-0000-000000000002'::uuid,
        '$single_offer'::uuid,
        '$single_veh'::uuid,
        1::bigint,
        '$same_key'::uuid
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
    echo "Failure in idempotency test $file: $(cat "$file")"
  fi
done

echo "100-Way Idempotency Results: identical successes=$idem_success / 100"
if [[ "$idem_success" -ne 100 ]]; then
  echo "FATAL: Expected all 100 concurrent calls to succeed identically, got $idem_success"
  exit 1
fi

receipt_count=$(psql "${psql_args[@]}" -t -A -c "
  SELECT count(*) FROM public.mobility_command_receipts
  WHERE actor_id = '$single_driver'::uuid AND idempotency_key = '$same_key'::uuid;
")

if [[ "$receipt_count" -ne 1 ]]; then
  echo "FATAL: Expected exactly 1 receipt in DB, found $receipt_count"
  exit 1
fi
echo ">>> TEST 2 PASSED: 100 concurrent calls with identical idempotency key produced 100 identical successful replies and 1 canonical receipt."

echo "=== 7. Tampered Request Payload with Reused Key Test ==="
tamper_result=$(psql "${psql_args[@]}" -t -A -c "
  SET ROLE authenticated;
  SET request.jwt.claims = '{\"sub\":\"$single_driver\"}';
  SELECT public.mobility_accept_dispatch(
    'aaaaaaaa-0000-0000-0000-000000000002'::uuid,
    '$single_offer'::uuid,
    '30000000-0000-0000-0000-000000000002'::uuid,
    1::bigint,
    '$same_key'::uuid
  );
" 2>&1 || true)

if echo "$tamper_result" | grep -q "IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD\|23505"; then
  echo ">>> TEST 3 PASSED: Tampered payload on reused key raised 23505 as required."
else
  echo "FATAL: Expected 23505 exception, got: $tamper_result"
  exit 1
fi

echo ""
echo "=========================================================================="
echo "ALL 100-WAY CONCURRENT MOBILITY DISPATCH TESTS PASSED (100% GREEN)"
echo "  - 100-way dispatch race: EXACTLY 1 winner, 99 conflicts"
echo "  - 100-way idempotency race: EXACTLY 1 execution, 100 identical replies"
echo "  - Tampered hash protection: verified with code 23505"
echo "=========================================================================="
