#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
for command_name in initdb pg_ctl psql; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Vanguard Convergence PostgreSQL: SKIP ($command_name unavailable)"
    exit 0
  fi
done

runtime_dir="$(mktemp -d "${TMPDIR:-/tmp}/meet-vanguard-pg.XXXXXX")"
cluster_dir="$runtime_dir/data"
socket_dir="$runtime_dir/socket"
server_log="$runtime_dir/postgres.log"
port="$((56400 + RANDOM % 400))"
mkdir -p "$socket_dir"
cleanup() {
  if [[ -d "$cluster_dir" ]]; then pg_ctl -D "$cluster_dir" stop -m fast >/dev/null 2>&1 || true; fi
  if [[ -d "$runtime_dir" && "$(basename "$runtime_dir")" == meet-vanguard-pg.* ]]; then rm -rf -- "$runtime_dir"; fi
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
create table auth.users(
  id uuid primary key default extensions.gen_random_uuid(),
  email text,
  email_confirmed_at timestamptz
);
create or replace function auth.uid() returns uuid language sql stable as $$
  select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid
$$;
create or replace function auth.jwt() returns jsonb language sql stable as $$
  select coalesce(nullif(current_setting('request.jwt.claims', true), ''), '{}')::jsonb
$$;
create table public.communication_conversations(
  id uuid primary key default extensions.gen_random_uuid(), kind text not null, title text not null,
  service_vertical text, service_reference_id uuid, created_by uuid not null references auth.users(id),
  unique(service_vertical,service_reference_id)
);
create table public.communication_participants(
  conversation_id uuid not null references public.communication_conversations(id),
  principal_id uuid not null references auth.users(id), role text not null,
  primary key(conversation_id,principal_id)
);
create publication supabase_realtime;
grant usage on schema public,auth,extensions to anon,authenticated;
grant select on auth.users to authenticated;
SQL

for migration in \
  20260826010000_market_operating_system_foundation.sql \
  20260826011000_market_taxonomy_costa_rica.sql \
  20260826012000_market_authoritative_commands.sql \
  20260826013000_market_runtime_wallet_and_realtime.sql \
  20260826014000_market_vertical_operations.sql \
  20260826015000_market_privacy_authority_and_evidence.sql \
  20260826160949_market_fk_index_hardening.sql; do
  psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/$migration" >/dev/null
done

psql "${psql_args[@]}" <<'SQL'
create table public.platform_authorities(
  user_id uuid primary key references auth.users(id), role text not null,
  email_snapshot text not null, active boolean not null default true,
  granted_at timestamptz not null default now()
);
create table public.service_verification_applications(
  id uuid primary key default extensions.gen_random_uuid(),
  applicant_user_id uuid not null references auth.users(id), service_type text not null,
  profile_reference text not null, display_name text not null, business_name text, phone text,
  location_label text, license_reference text, evidence_manifest_sha256 text, status text not null default 'PENDING',
  decision_reason text, submitted_at timestamptz not null default now(), reviewed_at timestamptz,
  reviewed_by uuid references auth.users(id), updated_at timestamptz not null default now(),
  unique(applicant_user_id,service_type,profile_reference),
  constraint service_verification_applications_service_type_check check(service_type in('PASSENGER','RIDE_DRIVER','TOW_TRUCK','MECHANIC','PARTS_STORE','SERVICE_PROVIDER'))
);
create table public.service_verification_audit_events(
  id bigint generated always as identity primary key,
  application_id uuid not null references public.service_verification_applications(id),
  actor_id uuid not null references auth.users(id), event_type text not null,
  from_status text,to_status text not null,reason text,created_at timestamptz not null default now()
);
grant select on public.service_verification_applications,
  public.service_verification_audit_events to authenticated;
create or replace function public.meet_bootstrap_platform_owner() returns trigger
language plpgsql security definer set search_path='' as $$ begin return new; end $$;
create trigger meet_bootstrap_platform_owner_trigger after insert on auth.users
for each row execute function public.meet_bootstrap_platform_owner();
create or replace function public.meet_is_platform_owner() returns boolean
language sql stable security definer set search_path='' as $$ select false $$;
create or replace function public.meet_owner_decide_verification_v1(uuid,text,text) returns jsonb
language sql security definer set search_path='' as $$ select '{}'::jsonb $$;
revoke all on function public.meet_owner_decide_verification_v1(uuid,text,text) from public;
grant execute on function public.meet_owner_decide_verification_v1(uuid,text,text) to authenticated;

insert into auth.users(id) values
 ('00000000-0000-0000-0000-000000000001'),
 ('00000000-0000-0000-0000-000000000002');
insert into public.platform_authorities(user_id,role,email_snapshot)
values('00000000-0000-0000-0000-000000000001','PLATFORM_OWNER','migration-snapshot');
SQL

psql "${psql_args[@]}" -f "$repo_root/supabase/migrations/20260828010000_vanguard_convergence_v5.sql" >/dev/null

psql "${psql_args[@]}" <<'SQL'
set role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',false);
select set_config('request.jwt.claims','{"aal":"aal1"}',false);
do $$ begin
  begin
    perform public.meet_owner_set_platform_authority_v1(
      '00000000-0000-0000-0000-000000000002','TRUST_REVIEWER',true,'AAL1 must fail'
    );
    raise exception 'AAL1 unexpectedly granted authority';
  exception when insufficient_privilege then
    if sqlerrm <> 'AAL2_REQUIRED' then raise; end if;
  end;
end $$;

select set_config('request.jwt.claims','{"aal":"aal2"}',false);
select public.meet_owner_set_platform_authority_v1(
  '00000000-0000-0000-0000-000000000002','TRUST_REVIEWER',true,'Controlled test grant'
);
do $$ begin
  if has_function_privilege('authenticated','public.meet_owner_decide_verification_v1(uuid,text,text)','EXECUTE') then
    raise exception 'Legacy non-AAL2 decision RPC remains executable';
  end if;
  if has_table_privilege('authenticated','public.principal_service_metrics','UPDATE') then
    raise exception 'Client can mutate server metrics';
  end if;
end $$;

select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',false);
do $$ begin
  if not public.meet_has_platform_authority('TRUST_REVIEWER') then
    raise exception 'AAL2 grant was not persisted';
  end if;
  begin
    perform public.meet_owner_set_platform_authority_v1(
      '00000000-0000-0000-0000-000000000002','PLATFORM_OWNER',true,'Client escalation must fail'
    );
    raise exception 'Client escalated itself';
  exception when insufficient_privilege then
    if sqlerrm <> 'PLATFORM_OWNER_REQUIRED' then raise; end if;
  end;
  perform public.meet_submit_capability_application_v1(
    'LAWYER','profile-lawyer','Professional test',repeat('a',64)
  );
  if not exists(
    select 1 from public.principal_capabilities
    where principal_id='00000000-0000-0000-0000-000000000002'
      and capability='LAWYER' and activation_state='SUBMITTED' and verified_at is null
  ) then
    raise exception 'Capability intent incorrectly granted or was not persisted';
  end if;
end $$;
reset role;
do $$ begin
  if (select count(*) from public.principals) <> 2 then
    raise exception 'Existing auth users were not bootstrapped as principals';
  end if;
end $$;
SQL

psql "${psql_args[@]}" \
  -f "$repo_root/supabase/migrations/20260829010000_platform_trust_center_delivery_realtime.sql" \
  >/dev/null
psql "${psql_args[@]}" \
  -f "$repo_root/tests/vanguard-convergence/trust-center-delivery-integration.sql"

echo "Vanguard Convergence PostgreSQL: PASS"
