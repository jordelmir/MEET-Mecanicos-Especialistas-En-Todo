\set ON_ERROR_STOP on

create unlogged table public.ride_concurrency_results(
    driver_number integer primary key,
    response jsonb not null
);

insert into auth.users(id)
select md5('phase3-driver-' || driver_number::text)::uuid
from generate_series(1, 100) as driver_number;

insert into public.ride_profiles(
    user_id, mobility_role, country_code, preferred_currency, display_name
)
select
    md5('phase3-driver-' || driver_number::text)::uuid,
    'DRIVER',
    'CR',
    'CRC',
    'Concurrent Driver ' || driver_number::text
from generate_series(1, 100) as driver_number;

insert into public.ride_wallets(driver_id, currency)
select
    md5('phase3-driver-' || driver_number::text)::uuid,
    'CRC'
from generate_series(1, 100) as driver_number;

insert into public.ride_wallet_ledger(
    driver_id, idempotency_key, entry_type, amount_minor, currency,
    direction, withdrawable, metadata
)
select
    md5('phase3-driver-' || driver_number::text)::uuid,
    'concurrency:promo:' || lpad(driver_number::text, 3, '0'),
    'PROMOTIONAL_GRANT',
    100000,
    'CRC',
    'CREDIT',
    false,
    jsonb_build_object('source', 'phase3_concurrency_test')
from generate_series(1, 100) as driver_number;

insert into public.ride_driver_vehicles(
    id, driver_id, vehicle_id, display_name, seats,
    verification_status, is_active
)
select
    md5('phase3-vehicle-' || driver_number::text)::uuid,
    md5('phase3-driver-' || driver_number::text)::uuid,
    'concurrent-vehicle-' || driver_number::text,
    'Concurrent Vehicle ' || driver_number::text,
    4,
    'VERIFIED',
    true
from generate_series(1, 100) as driver_number;

insert into public.ride_requests(
    id, passenger_id, pickup_latitude, pickup_longitude, pickup_address,
    destination_latitude, destination_longitude, destination_address,
    offered_fare_minor, currency, state, version
)
values (
    '66666666-6666-6666-6666-666666666666',
    '11111111-1111-1111-1111-111111111111',
    9.93,
    -84.08,
    'Concurrent Origin',
    9.96,
    -84.11,
    'Concurrent Destination',
    4600,
    'CRC',
    'SEARCHING',
    1
);

create or replace function public.ride_test_concurrent_claim(
    p_driver_number integer
)
returns void
language plpgsql
security invoker
set search_path = ''
as $$
declare
    v_driver_id uuid := md5(
        'phase3-driver-' || p_driver_number::text
    )::uuid;
    v_vehicle_id uuid := md5(
        'phase3-vehicle-' || p_driver_number::text
    )::uuid;
    v_response jsonb;
begin
    perform set_config(
        'request.jwt.claim.sub',
        v_driver_id::text,
        true
    );
    v_response := public.ride_claim_request_v2(
        '66666666-6666-6666-6666-666666666666',
        v_vehicle_id,
        1,
        'concurrency:claim:' || lpad(p_driver_number::text, 3, '0')
    );
    insert into public.ride_concurrency_results(driver_number, response)
    values (p_driver_number, v_response);
end;
$$;
