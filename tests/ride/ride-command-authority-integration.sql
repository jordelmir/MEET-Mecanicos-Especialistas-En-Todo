\set ON_ERROR_STOP on

insert into auth.users(id) values
    ('11111111-1111-1111-1111-111111111111'),
    ('22222222-2222-2222-2222-222222222222');

insert into public.ride_profiles(
    user_id, mobility_role, country_code, preferred_currency, display_name
)
values
    (
        '11111111-1111-1111-1111-111111111111',
        'PASSENGER', 'CR', 'CRC', 'Passenger Test'
    ),
    (
        '22222222-2222-2222-2222-222222222222',
        'DRIVER', 'CR', 'CRC', 'Driver Test'
    );

insert into public.ride_wallets(driver_id, currency)
values ('22222222-2222-2222-2222-222222222222', 'CRC');

insert into public.ride_wallet_ledger(
    driver_id, idempotency_key, entry_type, amount_minor, currency,
    direction, withdrawable, metadata
)
values (
    '22222222-2222-2222-2222-222222222222',
    'test:promo:00000001',
    'PROMOTIONAL_GRANT',
    100000,
    'CRC',
    'CREDIT',
    false,
    '{"source":"phase3_sql_test"}'::jsonb
);

insert into public.ride_driver_vehicles(
    id, driver_id, vehicle_id, display_name, seats,
    verification_status, is_active
)
values (
    '33333333-3333-3333-3333-333333333333',
    '22222222-2222-2222-2222-222222222222',
    'vehicle-test-1',
    'Test Vehicle',
    4,
    'VERIFIED',
    true
);

insert into public.ride_requests(
    id, passenger_id, pickup_latitude, pickup_longitude, pickup_address,
    destination_latitude, destination_longitude, destination_address,
    offered_fare_minor, currency, state, version
)
values
    (
        '44444444-4444-4444-4444-444444444444',
        '11111111-1111-1111-1111-111111111111',
        9.93, -84.08, 'Origen',
        9.94, -84.09, 'Destino',
        4600, 'CRC', 'SEARCHING', 1
    ),
    (
        '55555555-5555-5555-5555-555555555555',
        '11111111-1111-1111-1111-111111111111',
        9.93, -84.08, 'Origen 2',
        9.95, -84.10, 'Destino 2',
        4600, 'CRC', 'SEARCHING', 1
    );

select set_config(
    'request.jwt.claim.sub',
    '22222222-2222-2222-2222-222222222222',
    false
);

do $test$
declare
    v_first jsonb;
    v_replay jsonb;
    v_conflict jsonb;
    v_version_conflict jsonb;
    v_cancel jsonb;
    v_complete jsonb;
    v_forbidden jsonb;
    v_version bigint;
