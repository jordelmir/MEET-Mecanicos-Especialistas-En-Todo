begin;

-- ===========================================================================
-- Safety Observatory Demographics & Resolution Analytics
-- Adds victim demographic aggregates to public points and an enhanced
-- observatory RPC that returns gender-disaggregated statistics,
-- category breakdowns, and average resolution times.
--
-- Privacy invariant: only AGGREGATED counts appear in public projection.
-- Individual victim identities never exist in this schema.
-- ===========================================================================

-- 1. Victim demographic columns on public projection (aggregates only)
alter table public.safety_public_points
  add column if not exists victim_count_documented int not null default 0,
  add column if not exists victim_female_count int not null default 0,
  add column if not exists victim_male_count int not null default 0,
  add column if not exists victim_unknown_sex_count int not null default 0;

comment on column public.safety_public_points.victim_count_documented is
  'Total documented victims for this public point. Zero means not reported, not zero victims.';
comment on column public.safety_public_points.victim_female_count is
  'Number of female victims documented. UN femicide protocol categories.';

-- 2. Reporter-supplied victim demographics on private reports (optional)
alter table public.safety_reports
  add column if not exists reported_victim_count int,
  add column if not exists reported_victim_female int,
  add column if not exists reported_victim_male int;

-- Constraint: female + male <= total (when all provided)
alter table public.safety_reports
  add constraint safety_reports_victim_count_check
  check (
    reported_victim_count is null
    or (
      coalesce(reported_victim_female, 0) + coalesce(reported_victim_male, 0)
      <= reported_victim_count
    )
  );

-- 3. Observatory RPC V2 — demographics + resolution times + category breakdown
create or replace function public.safety_observatory_query_v2(
  p_from timestamptz default null,
  p_to timestamptz default null,
  p_category text default null,
  p_country_code text default null,
  p_admin1_code text default null,
  p_admin2_code text default null
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  result jsonb;
  resolution_all double precision;
  resolution_female double precision;
  resolution_male double precision;
begin
  -- Main aggregate query over public points
  select jsonb_build_object(
    'public_point_count', count(*),
    'homicide_count', count(*) filter (where category = 'HOMICIDE'),
    'violence_count', count(*) filter (where category = 'VIOLENT_INCIDENT'),
    'drugs_count', count(*) filter (where category = 'DRUG_SALE_ACTIVITY'),
    'threat_count', count(*) filter (where category = 'THREAT'),
    'missing_count', count(*) filter (where category = 'MISSING_PERSON'),
    'institutional_count', count(*) filter (where category = 'INSTITUTIONAL_CONDUCT'),
    'independent_source_count', coalesce(sum(independent_source_count), 0),
    'civil_source_count', coalesce(sum(civil_source_count), 0),
    'journalistic_source_count', coalesce(sum(journalistic_source_count), 0),
    'public_record_source_count', coalesce(sum(public_record_source_count), 0),
    'documentary_source_count', coalesce(sum(documentary_source_count), 0),
    'institutional_source_count', coalesce(sum(institutional_source_count), 0),
    'total_victims_documented', coalesce(sum(victim_count_documented), 0),
    'female_victims', coalesce(sum(victim_female_count), 0),
    'male_victims', coalesce(sum(victim_male_count), 0),
    'unknown_sex_victims', coalesce(sum(victim_unknown_sex_count), 0)
  ) into result
  from public.safety_public_points
  where (p_from is null or published_at >= p_from)
    and (p_to is null or published_at < p_to)
    and (p_category is null or category = p_category)
    and (p_country_code is null or country_code = p_country_code)
    and (p_admin1_code is null or admin1_code = p_admin1_code)
    and (p_admin2_code is null or admin2_code = p_admin2_code);

  -- Resolution time: average days from REPORT_SUBMITTED to RESULT_DOCUMENTED
  select coalesce(avg(extract(epoch from (r.occurred_at - i.occurred_at)) / 86400.0), -1)
  into resolution_all
  from public.safety_public_accountability_projection r
  join public.safety_public_accountability_projection i
    on r.case_id = i.case_id
  where i.event_type = 'REPORT_SUBMITTED'
    and r.event_type = 'RESULT_DOCUMENTED';

  -- Resolution time: female victim cases
  select coalesce(avg(extract(epoch from (r.occurred_at - i.occurred_at)) / 86400.0), -1)
  into resolution_female
  from public.safety_public_accountability_projection r
  join public.safety_public_accountability_projection i
    on r.case_id = i.case_id
  join public.safety_public_case_projection cp
    on cp.case_id = r.case_id
  where i.event_type = 'REPORT_SUBMITTED'
    and r.event_type = 'RESULT_DOCUMENTED'
    and exists (
      select 1 from public.safety_public_points sp
      where sp.category = 'HOMICIDE' and sp.victim_female_count > 0
    );

  -- Resolution time: male victim cases (excluding mixed female cases)
  select coalesce(avg(extract(epoch from (r.occurred_at - i.occurred_at)) / 86400.0), -1)
  into resolution_male
  from public.safety_public_accountability_projection r
  join public.safety_public_accountability_projection i
    on r.case_id = i.case_id
  join public.safety_public_case_projection cp
    on cp.case_id = r.case_id
  where i.event_type = 'REPORT_SUBMITTED'
    and r.event_type = 'RESULT_DOCUMENTED'
    and exists (
      select 1 from public.safety_public_points sp
      where sp.category = 'HOMICIDE'
        and sp.victim_male_count > 0
        and sp.victim_female_count = 0
    );

  result = result || jsonb_build_object(
    'avg_resolution_days_all', resolution_all,
    'avg_resolution_days_female_victim', resolution_female,
    'avg_resolution_days_male_victim', resolution_male
  );

  return result;
end;
$$;

revoke all on function public.safety_observatory_query_v2 from public;
grant execute on function public.safety_observatory_query_v2 to authenticated;

commit;
