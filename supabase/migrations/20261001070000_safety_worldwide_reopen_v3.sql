-- Reopen Safety V3 for Worldwide Production Access
-- Enables online feature gates and read access for public maps, cases, timelines, accountability, and observatory.

begin;

-- 1. Reopen runtime feature gates
insert into public.runtime_feature_gates (key, enabled, reason, updated_at)
values
  ('safety_foundation', true, 'Worldwide production safety foundation active', now()),
  ('safety_reporting', true, 'Worldwide citizen safety intake active', now()),
  ('safety_evidence_upload', true, 'Authoritative cryptographic evidence custody active', now()),
  ('safety_public_map', true, 'Worldwide safety observatory and map active', now()),
  ('safety_public_cases', true, 'Public verifiable safety cases active', now()),
  ('safety_accountability', true, 'Institutional accountability audit trails active', now()),
  ('safety_observatory', true, 'Macro analytics observatory active', now()),
  ('safety_realtime', true, 'Realtime event streaming active', now()),
  ('safety_guardian', true, 'Autonomous safety guardian monitor active', now())
on conflict (key) do update set
  enabled = true,
  reason = excluded.reason,
  updated_at = now();

-- 2. Open permissions and RLS read policy on safety_public_points
grant select on public.safety_public_points to anon, authenticated;
alter table public.safety_public_points enable row level security;
drop policy if exists safety_public_points_v3_closed on public.safety_public_points;
drop policy if exists safety_public_points_anyone_read on public.safety_public_points;
drop policy if exists safety_public_points_v3_published_read on public.safety_public_points;
drop policy if exists safety_public_points_worldwide_read on public.safety_public_points;

create policy safety_public_points_worldwide_read
  on public.safety_public_points
  for select
  to anon, authenticated
  using (true);

-- 3. Open permissions and RLS read policy on safety_public_case_projection
grant select on public.safety_public_case_projection to anon, authenticated;
alter table public.safety_public_case_projection enable row level security;
drop policy if exists safety_public_case_projection_read on public.safety_public_case_projection;
drop policy if exists safety_public_case_projection_v3_closed on public.safety_public_case_projection;
drop policy if exists safety_public_case_projection_v3_published_read on public.safety_public_case_projection;
drop policy if exists safety_public_case_projection_worldwide_read on public.safety_public_case_projection;

create policy safety_public_case_projection_worldwide_read
  on public.safety_public_case_projection
  for select
  to anon, authenticated
  using (true);

-- 4. Open permissions and RLS read policy on safety_public_case_timeline_projection
grant select on public.safety_public_case_timeline_projection to anon, authenticated;
alter table public.safety_public_case_timeline_projection enable row level security;
drop policy if exists safety_public_case_timeline_projection_read on public.safety_public_case_timeline_projection;
drop policy if exists safety_public_case_timeline_projection_v3_closed on public.safety_public_case_timeline_projection;
drop policy if exists safety_public_timelines_worldwide_read on public.safety_public_case_timeline_projection;
drop policy if exists safety_public_case_timeline_worldwide_read on public.safety_public_case_timeline_projection;

create policy safety_public_case_timeline_worldwide_read
  on public.safety_public_case_timeline_projection
  for select
  to anon, authenticated
  using (true);

-- 5. Open permissions and RLS read policy on safety_public_case_claim_projection
grant select on public.safety_public_case_claim_projection to anon, authenticated;
alter table public.safety_public_case_claim_projection enable row level security;
drop policy if exists safety_public_case_claim_projection_read on public.safety_public_case_claim_projection;
drop policy if exists safety_public_case_claim_projection_v3_closed on public.safety_public_case_claim_projection;
drop policy if exists safety_public_claims_worldwide_read on public.safety_public_case_claim_projection;
drop policy if exists safety_public_case_claim_worldwide_read on public.safety_public_case_claim_projection;

create policy safety_public_case_claim_worldwide_read
  on public.safety_public_case_claim_projection
  for select
  to anon, authenticated
  using (true);

-- 6. Open permissions and RLS read policy on safety_public_accountability_projection
grant select on public.safety_public_accountability_projection to anon, authenticated;
alter table public.safety_public_accountability_projection enable row level security;
drop policy if exists safety_public_accountability_projection_v3_closed on public.safety_public_accountability_projection;
drop policy if exists safety_accountability_v3_published_read on public.safety_public_accountability_projection;
drop policy if exists safety_public_accountability_worldwide_read on public.safety_public_accountability_projection;

create policy safety_public_accountability_worldwide_read
  on public.safety_public_accountability_projection
  for select
  to anon, authenticated
  using (true);

commit;
