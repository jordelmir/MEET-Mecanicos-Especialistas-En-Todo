\set ON_ERROR_STOP on

insert into auth.users(id) values
    ('91111111-1111-1111-1111-111111111111'),
    ('92222222-2222-2222-2222-222222222222');

select set_config(
    'request.jwt.claim.sub',
    '91111111-1111-1111-1111-111111111111',
    false
);

do $test$
declare
    v_enrollment jsonb;
    v_replay jsonb;
begin
    v_enrollment := public.ride_enroll_driver_pilot_v2(
        'Driver Pilot Test',
        'CR',
        'CRC',
        '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
        'Hyundai Accent 2005 Gris',
        4,
        'abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789',
        'enroll:pilot:test:0001'
    );
    if v_enrollment #>> '{data,status}' <> 'PILOT_ATTESTED' or
       v_enrollment #>> '{data,document_review_status}' <> 'UNDER_REVIEW' or
       v_enrollment #>> '{data,vehicle_id}' is null
    then
        raise exception 'pilot enrollment assertion failed: %', v_enrollment;
    end if;

    v_replay := public.ride_enroll_driver_pilot_v2(
        'Driver Pilot Test',
        'CR',
        'CRC',
        '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
        'Hyundai Accent 2005 Gris',
        4,
        'abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789',
        'enroll:pilot:test:0001'
    );
    if v_replay <> v_enrollment then
        raise exception 'pilot enrollment replay changed response';
    end if;
end;
$test$;

insert into public.ride_wallets(driver_id, currency)
values ('91111111-1111-1111-1111-111111111111', 'CRC');

select set_config(
    'request.jwt.claim.sub',
    '92222222-2222-2222-2222-222222222222',
    false
);

select public.ride_create_request_v2(
    '93333333-3333-3333-3333-333333333333',
    'Passenger Pilot Test',
    'CR',
    9.930, -84.080, 'Origen piloto',
    9.950, -84.100, 'Destino piloto',
    4200, 'CRC', 'CASH', '[]'::jsonb,
    'create:pilot:test:0001'
);

select set_config(
    'request.jwt.claim.sub',
    '91111111-1111-1111-1111-111111111111',
    false
);

do $test$
declare
    v_vehicle_id uuid;
    v_offer jsonb;
begin
    select id into v_vehicle_id
      from public.ride_driver_vehicles
     where driver_id = '91111111-1111-1111-1111-111111111111'
       and is_active;
    v_offer := public.ride_submit_offer_v2(
        '93333333-3333-3333-3333-333333333333',
        '94444444-4444-4444-4444-444444444444',
        v_vehicle_id,
        4200,
        'CRC',
        240,
        1,
        'offer:pilot:test:0001'
    );
    if v_offer #>> '{data,status}' <> 'OFFERED' then
        raise exception 'pilot offer assertion failed: %', v_offer;
    end if;
end;
$test$;

update public.ride_driver_vehicles
   set pilot_access_expires_at = now() - interval '1 second'
 where driver_id = '91111111-1111-1111-1111-111111111111';

do $test$
declare
    v_vehicle_id uuid;
begin
    select id into v_vehicle_id
      from public.ride_driver_vehicles
     where driver_id = '91111111-1111-1111-1111-111111111111';
    if public.ride_vehicle_dispatch_eligible(
        v_vehicle_id,
        '91111111-1111-1111-1111-111111111111'
    ) then
        raise exception 'expired pilot access remained eligible';
    end if;
end;
$test$;

select
    'ride driver pilot enrollment integration: PASS' as result,
    verification_method,
    document_review_status,
    public.ride_vehicle_dispatch_eligible(id, driver_id) as eligible_after_expiry
from public.ride_driver_vehicles
where driver_id = '91111111-1111-1111-1111-111111111111';
