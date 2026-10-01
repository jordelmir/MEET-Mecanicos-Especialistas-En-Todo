-- Preserve the existing completion/commission implementation for accepted fixed quotes.
-- Metered rides add an immutable server measurement quote immediately before that
-- implementation settles the trip in the same transaction.
alter function public.ride_complete_trip_v2(uuid,bigint,text)
    rename to ride_complete_trip_from_quote_v2;
revoke all on function public.ride_complete_trip_from_quote_v2(uuid,bigint,text)
    from public, anon, authenticated;

alter table public.ride_fare_quotes
    add column if not exists settlement_source text not null default 'ACCEPTED_QUOTE',
    add column if not exists meter_evidence jsonb;
alter table public.ride_fare_quotes
    drop constraint if exists ride_fare_quotes_settlement_source_check;
alter table public.ride_fare_quotes
    add constraint ride_fare_quotes_settlement_source_check
    check (settlement_source in ('ACCEPTED_QUOTE','SERVER_METER'));

create or replace function public.ride_complete_trip_v2(
    p_trip_id uuid,
    p_expected_version bigint,
    p_idempotency_key text
)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
    v_actor uuid := (select auth.uid());
    v_trip public.ride_requests%rowtype;
    v_quote public.ride_fare_quotes%rowtype;
    v_meter jsonb;
    v_amount bigint;
    v_last_capture_ms bigint;
    v_as_of_ms bigint;
    v_accepted bigint;
    v_rejected bigint;
    v_commission bigint;
    v_existing_reserve bigint;
    v_wallet jsonb;
    v_result jsonb;
begin
    if v_actor is null then
        return public.ride_command_error('UNAUTHENTICATED','Autenticación requerida',false);
    end if;
    if coalesce(p_expected_version,0) <= 0 or
       coalesce(p_idempotency_key,'') !~ '^[A-Za-z0-9._:-]{16,128}$' then
        return public.ride_complete_trip_from_quote_v2(p_trip_id,p_expected_version,p_idempotency_key);
    end if;

    select * into v_trip from public.ride_requests where id=p_trip_id for update;
    if not found or v_trip.assigned_driver_id is distinct from v_actor or
       v_trip.fare_mode is distinct from 'METERED_TIME_DISTANCE' or
       v_trip.state is distinct from 'IN_PROGRESS' or
       v_trip.version is distinct from p_expected_version then
        return public.ride_complete_trip_from_quote_v2(p_trip_id,p_expected_version,p_idempotency_key);
    end if;

    -- Realtime is only a wake-up. This function reads durable server breadcrumbs.
    v_meter := public.ride_shared_meter_snapshot_v1(p_trip_id);
    v_amount := (v_meter->>'measured_fare_minor')::bigint;
    v_last_capture_ms := (v_meter->>'last_capture_ms')::bigint;
    v_as_of_ms := (v_meter->>'server_as_of_ms')::bigint;
    v_accepted := coalesce((v_meter->>'accepted_segments')::bigint,0);
    v_rejected := coalesce((v_meter->>'rejected_segments')::bigint,0);
    if v_amount is null or v_amount < 1000 or v_last_capture_ms is null or
       v_as_of_ms-v_last_capture_ms > 30000 or v_accepted < 1 or
       v_rejected * 5 > v_accepted then
        return public.ride_command_error(
            'METER_EVIDENCE_PENDING',
            'La María requiere puntos GPS recientes y válidos antes del cobro final',
            true
        );
    end if;

    select * into v_quote from public.ride_fare_quotes
    where trip_id=p_trip_id order by quote_version desc limit 1 for share;
    if not found then
        return public.ride_command_error('METER_QUOTE_MISSING','Falta la tarifa aceptada',false);
    end if;
    if v_quote.currency is distinct from v_trip.currency or
       v_quote.settlement_source = 'SERVER_METER' then
        return public.ride_command_error('METER_QUOTE_INVALID','La tarifa previa no es válida',false);
    end if;

    if (v_amount::numeric + v_quote.approved_stops_minor +
        v_quote.approved_surcharges_minor + v_quote.collected_cancellation_fee_minor) >
        9223372036854775807::numeric then
        return public.ride_command_error('AMOUNT_OVERFLOW','La tarifa excede el rango permitido',false);
    end if;
    v_commission := round(greatest(0::numeric,
        v_amount::numeric + v_quote.approved_stops_minor +
        v_quote.approved_surcharges_minor + v_quote.collected_cancellation_fee_minor -
        v_quote.driver_funded_discount_minor - v_quote.refunded_transport_minor
    ) * 500 / 10000)::bigint;
    select coalesce(amount_minor,0) into v_existing_reserve
    from public.ride_commission_reservations where trip_id=p_trip_id for update;
    v_existing_reserve := coalesce(v_existing_reserve,0);
    v_wallet := public.elysium_provider_balance_v1(v_actor);
    if (v_wallet->>'available_minor')::bigint < greatest(0,v_commission-v_existing_reserve) then
        return public.ride_command_error(
            'PROVIDER_COMMISSION_BALANCE_REQUIRED',
            'El proveedor debe cubrir la diferencia de comisión antes del cierre',true
        );
    end if;

    insert into public.ride_fare_quotes(
        trip_id,quote_version,currency,transport_fare_minor,
        approved_wait_minor,approved_stops_minor,approved_surcharges_minor,
        collected_cancellation_fee_minor,driver_funded_discount_minor,
        refunded_transport_minor,tip_minor,tolls_minor,taxes_minor,
        platform_promotion_minor,created_by,accepted_by,supersedes_quote_id,
        idempotency_key,payload_version,settlement_source,meter_evidence
    ) values (
        p_trip_id,v_quote.quote_version+1,v_quote.currency,v_amount,
        0,v_quote.approved_stops_minor,v_quote.approved_surcharges_minor,
        v_quote.collected_cancellation_fee_minor,v_quote.driver_funded_discount_minor,
        v_quote.refunded_transport_minor,v_quote.tip_minor,v_quote.tolls_minor,
        v_quote.taxes_minor,v_quote.platform_promotion_minor,
        v_actor,v_trip.passenger_id,v_quote.id,
        p_idempotency_key||':server-meter',2,'SERVER_METER',v_meter
    );

    -- The existing RPC commits the final fare, five-percent commission,
    -- receipt, event, and authoritative version together with the meter quote.
    v_result := public.ride_complete_trip_from_quote_v2(
        p_trip_id,p_expected_version,p_idempotency_key
    );
    if coalesce((v_result->>'ok')::boolean,false) is not true then
        -- The existing RPC returns structured failures. Raising here rolls back
        -- the new immutable meter quote with every downstream side effect.
        raise exception 'METER_COMPLETION_ROLLED_BACK: %',
            coalesce(v_result->'error'->>'code','UNKNOWN') using errcode='P0001';
    end if;
    return v_result;
