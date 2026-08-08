-- MEET Viajes V5: explicit fare modes and immutable rate-card snapshots.
-- PostgreSQL is authoritative. Android may preview this formula, but may not
-- choose or mutate the final fare after publishing.

alter table public.ride_command_receipts
    drop constraint if exists ride_command_receipts_command_type_check;
alter table public.ride_command_receipts
    add constraint ride_command_receipts_command_type_check check (
        command_type in (
            'CREATE_REQUEST', 'SUBMIT_OFFER', 'ACCEPT_OFFER', 'CLAIM',
            'DRIVER_EN_ROUTE', 'DRIVER_ARRIVED', 'ISSUE_BOARDING_PIN',
            'VERIFY_BOARDING_PIN', 'START', 'CANCEL', 'COMPLETE',
            'ENROLL_DRIVER_PILOT', 'SAFETY_SIGNAL', 'OPEN_SUPPORT_CASE',
            'UPDATE_ROUTE'
        )
    );

alter table public.ride_requests
    add column if not exists fare_mode text not null default 'OPEN_BID',
    add column if not exists distance_rate_minor_per_km bigint not null default 0,
    add column if not exists time_rate_minor_per_minute bigint not null default 0,
    add column if not exists estimated_distance_meters bigint not null default 0,
    add column if not exists estimated_duration_seconds bigint not null default 0,
    add column if not exists estimated_fare_minor bigint not null default 0,
    add column if not exists fare_rate_card_version bigint not null default 1,
    add column if not exists allows_in_trip_stops boolean not null default false;

alter table public.ride_requests
    drop constraint if exists ride_requests_fare_mode_check,
    add constraint ride_requests_fare_mode_check check (
        fare_mode in ('OPEN_BID', 'METERED_TIME_DISTANCE')
    ),
    drop constraint if exists ride_requests_metered_values_check,
    add constraint ride_requests_metered_values_check check (
        distance_rate_minor_per_km >= 0 and
        time_rate_minor_per_minute >= 0 and
        estimated_distance_meters >= 0 and
        estimated_duration_seconds >= 0 and
        estimated_fare_minor >= 0 and
        fare_rate_card_version > 0 and
        (
            (fare_mode = 'OPEN_BID' and
             distance_rate_minor_per_km = 0 and
             time_rate_minor_per_minute = 0 and
             allows_in_trip_stops = false)
            or
            (fare_mode = 'METERED_TIME_DISTANCE' and
             currency = 'CRC' and
             distance_rate_minor_per_km = 300 and
             time_rate_minor_per_minute = 60 and
             allows_in_trip_stops = true)
        )
    );

