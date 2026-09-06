#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

required_commands=(initdb pg_ctl psql)
for command_name in "${required_commands[@]}"; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "tow authority PostgreSQL integration: SKIP ($command_name unavailable)"
    exit 0
  fi
done

runtime_dir="$(mktemp -d "${TMPDIR:-/tmp}/meet-tow-pg.XXXXXX")"
cluster_dir="$runtime_dir/data"
socket_dir="$runtime_dir/socket"
server_log="$runtime_dir/postgres.log"
port="$((56400 + RANDOM % 500))"
mkdir -p "$socket_dir"

cleanup() {
  if [[ -d "$cluster_dir" ]]; then
    pg_ctl -D "$cluster_dir" stop -m fast >/dev/null 2>&1 || true
  fi
  if [[ -d "$runtime_dir" && "$(basename "$runtime_dir")" == meet-tow-pg.* ]]; then
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

# 1. Base Supabase roles & schemas setup
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
    primary_role text not null default 'driver' check (primary_role in (
        'driver', 'enthusiast', 'pro_user', 'mechanic', 'workshop_owner',
        'parts_store', 'tow_provider', 'ride_driver', 'fleet_manager',
        'verified_company', 'creator', 'admin', 'super_admin',
        'support_agent', 'trust_safety_reviewer'
    )),
    updated_at timestamptz not null default now()
);

create table if not exists public.provider_profiles (
    id uuid primary key default extensions.gen_random_uuid(),
    user_profile_id uuid not null references public.user_profiles(id) on delete cascade,
    provider_type text not null check (provider_type in (
        'mechanic', 'workshop', 'parts_store', 'tow_provider', 'ride_driver', 'creator'
    )),
    business_name text,
    description text,
    phone text,
    email text,
    location_text text,
    location_lat double precision,
    location_lng double precision,
    coverage_radius_km double precision default 10.0,
    specializations text[] default '{}',
    certifications jsonb default '[]'::jsonb,
    operating_hours jsonb default '{}'::jsonb,
    is_verified boolean not null default false,
    is_active boolean not null default true,
    status text not null default 'pending' check (status in ('pending', 'active', 'suspended', 'banned')),
    version integer not null default 1,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (user_profile_id, provider_type)
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

create or replace function auth.role()
returns text
language sql
stable
as $$
    select nullif(
        current_setting('request.jwt.claim.role', true),
        ''
    )
$$;

grant usage on schema public to anon, authenticated;
grant usage on schema auth to anon, authenticated;
grant select on auth.users to authenticated;
grant select on public.user_profiles to authenticated;
grant select on public.provider_profiles to authenticated;
SQL

# 2. Run Migration: 20260905180000_tow_fulfillment_authority.sql
psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260905180000_tow_fulfillment_authority.sql"

# 3. Run Test Suite: tow_authority_v5.sql
psql "${psql_args[@]}" -f "$repo_root/tests/supabase/tow_authority_v5.sql"

echo "tow authority PostgreSQL integration: PASS"
