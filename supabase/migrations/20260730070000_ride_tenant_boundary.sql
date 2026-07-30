-- MEET Viajes tenant boundary. The existing global marketplace becomes the
-- explicit PLATFORM tenant. Private tenants remain backend-provisioned and
-- fail closed until their memberships and vehicles exist.

create table if not exists public.ride_tenants (
    id uuid primary key,
    tenant_type text not null check (
        tenant_type in (
            'PLATFORM', 'COOPERATIVE', 'DISPATCH_CENTER', 'FLEET',
            'HOTEL', 'COMPANY', 'INSTITUTION'
        )
    ),
    legal_name text not null check (char_length(legal_name) between 2 and 180),
    display_name text not null check (char_length(display_name) between 2 and 120),
    country_code text not null check (country_code ~ '^[A-Z]{2}$'),
    default_currency text not null check (default_currency ~ '^[A-Z]{3}$'),
    dispatch_strategy text not null default 'FIRST_VALID_CLAIM' check (
        dispatch_strategy in (
            'NEAREST_ETA', 'FIFO_ZONE', 'FAIR_ROTATION', 'MANUAL_DISPATCH',
            'FIRST_VALID_CLAIM', 'PASSENGER_OFFER',
            'DRIVER_COUNTER_OFFER', 'SCHEDULED_RESERVATION',
            'PREFERRED_DRIVER', 'FEDERATED_OVERFLOW'
        )
    ),
    status text not null default 'CONFIGURING' check (
        status in ('CONFIGURING', 'ACTIVE', 'SUSPENDED', 'CLOSED')
    ),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

insert into public.ride_tenants(
    id, tenant_type, legal_name, display_name, country_code,
    default_currency, dispatch_strategy, status
) values (
    '00000000-0000-0000-0000-00000000e1a1',
    'PLATFORM',
    'Elysium Vanguard Platform',
    'Elysium Vanguard',
    'CR',
    'CRC',
    'FIRST_VALID_CLAIM',
    'ACTIVE'
)
on conflict (id) do nothing;

create table if not exists public.ride_tenant_memberships (
    tenant_id uuid not null references public.ride_tenants(id) on delete restrict,
    user_id uuid not null references auth.users(id) on delete cascade,
    role text not null check (
        role in (
            'TENANT_ADMIN', 'DISPATCHER', 'DRIVER', 'SUPPORT',
            'FINANCE', 'AUDITOR', 'CORPORATE_RIDER'
        )
    ),
    status text not null default 'ACTIVE' check (
        status in ('INVITED', 'ACTIVE', 'SUSPENDED', 'REVOKED')
    ),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (tenant_id, user_id, role)
);

create index if not exists ride_tenant_memberships_user_idx
    on public.ride_tenant_memberships(user_id, status, tenant_id);

alter table public.ride_driver_vehicles
    add column if not exists tenant_id uuid not null
        default '00000000-0000-0000-0000-00000000e1a1'
        references public.ride_tenants(id) on delete restrict;
alter table public.ride_requests
    add column if not exists tenant_id uuid not null
        default '00000000-0000-0000-0000-00000000e1a1'
        references public.ride_tenants(id) on delete restrict;
alter table public.ride_offers
    add column if not exists tenant_id uuid not null
        default '00000000-0000-0000-0000-00000000e1a1'
        references public.ride_tenants(id) on delete restrict;
alter table public.ride_command_receipts
    add column if not exists tenant_id uuid not null
        default '00000000-0000-0000-0000-00000000e1a1'
        references public.ride_tenants(id) on delete restrict;
alter table public.ride_fare_quotes
    add column if not exists tenant_id uuid not null
        default '00000000-0000-0000-0000-00000000e1a1'
        references public.ride_tenants(id) on delete restrict;
alter table public.ride_operational_holds
    add column if not exists tenant_id uuid not null
        default '00000000-0000-0000-0000-00000000e1a1'
        references public.ride_tenants(id) on delete restrict;
alter table public.ride_commission_reservations
    add column if not exists tenant_id uuid not null
        default '00000000-0000-0000-0000-00000000e1a1'
        references public.ride_tenants(id) on delete restrict;
alter table public.ride_ledger_transactions
    add column if not exists tenant_id uuid not null
        default '00000000-0000-0000-0000-00000000e1a1'
        references public.ride_tenants(id) on delete restrict;
alter table public.ride_commission_calculations
    add column if not exists tenant_id uuid not null
        default '00000000-0000-0000-0000-00000000e1a1'
        references public.ride_tenants(id) on delete restrict;
alter table public.ride_safety_events
    add column if not exists tenant_id uuid not null
        default '00000000-0000-0000-0000-00000000e1a1'
        references public.ride_tenants(id) on delete restrict;
alter table public.ride_support_cases
    add column if not exists tenant_id uuid not null
        default '00000000-0000-0000-0000-00000000e1a1'
        references public.ride_tenants(id) on delete restrict;

create index if not exists ride_requests_tenant_state_created_idx
    on public.ride_requests(tenant_id, state, created_at desc);
create index if not exists ride_driver_vehicles_tenant_driver_idx
    on public.ride_driver_vehicles(tenant_id, driver_id, is_active);
create index if not exists ride_offers_tenant_request_idx
    on public.ride_offers(tenant_id, request_id, state);

create or replace function public.ride_is_active_tenant_member(
    p_tenant_id uuid,
    p_roles text[]
)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
          from public.ride_tenant_memberships m
         where m.tenant_id = p_tenant_id
           and m.user_id = (select auth.uid())
           and m.status = 'ACTIVE'
           and m.role = any(p_roles)
    );
