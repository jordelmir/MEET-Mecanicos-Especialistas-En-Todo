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
 then coalesce((r.fare_breakdown->>'base_fare_minor')::bigint,0)
    + ceil(meters*r.distance_rate_minor_per_km/1000)::bigint
    + ceil(seconds::numeric*r.time_rate_minor_per_minute/60)::bigint else null end;
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
