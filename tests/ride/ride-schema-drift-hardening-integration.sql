\set ON_ERROR_STOP on

insert into auth.users(id) values
    ('a1000000-0000-0000-0000-000000000001'),
    ('a2000000-0000-0000-0000-000000000002');

insert into public.ride_profiles(
    user_id, mobility_role, country_code, preferred_currency, display_name
) values
    ('a1000000-0000-0000-0000-000000000001', 'PASSENGER', 'CR', 'CRC', 'Passenger Hardening'),
    ('a2000000-0000-0000-0000-000000000002', 'DRIVER', 'CR', 'CRC', 'Driver Hardening');

insert into public.ride_wallets(driver_id, currency)
values ('a2000000-0000-0000-0000-000000000002', 'CRC');

insert into public.ride_wallet_ledger(
    driver_id, idempotency_key, entry_type, amount_minor, currency,
    direction, withdrawable, metadata
) values (
    'a2000000-0000-0000-0000-000000000002',
    'hardening:promo:0001', 'PROMOTIONAL_GRANT', 100000, 'CRC',
    'CREDIT', false, '{"source":"schema_drift_integration"}'::jsonb
);

insert into public.ride_driver_vehicles(
    id, driver_id, vehicle_id, display_name, seats,
    verification_status, is_active
) values (
    'a3000000-0000-0000-0000-000000000003',
    'a2000000-0000-0000-0000-000000000002',
    'hardening-vehicle', 'Hardening Vehicle', 4, 'VERIFIED', true
);

insert into public.ride_driver_public_profiles(driver_id, display_name)
values ('a2000000-0000-0000-0000-000000000002', 'Driver Hardening');

insert into public.ride_driver_presence(
    driver_id, vehicle_id, availability
) values (
    'a2000000-0000-0000-0000-000000000002',
    'a3000000-0000-0000-0000-000000000003', 'AVAILABLE'
);

insert into public.ride_requests(
    id, passenger_id, pickup_latitude, pickup_longitude, pickup_address,
    destination_latitude, destination_longitude, destination_address,
    offered_fare_minor, currency, state, version
) values (
    'a4000000-0000-0000-0000-000000000004',
    'a1000000-0000-0000-0000-000000000001',
    9.93, -84.08, 'Origen hardening',
    9.94, -84.09, 'Destino hardening',
    5000, 'CRC', 'SEARCHING', 1
);

insert into public.ride_offers(
    id, request_id, driver_id, vehicle_id, fare_minor, currency,
    eta_seconds, state
) values (
    'a5000000-0000-0000-0000-000000000005',
    'a4000000-0000-0000-0000-000000000004',
    'a2000000-0000-0000-0000-000000000002',
    'a3000000-0000-0000-0000-000000000003',
    4800, 'CRC', 240, 'PENDING'
);

insert into public.ride_auto_match_policies(
    request_id, tenant_id, strategy, max_fare_minor,
    minimum_trust_tier, maximum_eta_seconds
) values (
    'a4000000-0000-0000-0000-000000000004',
    '00000000-0000-0000-0000-00000000e1a1',
    'FASTEST_PICKUP', 5000, 'VERIFIED', 600
);

select set_config(
    'request.jwt.claim.sub',
    'a1000000-0000-0000-0000-000000000001',
    false
);

do $test$
declare
    v_result jsonb;
    v_payment jsonb;
    v_feedback jsonb;
begin
    v_result := public.ride_try_auto_match_v1(
        'a4000000-0000-0000-0000-000000000004', 1
    );
    if coalesce((v_result ->> 'matched')::boolean, false) is not true
       or (v_result ->> 'fare_minor')::bigint <> 4800
    then
        raise exception 'auto-match hardening failed: %', v_result;
    end if;
    if not exists (
        select 1 from public.ride_requests r
        where r.id = 'a4000000-0000-0000-0000-000000000004'
          and r.state = 'ASSIGNED'
          and r.assigned_driver_id = 'a2000000-0000-0000-0000-000000000002'
          and r.offered_fare_minor = 4800
    ) then
        raise exception 'auto-match did not persist authoritative assignment';
    end if;

    v_payment := public.ride_attest_payment_event_v1(
        'a4000000-0000-0000-0000-000000000004',
        'PAYMENT_METHOD_SELECTED', null
    );
    if (v_payment ->> 'amount_minor')::bigint <> 4800 then
        raise exception 'payment used a non-authoritative amount: %', v_payment;
    end if;
    perform public.ride_attest_payment_event_v1(
        'a4000000-0000-0000-0000-000000000004',
        'USER_MARKED_SENT', 'integration-reference'
    );

    perform set_config(
        'request.jwt.claim.sub',
        'a2000000-0000-0000-0000-000000000002',
        true
    );
    perform public.ride_attest_payment_event_v1(
        'a4000000-0000-0000-0000-000000000004',
        'DRIVER_MARKED_RECEIVED', null
    );
    begin
        perform public.ride_attest_payment_event_v1(
            'a4000000-0000-0000-0000-000000000004',
            'BANK_CONFIRMED', null
        );
        raise exception 'human actor fabricated bank confirmation';
    exception when insufficient_privilege then
        if sqlerrm <> 'BANK_CONFIRMATION_REQUIRES_TRUSTED_INGESTION' then
            raise;
        end if;
    end;

    update public.ride_requests
       set state = 'COMPLETED', final_fare_minor = 4800, completed_at = now()
     where id = 'a4000000-0000-0000-0000-000000000004';
    perform set_config(
        'request.jwt.claim.sub',
        'a1000000-0000-0000-0000-000000000001',
        true
    );
    v_feedback := public.ride_record_trip_feedback_v1(
        'a4000000-0000-0000-0000-000000000004', 5::smallint,
        array['COURTEOUS', 'SAFE_DRIVING']
    );
    if coalesce((v_feedback ->> 'already_recorded')::boolean, true) then
        raise exception 'first feedback was not recorded: %', v_feedback;
    end if;
    v_feedback := public.ride_record_trip_feedback_v1(
        'a4000000-0000-0000-0000-000000000004', 5::smallint,
        array['COURTEOUS', 'SAFE_DRIVING']
    );
    if coalesce((v_feedback ->> 'already_recorded')::boolean, false) is not true
       or (select count(*) from public.ride_trip_feedback
           where trip_id = 'a4000000-0000-0000-0000-000000000004') <> 1
    then
        raise exception 'feedback idempotency failed: %', v_feedback;
    end if;
end;
$test$;

select 'ride schema drift hardening integration: PASS' as result;
