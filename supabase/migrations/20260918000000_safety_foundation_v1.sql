-- MEET Safety Foundation V1
-- Migration: 20260918000000_safety_foundation_v1.sql
-- Creates private/public safety domains, reports, claims, evidence, sources,
-- events, cases, publication firewall, and accountability graph.
--
-- Key design: Android creates canonical aggregate ID (UUIDv4). Server uses it
-- as the authoritative report_id. No gen_random_uuid() for report IDs.
-- REPORT ≠ FACT. LOCAL_ONLY until server ACK. No optimistic state.

begin;

-- ============================================================
-- SCHEMA: safety_private
-- ============================================================
create schema if not exists safety_private;

revoke all on schema safety_private
from public, anon, authenticated;

-- ============================================================
-- EXTENSIONS
-- ============================================================
create extension if not exists pgcrypto
with schema extensions;

-- ============================================================
-- PUBLIC: safety_reports (owner-visible metadata only)
-- ============================================================
create table if not exists public.safety_reports (
    id uuid primary key,

    reporter_user_id uuid not null
        references auth.users(id)
        on delete restrict,

    category text not null
        check (
            category in (
                'HOMICIDE',
                'VIOLENT_INCIDENT',
                'DRUG_SALE_ACTIVITY',
                'THREAT',
                'MISSING_PERSON',
                'INSTITUTIONAL_CONDUCT',
                'OTHER'
            )
        ),

    state text not null default 'RECEIVED'
        check (
            state in (
                'RECEIVED',
                'TRIAGE',
                'UNDER_REVIEW',
                'CLOSED'
            )
        ),

    state_version bigint not null default 1,

    occurred_at timestamptz,

    created_at timestamptz not null default now(),

    updated_at timestamptz not null default now()
);

alter table public.safety_reports
enable row level security;

revoke all on public.safety_reports
from anon, authenticated;

grant select, insert on public.safety_reports
to authenticated;

create policy safety_reports_owner_read
on public.safety_reports
for select
to authenticated
using (
    reporter_user_id = auth.uid()
);

create policy safety_reports_owner_insert
on public.safety_reports
for insert
to authenticated
with check (
    reporter_user_id = auth.uid()
);

create policy safety_reports_service_update
on public.safety_reports
for update
to service_role
using (true)
with check (true);

-- ============================================================
-- PRIVATE: report_content (narrative, GPS, source relation)
-- ============================================================
create table if not exists safety_private.report_content (
    report_id uuid primary key
        references public.safety_reports(id)
        on delete restrict,

    narrative text not null,

    source_relation text not null,

    latitude double precision,
    longitude double precision,
    accuracy_meters real,

    client_payload_sha256 text,

    server_payload_sha256 text not null
        check (
            server_payload_sha256 ~ '^[a-f0-9]{64}$'
        ),

    created_at timestamptz not null default now(),

    check (
        latitude is null
        or latitude between -90 and 90
    ),

    check (
        longitude is null
        or longitude between -180 and 180
    )
);

revoke all on safety_private.report_content
from public, anon, authenticated;

grant select, insert on safety_private.report_content
to service_role;

-- ============================================================
-- PUBLIC: safety_command_dedup (idempotency)
-- ============================================================
create table if not exists public.safety_command_dedup (
    idempotency_key uuid primary key,

    actor_id uuid not null
        references auth.users(id)
        on delete restrict,

    aggregate_id uuid not null,

    command_type text not null,

    server_payload_sha256 text not null,

    result jsonb,

    created_at timestamptz not null default now()
);

alter table public.safety_command_dedup
enable row level security;

revoke all on public.safety_command_dedup
from anon, authenticated;

grant select, insert, update on public.safety_command_dedup
to service_role;

-- ============================================================
-- PUBLIC: safety_claims
-- ============================================================
create table if not exists public.safety_claims (
    id uuid primary key default gen_random_uuid(),

    report_id uuid not null
        references public.safety_reports(id)
        on delete restrict,

    subject_ref text,

    predicate text not null,

    state text not null default 'ALLEGED'
        check (
            state in (
                'ALLEGED',
                'OBSERVED',
                'DOCUMENTED',
                'CORROBORATED',
                'STRONGLY_CORROBORATED',
                'DISPUTED',
                'CONTRADICTED',
                'RETRACTED',
                'SUPERSEDED',
                'UNRESOLVED'
            )
        ),

    methodology_version text not null,

    state_version bigint not null default 1,

    created_at timestamptz not null default now(),

    updated_at timestamptz not null default now()
);

