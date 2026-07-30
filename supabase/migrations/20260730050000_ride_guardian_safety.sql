-- Elysium Guardian: participant-bound, idempotent safety signaling.
-- No authority, emergency service or trusted contact is contacted by this
-- migration. Integrations require an approved provider and an explicit user
-- action.

alter table public.ride_command_receipts
    drop constraint if exists ride_command_receipts_command_type_check;
alter table public.ride_command_receipts
    add constraint ride_command_receipts_command_type_check check (
        command_type in (
            'CREATE_REQUEST', 'SUBMIT_OFFER', 'ACCEPT_OFFER', 'CLAIM',
            'DRIVER_EN_ROUTE', 'DRIVER_ARRIVED', 'ISSUE_BOARDING_PIN',
            'VERIFY_BOARDING_PIN', 'START', 'CANCEL', 'COMPLETE',
            'ENROLL_DRIVER_PILOT', 'SAFETY_SIGNAL'
        )
    );

create table if not exists public.ride_safety_events (
    id uuid primary key default extensions.gen_random_uuid(),
    trip_id uuid not null references public.ride_requests(id) on delete restrict,
    actor_id uuid not null references auth.users(id) on delete restrict,
    signal_type text not null check (
        signal_type in (
            'SOS', 'CHECK_IN_REQUEST', 'ROUTE_DEVIATION', 'LONG_STOP',
            'POSSIBLE_COLLISION', 'SIGNAL_LOSS', 'VEHICLE_MISMATCH',
            'PERSON_MISMATCH', 'HARASSMENT', 'MEDICAL_CONCERN'
        )
    ),
    severity text not null check (severity in ('CHECK_IN', 'URGENT', 'CRITICAL')),
    status text not null default 'OPEN' check (status = 'OPEN'),
    detail_provided boolean not null default false,
    authorities_contacted boolean not null default false
        check (authorities_contacted = false),
    idempotency_key text not null check (
        char_length(idempotency_key) between 16 and 128 and
        idempotency_key ~ '^[A-Za-z0-9._:-]+$'
    ),
    created_at timestamptz not null default now(),
    unique (actor_id, idempotency_key)
);

create index if not exists ride_safety_events_trip_created_idx
    on public.ride_safety_events(trip_id, created_at desc);

