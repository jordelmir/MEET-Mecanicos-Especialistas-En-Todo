-- Exact trip paths are safety evidence, not public telemetry. Capture is
-- driver-authoritative, participant-readable, retention-limited and any
-- external disclosure requires an explicit audited legal hold.

alter table public.ride_location_breadcrumbs
  add column if not exists retention_until timestamptz;

update public.ride_location_breadcrumbs
set retention_until = recorded_at + interval '90 days'
where retention_until is null;

alter table public.ride_location_breadcrumbs
  alter column retention_until set default (now() + interval '90 days'),
  alter column retention_until set not null;

create unique index if not exists uq_ride_breadcrumb_driver_trip_seq
  on public.ride_location_breadcrumbs(driver_id, trip_id, seq)
  where trip_id is not null;

create table if not exists public.ride_location_legal_holds (
  id uuid primary key default gen_random_uuid(),
  trip_id uuid not null references public.ride_requests(id) on delete cascade,
  authority_name text not null check (length(trim(authority_name)) between 3 and 200),
  case_reference text not null check (length(trim(case_reference)) between 3 and 200),
  purpose text not null check (length(trim(purpose)) between 10 and 1000),
  created_by uuid not null references auth.users(id),
  created_at timestamptz not null default now(),
  expires_at timestamptz not null,
  revoked_at timestamptz,
  check (expires_at > created_at)
);

create table if not exists public.ride_location_disclosure_audit (
  id uuid primary key default gen_random_uuid(),
  trip_id uuid not null references public.ride_requests(id) on delete restrict,
  legal_hold_id uuid not null references public.ride_location_legal_holds(id) on delete restrict,
  disclosed_by uuid not null references auth.users(id),
  disclosed_at timestamptz not null default now(),
  purpose text not null,
  row_count integer not null check (row_count >= 0)
);

alter table public.ride_location_legal_holds enable row level security;
alter table public.ride_location_disclosure_audit enable row level security;
revoke all on public.ride_location_legal_holds from anon, authenticated;
revoke all on public.ride_location_disclosure_audit from anon, authenticated;
grant select on public.ride_location_legal_holds to authenticated;
grant select on public.ride_location_disclosure_audit to authenticated;

drop policy if exists ride_location_holds_owner_read on public.ride_location_legal_holds;
create policy ride_location_holds_owner_read on public.ride_location_legal_holds
for select to authenticated using (public.meet_is_platform_owner());

drop policy if exists ride_location_disclosure_owner_read on public.ride_location_disclosure_audit;
create policy ride_location_disclosure_owner_read on public.ride_location_disclosure_audit
for select to authenticated using (public.meet_is_platform_owner());

create or replace function public.ride_record_location_breadcrumb_v2(
  p_trip_id uuid,
  p_seq bigint,
  p_latitude double precision,
  p_longitude double precision,
  p_accuracy real,
  p_heading integer default null,
  p_speed real default null,
  p_captured_at_epoch_ms bigint default null
) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
  v_actor uuid := auth.uid();
  v_trip public.ride_requests;
  v_recorded_at timestamptz;
  v_id uuid;
begin
  if v_actor is null then raise exception 'AUTH_REQUIRED'; end if;
  select * into v_trip from public.ride_requests where id = p_trip_id for share;
  if not found then raise exception 'RIDE_NOT_FOUND'; end if;
  if v_trip.assigned_driver_id is distinct from v_actor then raise exception 'DRIVER_AUTHORITY_REQUIRED'; end if;
  if v_trip.state not in ('ASSIGNED','DRIVER_EN_ROUTE','ARRIVED','PASSENGER_ONBOARD','IN_PROGRESS') then
    raise exception 'RIDE_NOT_TRACKABLE';
  end if;
  if p_seq <= 0 or p_latitude not between -90 and 90 or p_longitude not between -180 and 180 then
    raise exception 'INVALID_LOCATION';
  end if;
  if p_accuracy < 0 or p_accuracy > 500 or p_heading is not null and p_heading not between 0 and 359
     or p_speed is not null and (p_speed < 0 or p_speed > 100) then
    raise exception 'INVALID_LOCATION_QUALITY';
  end if;
  v_recorded_at := to_timestamp(p_captured_at_epoch_ms / 1000.0);
  if v_recorded_at > now() + interval '2 minutes' or v_recorded_at < now() - interval '24 hours' then
    raise exception 'INVALID_CAPTURE_TIME';
  end if;

  insert into public.ride_location_breadcrumbs(
    driver_id, trip_id, seq, location, heading, speed_mps, accuracy_m,
    recorded_at, received_at, retention_until
  ) values (
    v_actor, p_trip_id, p_seq,
    extensions.st_setsrid(extensions.st_makepoint(p_longitude, p_latitude), 4326)::extensions.geography,
    p_heading::smallint, p_speed, p_accuracy, v_recorded_at, now(),
    v_recorded_at + interval '90 days'
  ) on conflict (driver_id, trip_id, seq) where trip_id is not null do update
    set received_at = public.ride_location_breadcrumbs.received_at
  returning id into v_id;

  return jsonb_build_object('id', v_id, 'sequence', p_seq, 'recorded_at', v_recorded_at);