alter table public.safety_claims
enable row level security;

revoke all on public.safety_claims
from anon, authenticated;

grant select on public.safety_claims
to authenticated;

grant select, insert, update on public.safety_claims
to service_role;

create policy safety_claims_owner_read
on public.safety_claims
for select
to authenticated
using (
    report_id in (
        select id from public.safety_reports
        where reporter_user_id = auth.uid()
    )
);

-- ============================================================
-- PUBLIC: safety_sources
-- ============================================================
create table if not exists public.safety_sources (
    id uuid primary key default gen_random_uuid(),

    report_id uuid not null
        references public.safety_reports(id)
        on delete restrict,

    source_type text not null
        check (
            source_type in (
                'DIRECT_WITNESS',
                'FAMILY_OR_NEIGHBOR',
                'SECOND_HAND',
                'DOCUMENTARY',
                'JOURNALISTIC',
                'PUBLIC_RECORD',
                'INSTITUTIONAL',
                'ANONYMOUS',
                'UNKNOWN'
            )
        ),

    reliability_score real not null default 0.5
        check (reliability_score between 0.0 and 1.0),

    independence_score real not null default 0.5
        check (independence_score between 0.0 and 1.0),

    cluster_id uuid
        references public.safety_source_clusters(id)
        on delete set null,

    created_at timestamptz not null default now()
);

alter table public.safety_sources
enable row level security;

revoke all on public.safety_sources
from anon, authenticated;

grant select on public.safety_sources
to authenticated;

grant select, insert, update on public.safety_sources
to service_role;

create policy safety_sources_owner_read
on public.safety_sources
for select
to authenticated
using (
    report_id in (
        select id from public.safety_reports
        where reporter_user_id = auth.uid()
    )
);

-- ============================================================
-- PUBLIC: safety_source_clusters
-- ============================================================
create table if not exists public.safety_source_clusters (
    id uuid primary key default gen_random_uuid(),

    methodology_version text not null,

    created_at timestamptz not null default now()
);

alter table public.safety_source_clusters
enable row level security;

revoke all on public.safety_source_clusters
from anon, authenticated;

grant select, insert on public.safety_source_clusters
to service_role;

-- ============================================================
-- PUBLIC: safety_claim_sources (claim ↔ source edges)
-- ============================================================
create table if not exists public.safety_claim_sources (
    claim_id uuid not null
        references public.safety_claims(id)
        on delete restrict,

    source_id uuid not null
        references public.safety_sources(id)
        on delete restrict,

    role text not null default 'SUPPORTING'
        check (
            role in (
                'SUPPORTING',
                'CONTRADICTING',
                'CORROBORATING',
                'PRIMARY',
                'SECONDARY'
            )
        ),

    created_at timestamptz not null default now(),

    primary key (claim_id, source_id)
);

alter table public.safety_claim_sources
enable row level security;

revoke all on public.safety_claim_sources
from anon, authenticated;

grant select, insert on public.safety_claim_sources
to service_role;

-- ============================================================
-- PUBLIC: safety_claim_relations (claim ↔ claim edges)
-- ============================================================
create table if not exists public.safety_claim_relations (
    source_claim_id uuid not null
        references public.safety_claims(id)
        on delete restrict,

    target_claim_id uuid not null
        references public.safety_claims(id)
        on delete restrict,

    relation_type text not null
        check (
            relation_type in (
                'SUPPORTS',
                'CONTRADICTS',
                'CORROBORATES',
                'SUPERSEDES',
                'DEPENDS_ON'
            )
        ),

    created_at timestamptz not null default now(),

    primary key (source_claim_id, target_claim_id),

    check (source_claim_id <> target_claim_id)
);

alter table public.safety_claim_relations
enable row level security;

revoke all on public.safety_claim_relations
from anon, authenticated;

grant select, insert on public.safety_claim_relations
to service_role;

-- ============================================================
-- PUBLIC: safety_events
-- ============================================================
create table if not exists public.safety_events (
    id uuid primary key default gen_random_uuid(),

    report_id uuid not null
        references public.safety_reports(id)
        on delete restrict,

    event_type text not null,

    event_summary text,

    confidence real not null default 0.5
        check (confidence between 0.0 and 1.0),

    occurred_at timestamptz,

    created_at timestamptz not null default now()
);

alter table public.safety_events
enable row level security;

revoke all on public.safety_events
from anon, authenticated;

grant select on public.safety_events
to authenticated;

grant select, insert, update on public.safety_events
to service_role;

