-- Migration: 20260918020000_ride_create_request_preferences_support.sql
-- Description: Add passenger preferences support (pets, kids, 5-passenger spacious)
-- to ride_create_request_v3 and ride_create_guest_request_v1, preserving them in fare_breakdown.

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
    p_idempotency_key text,
    p_preferences jsonb default '{}'::jsonb
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
        'stops', coalesce(p_stops, '[]'::jsonb),
        'preferences', coalesce(p_preferences, '{}'::jsonb)
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
               'is_estimate', p_fare_mode = 'METERED_TIME_DISTANCE',
               'preferences', coalesce(p_preferences, '{}'::jsonb)
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
        'allows_in_trip_stops', p_allows_in_trip_stops,
        'preferences', coalesce(p_preferences, '{}'::jsonb)
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
    text, bigint, bigint, bigint, bigint, bigint, boolean, text, jsonb
) from public;

grant execute on function public.ride_create_request_v3(
    uuid, text, text, double precision, double precision, text,
    double precision, double precision, text, bigint, text, text, jsonb,
    text, bigint, bigint, bigint, bigint, bigint, boolean, text, jsonb
) to authenticated;


-- Also update ride_create_guest_request_v1 to accept and forward p_preferences
create or replace function public.ride_create_guest_request_v1(
    p_request_id uuid,
    p_display_name text,
    p_guest_name text,
    p_guest_phone_e164 text,
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
    p_idempotency_key text,
    p_preferences jsonb default '{}'::jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_actor uuid := (select auth.uid());
    v_base jsonb;
    v_profile public.guest_ride_profiles%rowtype;
    v_tracking_token text;
begin
    if v_actor is null then
        return public.ride_command_error('UNAUTHENTICATED', 'Autenticación requerida', false);
    end if;
    if char_length(trim(coalesce(p_guest_name, ''))) not between 2 and 120 or
       coalesce(p_guest_phone_e164, '') !~ '^\+[1-9][0-9]{7,14}$'
    then
        return public.ride_command_error(
            'INVALID_GUEST_IDENTITY',
            'Nombre y teléfono internacional del pasajero requeridos',
            false
        );
    end if;

    v_base := public.ride_create_request_v3(
        p_request_id, p_display_name, p_country_code,
        p_pickup_latitude, p_pickup_longitude, p_pickup_address,
        p_destination_latitude, p_destination_longitude, p_destination_address,
        p_offered_fare_minor, p_currency, p_payment_method, p_stops,
        p_fare_mode, p_distance_rate_minor_per_km,
        p_time_rate_minor_per_minute, p_estimated_distance_meters,
        p_estimated_duration_seconds, p_fare_rate_card_version,
        p_allows_in_trip_stops, 'guest-base:' || p_idempotency_key,
        p_preferences
    );
    if not coalesce((v_base ->> 'ok')::boolean, false) then
        return v_base;
    end if;

    v_tracking_token := encode(
        extensions.digest(
            p_request_id::text || ':' || v_actor::text || ':' || p_idempotency_key,
            'sha256'
        ),
        'hex'
    );
    insert into public.guest_ride_profiles(
        ride_request_id, requested_by_rider_id, guest_name,
        guest_phone_e164, sms_notifications_enabled, tracking_token
    ) values (
        p_request_id, v_actor, trim(p_guest_name),
        p_guest_phone_e164, true, v_tracking_token
    )
    on conflict (ride_request_id) do nothing
    returning * into v_profile;

    if v_profile.guest_ride_id is null then
        select * into v_profile
          from public.guest_ride_profiles
         where ride_request_id = p_request_id
           and requested_by_rider_id = v_actor
           and guest_name = trim(p_guest_name)
           and guest_phone_e164 = p_guest_phone_e164;
        if not found then
            return public.ride_command_error(
                'GUEST_REQUEST_CONFLICT',
                'La identidad del pasajero no coincide con la solicitud existente',
                false
            );
        end if;
    end if;

    return v_base || jsonb_build_object(
        'guest_ride_id', v_profile.guest_ride_id,
        'guest_name', v_profile.guest_name
    );
end;
$$;

revoke all on function public.ride_create_guest_request_v1(
    uuid, text, text, text, text, double precision, double precision, text,
    double precision, double precision, text, bigint, text, text, jsonb,
    text, bigint, bigint, bigint, bigint, bigint, boolean, text, jsonb
) from public;

grant execute on function public.ride_create_guest_request_v1(
    uuid, text, text, text, text, double precision, double precision, text,
    double precision, double precision, text, bigint, text, text, jsonb,
    text, bigint, bigint, bigint, bigint, bigint, boolean, text, jsonb
) to authenticated;
