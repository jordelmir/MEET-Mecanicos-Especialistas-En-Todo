-- ============================================================
-- SAFETY PUBLIC VIEWS + STORAGE FIX
-- Provides authenticated read access to cases, accountability,
-- and observatory data without exposing private content.
-- ============================================================

begin;

-- ============================================================
-- 1. Fix storage policy: use auth.uid() as folder, not bucket name
-- ============================================================
drop policy if exists "Authenticated users can upload safety evidence originals"
    on storage.objects;

create policy "Authenticated users can upload safety evidence originals"
on storage.objects
for insert
to authenticated
with check (
    bucket_id = 'safety-evidence-original'
    and (storage.foldername(name))[1] = auth.uid()::text
);

-- ============================================================
-- 2. Public view: safety_public_cases
-- Exposes case metadata without private investigation details
-- ============================================================
create or replace view public.safety_public_cases with (security_invoker = true) as
select
    c.id as case_id,
    c.title,
    c.status as lifecycle,
    c.confidence_score,
    c.created_at as published_at,
    c.updated_at as last_updated_at,
    c.state_version as server_version,
    (select count(*) from public.safety_case_events ce where ce.case_id = c.id) as event_count,
    (select count(distinct e.report_id)
     from public.safety_case_events ce
     join public.safety_events e on e.id = ce.event_id
    ) as linked_report_count
from public.safety_cases c
where c.status in ('OPEN', 'UNDER_REVIEW', 'CLOSED');

alter view public.safety_public_cases owner to postgres;

grant select on public.safety_public_cases to authenticated;

-- ============================================================
-- 3. Public view: safety_public_accountability
-- Exposes accountability timeline without source details
-- ============================================================
create or replace view public.safety_public_accountability with (security_invoker = true) as
select
    ae.id as event_id,
    ae.case_id,
    ae.institution_ref,
    ae.event_type,
    ae.occurred_at,
    ae.created_at,
    c.title as case_title
from public.safety_accountability_events ae
left join public.safety_cases c on c.id = ae.case_id;

alter view public.safety_public_accountability owner to postgres;

grant select on public.safety_public_accountability to authenticated;

-- ============================================================
-- 4. Public view: safety_public_observatory
-- Aggregated stats for the observatory dashboard
-- ============================================================
create or replace view public.safety_public_observatory with (security_invoker = true) as
select
    (select count(*) from public.safety_reports)::bigint as total_reports,
    (select count(*) from public.safety_reports where state = 'RECEIVED')::bigint as reports_received,
    (select count(*) from public.safety_reports where state = 'TRIAGE')::bigint as reports_triage,
    (select count(*) from public.safety_reports where state = 'UNDER_REVIEW')::bigint as reports_under_review,
    (select count(*) from public.safety_reports where state = 'CLOSED')::bigint as reports_closed,
    (select count(*) from public.safety_cases)::bigint as total_cases,
    (select count(*) from public.safety_cases where status = 'OPEN')::bigint as cases_open,
    (select count(*) from public.safety_cases where status = 'CLOSED')::bigint as cases_closed,
    (select count(*) from public.safety_public_points)::bigint as total_public_points,
    (select count(*) from public.safety_claims)::bigint as total_claims,
    (select count(*) from public.safety_claims where state = 'CORROBORATED')::bigint as claims_corroborated,
    (select count(*) from public.safety_claims where state = 'DISPUTED')::bigint as claims_disputed,
    (select count(*) from public.safety_accountability_events)::bigint as total_accountability_events;

alter view public.safety_public_observatory owner to postgres;

grant select on public.safety_public_observatory to authenticated;

-- ============================================================
-- 5. RLS on safety_cases: allow authenticated read via view
-- ============================================================
create policy safety_cases_authenticated_read
on public.safety_cases
for select
to authenticated
using (true);

-- ============================================================
-- 6. RLS on safety_accountability_events: allow authenticated read
-- ============================================================
create policy safety_accountability_events_authenticated_read
on public.safety_accountability_events
for select
to authenticated
using (true);

-- ============================================================
-- 7. RLS on safety_case_events: allow authenticated read
-- ============================================================
alter table public.safety_case_events
enable row level security;

create policy safety_case_events_authenticated_read
on public.safety_case_events
for select
to authenticated
using (true);

-- ============================================================
-- 8. RLS on safety_event_claims: allow authenticated read
-- ============================================================
alter table public.safety_event_claims
enable row level security;

create policy safety_event_claims_authenticated_read
on public.safety_event_claims
for select
to authenticated
using (true);

commit;
