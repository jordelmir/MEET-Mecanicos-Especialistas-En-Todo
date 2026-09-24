-- Migration: 20260923010000_ride_create_request_v4_unified_authority.sql
-- Description: PR-1 & PR-2: Unify double authority. Create ride_create_request_v4
-- reading authoritative rates from public.mobility_pricing_policies instead of
-- hardcoding 300/60 rates, supporting dynamic rate cards (v2 baseline and v3 pilot).

begin;

create or replace function public.ride_create_request_v4(
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
    p_preferences jsonb default '{}'::jsonb,
    p_market_id text default 'CR_GAM',
    p_service_category_id text default 'STD_RIDE'
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
    v_policy public.mobility_pricing_policies%rowtype;
    v_expected_dist_rate bigint;
    v_expected_time_rate bigint;
    v_base_fare bigint;
    v_dist_fare bigint;
    v_time_fare bigint;
    v_raw_fare bigint;
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

    -- Lookup authoritative pricing policy for market, category, and rate card version
    select * into v_policy
    from public.mobility_pricing_policies
    where market_id = coalesce(p_market_id, 'CR_GAM')
      and service_category_id = coalesce(p_service_category_id, 'STD_RIDE')
      and (version = p_fare_rate_card_version or (active = true and p_fare_rate_card_version is null))
    order by version desc
    limit 1;

    -- Fallback to active policy if specific version not found
    if not found then
        select * into v_policy
        from public.mobility_pricing_policies
        where market_id = coalesce(p_market_id, 'CR_GAM')
          and service_category_id = coalesce(p_service_category_id, 'STD_RIDE')
          and active = true
        order by version desc
        limit 1;
    end if;

    if not found then
        return public.ride_command_error('POLICY_NOT_FOUND', 'Política tarifaria no configurada para el mercado', false);
    end if;

    -- Compute expected rates from authoritative policy
    v_expected_dist_rate := (v_policy.per_meter_numerator * 1000) / v_policy.per_meter_denominator;
    v_expected_time_rate := (v_policy.per_second_numerator * 60) / v_policy.per_second_denominator;
    v_base_fare := v_policy.base_fare_minor + v_policy.booking_fee_minor;

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
        if p_offered_fare_minor < v_policy.minimum_fare_minor then
            return public.ride_command_error(
                'FARE_BELOW_MINIMUM',
                'La oferta no puede ser inferior a la tarifa mínima (' || v_policy.minimum_fare_minor || ' ' || v_policy.currency_code || ')',
                false
            );
        end if;
        v_expected_fare := p_offered_fare_minor;
    else
        -- Metered time-distance mode: rates must match authoritative policy
        if p_currency <> v_policy.currency_code or
           p_distance_rate_minor_per_km <> v_expected_dist_rate or
           p_time_rate_minor_per_minute <> v_expected_time_rate or
           not coalesce(p_allows_in_trip_stops, false)
        then
            return public.ride_command_error(
                'FARE_POLICY_VIOLATION',
                'Tarifa medida requiere ' || v_expected_dist_rate || ' ' || v_policy.currency_code || '/km y ' || v_expected_time_rate || ' ' || v_policy.currency_code || '/min',
                false
            );
        end if;

        v_dist_fare := ceil((p_estimated_distance_meters::numeric * v_policy.per_meter_numerator) / v_policy.per_meter_denominator)::bigint;
        v_time_fare := ceil((p_estimated_duration_seconds::numeric * v_policy.per_second_numerator) / v_policy.per_second_denominator)::bigint;
        v_raw_fare := v_base_fare + v_dist_fare + v_time_fare;
        v_expected_fare := greatest(v_raw_fare, v_policy.minimum_fare_minor);

        if p_offered_fare_minor <> v_expected_fare then
            return public.ride_command_error(
                'FARE_ESTIMATE_MISMATCH',
                'El estimado no coincide con la tarjeta tarifaria autoritativa (esperado: ' || v_expected_fare || ')',
                false
            );
        end if;
    end if;

    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(v_user_id::text || ':' || p_idempotency_key, 0)
    );
    v_request_hash := public.ride_command_hash(jsonb_build_object(
        'command', 'CREATE_REQUEST_V4',
        'trip_id', p_request_id,
        'fare_mode', p_fare_mode,
        'offered_fare_minor', p_offered_fare_minor,
        'distance_rate_minor_per_km', p_distance_rate_minor_per_km,
        'time_rate_minor_per_minute', p_time_rate_minor_per_minute,
        'estimated_distance_meters', p_estimated_distance_meters,
        'estimated_duration_seconds', p_estimated_duration_seconds,
        'fare_rate_card_version', v_policy.version,
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
           fare_rate_card_version = v_policy.version,
           allows_in_trip_stops = p_allows_in_trip_stops,
           fare_breakdown = fare_breakdown || jsonb_build_object(
               'mode', p_fare_mode,
               'estimated_fare_minor', v_expected_fare,
               'base_fare_minor', v_base_fare,
               'distance_rate_minor_per_km', p_distance_rate_minor_per_km,
               'time_rate_minor_per_minute', p_time_rate_minor_per_minute,
               'estimated_distance_meters', p_estimated_distance_meters,
               'estimated_duration_seconds', p_estimated_duration_seconds,
               'rate_card_version', v_policy.version,
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
        'rate_card_version', v_policy.version,
        'allows_in_trip_stops', p_allows_in_trip_stops,
        'preferences', coalesce(p_preferences, '{}'::jsonb)
    ));
    return public.ride_record_command_receipt(
        v_user_id, p_request_id, 'CREATE_REQUEST', p_idempotency_key,
        v_request_hash, v_response
    );
end;
$$;

revoke all on function public.ride_create_request_v4(
    uuid, text, text, double precision, double precision, text,
    double precision, double precision, text, bigint, text, text, jsonb,
    text, bigint, bigint, bigint, bigint, bigint, boolean, text, jsonb, text, text
) from public;

grant execute on function public.ride_create_request_v4(
    uuid, text, text, double precision, double precision, text,
    double precision, double precision, text, bigint, text, text, jsonb,
    text, bigint, bigint, bigint, bigint, bigint, boolean, text, jsonb, text, text
) to authenticated;

commit;
