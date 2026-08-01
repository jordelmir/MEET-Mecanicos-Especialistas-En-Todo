-- Elysium Viajes: precise arrival, driver operations, fleet and notification authority.

alter table public.ride_driver_vehicles
    add column if not exists make text,
    add column if not exists model text,
    add column if not exists model_year integer,
    add column if not exists color text,
    add column if not exists plate_masked text,
    add column if not exists fleet_name text;

alter table public.ride_driver_vehicles
    drop constraint if exists ride_driver_vehicle_model_year_check;
alter table public.ride_driver_vehicles
    add constraint ride_driver_vehicle_model_year_check
    check (model_year is null or model_year between 1900 and 2200);

create table if not exists public.ride_driver_operating_profiles (
    driver_id uuid primary key references public.ride_profiles(user_id) on delete cascade,
    home_latitude double precision check (home_latitude between -90 and 90),
    home_longitude double precision check (home_longitude between -180 and 180),
    home_label text check (home_label is null or char_length(home_label) between 1 and 160),
    destination_home_enabled boolean not null default false,
    last_liveness_verified_at timestamptz,
    liveness_valid_until timestamptz,
    updated_at timestamptz not null default now(),
    check (
        (home_latitude is null and home_longitude is null) or
        (home_latitude is not null and home_longitude is not null)
    )
);

create table if not exists public.ride_driver_liveness_sessions (
    id uuid primary key default gen_random_uuid(),
    driver_id uuid not null references public.ride_profiles(user_id) on delete cascade,
    evidence_sha256 text not null check (evidence_sha256 ~ '^[0-9a-f]{64}$'),
    blink_detected boolean not null,
    device_attested boolean not null default false,
    captured_at timestamptz not null,
    valid_until timestamptz not null,
    created_at timestamptz not null default now(),
    check (captured_at <= valid_until)
);

create index if not exists ride_driver_liveness_recent_idx
    on public.ride_driver_liveness_sessions(driver_id, captured_at desc);

create table if not exists public.ride_push_outbox (
    id uuid primary key default gen_random_uuid(),
    recipient_id uuid not null references auth.users(id) on delete cascade,
    trip_id uuid references public.ride_requests(id) on delete cascade,
    notification_type text not null check (
        notification_type in ('IDLE_DEMAND', 'DESTINATION_ETA_7_MIN', 'DRIVER_ARRIVED', 'TRIP_ASSIGNED')
    ),
    title text not null check (char_length(title) between 1 and 120),
    body text not null check (char_length(body) between 1 and 500),
    dedupe_key text not null unique,
    deliver_after timestamptz not null default now(),
    delivered_at timestamptz,
    created_at timestamptz not null default now()
);

