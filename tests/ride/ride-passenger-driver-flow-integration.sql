\set ON_ERROR_STOP on

insert into auth.users(id) values
    ('81111111-1111-1111-1111-111111111111'),
    ('82222222-2222-2222-2222-222222222222');

insert into public.ride_profiles(
    user_id, mobility_role, country_code, preferred_currency, display_name
)
values (
    '82222222-2222-2222-2222-222222222222',
    'DRIVER', 'CR', 'CRC', 'Driver Vertical Test'
);

insert into public.ride_wallets(driver_id, currency)
values ('82222222-2222-2222-2222-222222222222', 'CRC');

insert into public.ride_wallet_ledger(
    driver_id, idempotency_key, entry_type, amount_minor, currency,
    direction, withdrawable, metadata
)
values (
    '82222222-2222-2222-2222-222222222222',
    'test:vertical:promo:01',
    'PROMOTIONAL_GRANT',
    100000,
    'CRC',
    'CREDIT',
    false,
    '{"source":"phase5_vertical_sql_test"}'::jsonb
);

insert into public.ride_driver_vehicles(
    id, driver_id, vehicle_id, display_name, seats,
    verification_status, is_active
)
values (
    '83333333-3333-3333-3333-333333333333',
    '82222222-2222-2222-2222-222222222222',
    'vehicle-vertical-1',
    'Verified Vertical Vehicle',
    4,
    'VERIFIED',
    true
);

select set_config(
    'request.jwt.claim.sub',
    '81111111-1111-1111-1111-111111111111',
    false
);

do $test$
declare
    v_create jsonb;
    v_replay jsonb;
    v_offer jsonb;
    v_accept jsonb;
    v_transition jsonb;
    v_issue jsonb;
    v_invalid jsonb;
    v_verified jsonb;
    v_complete jsonb;
    v_cancel jsonb;
    v_pin text;
    v_version bigint;
