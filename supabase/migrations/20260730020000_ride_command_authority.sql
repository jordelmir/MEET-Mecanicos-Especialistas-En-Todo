-- Elysium Vanguard Viajes: actor-bound, versioned command authority.
-- Expected business failures return stable JSON codes. Unknown failures are
-- intentionally not swallowed.

create table if not exists public.ride_command_receipts (
    id uuid primary key default gen_random_uuid(),
    actor_id uuid not null references auth.users(id) on delete restrict,
    trip_id uuid references public.ride_requests(id) on delete restrict,
    command_type text not null check (
        command_type in (
            'CLAIM', 'CANCEL', 'COMPLETE', 'ISSUE_BOARDING_PIN',
            'VERIFY_BOARDING_PIN'
        )
    ),
    idempotency_key text not null check (
        char_length(idempotency_key) between 16 and 128 and
        idempotency_key ~ '^[A-Za-z0-9._:-]+$'
    ),
    request_hash text not null check (request_hash ~ '^[a-f0-9]{64}$'),
    response jsonb not null,
    created_at timestamptz not null default now(),
    unique (actor_id, idempotency_key)
);

create index if not exists ride_command_receipts_trip_idx
    on public.ride_command_receipts(trip_id, created_at, id);

create table if not exists public.ride_fare_quotes (
    id uuid primary key default gen_random_uuid(),
    trip_id uuid not null references public.ride_requests(id) on delete restrict,
    quote_version bigint not null check (quote_version > 0),
    currency text not null check (currency ~ '^[A-Z]{3}$'),
    transport_fare_minor bigint not null check (transport_fare_minor >= 0),
    approved_wait_minor bigint not null default 0 check (approved_wait_minor >= 0),
    approved_stops_minor bigint not null default 0 check (approved_stops_minor >= 0),
    approved_surcharges_minor bigint not null default 0
        check (approved_surcharges_minor >= 0),
    collected_cancellation_fee_minor bigint not null default 0
        check (collected_cancellation_fee_minor >= 0),
    driver_funded_discount_minor bigint not null default 0
        check (driver_funded_discount_minor >= 0),
    refunded_transport_minor bigint not null default 0
        check (refunded_transport_minor >= 0),
    tip_minor bigint not null default 0 check (tip_minor >= 0),
    tolls_minor bigint not null default 0 check (tolls_minor >= 0),
    taxes_minor bigint not null default 0 check (taxes_minor >= 0),
    platform_promotion_minor bigint not null default 0
        check (platform_promotion_minor >= 0),
    created_by uuid not null references auth.users(id) on delete restrict,
    accepted_by uuid not null references auth.users(id) on delete restrict,
    supersedes_quote_id uuid references public.ride_fare_quotes(id) on delete restrict,
    idempotency_key text not null unique,
    payload_version integer not null default 1 check (payload_version > 0),
    accepted_at timestamptz not null default now(),
    created_at timestamptz not null default now(),
    unique (trip_id, quote_version)
);

create index if not exists ride_fare_quotes_trip_version_idx
    on public.ride_fare_quotes(trip_id, quote_version desc);

