-- Elysium CRC metered minimum: ₡1000 until validated time and distance
-- exceed ₡1000. The booked estimate and shared provisional meter use the
-- same threshold; neither authorizes a final charge.
-- METERED_MINIMUM_PARITY=CR_GAM|STD_RIDE|2|CRC|1000
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM public.mobility_pricing_policies
     WHERE market_id = 'CR_GAM' AND service_category_id = 'STD_RIDE'
       AND active = TRUE AND version > 2
  ) THEN
    RAISE EXCEPTION 'METERED_MINIMUM_REQUIRES_NEWER_RATE_CARD_REVIEW';
  END IF;
END;
$$;
-- Migration: 20260922090000_crc_ride_pricing_authority_parity.sql
-- Purpose: retire the legacy x100 CRC STD_RIDE seed and make the currently
-- enforced 300 CRC/km + 60 CRC/min contract explicit in the canonical policy
-- table. This does not activate the proposed 900/350/80 pilot rate card.
-- PRICING_PARITY_RATE_CARD=CR_GAM|STD_RIDE|2|CRC|0|300|60|0|0|500

UPDATE public.mobility_pricing_policies
SET active = FALSE,
    valid_until = COALESCE(valid_until, clock_timestamp())
WHERE market_id = 'CR_GAM'
  AND service_category_id = 'STD_RIDE'
  AND active = TRUE;

INSERT INTO public.mobility_pricing_policies (
    market_id,
    service_category_id,
    version,
    currency_code,
    base_fare_minor,
    per_meter_numerator,
    per_meter_denominator,
    per_second_numerator,
    per_second_denominator,
    minimum_fare_minor,
    booking_fee_minor,
    cancellation_fee_minor,
    tax_basis_points,
    surge_max_basis_points,
    active
) VALUES (
    'CR_GAM',
    'STD_RIDE',
    2,
    'CRC',
    0,
    300,
    1000,
    60,
    60,
    0,
    0,
    0,
    0,
    10000,
    TRUE
)
ON CONFLICT (market_id, service_category_id, version) DO UPDATE SET
    currency_code = EXCLUDED.currency_code,
    base_fare_minor = EXCLUDED.base_fare_minor,
    per_meter_numerator = EXCLUDED.per_meter_numerator,
    per_meter_denominator = EXCLUDED.per_meter_denominator,
    per_second_numerator = EXCLUDED.per_second_numerator,
    per_second_denominator = EXCLUDED.per_second_denominator,
    minimum_fare_minor = EXCLUDED.minimum_fare_minor,
    booking_fee_minor = EXCLUDED.booking_fee_minor,
    cancellation_fee_minor = EXCLUDED.cancellation_fee_minor,
    tax_basis_points = EXCLUDED.tax_basis_points,
    surge_max_basis_points = EXCLUDED.surge_max_basis_points,
    valid_from = clock_timestamp(),
    valid_until = NULL,
    active = TRUE;

-- The minimum applies only to METERED_TIME_DISTANCE. OPEN_BID retains its
-- independent negotiated-fare policy and the existing 5% commission.
ALTER TABLE public.mobility_pricing_policies
  ADD COLUMN IF NOT EXISTS metered_minimum_fare_minor bigint NOT NULL DEFAULT 0;
UPDATE public.mobility_pricing_policies
   SET metered_minimum_fare_minor = 1000
 WHERE market_id = 'CR_GAM' AND service_category_id = 'STD_RIDE'
   AND version = 2;

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
        v_expected_fare := greatest(
            1000,
            ceil((p_estimated_distance_meters::numeric * 300) / 1000)::bigint +
            ceil((p_estimated_duration_seconds::numeric * 60) / 60)::bigint
        );
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



-- Participant-only shared measurements. This projection never authorizes a
-- charge or alters the approved quote, completion receipt or constitutional fee.
create or replace function public.ride_shared_meter_snapshot_v1(p_trip_id uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
 r public.ride_requests;
 started timestamptz;
 ended timestamptz;
 as_of timestamptz := clock_timestamp();
 seconds bigint;
 meters numeric;
 gaps bigint;
 accepted bigint;
 last_capture timestamptz;
 measured bigint;
begin
 if auth.uid() is null then raise exception 'AUTH_REQUIRED'; end if;
 select * into r from public.ride_requests where id = p_trip_id;
 if not found or (auth.uid() is distinct from r.passenger_id and auth.uid() is distinct from r.assigned_driver_id) then
   raise exception 'PARTICIPANT_REQUIRED';
 end if;
 select min(created_at) into started from public.ride_trip_events
 where trip_id=p_trip_id and to_state='IN_PROGRESS';
 select min(created_at) into ended from public.ride_trip_events
 where trip_id=p_trip_id and to_state in ('COMPLETED','CANCELLED');
 as_of := least(as_of, coalesce(ended,as_of));
 seconds := case when started is null then null else greatest(0,floor(extract(epoch from as_of-started)))::bigint end;
 with points as (
   select location, recorded_at, accuracy_m, seq,
     lag(location) over(order by recorded_at,seq) prev_location,
     lag(recorded_at) over(order by recorded_at,seq) prev_at,
     lag(accuracy_m) over(order by recorded_at,seq) prev_accuracy
   from public.ride_location_breadcrumbs
   where trip_id=p_trip_id and driver_id=r.assigned_driver_id
     and recorded_at >= started and recorded_at <= as_of
 ), segments as (
   select *, extensions.st_distance(location,prev_location) distance,
     extract(epoch from recorded_at-prev_at) dt from points
 )
 select coalesce(sum(case when dt > 0 and dt <= 30 and accuracy_m <= 50 and prev_accuracy <= 50
     and distance/nullif(dt,0) <= 55 then greatest(0,distance-greatest(accuracy_m,prev_accuracy)) else 0 end),0),
   count(*) filter(where prev_at is not null and (dt <= 0 or dt > 30 or accuracy_m > 50 or prev_accuracy > 50 or distance/nullif(dt,0)>55)),
   max(recorded_at) filter(where dt > 0 and dt <= 30 and accuracy_m <= 50 and prev_accuracy <= 50 and distance/nullif(dt,0) <= 55),
   count(*) filter(where dt > 0 and dt <= 30 and accuracy_m <= 50 and prev_accuracy <= 50 and distance/nullif(dt,0) <= 55)
 into meters,gaps,last_capture,accepted from segments;
 measured := case when r.fare_mode='METERED_TIME_DISTANCE' and started is not null and last_capture is not null
 then greatest(1000,
      coalesce((r.fare_breakdown->>'base_fare_minor')::bigint,0)
      + ceil(meters*r.distance_rate_minor_per_km/1000)::bigint
      + ceil(seconds::numeric*r.time_rate_minor_per_minute/60)::bigint)
    else null end;
 return jsonb_build_object('trip_id',r.id,'server_version',r.version,
   'started_at_ms',case when started is null then null else floor(extract(epoch from started)*1000)::bigint end,
   'server_as_of_ms',floor(extract(epoch from as_of)*1000)::bigint,
   'elapsed_seconds',seconds,'validated_distance_meters',floor(meters)::bigint,
   'last_capture_ms',case when last_capture is null then null else floor(extract(epoch from last_capture)*1000)::bigint end,
   'rejected_segments',gaps,'accepted_segments',accepted,'measured_fare_minor',measured,
   'currency',r.currency,'rate_card_version',r.fare_rate_card_version,
   'is_final',false,'charge_authorized',false);
end; $$;
revoke all on function public.ride_shared_meter_snapshot_v1(uuid) from public,anon;
grant execute on function public.ride_shared_meter_snapshot_v1(uuid) to authenticated;
