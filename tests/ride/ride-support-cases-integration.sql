begin;

insert into auth.users(id) values
    ('a1000000-0000-0000-0000-000000000001'),
    ('a1000000-0000-0000-0000-000000000002'),
    ('a1000000-0000-0000-0000-000000000003');

insert into public.ride_profiles(
    user_id, mobility_role, country_code, preferred_currency, display_name
) values
    ('a1000000-0000-0000-0000-000000000001', 'PASSENGER', 'CR', 'CRC', 'Support passenger'),
    ('a1000000-0000-0000-0000-000000000002', 'DRIVER', 'CR', 'CRC', 'Support driver'),
    ('a1000000-0000-0000-0000-000000000003', 'PASSENGER', 'CR', 'CRC', 'Support outsider');

insert into public.ride_driver_vehicles(
    id, driver_id, vehicle_id, display_name, seats,
    verification_status, is_active
) values (
    'a2000000-0000-0000-0000-000000000001',
    'a1000000-0000-0000-0000-000000000002',
    'support-vehicle',
    'Support test vehicle',
    4,
    'VERIFIED',
    true
);

insert into public.ride_requests(
    id, passenger_id, assigned_driver_id, assigned_vehicle_id,
    pickup_latitude, pickup_longitude, pickup_address,
    destination_latitude, destination_longitude, destination_address,
    offered_fare_minor, final_fare_minor, currency, state, version,
    completed_at
) values (
    'a3000000-0000-0000-0000-000000000001',
    'a1000000-0000-0000-0000-000000000001',
    'a1000000-0000-0000-0000-000000000002',
    'a2000000-0000-0000-0000-000000000001',
    9.935, -84.091, 'Support pickup',
    9.928, -84.083, 'Support destination',
    4600, 4600, 'CRC', 'COMPLETED', 9, now()
);

select set_config(
    'request.jwt.claim.sub',
    'a1000000-0000-0000-0000-000000000001',
    true
);

do $test$
declare
    v_first jsonb;
    v_replay jsonb;
    v_conflict jsonb;
begin
    v_first := public.ride_open_support_case_v2(
        'a3000000-0000-0000-0000-000000000001',
        9,
        'WRONG_CHARGE',
        'El desglose no coincide con el monto acordado.',
        null,
        'support:passenger:00000001'
    );
    v_replay := public.ride_open_support_case_v2(
        'a3000000-0000-0000-0000-000000000001',
        9,
        'WRONG_CHARGE',
        'El desglose no coincide con el monto acordado.',
        null,
        'support:passenger:00000001'
    );
    v_conflict := public.ride_open_support_case_v2(
        'a3000000-0000-0000-0000-000000000001',
        9,
        'LOST_ITEM',
        'Objeto olvidado dentro del vehículo de la prueba.',
        null,
        'support:passenger:00000001'
    );
    if v_first #>> '{data,status}' <> 'SUPPORT_CASE_OPENED' or
       v_first #>> '{data,severity}' <> 'PRIORITY' or
       v_replay <> v_first or
       v_conflict #>> '{error,code}' <> 'IDEMPOTENCY_CONFLICT'
    then
        raise exception 'Support case success, replay or conflict invariant failed';
    end if;
end;
$test$;

select set_config(
    'request.jwt.claim.sub',
    'a1000000-0000-0000-0000-000000000003',
    true
);

do $test$
declare
    v_forbidden jsonb;
begin
    v_forbidden := public.ride_open_support_case_v2(
        'a3000000-0000-0000-0000-000000000001',
        9,
        'OTHER',
        'Intento de acceso de una persona no participante.',
        null,
        'support:outsider:000000001'
    );
    if v_forbidden #>> '{error,code}' <> 'FORBIDDEN' then
        raise exception 'Support outsider was not rejected';
    end if;
end;
$test$;

reset role;
select set_config('request.jwt.claim.sub', '', true);

do $test$
declare
    v_cases bigint;
    v_timeline bigint;
    v_events bigint;
    v_adjustments bigint;
begin
    select count(*) into v_cases
      from public.ride_support_cases
     where trip_id = 'a3000000-0000-0000-0000-000000000001'
       and status = 'OPEN'
       and category = 'WRONG_CHARGE';
    select count(*) into v_timeline
      from public.ride_support_case_timeline t
      join public.ride_support_cases c on c.id = t.case_id
     where c.trip_id = 'a3000000-0000-0000-0000-000000000001'
       and t.event_type = 'CASE_OPENED';
    select count(*) into v_events
      from public.ride_trip_events
     where trip_id = 'a3000000-0000-0000-0000-000000000001'
       and event_type = 'SUPPORT_CASE_OPENED';
    select count(*) into v_adjustments
      from public.ride_support_cases
     where trip_id = 'a3000000-0000-0000-0000-000000000001'
       and financial_adjustment_transaction_id is not null;

    if v_cases <> 1 or v_timeline <> 1 or v_events <> 1 or
       v_adjustments <> 0
    then
        raise exception using message = format(
            'Support persistence invariant failed cases=%s timeline=%s events=%s adjustments=%s',
            v_cases, v_timeline, v_events, v_adjustments
        );
    end if;
end;
$test$;

select 'ride support cases integration: PASS' as result;

rollback;