create or replace function public.ride_create_request_v3(
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
    p_fare_mode text,
    p_distance_rate_minor_per_km bigint,
    p_time_rate_minor_per_minute bigint,
    p_estimated_distance_meters bigint,
    p_estimated_duration_seconds bigint,
    p_fare_rate_card_version bigint,
    p_allows_in_trip_stops boolean,
    p_idempotency_key text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := (select auth.uid());
    v_request_hash text;
    v_replay jsonb;
    v_base jsonb;
    v_response jsonb;
    v_expected_fare bigint;
    v_child_key text := 'v2:' || public.ride_command_hash(to_jsonb(p_idempotency_key));
begin
    if v_user_id is null then
        return public.ride_command_error('UNAUTHENTICATED', 'Autenticación requerida', false);
    end if;
    if coalesce(p_fare_mode, '') not in ('OPEN_BID', 'METERED_TIME_DISTANCE') or
       coalesce(p_estimated_distance_meters, -1) < 0 or
       coalesce(p_estimated_duration_seconds, -1) < 0 or
       coalesce(p_fare_rate_card_version, 0) <= 0
    then
        return public.ride_command_error('VALIDATION_ERROR', 'Contrato tarifario inválido', false);
    end if;

    if p_fare_mode = 'OPEN_BID' then
        if coalesce(p_distance_rate_minor_per_km, -1) <> 0 or
           coalesce(p_time_rate_minor_per_minute, -1) <> 0 or
           coalesce(p_allows_in_trip_stops, true)
        then
            return public.ride_command_error(
                'FARE_POLICY_VIOLATION',
                'Pon tu precio solo admite paradas declaradas antes de publicar',
                false
            );
        end if;
        v_expected_fare := p_offered_fare_minor;
    else
        if p_currency <> 'CRC' or
           p_distance_rate_minor_per_km <> 300 or
           p_time_rate_minor_per_minute <> 60 or
           not coalesce(p_allows_in_trip_stops, false)
        then
            return public.ride_command_error(
                'FARE_POLICY_VIOLATION',
                'La tarifa medida CRC requiere ₡300/km y ₡60/min',
                false
            );
        end if;
        v_expected_fare :=
            ceil((p_estimated_distance_meters::numeric * 300) / 1000)::bigint +
            ceil((p_estimated_duration_seconds::numeric * 60) / 60)::bigint;
        if p_offered_fare_minor <> v_expected_fare then
            return public.ride_command_error(
                'FARE_ESTIMATE_MISMATCH',
                'El estimado no coincide con la tarjeta tarifaria',
                false
            );
        end if;
    end if;

    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(v_user_id::text || ':' || p_idempotency_key, 0)
    );
    v_request_hash := public.ride_command_hash(jsonb_build_object(
        'command', 'CREATE_REQUEST_V3',
        'trip_id', p_request_id,
        'fare_mode', p_fare_mode,
        'offered_fare_minor', p_offered_fare_minor,
        'distance_rate_minor_per_km', p_distance_rate_minor_per_km,
        'time_rate_minor_per_minute', p_time_rate_minor_per_minute,
        'estimated_distance_meters', p_estimated_distance_meters,
        'estimated_duration_seconds', p_estimated_duration_seconds,
        'fare_rate_card_version', p_fare_rate_card_version,
        'allows_in_trip_stops', p_allows_in_trip_stops,
        'stops', coalesce(p_stops, '[]'::jsonb)
    ));
    v_replay := public.ride_command_replay(v_user_id, p_idempotency_key, v_request_hash);
    if v_replay is not null then return v_replay; end if;

    v_base := public.ride_create_request_v2(
        p_request_id, p_display_name, p_country_code,
        p_pickup_latitude, p_pickup_longitude, p_pickup_address,
        p_destination_latitude, p_destination_longitude, p_destination_address,
        p_offered_fare_minor, p_currency, p_payment_method, p_stops, v_child_key
    );
    if not coalesce((v_base ->> 'ok')::boolean, false) then return v_base; end if;

    update public.ride_requests
       set fare_mode = p_fare_mode,
           distance_rate_minor_per_km = p_distance_rate_minor_per_km,
           time_rate_minor_per_minute = p_time_rate_minor_per_minute,
           estimated_distance_meters = p_estimated_distance_meters,
           estimated_duration_seconds = p_estimated_duration_seconds,
           estimated_fare_minor = v_expected_fare,
           fare_rate_card_version = p_fare_rate_card_version,
           allows_in_trip_stops = p_allows_in_trip_stops,
           fare_breakdown = fare_breakdown || jsonb_build_object(
               'mode', p_fare_mode,
               'estimated_fare_minor', v_expected_fare,
               'distance_rate_minor_per_km', p_distance_rate_minor_per_km,
               'time_rate_minor_per_minute', p_time_rate_minor_per_minute,
               'estimated_distance_meters', p_estimated_distance_meters,
               'estimated_duration_seconds', p_estimated_duration_seconds,
               'rate_card_version', p_fare_rate_card_version,
               'is_estimate', p_fare_mode = 'METERED_TIME_DISTANCE'
           )
     where id = p_request_id and passenger_id = v_user_id;

    v_response := public.ride_command_success(jsonb_build_object(
        'status', 'SEARCHING',
        'trip_id', p_request_id,
        'version', 1,
        'offered_fare_minor', p_offered_fare_minor,
        'estimated_fare_minor', v_expected_fare,
        'currency', p_currency,
        'fare_mode', p_fare_mode,
        'allows_in_trip_stops', p_allows_in_trip_stops
    ));
    return public.ride_record_command_receipt(
        v_user_id, p_request_id, 'CREATE_REQUEST', p_idempotency_key,
        v_request_hash, v_response
    );
end;
$$;

revoke all on function public.ride_create_request_v3(
    uuid, text, text, double precision, double precision, text,
    double precision, double precision, text, bigint, text, text, jsonb,
    text, bigint, bigint, bigint, bigint, bigint, boolean, text
) from public;
grant execute on function public.ride_create_request_v3(
    uuid, text, text, double precision, double precision, text,
    double precision, double precision, text, bigint, text, text, jsonb,
    text, bigint, bigint, bigint, bigint, bigint, boolean, text
) to authenticated;

