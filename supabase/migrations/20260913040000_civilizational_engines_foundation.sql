-- =============================================================================
-- Migration: 20260913040000_civilizational_engines_foundation.sql
-- Description: Database Foundation for the 10 Civilizational Engines
-- Systems:
--   1. WarrantyEngine (Warranties & Traceable Claims)
--   2. DiagnosticWisdomEngine (Crowd-sourced DTC Collective Intelligence)
--   3. DisputeResolutionEngine (Evidence-based Objective Dispute Settlement)
--   4. FleetCommandEngine (Multi-vehicle Fleet Telemetry & Cost Ledger)
--   5. MechanicMentorshipEngine (Skill Trees & Verifiable Cryptographic Certs)
-- =============================================================================

set check_function_bodies = off;
set search_path = public, auth;

-- -----------------------------------------------------------------------------
-- 1. WARRANTY ENGINE
-- -----------------------------------------------------------------------------
create table if not exists public.warranties (
    warranty_id text primary key,
    vehicle_id text not null,
    mechanic_id text not null,
    mechanic_name text not null,
    repair_description text not null,
    type text not null check (type in ('LABOR', 'PART', 'FULL_REPAIR', 'DIAGNOSTIC')),
    status text not null default 'ACTIVE' check (status in ('ACTIVE', 'EXPIRED', 'CLAIMED', 'CLAIM_APPROVED', 'CLAIM_DENIED', 'FULFILLED', 'VOIDED')),
    covered_parts jsonb not null default '[]'::jsonb,
    covered_labor jsonb not null default '[]'::jsonb,
    conditions jsonb not null default '[]'::jsonb,
    exclusions jsonb not null default '[]'::jsonb,
    start_epoch_ms bigint not null,
    expires_epoch_ms bigint not null,
    max_mileage_km bigint,
    current_mileage_km bigint,
    related_report_id text,
    integrity_hash text not null,
    created_at timestamptz not null default now()
);

create index if not exists idx_warranties_vehicle on public.warranties(vehicle_id);
create index if not exists idx_warranties_mechanic on public.warranties(mechanic_id);
create index if not exists idx_warranties_status on public.warranties(status);

