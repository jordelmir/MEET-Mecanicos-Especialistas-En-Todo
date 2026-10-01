-- Reopen Safety V3 for Worldwide Production Access
-- Enables online feature gates and read access for public maps, cases, timelines, accountability, and observatory.

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

-- 2. Open RLS read policy on safety_public_points
drop policy if exists safety_public_points_v3_closed on public.safety_public_points;
drop policy if exists safety_public_points_worldwide_read on public.safety_public_points;

create policy safety_public_points_worldwide_read
  on public.safety_public_points
  for select
  to anon, authenticated
  using (true);

-- 3. Open RLS read policy on safety_public_cases
drop policy if exists safety_public_cases_v3_closed on public.safety_public_cases;
drop policy if exists safety_public_cases_worldwide_read on public.safety_public_cases;

create policy safety_public_cases_worldwide_read
  on public.safety_public_cases
  for select
  to anon, authenticated
  using (true);

-- 4. Open RLS read policy on safety_public_timelines
drop policy if exists safety_public_timelines_worldwide_read on public.safety_public_timelines;

create policy safety_public_timelines_worldwide_read
  on public.safety_public_timelines
  for select
  to anon, authenticated
  using (true);

-- 5. Open RLS read policy on safety_public_claims
drop policy if exists safety_public_claims_worldwide_read on public.safety_public_claims;

create policy safety_public_claims_worldwide_read
  on public.safety_public_claims
  for select
  to anon, authenticated
  using (true);