$$;

create or replace function public.ride_inherit_trip_tenant()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_tenant_id uuid;
begin
    if new.trip_id is null then
        return new;
    end if;

    select r.tenant_id
      into v_tenant_id
      from public.ride_requests r
     where r.id = new.trip_id;

    if not found then
        return new;
    end if;

    new.tenant_id := v_tenant_id;
    return new;
end;
$$;

create or replace function public.ride_inherit_offer_tenant()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_tenant_id uuid;
begin
    select r.tenant_id
      into v_tenant_id
      from public.ride_requests r
     where r.id = new.request_id;

    if found then
        new.tenant_id := v_tenant_id;
    end if;
    return new;
end;
$$;

create or replace function public.ride_guard_tenant_assignment()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_vehicle_tenant uuid;
    v_request_tenant uuid;
begin
    if tg_table_name = 'ride_offers' then
        select r.tenant_id into v_request_tenant
          from public.ride_requests r
         where r.id = new.request_id;
        select v.tenant_id into v_vehicle_tenant
          from public.ride_driver_vehicles v
         where v.id = new.vehicle_id;
        if v_request_tenant is distinct from v_vehicle_tenant or
           new.tenant_id is distinct from v_request_tenant
        then
            raise exception using
                errcode = '23514',
                message = 'RIDE_TENANT_MISMATCH';
        end if;
    elsif new.assigned_vehicle_id is not null then
        select v.tenant_id into v_vehicle_tenant
          from public.ride_driver_vehicles v
         where v.id = new.assigned_vehicle_id;
        if new.tenant_id is distinct from v_vehicle_tenant then
            raise exception using
                errcode = '23514',
                message = 'RIDE_TENANT_MISMATCH';
        end if;
    end if;
    return new;
end;
$$;

drop trigger if exists ride_offer_inherit_tenant on public.ride_offers;
create trigger ride_offer_inherit_tenant
before insert or update of request_id on public.ride_offers
for each row execute function public.ride_inherit_offer_tenant();

drop trigger if exists ride_offer_tenant_guard on public.ride_offers;
create trigger ride_offer_tenant_guard
before insert or update of request_id, vehicle_id, tenant_id
on public.ride_offers
for each row execute function public.ride_guard_tenant_assignment();

drop trigger if exists ride_request_tenant_guard on public.ride_requests;
create trigger ride_request_tenant_guard
before insert or update of assigned_vehicle_id, assigned_driver_id, tenant_id
on public.ride_requests
for each row execute function public.ride_guard_tenant_assignment();

drop trigger if exists ride_receipt_inherit_tenant
    on public.ride_command_receipts;
create trigger ride_receipt_inherit_tenant
before insert on public.ride_command_receipts
for each row execute function public.ride_inherit_trip_tenant();

drop trigger if exists ride_quote_inherit_tenant on public.ride_fare_quotes;
create trigger ride_quote_inherit_tenant
before insert on public.ride_fare_quotes
for each row execute function public.ride_inherit_trip_tenant();