end;
$$;

create or replace function public.ride_create_location_legal_hold_v1(
  p_trip_id uuid, p_authority_name text, p_case_reference text,
  p_purpose text, p_expires_at timestamptz
) returns uuid
language plpgsql security definer set search_path = '' as $$
declare v_id uuid;
begin
  if not public.meet_is_platform_owner() then raise exception 'OWNER_AUTHORITY_REQUIRED'; end if;
  if p_expires_at <= now() or p_expires_at > now() + interval '10 years' then
    raise exception 'INVALID_HOLD_EXPIRY';
  end if;
  insert into public.ride_location_legal_holds(
    trip_id, authority_name, case_reference, purpose, created_by, expires_at
  ) values (
    p_trip_id, trim(p_authority_name), trim(p_case_reference), trim(p_purpose), auth.uid(), p_expires_at
  ) returning id into v_id;
  return v_id;
end;
$$;

create or replace function public.ride_disclose_location_v1(
  p_legal_hold_id uuid, p_purpose text
) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare v_hold public.ride_location_legal_holds; v_points jsonb; v_count integer;
begin
  if not public.meet_is_platform_owner() then raise exception 'OWNER_AUTHORITY_REQUIRED'; end if;
  select * into v_hold from public.ride_location_legal_holds
  where id = p_legal_hold_id and revoked_at is null and expires_at > now();
  if not found then raise exception 'ACTIVE_LEGAL_HOLD_REQUIRED'; end if;
  if length(trim(p_purpose)) < 10 then raise exception 'DISCLOSURE_PURPOSE_REQUIRED'; end if;

  select count(*), coalesce(jsonb_agg(jsonb_build_object(
    'sequence', b.seq,
    'latitude', extensions.st_y(b.location::extensions.geometry),
    'longitude', extensions.st_x(b.location::extensions.geometry),
    'accuracy_meters', b.accuracy_m,
    'heading_degrees', b.heading,
    'speed_mps', b.speed_mps,
    'captured_at', b.recorded_at,
    'received_at', b.received_at
  ) order by b.seq), '[]'::jsonb)
  into v_count, v_points from public.ride_location_breadcrumbs b
  where b.trip_id = v_hold.trip_id;

  insert into public.ride_location_disclosure_audit(
    trip_id, legal_hold_id, disclosed_by, purpose, row_count
  ) values (v_hold.trip_id, v_hold.id, auth.uid(), trim(p_purpose), v_count);
  return jsonb_build_object('trip_id', v_hold.trip_id, 'captured_points', v_points, 'point_count', v_count);
end;
$$;

create or replace function public.ride_purge_expired_location_breadcrumbs_v1()
returns integer language plpgsql security definer set search_path = '' as $$
declare v_count integer;
begin
  if not public.meet_is_platform_owner() then raise exception 'OWNER_AUTHORITY_REQUIRED'; end if;
  delete from public.ride_location_breadcrumbs b
  where b.retention_until < now()
    and not exists (
      select 1 from public.ride_location_legal_holds h
      where h.trip_id = b.trip_id and h.revoked_at is null and h.expires_at > now()
    );
  get diagnostics v_count = row_count;
  return v_count;
end;
$$;

revoke all on function public.ride_record_location_breadcrumb_v2(uuid,bigint,double precision,double precision,real,integer,real,bigint) from public;
revoke all on function public.ride_create_location_legal_hold_v1(uuid,text,text,text,timestamptz) from public;
revoke all on function public.ride_disclose_location_v1(uuid,text) from public;
revoke all on function public.ride_purge_expired_location_breadcrumbs_v1() from public;
revoke all on function public.ride_record_location_breadcrumb_v2(uuid,bigint,double precision,double precision,real,integer,real,bigint) from anon;
revoke all on function public.ride_create_location_legal_hold_v1(uuid,text,text,text,timestamptz) from anon;
revoke all on function public.ride_disclose_location_v1(uuid,text) from anon;
revoke all on function public.ride_purge_expired_location_breadcrumbs_v1() from anon;
grant execute on function public.ride_record_location_breadcrumb_v2(uuid,bigint,double precision,double precision,real,integer,real,bigint) to authenticated;
grant execute on function public.ride_create_location_legal_hold_v1(uuid,text,text,text,timestamptz) to authenticated;
grant execute on function public.ride_disclose_location_v1(uuid,text) to authenticated;
grant execute on function public.ride_purge_expired_location_breadcrumbs_v1() to authenticated;