create policy safety_events_owner_read
on public.safety_events
for select
to authenticated
using (
    report_id in (
        select id from public.safety_reports
        where reporter_user_id = auth.uid()
    )
);

-- ============================================================
-- PUBLIC: safety_event_claims (event ↔ claim edges)
-- ============================================================
create table if not exists public.safety_event_claims (
    event_id uuid not null
        references public.safety_events(id)
        on delete restrict,

    claim_id uuid not null
        references public.safety_claims(id)
        on delete restrict,

    relation_type text not null default 'IMPLIES'
        check (
            relation_type in (
                'IMPLIES',
                'SUPPORTS',
                'CONTRADICTS',
                'DEPENDS_ON'
            )
        ),

    created_at timestamptz not null default now(),

    primary key (event_id, claim_id)
);

alter table public.safety_event_claims
enable row level security;

revoke all on public.safety_event_claims
from anon, authenticated;

grant select, insert on public.safety_event_claims
to service_role;

-- ============================================================
-- PUBLIC: safety_cases
-- ============================================================
create table if not exists public.safety_cases (
    id uuid primary key default gen_random_uuid(),

    title text not null,

    status text not null default 'OPEN'
        check (
            status in (
                'OPEN',
                'TRIAGE',
                'UNDER_REVIEW',
                'CLOSED',
                'VOIDED'
            )
        ),

    confidence_score real not null default 0.0
        check (confidence_score between 0.0 and 1.0),

    state_version bigint not null default 1,

    created_at timestamptz not null default now(),

    updated_at timestamptz not null default now()
);

alter table public.safety_cases
enable row level security;

revoke all on public.safety_cases
from anon, authenticated;

grant select, insert, update on public.safety_cases
to service_role;

-- ============================================================
-- PUBLIC: safety_case_events (case ↔ event edges)
-- ============================================================
create table if not exists public.safety_case_events (
    case_id uuid not null
        references public.safety_cases(id)
        on delete restrict,

    event_id uuid not null
        references public.safety_events(id)
        on delete restrict,

    added_at timestamptz not null default now(),

    primary key (case_id, event_id)
);

alter table public.safety_case_events
enable row level security;

revoke all on public.safety_case_events
from anon, authenticated;

grant select, insert on public.safety_case_events
to service_role;

-- ============================================================
-- PUBLIC: safety_evidence_objects
-- ============================================================
create table if not exists public.safety_evidence_objects (
    id uuid primary key default gen_random_uuid(),

    report_id uuid not null
        references public.safety_reports(id)
        on delete restrict,

    uploader_user_id uuid not null,

    storage_path text not null unique,

    content_sha256 text not null
        check (
            content_sha256 ~ '^[a-f0-9]{64}$'
        ),

    mime_type text not null,

    byte_count bigint not null
        check (byte_count > 0),

    captured_at timestamptz,

    received_at timestamptz not null default now(),

    parent_evidence_id uuid
        references public.safety_evidence_objects(id),

    derivation_type text,

    created_at timestamptz not null default now()
);

alter table public.safety_evidence_objects
enable row level security;

revoke all on public.safety_evidence_objects
from anon, authenticated;

grant select on public.safety_evidence_objects
to authenticated;

grant select, insert, update on public.safety_evidence_objects
to service_role;

create policy safety_evidence_objects_owner_read
on public.safety_evidence_objects
for select
to authenticated
using (
    report_id in (
        select id from public.safety_reports
        where reporter_user_id = auth.uid()
    )
);

-- ============================================================
-- PUBLIC: safety_evidence_custody
-- ============================================================
create table if not exists public.safety_evidence_custody (
    id uuid primary key default gen_random_uuid(),

    evidence_id uuid not null
        references public.safety_evidence_objects(id)
        on delete restrict,

    event_type text not null,

    actor_id uuid,

    reason_code text,

    created_at timestamptz not null default now()
);

alter table public.safety_evidence_custody
enable row level security;

revoke all on public.safety_evidence_custody
from anon, authenticated;

grant select, insert on public.safety_evidence_custody
to service_role;

-- ============================================================
-- PUBLIC: safety_publication_decisions (firewall)
-- ============================================================
create table if not exists public.safety_publication_decisions (
    id uuid primary key default gen_random_uuid(),

    report_id uuid not null
        references public.safety_reports(id)
        on delete restrict,

    decision text not null default 'HOLD'
        check (
            decision in (
                'PUBLISH',
                'HOLD',
                'REDACT',
                'REJECT',
                'WITHDRAW'
            )
        ),

    redaction_reason text,

    reviewer_id uuid,

    reviewed_at timestamptz,

    created_at timestamptz not null default now(),

    updated_at timestamptz not null default now()
);