drop trigger if exists ride_hold_inherit_tenant on public.ride_operational_holds;
create trigger ride_hold_inherit_tenant
before insert on public.ride_operational_holds
for each row execute function public.ride_inherit_trip_tenant();

drop trigger if exists ride_reservation_inherit_tenant
    on public.ride_commission_reservations;
create trigger ride_reservation_inherit_tenant
before insert on public.ride_commission_reservations
for each row execute function public.ride_inherit_trip_tenant();

drop trigger if exists ride_ledger_transaction_inherit_tenant
    on public.ride_ledger_transactions;
create trigger ride_ledger_transaction_inherit_tenant
before insert on public.ride_ledger_transactions
for each row execute function public.ride_inherit_trip_tenant();

drop trigger if exists ride_commission_calculation_inherit_tenant
    on public.ride_commission_calculations;
create trigger ride_commission_calculation_inherit_tenant
before insert on public.ride_commission_calculations
for each row execute function public.ride_inherit_trip_tenant();

drop trigger if exists ride_safety_event_inherit_tenant
    on public.ride_safety_events;
create trigger ride_safety_event_inherit_tenant
before insert on public.ride_safety_events
for each row execute function public.ride_inherit_trip_tenant();

drop trigger if exists ride_support_case_inherit_tenant
    on public.ride_support_cases;
create trigger ride_support_case_inherit_tenant
before insert on public.ride_support_cases
for each row execute function public.ride_inherit_trip_tenant();

alter table public.ride_tenants enable row level security;
alter table public.ride_tenant_memberships enable row level security;

drop policy if exists ride_tenants_visible on public.ride_tenants;
create policy ride_tenants_visible
on public.ride_tenants for select to authenticated
using (
    id = '00000000-0000-0000-0000-00000000e1a1' or
    public.ride_is_active_tenant_member(
        id,
        array[
            'TENANT_ADMIN', 'DISPATCHER', 'DRIVER', 'SUPPORT',
            'FINANCE', 'AUDITOR', 'CORPORATE_RIDER'
        ]
    )
);

drop policy if exists ride_tenant_memberships_self_select
    on public.ride_tenant_memberships;
create policy ride_tenant_memberships_self_select
on public.ride_tenant_memberships for select to authenticated
using (user_id = (select auth.uid()));

drop policy if exists ride_driver_vehicles_owner_all
    on public.ride_driver_vehicles;
drop policy if exists ride_driver_vehicles_owner_select
    on public.ride_driver_vehicles;
create policy ride_driver_vehicles_owner_select
on public.ride_driver_vehicles for select to authenticated
using (driver_id = (select auth.uid()));

drop policy if exists ride_requests_participant_select
    on public.ride_requests;
create policy ride_requests_participant_select
on public.ride_requests for select to authenticated
using (
    passenger_id = (select auth.uid()) or
    assigned_driver_id = (select auth.uid()) or
    (
        state in ('SEARCHING', 'OFFERED') and
        (
            exists (
                select 1
                  from public.ride_driver_vehicles v
                 where v.driver_id = (select auth.uid())
                   and v.tenant_id = ride_requests.tenant_id
                   and public.ride_vehicle_dispatch_eligible(
                       v.id,
                       (select auth.uid())
                   )
                   and (
                       ride_requests.tenant_id =
                           '00000000-0000-0000-0000-00000000e1a1' or
                       public.ride_is_active_tenant_member(
                           ride_requests.tenant_id,
                           array['DRIVER']
                       )
                   )
            ) or
            public.ride_is_active_tenant_member(
                ride_requests.tenant_id,
                array['TENANT_ADMIN', 'DISPATCHER']
            )
        )
    )
);

revoke all on public.ride_tenants from anon, authenticated;
revoke all on public.ride_tenant_memberships from anon, authenticated;
grant select on public.ride_tenants to authenticated;
grant select on public.ride_tenant_memberships to authenticated;

revoke insert, update, delete on public.ride_driver_vehicles
    from authenticated;
revoke insert, update, delete on public.ride_requests from authenticated;
revoke insert, update, delete on public.ride_offers from authenticated;

revoke all on function public.ride_is_active_tenant_member(uuid, text[])
    from public;
grant execute on function public.ride_is_active_tenant_member(uuid, text[])
    to authenticated;

comment on table public.ride_tenants is
    'Backend-provisioned mobility operators. Android cannot create or activate tenants.';
