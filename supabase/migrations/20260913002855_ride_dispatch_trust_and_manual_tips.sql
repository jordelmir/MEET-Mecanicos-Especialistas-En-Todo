-- Viajes marketplace hardening: 30-minute dispatch leases, owner-scoped
-- driver decisions, trusted-driver invitations and manual-only tips.

alter table public.ride_requests
    add column if not exists dispatch_expires_at timestamptz,
    add column if not exists preferred_driver_id uuid references public.ride_profiles(user_id) on delete set null,
    add column if not exists preferred_driver_invited_at timestamptz,
    add column if not exists driver_rejection_count integer not null default 0
        check (driver_rejection_count >= 0);

update public.ride_requests
set dispatch_expires_at = created_at + interval '30 minutes'
where dispatch_expires_at is null;

alter table public.ride_requests
    alter column dispatch_expires_at set default (clock_timestamp() + interval '30 minutes'),
    alter column dispatch_expires_at set not null;

create index if not exists ride_requests_dispatch_expiry_idx
    on public.ride_requests(dispatch_expires_at)
    where state in ('SEARCHING', 'OFFERED');
create index if not exists ride_requests_preferred_driver_idx
    on public.ride_requests(preferred_driver_id, created_at desc)
    where preferred_driver_id is not null;

create table if not exists public.ride_driver_request_decisions (
    trip_id uuid not null references public.ride_requests(id) on delete cascade,
    driver_id uuid not null references public.ride_profiles(user_id) on delete cascade,
    action text not null check (action in ('DISMISS', 'REJECT')),
    idempotency_key uuid not null unique,
    created_at timestamptz not null default clock_timestamp(),
    updated_at timestamptz not null default clock_timestamp(),
    primary key (trip_id, driver_id)
);

alter table public.ride_driver_request_decisions enable row level security;
alter table public.ride_driver_request_decisions force row level security;
drop policy if exists ride_driver_request_decisions_owner_read
    on public.ride_driver_request_decisions;
create policy ride_driver_request_decisions_owner_read
    on public.ride_driver_request_decisions for select to authenticated
    using (driver_id = (select auth.uid()));
revoke insert, update, delete on public.ride_driver_request_decisions from public, anon, authenticated;
grant select on public.ride_driver_request_decisions to authenticated;

create table if not exists public.ride_trusted_driver_invites (
    trip_id uuid primary key references public.ride_requests(id) on delete cascade,
    passenger_id uuid not null references public.ride_profiles(user_id) on delete cascade,
    driver_id uuid not null references public.ride_profiles(user_id) on delete cascade,
    state text not null default 'PENDING' check (state in ('PENDING', 'SEEN', 'ACCEPTED', 'DECLINED', 'EXPIRED')),
    idempotency_key uuid not null unique,
    created_at timestamptz not null default clock_timestamp(),
    updated_at timestamptz not null default clock_timestamp()
);

alter table public.ride_trusted_driver_invites enable row level security;
alter table public.ride_trusted_driver_invites force row level security;
drop policy if exists ride_trusted_driver_invites_party_read
    on public.ride_trusted_driver_invites;
create policy ride_trusted_driver_invites_party_read
    on public.ride_trusted_driver_invites for select to authenticated
    using (passenger_id = (select auth.uid()) or driver_id = (select auth.uid()));
revoke insert, update, delete on public.ride_trusted_driver_invites from public, anon, authenticated;
grant select on public.ride_trusted_driver_invites to authenticated;

alter table public.ride_push_outbox
    drop constraint if exists ride_push_outbox_notification_type_check;
alter table public.ride_push_outbox
    add constraint ride_push_outbox_notification_type_check check (
        notification_type in (
            'IDLE_DEMAND', 'DESTINATION_ETA_7_MIN', 'DRIVER_ARRIVED', 'TRIP_ASSIGNED',
            'DRIVER_REJECTED', 'TRUSTED_DRIVER_INVITE', 'REQUEST_EXPIRED'
        )
    );

