-- Elysium Vanguard Viajes: authoritative passenger/driver vertical slice.
-- All mutations are actor-bound, versioned and idempotent. Room remains a
-- projection; PostgreSQL is the only authority for assignment and lifecycle.

alter table public.ride_command_receipts
    drop constraint if exists ride_command_receipts_command_type_check;
alter table public.ride_command_receipts
    add constraint ride_command_receipts_command_type_check check (
        command_type in (
            'CREATE_REQUEST', 'SUBMIT_OFFER', 'ACCEPT_OFFER', 'CLAIM',
            'DRIVER_EN_ROUTE', 'DRIVER_ARRIVED', 'ISSUE_BOARDING_PIN',
            'VERIFY_BOARDING_PIN', 'START', 'CANCEL', 'COMPLETE'
        )
    );

create or replace function public.ride_create_request_v2(
    p_request_id uuid,
    p_display_name text,
    p_country_code text,
    p_pickup_latitude double precision,
    p_pickup_longitude double precision,
    p_pickup_address text,
    p_destination_latitude double precision,
    p_destination_longitude double precision,
    p_destination_address text,
    p_offered_fare_minor bigint,
    p_currency text,
    p_payment_method text,
    p_stops jsonb,
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
    v_request_hash text;
    v_replay jsonb;
    v_response jsonb;
    v_stop jsonb;
    v_stop_count integer;
    v_stop_order integer;
begin
    if v_user_id is null then
        return public.ride_command_error(
            'UNAUTHENTICATED', 'Autenticación requerida', false
        );
    end if;
    if p_request_id is null or
       coalesce(trim(p_display_name), '') = '' or
       char_length(trim(p_display_name)) > 120 or
       coalesce(p_country_code, '') !~ '^[A-Z]{2}$' or
       p_pickup_latitude not between -90 and 90 or
       p_pickup_longitude not between -180 and 180 or
       p_destination_latitude not between -90 and 90 or
       p_destination_longitude not between -180 and 180 or
       char_length(coalesce(p_pickup_address, '')) not between 1 and 500 or
       char_length(coalesce(p_destination_address, '')) not between 1 and 500 or
       coalesce(p_offered_fare_minor, 0) <= 0 or
       coalesce(p_currency, '') !~ '^[A-Z]{3}$' or
       coalesce(p_payment_method, '') not in ('CASH', 'SINPE') or
       coalesce(p_idempotency_key, '') !~ '^[A-Za-z0-9._:-]{16,128}$' or
       jsonb_typeof(coalesce(p_stops, '[]'::jsonb)) <> 'array'
    then
        return public.ride_command_error(
            'VALIDATION_ERROR', 'Solicitud de viaje inválida', false
        );
    end if;

    v_stop_count := jsonb_array_length(coalesce(p_stops, '[]'::jsonb));
    if v_stop_count > 32 then
        return public.ride_command_error(
            'VALIDATION_ERROR', 'Máximo 32 paradas ordenadas', false
        );
    end if;

    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(
            v_user_id::text || ':' || p_idempotency_key,
            0
        )
    );

    v_request_hash := public.ride_command_hash(jsonb_build_object(
        'command', 'CREATE_REQUEST',
        'trip_id', p_request_id,
        'display_name', trim(p_display_name),
        'country_code', p_country_code,
        'pickup_latitude', p_pickup_latitude,
        'pickup_longitude', p_pickup_longitude,
        'pickup_address', trim(p_pickup_address),
        'destination_latitude', p_destination_latitude,
        'destination_longitude', p_destination_longitude,
        'destination_address', trim(p_destination_address),
        'offered_fare_minor', p_offered_fare_minor,
        'currency', p_currency,
        'payment_method', p_payment_method,
        'stops', coalesce(p_stops, '[]'::jsonb)
    ));
    v_replay := public.ride_command_replay(
        v_user_id, p_idempotency_key, v_request_hash
    );
    if v_replay is not null then
        return v_replay;
    end if;

    if exists (
        select 1
          from public.ride_requests r
         where r.id = p_request_id
    ) then
        return public.ride_command_error(
            'RIDE_ID_CONFLICT',
            'El identificador ya pertenece a otra solicitud',
            false
        );
    end if;

    insert into public.ride_profiles(
        user_id, mobility_role, country_code, preferred_currency,
        display_name, updated_at
    )
    values (
        v_user_id, 'PASSENGER', p_country_code, p_currency,
        trim(p_display_name), now()
    )
    on conflict (user_id) do update
       set mobility_role = case
               when public.ride_profiles.mobility_role = 'DRIVER' then 'BOTH'
               else public.ride_profiles.mobility_role
           end,
           country_code = excluded.country_code,
           preferred_currency = excluded.preferred_currency,
           display_name = excluded.display_name,
           updated_at = now();

    insert into public.ride_requests(
        id, passenger_id,
        pickup_latitude, pickup_longitude, pickup_address,
        destination_latitude, destination_longitude, destination_address,
        offered_fare_minor, currency, payment_method, state, version,
        fare_breakdown
    )
    values (
        p_request_id, v_user_id,
        p_pickup_latitude, p_pickup_longitude, trim(p_pickup_address),
        p_destination_latitude, p_destination_longitude,
        trim(p_destination_address),
        p_offered_fare_minor, p_currency, p_payment_method,
        'SEARCHING', 1,
        jsonb_build_object(
            'transport_fare_minor', p_offered_fare_minor,
            'currency', p_currency,
            'source', 'passenger_offer'
        )
    )
    returning * into v_request;

    v_stop_order := 0;
    for v_stop in
        select value
          from jsonb_array_elements(coalesce(p_stops, '[]'::jsonb))
    loop
        v_stop_order := v_stop_order + 1;
        if jsonb_typeof(v_stop) <> 'object' or
           char_length(coalesce(v_stop ->> 'label', '')) not between 1 and 500 or
           coalesce(v_stop ->> 'latitude', '') !~
               '^-?([0-9]+([.][0-9]+)?|[.][0-9]+)$' or
           coalesce(v_stop ->> 'longitude', '') !~
               '^-?([0-9]+([.][0-9]+)?|[.][0-9]+)$' or
           (v_stop ->> 'latitude')::double precision not between -90 and 90 or
           (v_stop ->> 'longitude')::double precision not between -180 and 180
        then
            raise exception using
                errcode = '22023',
                message = 'Invalid ordered stop payload';
        end if;

        insert into public.ride_request_stops(
            request_id, stop_order, provider_place_id, label,
            latitude, longitude, resolved_at
        )
        values (
            p_request_id,
            v_stop_order,
            nullif(trim(v_stop ->> 'providerPlaceId'), ''),
            trim(v_stop ->> 'label'),
            (v_stop ->> 'latitude')::double precision,
            (v_stop ->> 'longitude')::double precision,
            case
                when nullif(trim(v_stop ->> 'providerPlaceId'), '') is null
                    then null
                else now()
            end
        );
    end loop;

    insert into public.ride_trip_events(
        trip_id, actor_id, event_type, from_state, to_state,
        payload, idempotency_key
    )
    values (
        p_request_id, v_user_id, 'REQUEST_PUBLISHED', null, 'SEARCHING',
        jsonb_build_object(
            'version', 1,
            'stop_count', v_stop_count,
            'payment_method', p_payment_method,
            'offered_fare_minor', p_offered_fare_minor,
            'currency', p_currency
        ),
        p_idempotency_key
    );

    v_response := public.ride_command_success(jsonb_build_object(
        'status', 'SEARCHING',
        'trip_id', p_request_id,
        'version', 1,
        'offered_fare_minor', p_offered_fare_minor,
        'currency', p_currency
    ));
    return public.ride_record_command_receipt(
        v_user_id, p_request_id, 'CREATE_REQUEST', p_idempotency_key,
        v_request_hash, v_response
    );
