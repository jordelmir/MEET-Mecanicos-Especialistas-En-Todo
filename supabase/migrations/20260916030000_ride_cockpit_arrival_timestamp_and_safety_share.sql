-- =============================================================================
-- Migration: 20260916030000_ride_cockpit_arrival_timestamp_and_safety_share.sql
-- Module: MEET Vanguard Active Trip Cockpit
-- Purpose:
--   1. Add driver_arrived_at timestamptz to ride_requests for authoritative waiting time.
--   2. Update ride_driver_arrived_v3 to record driver_arrived_at with statement_timestamp().
--   3. Create ride_share_sessions for cryptographic, privacy-safe trip tracking links.
-- =============================================================================

alter table public.ride_requests
    add column if not exists driver_arrived_at timestamptz;

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
       set state = 'ARRIVED',
           version = version + 1,
           driver_arrived_at = coalesce(driver_arrived_at, statement_timestamp()),
           updated_at = now()
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
            'version', v_request.version,
            'driver_arrived_at', v_request.driver_arrived_at
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
        'distance_meters', round(v_distance_meters::numeric, 1),
        'driver_arrived_at', v_request.driver_arrived_at
    ));
    return public.ride_record_command_receipt(
        v_user_id, p_trip_id, 'DRIVER_ARRIVED', p_idempotency_key,
        v_request_hash, v_response
    );
end;
$$;

revoke all on function public.ride_driver_arrived_v3(
    uuid, double precision, double precision, real, timestamptz, bigint, text
) from public;

grant execute on function public.ride_driver_arrived_v3(
    uuid, double precision, double precision, real, timestamptz, bigint, text
) to authenticated;

-- =============================================================================
-- Table: ride_share_sessions
-- Cryptographically safe tracking sessions using 256-bit CSPRNG token hash.
-- The raw token is NEVER persisted in the database.
-- =============================================================================

create table if not exists public.ride_share_sessions (
    id uuid primary key default gen_random_uuid(),
    ride_id uuid not null references public.ride_requests(id) on delete cascade,
    owner_user_id uuid not null,
    token_hash bytea not null unique,
    created_at timestamptz not null default now(),
    expires_at timestamptz not null,
    revoked_at timestamptz
);

create index if not exists idx_ride_share_sessions_token_hash on public.ride_share_sessions(token_hash);
create index if not exists idx_ride_share_sessions_ride_id on public.ride_share_sessions(ride_id);

alter table public.ride_share_sessions enable row level security;

create policy ride_share_sessions_owner_all
on public.ride_share_sessions for all to authenticated
using (owner_user_id = (select auth.uid()))
with check (owner_user_id = (select auth.uid()));

create policy ride_share_sessions_public_token_select
on public.ride_share_sessions for select to anon, authenticated
using (revoked_at is null and expires_at > now());