alter table public.safety_publication_decisions
enable row level security;

revoke all on public.safety_publication_decisions
from anon, authenticated;

grant select on public.safety_publication_decisions
to authenticated;

grant select, insert, update on public.safety_publication_decisions
to service_role;

create policy safety_publication_decisions_owner_read
on public.safety_publication_decisions
for select
to authenticated
using (
    report_id in (
        select id from public.safety_reports
        where reporter_user_id = auth.uid()
    )
);

-- ============================================================
-- PUBLIC: safety_publication_reviews (firewall audit)
-- ============================================================
create table if not exists public.safety_publication_reviews (
    id uuid primary key default gen_random_uuid(),

    decision_id uuid not null
        references public.safety_publication_decisions(id)
        on delete restrict,

    review_action text not null
        check (
            review_action in (
                'SUBMITTED',
                'AUTO_APPROVED',
                'MANUAL_APPROVED',
                'MANUAL_REJECTED',
                'OVERRIDE_REDACTED',
                'OVERRIDE_WITHDRAWN'
            )
        ),

    reviewer_id uuid,

    notes text,

    created_at timestamptz not null default now()
);

alter table public.safety_publication_reviews
enable row level security;

revoke all on public.safety_publication_reviews
from anon, authenticated;

grant select, insert on public.safety_publication_reviews
to service_role;

-- ============================================================
-- PUBLIC: safety_accountability_events
-- ============================================================
create table if not exists public.safety_accountability_events (
    id uuid primary key default gen_random_uuid(),

    case_id uuid,

    institution_ref text not null,

    event_type text not null
        check (
            event_type in (
                'REPORT_SENT',
                'DELIVERY_CONFIRMED',
                'REFERENCE_RECEIVED',
                'FOLLOW_UP_SENT',
                'RESPONSE_DOCUMENTED',
                'PUBLIC_ACTION_FOUND',
                'RESULT_DOCUMENTED'
            )
        ),

    occurred_at timestamptz not null,

    source_id uuid,

    evidence_id uuid,

    created_at timestamptz not null default now()
);

alter table public.safety_accountability_events
enable row level security;

revoke all on public.safety_accountability_events
from anon, authenticated;

grant select, insert on public.safety_accountability_events
to service_role;

-- ============================================================
-- PUBLIC: safety_public_points (map projection only)
-- ============================================================
create table if not exists public.safety_public_points (
    id uuid primary key default gen_random_uuid(),

    claim_id uuid,

    category text not null,

    display_latitude double precision not null,

    display_longitude double precision not null,

    geo_disclosure text not null,

    location_accuracy_meters integer,

    label text not null,

    claim_state text not null,

    independent_source_count integer not null default 0,

    first_documented_at timestamptz,

    last_reviewed_at timestamptz not null,

    published_at timestamptz not null default now(),

    server_version bigint not null default 1
);

alter table public.safety_public_points
enable row level security;

revoke all on public.safety_public_points
from anon, authenticated;

grant select on public.safety_public_points
to authenticated;

create policy safety_public_points_anyone_read
on public.safety_public_points
for select
to authenticated
using (true);

-- ============================================================
-- RPC: safety_create_report_v2
-- Idempotent, server-authoritative report creation.
-- Android creates canonical aggregate ID (p_report_id).
-- Server does NOT generate report ID.
-- ============================================================
create or replace function public.safety_create_report_v2(
    p_report_id uuid,
    p_idempotency_key uuid,
    p_category text,
    p_narrative text,
    p_occurred_at timestamptz,
    p_latitude double precision,
    p_longitude double precision,
    p_accuracy_meters real,
    p_source_relation text,
    p_client_payload_sha256 text
)
returns jsonb
language plpgsql
security definer
set search_path = public, safety_private
as $$
declare
    v_actor uuid;
    v_server_sha256 text;
    v_server_payload jsonb;
    v_reserved uuid;
    v_existing record;
