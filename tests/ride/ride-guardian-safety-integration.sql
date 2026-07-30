begin;

insert into auth.users(id) values
    ('91000000-0000-0000-0000-000000000001'),
    ('91000000-0000-0000-0000-000000000002'),
    ('91000000-0000-0000-0000-000000000003');

insert into public.ride_profiles(
    user_id, mobility_role, country_code, preferred_currency, display_name
) values
    ('91000000-0000-0000-0000-000000000001', 'PASSENGER', 'CR', 'CRC', 'Guardian passenger'),
    ('91000000-0000-0000-0000-000000000002', 'DRIVER', 'CR', 'CRC', 'Guardian driver'),
    ('91000000-0000-0000-0000-000000000003', 'PASSENGER', 'CR', 'CRC', 'Unrelated user');

insert into public.ride_driver_vehicles(
    id, driver_id, vehicle_id, display_name, seats,
    verification_status, is_active
) values (
    '92000000-0000-0000-0000-000000000001',
    '91000000-0000-0000-0000-000000000002',
    'guardian-vehicle',
    'Guardian test vehicle',
    4,
    'VERIFIED',
    true
);

insert into public.ride_requests(
    id, passenger_id, assigned_driver_id, assigned_vehicle_id,
    pickup_latitude, pickup_longitude, pickup_address,
    destination_latitude, destination_longitude, destination_address,
    offered_fare_minor, currency, state, version
) values (
    '93000000-0000-0000-0000-000000000001',
    '91000000-0000-0000-0000-000000000001',
    '91000000-0000-0000-0000-000000000002',
    '92000000-0000-0000-0000-000000000001',
    9.935, -84.091, 'Pickup protected',
    9.928, -84.083, 'Destination protected',
    4000, 'CRC', 'IN_PROGRESS', 7
);

select set_config(
    'request.jwt.claim.sub',
    '91000000-0000-0000-0000-000000000001',
    true
);

do $test$
declare
    v_first jsonb;
    v_replay jsonb;
    v_conflict jsonb;
begin
    v_first := public.ride_signal_safety_v2(
        '93000000-0000-0000-0000-000000000001',
        7,
        'SOS',
        'User-provided private detail',
        'guardian:passenger:00000001'
    );
    v_replay := public.ride_signal_safety_v2(
        '93000000-0000-0000-0000-000000000001',
        7,
        'SOS',
        'User-provided private detail',
        'guardian:passenger:00000001'
    );
    v_conflict := public.ride_signal_safety_v2(
        '93000000-0000-0000-0000-000000000001',
        7,
        'LONG_STOP',
        null,
        'guardian:passenger:00000001'
    );

    if v_first #>> '{data,status}' <> 'SAFETY_SIGNAL_RECORDED' or
       v_first #>> '{data,severity}' <> 'CRITICAL' or
       (v_first #>> '{data,authorities_contacted}')::boolean <> false or
       v_replay <> v_first or
       v_conflict #>> '{error,code}' <> 'IDEMPOTENCY_CONFLICT'
    then
        raise exception 'Guardian success, replay or conflict invariant failed';
    end if;
end;
$test$;

select set_config(
    'request.jwt.claim.sub',
    '91000000-0000-0000-0000-000000000003',
    true
);

do $test$
declare
    v_forbidden jsonb;
begin
    v_forbidden := public.ride_signal_safety_v2(
        '93000000-0000-0000-0000-000000000001',
        7,
        'CHECK_IN_REQUEST',
        null,
        'guardian:outsider:000000001'
    );
    if v_forbidden #>> '{error,code}' <> 'FORBIDDEN' then
        raise exception 'Guardian outsider was not rejected';
    end if;
end;
$test$;

set role authenticated;

do $test$
begin
    begin
        insert into public.ride_safety_events(
            trip_id, actor_id, signal_type, severity, idempotency_key
        ) values (
            '93000000-0000-0000-0000-000000000001',
            '91000000-0000-0000-0000-000000000003',
            'SOS',
            'CRITICAL',
            'guardian:direct:0000000001'
        );
        raise exception 'Direct safety insert unexpectedly succeeded';
    exception
        when insufficient_privilege then null;
    end;
end;
$test$;

reset role;
select set_config('request.jwt.claim.sub', '', true);

do $test$
declare
    v_events bigint;
    v_holds bigint;
    v_trip_events bigint;
    v_detail_leaks bigint;
begin
    select count(*) into v_events
      from public.ride_safety_events
     where trip_id = '93000000-0000-0000-0000-000000000001'
       and authorities_contacted = false;
    select count(*) into v_holds
      from public.ride_operational_holds
     where trip_id = '93000000-0000-0000-0000-000000000001'
       and reason_code = 'SOS';
    select count(*) into v_trip_events
      from public.ride_trip_events
     where trip_id = '93000000-0000-0000-0000-000000000001'
       and event_type = 'SAFETY_SIGNAL_RECORDED';
    select count(*) into v_detail_leaks
      from public.ride_safety_events
     where row_to_json(ride_safety_events)::text like
        '%User-provided private detail%';

    if v_events <> 1 or v_holds <> 1 or v_trip_events <> 1 or
       v_detail_leaks <> 0
    then
        raise exception using message = format(
            'Guardian persistence invariant failed events=%s holds=%s trip_events=%s detail_leaks=%s',
            v_events, v_holds, v_trip_events, v_detail_leaks
        );
    end if;
end;
$test$;

select 'ride Guardian safety integration: PASS' as result;

rollback;
