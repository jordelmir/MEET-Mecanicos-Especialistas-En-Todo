#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

required_commands=(initdb pg_ctl psql)
for command_name in "${required_commands[@]}"; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "ride command authority PostgreSQL integration: SKIP ($command_name unavailable)"
    exit 0
  fi
done

runtime_dir="$(mktemp -d "${TMPDIR:-/tmp}/meet-ride-pg.XXXXXX")"
cluster_dir="$runtime_dir/data"
socket_dir="$runtime_dir/socket"
server_log="$runtime_dir/postgres.log"
port="$((55400 + RANDOM % 500))"
mkdir -p "$socket_dir"

cleanup() {
  if [[ -d "$cluster_dir" ]]; then
    pg_ctl -D "$cluster_dir" stop -m fast >/dev/null 2>&1 || true
  fi
  if [[ -d "$runtime_dir" && "$(basename "$runtime_dir")" == meet-ride-pg.* ]]; then
    rm -rf -- "$runtime_dir"
  fi
}
trap cleanup EXIT

initdb -D "$cluster_dir" --no-locale --encoding=UTF8 >/dev/null
pg_ctl \
  -D "$cluster_dir" \
  -l "$server_log" \
  -o "-p $port -k $socket_dir" \
  start >/dev/null

psql_args=(-h "$socket_dir" -p "$port" -d postgres -v ON_ERROR_STOP=1 -q)
export PGOPTIONS="-c client_min_messages=warning"

psql "${psql_args[@]}" <<'SQL'
create role anon nologin;
create role authenticated nologin;
create schema auth;
create schema extensions;
create extension pgcrypto with schema extensions;
create table auth.users(
    id uuid primary key default extensions.gen_random_uuid()
);
create or replace function auth.uid()
returns uuid
language sql
stable
as $$
    select nullif(
        current_setting('request.jwt.claim.sub', true),
        ''
    )::uuid
$$;
grant usage on schema public, auth, extensions to anon, authenticated;
grant select on auth.users to authenticated;
SQL

migrations=(
  "$repo_root/supabase/migrations/20260726010000_ride_platform_foundation.sql"
  "$repo_root/supabase/migrations/20260728010000_ride_stops_boarding_and_road_intelligence.sql"
  "$repo_root/supabase/migrations/20260730010000_ride_double_entry_ledger.sql"
  "$repo_root/supabase/migrations/20260730020000_ride_command_authority.sql"
  "$repo_root/supabase/migrations/20260730030000_ride_passenger_driver_commands.sql"
)

for migration in "${migrations[@]}"; do
  psql "${psql_args[@]}" -f "$migration" >/dev/null
done

psql \
  "${psql_args[@]}" \
  -f "$repo_root/tests/ride/ride-command-authority-integration.sql"

psql \
  "${psql_args[@]}" \
  -f "$repo_root/tests/ride/ride-passenger-driver-flow-integration.sql"

psql \
  "${psql_args[@]}" \
  -f "$repo_root/tests/ride/ride-command-authority-concurrency-setup.sql" \
  >/dev/null

seq 1 100 | xargs -P 12 -I {} \
  psql \
    -h "$socket_dir" \
    -p "$port" \
    -d postgres \
    -v ON_ERROR_STOP=1 \
    -Atq \
    -c "select public.ride_test_concurrent_claim({});" \
    >/dev/null

psql "${psql_args[@]}" <<'SQL'
do $test$
declare
    v_claimed bigint;
    v_lost bigint;
    v_other bigint;
    v_assignments bigint;
    v_reservations bigint;
    v_estimates bigint;
    v_events bigint;
begin
    select
        count(*) filter (
            where response #>> '{data,status}' = 'CLAIMED'
        ),
        count(*) filter (
            where response #>> '{error,code}' = 'ALREADY_ASSIGNED'
        ),
        count(*) filter (
            where response #>> '{data,status}' is distinct from 'CLAIMED'
              and response #>> '{error,code}' is distinct from
                  'ALREADY_ASSIGNED'
        )
      into v_claimed, v_lost, v_other
      from public.ride_concurrency_results;

    select count(*)
      into v_assignments
      from public.ride_requests
     where id = '66666666-6666-6666-6666-666666666666'
       and assigned_driver_id is not null
       and state = 'ASSIGNED'
       and version = 2;

    select count(*)
      into v_reservations
      from public.ride_commission_reservations
     where trip_id = '66666666-6666-6666-6666-666666666666'
       and state = 'RESERVED'
       and amount_minor = 230;

    select count(*)
      into v_estimates
      from public.ride_commission_calculations
     where trip_id = '66666666-6666-6666-6666-666666666666'
       and calculation_kind = 'ESTIMATE';

    select count(*)
      into v_events
      from public.ride_trip_events
     where trip_id = '66666666-6666-6666-6666-666666666666'
       and event_type = 'DRIVER_CLAIMED';

    if v_claimed <> 1 or v_lost <> 99 or v_other <> 0 or
       v_assignments <> 1 or v_reservations <> 1 or
       v_estimates <> 1 or v_events <> 1
    then
        raise exception using
            message = format(
                'concurrency invariant failed: claimed=%s lost=%s other=%s assignments=%s reservations=%s estimates=%s events=%s',
                v_claimed, v_lost, v_other, v_assignments,
                v_reservations, v_estimates, v_events
            );
    end if;
end;
$test$;

select
    'ride command authority 100-claim concurrency: PASS' as result,
    count(*) filter (
        where response #>> '{data,status}' = 'CLAIMED'
    ) as winners,
    count(*) filter (
        where response #>> '{error,code}' = 'ALREADY_ASSIGNED'
    ) as non_winners
from public.ride_concurrency_results;
SQL