begin
    -- 1. Authenticate
    v_actor := auth.uid();
    if v_actor is null then
        raise exception using
            errcode = '28000',
            message = 'AUTHENTICATION_REQUIRED';
    end if;

    -- 2. Validate
    if p_category not in (
        'HOMICIDE','VIOLENT_INCIDENT','DRUG_SALE_ACTIVITY',
        'THREAT','MISSING_PERSON','INSTITUTIONAL_CONDUCT','OTHER'
    ) then
        raise exception using
            errcode = '22023',
            message = 'INVALID_CATEGORY';
    end if;

    if p_narrative is null or length(trim(p_narrative)) < 10 then
        raise exception using
            errcode = '22023',
            message = 'NARRATIVE_TOO_SHORT';
    end if;

    if p_source_relation not in (
        'DIRECT_WITNESS','FAMILY_OR_NEIGHBOR','SECOND_HAND',
        'DOCUMENTARY','JOURNALISTIC','PUBLIC_RECORD','UNKNOWN'
    ) then
        raise exception using
            errcode = '22023',
            message = 'INVALID_SOURCE_RELATION';
    end if;

    -- 3. Canonicalize server payload (includes Android-provided report_id)
    v_server_payload := jsonb_build_object(
        'report_id', p_report_id,
        'category', p_category,
        'narrative', trim(p_narrative),
        'occurred_at', p_occurred_at,
        'latitude', p_latitude,
        'longitude', p_longitude,
        'accuracy_meters', p_accuracy_meters,
        'source_relation', p_source_relation
    );

    -- 4. Server-authoritative SHA-256
    v_server_sha256 := encode(
        extensions.digest(
            convert_to(
                'MEET-SAFETY-REPORT-V2' || chr(0) || v_server_payload::text,
                'UTF8'
            ),
            'sha256'
        ),
        'hex'
    );

    -- 5. Atomic idempotency reserve
    insert into public.safety_command_dedup (
        idempotency_key, actor_id, aggregate_id,
        command_type, server_payload_sha256, result
    ) values (
        p_idempotency_key, v_actor, p_report_id,
        'CREATE_REPORT', v_server_sha256, null
    )
    on conflict (idempotency_key) do nothing
    returning idempotency_key into v_reserved;

    if v_reserved is null then
        -- Duplicate key: check for protocol violation
        select * into v_existing
        from public.safety_command_dedup
        where idempotency_key = p_idempotency_key;

        if v_existing.actor_id <> v_actor
           or v_existing.command_type <> 'CREATE_REPORT'
           or v_existing.server_payload_sha256 <> v_server_sha256
        then
            raise exception using
                errcode = '23505',
                message = 'IDEMPOTENCY_PROTOCOL_VIOLATION';
        end if;

        if v_existing.result is null then
            raise exception using
                errcode = '40001',
                message = 'COMMAND_RESERVATION_INCOMPLETE';
        end if;

        return v_existing.result;
    end if;

    -- 6. Insert report (using Android-provided report_id as canonical)
    insert into public.safety_reports (
        id, reporter_user_id, category, state, occurred_at
    ) values (
        p_report_id, v_actor, p_category, 'RECEIVED', p_occurred_at
    );

    -- 7. Insert private content
    insert into safety_private.report_content (
        report_id, narrative, source_relation,
        latitude, longitude, accuracy_meters,
        client_payload_sha256, server_payload_sha256
    ) values (
        p_report_id, trim(p_narrative), p_source_relation,
        p_latitude, p_longitude, p_accuracy_meters,
        p_client_payload_sha256, v_server_sha256
    );

    -- 8. Build result
    declare
        v_result jsonb;
    begin
        v_result := jsonb_build_object(
            'report_id', p_report_id,
            'state', 'RECEIVED',
            'server_version', 1,
            'server_payload_sha256', v_server_sha256,
            'correlation_id', null::text
        );

        -- 9. Persist result in dedup
        update public.safety_command_dedup
        set result = v_result
        where idempotency_key = p_idempotency_key;

        return v_result;
    end;
end;
$$;

-- ============================================================
-- STORAGE BUCKETS
-- ============================================================
insert into storage.buckets (id, name, public)
values ('safety-evidence-original', 'safety-evidence-original', false)
on conflict (id) do nothing;

insert into storage.buckets (id, name, public)
values ('safety-evidence-public', 'safety-evidence-public', true)
on conflict (id) do nothing;

-- ============================================================
-- STORAGE POLICIES
-- ============================================================
create policy "Authenticated users can upload safety evidence originals"
on storage.objects
for insert
to authenticated
with check (
    bucket_id = 'safety-evidence-original'
    and (storage.foldername(name))[1] = 'safety-evidence-original'
);

create policy "Authenticated users can read own safety evidence originals"
on storage.objects
for select
to authenticated
using (
    bucket_id = 'safety-evidence-original'
    and owner = auth.uid()
);

create policy "Anyone can read public safety evidence"
on storage.objects
for select
to public
using (
    bucket_id = 'safety-evidence-public'
);

commit;