begin
    v_first := public.ride_claim_request_v2(
        '44444444-4444-4444-4444-444444444444',
        '33333333-3333-3333-3333-333333333333',
        1,
        'claim:test:00000001'
    );
    if v_first #>> '{data,status}' <> 'CLAIMED' or
       (v_first #>> '{data,commission_reserved_minor}')::bigint <> 230 or
       nullif(v_first ->> 'correlation_id', '') is null
    then
        raise exception 'claim assertion failed: %', v_first;
    end if;

    v_replay := public.ride_claim_request_v2(
        '44444444-4444-4444-4444-444444444444',
        '33333333-3333-3333-3333-333333333333',
        1,
        'claim:test:00000001'
    );
    if v_replay <> v_first then
        raise exception 'idempotent replay changed response';
    end if;

    v_conflict := public.ride_claim_request_v2(
        '44444444-4444-4444-4444-444444444444',
        '33333333-3333-3333-3333-333333333333',
        2,
        'claim:test:00000001'
    );
    if v_conflict #>> '{error,code}' <> 'IDEMPOTENCY_CONFLICT' then
        raise exception 'idempotency conflict assertion failed: %', v_conflict;
    end if;

    v_version_conflict := public.ride_cancel_trip_v2(
        '44444444-4444-4444-4444-444444444444',
        1,
        'CHANGE_OF_PLANS',
        null,
        'cancel:test:stale01'
    );
    if v_version_conflict #>> '{error,code}' <> 'VERSION_CONFLICT' then
        raise exception 'version conflict assertion failed: %',
            v_version_conflict;
    end if;

    select version
      into v_version
      from public.ride_requests
     where id = '44444444-4444-4444-4444-444444444444';

    v_cancel := public.ride_cancel_trip_v2(
        '44444444-4444-4444-4444-444444444444',
        v_version,
        'SAFETY_CONCERN',
        'Test safety cancellation',
        'cancel:test:000001'
    );
    if v_cancel #>> '{data,status}' <> 'CANCELLED' or
       (v_cancel #>> '{data,reservation_released}')::boolean is not true
    then
        raise exception 'cancel assertion failed: %', v_cancel;
    end if;

    v_first := public.ride_claim_request_v2(
        '55555555-5555-5555-5555-555555555555',
        '33333333-3333-3333-3333-333333333333',
        1,
        'claim:test:00000002'
    );
    if v_first #>> '{data,status}' <> 'CLAIMED' then
        raise exception 'second claim assertion failed: %', v_first;
    end if;

    update public.ride_requests
       set state = 'IN_PROGRESS',
           version = version + 1,
           updated_at = now()
     where id = '55555555-5555-5555-5555-555555555555'
    returning version into v_version;

    insert into public.ride_fare_quotes(
        trip_id, quote_version, currency, transport_fare_minor,
        approved_wait_minor, approved_stops_minor,
        approved_surcharges_minor, driver_funded_discount_minor,
        tip_minor, tolls_minor, taxes_minor, platform_promotion_minor,
        created_by, accepted_by, idempotency_key
    )
    values (
        '55555555-5555-5555-5555-555555555555',
        2,
        'CRC',
        4600,
        300,
        300,
        200,
        100,
        500,
        200,
        100,
        300,
        '11111111-1111-1111-1111-111111111111',
        '11111111-1111-1111-1111-111111111111',
        'quote:test:00000002'
    );

    perform set_config(
        'request.jwt.claim.sub',
        '11111111-1111-1111-1111-111111111111',
        true
    );
    v_forbidden := public.ride_complete_trip_v2(
        '55555555-5555-5555-5555-555555555555',
        v_version,
        'complete:test:deny01'
    );
    if v_forbidden #>> '{error,code}' <> 'FORBIDDEN' then
        raise exception 'actor authorization assertion failed: %', v_forbidden;
    end if;

    perform set_config(
        'request.jwt.claim.sub',
        '22222222-2222-2222-2222-222222222222',
        true
    );
    v_complete := public.ride_complete_trip_v2(
        '55555555-5555-5555-5555-555555555555',
        v_version,
        'complete:test:0001'
    );
    if v_complete #>> '{data,status}' <> 'COMPLETED' or
       (v_complete #>> '{data,commissionable_base_minor}')::bigint <> 5300 or
       (v_complete #>> '{data,commission_minor}')::bigint <> 265 or
       (v_complete #>> '{data,customer_total_minor}')::bigint <> 5800
    then
        raise exception 'completion calculation assertion failed: %', v_complete;
    end if;
end;
$test$;

do $test$
declare
    v_unbalanced bigint;
    v_legacy_policy bigint;
    v_holds bigint;
    v_cancel_from text;
    v_claim_receipts bigint;
begin
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
    if v_unbalanced <> 0 then
        raise exception 'unbalanced journals: %', v_unbalanced;
    end if;

    select count(*)
      into v_legacy_policy
      from public.ride_ledger_transactions
     where event_type like 'COMMISSION_%'
       and commission_policy_version <> 'ride-commission-v1';
    if v_legacy_policy <> 0 then
        raise exception 'incorrect commission provenance rows: %',
            v_legacy_policy;
    end if;

    select count(*)
      into v_holds
      from public.ride_operational_holds
     where trip_id = '44444444-4444-4444-4444-444444444444'
       and hold_type = 'SAFETY_REVIEW';
    if v_holds <> 1 then
        raise exception 'safety hold missing';
    end if;

    select from_state
      into v_cancel_from
      from public.ride_trip_events
     where trip_id = '44444444-4444-4444-4444-444444444444'
       and event_type = 'TRIP_CANCELLED';
    if v_cancel_from <> 'ASSIGNED' then
        raise exception 'cancel from_state incorrect: %', v_cancel_from;
    end if;

    select count(*)
      into v_claim_receipts
      from public.ride_command_receipts
     where actor_id = '22222222-2222-2222-2222-222222222222'
       and idempotency_key = 'claim:test:00000001';
    if v_claim_receipts <> 1 then
        raise exception 'idempotent receipt count incorrect: %',
            v_claim_receipts;
    end if;

    begin
        update public.ride_fare_quotes
           set transport_fare_minor = 9999
         where trip_id = '55555555-5555-5555-5555-555555555555';
        raise exception 'fare quote mutation unexpectedly succeeded';
    exception
        when others then
            if sqlerrm = 'fare quote mutation unexpectedly succeeded' then
                raise;
            end if;
    end;
end;
$test$;

select
    'ride command authority PostgreSQL integration: PASS' as result,
    count(*) as balanced_transactions
from public.ride_ledger_transactions;

select 1 / case
    when has_table_privilege(
        'authenticated',
        'public.ride_command_receipts',
        'INSERT'
    ) then 0
    else 1
end as direct_command_write_revoked;

select set_config(
    'request.jwt.claim.sub',
    '77777777-7777-7777-7777-777777777777',
    false
);
set role authenticated;
select 1 / case when count(*) = 0 then 1 else 0 end
    as unrelated_receipts_hidden
from public.ride_command_receipts;
select 1 / case when count(*) = 0 then 1 else 0 end
    as unrelated_quotes_hidden
from public.ride_fare_quotes;
select 1 / case when count(*) = 0 then 1 else 0 end
    as unrelated_holds_hidden
from public.ride_operational_holds;
reset role;