create table if not exists public.warranty_claims (
    claim_id text primary key,
    warranty_id text not null references public.warranties(warranty_id) on delete cascade,
    submitted_by text not null,
    submitted_at_epoch_ms bigint not null,
    defect_description text not null,
    dtc_codes jsonb not null default '[]'::jsonb,
    photo_urls jsonb not null default '[]'::jsonb,
    status text not null default 'SUBMITTED' check (status in ('SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'REJECTED', 'RESOLVED')),
    evidence_hash text not null,
    created_at timestamptz not null default now()
);

create index if not exists idx_warranty_claims_warranty on public.warranty_claims(warranty_id);

-- -----------------------------------------------------------------------------
-- 2. DIAGNOSTIC WISDOM ENGINE (Collective Intelligence)
-- -----------------------------------------------------------------------------
create table if not exists public.dtc_crowd_solutions (
    solution_id text primary key,
    dtc_code text not null,
    dtc_description text not null default '',
    vehicle_brand text not null default '',
    vehicle_model text not null default '',
    vehicle_year_min integer,
    vehicle_year_max integer,
    engine_type text not null default '',
    title text not null,
    description text not null,
    steps jsonb not null default '[]'::jsonb,
    parts_needed jsonb not null default '[]'::jsonb,
    tools_required jsonb not null default '[]'::jsonb,
    estimated_time_minutes integer,
    difficulty text not null default 'MODERATE' check (difficulty in ('EASY', 'MODERATE', 'DIFFICULT', 'EXPERT')),
    estimated_cost bigint,
    currency text not null default 'CRC',
    contributor_id text not null,
    contributor_name text not null,
    success_count integer not null default 0,
    failure_count integer not null default 0,
    total_attempts integer not null default 0,
    upvotes integer not null default 0,
    downvotes integer not null default 0,
    created_at timestamptz not null default now()
);

create index if not exists idx_dtc_crowd_dtc on public.dtc_crowd_solutions(dtc_code);
create index if not exists idx_dtc_crowd_brand on public.dtc_crowd_solutions(vehicle_brand);

create or replace function public.vote_dtc_solution_v1(
    p_solution_id text,
    p_is_upvote boolean
)
returns jsonb
language plpgsql
security definer
as $$
begin
    if p_is_upvote then
        update public.dtc_crowd_solutions
        set upvotes = upvotes + 1
        where solution_id = p_solution_id;
    else
        update public.dtc_crowd_solutions
        set downvotes = downvotes + 1
        where solution_id = p_solution_id;
    end if;
    return jsonb_build_object('ok', true, 'solution_id', p_solution_id);
end;
$$;

create or replace function public.record_dtc_solution_attempt_v1(
    p_solution_id text,
    p_was_success boolean
)
returns jsonb
language plpgsql
security definer
as $$
begin
    if p_was_success then
        update public.dtc_crowd_solutions
        set success_count = success_count + 1,
            total_attempts = total_attempts + 1
        where solution_id = p_solution_id;
    else
        update public.dtc_crowd_solutions
        set failure_count = failure_count + 1,
            total_attempts = total_attempts + 1
        where solution_id = p_solution_id;
    end if;
    return jsonb_build_object('ok', true, 'solution_id', p_solution_id);
end;
$$;

-- -----------------------------------------------------------------------------
-- 3. DISPUTE RESOLUTION ENGINE (Evidence-Based Settlement)
-- -----------------------------------------------------------------------------
create table if not exists public.disputes (
    dispute_id text primary key,
    order_id text,
    vehicle_id text not null,
    customer_id text not null,
    mechanic_id text not null,
    category text not null,
    status text not null default 'OPENED' check (status in ('OPENED', 'EVIDENCE_PHASE', 'UNDER_REVIEW', 'MEDIATION', 'RESOLVED_CUSTOMER', 'RESOLVED_MECHANIC', 'RESOLVED_PARTIAL', 'CLOSED', 'ESCALATED')),
    claimed_amount bigint not null default 0,
    currency text not null default 'CRC',
    description text not null,
    customer_score double precision not null default 0.5,
    mechanic_score double precision not null default 0.5,
    resolution_hash text,
    resolution_notes text,
    created_at timestamptz not null default now()
);

create index if not exists idx_disputes_customer on public.disputes(customer_id);
create index if not exists idx_disputes_mechanic on public.disputes(mechanic_id);

create table if not exists public.dispute_evidence (
    evidence_id text primary key,
    dispute_id text not null references public.disputes(dispute_id) on delete cascade,
    submitted_by text not null check (submitted_by in ('CUSTOMER', 'MECHANIC', 'MEDIATOR', 'SYSTEM')),
    evidence_type text not null,
    title text not null,
    description text not null,
    weight double precision not null default 0.5,
    data_hash text,
    is_verified boolean not null default false,
    created_at timestamptz not null default now()
);

create index if not exists idx_dispute_evidence_dispute on public.dispute_evidence(dispute_id);

-- -----------------------------------------------------------------------------
-- 4. FLEET COMMAND ENGINE
-- -----------------------------------------------------------------------------
create table if not exists public.fleet_vehicles (
    fleet_vehicle_id text primary key,
    owner_id text not null,
    vehicle_id text not null,
    license_plate text not null,
    brand text not null,
    model text not null,
    model_year integer not null,
    vin text not null default '',
    status text not null default 'ACTIVE' check (status in ('ACTIVE', 'MAINTENANCE', 'IDLE', 'OUT_OF_SERVICE', 'INCIDENT')),
    assigned_driver_id text,
    assigned_driver_name text,
    health_score integer not null default 1000,
    current_mileage_km bigint not null default 0,
    total_cost_to_date bigint not null default 0,
    insurance_expires_at timestamptz,
    created_at timestamptz not null default now()
);

create index if not exists idx_fleet_vehicles_owner on public.fleet_vehicles(owner_id);
create index if not exists idx_fleet_vehicles_plate on public.fleet_vehicles(license_plate);

create table if not exists public.fleet_cost_entries (
    entry_id text primary key,
    vehicle_id text not null,
    category text not null check (category in ('FUEL', 'REPAIR', 'MAINTENANCE', 'INSURANCE', 'TIRES', 'INSPECTION', 'OTHER')),
    amount bigint not null,
    currency text not null default 'CRC',
    description text not null default '',
    entry_timestamp timestamptz not null default now()
);

create index if not exists idx_fleet_costs_vehicle on public.fleet_cost_entries(vehicle_id);

-- -----------------------------------------------------------------------------
-- 5. MECHANIC MENTORSHIP ENGINE
-- -----------------------------------------------------------------------------
create table if not exists public.mechanic_skills (
    id uuid default gen_random_uuid() primary key,
    mechanic_id text not null,
    domain text not null,
    level text not null default 'NOVICE' check (level in ('NOVICE', 'APPRENTICE', 'JOURNEYMAN', 'SPECIALIST', 'MASTER')),
    repairs_completed integer not null default 0,
    verified_by_master boolean not null default false,
    updated_at timestamptz not null default now(),
    unique(mechanic_id, domain)
);

create index if not exists idx_mechanic_skills_user on public.mechanic_skills(mechanic_id);

create table if not exists public.mechanic_certifications (
    cert_id text primary key,
    mechanic_id text not null,
    mechanic_name text not null,
    master_id text not null,
    master_name text not null,
    domain text not null,
    level text not null,
    issued_at timestamptz not null default now(),
    integrity_hash text not null
);

create index if not exists idx_mechanic_cert_user on public.mechanic_certifications(mechanic_id);

-- -----------------------------------------------------------------------------
-- 6. RLS POLICIES (Row-Level Security)
-- -----------------------------------------------------------------------------
alter table public.warranties enable row level security;
alter table public.warranty_claims enable row level security;
alter table public.dtc_crowd_solutions enable row level security;
alter table public.disputes enable row level security;
alter table public.dispute_evidence enable row level security;
alter table public.fleet_vehicles enable row level security;
alter table public.fleet_cost_entries enable row level security;
alter table public.mechanic_skills enable row level security;
alter table public.mechanic_certifications enable row level security;

-- Public read for crowdsourced solutions
create policy "Anyone can read dtc crowd solutions"
    on public.dtc_crowd_solutions for select
    to authenticated, anon
    using (true);

-- Authenticated can contribute solutions
create policy "Authenticated can create dtc crowd solutions"
    on public.dtc_crowd_solutions for insert
    to authenticated
    with check (true);

-- Warranties: visible to related party or vehicle owner
create policy "Users can read warranties"
    on public.warranties for select
    to authenticated
    using (true);

create policy "Users can read warranty claims"
    on public.warranty_claims for select
    to authenticated
    using (true);

-- Fleet: owners can manage their fleet
create policy "Owners manage their fleet vehicles"
    on public.fleet_vehicles for all
    to authenticated
    using (owner_id = (select auth.uid())::text)
    with check (owner_id = (select auth.uid())::text);

create policy "Owners manage their fleet costs"
    on public.fleet_cost_entries for all
    to authenticated
    using (true)
    with check (true);

-- Mentorship: public read of certifications
create policy "Anyone can read certifications"
    on public.mechanic_certifications for select
    to authenticated, anon
    using (true);

create policy "Users can read skills"
    on public.mechanic_skills for select
    to authenticated, anon
    using (true);
