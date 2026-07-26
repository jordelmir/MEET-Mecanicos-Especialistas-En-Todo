-- MEET Viajes: authoritative mobility, privacy and wallet foundation.
-- Costa Rica is the pilot market; every monetary value uses ISO-4217 minor units.

create table if not exists public.ride_profiles (
    user_id uuid primary key references auth.users(id) on delete cascade,
    mobility_role text not null check (mobility_role in ('PASSENGER', 'DRIVER', 'BOTH')),
    country_code text not null check (country_code ~ '^[A-Z]{2}$'),
    preferred_currency text not null check (preferred_currency ~ '^[A-Z]{3}$'),
    display_name text not null check (char_length(display_name) between 1 and 120),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.ride_driver_vehicles (
    id uuid primary key default gen_random_uuid(),
    driver_id uuid not null references public.ride_profiles(user_id) on delete cascade,
    vehicle_id text not null,
    display_name text not null check (char_length(display_name) between 1 and 160),
    seats integer not null check (seats between 1 and 16),
    verification_status text not null default 'PENDING'
        check (verification_status in ('PENDING', 'VERIFIED', 'REJECTED', 'SUSPENDED')),
    is_active boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (driver_id, vehicle_id)
);

create unique index if not exists ride_one_active_vehicle_per_driver
    on public.ride_driver_vehicles(driver_id)
    where is_active;

create table if not exists public.ride_requests (
    id uuid primary key default gen_random_uuid(),
    passenger_id uuid not null references public.ride_profiles(user_id) on delete restrict,
    assigned_driver_id uuid references public.ride_profiles(user_id) on delete restrict,
    assigned_vehicle_id uuid references public.ride_driver_vehicles(id) on delete restrict,
    pickup_latitude double precision not null check (pickup_latitude between -90 and 90),
    pickup_longitude double precision not null check (pickup_longitude between -180 and 180),
    pickup_address text not null check (char_length(pickup_address) between 1 and 500),
    destination_latitude double precision not null check (destination_latitude between -90 and 90),
    destination_longitude double precision not null check (destination_longitude between -180 and 180),
    destination_address text not null check (char_length(destination_address) between 1 and 500),
    offered_fare_minor bigint not null check (offered_fare_minor > 0),
    final_fare_minor bigint check (final_fare_minor is null or final_fare_minor > 0),
    currency text not null check (currency ~ '^[A-Z]{3}$'),
    state text not null default 'SEARCHING' check (
        state in (
            'DRAFT', 'SEARCHING', 'OFFERED', 'ASSIGNED', 'DRIVER_EN_ROUTE',
            'ARRIVED', 'PASSENGER_ONBOARD', 'IN_PROGRESS', 'COMPLETED',
            'CANCELLED', 'EXPIRED', 'DISPUTED', 'SAFETY_HOLD'
        )
    ),
    version bigint not null default 1 check (version > 0),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    completed_at timestamptz,
    cancelled_at timestamptz,
    check (
        (assigned_driver_id is null and assigned_vehicle_id is null) or
        (assigned_driver_id is not null and assigned_vehicle_id is not null)
    )
);

create index if not exists ride_requests_passenger_idx
    on public.ride_requests(passenger_id, created_at desc);
create index if not exists ride_requests_driver_idx
    on public.ride_requests(assigned_driver_id, created_at desc)
    where assigned_driver_id is not null;
create index if not exists ride_requests_searching_idx
    on public.ride_requests(state, created_at desc)
    where state in ('SEARCHING', 'OFFERED');

create table if not exists public.ride_offers (
    id uuid primary key default gen_random_uuid(),
    request_id uuid not null references public.ride_requests(id) on delete cascade,
    driver_id uuid not null references public.ride_profiles(user_id) on delete restrict,
    vehicle_id uuid not null references public.ride_driver_vehicles(id) on delete restrict,
    fare_minor bigint not null check (fare_minor > 0),
    currency text not null check (currency ~ '^[A-Z]{3}$'),
    eta_seconds integer check (eta_seconds is null or eta_seconds between 0 and 86400),
    state text not null default 'PENDING'
        check (state in ('PENDING', 'ACCEPTED', 'REJECTED', 'WITHDRAWN')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (request_id, driver_id)
);

create unique index if not exists ride_one_accepted_offer_per_request
    on public.ride_offers(request_id)
    where state = 'ACCEPTED';

create table if not exists public.ride_trip_events (
    id bigint generated always as identity primary key,
    trip_id uuid not null references public.ride_requests(id) on delete restrict,
    actor_id uuid references auth.users(id) on delete set null,
    event_type text not null,
    from_state text,
    to_state text,
    payload jsonb not null default '{}'::jsonb,
    idempotency_key text not null unique,
    created_at timestamptz not null default now()
);

create index if not exists ride_trip_events_trip_idx
    on public.ride_trip_events(trip_id, id);

create table if not exists public.ride_consents (
    trip_id uuid not null references public.ride_requests(id) on delete cascade,
    driver_id uuid not null references public.ride_profiles(user_id) on delete restrict,
    category text not null check (
        category in (
            'EXACT_LOCATION', 'BASIC_TELEMETRY', 'ACTIVE_DTCS', 'DTC_HISTORY',
            'MAINTENANCE', 'INSTALLED_PARTS', 'CERTIFIED_REPORTS'
        )
    ),
    granted_at timestamptz,
    expires_at timestamptz not null,
    revoked_at timestamptz,
    updated_at timestamptz not null default now(),
    primary key (trip_id, category),
    check (granted_at is null or granted_at <= expires_at)
);

create table if not exists public.ride_positions (
    trip_id uuid not null references public.ride_requests(id) on delete cascade,
    subject_user_id uuid not null references auth.users(id) on delete cascade,
    subject_role text not null check (subject_role in ('PASSENGER', 'DRIVER')),
    latitude double precision not null check (latitude between -90 and 90),
    longitude double precision not null check (longitude between -180 and 180),
    accuracy_meters real check (accuracy_meters is null or accuracy_meters >= 0),
    sequence bigint not null check (sequence >= 0),
    captured_at timestamptz not null,
    expires_at timestamptz not null,
    primary key (trip_id, subject_user_id),
    check (captured_at <= expires_at)
);

create table if not exists public.ride_vehicle_questions (
    id uuid primary key default gen_random_uuid(),
    trip_id uuid not null references public.ride_requests(id) on delete cascade,
    passenger_id uuid not null references public.ride_profiles(user_id) on delete restrict,
    driver_id uuid not null references public.ride_profiles(user_id) on delete restrict,
    question text not null check (char_length(question) between 1 and 1000),
    answer text check (answer is null or char_length(answer) between 1 and 2000),
    evidence_reference text,
    created_at timestamptz not null default now(),
    answered_at timestamptz
);

create table if not exists public.ride_vehicle_evidence (
    id bigint generated always as identity primary key,
    trip_id uuid not null references public.ride_requests(id) on delete cascade,
    driver_id uuid not null references public.ride_profiles(user_id) on delete restrict,
    category text not null check (
        category in (
            'BASIC_TELEMETRY', 'ACTIVE_DTCS', 'DTC_HISTORY',
            'MAINTENANCE', 'INSTALLED_PARTS', 'CERTIFIED_REPORTS'
        )
    ),
    sample_key text not null check (char_length(sample_key) between 1 and 120),
    display_value text not null check (char_length(display_value) between 1 and 1000),
    source text not null check (
        source in (
            'REAL_OBD', 'CERTIFIED_REPORT', 'VERIFIED_SERVICE_EVENT',
            'DRIVER_STATEMENT'
        )
    ),
    captured_at timestamptz not null,
    expires_at timestamptz not null,
    evidence_reference text check (
        evidence_reference is null or char_length(evidence_reference) between 1 and 500
    ),
    created_at timestamptz not null default now(),
    check (captured_at <= expires_at)
);

create index if not exists ride_vehicle_evidence_trip_idx
    on public.ride_vehicle_evidence(trip_id, category, captured_at desc);

create table if not exists public.ride_wallets (
    driver_id uuid primary key references public.ride_profiles(user_id) on delete restrict,
    currency text not null check (currency ~ '^[A-Z]{3}$'),
    created_at timestamptz not null default now()
);

create table if not exists public.ride_wallet_ledger (
    id uuid primary key default gen_random_uuid(),
    driver_id uuid not null references public.ride_wallets(driver_id) on delete restrict,
    idempotency_key text not null unique,
    entry_type text not null check (
        entry_type in (
            'PROMOTIONAL_GRANT', 'TOP_UP_PENDING', 'TOP_UP_CONFIRMED',
            'COMMISSION_RESERVED', 'COMMISSION_CAPTURED', 'COMMISSION_RELEASED',
            'REFUND', 'ADJUSTMENT'
        )
    ),
    amount_minor bigint not null check (amount_minor >= 0),
    currency text not null check (currency ~ '^[A-Z]{3}$'),
    direction text not null check (direction in ('CREDIT', 'DEBIT')),
    trip_id uuid references public.ride_requests(id) on delete restrict,
    withdrawable boolean not null default false,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

create index if not exists ride_wallet_ledger_driver_idx
    on public.ride_wallet_ledger(driver_id, created_at, id);

create table if not exists public.ride_commission_reservations (
    trip_id uuid primary key references public.ride_requests(id) on delete restrict,
    driver_id uuid not null references public.ride_wallets(driver_id) on delete restrict,
    amount_minor bigint not null check (amount_minor > 0),
    currency text not null check (currency ~ '^[A-Z]{3}$'),
    state text not null check (state in ('RESERVED', 'CAPTURED', 'RELEASED')),
    reserve_idempotency_key text not null unique,
    settlement_idempotency_key text unique,
    created_at timestamptz not null default now(),
    settled_at timestamptz
);

create table if not exists public.ride_cancellations (
    trip_id uuid primary key references public.ride_requests(id) on delete restrict,
    actor_id uuid not null references auth.users(id) on delete restrict,
    reason_code text not null check (
        reason_code in (
            'SAFETY_CONCERN', 'UNACCOMPANIED_MINOR', 'CHILD_SEAT_REQUIRED',
            'TOO_MANY_PASSENGERS', 'IDENTITY_MISMATCH', 'VEHICLE_MISMATCH',
            'HARASSMENT', 'PROHIBITED_ITEM_OR_ACTIVITY', 'DANGEROUS_PICKUP',
            'MEDICAL_EMERGENCY', 'UNSAFE_VEHICLE_CONDITION', 'PASSENGER_NO_SHOW',
            'DRIVER_NO_SHOW', 'EXCESSIVE_WAIT', 'INCORRECT_PICKUP',
            'INCORRECT_DESTINATION', 'CHANGE_OF_PLANS',
            'DUPLICATE_OR_ACCIDENTAL', 'OTHER'
        )
    ),
    detail text check (detail is null or char_length(detail) <= 500),
    requires_safety_review boolean not null,
    automatic_fee_minor bigint not null default 0 check (automatic_fee_minor = 0),
    created_at timestamptz not null default now()
);

create or replace function public.ride_is_participant(p_trip_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.ride_requests r
        where r.id = p_trip_id
          and (
              r.passenger_id = (select auth.uid()) or
              r.assigned_driver_id = (select auth.uid())
          )
    );
$$;

create or replace function public.ride_is_assigned_driver(p_trip_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.ride_requests r
        where r.id = p_trip_id
          and r.assigned_driver_id = (select auth.uid())
    );
$$;

create or replace function public.ride_trip_is_active(p_trip_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.ride_requests r
        where r.id = p_trip_id
          and r.state in (
              'ASSIGNED', 'DRIVER_EN_ROUTE', 'ARRIVED',
              'PASSENGER_ONBOARD', 'IN_PROGRESS', 'SAFETY_HOLD'
          )
    );
$$;

create or replace function public.ride_can_view_profile(p_profile_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select
        p_profile_id = (select auth.uid()) or
        exists (
            select 1
            from public.ride_requests r
            where (
                r.passenger_id = (select auth.uid()) or
                r.assigned_driver_id = (select auth.uid())
            )
              and p_profile_id in (r.passenger_id, r.assigned_driver_id)
        );
$$;

create or replace function public.ride_can_view_vehicle(p_vehicle_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select
        exists (
            select 1
            from public.ride_driver_vehicles v
            where v.id = p_vehicle_id
              and v.driver_id = (select auth.uid())
        ) or
        exists (
            select 1
            from public.ride_requests r
            where r.assigned_vehicle_id = p_vehicle_id
              and (
                  r.passenger_id = (select auth.uid()) or
                  r.assigned_driver_id = (select auth.uid())
              )
        ) or
        exists (
            select 1
            from public.ride_offers o
            join public.ride_requests r on r.id = o.request_id
            where o.vehicle_id = p_vehicle_id
              and r.passenger_id = (select auth.uid())
              and o.state = 'PENDING'
        );
$$;

create or replace function public.ride_reject_immutable_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    raise exception 'Ride audit records are immutable';
end;
$$;

drop trigger if exists ride_trip_events_immutable on public.ride_trip_events;
create trigger ride_trip_events_immutable
before update or delete on public.ride_trip_events
for each row execute function public.ride_reject_immutable_change();

drop trigger if exists ride_wallet_ledger_immutable on public.ride_wallet_ledger;
create trigger ride_wallet_ledger_immutable
before update or delete on public.ride_wallet_ledger
for each row execute function public.ride_reject_immutable_change();

drop trigger if exists ride_vehicle_evidence_immutable on public.ride_vehicle_evidence;
create trigger ride_vehicle_evidence_immutable
before update or delete on public.ride_vehicle_evidence
for each row execute function public.ride_reject_immutable_change();

alter table public.ride_profiles enable row level security;
alter table public.ride_driver_vehicles enable row level security;
alter table public.ride_requests enable row level security;
alter table public.ride_offers enable row level security;
alter table public.ride_trip_events enable row level security;
alter table public.ride_consents enable row level security;
alter table public.ride_positions enable row level security;
alter table public.ride_vehicle_questions enable row level security;
alter table public.ride_vehicle_evidence enable row level security;
alter table public.ride_wallets enable row level security;
alter table public.ride_wallet_ledger enable row level security;
alter table public.ride_commission_reservations enable row level security;
alter table public.ride_cancellations enable row level security;

drop policy if exists ride_profiles_self_select on public.ride_profiles;
create policy ride_profiles_self_select on public.ride_profiles
for select to authenticated
using (public.ride_can_view_profile(user_id));

drop policy if exists ride_profiles_self_insert on public.ride_profiles;
create policy ride_profiles_self_insert on public.ride_profiles
for insert to authenticated
with check (user_id = (select auth.uid()));

drop policy if exists ride_profiles_self_update on public.ride_profiles;
create policy ride_profiles_self_update on public.ride_profiles
for update to authenticated
using (user_id = (select auth.uid()))
with check (user_id = (select auth.uid()));

drop policy if exists ride_driver_vehicles_owner_all on public.ride_driver_vehicles;
create policy ride_driver_vehicles_owner_all on public.ride_driver_vehicles
for all to authenticated
using (driver_id = (select auth.uid()))
with check (driver_id = (select auth.uid()));

drop policy if exists ride_driver_vehicles_participant_select on public.ride_driver_vehicles;
create policy ride_driver_vehicles_participant_select on public.ride_driver_vehicles
for select to authenticated
using (public.ride_can_view_vehicle(id));

drop policy if exists ride_requests_participant_select on public.ride_requests;
create policy ride_requests_participant_select on public.ride_requests
for select to authenticated
using (
    passenger_id = (select auth.uid()) or
    assigned_driver_id = (select auth.uid()) or
    (
        state in ('SEARCHING', 'OFFERED') and
        exists (
            select 1
            from public.ride_driver_vehicles v
            where v.driver_id = (select auth.uid())
              and v.is_active
              and v.verification_status = 'VERIFIED'
        )
    )
);

drop policy if exists ride_requests_passenger_insert on public.ride_requests;
create policy ride_requests_passenger_insert on public.ride_requests
for insert to authenticated
with check (
    passenger_id = (select auth.uid()) and
    assigned_driver_id is null and
    assigned_vehicle_id is null and
    state = 'SEARCHING'
);

drop policy if exists ride_offers_participant_select on public.ride_offers;
create policy ride_offers_participant_select on public.ride_offers
for select to authenticated
using (
    driver_id = (select auth.uid()) or
    exists (
        select 1
        from public.ride_requests r
        where r.id = ride_offers.request_id
          and r.passenger_id = (select auth.uid())
    )
);

drop policy if exists ride_offers_driver_insert on public.ride_offers;
create policy ride_offers_driver_insert on public.ride_offers
for insert to authenticated
with check (
    driver_id = (select auth.uid()) and
    exists (
        select 1
        from public.ride_driver_vehicles v
        where v.id = ride_offers.vehicle_id
          and v.driver_id = (select auth.uid())
          and v.is_active
          and v.verification_status = 'VERIFIED'
    ) and
    exists (
        select 1
        from public.ride_requests r
        where r.id = ride_offers.request_id
          and r.state in ('SEARCHING', 'OFFERED')
          and r.currency = ride_offers.currency
    )
);

drop policy if exists ride_trip_events_participant_select on public.ride_trip_events;
create policy ride_trip_events_participant_select on public.ride_trip_events
for select to authenticated
using (public.ride_is_participant(trip_id));

drop policy if exists ride_consents_participant_select on public.ride_consents;
create policy ride_consents_participant_select on public.ride_consents
for select to authenticated
using (public.ride_is_participant(trip_id));

drop policy if exists ride_consents_driver_write on public.ride_consents;
create policy ride_consents_driver_write on public.ride_consents
for all to authenticated
using (
    driver_id = (select auth.uid()) and
    public.ride_is_assigned_driver(trip_id) and
    public.ride_trip_is_active(trip_id)
)
with check (
    driver_id = (select auth.uid()) and
    public.ride_is_assigned_driver(trip_id) and
    public.ride_trip_is_active(trip_id)
);

drop policy if exists ride_positions_participant_select on public.ride_positions;
create policy ride_positions_participant_select on public.ride_positions
for select to authenticated
using (
    public.ride_is_participant(trip_id) and
    public.ride_trip_is_active(trip_id) and
    expires_at > now()
);

drop policy if exists ride_positions_subject_write on public.ride_positions;
create policy ride_positions_subject_write on public.ride_positions
for all to authenticated
using (
    subject_user_id = (select auth.uid()) and
    public.ride_is_participant(trip_id) and
    public.ride_trip_is_active(trip_id)
)
with check (
    subject_user_id = (select auth.uid()) and
    public.ride_is_participant(trip_id) and
    public.ride_trip_is_active(trip_id) and
    expires_at <= now() + interval '5 minutes'
);

drop policy if exists ride_vehicle_questions_participant_all on public.ride_vehicle_questions;
create policy ride_vehicle_questions_participant_all on public.ride_vehicle_questions
for all to authenticated
using (public.ride_is_participant(trip_id))
with check (
    exists (
        select 1
        from public.ride_requests r
        where r.id = ride_vehicle_questions.trip_id
          and r.passenger_id = ride_vehicle_questions.passenger_id
          and r.assigned_driver_id = ride_vehicle_questions.driver_id
          and (
              r.passenger_id = (select auth.uid()) or
              r.assigned_driver_id = (select auth.uid())
          )
    )
);

drop policy if exists ride_vehicle_evidence_participant_select on public.ride_vehicle_evidence;
create policy ride_vehicle_evidence_participant_select on public.ride_vehicle_evidence
for select to authenticated
using (
    public.ride_is_participant(trip_id) and
    public.ride_trip_is_active(trip_id) and
    expires_at > now() and
    exists (
        select 1
        from public.ride_consents c
        where c.trip_id = ride_vehicle_evidence.trip_id
          and c.driver_id = ride_vehicle_evidence.driver_id
          and c.category = ride_vehicle_evidence.category
          and c.granted_at is not null
          and c.granted_at <= now()
          and c.revoked_at is null
          and c.expires_at > now()
    )
);

drop policy if exists ride_vehicle_evidence_driver_insert on public.ride_vehicle_evidence;
create policy ride_vehicle_evidence_driver_insert on public.ride_vehicle_evidence
for insert to authenticated
with check (
    driver_id = (select auth.uid()) and
    public.ride_is_assigned_driver(trip_id) and
    public.ride_trip_is_active(trip_id) and
    expires_at <= now() + interval '24 hours' and
    exists (
        select 1
        from public.ride_consents c
        where c.trip_id = ride_vehicle_evidence.trip_id
          and c.driver_id = (select auth.uid())
          and c.category = ride_vehicle_evidence.category
          and c.granted_at is not null
          and c.granted_at <= now()
          and c.revoked_at is null
          and c.expires_at >= ride_vehicle_evidence.expires_at
    )
);

drop policy if exists ride_wallets_owner_select on public.ride_wallets;
create policy ride_wallets_owner_select on public.ride_wallets
for select to authenticated
using (driver_id = (select auth.uid()));

drop policy if exists ride_wallet_ledger_owner_select on public.ride_wallet_ledger;
create policy ride_wallet_ledger_owner_select on public.ride_wallet_ledger
for select to authenticated
using (driver_id = (select auth.uid()));

drop policy if exists ride_commission_reservations_owner_select on public.ride_commission_reservations;
create policy ride_commission_reservations_owner_select on public.ride_commission_reservations
for select to authenticated
using (driver_id = (select auth.uid()));

drop policy if exists ride_cancellations_participant_select on public.ride_cancellations;
create policy ride_cancellations_participant_select on public.ride_cancellations
for select to authenticated
using (public.ride_is_participant(trip_id));

revoke all on public.ride_profiles from anon;
revoke all on public.ride_driver_vehicles from anon;
revoke all on public.ride_requests from anon;
revoke all on public.ride_offers from anon;
revoke all on public.ride_trip_events from anon;
revoke all on public.ride_consents from anon;
revoke all on public.ride_positions from anon;
revoke all on public.ride_vehicle_questions from anon;
revoke all on public.ride_vehicle_evidence from anon;
revoke all on public.ride_wallets from anon;
revoke all on public.ride_wallet_ledger from anon;
revoke all on public.ride_commission_reservations from anon;
revoke all on public.ride_cancellations from anon;

grant select, insert, update on public.ride_profiles to authenticated;
grant select, insert, update, delete on public.ride_driver_vehicles to authenticated;
grant select, insert on public.ride_requests to authenticated;
grant select, insert on public.ride_offers to authenticated;
grant select on public.ride_trip_events to authenticated;
grant select, insert, update, delete on public.ride_consents to authenticated;
grant select, insert, update, delete on public.ride_positions to authenticated;
grant select, insert, update on public.ride_vehicle_questions to authenticated;
grant select, insert on public.ride_vehicle_evidence to authenticated;
grant select on public.ride_wallets to authenticated;
grant select on public.ride_wallet_ledger to authenticated;
grant select on public.ride_commission_reservations to authenticated;
grant select on public.ride_cancellations to authenticated;

revoke insert, update, delete on public.ride_wallet_ledger from authenticated;
revoke insert, update, delete on public.ride_trip_events from authenticated;
revoke update, delete on public.ride_vehicle_evidence from authenticated;

create or replace function public.ride_grant_promotional_balance()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := (select auth.uid());
    v_country text;
    v_entry public.ride_wallet_ledger%rowtype;
    v_key text;
    v_inserted_rows integer := 0;
begin
    if v_user_id is null then
        raise exception 'Authentication required';
    end if;

    select p.country_code
      into v_country
      from public.ride_profiles p
     where p.user_id = v_user_id
       and p.mobility_role in ('DRIVER', 'BOTH');

    if v_country is distinct from 'CR' then
        raise exception 'Promotional grant is not configured for this market';
    end if;

    insert into public.ride_wallets(driver_id, currency)
    values (v_user_id, 'CRC')
    on conflict (driver_id) do nothing;

    if not exists (
        select 1 from public.ride_wallets w
        where w.driver_id = v_user_id and w.currency = 'CRC'
    ) then
        raise exception 'Driver wallet currency mismatch';
    end if;

    v_key := 'promo:cr-pilot-2026:' || v_user_id::text;

    insert into public.ride_wallet_ledger(
        driver_id, idempotency_key, entry_type, amount_minor, currency,
        direction, withdrawable, metadata
    )
    values (
        v_user_id, v_key, 'PROMOTIONAL_GRANT', 100000, 'CRC',
        'CREDIT', false, jsonb_build_object('campaign', 'cr-pilot-2026')
    )
    on conflict (idempotency_key) do nothing;
    get diagnostics v_inserted_rows = row_count;

    select l.*
      into strict v_entry
      from public.ride_wallet_ledger l
     where l.idempotency_key = v_key
       and l.driver_id = v_user_id;

    return jsonb_build_object(
        'ledger_entry_id', v_entry.id,
        'amount_minor', v_entry.amount_minor,
        'currency', v_entry.currency,
        'already_granted', v_inserted_rows = 0
    );
end;
$$;

create or replace function public.ride_accept_offer(
    p_request_id uuid,
    p_offer_id uuid,
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
    v_offer public.ride_offers%rowtype;
    v_commission bigint;
    v_posted bigint;
    v_reserved bigint;
    v_from_state text;
begin
    if v_user_id is null or nullif(trim(p_idempotency_key), '') is null then
        raise exception 'Authentication and idempotency key are required';
    end if;

    if exists (
        select 1 from public.ride_trip_events e
        where e.idempotency_key = p_idempotency_key
          and e.trip_id = p_request_id
    ) then
        return (select to_jsonb(r) from public.ride_requests r where r.id = p_request_id);
    end if;

    select r.* into v_request
      from public.ride_requests r
     where r.id = p_request_id
     for update;

    if not found or v_request.passenger_id <> v_user_id then
        raise exception 'Passenger is not authorized for this request';
    end if;
    if v_request.state not in ('SEARCHING', 'OFFERED') then
        raise exception 'Ride transition denied';
    end if;
    v_from_state := v_request.state;

    select o.* into v_offer
      from public.ride_offers o
     where o.id = p_offer_id
       and o.request_id = p_request_id
     for update;

    if not found or v_offer.state <> 'PENDING' then
        raise exception 'Offer is not available';
    end if;
    if v_offer.currency <> v_request.currency then
        raise exception 'Offer currency mismatch';
    end if;

    v_commission := round(
        v_offer.fare_minor::numeric * 500::numeric / 10000::numeric
    )::bigint;

    select coalesce(sum(
        case
            when l.entry_type in ('PROMOTIONAL_GRANT', 'TOP_UP_CONFIRMED', 'REFUND')
                and l.direction = 'CREDIT' then l.amount_minor
            when l.entry_type in ('COMMISSION_CAPTURED', 'ADJUSTMENT')
                and l.direction = 'DEBIT' then -l.amount_minor
            when l.entry_type = 'ADJUSTMENT'
                and l.direction = 'CREDIT' then l.amount_minor
            else 0
        end
    ), 0)
      into v_posted
      from public.ride_wallet_ledger l
     where l.driver_id = v_offer.driver_id
       and l.currency = v_offer.currency;

    select coalesce(sum(cr.amount_minor), 0)
      into v_reserved
      from public.ride_commission_reservations cr
     where cr.driver_id = v_offer.driver_id
       and cr.currency = v_offer.currency
       and cr.state = 'RESERVED';

    if v_posted - v_reserved < v_commission then
        raise exception 'Driver has insufficient available balance';
    end if;

    insert into public.ride_commission_reservations(
        trip_id, driver_id, amount_minor, currency, state, reserve_idempotency_key
    )
    values (
        p_request_id, v_offer.driver_id, v_commission, v_offer.currency,
        'RESERVED', p_idempotency_key || ':reserve'
    );

    insert into public.ride_wallet_ledger(
        driver_id, idempotency_key, entry_type, amount_minor, currency,
        direction, trip_id, withdrawable
    )
    values (
        v_offer.driver_id, p_idempotency_key || ':ledger-reserve',
        'COMMISSION_RESERVED', v_commission, v_offer.currency,
        'DEBIT', p_request_id, false
    );

    update public.ride_offers
       set state = case when id = p_offer_id then 'ACCEPTED' else 'REJECTED' end,
           updated_at = now()
     where request_id = p_request_id
       and state = 'PENDING';

    update public.ride_requests
       set assigned_driver_id = v_offer.driver_id,
           assigned_vehicle_id = v_offer.vehicle_id,
           offered_fare_minor = v_offer.fare_minor,
           state = 'ASSIGNED',
           version = version + 1,
           updated_at = now()
     where id = p_request_id
     returning * into v_request;

    insert into public.ride_trip_events(
        trip_id, actor_id, event_type, from_state, to_state,
        payload, idempotency_key
    )
    values (
        p_request_id, v_user_id, 'OFFER_ACCEPTED', v_from_state, 'ASSIGNED',
        jsonb_build_object(
            'offer_id', p_offer_id,
            'driver_id', v_offer.driver_id,
            'commission_reserved_minor', v_commission,
            'currency', v_offer.currency
        ),
        p_idempotency_key
    );

    return to_jsonb(v_request);
end;
$$;

create or replace function public.ride_cancel_trip(
    p_trip_id uuid,
    p_reason_code text,
    p_detail text,
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
    v_reservation public.ride_commission_reservations%rowtype;
    v_reason text := upper(trim(p_reason_code));
    v_safety boolean;
    v_from_state text;
begin
    if v_user_id is null or nullif(trim(p_idempotency_key), '') is null then
        raise exception 'Authentication and idempotency key are required';
    end if;
    if char_length(coalesce(p_detail, '')) > 500 then
        raise exception 'Cancellation detail is too long';
    end if;
    if v_reason = 'OTHER' and nullif(trim(coalesce(p_detail, '')), '') is null then
        raise exception 'Other cancellation reason requires detail';
    end if;

    if exists (
        select 1 from public.ride_trip_events e
        where e.idempotency_key = p_idempotency_key and e.trip_id = p_trip_id
    ) then
        return (select to_jsonb(r) from public.ride_requests r where r.id = p_trip_id);
    end if;

    select r.* into v_request
      from public.ride_requests r
     where r.id = p_trip_id
     for update;

    if not found or (
        v_user_id <> v_request.passenger_id and
        (
            v_request.assigned_driver_id is null or
            v_user_id <> v_request.assigned_driver_id
        )
    ) then
        raise exception 'Actor is not authorized for this trip';
    end if;
    if v_request.state in ('COMPLETED', 'CANCELLED', 'EXPIRED', 'DISPUTED') then
        raise exception 'Ride transition denied';
    end if;
    v_from_state := v_request.state;

    v_safety := v_reason in (
        'SAFETY_CONCERN', 'UNACCOMPANIED_MINOR', 'CHILD_SEAT_REQUIRED',
        'TOO_MANY_PASSENGERS', 'IDENTITY_MISMATCH', 'VEHICLE_MISMATCH',
        'HARASSMENT', 'PROHIBITED_ITEM_OR_ACTIVITY', 'DANGEROUS_PICKUP',
        'MEDICAL_EMERGENCY', 'UNSAFE_VEHICLE_CONDITION'
    );

    insert into public.ride_cancellations(
        trip_id, actor_id, reason_code, detail, requires_safety_review
    )
    values (p_trip_id, v_user_id, v_reason, nullif(trim(p_detail), ''), v_safety);

    select cr.* into v_reservation
      from public.ride_commission_reservations cr
     where cr.trip_id = p_trip_id
     for update;

    if found and v_reservation.state = 'RESERVED' then
        update public.ride_commission_reservations
           set state = 'RELEASED',
               settlement_idempotency_key = p_idempotency_key || ':release',
               settled_at = now()
         where trip_id = p_trip_id;

        insert into public.ride_wallet_ledger(
            driver_id, idempotency_key, entry_type, amount_minor, currency,
            direction, trip_id, withdrawable
        )
        values (
            v_reservation.driver_id, p_idempotency_key || ':ledger-release',
            'COMMISSION_RELEASED', v_reservation.amount_minor,
            v_reservation.currency, 'CREDIT', p_trip_id, false
        );
    end if;

    update public.ride_requests
       set state = 'CANCELLED',
           version = version + 1,
           updated_at = now(),
           cancelled_at = now()
     where id = p_trip_id
     returning * into v_request;

    insert into public.ride_trip_events(
        trip_id, actor_id, event_type, from_state, to_state,
        payload, idempotency_key
    )
    values (
        p_trip_id, v_user_id, 'TRIP_CANCELLED', v_from_state, 'CANCELLED',
        jsonb_build_object(
            'reason_code', v_reason,
            'requires_safety_review', v_safety,
            'automatic_fee_minor', 0
        ),
        p_idempotency_key
    );

    return to_jsonb(v_request);
end;
$$;

create or replace function public.ride_complete_trip(
    p_trip_id uuid,
    p_final_fare_minor bigint,
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
    v_reservation public.ride_commission_reservations%rowtype;
    v_commission bigint;
begin
    if v_user_id is null or nullif(trim(p_idempotency_key), '') is null then
        raise exception 'Authentication and idempotency key are required';
    end if;
    if p_final_fare_minor <= 0 then
        raise exception 'Final fare must be positive';
    end if;

    if exists (
        select 1 from public.ride_trip_events e
        where e.idempotency_key = p_idempotency_key and e.trip_id = p_trip_id
    ) then
        return (select to_jsonb(r) from public.ride_requests r where r.id = p_trip_id);
    end if;

    select r.* into v_request
      from public.ride_requests r
     where r.id = p_trip_id
     for update;

    if not found or v_request.assigned_driver_id <> v_user_id then
        raise exception 'Assigned driver is required';
    end if;
    if v_request.state <> 'IN_PROGRESS' then
        raise exception 'Ride transition denied';
    end if;
    if p_final_fare_minor <> v_request.offered_fare_minor then
        raise exception 'Final fare must match the passenger accepted fare';
    end if;

    v_commission := round(
        p_final_fare_minor::numeric * 500::numeric / 10000::numeric
    )::bigint;

    select cr.* into strict v_reservation
      from public.ride_commission_reservations cr
     where cr.trip_id = p_trip_id
     for update;

    if v_reservation.state <> 'RESERVED' or
       v_reservation.amount_minor <> v_commission then
        raise exception 'Commission reservation is not valid';
    end if;

    update public.ride_commission_reservations
       set state = 'CAPTURED',
           settlement_idempotency_key = p_idempotency_key || ':capture',
           settled_at = now()
     where trip_id = p_trip_id;

    insert into public.ride_wallet_ledger(
        driver_id, idempotency_key, entry_type, amount_minor, currency,
        direction, trip_id, withdrawable
    )
    values (
        v_user_id, p_idempotency_key || ':ledger-capture',
        'COMMISSION_CAPTURED', v_commission, v_request.currency,
        'DEBIT', p_trip_id, false
    );

    update public.ride_requests
       set state = 'COMPLETED',
           final_fare_minor = p_final_fare_minor,
           version = version + 1,
           updated_at = now(),
           completed_at = now()
     where id = p_trip_id
     returning * into v_request;

    insert into public.ride_trip_events(
        trip_id, actor_id, event_type, from_state, to_state,
        payload, idempotency_key
    )
    values (
        p_trip_id, v_user_id, 'TRIP_COMPLETED', 'IN_PROGRESS', 'COMPLETED',
        jsonb_build_object(
            'final_fare_minor', p_final_fare_minor,
            'commission_minor', v_commission,
            'commission_basis_points', 500,
            'currency', v_request.currency
        ),
        p_idempotency_key
    );

    return to_jsonb(v_request);
end;
$$;

revoke all on function public.ride_grant_promotional_balance() from public;
revoke all on function public.ride_accept_offer(uuid, uuid, text) from public;
revoke all on function public.ride_cancel_trip(uuid, text, text, text) from public;
revoke all on function public.ride_complete_trip(uuid, bigint, text) from public;
revoke all on function public.ride_is_participant(uuid) from public;
revoke all on function public.ride_is_assigned_driver(uuid) from public;
revoke all on function public.ride_trip_is_active(uuid) from public;
revoke all on function public.ride_can_view_profile(uuid) from public;
revoke all on function public.ride_can_view_vehicle(uuid) from public;
revoke all on function public.ride_reject_immutable_change() from public;

grant execute on function public.ride_grant_promotional_balance() to authenticated;
grant execute on function public.ride_accept_offer(uuid, uuid, text) to authenticated;
grant execute on function public.ride_cancel_trip(uuid, text, text, text) to authenticated;
grant execute on function public.ride_complete_trip(uuid, bigint, text) to authenticated;
grant execute on function public.ride_is_participant(uuid) to authenticated;
grant execute on function public.ride_is_assigned_driver(uuid) to authenticated;
grant execute on function public.ride_trip_is_active(uuid) to authenticated;
grant execute on function public.ride_can_view_profile(uuid) to authenticated;
grant execute on function public.ride_can_view_vehicle(uuid) to authenticated;
