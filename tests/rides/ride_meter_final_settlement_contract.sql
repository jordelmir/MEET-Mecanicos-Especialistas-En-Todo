do $$
declare
 driver uuid := '00000000-0000-0000-0000-000000000002';
 rider uuid := '00000000-0000-0000-0000-000000000001';
 trip uuid := '00000000-0000-0000-0000-000000000003';
 empty_trip uuid := '00000000-0000-0000-0000-000000000004';
 failed_trip uuid := '00000000-0000-0000-0000-000000000005';
 result jsonb;
 rollback_seen boolean := false;
begin
 perform set_config('test.uid',driver::text,true);
 insert into public.ride_requests(id,passenger_id,assigned_driver_id,fare_mode,state,version,currency,
 distance_rate_minor_per_km,time_rate_minor_per_minute)
 values(trip,rider,driver,'METERED_TIME_DISTANCE','IN_PROGRESS',1,'CRC',1000,6000),
       (empty_trip,rider,driver,'METERED_TIME_DISTANCE','IN_PROGRESS',1,'CRC',1000,6000),
       (failed_trip,rider,driver,'METERED_TIME_DISTANCE','IN_PROGRESS',1,'CRC',1000,6000);
 insert into public.ride_fare_quotes(trip_id,quote_version,currency,transport_fare_minor,
 created_by,accepted_by,idempotency_key)
 values(trip,1,'CRC',1000,driver,rider,'first-quote-00001'),
       (empty_trip,1,'CRC',1000,driver,rider,'first-quote-00002'),
       (failed_trip,1,'CRC',1000,driver,rider,'first-quote-00003');
 insert into public.ride_trip_events(trip_id,to_state,created_at)
 values(trip,'IN_PROGRESS',now()-interval '20 seconds'),
       (empty_trip,'IN_PROGRESS',now()-interval '20 seconds'),
       (failed_trip,'IN_PROGRESS',now()-interval '20 seconds');
 insert into public.ride_location_breadcrumbs(trip_id,driver_id,location,recorded_at,accuracy_m,seq)
 values(trip,driver,'A',now()-interval '10 seconds',1,1),
       (trip,driver,'B',now()-interval '5 seconds',1,2),
       (failed_trip,driver,'A',now()-interval '10 seconds',1,1),
       (failed_trip,driver,'B',now()-interval '5 seconds',1,2);

 result := public.ride_complete_trip_v2(empty_trip,1,'meter-empty-00000001');
 if result->'error'->>'code' <> 'METER_EVIDENCE_PENDING' or
    (select count(*) from public.ride_fare_quotes where trip_id=empty_trip) <> 1 or
    (select state from public.ride_requests where id=empty_trip) <> 'IN_PROGRESS' then
   raise exception 'missing GPS must fail without writing a final quote';
 end if;
 begin
   perform public.ride_complete_trip_v2(failed_trip,1,'meter-failed-00000001');
 exception when others then
   rollback_seen := sqlerrm like 'METER_COMPLETION_ROLLED_BACK:%';
 end;
 if not rollback_seen or
    (select count(*) from public.ride_fare_quotes where trip_id=failed_trip) <> 1 or
    (select state from public.ride_requests where id=failed_trip) <> 'IN_PROGRESS' then
   raise exception 'downstream failure left an immutable meter quote behind';
 end if;
 result := public.ride_complete_trip_v2(trip,1,'meter-valid-00000001');
 if result->>'ok' <> 'true' then raise exception 'valid meter completion failed: %',result; end if;
 if (select count(*) from public.ride_fare_quotes where trip_id=trip) <> 2 or
    (select transport_fare_minor from public.ride_fare_quotes where trip_id=trip order by quote_version desc limit 1) <= 1000 or
    (select settlement_source from public.ride_fare_quotes where trip_id=trip order by quote_version desc limit 1) <> 'SERVER_METER' or
    (select final_fare_minor from public.ride_requests where id=trip) < 1000 then
   raise exception 'server meter was not used as the final fare';
 end if;
 result := public.ride_shared_meter_snapshot_v1(trip);
 if result->>'is_final' <> 'true' or
    (result->>'final_fare_minor')::bigint <> (select final_fare_minor from public.ride_requests where id=trip) then
   raise exception 'passenger and driver projection lacks confirmed final total';
 end if;
end;
$$;