create or replace function public.ride_replace_stops_v3(
    p_trip_id uuid,
    p_stops jsonb,
    p_estimated_distance_meters bigint,
    p_estimated_duration_seconds bigint,
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
    v_hash text;
    v_replay jsonb;
    v_stop jsonb;
    v_order integer := 0;
    v_estimated_fare bigint;
    v_response jsonb;
begin
    if v_user_id is null then
        return public.ride_command_error('UNAUTHENTICATED', 'Autenticación requerida', false);
    end if;
    if p_trip_id is null or jsonb_typeof(coalesce(p_stops, '[]'::jsonb)) <> 'array' or
       jsonb_array_length(coalesce(p_stops, '[]'::jsonb)) > 8 or
       coalesce(p_estimated_distance_meters, -1) < 0 or
       coalesce(p_estimated_duration_seconds, -1) < 0 or
       coalesce(p_idempotency_key, '') !~ '^[A-Za-z0-9._:-]{16,128}$'
    then
        return public.ride_command_error('VALIDATION_ERROR', 'Cambio de paradas inválido', false);
    end if;

    perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(p_trip_id::text, 0));
    select * into v_request from public.ride_requests where id = p_trip_id for update;
    if not found then return public.ride_command_error('NOT_FOUND', 'Viaje no encontrado', false); end if;
    if v_request.passenger_id <> v_user_id then
        return public.ride_command_error('FORBIDDEN', 'Solo el pasajero puede cambiar paradas', false);
    end if;
    if v_request.version <> p_expected_version then
        return public.ride_command_error(
            'STALE_VERSION', 'El viaje cambió; sincroniza antes de reintentar', true,
            jsonb_build_object('current_version', v_request.version)
        );
    end if;
    if v_request.fare_mode <> 'METERED_TIME_DISTANCE' or not v_request.allows_in_trip_stops then
        return public.ride_command_error(
            'FARE_POLICY_VIOLATION',
            'Pon tu precio no admite paradas después de publicar',
            false
        );
    end if;
    if v_request.state not in (
        'SEARCHING', 'OFFERED', 'ASSIGNED', 'DRIVER_EN_ROUTE', 'ARRIVED',
        'PASSENGER_ONBOARD', 'IN_PROGRESS'
    ) then
        return public.ride_command_error('INVALID_STATE', 'El viaje ya no admite cambios de ruta', false);
    end if;

    v_hash := public.ride_command_hash(jsonb_build_object(
        'command', 'UPDATE_ROUTE', 'trip_id', p_trip_id,
        'expected_version', p_expected_version, 'stops', coalesce(p_stops, '[]'::jsonb),
        'estimated_distance_meters', p_estimated_distance_meters,
        'estimated_duration_seconds', p_estimated_duration_seconds
    ));
    v_replay := public.ride_command_replay(v_user_id, p_idempotency_key, v_hash);
    if v_replay is not null then return v_replay; end if;

    delete from public.ride_request_stops where request_id = p_trip_id;
    for v_stop in select value from jsonb_array_elements(coalesce(p_stops, '[]'::jsonb))
    loop
        v_order := v_order + 1;
        if jsonb_typeof(v_stop) <> 'object' or
           char_length(coalesce(v_stop ->> 'label', '')) not between 1 and 500 or
           coalesce(v_stop ->> 'latitude', '') !~ '^-?([0-9]+([.][0-9]+)?|[.][0-9]+)$' or
           coalesce(v_stop ->> 'longitude', '') !~ '^-?([0-9]+([.][0-9]+)?|[.][0-9]+)$' or
           (v_stop ->> 'latitude')::double precision not between -90 and 90 or
           (v_stop ->> 'longitude')::double precision not between -180 and 180
        then
            raise exception using errcode = '22023', message = 'Invalid ordered stop payload';
        end if;
        insert into public.ride_request_stops(
            request_id, stop_order, provider_place_id, label, latitude, longitude, resolved_at
        ) values (
            p_trip_id, v_order, nullif(trim(v_stop ->> 'providerPlaceId'), ''),
            trim(v_stop ->> 'label'), (v_stop ->> 'latitude')::double precision,
            (v_stop ->> 'longitude')::double precision, now()
        );
    end loop;

    v_estimated_fare :=
        ceil((p_estimated_distance_meters::numeric * v_request.distance_rate_minor_per_km) / 1000)::bigint +
        ceil((p_estimated_duration_seconds::numeric * v_request.time_rate_minor_per_minute) / 60)::bigint;
    update public.ride_requests
       set estimated_distance_meters = p_estimated_distance_meters,
           estimated_duration_seconds = p_estimated_duration_seconds,
           estimated_fare_minor = v_estimated_fare,
           offered_fare_minor = v_estimated_fare,
           quote_version = quote_version + 1,
           version = version + 1,
           fare_breakdown = fare_breakdown || jsonb_build_object(
               'estimated_fare_minor', v_estimated_fare,
               'estimated_distance_meters', p_estimated_distance_meters,
               'estimated_duration_seconds', p_estimated_duration_seconds,
               'is_estimate', true
           ),
           updated_at = now()
     where id = p_trip_id
     returning * into v_request;

    insert into public.ride_trip_events(
        trip_id, actor_id, event_type, from_state, to_state, payload, idempotency_key
    ) values (
        p_trip_id, v_user_id, 'ROUTE_STOPS_CHANGED', v_request.state, v_request.state,
        jsonb_build_object(
            'version', v_request.version, 'quote_version', v_request.quote_version,
            'stop_count', v_order, 'estimated_fare_minor', v_estimated_fare
        ), p_idempotency_key
    );
    v_response := public.ride_command_success(jsonb_build_object(
        'status', v_request.state, 'trip_id', p_trip_id, 'version', v_request.version,
        'quote_version', v_request.quote_version,
        'estimated_fare_minor', v_estimated_fare, 'customer_total_minor', null
    ));
    return public.ride_record_command_receipt(
        v_user_id, p_trip_id, 'UPDATE_ROUTE', p_idempotency_key, v_hash, v_response
    );
end;
$$;

revoke all on function public.ride_replace_stops_v3(uuid, jsonb, bigint, bigint, bigint, text) from public;
grant execute on function public.ride_replace_stops_v3(uuid, jsonb, bigint, bigint, bigint, text) to authenticated;
