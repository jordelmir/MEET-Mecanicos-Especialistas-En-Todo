begin;

grant usage on schema safety_private to service_role;

-- Intake must remain private while publication authority is rebuilt. Keep the
-- gate change, trigger retirement, historical quarantine, and read closure in
-- one transaction so a partially applied firewall cannot expose old rows.
update public.runtime_feature_gates
set enabled = false,
    reason = 'Safety V3 authoritative public projections not yet reopened',
    updated_at = now()
where key in (
    'safety_public_map',
    'safety_public_cases',
    'safety_accountability',
    'safety_observatory',
    'safety_realtime'
);

-- These historical SECURITY DEFINER RPCs bypass table RLS and do not check
-- the publication gate. Observatory access resumes only through a V3
-- suppression-aware replacement.
revoke all on function public.safety_observatory_query_v1(
    timestamptz, timestamptz, text, text, text, text
) from public, anon, authenticated, service_role;
revoke all on function public.safety_observatory_query_v2(
    timestamptz, timestamptz, text, text, text, text
) from public, anon, authenticated, service_role;

drop trigger if exists safety_project_citizen_report_point
on safety_private.report_content;

-- Fail closed even if a future migration accidentally reattaches the legacy
-- trigger function. Report intake itself continues through create_report_v2.
create or replace function public.safety_project_citizen_report_point()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    raise exception using
        errcode = '42501',
        message = 'SAFETY_REPORT_AUTO_PUBLICATION_FORBIDDEN';
end;
$$;
revoke all on function public.safety_project_citizen_report_point()
from public, anon, authenticated;

create table if not exists safety_private.public_point_quarantine_v3 (
    public_point_id uuid primary key,
    snapshot jsonb not null,
    quarantine_reason text not null,
    quarantined_at timestamptz not null default now()
);
revoke all on safety_private.public_point_quarantine_v3
from public, anon, authenticated;
grant select on safety_private.public_point_quarantine_v3 to service_role;

create table if not exists safety_private.public_point_report_link_quarantine_v3 (
    report_id uuid primary key,
    public_point_id uuid not null,
    snapshot jsonb not null,
    quarantine_reason text not null,
    quarantined_at timestamptz not null default now()
);
revoke all on safety_private.public_point_report_link_quarantine_v3
from public, anon, authenticated;
grant select on safety_private.public_point_report_link_quarantine_v3 to service_role;

-- The case, claim, timeline, and accountability projections have no V3
-- publication decision link. Preserve every row before retiring them.
create table if not exists safety_private.public_case_projection_quarantine_v3 (
    source_table text not null,
    source_key text not null,
    snapshot jsonb not null,
    quarantine_reason text not null,
    quarantined_at timestamptz not null default now(),
    primary key (source_table, source_key)
);
revoke all on safety_private.public_case_projection_quarantine_v3
from public, anon, authenticated;
grant select on safety_private.public_case_projection_quarantine_v3 to service_role;

insert into safety_private.public_point_quarantine_v3 (
    public_point_id, snapshot, quarantine_reason
)
select p.id, to_jsonb(p), 'PRE_V3_PUBLICATION_AUTHORITY'
from public.safety_public_points p
on conflict (public_point_id) do nothing;

-- This private link has ON DELETE CASCADE to the point, so snapshot it first.
insert into safety_private.public_point_report_link_quarantine_v3 (
    report_id, public_point_id, snapshot, quarantine_reason
)
select l.report_id, l.public_point_id, to_jsonb(l),
       'PRE_V3_PUBLICATION_AUTHORITY'
from safety_private.public_point_report_links l
on conflict (report_id) do nothing;

insert into safety_private.public_case_projection_quarantine_v3 (
    source_table, source_key, snapshot, quarantine_reason
)
select 'safety_public_case_projection', c.case_id::text, to_jsonb(c),
       'PRE_V3_PUBLICATION_AUTHORITY'
from public.safety_public_case_projection c
union all
select 'safety_public_case_timeline_projection',
       t.case_id::text || ':' || t.milestone_id::text, to_jsonb(t),
       'PRE_V3_PUBLICATION_AUTHORITY'
from public.safety_public_case_timeline_projection t
union all
select 'safety_public_case_claim_projection',
       c.case_id::text || ':' || c.claim_id::text, to_jsonb(c),
       'PRE_V3_PUBLICATION_AUTHORITY'
from public.safety_public_case_claim_projection c
union all
select 'safety_public_accountability_projection', a.event_id::text,
       to_jsonb(a), 'PRE_V3_PUBLICATION_AUTHORITY'
from public.safety_public_accountability_projection a
on conflict (source_table, source_key) do nothing;

-- Close direct Data API reads as well as the app feature gates. Later V3
-- publication migrations must explicitly replace these deny policies.
drop policy if exists safety_public_points_anyone_read
on public.safety_public_points;
drop policy if exists safety_public_points_v3_closed
on public.safety_public_points;
create policy safety_public_points_v3_closed
on public.safety_public_points for select to authenticated using (false);

drop policy if exists safety_public_case_projection_read
on public.safety_public_case_projection;
drop policy if exists safety_public_case_projection_v3_closed
on public.safety_public_case_projection;
create policy safety_public_case_projection_v3_closed
on public.safety_public_case_projection for select to authenticated using (false);

drop policy if exists safety_public_case_timeline_read
on public.safety_public_case_timeline_projection;
drop policy if exists safety_public_case_timeline_v3_closed
on public.safety_public_case_timeline_projection;
create policy safety_public_case_timeline_v3_closed
on public.safety_public_case_timeline_projection for select to authenticated using (false);

drop policy if exists safety_public_case_claim_read
on public.safety_public_case_claim_projection;
drop policy if exists safety_public_case_claim_v3_closed
on public.safety_public_case_claim_projection;
create policy safety_public_case_claim_v3_closed
on public.safety_public_case_claim_projection for select to authenticated using (false);

drop policy if exists safety_public_accountability_projection_read
on public.safety_public_accountability_projection;
drop policy if exists safety_public_accountability_projection_v3_closed
on public.safety_public_accountability_projection;
create policy safety_public_accountability_projection_v3_closed
on public.safety_public_accountability_projection for select to authenticated using (false);

delete from public.safety_public_points;
delete from public.safety_public_accountability_projection;
delete from public.safety_public_case_claim_projection;
delete from public.safety_public_case_timeline_projection;
delete from public.safety_public_case_projection;

commit;