end;
$$;

revoke all on function public.ride_complete_trip_v2(uuid,bigint,text)
    from public, anon;
grant execute on function public.ride_complete_trip_v2(uuid,bigint,text)
    to authenticated;

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
 select * into r from public.ride_requests where id=p_trip_id;
 if not found or (auth.uid() is distinct from r.passenger_id and
                  auth.uid() is distinct from r.assigned_driver_id) then
   raise exception 'PARTICIPANT_REQUIRED';
 end if;
 select min(created_at) into started from public.ride_trip_events
 where trip_id=p_trip_id and to_state='IN_PROGRESS';
 select min(created_at) into ended from public.ride_trip_events
 where trip_id=p_trip_id and to_state in ('COMPLETED','CANCELLED');
 as_of := least(as_of,coalesce(ended,as_of));
 seconds := case when started is null then null else
   greatest(0,floor(extract(epoch from as_of-started)))::bigint end;
 with points as (
   select location,recorded_at,accuracy_m,seq,
     lag(location) over(order by recorded_at,seq) prev_location,
     lag(recorded_at) over(order by recorded_at,seq) prev_at,
     lag(accuracy_m) over(order by recorded_at,seq) prev_accuracy
   from public.ride_location_breadcrumbs
   where trip_id=p_trip_id and driver_id=r.assigned_driver_id
     and recorded_at>=started and recorded_at<=as_of
 ), segments as (
   select *,extensions.st_distance(location,prev_location) distance,
     extract(epoch from recorded_at-prev_at) dt from points
 )
 select coalesce(sum(case when dt>0 and dt<=30 and accuracy_m<=50 and
     prev_accuracy<=50 and distance/nullif(dt,0)<=55 then
     greatest(0,distance-greatest(accuracy_m,prev_accuracy)) else 0 end),0),
   count(*) filter(where prev_at is not null and
     (dt<=0 or dt>30 or accuracy_m>50 or prev_accuracy>50 or
      distance/nullif(dt,0)>55)),
   max(recorded_at) filter(where dt>0 and dt<=30 and accuracy_m<=50 and
     prev_accuracy<=50 and distance/nullif(dt,0)<=55),
   count(*) filter(where dt>0 and dt<=30 and accuracy_m<=50 and
     prev_accuracy<=50 and distance/nullif(dt,0)<=55)
 into meters,gaps,last_capture,accepted from segments;
 measured := case when r.fare_mode='METERED_TIME_DISTANCE' and
   started is not null and last_capture is not null then greatest(1000,
      coalesce((r.fare_breakdown->>'base_fare_minor')::bigint,0)
      + ceil(meters*r.distance_rate_minor_per_km/1000)::bigint
      + ceil(seconds::numeric*r.time_rate_minor_per_minute/60)::bigint)
   else null end;
 return jsonb_build_object(
   'trip_id',r.id,'server_version',r.version,
   'started_at_ms',case when started is null then null else floor(extract(epoch from started)*1000)::bigint end,
   'server_as_of_ms',floor(extract(epoch from as_of)*1000)::bigint,
   'elapsed_seconds',seconds,'validated_distance_meters',floor(meters)::bigint,
   'last_capture_ms',case when last_capture is null then null else floor(extract(epoch from last_capture)*1000)::bigint end,
   'rejected_segments',gaps,'accepted_segments',accepted,'measured_fare_minor',measured,
   'final_fare_minor',case when r.state='COMPLETED' then r.final_fare_minor else null end,
   'currency',r.currency,'rate_card_version',r.fare_rate_card_version,
   'is_final',r.state='COMPLETED' and r.final_fare_minor is not null,
   'charge_authorized',false
 );
end;
$$;
revoke all on function public.ride_shared_meter_snapshot_v1(uuid)
    from public,anon;
grant execute on function public.ride_shared_meter_snapshot_v1(uuid)
    to authenticated;