create or replace function public.ride_record_driver_liveness_v1(
    p_evidence_sha256 text,
    p_captured_at timestamptz
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := (select auth.uid());
    v_valid_until timestamptz := p_captured_at + interval '12 hours';
begin
    if v_user_id is null then
        return public.ride_command_error('UNAUTHENTICATED', 'Autenticación requerida', false);
    end if;
    if p_evidence_sha256 !~ '^[0-9a-f]{64}$' or
       p_captured_at < now() - interval '2 minutes' or
       p_captured_at > now() + interval '10 seconds'
    then
        return public.ride_command_error('LIVENESS_EVIDENCE_INVALID', 'Evidencia de presencia inválida', false);
    end if;
    insert into public.ride_driver_liveness_sessions(
        driver_id, evidence_sha256, blink_detected, device_attested,
        captured_at, valid_until
    ) values (
        v_user_id, p_evidence_sha256, true, false, p_captured_at, v_valid_until
    );
    insert into public.ride_driver_operating_profiles(
        driver_id, last_liveness_verified_at, liveness_valid_until, updated_at
    ) values (v_user_id, p_captured_at, v_valid_until, now())
    on conflict (driver_id) do update set
        last_liveness_verified_at = excluded.last_liveness_verified_at,
        liveness_valid_until = excluded.liveness_valid_until,
        updated_at = now();
    return public.ride_command_success(jsonb_build_object(
        'status', 'LIVENESS_VERIFIED', 'valid_until', v_valid_until
    ));
end;
$$;

create or replace function public.ride_upsert_driver_vehicle_v1(
    p_vehicle_id text,
    p_display_name text,
    p_seats integer,
    p_make text,
    p_model text,
    p_model_year integer,
    p_color text,
    p_plate_masked text,
    p_fleet_name text default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := (select auth.uid());
    v_id uuid;
begin
    if v_user_id is null then
        return public.ride_command_error('UNAUTHENTICATED', 'Autenticación requerida', false);
    end if;
    if char_length(trim(p_vehicle_id)) not between 1 and 120 or
       char_length(trim(p_display_name)) not between 1 and 160 or
       p_seats not between 1 and 16 or
       p_model_year not between 1900 and 2200 or
       coalesce(trim(p_make), '') = '' or coalesce(trim(p_model), '') = '' or
       coalesce(trim(p_color), '') = '' or coalesce(trim(p_plate_masked), '') = ''
    then
        return public.ride_command_error('VALIDATION_ERROR', 'Datos del vehículo inválidos', false);
    end if;
    insert into public.ride_driver_vehicles(
        driver_id, vehicle_id, display_name, seats, verification_status,
        is_active, make, model, model_year, color, plate_masked, fleet_name
    ) values (
        v_user_id, trim(p_vehicle_id), trim(p_display_name), p_seats, 'PENDING',
        false, trim(p_make), trim(p_model), p_model_year, trim(p_color),
        upper(trim(p_plate_masked)), nullif(trim(p_fleet_name), '')
    )
    returning id into v_id;
    return public.ride_command_success(jsonb_build_object(
        'status', 'PENDING_REVIEW', 'vehicle_id', v_id
    ));
end;
$$;

create or replace function public.ride_set_active_vehicle_v1(p_vehicle_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := (select auth.uid());
begin
    if v_user_id is null then
        return public.ride_command_error('UNAUTHENTICATED', 'Autenticación requerida', false);
    end if;
    if not exists (
        select 1 from public.ride_driver_vehicles v
         where v.id = p_vehicle_id and v.driver_id = v_user_id
           and v.verification_status = 'VERIFIED'
    ) then
        return public.ride_command_error('VEHICLE_NOT_VERIFIED', 'Vehículo no verificado', false);
    end if;
    perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_user_id::text, 0));
    update public.ride_driver_vehicles set is_active = false, updated_at = now()
     where driver_id = v_user_id and is_active;
    update public.ride_driver_vehicles set is_active = true, updated_at = now()
     where id = p_vehicle_id and driver_id = v_user_id;
    return public.ride_command_success(jsonb_build_object(
        'status', 'ACTIVE', 'vehicle_id', p_vehicle_id
    ));
end;
$$;

alter table public.ride_road_incidents
    drop constraint if exists ride_road_incidents_incident_type_check;
alter table public.ride_road_incidents
    add constraint ride_road_incidents_incident_type_check check (
        incident_type in (
            'SLOW_TRAFFIC', 'VERY_SLOW_TRAFFIC', 'STALLED_VEHICLE', 'POTHOLE',
            'OBSTACLE', 'ROAD_CLOSED', 'WRONG_WAY_HAZARD', 'POLICE_PRESENCE',
            'TRAFFIC_CONTROL', 'PUBLIC_POLICE', 'TRAFFIC_POLICE', 'SPEED_BUMP',
            'FLOODING'
        )
    );

create or replace function public.ride_driver_arrived_v3(
    p_trip_id uuid,
    p_driver_latitude double precision,
    p_driver_longitude double precision,
    p_accuracy_meters real,
    p_captured_at timestamptz,
    p_expected_version bigint,
    p_idempotency_key text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := (select auth.uid());
    v_request public.ride_requests%rowtype;
    v_distance_meters double precision;
    v_request_hash text;
    v_replay jsonb;
    v_response jsonb;
begin
    if v_user_id is null then
        return public.ride_command_error('UNAUTHENTICATED', 'Autenticación requerida', false);
    end if;
    if p_driver_latitude not between -90 and 90 or
       p_driver_longitude not between -180 and 180 or
       p_accuracy_meters not between 0 and 75 or
       p_captured_at < now() - interval '30 seconds' or
       p_captured_at > now() + interval '5 seconds' or
       coalesce(p_expected_version, 0) <= 0 or
       coalesce(p_idempotency_key, '') !~ '^[A-Za-z0-9._:-]{16,128}$'
    then
        return public.ride_command_error(
            'LOCATION_EVIDENCE_INVALID',
            'Se requiere GPS reciente con precisión de 75 m o mejor', false
        );
    end if;

    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(v_user_id::text || ':' || p_idempotency_key, 0)
    );
    v_request_hash := public.ride_command_hash(jsonb_build_object(
        'command', 'DRIVER_ARRIVED', 'trip_id', p_trip_id,
        'driver_latitude', p_driver_latitude,
        'driver_longitude', p_driver_longitude,
        'accuracy_meters', p_accuracy_meters,
        'captured_at', p_captured_at,
        'expected_version', p_expected_version
    ));
    v_replay := public.ride_command_replay(v_user_id, p_idempotency_key, v_request_hash);
    if v_replay is not null then return v_replay; end if;

    select r.* into v_request
      from public.ride_requests r
     where r.id = p_trip_id
     for update;
    if not found then
        return public.ride_command_error('NOT_FOUND', 'Viaje no encontrado', false);
    end if;
    if v_request.assigned_driver_id <> v_user_id then
        return public.ride_command_error('FORBIDDEN', 'Sólo el conductor asignado puede llegar', false);
    end if;
    if v_request.version <> p_expected_version then
        return public.ride_command_error(
            'VERSION_CONFLICT', 'La versión del viaje cambió', true,
            jsonb_build_object('current_version', v_request.version)
        );
    end if;
    if v_request.state <> 'DRIVER_EN_ROUTE' then
        return public.ride_command_error('INVALID_STATE', 'El viaje no está en ruta a recogida', false);
    end if;

    v_distance_meters := 2 * 6371000 * asin(sqrt(
        power(sin(radians(p_driver_latitude - v_request.pickup_latitude) / 2), 2) +
        cos(radians(v_request.pickup_latitude)) * cos(radians(p_driver_latitude)) *
        power(sin(radians(p_driver_longitude - v_request.pickup_longitude) / 2), 2)
    ));
    if v_distance_meters > 100 then
        return public.ride_command_error(
            'OUTSIDE_PICKUP_GEOFENCE',
            'El conductor debe estar a 100 m o menos del pin de recogida', false,
            jsonb_build_object('distance_meters', round(v_distance_meters::numeric, 1))
        );
    end if;

    insert into public.ride_positions(
        trip_id, subject_user_id, subject_role, latitude, longitude,
        accuracy_meters, sequence, captured_at, expires_at
    ) values (
        p_trip_id, v_user_id, 'DRIVER', p_driver_latitude, p_driver_longitude,
        p_accuracy_meters, p_expected_version, p_captured_at, now() + interval '24 hours'
    ) on conflict (trip_id, subject_user_id) do update set
        latitude = excluded.latitude,
        longitude = excluded.longitude,
        accuracy_meters = excluded.accuracy_meters,
        sequence = excluded.sequence,
        captured_at = excluded.captured_at,
        expires_at = excluded.expires_at;

    update public.ride_requests
       set state = 'ARRIVED', version = version + 1, updated_at = now()
     where id = p_trip_id and version = p_expected_version and state = 'DRIVER_EN_ROUTE'
     returning * into v_request;

    insert into public.ride_trip_events(
        trip_id, actor_id, event_type, from_state, to_state, payload, idempotency_key
    ) values (
        p_trip_id, v_user_id, 'DRIVER_ARRIVED', 'DRIVER_EN_ROUTE', 'ARRIVED',
        jsonb_build_object(
            'distance_meters', round(v_distance_meters::numeric, 1),
            'accuracy_meters', p_accuracy_meters,
            'captured_at', p_captured_at,
            'version', v_request.version
        ),
        p_idempotency_key
    );

    insert into public.ride_push_outbox(
        recipient_id, trip_id, notification_type, title, body, dedupe_key
    ) values (
        v_request.passenger_id, p_trip_id, 'DRIVER_ARRIVED',
        'Tu conductor llegó', 'Abre Elysium Viajes para ver tu PIN privado de abordaje.',
        'driver-arrived:' || p_trip_id::text
    ) on conflict (dedupe_key) do nothing;

    v_response := public.ride_command_success(jsonb_build_object(
        'status', 'ARRIVED', 'trip_id', p_trip_id, 'version', v_request.version,
        'distance_meters', round(v_distance_meters::numeric, 1)
    ));
    return public.ride_record_command_receipt(
        v_user_id, p_trip_id, 'DRIVER_ARRIVED', p_idempotency_key,
        v_request_hash, v_response
    );
end;
$$;

alter table public.ride_driver_operating_profiles enable row level security;
alter table public.ride_driver_liveness_sessions enable row level security;
alter table public.ride_push_outbox enable row level security;

create policy ride_driver_operating_profile_owner_select
on public.ride_driver_operating_profiles for select to authenticated
using (driver_id = (select auth.uid()));

create policy ride_driver_liveness_owner_select
on public.ride_driver_liveness_sessions for select to authenticated
using (driver_id = (select auth.uid()));

create policy ride_push_outbox_recipient_select
on public.ride_push_outbox for select to authenticated
using (recipient_id = (select auth.uid()));

revoke all on public.ride_driver_liveness_sessions from anon, authenticated;
revoke all on public.ride_push_outbox from anon, authenticated;
revoke insert, update, delete on public.ride_driver_operating_profiles from authenticated;
grant select on public.ride_driver_liveness_sessions to authenticated;
grant select on public.ride_push_outbox to authenticated;
revoke all on function public.ride_driver_arrived_v3(
    uuid, double precision, double precision, real, timestamptz, bigint, text
) from public;
grant execute on function public.ride_driver_arrived_v3(
    uuid, double precision, double precision, real, timestamptz, bigint, text
) to authenticated;
revoke all on function public.ride_record_driver_liveness_v1(text, timestamptz) from public;
grant execute on function public.ride_record_driver_liveness_v1(text, timestamptz) to authenticated;
revoke all on function public.ride_upsert_driver_vehicle_v1(
    text, text, integer, text, text, integer, text, text, text
) from public;
grant execute on function public.ride_upsert_driver_vehicle_v1(
    text, text, integer, text, text, integer, text, text, text
) to authenticated;
revoke all on function public.ride_set_active_vehicle_v1(uuid) from public;
grant execute on function public.ride_set_active_vehicle_v1(uuid) to authenticated;

comment on table public.ride_driver_liveness_sessions is
    'Stores hashed liveness evidence and blink outcome, never a reusable face template.';
comment on table public.ride_push_outbox is
    'Authoritative notification outbox. Delivery requires a configured push worker; rows are not proof of delivery.';

create or replace view public.ride_driver_performance_v1
with (security_invoker = true)
as
select
    p.user_id as driver_id,
    (select count(*) from public.ride_offers o where o.driver_id = p.user_id) as offers_submitted,
    (select count(*) from public.ride_offers o where o.driver_id = p.user_id and o.state = 'ACCEPTED') as offers_accepted,
    case when (select count(*) from public.ride_offers o where o.driver_id = p.user_id) = 0 then null else
        round(
            100.0 * (select count(*) from public.ride_offers o where o.driver_id = p.user_id and o.state = 'ACCEPTED') /
            (select count(*) from public.ride_offers o where o.driver_id = p.user_id),
            2
        )
    end as acceptance_rate_percent,
    (select count(*) from public.ride_requests r where r.assigned_driver_id = p.user_id and r.state = 'COMPLETED') as trips_completed,
    (select count(*) from public.ride_requests r where r.assigned_driver_id = p.user_id and r.state = 'CANCELLED') as accepted_then_cancelled,
    case when (select count(*) from public.ride_requests r where r.assigned_driver_id = p.user_id and r.state in ('COMPLETED', 'CANCELLED')) = 0 then null else
        round(
            100.0 * (select count(*) from public.ride_requests r where r.assigned_driver_id = p.user_id and r.state = 'COMPLETED') /
            (select count(*) from public.ride_requests r where r.assigned_driver_id = p.user_id and r.state in ('COMPLETED', 'CANCELLED')),
            2
        )
    end as completion_rate_percent
from public.ride_profiles p
where p.user_id = (select auth.uid())
;

grant select on public.ride_driver_performance_v1 to authenticated;
