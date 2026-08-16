\set ON_ERROR_STOP on

insert into auth.users(id) values
    ('89999999-1111-1111-1111-111111111111'),
    ('89999999-2222-2222-2222-222222222222');

set role authenticated;
select set_config(
    'request.jwt.claim.sub',
    '89999999-1111-1111-1111-111111111111',
    false
);
select set_config('request.jwt.claim.email', 'rider@example.test', false);

do $test$
declare
    v_passenger jsonb;
    v_driver jsonb;
    v_replay jsonb;
begin
    v_passenger := public.meet_activate_usage_profile_v1(
        'ride_passenger',
        'onboarding:ride_passenger:89999999-1111-1111-1111-111111111111'
    );
    if v_passenger #>> '{data,role}' <> 'ride_passenger' or
       v_passenger #>> '{data,mobility_role}' <> 'PASSENGER' or
       (v_passenger #>> '{data,verification_required}')::boolean
    then
        raise exception 'passenger activation assertion failed: %', v_passenger;
    end if;

    v_driver := public.meet_activate_usage_profile_v1(
        'ride_driver',
        'onboarding:ride_driver:89999999-1111-1111-1111-111111111111'
    );
    if v_driver #>> '{data,role}' <> 'ride_driver' or
       v_driver #>> '{data,mobility_role}' <> 'BOTH' or
       not (v_driver #>> '{data,verification_required}')::boolean
    then
        raise exception 'driver activation assertion failed: %', v_driver;
    end if;

    v_replay := public.meet_activate_usage_profile_v1(
        'ride_driver',
        'onboarding:ride_driver:89999999-1111-1111-1111-111111111111'
    );
    if v_replay #>> '{data,mobility_role}' <> 'BOTH' then
        raise exception 'idempotent replay changed mobility role: %', v_replay;
    end if;
end;
$test$;

reset role;

do $test$
declare
    v_roles bigint;
    v_events bigint;
    v_provider_rows bigint;
    v_vehicle_rows bigint;
begin
    select count(*) into v_roles
      from public.user_roles ur
      join public.user_profiles up on up.id = ur.user_profile_id
     where up.auth_user_id = '89999999-1111-1111-1111-111111111111'
       and ur.role_name in ('ride_passenger', 'ride_driver')
       and ur.is_active;
    select count(*) into v_events
      from public.usage_profile_activation_events
     where user_id = '89999999-1111-1111-1111-111111111111';
    select count(*) into v_provider_rows
      from public.provider_profiles pp
      join public.user_profiles up on up.id = pp.user_profile_id
     where up.auth_user_id = '89999999-1111-1111-1111-111111111111'
       and pp.provider_type = 'ride_driver';
    select count(*) into v_vehicle_rows
      from public.ride_driver_vehicles
     where driver_id = '89999999-1111-1111-1111-111111111111';

    if v_roles <> 2 or v_events <> 2 or
       v_provider_rows <> 0 or v_vehicle_rows <> 0
    then
        raise exception using message = format(
            'usage role invariant failed: roles=%s events=%s providers=%s vehicles=%s',
            v_roles, v_events, v_provider_rows, v_vehicle_rows
        );
    end if;
end;
$test$;

set role authenticated;
select set_config(
    'request.jwt.claim.sub',
    '89999999-2222-2222-2222-222222222222',
    false
);

do $test$
begin
    if exists (
        select 1 from public.usage_profile_activation_events
         where user_id = '89999999-1111-1111-1111-111111111111'
    ) then
        raise exception 'cross-principal activation event leak';
    end if;
end;
$test$;

reset role;
select 'ride usage profile integration: PASS' as result;