begin
    v_create := public.ride_create_request_v2(
        '84444444-4444-4444-4444-444444444444',
        'Passenger Vertical Test',
        'CR',
        9.932, -84.081, 'Origen validado',
        9.955, -84.112, 'Destino validado',
        4800, 'CRC', 'SINPE',
        '[
            {
                "providerPlaceId":"osm:node:1",
                "label":"Parada 1",
                "latitude":9.940,
                "longitude":-84.090
            },
            {
                "providerPlaceId":"osm:node:2",
                "label":"Parada 2",
                "latitude":9.948,
                "longitude":-84.101
            }
        ]'::jsonb,
        'create:vertical:0001'
    );
    if v_create #>> '{data,status}' <> 'SEARCHING' or
       (v_create #>> '{data,version}')::bigint <> 1
    then
        raise exception 'create request assertion failed: %', v_create;
    end if;
    v_replay := public.ride_create_request_v2(
        '84444444-4444-4444-4444-444444444444',
        'Passenger Vertical Test',
        'CR',
        9.932, -84.081, 'Origen validado',
        9.955, -84.112, 'Destino validado',
        4800, 'CRC', 'SINPE',
        '[
            {
                "providerPlaceId":"osm:node:1",
                "label":"Parada 1",
                "latitude":9.940,
                "longitude":-84.090
            },
            {
                "providerPlaceId":"osm:node:2",
                "label":"Parada 2",
                "latitude":9.948,
                "longitude":-84.101
            }
        ]'::jsonb,
        'create:vertical:0001'
    );
    if v_replay <> v_create then
        raise exception 'create replay changed response';
    end if;

    perform set_config(
        'request.jwt.claim.sub',
        '82222222-2222-2222-2222-222222222222',
        true
    );
    v_offer := public.ride_submit_offer_v2(
        '84444444-4444-4444-4444-444444444444',
        '86666666-6666-6666-6666-666666666666',
        '83333333-3333-3333-3333-333333333333',
        5100, 'CRC', 420, 1,
        'offer:vertical:0001'
    );
    if v_offer #>> '{data,status}' <> 'OFFERED' or
       (v_offer #>> '{data,version}')::bigint <> 2
    then
        raise exception 'submit offer assertion failed: %', v_offer;
    end if;

    perform set_config(
        'request.jwt.claim.sub',
        '81111111-1111-1111-1111-111111111111',
        true
    );
    v_accept := public.ride_accept_offer_v2(
        '84444444-4444-4444-4444-444444444444',
        (v_offer #>> '{data,offer_id}')::uuid,
        2,
        'accept:vertical:001'
    );
    if v_accept #>> '{data,status}' <> 'ASSIGNED' or
       (v_accept #>> '{data,version}')::bigint <> 3 or
       (v_accept #>> '{data,commission_reserved_minor}')::bigint <> 255
    then
        raise exception 'accept offer assertion failed: %', v_accept;
    end if;

    perform set_config(
        'request.jwt.claim.sub',
        '82222222-2222-2222-2222-222222222222',
        true
    );
    v_transition := public.ride_driver_transition_v2(
        '84444444-4444-4444-4444-444444444444',
        'DRIVER_EN_ROUTE', 3, 'enroute:vertical:01'
    );
    if v_transition #>> '{data,status}' <> 'DRIVER_EN_ROUTE' or
       (v_transition #>> '{data,version}')::bigint <> 4
    then
        raise exception 'en route assertion failed: %', v_transition;
    end if;
    v_transition := public.ride_driver_transition_v2(
        '84444444-4444-4444-4444-444444444444',
        'DRIVER_ARRIVED', 4, 'arrived:vertical:01'
    );
    if v_transition #>> '{data,status}' <> 'ARRIVED' or
       (v_transition #>> '{data,version}')::bigint <> 5
    then
        raise exception 'arrived assertion failed: %', v_transition;
    end if;

    perform set_config(
        'request.jwt.claim.sub',
        '81111111-1111-1111-1111-111111111111',
        true
    );
    v_issue := public.ride_issue_boarding_pin_v2(
        '84444444-4444-4444-4444-444444444444',
        5,
        'pinissue:vertical:1'
    );
    v_pin := v_issue #>> '{data,boarding_pin}';
    if v_issue #>> '{data,status}' <> 'ARRIVED' or
       (v_issue #>> '{data,version}')::bigint <> 6 or
       v_pin !~ '^[0-9]{4}$'
    then
        raise exception 'PIN issue assertion failed: %', v_issue;
    end if;

    perform set_config(
        'request.jwt.claim.sub',
        '82222222-2222-2222-2222-222222222222',
        true
    );
    v_invalid := public.ride_verify_boarding_pin_v2(
        '84444444-4444-4444-4444-444444444444',
        case when v_pin = '9999' then '9998' else '9999' end,
        6,
        'pinverify:invalid:1'
    );
    if v_invalid #>> '{data,status}' <> 'INVALID' or
       (v_invalid #>> '{data,version}')::bigint <> 6
    then
        raise exception 'invalid PIN assertion failed: %', v_invalid;
    end if;
    v_verified := public.ride_verify_boarding_pin_v2(
        '84444444-4444-4444-4444-444444444444',
        v_pin,
        6,
        'pinverify:valid:001'
    );
    if v_verified #>> '{data,status}' <> 'PASSENGER_ONBOARD' or
       (v_verified #>> '{data,version}')::bigint <> 7
    then
        raise exception 'valid PIN assertion failed: %', v_verified;
    end if;

    v_transition := public.ride_driver_transition_v2(
        '84444444-4444-4444-4444-444444444444',
        'START', 7, 'start:vertical:0001'
    );
    if v_transition #>> '{data,status}' <> 'IN_PROGRESS' or
       (v_transition #>> '{data,version}')::bigint <> 8
    then
        raise exception 'start assertion failed: %', v_transition;
    end if;
    v_complete := public.ride_complete_trip_v2(
        '84444444-4444-4444-4444-444444444444',
        8,
        'complete:vertical:01'
    );
    if v_complete #>> '{data,status}' <> 'COMPLETED' or
       (v_complete #>> '{data,commissionable_base_minor}')::bigint <> 5100 or
       (v_complete #>> '{data,commission_minor}')::bigint <> 255
    then
        raise exception 'complete assertion failed: %', v_complete;
    end if;

    perform set_config(
        'request.jwt.claim.sub',
        '81111111-1111-1111-1111-111111111111',
        true
    );
    v_create := public.ride_create_request_v2(
        '85555555-5555-5555-5555-555555555555',
        'Passenger Vertical Test',
        'CR',
        9.932, -84.081, 'Origen cancelación',
        9.955, -84.112, 'Destino cancelación',
        3600, 'CRC', 'CASH', '[]'::jsonb,
        'create:vertical:0002'
    );
    v_cancel := public.ride_cancel_trip_v2(
        '85555555-5555-5555-5555-555555555555',
        1, 'CHANGE_OF_PLANS', null,
        'cancel:vertical:0001'
    );
    if v_cancel #>> '{data,status}' <> 'CANCELLED' then
        raise exception 'passenger cancellation assertion failed: %', v_cancel;
    end if;

    select version
      into v_version
      from public.ride_requests
     where id = '84444444-4444-4444-4444-444444444444';
    if v_version <> 9 then
        raise exception 'completed ride final version incorrect: %', v_version;
    end if;
end;
$test$;

do $test$
declare
    v_stops bigint;
    v_receipts bigint;
    v_events bigint;
    v_reservation_state text;
    v_unbalanced bigint;
begin
    select count(*)
      into v_stops
      from public.ride_request_stops
     where request_id = '84444444-4444-4444-4444-444444444444';
    select count(*)
      into v_receipts
      from public.ride_command_receipts
     where trip_id = '84444444-4444-4444-4444-444444444444';
    select count(*)
      into v_events
      from public.ride_trip_events
     where trip_id = '84444444-4444-4444-4444-444444444444';
    select state
      into v_reservation_state
      from public.ride_commission_reservations
     where trip_id = '84444444-4444-4444-4444-444444444444';
    select count(*)
      into v_unbalanced
      from (
          select p.transaction_id
            from public.ride_ledger_postings p
           group by p.transaction_id
          having sum(
              case when p.direction = 'DEBIT' then p.amount_minor else 0 end
          ) <> sum(
              case when p.direction = 'CREDIT' then p.amount_minor else 0 end
          )
      ) unbalanced;

    if v_stops <> 2 or v_receipts <> 10 or v_events <> 9 or
       v_reservation_state <> 'CAPTURED' or v_unbalanced <> 0
    then
        raise exception using
            message = format(
                'vertical invariants failed: stops=%s receipts=%s events=%s reservation=%s unbalanced=%s',
                v_stops, v_receipts, v_events, v_reservation_state,
                v_unbalanced
            );
    end if;
end;
$test$;

select
    'ride passenger-driver vertical integration: PASS' as result,
    state,
    version,
    offered_fare_minor,
    final_fare_minor
from public.ride_requests
where id = '84444444-4444-4444-4444-444444444444';