create or replace function public.ride_expire_stale_requests_v1()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_actor uuid := (select auth.uid());
    v_expired integer := 0;
begin
    if v_actor is null then
        raise exception using errcode = '42501', message = 'UNAUTHENTICATED';
    end if;

    with candidates as materialized (
        select r.id, r.passenger_id, r.state as previous_state
          from public.ride_requests r
         where r.state in ('SEARCHING', 'OFFERED')
           and r.dispatch_expires_at <= clock_timestamp()
         for update skip locked
    ), expired as (
        update public.ride_requests r
           set state = 'EXPIRED',
               version = r.version + 1,
               updated_at = clock_timestamp()
          from candidates c
         where r.id = c.id
        returning r.id, r.passenger_id, r.version, c.previous_state
    ), events as (
        insert into public.ride_trip_events(
            trip_id, actor_id, event_type, from_state, to_state, payload, idempotency_key
        )
        select id, null, 'REQUEST_EXPIRED', previous_state, 'EXPIRED',
               jsonb_build_object('version', version, 'next_action', 'OPEN_BID_REPRICE'),
               'dispatch-expired:' || id::text || ':' || version::text
          from expired
        on conflict (idempotency_key) do nothing
    ), notifications as (
        insert into public.ride_push_outbox(
            recipient_id, trip_id, notification_type, title, body, dedupe_key
        )
        select passenger_id, id, 'REQUEST_EXPIRED',
               'Tu solicitud de viaje venció',
               'Vuelve a pedir por tiempo y distancia o sube tu oferta en Pon tu precio.',
               'request-expired:' || id::text || ':' || version::text
          from expired
        on conflict (dedupe_key) do nothing
    )
    select count(*) into v_expired from expired;

    update public.ride_offers o
       set state = 'WITHDRAWN', updated_at = clock_timestamp()
     where o.state = 'PENDING'
       and exists (
           select 1 from public.ride_requests r
            where r.id = o.request_id and r.state = 'EXPIRED'
       );

    return jsonb_build_object('ok', true, 'expired_count', v_expired);
end;
$$;

revoke all on function public.ride_expire_stale_requests_v1() from public, anon;
grant execute on function public.ride_expire_stale_requests_v1() to authenticated, service_role;