create or replace function public.ride_signal_safety_v2(
    p_trip_id uuid,
    p_expected_version bigint,
    p_signal_type text,
    p_detail text,
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
    v_signal text := upper(trim(coalesce(p_signal_type, '')));
    v_severity text;
    v_request_hash text;
    v_replay jsonb;
    v_response jsonb;
    v_event_id uuid;
begin
    if v_user_id is null then
        return public.ride_command_error(
            'UNAUTHENTICATED', 'Autenticación requerida', false
        );
    end if;
    if coalesce(p_expected_version, 0) <= 0 or
       coalesce(p_idempotency_key, '') !~ '^[A-Za-z0-9._:-]{16,128}$' or
       v_signal not in (
           'SOS', 'CHECK_IN_REQUEST', 'ROUTE_DEVIATION', 'LONG_STOP',
           'POSSIBLE_COLLISION', 'SIGNAL_LOSS', 'VEHICLE_MISMATCH',
           'PERSON_MISMATCH', 'HARASSMENT', 'MEDICAL_CONCERN'
       ) or
       char_length(coalesce(p_detail, '')) > 500
    then
        return public.ride_command_error(
            'VALIDATION_ERROR', 'Señal de seguridad inválida', false
        );
    end if;

    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(
            v_user_id::text || ':' || p_idempotency_key,
            0
        )
    );

    v_request_hash := public.ride_command_hash(jsonb_build_object(
        'command', 'SAFETY_SIGNAL',
        'trip_id', p_trip_id,
        'expected_version', p_expected_version,
        'signal_type', v_signal,
        'detail', nullif(trim(coalesce(p_detail, '')), '')
    ));
    v_replay := public.ride_command_replay(
        v_user_id, p_idempotency_key, v_request_hash
    );
    if v_replay is not null then
        return v_replay;
    end if;

    select r.*
      into v_request
      from public.ride_requests r
     where r.id = p_trip_id
     for share;

    if not found then
        return public.ride_command_error('NOT_FOUND', 'Viaje no encontrado', false);
    end if;
    if v_user_id <> v_request.passenger_id and
       v_user_id is distinct from v_request.assigned_driver_id
    then
        return public.ride_command_error(
            'FORBIDDEN', 'Actor no autorizado para este viaje', false
        );
    end if;
    if v_request.version <> p_expected_version then
        return public.ride_command_error(
            'VERSION_CONFLICT', 'La versión del viaje cambió', true,
            jsonb_build_object('current_version', v_request.version)
        );
    end if;
    if v_request.state not in (
        'ASSIGNED', 'DRIVER_EN_ROUTE', 'ARRIVED',
        'PASSENGER_ONBOARD', 'IN_PROGRESS'
    ) then
        return public.ride_command_error(
            'INVALID_STATE',
            'Guardian solo se activa durante un viaje asignado o en curso',
            false
        );
    end if;

    v_severity := case
        when v_signal in ('SOS', 'POSSIBLE_COLLISION', 'MEDICAL_CONCERN')
            then 'CRITICAL'
        when v_signal in (
            'VEHICLE_MISMATCH', 'PERSON_MISMATCH', 'HARASSMENT',
            'ROUTE_DEVIATION'
        ) then 'URGENT'
        else 'CHECK_IN'
    end;

    insert into public.ride_safety_events(
        trip_id, actor_id, signal_type, severity, detail_provided,
        idempotency_key
    )
    values (
        p_trip_id, v_user_id, v_signal, v_severity,
        nullif(trim(coalesce(p_detail, '')), '') is not null,
        p_idempotency_key
    )
    returning id into v_event_id;

    insert into public.ride_operational_holds(
        trip_id, hold_type, reason_code, requested_by, source_state, metadata
    )
    values (
        p_trip_id, 'SAFETY_REVIEW', v_signal, v_user_id, v_request.state,
        jsonb_build_object(
            'source', 'ride_signal_safety_v2',
            'safety_event_id', v_event_id,
            'severity', v_severity,
            'authorities_contacted', false
        )
    );

    insert into public.ride_trip_events(
        trip_id, actor_id, event_type, from_state, to_state,
        payload, idempotency_key
    )
    values (
        p_trip_id, v_user_id, 'SAFETY_SIGNAL_RECORDED',
        v_request.state, v_request.state,
        jsonb_build_object(
            'safety_event_id', v_event_id,
            'signal_type', v_signal,
            'severity', v_severity,
            'authorities_contacted', false,
            'version', v_request.version
        ),
        p_idempotency_key
    );

    v_response := public.ride_command_success(jsonb_build_object(
        'status', 'SAFETY_SIGNAL_RECORDED',
        'trip_id', p_trip_id,
        'version', v_request.version,
        'safety_event_id', v_event_id,
        'severity', v_severity,
        'authorities_contacted', false
    ));
    return public.ride_record_command_receipt(
        v_user_id, p_trip_id, 'SAFETY_SIGNAL', p_idempotency_key,
        v_request_hash, v_response
    );
end;
$$;

drop trigger if exists ride_safety_events_immutable
    on public.ride_safety_events;
create trigger ride_safety_events_immutable
before update or delete on public.ride_safety_events
for each row execute function public.ride_reject_immutable_change();

alter table public.ride_safety_events enable row level security;

drop policy if exists ride_safety_events_participant_select
    on public.ride_safety_events;
create policy ride_safety_events_participant_select
on public.ride_safety_events for select to authenticated
using (public.ride_is_participant(trip_id));

revoke all on public.ride_safety_events from anon, authenticated;
grant select on public.ride_safety_events to authenticated;

revoke all on function public.ride_signal_safety_v2(
    uuid, bigint, text, text, text
) from public;
grant execute on function public.ride_signal_safety_v2(
    uuid, bigint, text, text, text
) to authenticated;

comment on table public.ride_safety_events is
    'Append-only Guardian signals. Does not imply emergency dispatch or authority contact.';
comment on column public.ride_safety_events.authorities_contacted is
    'Always false until a separately approved, explicit integration exists.';
