-- MEET Viajes V3: ordered stops, declared payment, secure boarding,
-- first-confirmed driver claim, and privacy-preserving road intelligence.

alter table public.ride_requests
    add column if not exists payment_method text,
    add column if not exists quote_version bigint not null default 1,
    add column if not exists route_preference text,
    add column if not exists fare_breakdown jsonb not null default '{}'::jsonb;

alter table public.ride_requests
    drop constraint if exists ride_requests_payment_method_check;
alter table public.ride_requests
    add constraint ride_requests_payment_method_check
    check (payment_method is null or payment_method in ('CASH', 'SINPE'));

create table if not exists public.ride_request_stops (
    id uuid primary key default gen_random_uuid(),
    request_id uuid not null references public.ride_requests(id) on delete cascade,
    stop_order integer not null check (stop_order between 1 and 32),
    provider_place_id text,
    label text not null check (char_length(label) between 1 and 500),
    latitude double precision not null check (latitude between -90 and 90),
    longitude double precision not null check (longitude between -180 and 180),
    resolved_at timestamptz,
    created_at timestamptz not null default now(),
    unique (request_id, stop_order)
);

create index if not exists ride_request_stops_request_idx
    on public.ride_request_stops(request_id, stop_order);

create table if not exists public.ride_boarding_challenges (
    trip_id uuid primary key references public.ride_requests(id) on delete cascade,
    pin_hash text not null,
    failed_attempts integer not null default 0 check (failed_attempts between 0 and 20),
    locked_until timestamptz,
    expires_at timestamptz not null,
    verified_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.ride_road_incidents (
    id uuid primary key default gen_random_uuid(),
    reporter_id uuid not null references public.ride_profiles(user_id) on delete cascade,
    trip_id uuid references public.ride_requests(id) on delete set null,
    road_segment_id text not null check (char_length(road_segment_id) between 1 and 200),
    incident_type text not null check (
        incident_type in (
            'SLOW_TRAFFIC', 'VERY_SLOW_TRAFFIC', 'STALLED_VEHICLE', 'POTHOLE',
            'OBSTACLE', 'ROAD_CLOSED', 'WRONG_WAY_HAZARD', 'POLICE_PRESENCE',
            'TRAFFIC_CONTROL'
        )
    ),
    road_side text not null default 'NOT_APPLICABLE'
        check (road_side in ('LEFT', 'CENTER', 'RIGHT', 'NOT_APPLICABLE')),
    severity integer not null check (severity between 1 and 3),
    latitude double precision not null check (latitude between -90 and 90),
    longitude double precision not null check (longitude between -180 and 180),
    bearing_degrees real check (bearing_degrees is null or bearing_degrees between 0 and 360),
    accuracy_meters real check (accuracy_meters is null or accuracy_meters between 0 and 500),
    geohash_coarse text not null check (char_length(geohash_coarse) between 4 and 12),
    moderation_state text not null default 'VISIBLE'
        check (moderation_state in ('VISIBLE', 'LIMITED', 'HIDDEN', 'REVIEW')),
    created_at timestamptz not null default now(),
    expires_at timestamptz not null,
    check (created_at < expires_at)
);

create index if not exists ride_road_incidents_active_segment_idx
    on public.ride_road_incidents(road_segment_id, expires_at desc)
    where moderation_state = 'VISIBLE';
create index if not exists ride_road_incidents_coarse_idx
    on public.ride_road_incidents(geohash_coarse, expires_at desc);

create table if not exists public.ride_road_incident_votes (
    incident_id uuid not null references public.ride_road_incidents(id) on delete cascade,
    voter_id uuid not null references public.ride_profiles(user_id) on delete cascade,
    vote text not null check (vote in ('CONFIRM', 'DENY', 'CLEARED')),
    created_at timestamptz not null default now(),
    primary key (incident_id, voter_id)
);

create table if not exists public.ride_segment_speed_observations (
    id bigint generated always as identity primary key,
    observer_id uuid not null references public.ride_profiles(user_id) on delete cascade,
    trip_id uuid not null references public.ride_requests(id) on delete cascade,
    road_segment_id text not null check (char_length(road_segment_id) between 1 and 200),
    speed_mps real not null check (speed_mps between 0 and 100),
    accuracy_meters real check (accuracy_meters is null or accuracy_meters between 0 and 200),
    bearing_degrees real check (bearing_degrees is null or bearing_degrees between 0 and 360),
    captured_at timestamptz not null,
    time_bucket timestamptz not null,
    created_at timestamptz not null default now(),
    unique (observer_id, road_segment_id, time_bucket)
);

create index if not exists ride_segment_speed_recent_idx
    on public.ride_segment_speed_observations(road_segment_id, captured_at desc);

alter table public.ride_request_stops enable row level security;
alter table public.ride_boarding_challenges enable row level security;
alter table public.ride_road_incidents enable row level security;
alter table public.ride_road_incident_votes enable row level security;
alter table public.ride_segment_speed_observations enable row level security;

create policy ride_stops_visible_to_parties_or_searching_drivers
on public.ride_request_stops for select to authenticated
using (
    exists (
        select 1
        from public.ride_requests r
        where r.id = request_id
          and (
              r.passenger_id = (select auth.uid()) or
              r.assigned_driver_id = (select auth.uid()) or
              (
                  r.state in ('SEARCHING', 'OFFERED') and
                  exists (
                      select 1 from public.ride_driver_vehicles v
                      where v.driver_id = (select auth.uid())
                        and v.is_active
                        and v.verification_status = 'VERIFIED'
                  )
              )
          )
    )
);

create policy ride_stops_passenger_insert
on public.ride_request_stops for insert to authenticated
with check (
    exists (
        select 1 from public.ride_requests r
        where r.id = request_id
          and r.passenger_id = (select auth.uid())
          and r.state in ('DRAFT', 'SEARCHING')
    )
);

create policy ride_stops_passenger_update
on public.ride_request_stops for update to authenticated
using (
    exists (
        select 1 from public.ride_requests r
        where r.id = request_id
          and r.passenger_id = (select auth.uid())
          and r.state in ('DRAFT', 'SEARCHING')
    )
)
with check (
    exists (
        select 1 from public.ride_requests r
        where r.id = request_id
          and r.passenger_id = (select auth.uid())
          and r.state in ('DRAFT', 'SEARCHING')
    )
);

create policy ride_stops_passenger_delete
on public.ride_request_stops for delete to authenticated
using (
    exists (
        select 1 from public.ride_requests r
        where r.id = request_id
          and r.passenger_id = (select auth.uid())
          and r.state in ('DRAFT', 'SEARCHING')
    )
);

-- Challenges have no direct policies. Only security-definer RPCs can access PIN hashes.

create policy ride_incidents_read_active
on public.ride_road_incidents for select to authenticated
using (moderation_state in ('VISIBLE', 'LIMITED') and expires_at > now());

create policy ride_incidents_driver_insert
on public.ride_road_incidents for insert to authenticated
with check (
    reporter_id = (select auth.uid()) and
    expires_at <= now() + interval '6 hours' and
    exists (
        select 1 from public.ride_driver_vehicles v
        where v.driver_id = (select auth.uid())
          and v.is_active
          and v.verification_status = 'VERIFIED'
    )
);

create policy ride_incident_votes_read
on public.ride_road_incident_votes for select to authenticated
using (true);

create policy ride_incident_votes_own_insert
on public.ride_road_incident_votes for insert to authenticated
with check (voter_id = (select auth.uid()));

create policy ride_incident_votes_own_update
on public.ride_road_incident_votes for update to authenticated
using (voter_id = (select auth.uid()))
with check (voter_id = (select auth.uid()));

create policy ride_speed_own_insert
on public.ride_segment_speed_observations for insert to authenticated
with check (
    observer_id = (select auth.uid()) and
    captured_at between now() - interval '5 minutes' and now() + interval '1 minute' and
    exists (
        select 1 from public.ride_requests r
        where r.id = trip_id
          and r.assigned_driver_id = (select auth.uid())
          and r.state in ('DRIVER_EN_ROUTE', 'ARRIVED', 'PASSENGER_ONBOARD', 'IN_PROGRESS')
    )
);

create policy ride_speed_read_recent_aggregate_inputs
on public.ride_segment_speed_observations for select to authenticated
using (captured_at > now() - interval '15 minutes');

create or replace function public.ride_create_boarding_pin(
    p_trip_id uuid,
    p_idempotency_key text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := (select auth.uid());
    v_request public.ride_requests%rowtype;
    v_pin text;
    v_random_bytes bytea;
begin
    if v_user_id is null or nullif(trim(p_idempotency_key), '') is null then
        raise exception 'Authentication and idempotency key are required';
    end if;

    select * into v_request
    from public.ride_requests
    where id = p_trip_id
    for update;

    if not found or v_request.passenger_id <> v_user_id then
        raise exception 'Passenger is not authorized for this trip';
    end if;
    if v_request.state not in ('ASSIGNED', 'DRIVER_EN_ROUTE', 'ARRIVED') then
        raise exception 'Boarding PIN is not available in this state';
    end if;

    v_random_bytes := extensions.gen_random_bytes(2);
    v_pin := lpad((
        (get_byte(v_random_bytes, 0) * 256 + get_byte(v_random_bytes, 1)) % 10000
    )::text, 4, '0');

    insert into public.ride_boarding_challenges(
        trip_id, pin_hash, failed_attempts, locked_until, expires_at,
        verified_at, updated_at
    )
    values (
        p_trip_id, extensions.crypt(v_pin, extensions.gen_salt('bf', 8)),
        0, null, now() + interval '30 minutes', null, now()
    )
    on conflict (trip_id) do update
    set pin_hash = excluded.pin_hash,
        failed_attempts = 0,
        locked_until = null,
        expires_at = excluded.expires_at,
        verified_at = null,
        updated_at = now();

    insert into public.ride_trip_events(
        trip_id, actor_id, event_type, from_state, to_state, payload, idempotency_key
    )
    values (
        p_trip_id, v_user_id, 'BOARDING_PIN_ISSUED', v_request.state, v_request.state,
        jsonb_build_object('expires_at', now() + interval '30 minutes'),
        p_idempotency_key
    )
    on conflict (idempotency_key) do nothing;

    return jsonb_build_object('trip_id', p_trip_id, 'pin', v_pin, 'expires_in_seconds', 1800);
end;
$$;

create or replace function public.ride_verify_boarding_pin(
    p_trip_id uuid,
    p_pin text,
    p_idempotency_key text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := (select auth.uid());
    v_request public.ride_requests%rowtype;
    v_challenge public.ride_boarding_challenges%rowtype;
    v_retry_after integer;
begin
    if v_user_id is null or p_pin !~ '^[0-9]{4}$' or nullif(trim(p_idempotency_key), '') is null then
        raise exception 'Authentication, four-digit PIN, and idempotency key are required';
    end if;

    if exists (
        select 1 from public.ride_trip_events e
        where e.trip_id = p_trip_id and e.idempotency_key = p_idempotency_key
    ) then
        return jsonb_build_object('status', 'VERIFIED', 'trip_id', p_trip_id);
    end if;

    select * into v_request
    from public.ride_requests
    where id = p_trip_id
    for update;

    if not found or v_request.assigned_driver_id <> v_user_id then
        raise exception 'Assigned driver is not authorized for this trip';
    end if;
    if v_request.state <> 'ARRIVED' then
        raise exception 'Trip must be in ARRIVED state';
    end if;

    select * into v_challenge
    from public.ride_boarding_challenges
    where trip_id = p_trip_id
    for update;

    if not found or v_challenge.expires_at <= now() or v_challenge.verified_at is not null then
        return jsonb_build_object('status', 'EXPIRED_OR_USED', 'trip_id', p_trip_id);
    end if;
    if v_challenge.locked_until is not null and v_challenge.locked_until > now() then
        v_retry_after := greatest(1, extract(epoch from (v_challenge.locked_until - now()))::integer);
        return jsonb_build_object(
            'status', 'LOCKED', 'trip_id', p_trip_id, 'retry_after_seconds', v_retry_after
        );
    end if;

    if extensions.crypt(p_pin, v_challenge.pin_hash) <> v_challenge.pin_hash then
        update public.ride_boarding_challenges
        set failed_attempts = failed_attempts + 1,
            locked_until = case
                when failed_attempts + 1 >= 5 then now() + interval '5 minutes'
                else null
            end,
            updated_at = now()
        where trip_id = p_trip_id;
        return jsonb_build_object('status', 'INVALID', 'trip_id', p_trip_id);
    end if;

    update public.ride_boarding_challenges
    set verified_at = now(), updated_at = now()
    where trip_id = p_trip_id;

    update public.ride_requests
    set state = 'PASSENGER_ONBOARD', version = version + 1, updated_at = now()
    where id = p_trip_id
    returning * into v_request;

    insert into public.ride_trip_events(
        trip_id, actor_id, event_type, from_state, to_state, payload, idempotency_key
    )
    values (
        p_trip_id, v_user_id, 'BOARDING_PIN_VERIFIED', 'ARRIVED', 'PASSENGER_ONBOARD',
        '{}'::jsonb, p_idempotency_key
    );

    return jsonb_build_object('status', 'VERIFIED', 'trip', to_jsonb(v_request));
end;
$$;

create or replace function public.ride_claim_request(
    p_request_id uuid,
    p_vehicle_id uuid,
    p_idempotency_key text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := (select auth.uid());
    v_request public.ride_requests%rowtype;
    v_vehicle public.ride_driver_vehicles%rowtype;
    v_commission bigint;
    v_posted bigint;
    v_reserved bigint;
begin
    if v_user_id is null or nullif(trim(p_idempotency_key), '') is null then
        raise exception 'Authentication and idempotency key are required';
    end if;

    if exists (
        select 1 from public.ride_trip_events e
        where e.trip_id = p_request_id
          and e.idempotency_key = p_idempotency_key
          and e.event_type = 'DRIVER_CLAIMED'
    ) then
        return jsonb_build_object('status', 'CLAIMED', 'trip_id', p_request_id);
    end if;

    select * into v_request
    from public.ride_requests
    where id = p_request_id
    for update;

    if not found then
        return jsonb_build_object('status', 'NOT_FOUND');
    end if;
    if v_request.assigned_driver_id is not null or v_request.state not in ('SEARCHING', 'OFFERED') then
        return jsonb_build_object('status', 'ALREADY_CLAIMED', 'trip_id', p_request_id);
    end if;

    select * into v_vehicle
    from public.ride_driver_vehicles
    where id = p_vehicle_id
      and driver_id = v_user_id
      and is_active
      and verification_status = 'VERIFIED';

    if not found then
        raise exception 'An active verified vehicle is required';
    end if;

    v_commission := round(v_request.offered_fare_minor::numeric * 500 / 10000)::bigint;

    select coalesce(sum(
        case
            when l.direction = 'CREDIT' then l.amount_minor
            when l.direction = 'DEBIT' and l.entry_type <> 'COMMISSION_RESERVED' then -l.amount_minor
            else 0
        end
    ), 0)
    into v_posted
    from public.ride_wallet_ledger l
    where l.driver_id = v_user_id and l.currency = v_request.currency;

    select coalesce(sum(r.amount_minor), 0)
    into v_reserved
    from public.ride_commission_reservations r
    where r.driver_id = v_user_id
      and r.currency = v_request.currency
      and r.state = 'RESERVED';

    if v_posted - v_reserved < v_commission then
        return jsonb_build_object(
            'status', 'INSUFFICIENT_BALANCE',
            'required_minor', v_commission,
            'available_minor', greatest(0, v_posted - v_reserved)
        );
    end if;

    insert into public.ride_commission_reservations(
        trip_id, driver_id, amount_minor, currency, state, reserve_idempotency_key
    )
    values (
        p_request_id, v_user_id, v_commission, v_request.currency,
        'RESERVED', p_idempotency_key || ':reserve'
    );

    insert into public.ride_wallet_ledger(
        driver_id, idempotency_key, entry_type, amount_minor, currency,
        direction, trip_id, withdrawable
    )
    values (
        v_user_id, p_idempotency_key || ':ledger-reserve', 'COMMISSION_RESERVED',
        v_commission, v_request.currency, 'DEBIT', p_request_id, false
    );

    update public.ride_requests
    set assigned_driver_id = v_user_id,
        assigned_vehicle_id = p_vehicle_id,
        state = 'ASSIGNED',
        version = version + 1,
        updated_at = now()
    where id = p_request_id
      and assigned_driver_id is null
      and state in ('SEARCHING', 'OFFERED')
    returning * into v_request;

    if not found then
        raise exception 'Concurrent claim invariant violated';
    end if;

    update public.ride_offers
    set state = case when driver_id = v_user_id then 'ACCEPTED' else 'REJECTED' end,
        updated_at = now()
    where request_id = p_request_id and state = 'PENDING';

    insert into public.ride_trip_events(
        trip_id, actor_id, event_type, from_state, to_state, payload, idempotency_key
    )
    values (
        p_request_id, v_user_id, 'DRIVER_CLAIMED', 'SEARCHING', 'ASSIGNED',
        jsonb_build_object(
            'vehicle_id', p_vehicle_id,
            'commission_reserved_minor', v_commission,
            'currency', v_request.currency
        ),
        p_idempotency_key
    );

    return jsonb_build_object('status', 'CLAIMED', 'trip', to_jsonb(v_request));
end;
$$;

revoke all on function public.ride_create_boarding_pin(uuid, text) from public;
revoke all on function public.ride_verify_boarding_pin(uuid, text, text) from public;
revoke all on function public.ride_claim_request(uuid, uuid, text) from public;
grant execute on function public.ride_create_boarding_pin(uuid, text) to authenticated;
grant execute on function public.ride_verify_boarding_pin(uuid, text, text) to authenticated;
grant execute on function public.ride_claim_request(uuid, uuid, text) to authenticated;
