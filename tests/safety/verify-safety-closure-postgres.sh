#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

for command_name in initdb pg_ctl psql; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Safety PostgreSQL integration: FAIL ($command_name unavailable)" >&2
    exit 1
  fi
done

runtime_dir="$(mktemp -d "${TMPDIR:-/tmp}/meet-safety-pg.XXXXXX")"
cluster_dir="$runtime_dir/data"
socket_dir="$runtime_dir/socket"
server_log="$runtime_dir/postgres.log"
port="$((57000 + RANDOM % 1000))"
mkdir -p "$socket_dir"

cleanup() {
  if [[ -d "$cluster_dir" ]]; then
    pg_ctl -D "$cluster_dir" stop -m fast >/dev/null 2>&1 || true
  fi
  if [[ -d "$runtime_dir" && "$(basename "$runtime_dir")" == meet-safety-pg.* ]]; then
    rm -rf -- "$runtime_dir"
  fi
}
trap cleanup EXIT

initdb -D "$cluster_dir" --no-locale --encoding=UTF8 >/dev/null
pg_ctl -D "$cluster_dir" -l "$server_log" -o "-p $port -k $socket_dir" start >/dev/null
psql_args=(-h "$socket_dir" -p "$port" -d postgres -v ON_ERROR_STOP=1 -q)
export PGOPTIONS="-c client_min_messages=warning"

# Minimal Supabase-owned dependencies. Safety migrations themselves remain real.
psql "${psql_args[@]}" <<'SQL'
create role anon nologin;
create role authenticated nologin;
create role service_role nologin;
create role postgres nologin;
create schema auth;
create schema extensions;
create schema storage;
create extension pgcrypto with schema extensions;
create table auth.users(id uuid primary key);
create or replace function auth.uid() returns uuid language sql stable as $$
  select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid
$$;
create or replace function auth.jwt() returns jsonb language sql stable as $$
  select coalesce(nullif(current_setting('request.jwt.claims', true), '')::jsonb, '{}'::jsonb)
$$;
create table public.platform_authority_grants(
  user_id uuid not null references auth.users(id),
  role text not null,
  active boolean not null default true,
  primary key(user_id, role)
);
create or replace function public.meet_has_platform_authority(p_role text)
returns boolean language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.platform_authority_grants
                 where user_id = auth.uid() and role = p_role and active)
$$;
create or replace function public.meet_session_has_aal2()
returns boolean language sql stable set search_path = '' as $$
  select coalesce((auth.jwt()->>'aal'), '') = 'aal2'
$$;
create table storage.buckets(id text primary key, name text not null, public boolean not null);
create table storage.objects(
  id uuid primary key default gen_random_uuid(),
  bucket_id text not null references storage.buckets(id),
  name text not null,
  owner uuid
);
alter table storage.objects enable row level security;
create or replace function storage.foldername(name text) returns text[]
language sql immutable as $$
  select case when strpos(name, '/') = 0 then array[]::text[]
              else string_to_array(left(name, length(name) - length(split_part(name, '/', array_length(string_to_array(name, '/'), 1)))), '/')
         end
$$;
create table public.runtime_feature_gates(
  key text primary key,
  enabled boolean not null,
  reason text,
  updated_at timestamptz not null default now()
);
alter table public.runtime_feature_gates enable row level security;
create publication supabase_realtime;
grant usage on schema public, auth, extensions to anon, authenticated, service_role;
grant select on auth.users to authenticated;
SQL

migrations=(
  20260918000000_safety_foundation_v1.sql
  20260918100000_safety_public_views.sql
  20260919053000_safety_operational_v2.sql
  20260919110000_safety_public_accountability_projection.sql
  20260919160821_safety_authority_integrity.sql
  20260920010000_safety_global_citizen_map_and_withdrawal.sql
  20260920020000_safety_geographic_timeline_search.sql
  20260920021000_safety_reporter_anonymity_boundary.sql
  20260923000000_safety_observatory_demographics.sql
  20260926181000_safety_report_provenance_v3.sql
  20260928090000_safety_publication_firewall_v3.sql
  20260928100000_safety_moderation_authority_v3.sql
  20260928110000_safety_case_publication_authority_v3.sql
  20260930120000_safety_review_closure_v3.sql
  20261001010000_safety_evidence_verification_custody_v2.sql
  20261001020000_safety_intake_device_trust.sql
  20261001030000_safety_accountability_observatory_ingestion_v3.sql
  20261001040000_safety_institutional_gateway_retention.sql
  20261001050000_safety_held_withdrawal_preservation.sql
)
for migration in "${migrations[@]}"; do
  psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/$migration" >/dev/null
done

psql "${psql_args[@]}" -f "$repo_root/tests/parity/safety-custody-v2.sql"
psql "${psql_args[@]}" -f "$repo_root/tests/safety/safety-evidence-verification-integration.sql"
psql "${psql_args[@]}" -f "$repo_root/tests/safety/safety-backend-domains-integration.sql"
bash "$repo_root/tests/safety/run-safety-intake-concurrency.sh" "$socket_dir" "$port"
echo "Safety closure PostgreSQL integration: PASS"
