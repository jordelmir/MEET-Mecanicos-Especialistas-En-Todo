begin;
-- 20261002090000_safety_public_evidence_access.sql
-- Allow public map points to display attached images and evidence worldwide

grant select on public.safety_evidence_objects to anon, authenticated;

-- Allow reading evidence objects for reports published on the public map
drop policy if exists safety_evidence_objects_public_read on public.safety_evidence_objects;
create policy safety_evidence_objects_public_read
on public.safety_evidence_objects
for select
to anon, authenticated
using (
    report_id in (select id from public.safety_public_points)
);

-- Allow reading storage objects in safety-evidence-original for published reports
drop policy if exists "Authenticated users can read public report evidence" on storage.objects;
drop policy if exists "Anyone can read published report evidence" on storage.objects;
create policy "Anyone can read published report evidence"
on storage.objects
for select
to anon, authenticated
using (
    bucket_id = 'safety-evidence-original'
    and exists (
        select 1 from public.safety_evidence_objects eo
        join public.safety_public_points pp on pp.id = eo.report_id
        where eo.storage_path = name
    )
);

-- Create public evidence view for fast, clean querying
create or replace view public.safety_public_evidence as
select
    eo.id as evidence_id,
    eo.report_id,
    eo.storage_path,
    eo.mime_type,
    eo.byte_count,
    eo.content_sha256,
    eo.created_at
from public.safety_evidence_objects eo
join public.safety_public_points pp on pp.id = eo.report_id;

grant select on public.safety_public_evidence to anon, authenticated;

commit;