create or replace function public.ride_driver_decide_request_v1(
    p_trip_id uuid,
    p_action text,
    p_idempotency_key uuid
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_actor uuid := (select auth.uid());
    v_trip public.ride_requests%rowtype;
    v_existing public.ride_driver_request_decisions%rowtype;
    v_eligible boolean;
begin
    if v_actor is null then
        raise exception using errcode = '42501', message = 'UNAUTHENTICATED';
    end if;
    if p_idempotency_key is null then
        raise exception using errcode = '22023', message = 'IDEMPOTENCY_KEY_REQUIRED';
    end if;
    if p_action not in ('DISMISS', 'REJECT') then
        raise exception using errcode = '22023', message = 'INVALID_DRIVER_DECISION';
    end if;

    select * into v_existing
      from public.ride_driver_request_decisions
     where idempotency_key = p_idempotency_key;
    if found then
        if v_existing.driver_id is distinct from v_actor or
           v_existing.trip_id is distinct from p_trip_id or
           v_existing.action is distinct from p_action then
            raise exception using errcode = '22023', message = 'IDEMPOTENCY_CONFLICT';
        end if;
        return jsonb_build_object('ok', true, 'action', v_existing.action, 'idempotent', true);
    end if;

    select * into v_trip from public.ride_requests where id = p_trip_id for update;
    if not found then
        raise exception using errcode = 'P0002', message = 'RIDE_NOT_FOUND';
    end if;
    if v_trip.passenger_id = v_actor then
        raise exception using errcode = '42501', message = 'SELF_RIDE_FORBIDDEN';
    end if;
    if v_trip.state not in ('SEARCHING', 'OFFERED') or
       v_trip.dispatch_expires_at <= clock_timestamp() then
        raise exception using errcode = '22023', message = 'RIDE_NOT_OPEN';
    end if;
    select exists (
        select 1 from public.ride_driver_vehicles v
         where v.driver_id = v_actor
           and public.ride_vehicle_dispatch_eligible(v.id, v_actor)
    ) into v_eligible;
    if not v_eligible then
        raise exception using errcode = '42501', message = 'DRIVER_NOT_DISPATCH_ELIGIBLE';
    end if;

    insert into public.ride_driver_request_decisions(
        trip_id, driver_id, action, idempotency_key
    ) values (p_trip_id, v_actor, p_action, p_idempotency_key)
    on conflict (trip_id, driver_id) do update
       set action = excluded.action,
           idempotency_key = excluded.idempotency_key,
           updated_at = clock_timestamp();

    update public.ride_trusted_driver_invites
       set state = case when p_action = 'REJECT' then 'DECLINED' else 'SEEN' end,
           updated_at = clock_timestamp()
     where trip_id = p_trip_id
       and driver_id = v_actor
       and state in ('PENDING', 'SEEN');

    if p_action = 'REJECT' then
        update public.ride_requests
           set driver_rejection_count = driver_rejection_count + 1,
               updated_at = clock_timestamp()
         where id = p_trip_id;
        insert into public.ride_trip_events(
            trip_id, actor_id, event_type, from_state, to_state, payload, idempotency_key
        ) values (
            p_trip_id, v_actor, 'DRIVER_REJECTED_REQUEST', v_trip.state, v_trip.state,
            jsonb_build_object('driver_rejection_count', v_trip.driver_rejection_count + 1),
            'driver-reject:' || p_idempotency_key::text
        ) on conflict (idempotency_key) do nothing;
        insert into public.ride_push_outbox(
            recipient_id, trip_id, notification_type, title, body, dedupe_key
        ) values (
            v_trip.passenger_id, p_trip_id, 'DRIVER_REJECTED',
            'Un chofer rechazó tu oferta',
            'Tu solicitud sigue activa para otros choferes.',
            'driver-rejected:' || p_trip_id::text || ':' || v_actor::text
        ) on conflict (dedupe_key) do nothing;
    end if;

    return jsonb_build_object('ok', true, 'action', p_action, 'idempotent', false);
end;
$$;

revoke all on function public.ride_driver_decide_request_v1(uuid, text, uuid) from public, anon;
grant execute on function public.ride_driver_decide_request_v1(uuid, text, uuid) to authenticated, service_role;

create or replace function public.ride_list_trusted_drivers_v1()
returns jsonb
language sql
security definer
set search_path = ''
stable
as $$
    select coalesce(jsonb_agg(row_data order by last_completed_at desc), '[]'::jsonb)
    from (
        select jsonb_build_object(
            'driver_id', r.assigned_driver_id,
            'display_name', max(p.display_name),
            'completed_trips', count(*),
            'last_completed_at', max(r.completed_at),
            'vehicle_name', max(v.display_name),
            'is_available', coalesce(bool_or(
                v.is_active
                and v.verification_status = 'VERIFIED'
                and presence.availability in ('AVAILABLE', 'OFFERING', 'FINISHING_CURRENT_TRIP')
                and presence.last_seen_at >= now() - interval '5 minutes'
            ), false)
        ) as row_data,
        max(r.completed_at) as last_completed_at
        from public.ride_requests r
        join public.ride_profiles p on p.user_id = r.assigned_driver_id
        left join public.ride_driver_vehicles v
          on v.driver_id = r.assigned_driver_id and v.is_active
        left join public.ride_driver_presence presence
          on presence.driver_id = r.assigned_driver_id
        where r.passenger_id = auth.uid()
          and r.state = 'COMPLETED'
          and r.assigned_driver_id is not null
          and r.assigned_driver_id <> auth.uid()
        group by r.assigned_driver_id
    ) trusted;
$$;

revoke all on function public.ride_list_trusted_drivers_v1() from public, anon;
grant execute on function public.ride_list_trusted_drivers_v1() to authenticated, service_role;

create or replace function public.ride_invite_trusted_driver_v1(
    p_trip_id uuid,
    p_driver_id uuid,
    p_idempotency_key uuid
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_actor uuid := (select auth.uid());
    v_trip public.ride_requests%rowtype;
begin
    if v_actor is null then
        raise exception using errcode = '42501', message = 'UNAUTHENTICATED';
    end if;
    if p_driver_id is null or p_idempotency_key is null or p_driver_id = v_actor then
        raise exception using errcode = '22023', message = 'INVALID_TRUSTED_DRIVER_INVITE';
    end if;
    if not exists (
        select 1 from public.ride_requests history
         where history.passenger_id = v_actor
           and history.assigned_driver_id = p_driver_id
           and history.state = 'COMPLETED'
    ) then
        raise exception using errcode = '42501', message = 'TRUST_RELATIONSHIP_NOT_ESTABLISHED';
    end if;

    select * into v_trip from public.ride_requests where id = p_trip_id for update;
    if not found or v_trip.passenger_id is distinct from v_actor then
        raise exception using errcode = '42501', message = 'NOT_PASSENGER';
    end if;
    if v_trip.state not in ('SEARCHING', 'OFFERED') or
       v_trip.dispatch_expires_at <= clock_timestamp() then
        raise exception using errcode = '22023', message = 'RIDE_NOT_OPEN';
    end if;

    insert into public.ride_trusted_driver_invites(
        trip_id, passenger_id, driver_id, idempotency_key
    ) values (p_trip_id, v_actor, p_driver_id, p_idempotency_key)
    on conflict (trip_id) do update
       set driver_id = excluded.driver_id,
           idempotency_key = excluded.idempotency_key,
           state = 'PENDING',
           updated_at = clock_timestamp();

    update public.ride_requests
       set preferred_driver_id = p_driver_id,
           preferred_driver_invited_at = clock_timestamp(),
           updated_at = clock_timestamp()
     where id = p_trip_id;

    insert into public.ride_push_outbox(
        recipient_id, trip_id, notification_type, title, body, dedupe_key
    ) values (
        p_driver_id, p_trip_id, 'TRUSTED_DRIVER_INVITE',
        'Solicitud de un pasajero de confianza',
        'Un pasajero que ya llevaste quiere solicitarte este viaje.',
        'trusted-driver:' || p_trip_id::text || ':' || p_driver_id::text
    ) on conflict (dedupe_key) do nothing;

    return jsonb_build_object('ok', true, 'driver_id', p_driver_id, 'state', 'PENDING');
end;
$$;

revoke all on function public.ride_invite_trusted_driver_v1(uuid, uuid, uuid) from public, anon;
grant execute on function public.ride_invite_trusted_driver_v1(uuid, uuid, uuid) to authenticated, service_role;

alter table public.ride_tips
    add column if not exists delivery_method text,
    add column if not exists delivery_state text;
update public.ride_tips
   set delivery_method = coalesce(delivery_method, 'CASH'),
       delivery_state = coalesce(delivery_state, 'PLEDGED');
alter table public.ride_tips
    alter column delivery_method set not null,
    alter column delivery_state set not null;
alter table public.ride_tips
    drop constraint if exists ride_tips_delivery_method_check,
    drop constraint if exists ride_tips_delivery_state_check;
alter table public.ride_tips
    add constraint ride_tips_delivery_method_check check (delivery_method in ('CASH', 'SINPE')),
    add constraint ride_tips_delivery_state_check check (delivery_state in ('PLEDGED', 'CONFIRMED_BY_DRIVER', 'DISPUTED'));
create unique index if not exists ride_tips_one_per_ride_idx on public.ride_tips(ride_id);

drop function if exists public.ride_submit_tip_v1(text, bigint, text);
create function public.ride_submit_tip_v1(
    p_ride_id text,
    p_tip_minor bigint,
    p_currency text,
    p_delivery_method text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_actor uuid := (select auth.uid());
    v_ride public.ride_requests%rowtype;
    v_tip public.ride_tips%rowtype;
begin
    if v_actor is null then
        raise exception using errcode = '42501', message = 'UNAUTHENTICATED';
    end if;
    if p_tip_minor is null or p_tip_minor <= 0 or p_tip_minor > 100000 then
        raise exception using errcode = '22023', message = 'INVALID_TIP_AMOUNT';
    end if;
    if p_delivery_method not in ('CASH', 'SINPE') then
        raise exception using errcode = '22023', message = 'TIP_MUST_BE_CASH_OR_SINPE';
    end if;
    select * into v_ride from public.ride_requests where id = p_ride_id::uuid for update;
    if not found or v_ride.passenger_id is distinct from v_actor then
        raise exception using errcode = '42501', message = 'NOT_PASSENGER';
    end if;
    if v_ride.state <> 'COMPLETED' or v_ride.assigned_driver_id is null then
        raise exception using errcode = '22023', message = 'RIDE_NOT_COMPLETED';
    end if;
    if p_currency not in ('CRC', 'USD') or p_currency <> v_ride.currency then
        raise exception using errcode = '22023', message = 'CURRENCY_MISMATCH';
    end if;

    insert into public.ride_tips(
        ride_id, passenger_id, driver_id, tip_minor, currency,
        delivery_method, delivery_state
    ) values (
        v_ride.id::text, v_actor, v_ride.assigned_driver_id, p_tip_minor,
        p_currency, p_delivery_method, 'PLEDGED'
    )
    on conflict (ride_id) do update set
        tip_minor = excluded.tip_minor,
        currency = excluded.currency,
        delivery_method = excluded.delivery_method
    where public.ride_tips.passenger_id = v_actor
      and public.ride_tips.delivery_state = 'PLEDGED'
    returning * into v_tip;

    if not found then
        raise exception using errcode = '22023', message = 'TIP_ALREADY_FINALIZED';
    end if;

    -- No wallet or ledger mutation occurs here. This is only a manual-delivery pledge.
    return jsonb_build_object(
        'ok', true,
        'tip_minor', v_tip.tip_minor,
        'currency', v_tip.currency,
        'delivery_method', v_tip.delivery_method,
        'delivery_state', v_tip.delivery_state,
        'wallet_credited', false
    );
end;
$$;

revoke all on function public.ride_submit_tip_v1(text, bigint, text, text) from public, anon;
grant execute on function public.ride_submit_tip_v1(text, bigint, text, text) to authenticated, service_role;

-- Compatibility for already-installed clients. It records a manual cash pledge
-- and never credits a wallet; new clients always ask for the delivery channel.
create or replace function public.ride_submit_tip_v1(
    p_ride_id text,
    p_tip_minor bigint,
    p_currency text
)
returns jsonb
language sql
security definer
set search_path = ''
as $$
    select public.ride_submit_tip_v1(p_ride_id, p_tip_minor, p_currency, 'CASH')
        || jsonb_build_object('legacy_delivery_defaulted', true);
$$;
revoke all on function public.ride_submit_tip_v1(text, bigint, text) from public, anon;
grant execute on function public.ride_submit_tip_v1(text, bigint, text) to authenticated, service_role;

comment on column public.ride_tips.delivery_method is
    'Manual delivery channel chosen by rider. Never mints wallet balance.';
comment on table public.ride_driver_request_decisions is
    'Owner-scoped dismiss/reject decisions; neither action cancels the passenger request.';
