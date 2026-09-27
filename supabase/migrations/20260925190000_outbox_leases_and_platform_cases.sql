-- =============================================================================
-- Migration: 20260925190000_outbox_leases_and_platform_cases.sql
-- Description: Master Order Ascension Maxima: Outbox Leases, Operational Cases,
--              and Server-Authoritative Agent Entitlements.
-- Directives: 22, 23, 24, 25, 46, 47, 48, 51.
-- =============================================================================

set check_function_bodies = off;
set search_path = public, auth;

-- -----------------------------------------------------------------------------
-- 1. TRANSACTIONAL OUTBOX EVENTS (FOR UPDATE SKIP LOCKED & LEASE LOCKS)
-- -----------------------------------------------------------------------------
create table if not exists public.elysium_outbox_events (
    outbox_id bigserial primary key,
    event_id varchar(64) not null unique,
    source_domain varchar(64) not null,
    source_type varchar(64) not null default 'DOMAIN_ENTITY',
    source_id varchar(128) not null,
    aggregate_type varchar(64) not null,
    aggregate_id varchar(128) not null,
    aggregate_version bigint not null default 1,
    event_type varchar(128) not null,
    payload text not null default '{}',
    target_principal_id varchar(128),
    correlation_id varchar(128),
    publish_attempts int not null default 0,
    next_attempt_at_epoch_ms bigint not null default 0,
    lease_owner varchar(128),
    lease_token varchar(128),
    lease_until_epoch_ms bigint,
    published_at_epoch_ms bigint,
    dead_lettered_at_epoch_ms bigint,
    last_error_code text,
    created_at timestamptz not null default now()
);

alter table public.elysium_outbox_events add column if not exists lease_owner varchar(128);
alter table public.elysium_outbox_events add column if not exists lease_token varchar(128);
alter table public.elysium_outbox_events add column if not exists lease_until_epoch_ms bigint;
alter table public.elysium_outbox_events add column if not exists next_attempt_at_epoch_ms bigint not null default 0;
alter table public.elysium_outbox_events add column if not exists dead_lettered_at_epoch_ms bigint;
alter table public.elysium_outbox_events add column if not exists last_error_code text;

create index if not exists idx_elysium_outbox_pending
    on public.elysium_outbox_events (next_attempt_at_epoch_ms, outbox_id)
    where published_at_epoch_ms is null and dead_lettered_at_epoch_ms is null;

create index if not exists idx_elysium_outbox_aggregate
    on public.elysium_outbox_events (aggregate_type, aggregate_id);

-- -----------------------------------------------------------------------------
-- 2. PLATFORM OPERATION CASES (DURABLE ANOMALIES & TRUTHFUL RECONCILIATION)
-- -----------------------------------------------------------------------------
create table if not exists public.platform_operation_cases (
    case_id text primary key,
    anomaly_type text not null,
    severity text not null check (severity in ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    affected_entity_type text not null,
    affected_entity_id text not null,
    reconciliation_state text not null default 'DETECTED' check (reconciliation_state in ('DETECTED', 'ANALYZING', 'REMEDIATING', 'RECONCILED', 'QUARANTINED', 'ESCALATED_MANUAL')),
    remediation_outcome text not null default 'PROPOSED' check (remediation_outcome in ('PROPOSED', 'EXECUTED', 'FAILED', 'MANUAL_REQUIRED')),
    remediation_action text,
    observed_metric jsonb not null default '{}'::jsonb,
    evidence_snapshot jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_platform_cases_state on public.platform_operation_cases (reconciliation_state, severity);
create index if not exists idx_platform_cases_entity on public.platform_operation_cases (affected_entity_type, affected_entity_id);

-- -----------------------------------------------------------------------------
-- 3. SERVER AUTHORITATIVE AGENT ENTITLEMENTS (FAIL-CLOSED)
-- -----------------------------------------------------------------------------
create table if not exists public.agent_entitlements (
    entitlement_id text primary key default gen_random_uuid()::text,
    user_id uuid not null,
    entitlement_key text not null,
    status text not null default 'ACTIVE' check (status in ('ACTIVE', 'REVOKED', 'EXPIRED', 'PENDING_PAYMENT')),
    source text not null default 'GOOGLE_PLAY' check (source in ('GOOGLE_PLAY', 'SINPE_MOVIL', 'ADMIN_GRANT', 'PROMO')),
    granted_at timestamptz not null default now(),
    expires_at timestamptz,
    purchase_token text,
    order_id text,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (user_id, entitlement_key)
);

create index if not exists idx_agent_entitlements_user on public.agent_entitlements (user_id, status);

-- Enable RLS
alter table public.agent_entitlements enable row level security;
alter table public.platform_operation_cases enable row level security;
alter table public.elysium_outbox_events enable row level security;

-- Policy: users can read their own entitlements
create policy "Users can read own entitlements"
    on public.agent_entitlements
    for select
    to authenticated
    using (auth.uid() = user_id);

-- Service role full access
create policy "Service role manages entitlements"
    on public.agent_entitlements
    for all
    to service_role
    using (true)
    with check (true);

create policy "Service role manages platform cases"
    on public.platform_operation_cases
    for all
    to service_role
    using (true)
    with check (true);

create policy "Service role manages outbox events"
    on public.elysium_outbox_events
    for all
    to service_role
    using (true)
    with check (true);