create table if not exists public.ride_operational_holds (
    id uuid primary key default gen_random_uuid(),
    trip_id uuid not null references public.ride_requests(id) on delete restrict,
    hold_type text not null check (
        hold_type in ('SAFETY_REVIEW', 'PAYMENT_REVIEW', 'DISPUTE_REVIEW')
    ),
    reason_code text not null check (char_length(reason_code) between 1 and 120),
    requested_by uuid references auth.users(id) on delete set null,
    source_state text,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

create unique index if not exists ride_one_migrated_safety_hold
    on public.ride_operational_holds(trip_id, reason_code)
    where reason_code = 'MIGRATED_LEGACY_SAFETY_HOLD';

-- Preserve legacy safety holds as auditable operational metadata. If no
-- trustworthy prior active state is present in the event log, use DISPUTED,
-- a safe terminal state, and record that fact.
insert into public.ride_operational_holds(
    trip_id, hold_type, reason_code, requested_by, source_state, metadata
)
select
    r.id,
    'SAFETY_REVIEW',
    'MIGRATED_LEGACY_SAFETY_HOLD',
    null,
    r.state,
    jsonb_build_object(
        'migration', '20260730020000',
        'restoration_policy', 'last_event_from_state_or_disputed'
    )
from public.ride_requests r
where r.state = 'SAFETY_HOLD'
on conflict do nothing;

with restored as (
    select
        r.id,
        coalesce(
            (
                select e.from_state
                from public.ride_trip_events e
                where e.trip_id = r.id
                  and e.to_state = 'SAFETY_HOLD'
                  and e.from_state in (
                      'SEARCHING', 'OFFERED', 'ASSIGNED', 'DRIVER_EN_ROUTE',
                      'ARRIVED', 'PASSENGER_ONBOARD', 'IN_PROGRESS'
                  )
                order by e.id desc
                limit 1
            ),
            'DISPUTED'
        ) as restored_state
    from public.ride_requests r
    where r.state = 'SAFETY_HOLD'
),
updated as (
    update public.ride_requests r
       set state = restored.restored_state,
           version = r.version + 1,
           updated_at = now()
      from restored
     where r.id = restored.id
    returning r.id, restored.restored_state
)
insert into public.ride_trip_events(
    trip_id, actor_id, event_type, from_state, to_state, payload, idempotency_key
)
select
    u.id,
    null,
    'LEGACY_SAFETY_HOLD_MIGRATED',
    'SAFETY_HOLD',
    u.restored_state,
    jsonb_build_object('migration', '20260730020000'),
    'migration:safety-hold:' || u.id::text
from updated u
on conflict (idempotency_key) do nothing;

alter table public.ride_requests
    drop constraint if exists ride_requests_state_check;
alter table public.ride_requests
    add constraint ride_requests_state_check check (
        state in (
            'DRAFT', 'SEARCHING', 'OFFERED', 'ASSIGNED', 'DRIVER_EN_ROUTE',
            'ARRIVED', 'PASSENGER_ONBOARD', 'IN_PROGRESS', 'COMPLETED',
            'CANCELLED', 'EXPIRED', 'DISPUTED'
        )
    );

alter table public.ride_requests
    drop constraint if exists ride_requests_final_fare_minor_check;
alter table public.ride_requests
    add constraint ride_requests_final_fare_minor_check check (
        final_fare_minor is null or final_fare_minor >= 0
    );

-- Existing assigned trips receive a truthful accepted quote derived from the
-- passenger-offered fare already stored on the request.
insert into public.ride_fare_quotes(
    trip_id,
    quote_version,
    currency,
    transport_fare_minor,
    created_by,
    accepted_by,
    idempotency_key,
    payload_version,
    accepted_at
)
select
    r.id,
    1,
    r.currency,
    r.offered_fare_minor,
    r.passenger_id,
    r.passenger_id,
    'migration:accepted-fare:' || r.id::text,
    1,
    r.updated_at
from public.ride_requests r
where r.assigned_driver_id is not null
  and r.state in (
      'ASSIGNED', 'DRIVER_EN_ROUTE', 'ARRIVED', 'PASSENGER_ONBOARD',
      'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'DISPUTED'
  )
on conflict (trip_id, quote_version) do nothing;

create or replace function public.ride_command_error(
    p_code text,
    p_message text,
    p_retryable boolean default false,
    p_details jsonb default '{}'::jsonb
)
returns jsonb
language sql
immutable
security invoker
set search_path = ''
as $$
    select jsonb_build_object(
        'ok', false,
        'error', jsonb_build_object(
            'code', p_code,
            'message', p_message,
            'retryable', p_retryable,
            'details', coalesce(p_details, '{}'::jsonb)
        )
    );
$$;

create or replace function public.ride_command_success(p_data jsonb)
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$
    select jsonb_build_object(
        'ok', true,
        'data', coalesce(p_data, '{}'::jsonb),
        'server_timestamp', now()
    );
$$;

create or replace function public.ride_command_hash(p_payload jsonb)
returns text
language sql
immutable
security invoker
set search_path = ''
as $$
    select encode(
        extensions.digest(
            convert_to(coalesce(p_payload, '{}'::jsonb)::text, 'UTF8'),
            'sha256'
        ),
        'hex'
    );
$$;

create or replace function public.ride_command_replay(
    p_actor_id uuid,
    p_idempotency_key text,
    p_request_hash text
)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_receipt public.ride_command_receipts%rowtype;
begin
    select r.*
      into v_receipt
      from public.ride_command_receipts r
     where r.actor_id = p_actor_id
       and r.idempotency_key = p_idempotency_key;

    if not found then
        return null;
    end if;
    if v_receipt.request_hash <> p_request_hash then
        return public.ride_command_error(
            'IDEMPOTENCY_CONFLICT',
            'La clave ya fue utilizada con otro contenido',
            false
        );
    end if;
    return v_receipt.response;
end;
$$;

create or replace function public.ride_record_command_receipt(
    p_actor_id uuid,
    p_trip_id uuid,
    p_command_type text,
    p_idempotency_key text,
    p_request_hash text,
    p_response jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_existing public.ride_command_receipts%rowtype;
    v_receipt_id uuid := extensions.gen_random_uuid();
    v_recorded_response jsonb := p_response || jsonb_build_object(
        'correlation_id',
        v_receipt_id
    );
begin
    insert into public.ride_command_receipts(
        id, actor_id, trip_id, command_type, idempotency_key,
        request_hash, response
    )
    values (
        v_receipt_id, p_actor_id, p_trip_id, p_command_type,
        p_idempotency_key, p_request_hash, v_recorded_response
    )
    on conflict (actor_id, idempotency_key) do nothing;

    if found then
        return v_recorded_response;
    end if;

    select r.*
      into v_existing
      from public.ride_command_receipts r
     where r.actor_id = p_actor_id
       and r.idempotency_key = p_idempotency_key;

    if v_existing.request_hash <> p_request_hash then
        return public.ride_command_error(
            'IDEMPOTENCY_CONFLICT',
            'La clave ya fue utilizada con otro contenido',
            false
        );
    end if;
    return v_existing.response;
end;
$$;

create or replace function public.ride_claim_request_v2(
    p_request_id uuid,
    p_vehicle_id uuid,
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
    v_vehicle public.ride_driver_vehicles%rowtype;
    v_commission bigint;
    v_posted bigint;
    v_reserved bigint;
    v_request_hash text;
    v_replay jsonb;
    v_response jsonb;
    v_quote_version bigint;
    v_from_state text;
begin
    if v_user_id is null then
        return public.ride_command_error(
            'UNAUTHENTICATED', 'Autenticación requerida', false
        );
    end if;
    if coalesce(p_expected_version, 0) <= 0 or
       coalesce(p_idempotency_key, '') !~ '^[A-Za-z0-9._:-]{16,128}$' then
        return public.ride_command_error(
            'VALIDATION_ERROR', 'Versión o idempotency key inválida', false
        );
    end if;

    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(
            v_user_id::text || ':' || p_idempotency_key,
            0
        )
    );

    v_request_hash := public.ride_command_hash(jsonb_build_object(
        'command', 'CLAIM',
        'trip_id', p_request_id,
        'vehicle_id', p_vehicle_id,
        'expected_version', p_expected_version
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
     where r.id = p_request_id
     for update;

    if not found then
        return public.ride_command_error('NOT_FOUND', 'Viaje no encontrado', false);
    end if;
    if v_request.assigned_driver_id is not null or
       v_request.state not in ('SEARCHING', 'OFFERED') then
        return public.ride_command_error(
            'ALREADY_ASSIGNED', 'Otro conductor obtuvo el viaje', false
        );
    end if;
    if v_request.version <> p_expected_version then
        return public.ride_command_error(
            'VERSION_CONFLICT',
            'La versión del viaje cambió',
            true,
            jsonb_build_object('current_version', v_request.version)
        );
    end if;
    v_from_state := v_request.state;

    select v.*
      into v_vehicle
      from public.ride_driver_vehicles v
     where v.id = p_vehicle_id
       and v.driver_id = v_user_id
       and v.is_active
       and v.verification_status = 'VERIFIED'
     for update;

    if not found then
        return public.ride_command_error(
            'VEHICLE_NOT_VERIFIED',
            'Se requiere un vehículo activo y verificado',
            false
        );
    end if;

    v_commission := round(
        v_request.offered_fare_minor::numeric * 500::numeric / 10000::numeric
    )::bigint;

    select coalesce(sum(
        case
            when l.direction = 'CREDIT' then l.amount_minor
            when l.direction = 'DEBIT' and
                 l.entry_type <> 'COMMISSION_RESERVED' then -l.amount_minor
            else 0
        end
    ), 0)
      into v_posted
      from public.ride_wallet_ledger l
     where l.driver_id = v_user_id
       and l.currency = v_request.currency;

    select coalesce(sum(r.amount_minor), 0)
      into v_reserved
      from public.ride_commission_reservations r
     where r.driver_id = v_user_id
       and r.currency = v_request.currency
       and r.state = 'RESERVED';

    if v_posted - v_reserved < v_commission then
        return public.ride_command_error(
            'INSUFFICIENT_BALANCE',
            'Saldo insuficiente para aceptar un viaje nuevo',
            false,
            jsonb_build_object(
                'required_minor', v_commission,
                'available_minor', greatest(0, v_posted - v_reserved),
                'currency', v_request.currency
            )
        );
    end if;

    insert into public.ride_commission_calculations(
        trip_id, calculation_kind, idempotency_key,
        commission_policy_version, commission_basis_points,
        commissionable_base_minor, commission_amount_minor,
        rounding_mode, currency, metadata
    )
    values (
        p_request_id, 'ESTIMATE', p_idempotency_key || ':estimate',
        'ride-commission-v1', 500, v_request.offered_fare_minor,
        v_commission, 'HALF_UP', v_request.currency,
        jsonb_build_object('source', 'passenger_offered_fare')
    );

    if v_commission > 0 then
        insert into public.ride_commission_reservations(
            trip_id, driver_id, amount_minor, currency, state,
            reserve_idempotency_key
        )
        values (
            p_request_id, v_user_id, v_commission, v_request.currency,
            'RESERVED', p_idempotency_key || ':reserve'
        );

        insert into public.ride_wallet_ledger(
            driver_id, idempotency_key, entry_type, amount_minor, currency,
            direction, trip_id, withdrawable, metadata
        )
        values (
            v_user_id, p_idempotency_key || ':ledger-reserve',
            'COMMISSION_RESERVED', v_commission, v_request.currency,
            'DEBIT', p_request_id, false,
            jsonb_build_object(
                'commission_policy_version', 'ride-commission-v1',
                'commission_basis_points', 500,
                'commissionable_base_minor', v_request.offered_fare_minor
            )
        );
    end if;

    select coalesce(max(q.quote_version), 0) + 1
      into v_quote_version
      from public.ride_fare_quotes q
     where q.trip_id = p_request_id;

    insert into public.ride_fare_quotes(
        trip_id, quote_version, currency, transport_fare_minor,
        created_by, accepted_by, idempotency_key, payload_version
    )
    values (
        p_request_id, v_quote_version, v_request.currency,
        v_request.offered_fare_minor, v_request.passenger_id,
        v_request.passenger_id, p_idempotency_key || ':accepted-quote', 1
    );

    update public.ride_requests
       set assigned_driver_id = v_user_id,
           assigned_vehicle_id = p_vehicle_id,
           state = 'ASSIGNED',
           version = version + 1,
           updated_at = now()
     where id = p_request_id
       and version = p_expected_version
       and assigned_driver_id is null
       and state in ('SEARCHING', 'OFFERED')
    returning * into v_request;

    if not found then
        raise exception using
            errcode = '40001',
            message = 'Concurrent claim invariant violated';
    end if;

    update public.ride_offers
       set state = case
           when driver_id = v_user_id then 'ACCEPTED'
           else 'REJECTED'
       end,
       updated_at = now()
     where request_id = p_request_id
       and state = 'PENDING';

    insert into public.ride_trip_events(
        trip_id, actor_id, event_type, from_state, to_state,
        payload, idempotency_key
    )
    values (
        p_request_id, v_user_id, 'DRIVER_CLAIMED',
        v_from_state, 'ASSIGNED',
        jsonb_build_object(
            'vehicle_id', p_vehicle_id,
            'commission_reserved_minor', v_commission,
            'commission_basis_points', 500,
            'commission_policy_version', 'ride-commission-v1',
            'currency', v_request.currency,
            'version', v_request.version
        ),
        p_idempotency_key
    );

    v_response := public.ride_command_success(jsonb_build_object(
        'status', 'CLAIMED',
        'trip_id', p_request_id,
        'version', v_request.version,
        'commission_reserved_minor', v_commission,
        'currency', v_request.currency
    ));
    return public.ride_record_command_receipt(
        v_user_id, p_request_id, 'CLAIM', p_idempotency_key,
        v_request_hash, v_response
    );
end;
$$;

create or replace function public.ride_cancel_trip_v2(
    p_trip_id uuid,
    p_expected_version bigint,
    p_reason_code text,
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
    v_reservation public.ride_commission_reservations%rowtype;
    v_reason text := upper(trim(p_reason_code));
    v_request_hash text;
    v_replay jsonb;
    v_response jsonb;
    v_safety boolean;
    v_from_state text;
    v_reservation_found boolean := false;
    v_reservation_released boolean := false;
begin
    if v_user_id is null then
        return public.ride_command_error(
            'UNAUTHENTICATED', 'Autenticación requerida', false
        );
    end if;
    if coalesce(p_expected_version, 0) <= 0 or
       coalesce(p_idempotency_key, '') !~ '^[A-Za-z0-9._:-]{16,128}$' or
       coalesce(v_reason, '') not in (
           'SAFETY_CONCERN', 'UNACCOMPANIED_MINOR', 'CHILD_SEAT_REQUIRED',
           'TOO_MANY_PASSENGERS', 'IDENTITY_MISMATCH', 'VEHICLE_MISMATCH',
           'HARASSMENT', 'PROHIBITED_ITEM_OR_ACTIVITY', 'DANGEROUS_PICKUP',
           'MEDICAL_EMERGENCY', 'UNSAFE_VEHICLE_CONDITION',
           'PASSENGER_NO_SHOW', 'DRIVER_NO_SHOW', 'EXCESSIVE_WAIT',
           'INCORRECT_PICKUP', 'INCORRECT_DESTINATION', 'CHANGE_OF_PLANS',
           'DUPLICATE_OR_ACCIDENTAL', 'OTHER'
       ) or
       char_length(coalesce(p_detail, '')) > 500 or
       (v_reason = 'OTHER' and nullif(trim(coalesce(p_detail, '')), '') is null)
    then
        return public.ride_command_error(
            'VALIDATION_ERROR', 'Datos de cancelación inválidos', false
        );
    end if;

    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(
            v_user_id::text || ':' || p_idempotency_key,
            0
        )
    );

    v_request_hash := public.ride_command_hash(jsonb_build_object(
        'command', 'CANCEL',
        'trip_id', p_trip_id,
        'expected_version', p_expected_version,
        'reason_code', v_reason,
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
     for update;

    if not found then
        return public.ride_command_error('NOT_FOUND', 'Viaje no encontrado', false);
    end if;
    if v_user_id <> v_request.passenger_id and
       v_user_id is distinct from v_request.assigned_driver_id then
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
    if v_request.state in ('COMPLETED', 'CANCELLED', 'EXPIRED', 'DISPUTED') then
        return public.ride_command_error(
            'TERMINAL_STATE', 'El viaje está en un estado terminal', false
        );
    end if;
    v_from_state := v_request.state;

    v_safety := v_reason in (
        'SAFETY_CONCERN', 'UNACCOMPANIED_MINOR', 'CHILD_SEAT_REQUIRED',
        'TOO_MANY_PASSENGERS', 'IDENTITY_MISMATCH', 'VEHICLE_MISMATCH',
        'HARASSMENT', 'PROHIBITED_ITEM_OR_ACTIVITY', 'DANGEROUS_PICKUP',
        'MEDICAL_EMERGENCY', 'UNSAFE_VEHICLE_CONDITION'
    );

    insert into public.ride_cancellations(
        trip_id, actor_id, reason_code, detail, requires_safety_review
    )
    values (
        p_trip_id, v_user_id, v_reason,
        nullif(trim(coalesce(p_detail, '')), ''), v_safety
    );

    if v_safety then
        insert into public.ride_operational_holds(
            trip_id, hold_type, reason_code, requested_by, source_state, metadata
        )
        values (
            p_trip_id, 'SAFETY_REVIEW', v_reason, v_user_id, v_from_state,
            jsonb_build_object(
                'source', 'ride_cancel_trip_v2',
                'detail_provided',
                    nullif(trim(coalesce(p_detail, '')), '') is not null
            )
        );
    end if;

    select r.*
      into v_reservation
     from public.ride_commission_reservations r
     where r.trip_id = p_trip_id
     for update;
    v_reservation_found := found;

    if v_reservation_found and v_reservation.state = 'RESERVED' then
        update public.ride_commission_reservations
           set state = 'RELEASED',
               settlement_idempotency_key = p_idempotency_key || ':release',
               settled_at = now()
         where trip_id = p_trip_id;

        insert into public.ride_wallet_ledger(
            driver_id, idempotency_key, entry_type, amount_minor, currency,
            direction, trip_id, withdrawable, metadata
        )
        values (
            v_reservation.driver_id,
            p_idempotency_key || ':ledger-release',
            'COMMISSION_RELEASED',
            v_reservation.amount_minor,
            v_reservation.currency,
            'CREDIT',
            p_trip_id,
            false,
            jsonb_build_object(
                'commission_policy_version', 'ride-commission-v1',
                'reason', 'trip_cancelled'
            )
        );
        v_reservation_released := true;
    end if;

    update public.ride_requests
       set state = 'CANCELLED',
           version = version + 1,
           updated_at = now(),
           cancelled_at = now()
     where id = p_trip_id
       and version = p_expected_version
    returning * into v_request;

    if not found then
        raise exception using
            errcode = '40001',
            message = 'Concurrent cancellation invariant violated';
    end if;

    insert into public.ride_trip_events(
        trip_id, actor_id, event_type, from_state, to_state,
        payload, idempotency_key
    )
    values (
        p_trip_id, v_user_id, 'TRIP_CANCELLED',
        v_from_state, 'CANCELLED',
        jsonb_build_object(
            'reason_code', v_reason,
            'requires_safety_review', v_safety,
            'automatic_fee_minor', 0,
            'version', v_request.version
        ),
        p_idempotency_key
    );

    v_response := public.ride_command_success(jsonb_build_object(
        'status', 'CANCELLED',
        'trip_id', p_trip_id,
        'version', v_request.version,
        'reservation_released', v_reservation_released
    ));
    return public.ride_record_command_receipt(
        v_user_id, p_trip_id, 'CANCEL', p_idempotency_key,
        v_request_hash, v_response
    );
end;
$$;

create or replace function public.ride_complete_trip_v2(
    p_trip_id uuid,
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
    v_quote public.ride_fare_quotes%rowtype;
    v_reservation public.ride_commission_reservations%rowtype;
    v_gross numeric;
    v_reductions numeric;
    v_customer_total_numeric numeric;
    v_base bigint;
    v_customer_total bigint;
    v_commission bigint;
    v_delta bigint;
    v_request_hash text;
    v_replay jsonb;
    v_response jsonb;
    v_reservation_found boolean := false;
begin
    if v_user_id is null then
        return public.ride_command_error(
            'UNAUTHENTICATED', 'Autenticación requerida', false
        );
    end if;
    if coalesce(p_expected_version, 0) <= 0 or
       coalesce(p_idempotency_key, '') !~ '^[A-Za-z0-9._:-]{16,128}$' then
        return public.ride_command_error(
            'VALIDATION_ERROR', 'Versión o idempotency key inválida', false
        );
    end if;

    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(
            v_user_id::text || ':' || p_idempotency_key,
            0
        )
    );

    v_request_hash := public.ride_command_hash(jsonb_build_object(
        'command', 'COMPLETE',
        'trip_id', p_trip_id,
        'expected_version', p_expected_version
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
     for update;

    if not found then
        return public.ride_command_error('NOT_FOUND', 'Viaje no encontrado', false);
    end if;
    if v_request.assigned_driver_id <> v_user_id then
        return public.ride_command_error(
            'FORBIDDEN', 'Se requiere el conductor asignado', false
        );
    end if;
    if v_request.version <> p_expected_version then
        return public.ride_command_error(
            'VERSION_CONFLICT', 'La versión del viaje cambió', true,
            jsonb_build_object('current_version', v_request.version)
        );
    end if;
    if v_request.state <> 'IN_PROGRESS' then
        return public.ride_command_error(
            'INVALID_TRANSITION',
            'El viaje debe estar IN_PROGRESS para completar',
            false
        );
    end if;

    select q.*
      into v_quote
      from public.ride_fare_quotes q
     where q.trip_id = p_trip_id
     order by q.quote_version desc
     limit 1
     for share;

    if not found then
        return public.ride_command_error(
            'FARE_QUOTE_REQUIRED',
            'No existe una tarifa aceptada y versionada',
            false
        );
    end if;
    if v_quote.currency <> v_request.currency then
        return public.ride_command_error(
            'CURRENCY_MISMATCH', 'La moneda de la tarifa no coincide', false
        );
    end if;

    v_gross :=
        v_quote.transport_fare_minor::numeric +
        v_quote.approved_wait_minor::numeric +
        v_quote.approved_stops_minor::numeric +
        v_quote.approved_surcharges_minor::numeric +
        v_quote.collected_cancellation_fee_minor::numeric;
    v_reductions :=
        v_quote.driver_funded_discount_minor::numeric +
        v_quote.refunded_transport_minor::numeric;
    v_customer_total_numeric := greatest(
        0::numeric,
        v_gross +
        v_quote.tip_minor::numeric +
        v_quote.tolls_minor::numeric +
        v_quote.taxes_minor::numeric -
        v_reductions -
        v_quote.platform_promotion_minor::numeric
    );

    if v_gross > 9223372036854775807::numeric or
       v_reductions > 9223372036854775807::numeric or
       v_customer_total_numeric > 9223372036854775807::numeric then
        return public.ride_command_error(
            'AMOUNT_OVERFLOW', 'Los importes exceden el rango permitido', false
        );
    end if;

    v_base := greatest(0::numeric, v_gross - v_reductions)::bigint;
    v_customer_total := v_customer_total_numeric::bigint;
    v_commission := round(
        v_base::numeric * 500::numeric / 10000::numeric
    )::bigint;

    select r.*
      into v_reservation
      from public.ride_commission_reservations r
     where r.trip_id = p_trip_id
     for update;
    v_reservation_found := found;

    if v_reservation_found and
       (
           v_reservation.driver_id <> v_user_id or
           v_reservation.currency <> v_request.currency
       )
    then
        raise exception using
            errcode = '23514',
            message = 'Commission reservation ownership invariant violated';
    end if;
    if v_reservation_found and v_reservation.state <> 'RESERVED' then
        return public.ride_command_error(
            'ALREADY_SETTLED', 'La comisión ya fue liquidada', false
        );
    end if;

    insert into public.ride_commission_calculations(
        trip_id, calculation_kind, idempotency_key,
        commission_policy_version, commission_basis_points,
        commissionable_base_minor, commission_amount_minor,
        rounding_mode, currency, settled_at, metadata
    )
    values (
        p_trip_id, 'FINAL', p_idempotency_key || ':final-calculation',
        'ride-commission-v1', 500, v_base, v_commission,
        'HALF_UP', v_request.currency, now(),
        jsonb_build_object(
            'quote_id', v_quote.id,
            'quote_version', v_quote.quote_version,
            'tip_minor_excluded', v_quote.tip_minor,
            'tolls_minor_excluded', v_quote.tolls_minor,
            'taxes_minor_excluded', v_quote.taxes_minor,
            'platform_promotion_minor_excluded',
                v_quote.platform_promotion_minor
        )
    );

    if v_commission > 0 then
        if not v_reservation_found then
            insert into public.ride_commission_reservations(
                trip_id, driver_id, amount_minor, currency, state,
                reserve_idempotency_key
            )
            values (
                p_trip_id, v_user_id, v_commission, v_request.currency,
                'RESERVED', p_idempotency_key || ':late-reserve'
            )
            returning * into v_reservation;

            insert into public.ride_wallet_ledger(
                driver_id, idempotency_key, entry_type, amount_minor,
                currency, direction, trip_id, withdrawable, metadata
            )
            values (
                v_user_id, p_idempotency_key || ':ledger-late-reserve',
                'COMMISSION_RESERVED', v_commission, v_request.currency,
                'DEBIT', p_trip_id, false,
                jsonb_build_object(
                    'commission_policy_version', 'ride-commission-v1',
                    'commissionable_base_minor', v_base
                )
            );
        elsif v_reservation.amount_minor <> v_commission then
            v_delta := abs(v_reservation.amount_minor - v_commission);
            insert into public.ride_wallet_ledger(
                driver_id, idempotency_key, entry_type, amount_minor,
                currency, direction, trip_id, withdrawable, metadata
            )
            values (
                v_user_id,
                p_idempotency_key || case
                    when v_commission > v_reservation.amount_minor
                        then ':ledger-reserve-increase'
                    else ':ledger-reserve-release'
                end,
                case
                    when v_commission > v_reservation.amount_minor
                        then 'COMMISSION_RESERVED'
                    else 'COMMISSION_RELEASED'
                end,
                v_delta,
                v_request.currency,
                case
                    when v_commission > v_reservation.amount_minor
                        then 'DEBIT'
                    else 'CREDIT'
                end,
                p_trip_id,
                false,
                jsonb_build_object(
                    'commission_policy_version', 'ride-commission-v1',
                    'commissionable_base_minor', v_base,
                    'adjustment', true
                )
            );
        end if;

        update public.ride_commission_reservations
           set amount_minor = v_commission,
               state = 'CAPTURED',
               settlement_idempotency_key = p_idempotency_key || ':capture',
               settled_at = now()
         where trip_id = p_trip_id;

        insert into public.ride_wallet_ledger(
            driver_id, idempotency_key, entry_type, amount_minor, currency,
            direction, trip_id, withdrawable, metadata
        )
        values (
            v_user_id, p_idempotency_key || ':ledger-capture',
            'COMMISSION_CAPTURED', v_commission, v_request.currency,
            'DEBIT', p_trip_id, false,
            jsonb_build_object(
                'commission_policy_version', 'ride-commission-v1',
                'commission_basis_points', 500,
                'commissionable_base_minor', v_base,
                'quote_id', v_quote.id
            )
        );
    elsif v_reservation_found and v_reservation.state = 'RESERVED' then
        update public.ride_commission_reservations
           set state = 'RELEASED',
               settlement_idempotency_key = p_idempotency_key || ':release',
               settled_at = now()
         where trip_id = p_trip_id;

        insert into public.ride_wallet_ledger(
            driver_id, idempotency_key, entry_type, amount_minor, currency,
            direction, trip_id, withdrawable, metadata
        )
        values (
            v_user_id, p_idempotency_key || ':ledger-release',
            'COMMISSION_RELEASED', v_reservation.amount_minor,
            v_request.currency, 'CREDIT', p_trip_id, false,
            jsonb_build_object('reason', 'final_commission_zero')
        );
    end if;

    update public.ride_requests
       set state = 'COMPLETED',
           final_fare_minor = v_customer_total,
           version = version + 1,
           updated_at = now(),
           completed_at = now()
     where id = p_trip_id
       and version = p_expected_version
    returning * into v_request;

    if not found then
        raise exception using
            errcode = '40001',
            message = 'Concurrent completion invariant violated';
    end if;

    insert into public.ride_trip_events(
        trip_id, actor_id, event_type, from_state, to_state,
        payload, idempotency_key
    )
    values (
        p_trip_id, v_user_id, 'TRIP_COMPLETED',
        'IN_PROGRESS', 'COMPLETED',
        jsonb_build_object(
            'quote_id', v_quote.id,
            'quote_version', v_quote.quote_version,
            'customer_total_minor', v_customer_total,
            'commissionable_base_minor', v_base,
            'commission_minor', v_commission,
            'commission_basis_points', 500,
            'commission_policy_version', 'ride-commission-v1',
            'currency', v_request.currency,
            'version', v_request.version
        ),
        p_idempotency_key
    );

    v_response := public.ride_command_success(jsonb_build_object(
        'status', 'COMPLETED',
        'trip_id', p_trip_id,
        'version', v_request.version,
        'customer_total_minor', v_customer_total,
        'commissionable_base_minor', v_base,
        'commission_minor', v_commission,
        'commission_basis_points', 500,
        'currency', v_request.currency
    ));
    return public.ride_record_command_receipt(
        v_user_id, p_trip_id, 'COMPLETE', p_idempotency_key,
        v_request_hash, v_response
    );
end;
$$;

drop trigger if exists ride_command_receipts_immutable
    on public.ride_command_receipts;
create trigger ride_command_receipts_immutable
before update or delete on public.ride_command_receipts
for each row execute function public.ride_reject_immutable_change();

drop trigger if exists ride_fare_quotes_immutable
    on public.ride_fare_quotes;
create trigger ride_fare_quotes_immutable
before update or delete on public.ride_fare_quotes
for each row execute function public.ride_reject_immutable_change();

drop trigger if exists ride_operational_holds_immutable
    on public.ride_operational_holds;
create trigger ride_operational_holds_immutable
before update or delete on public.ride_operational_holds
for each row execute function public.ride_reject_immutable_change();

alter table public.ride_command_receipts enable row level security;
alter table public.ride_fare_quotes enable row level security;
alter table public.ride_operational_holds enable row level security;

drop policy if exists ride_command_receipts_actor_select
    on public.ride_command_receipts;
create policy ride_command_receipts_actor_select
on public.ride_command_receipts for select to authenticated
using (actor_id = (select auth.uid()));

drop policy if exists ride_fare_quotes_participant_select
    on public.ride_fare_quotes;
create policy ride_fare_quotes_participant_select
on public.ride_fare_quotes for select to authenticated
using (public.ride_is_participant(trip_id));

drop policy if exists ride_operational_holds_participant_select
    on public.ride_operational_holds;
create policy ride_operational_holds_participant_select
on public.ride_operational_holds for select to authenticated
using (public.ride_is_participant(trip_id));

revoke all on public.ride_command_receipts from anon, authenticated;
revoke all on public.ride_fare_quotes from anon, authenticated;
revoke all on public.ride_operational_holds from anon, authenticated;
grant select on public.ride_command_receipts to authenticated;
grant select on public.ride_fare_quotes to authenticated;
grant select on public.ride_operational_holds to authenticated;

-- Disable legacy commercial commands. Current Android did not consume these
-- RPCs; v2 is the only authenticated mutation boundary after this migration.
revoke execute on function public.ride_claim_request(uuid, uuid, text)
    from authenticated;
revoke execute on function public.ride_accept_offer(uuid, uuid, text)
    from authenticated;
revoke execute on function public.ride_cancel_trip(uuid, text, text, text)
    from authenticated;
revoke execute on function public.ride_complete_trip(uuid, bigint, text)
    from authenticated;

revoke all on function public.ride_command_error(text, text, boolean, jsonb)
    from public;
revoke all on function public.ride_command_success(jsonb) from public;
revoke all on function public.ride_command_hash(jsonb) from public;
revoke all on function public.ride_command_replay(uuid, text, text)
    from public;
revoke all on function public.ride_record_command_receipt(
    uuid, uuid, text, text, text, jsonb
) from public;
revoke all on function public.ride_claim_request_v2(uuid, uuid, bigint, text)
    from public;
revoke all on function public.ride_cancel_trip_v2(
    uuid, bigint, text, text, text
) from public;
revoke all on function public.ride_complete_trip_v2(uuid, bigint, text)
    from public;

grant execute on function public.ride_claim_request_v2(
    uuid, uuid, bigint, text
) to authenticated;
grant execute on function public.ride_cancel_trip_v2(
    uuid, bigint, text, text, text
) to authenticated;
grant execute on function public.ride_complete_trip_v2(
    uuid, bigint, text
) to authenticated;
