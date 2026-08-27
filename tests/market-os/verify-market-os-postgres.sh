#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

for command_name in initdb pg_ctl psql; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Market OS PostgreSQL integration: SKIP ($command_name unavailable)"
    exit 0
  fi
done

runtime_dir="$(mktemp -d "${TMPDIR:-/tmp}/meet-market-os-pg.XXXXXX")"
cluster_dir="$runtime_dir/data"
socket_dir="$runtime_dir/socket"
server_log="$runtime_dir/postgres.log"
port="$((55900 + RANDOM % 500))"
mkdir -p "$socket_dir"

cleanup() {
  if [[ -d "$cluster_dir" ]]; then
    pg_ctl -D "$cluster_dir" stop -m fast >/dev/null 2>&1 || true
  fi
  if [[ -d "$runtime_dir" && "$(basename "$runtime_dir")" == meet-market-os-pg.* ]]; then
    rm -rf -- "$runtime_dir"
  fi
}
trap cleanup EXIT

initdb -D "$cluster_dir" --no-locale --encoding=UTF8 >/dev/null
pg_ctl -D "$cluster_dir" -l "$server_log" -o "-p $port -k $socket_dir" start >/dev/null

psql_args=(-h "$socket_dir" -p "$port" -d postgres -v ON_ERROR_STOP=1 -q)
export PGOPTIONS="-c client_min_messages=warning"

psql "${psql_args[@]}" <<'SQL'
create role anon nologin;
create role authenticated nologin;
create role service_role nologin;
create schema auth;
create schema extensions;
create extension pgcrypto with schema extensions;
create table auth.users(id uuid primary key default extensions.gen_random_uuid());
create or replace function auth.uid() returns uuid language sql stable as $$
  select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid
$$;

-- The legal communication bridge references the already-deployed communication
-- core. Minimal authoritative shapes keep this suite focused on Market OS.
create table public.communication_conversations(
  id uuid primary key default extensions.gen_random_uuid(),
  kind text not null,
  title text not null,
  service_vertical text,
  service_reference_id uuid,
  created_by uuid not null references auth.users(id),
  unique(service_vertical, service_reference_id)
);
create table public.communication_participants(
  conversation_id uuid not null references public.communication_conversations(id),
  principal_id uuid not null references auth.users(id),
  role text not null,
  primary key(conversation_id, principal_id)
);

create publication supabase_realtime;
grant usage on schema public, auth, extensions to anon, authenticated;
grant select on auth.users to authenticated;
SQL

migrations=(
  "$repo_root/supabase/migrations/20260826010000_market_operating_system_foundation.sql"
  "$repo_root/supabase/migrations/20260826011000_market_taxonomy_costa_rica.sql"
  "$repo_root/supabase/migrations/20260826012000_market_authoritative_commands.sql"
  "$repo_root/supabase/migrations/20260826013000_market_runtime_wallet_and_realtime.sql"
  "$repo_root/supabase/migrations/20260826014000_market_vertical_operations.sql"
  "$repo_root/supabase/migrations/20260826015000_market_privacy_authority_and_evidence.sql"
  "$repo_root/supabase/migrations/20260826160949_market_fk_index_hardening.sql"
)

for migration in "${migrations[@]}"; do
  psql "${psql_args[@]}" -f "$migration" >/dev/null
done

psql "${psql_args[@]}" -f "$repo_root/tests/market-os/market-os-integration.sql"
echo "Market OS PostgreSQL integration: PASS"