end;
$$;

create or replace function public.ride_submit_offer_v2(
    p_request_id uuid,
    p_offer_id uuid,
    p_vehicle_id uuid,
    p_fare_minor bigint,
    p_currency text,
    p_eta_seconds integer,
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
    v_offer public.ride_offers%rowtype;
    v_request_hash text;
    v_replay jsonb;
    v_response jsonb;
    v_from_state text;
begin
    if v_user_id is null then
        return public.ride_command_error(
            'UNAUTHENTICATED', 'Autenticación requerida', false
        );
    end if;
    if p_offer_id is null or
       coalesce(p_fare_minor, 0) <= 0 or
       coalesce(p_currency, '') !~ '^[A-Z]{3}$' or
       p_eta_seconds is not null and p_eta_seconds not between 0 and 86400 or
       coalesce(p_expected_version, 0) <= 0 or
       coalesce(p_idempotency_key, '') !~ '^[A-Za-z0-9._:-]{16,128}$'
    then
        return public.ride_command_error(
            'VALIDATION_ERROR', 'Oferta inválida', false
        );
    end if;

    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(
            v_user_id::text || ':' || p_idempotency_key,
            0
        )
    );
    v_request_hash := public.ride_command_hash(jsonb_build_object(
        'command', 'SUBMIT_OFFER',
        'trip_id', p_request_id,
        'offer_id', p_offer_id,
        'vehicle_id', p_vehicle_id,
        'fare_minor', p_fare_minor,
        'currency', p_currency,
        'eta_seconds', p_eta_seconds,
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
    if v_request.state not in ('SEARCHING', 'OFFERED') or
       v_request.assigned_driver_id is not null
    then
        return public.ride_command_error(
            'RIDE_NOT_AVAILABLE', 'El viaje ya no acepta ofertas', false
        );
    end if;
    if v_request.version <> p_expected_version then
        return public.ride_command_error(
            'VERSION_CONFLICT', 'La versión del viaje cambió', true,
            jsonb_build_object('current_version', v_request.version)
        );
    end if;
    if v_request.currency <> p_currency then
        return public.ride_command_error(
            'CURRENCY_MISMATCH', 'La moneda de la oferta no coincide', false
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

    insert into public.ride_offers(
        id, request_id, driver_id, vehicle_id, fare_minor,
        currency, eta_seconds, state, updated_at
    )
    values (
        p_offer_id, p_request_id, v_user_id, p_vehicle_id, p_fare_minor,
        p_currency, p_eta_seconds, 'PENDING', now()
    )
    on conflict (request_id, driver_id) do update
       set vehicle_id = excluded.vehicle_id,
           fare_minor = excluded.fare_minor,
           currency = excluded.currency,
           eta_seconds = excluded.eta_seconds,
           state = 'PENDING',
           updated_at = now()
     where public.ride_offers.state in ('PENDING', 'WITHDRAWN')
    returning * into v_offer;
    if not found then
        return public.ride_command_error(
            'OFFER_FINALIZED', 'La oferta ya fue resuelta', false
        );
    end if;

    update public.ride_requests
       set state = 'OFFERED',
           version = version + 1,
           updated_at = now()
     where id = p_request_id
       and version = p_expected_version
    returning * into v_request;
    if not found then
        raise exception using
            errcode = '40001',
            message = 'Concurrent offer invariant violated';
    end if;

    insert into public.ride_trip_events(
        trip_id, actor_id, event_type, from_state, to_state,
        payload, idempotency_key
    )
    values (
        p_request_id, v_user_id, 'DRIVER_OFFER_SUBMITTED',
        v_from_state, 'OFFERED',
        jsonb_build_object(
            'offer_id', v_offer.id,
            'vehicle_id', p_vehicle_id,
            'fare_minor', p_fare_minor,
            'currency', p_currency,
            'eta_seconds', p_eta_seconds,
            'version', v_request.version
        ),
        p_idempotency_key
    );

    v_response := public.ride_command_success(jsonb_build_object(
        'status', 'OFFERED',
        'trip_id', p_request_id,
        'offer_id', v_offer.id,
        'version', v_request.version
    ));
    return public.ride_record_command_receipt(
        v_user_id, p_request_id, 'SUBMIT_OFFER', p_idempotency_key,
        v_request_hash, v_response
    );
end;
$$;

create or replace function public.ride_accept_offer_v2(
    p_request_id uuid,
    p_offer_id uuid,
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
    v_offer public.ride_offers%rowtype;
    v_vehicle public.ride_driver_vehicles%rowtype;
    v_request_hash text;
    v_replay jsonb;
    v_response jsonb;
    v_from_state text;
    v_commission bigint;
    v_posted bigint;
    v_reserved bigint;
    v_quote_version bigint;
begin
    if v_user_id is null then
        return public.ride_command_error(
            'UNAUTHENTICATED', 'Autenticación requerida', false
        );
    end if;
    if coalesce(p_expected_version, 0) <= 0 or
       coalesce(p_idempotency_key, '') !~ '^[A-Za-z0-9._:-]{16,128}$'
    then
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
        'command', 'ACCEPT_OFFER',
        'trip_id', p_request_id,
        'offer_id', p_offer_id,
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
    if v_request.passenger_id <> v_user_id then
        return public.ride_command_error(
            'FORBIDDEN', 'Sólo el pasajero puede aceptar la oferta', false
        );
    end if;
    if v_request.state not in ('SEARCHING', 'OFFERED') or
       v_request.assigned_driver_id is not null
    then
        return public.ride_command_error(
            'ALREADY_ASSIGNED', 'El viaje ya fue asignado', false
        );
    end if;
    if v_request.version <> p_expected_version then
        return public.ride_command_error(
            'VERSION_CONFLICT', 'La versión del viaje cambió', true,
            jsonb_build_object('current_version', v_request.version)
        );
    end if;
    v_from_state := v_request.state;

    select o.*
      into v_offer
      from public.ride_offers o
     where o.id = p_offer_id
       and o.request_id = p_request_id
       and o.state = 'PENDING'
     for update;
    if not found then
        return public.ride_command_error(
            'OFFER_NOT_AVAILABLE', 'La oferta ya no está disponible', false
        );
    end if;
    if v_offer.currency <> v_request.currency then
        return public.ride_command_error(
            'CURRENCY_MISMATCH', 'La moneda de la oferta no coincide', false
        );
    end if;

    select v.*
      into v_vehicle
      from public.ride_driver_vehicles v
     where v.id = v_offer.vehicle_id
       and v.driver_id = v_offer.driver_id
       and v.is_active
       and v.verification_status = 'VERIFIED'
     for update;
    if not found then
        return public.ride_command_error(
            'VEHICLE_NOT_VERIFIED',
            'El vehículo de la oferta ya no está habilitado',
            false
        );
    end if;

    v_commission := round(
        v_offer.fare_minor::numeric * 500::numeric / 10000::numeric
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
     where l.driver_id = v_offer.driver_id
       and l.currency = v_offer.currency;

    select coalesce(sum(r.amount_minor), 0)
      into v_reserved
      from public.ride_commission_reservations r
     where r.driver_id = v_offer.driver_id
       and r.currency = v_offer.currency
       and r.state = 'RESERVED';

    if v_posted - v_reserved < v_commission then
        return public.ride_command_error(
            'INSUFFICIENT_BALANCE',
            'El conductor ya no dispone del saldo requerido',
            false,
            jsonb_build_object(
                'required_minor', v_commission,
                'available_minor', greatest(0, v_posted - v_reserved),
                'currency', v_offer.currency
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
        'ride-commission-v1', 500, v_offer.fare_minor,
        v_commission, 'HALF_UP', v_offer.currency,
        jsonb_build_object(
            'source', 'accepted_driver_offer',
            'offer_id', p_offer_id
        )
    );

    if v_commission > 0 then
        insert into public.ride_commission_reservations(
            trip_id, driver_id, amount_minor, currency, state,
            reserve_idempotency_key
        )
        values (
            p_request_id, v_offer.driver_id, v_commission, v_offer.currency,
            'RESERVED', p_idempotency_key || ':reserve'
        );

        insert into public.ride_wallet_ledger(
            driver_id, idempotency_key, entry_type, amount_minor, currency,
            direction, trip_id, withdrawable, metadata
        )
        values (
            v_offer.driver_id, p_idempotency_key || ':ledger-reserve',
            'COMMISSION_RESERVED', v_commission, v_offer.currency,
            'DEBIT', p_request_id, false,
            jsonb_build_object(
                'commission_policy_version', 'ride-commission-v1',
                'commission_basis_points', 500,
                'commissionable_base_minor', v_offer.fare_minor
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
        p_request_id, v_quote_version, v_offer.currency, v_offer.fare_minor,
        v_offer.driver_id, v_user_id,
        p_idempotency_key || ':accepted-quote', 1
    );

    update public.ride_offers
       set state = case
               when id = p_offer_id then 'ACCEPTED'
               else 'REJECTED'
           end,
           updated_at = now()
     where request_id = p_request_id
       and state = 'PENDING';

    update public.ride_requests
       set assigned_driver_id = v_offer.driver_id,
           assigned_vehicle_id = v_offer.vehicle_id,
           offered_fare_minor = v_offer.fare_minor,
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
            message = 'Concurrent offer acceptance invariant violated';
    end if;

    insert into public.ride_trip_events(
        trip_id, actor_id, event_type, from_state, to_state,
        payload, idempotency_key
    )
    values (
        p_request_id, v_user_id, 'OFFER_ACCEPTED',
        v_from_state, 'ASSIGNED',
        jsonb_build_object(
            'offer_id', p_offer_id,
            'driver_id', v_offer.driver_id,
            'vehicle_id', v_offer.vehicle_id,
            'commission_reserved_minor', v_commission,
            'currency', v_offer.currency,
            'version', v_request.version
        ),
        p_idempotency_key
    );

    v_response := public.ride_command_success(jsonb_build_object(
        'status', 'ASSIGNED',
        'trip_id', p_request_id,
        'version', v_request.version,
        'offer_id', p_offer_id,
        'commission_reserved_minor', v_commission,
        'currency', v_offer.currency
    ));
    return public.ride_record_command_receipt(
        v_user_id, p_request_id, 'ACCEPT_OFFER', p_idempotency_key,
        v_request_hash, v_response
    );
end;
$$;

create or replace function public.ride_driver_transition_v2(
    p_trip_id uuid,
    p_command_type text,
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
    v_request_hash text;
    v_replay jsonb;
    v_response jsonb;
    v_expected_state text;
    v_target_state text;
    v_event_type text;
begin
    if v_user_id is null then
        return public.ride_command_error(
            'UNAUTHENTICATED', 'Autenticación requerida', false
        );
    end if;
    case p_command_type
        when 'DRIVER_EN_ROUTE' then
            v_expected_state := 'ASSIGNED';
            v_target_state := 'DRIVER_EN_ROUTE';
            v_event_type := 'DRIVER_EN_ROUTE';
        when 'DRIVER_ARRIVED' then
            v_expected_state := 'DRIVER_EN_ROUTE';
            v_target_state := 'ARRIVED';
            v_event_type := 'DRIVER_ARRIVED';
        when 'START' then
            v_expected_state := 'PASSENGER_ONBOARD';
            v_target_state := 'IN_PROGRESS';
            v_event_type := 'TRIP_STARTED';
        else
            return public.ride_command_error(
                'VALIDATION_ERROR', 'Transición de conductor inválida', false
            );
    end case;
    if coalesce(p_expected_version, 0) <= 0 or
       coalesce(p_idempotency_key, '') !~ '^[A-Za-z0-9._:-]{16,128}$'
    then
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
        'command', p_command_type,
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
            'FORBIDDEN', 'Sólo el conductor asignado puede avanzar el viaje', false
        );
    end if;
    if v_request.version <> p_expected_version then
        return public.ride_command_error(
            'VERSION_CONFLICT', 'La versión del viaje cambió', true,
            jsonb_build_object('current_version', v_request.version)
        );
    end if;
    if v_request.state <> v_expected_state then
        return public.ride_command_error(
            'INVALID_STATE',
            'El viaje no permite esta transición',
            false,
            jsonb_build_object(
                'current_state', v_request.state,
                'expected_state', v_expected_state
            )
        );
    end if;

    update public.ride_requests
       set state = v_target_state,
           version = version + 1,
           updated_at = now()
     where id = p_trip_id
       and version = p_expected_version
       and state = v_expected_state
    returning * into v_request;
    if not found then
        raise exception using
            errcode = '40001',
            message = 'Concurrent driver transition invariant violated';
    end if;

    insert into public.ride_trip_events(
        trip_id, actor_id, event_type, from_state, to_state,
        payload, idempotency_key
    )
    values (
        p_trip_id, v_user_id, v_event_type,
        v_expected_state, v_target_state,
        jsonb_build_object('version', v_request.version),
        p_idempotency_key
    );

    v_response := public.ride_command_success(jsonb_build_object(
        'status', v_target_state,
        'trip_id', p_trip_id,
        'version', v_request.version
    ));
    return public.ride_record_command_receipt(
        v_user_id, p_trip_id, p_command_type, p_idempotency_key,
        v_request_hash, v_response
    );
end;
$$;

create or replace function public.ride_issue_boarding_pin_v2(
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
    v_request_hash text;
    v_replay jsonb;
    v_response jsonb;
    v_pin text;
    v_random_bytes bytea;
    v_expires_at timestamptz := now() + interval '30 minutes';
begin
    if v_user_id is null then
        return public.ride_command_error(
            'UNAUTHENTICATED', 'Autenticación requerida', false
        );
    end if;
    if coalesce(p_expected_version, 0) <= 0 or
       coalesce(p_idempotency_key, '') !~ '^[A-Za-z0-9._:-]{16,128}$'
    then
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
        'command', 'ISSUE_BOARDING_PIN',
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
    if v_request.passenger_id <> v_user_id then
        return public.ride_command_error(
            'FORBIDDEN', 'Sólo el pasajero puede solicitar el PIN', false
        );
    end if;
    if v_request.version <> p_expected_version then
        return public.ride_command_error(
            'VERSION_CONFLICT', 'La versión del viaje cambió', true,
            jsonb_build_object('current_version', v_request.version)
        );
    end if;
    if v_request.state <> 'ARRIVED' then
        return public.ride_command_error(
            'INVALID_STATE', 'El PIN se habilita cuando el conductor llega', false
        );
    end if;

    v_random_bytes := extensions.gen_random_bytes(2);
    v_pin := lpad((
        (get_byte(v_random_bytes, 0) * 256 +
         get_byte(v_random_bytes, 1)) % 10000
    )::text, 4, '0');

    insert into public.ride_boarding_challenges(
        trip_id, pin_hash, failed_attempts, locked_until, expires_at,
        verified_at, updated_at
    )
    values (
        p_trip_id, extensions.crypt(v_pin, extensions.gen_salt('bf', 8)),
        0, null, v_expires_at, null, now()
    )
    on conflict (trip_id) do update
       set pin_hash = excluded.pin_hash,
           failed_attempts = 0,
           locked_until = null,
           expires_at = excluded.expires_at,
           verified_at = null,
           updated_at = now();

    update public.ride_requests
       set version = version + 1,
           updated_at = now()
     where id = p_trip_id
       and version = p_expected_version
    returning * into v_request;
    if not found then
        raise exception using
            errcode = '40001',
            message = 'Concurrent PIN issue invariant violated';
    end if;

    insert into public.ride_trip_events(
        trip_id, actor_id, event_type, from_state, to_state,
        payload, idempotency_key
    )
    values (
        p_trip_id, v_user_id, 'BOARDING_PIN_ISSUED',
        'ARRIVED', 'ARRIVED',
        jsonb_build_object(
            'expires_at', v_expires_at,
            'version', v_request.version
        ),
        p_idempotency_key
    );

    v_response := public.ride_command_success(jsonb_build_object(
        'status', 'ARRIVED',
        'trip_id', p_trip_id,
        'version', v_request.version,
        'boarding_pin', v_pin,
        'expires_at', v_expires_at
    ));
    return public.ride_record_command_receipt(
        v_user_id, p_trip_id, 'ISSUE_BOARDING_PIN', p_idempotency_key,
        v_request_hash, v_response
    );
end;
$$;

create or replace function public.ride_verify_boarding_pin_v2(
    p_trip_id uuid,
    p_pin text,
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
    v_challenge public.ride_boarding_challenges%rowtype;
    v_request_hash text;
    v_replay jsonb;
    v_response jsonb;
    v_retry_after integer;
begin
    if v_user_id is null then
        return public.ride_command_error(
            'UNAUTHENTICATED', 'Autenticación requerida', false
        );
    end if;
    if coalesce(p_pin, '') !~ '^[0-9]{4}$' or
       coalesce(p_expected_version, 0) <= 0 or
       coalesce(p_idempotency_key, '') !~ '^[A-Za-z0-9._:-]{16,128}$'
    then
        return public.ride_command_error(
            'VALIDATION_ERROR', 'PIN, versión o idempotency key inválida', false
        );
    end if;

    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(
            v_user_id::text || ':' || p_idempotency_key,
            0
        )
    );
    v_request_hash := public.ride_command_hash(jsonb_build_object(
        'command', 'VERIFY_BOARDING_PIN',
        'trip_id', p_trip_id,
        'pin', p_pin,
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
            'FORBIDDEN', 'Sólo el conductor asignado puede validar el PIN', false
        );
    end if;
    if v_request.version <> p_expected_version then
        return public.ride_command_error(
            'VERSION_CONFLICT', 'La versión del viaje cambió', true,
            jsonb_build_object('current_version', v_request.version)
        );
    end if;
    if v_request.state <> 'ARRIVED' then
        return public.ride_command_error(
            'INVALID_STATE', 'El viaje debe estar en ARRIVED', false
        );
    end if;

    select c.*
      into v_challenge
      from public.ride_boarding_challenges c
     where c.trip_id = p_trip_id
     for update;
    if not found or v_challenge.expires_at <= now() or
       v_challenge.verified_at is not null
    then
        v_response := public.ride_command_success(jsonb_build_object(
            'status', 'EXPIRED_OR_USED',
            'trip_id', p_trip_id,
            'version', v_request.version
        ));
        return public.ride_record_command_receipt(
            v_user_id, p_trip_id, 'VERIFY_BOARDING_PIN',
            p_idempotency_key, v_request_hash, v_response
        );
    end if;
    if v_challenge.locked_until is not null and
       v_challenge.locked_until > now()
    then
        v_retry_after := greatest(
            1,
            extract(epoch from (v_challenge.locked_until - now()))::integer
        );
        v_response := public.ride_command_success(jsonb_build_object(
            'status', 'LOCKED',
            'trip_id', p_trip_id,
            'version', v_request.version,
            'retry_after_seconds', v_retry_after
        ));
        return public.ride_record_command_receipt(
            v_user_id, p_trip_id, 'VERIFY_BOARDING_PIN',
            p_idempotency_key, v_request_hash, v_response
        );
    end if;

    if extensions.crypt(p_pin, v_challenge.pin_hash) <>
       v_challenge.pin_hash
    then
        update public.ride_boarding_challenges
           set failed_attempts = failed_attempts + 1,
               locked_until = case
                   when failed_attempts + 1 >= 5
                       then now() + interval '5 minutes'
                   else null
               end,
               updated_at = now()
         where trip_id = p_trip_id;
        v_response := public.ride_command_success(jsonb_build_object(
            'status', 'INVALID',
            'trip_id', p_trip_id,
            'version', v_request.version
        ));
        return public.ride_record_command_receipt(
            v_user_id, p_trip_id, 'VERIFY_BOARDING_PIN',
            p_idempotency_key, v_request_hash, v_response
        );
    end if;

    update public.ride_boarding_challenges
       set verified_at = now(),
           updated_at = now()
     where trip_id = p_trip_id;
    update public.ride_requests
       set state = 'PASSENGER_ONBOARD',
           version = version + 1,
           updated_at = now()
     where id = p_trip_id
       and version = p_expected_version
       and state = 'ARRIVED'
    returning * into v_request;
    if not found then
        raise exception using
            errcode = '40001',
            message = 'Concurrent PIN verification invariant violated';
    end if;

    insert into public.ride_trip_events(
        trip_id, actor_id, event_type, from_state, to_state,
        payload, idempotency_key
    )
    values (
        p_trip_id, v_user_id, 'BOARDING_PIN_VERIFIED',
        'ARRIVED', 'PASSENGER_ONBOARD',
        jsonb_build_object('version', v_request.version),
        p_idempotency_key
    );

    v_response := public.ride_command_success(jsonb_build_object(
        'status', 'PASSENGER_ONBOARD',
        'trip_id', p_trip_id,
        'version', v_request.version
    ));
    return public.ride_record_command_receipt(
        v_user_id, p_trip_id, 'VERIFY_BOARDING_PIN',
        p_idempotency_key, v_request_hash, v_response
    );
end;
$$;

-- Direct inserts were a bootstrap path. From this migration onward all
-- passenger requests and driver offers must pass through the command layer.
revoke insert on public.ride_requests from authenticated;
revoke insert on public.ride_offers from authenticated;

-- Legacy PIN RPCs do not expose expected-version or stable receipts.
revoke execute on function public.ride_create_boarding_pin(uuid, text)
    from authenticated;
revoke execute on function public.ride_verify_boarding_pin(
    uuid, text, text
) from authenticated;

revoke all on function public.ride_create_request_v2(
    uuid, text, text, double precision, double precision, text,
    double precision, double precision, text, bigint, text, text, jsonb, text
) from public;
revoke all on function public.ride_submit_offer_v2(
    uuid, uuid, uuid, bigint, text, integer, bigint, text
) from public;
revoke all on function public.ride_accept_offer_v2(
    uuid, uuid, bigint, text
) from public;
revoke all on function public.ride_driver_transition_v2(
    uuid, text, bigint, text
) from public;
revoke all on function public.ride_issue_boarding_pin_v2(
    uuid, bigint, text
) from public;
revoke all on function public.ride_verify_boarding_pin_v2(
    uuid, text, bigint, text
) from public;

grant execute on function public.ride_create_request_v2(
    uuid, text, text, double precision, double precision, text,
    double precision, double precision, text, bigint, text, text, jsonb, text
) to authenticated;
grant execute on function public.ride_submit_offer_v2(
    uuid, uuid, uuid, bigint, text, integer, bigint, text
) to authenticated;
grant execute on function public.ride_accept_offer_v2(
    uuid, uuid, bigint, text
) to authenticated;
grant execute on function public.ride_driver_transition_v2(
    uuid, text, bigint, text
) to authenticated;
grant execute on function public.ride_issue_boarding_pin_v2(
    uuid, bigint, text
) to authenticated;
grant execute on function public.ride_verify_boarding_pin_v2(
    uuid, text, bigint, text
) to authenticated;

-- Realtime is only a wake-up signal. Every client performs an RLS-protected
-- catch-up query after the signal, so missed websocket events are recoverable.
do $publication$
begin
    if exists (
        select 1
          from pg_catalog.pg_publication p
         where p.pubname = 'supabase_realtime'
    ) and not exists (
        select 1
          from pg_catalog.pg_publication_tables pt
         where pt.pubname = 'supabase_realtime'
           and pt.schemaname = 'public'
           and pt.tablename = 'ride_requests'
    ) then
        execute 'alter publication supabase_realtime add table public.ride_requests';
    end if;
end;
$publication$;
