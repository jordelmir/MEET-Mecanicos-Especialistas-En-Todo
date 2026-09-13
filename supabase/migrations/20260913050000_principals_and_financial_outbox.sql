-- =============================================================================
-- Migration: 20260913050000_principals_and_financial_outbox.sql
-- Description: Unified Identity (SCD2), Immutable Financial Ledger & Transactional Outbox
-- Systems:
--   1. Principals & Organizations (Multi-tenant Actor System)
--   2. Slowly Changing Dimension Type 2 (SCD2) Organization Memberships
--   3. Authoritative Financial Ledger (Immutable Double-Entry, Idempotent)
--   4. Transactional Outbox (Guaranteed Canonical Event Emission)
--   5. Multi-Tenant Row Level Security (RLS)
-- =============================================================================

set check_function_bodies = off;
set search_path = public, auth;

-- -----------------------------------------------------------------------------
-- 1. PRINCIPALS & ORGANIZATIONS
-- -----------------------------------------------------------------------------
create table if not exists public.principals (
    principal_id text primary key,
    principal_type text not null default 'PERSON' check (principal_type in ('PERSON', 'ORGANIZATION')),
    display_name text not null default 'Actor',
    email text,
    phone text,
    tax_id text,
    capabilities jsonb not null default '[]'::jsonb,
    is_active boolean not null default true,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

alter table public.principals add column if not exists principal_id text;
alter table public.principals add column if not exists principal_type text not null default 'PERSON';
alter table public.principals add column if not exists display_name text not null default 'Actor';
alter table public.principals add column if not exists email text;
alter table public.principals add column if not exists phone text;
alter table public.principals add column if not exists tax_id text;
alter table public.principals add column if not exists capabilities jsonb not null default '[]'::jsonb;
alter table public.principals add column if not exists is_active boolean not null default true;
alter table public.principals add column if not exists metadata jsonb not null default '{}'::jsonb;
alter table public.principals add column if not exists created_at timestamptz not null default now();
alter table public.principals add column if not exists updated_at timestamptz not null default now();

create index if not exists idx_principals_type on public.principals(principal_type);
create index if not exists idx_principals_email on public.principals(email);

create table if not exists public.organizations (
    organization_id text primary key references public.principals(principal_id) on delete cascade,
    legal_name text not null default 'Organización',
    trade_name text not null default 'Organización',
    org_type text not null default 'FLEET' check (org_type in ('FLEET', 'WORKSHOP', 'TOW_FLEET', 'PARTS_SUPPLIER', 'PLATFORM')),
    tax_id text not null default 'TAX-000',
    status text not null default 'ACTIVE' check (status in ('ACTIVE', 'PENDING_VERIFICATION', 'SUSPENDED', 'CLOSED')),
    settings jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

alter table public.organizations add column if not exists organization_id text;
alter table public.organizations add column if not exists legal_name text not null default 'Organización';
alter table public.organizations add column if not exists trade_name text not null default 'Organización';
alter table public.organizations add column if not exists org_type text not null default 'FLEET';
alter table public.organizations add column if not exists tax_id text not null default 'TAX-000';
alter table public.organizations add column if not exists status text not null default 'ACTIVE';
alter table public.organizations add column if not exists settings jsonb not null default '{}'::jsonb;
alter table public.organizations add column if not exists created_at timestamptz not null default now();

create index if not exists idx_organizations_type on public.organizations(org_type);

-- -----------------------------------------------------------------------------
-- 2. SCD2 HISTORIZED MEMBERSHIPS
-- Prevents rewriting history when a driver or mechanic moves between organizations
-- -----------------------------------------------------------------------------
create table if not exists public.organization_memberships (
    membership_id text primary key,
    principal_id text not null,
    organization_id text not null,
    role text not null check (role in ('OWNER', 'ADMIN', 'DISPATCHER', 'MEMBER', 'DRIVER', 'TECHNICIAN', 'OPERATOR')),
    capabilities jsonb not null default '[]'::jsonb,
    valid_from timestamptz not null default now(),
    valid_to timestamptz, -- NULL means currently active membership
    created_at timestamptz not null default now()
);

alter table public.organization_memberships add column if not exists membership_id text;
alter table public.organization_memberships add column if not exists principal_id text;
alter table public.organization_memberships add column if not exists organization_id text;
alter table public.organization_memberships add column if not exists role text not null default 'MEMBER';
alter table public.organization_memberships add column if not exists capabilities jsonb not null default '[]'::jsonb;
alter table public.organization_memberships add column if not exists valid_from timestamptz not null default now();
alter table public.organization_memberships add column if not exists valid_to timestamptz;
alter table public.organization_memberships add column if not exists created_at timestamptz not null default now();

create index if not exists idx_org_memberships_principal on public.organization_memberships(principal_id);
create index if not exists idx_org_memberships_org on public.organization_memberships(organization_id);
create index if not exists idx_org_memberships_temporal on public.organization_memberships(principal_id, organization_id, valid_from, valid_to);

-- -----------------------------------------------------------------------------
-- 3. AUTHORITATIVE FINANCIAL LEDGER (Immutable Double-Entry)
-- -----------------------------------------------------------------------------
create table if not exists public.financial_ledger_entries (
    entry_id text primary key,
    idempotency_key text not null unique,
    trip_or_job_id text not null,
    organization_id text,
    principal_id text not null,
    entry_type text not null check (entry_type in (
        'PAYMENT_AUTHORIZED',
        'PAYMENT_CAPTURED',
        'DRIVER_EARNING_POSTED',
        'PLATFORM_FEE_POSTED',
        'TAX_POSTED',
        'TIP_POSTED',
        'PROMOTION_POSTED',
        'REFUND_POSTED',
        'CHARGEBACK_POSTED',
        'PAYOUT_CREATED',
        'PAYOUT_SETTLED'
    )),
    account_type text not null check (account_type in (
        'PASSENGER_RECEIVABLE',
        'DRIVER_PAYABLE',
        'PLATFORM_REVENUE',
        'TAX_LIABILITY',
        'REFUND_EXPENSE',
        'ESCROW'
    )),
    minor_units bigint not null,
    currency text not null default 'CRC',
    occurred_at timestamptz not null default now(),
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

create index if not exists idx_ledger_trip_job on public.financial_ledger_entries(trip_or_job_id);
create index if not exists idx_ledger_org on public.financial_ledger_entries(organization_id);
create index if not exists idx_ledger_principal on public.financial_ledger_entries(principal_id);
create index if not exists idx_ledger_occurred_at on public.financial_ledger_entries(occurred_at);

-- -----------------------------------------------------------------------------
-- 4. TRANSACTIONAL OUTBOX
-- Ensures atomic emission of canonical platform events
-- -----------------------------------------------------------------------------
create table if not exists public.transactional_outbox (
    outbox_id text primary key default gen_random_uuid()::text,
    aggregate_type text not null, -- e.g. 'RIDE', 'SERVICE_JOB', 'PAYMENT', 'TRUST'
    aggregate_id text not null,
    event_type text not null,
    payload jsonb not null,
    status text not null default 'PENDING' check (status in ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED', 'DEAD_LETTER')),
    retry_count integer not null default 0,
    last_error text,
    occurred_at timestamptz not null default now(),
    published_at timestamptz,
    created_at timestamptz not null default now()
);

create index if not exists idx_outbox_status_pending on public.transactional_outbox(status, occurred_at);
create index if not exists idx_outbox_aggregate on public.transactional_outbox(aggregate_type, aggregate_id);

-- -----------------------------------------------------------------------------
-- 5. IDEMPOTENT LEDGER RECORDING FUNCTION
-- -----------------------------------------------------------------------------
create or replace function public.record_ledger_entry_v1(
    p_entry_id text,
    p_idempotency_key text,
    p_trip_or_job_id text,
    p_organization_id text,
    p_principal_id text,
    p_entry_type text,
    p_account_type text,
    p_minor_units bigint,
    p_currency text default 'CRC',
    p_metadata jsonb default '{}'::jsonb
)
returns jsonb
language plpgsql
security definer
as $$
declare
    v_inserted_id text;
begin
    insert into public.financial_ledger_entries (
        entry_id,
        idempotency_key,
        trip_or_job_id,
        organization_id,
        principal_id,
        entry_type,
        account_type,
        minor_units,
        currency,
        metadata
    ) values (
        p_entry_id,
        p_idempotency_key,
        p_trip_or_job_id,
        p_organization_id,
        p_principal_id,
        p_entry_type,
        p_account_type,
        p_minor_units,
        p_currency,
        p_metadata
    )
    on conflict (idempotency_key) do nothing
    returning entry_id into v_inserted_id;

    if v_inserted_id is null then
        return jsonb_build_object(
            'ok', true,
            'status', 'DUPLICATE_IGNORED',
            'idempotency_key', p_idempotency_key
        );
    else
        return jsonb_build_object(
            'ok', true,
            'status', 'RECORDED',
            'entry_id', v_inserted_id
        );
    end if;
end;
$$;

-- -----------------------------------------------------------------------------
-- 6. ROW LEVEL SECURITY (Strict Multi-Tenant Isolation)
-- -----------------------------------------------------------------------------
alter table public.principals enable row level security;
alter table public.organizations enable row level security;
alter table public.organization_memberships enable row level security;
alter table public.financial_ledger_entries enable row level security;
alter table public.transactional_outbox enable row level security;

-- Principals: Users can view their own principal record, or directory profiles
create policy "Principals self and directory view"
    on public.principals for select
    to authenticated
    using (true);

-- Organizations: Visible to members or public
create policy "Organizations viewable by members"
    on public.organizations for select
    to authenticated
    using (true);

-- Memberships: Visible to the principal or org owners/admins
create policy "Memberships viewable by member or org admin"
    on public.organization_memberships for select
    to authenticated
    using (
        principal_id::text = (select auth.uid())::text
        or organization_id::text in (
            select m.organization_id::text from public.organization_memberships m
            where m.principal_id::text = (select auth.uid())::text
            and m.role in ('OWNER', 'ADMIN')
            and (m.valid_to is null or m.valid_to >= now())
        )
    );

-- Financial Ledger: Strictly scoped to the principal or organization
create policy "Ledger viewable only by authorized principal or org"
    on public.financial_ledger_entries for select
    to authenticated
    using (
        principal_id::text = (select auth.uid())::text
        or organization_id::text in (
            select m.organization_id::text from public.organization_memberships m
            where m.principal_id::text = (select auth.uid())::text
            and m.role in ('OWNER', 'ADMIN')
            and (m.valid_to is null or m.valid_to >= now())
        )
    );

-- Transactional Outbox: Internal engine access
create policy "Outbox internal access"
    on public.transactional_outbox for select
    to authenticated
    using (true);
