begin;

insert into auth.users(id) values
    ('b1000000-0000-0000-0000-000000000001'),
    ('b1000000-0000-0000-0000-000000000002'),
    ('b1000000-0000-0000-0000-000000000003'),
    ('b1000000-0000-0000-0000-000000000004');

insert into public.ride_profiles(
    user_id, mobility_role, country_code, preferred_currency, display_name
) values
    ('b1000000-0000-0000-0000-000000000001', 'DRIVER', 'CR', 'CRC', 'Tenant A driver'),
    ('b1000000-0000-0000-0000-000000000002', 'DRIVER', 'CR', 'CRC', 'Tenant B driver'),
    ('b1000000-0000-0000-0000-000000000003', 'PASSENGER', 'CR', 'CRC', 'Tenant A passenger'),
    ('b1000000-0000-0000-0000-000000000004', 'PASSENGER', 'CR', 'CRC', 'Tenant B passenger');

insert into public.ride_tenants(
    id, tenant_type, legal_name, display_name, country_code,
    default_currency, status
) values
    ('b2000000-0000-0000-0000-000000000001', 'COOPERATIVE', 'Tenant A legal', 'Tenant A', 'CR', 'CRC', 'ACTIVE'),
    ('b2000000-0000-0000-0000-000000000002', 'FLEET', 'Tenant B legal', 'Tenant B', 'CR', 'CRC', 'ACTIVE');

insert into public.ride_tenant_memberships(tenant_id, user_id, role)
values
    ('b2000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0000-000000000001', 'DRIVER'),
    ('b2000000-0000-0000-0000-000000000002', 'b1000000-0000-0000-0000-000000000002', 'DRIVER');

insert into public.ride_driver_vehicles(
    id, tenant_id, driver_id, vehicle_id, display_name, seats,
    verification_status, is_active
) values
    (
        'b3000000-0000-0000-0000-000000000001',
        'b2000000-0000-0000-0000-000000000001',
        'b1000000-0000-0000-0000-000000000001',
        'tenant-a-vehicle', 'Tenant A vehicle', 4, 'VERIFIED', true
    ),
    (
        'b3000000-0000-0000-0000-000000000002',
        'b2000000-0000-0000-0000-000000000002',
        'b1000000-0000-0000-0000-000000000002',
        'tenant-b-vehicle', 'Tenant B vehicle', 4, 'VERIFIED', true
    );

insert into public.ride_requests(
    id, tenant_id, passenger_id,
    pickup_latitude, pickup_longitude, pickup_address,
    destination_latitude, destination_longitude, destination_address,
    offered_fare_minor, currency, state, version
) values
    (
        'b4000000-0000-0000-0000-000000000001',
        'b2000000-0000-0000-0000-000000000001',
        'b1000000-0000-0000-0000-000000000003',
        9.93, -84.08, 'Tenant A coarse pickup',
        9.94, -84.09, 'Tenant A destination',
        4000, 'CRC', 'SEARCHING', 1
    ),
    (
        'b4000000-0000-0000-0000-000000000002',
        'b2000000-0000-0000-0000-000000000002',
        'b1000000-0000-0000-0000-000000000004',
        9.91, -84.07, 'Tenant B coarse pickup',
        9.92, -84.06, 'Tenant B destination',
        4200, 'CRC', 'SEARCHING', 1
    );

select set_config(
    'request.jwt.claim.sub',
    'b1000000-0000-0000-0000-000000000001',
    true
);
set role authenticated;

do $test$
declare
    v_a bigint;
    v_b bigint;
begin
    select count(*) into v_a
      from public.ride_requests
     where id = 'b4000000-0000-0000-0000-000000000001';
    select count(*) into v_b
      from public.ride_requests
     where id = 'b4000000-0000-0000-0000-000000000002';
    if v_a <> 1 or v_b <> 0 then
        raise exception 'Tenant search isolation failed a=% b=%', v_a, v_b;
    end if;
end;
$test$;

do $test$
begin
    begin
        update public.ride_driver_vehicles
           set tenant_id = 'b2000000-0000-0000-0000-000000000002'
         where id = 'b3000000-0000-0000-0000-000000000001';
        raise exception 'Driver unexpectedly moved its vehicle across tenants';
    exception
        when insufficient_privilege then null;
    end;
end;
$test$;

reset role;
select set_config('request.jwt.claim.sub', '', true);

do $test$
begin
    begin
        insert into public.ride_offers(
            id, tenant_id, request_id, driver_id, vehicle_id,
            fare_minor, currency, state
        ) values (
            'b5000000-0000-0000-0000-000000000001',
            'b2000000-0000-0000-0000-000000000002',
            'b4000000-0000-0000-0000-000000000002',
            'b1000000-0000-0000-0000-000000000001',
            'b3000000-0000-0000-0000-000000000001',
            4200, 'CRC', 'PENDING'
        );
        raise exception 'Cross-tenant offer unexpectedly succeeded';
    exception
        when check_violation then
            if sqlerrm <> 'RIDE_TENANT_MISMATCH' then
                raise;
            end if;
    end;
end;
$test$;

select 'ride tenant boundary integration: PASS' as result;

rollback;
