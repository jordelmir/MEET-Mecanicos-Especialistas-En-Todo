\echo 'SourceMutationAndCrossReportWithdrawalRevokePublishedPoint'

-- The claim belongs to one report while its supporting source belongs to
-- another. This is the path a same-report-only withdrawal misses.
set role authenticated;
select set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', false);
select public.safety_create_report_v2(
  '31313131-3131-4313-8313-313131313131',
  '41414141-4141-4414-8414-414141414141',
  'VIOLENT_INCIDENT', 'Private claim report with separately sourced evidence.',
  now(), 9.87654, -84.23456, 20.0, 'DIRECT_WITNESS', repeat('c', 64));
select public.safety_create_report_v2(
  '51515151-5151-4515-8515-515151515151',
  '61616161-6161-4616-8616-616161616161',
  'VIOLENT_INCIDENT', 'Private source report that can be withdrawn.',
  now(), 9.76543, -84.34567, 20.0, 'DIRECT_WITNESS', repeat('d', 64));
reset role;

insert into public.safety_claims(id, report_id, predicate, state, methodology_version)
values ('71717171-7171-4717-8717-717171717171',
        '31313131-3131-4313-8313-313131313131',
        'Cross-report documented claim', 'DOCUMENTED', 'SAFETY-CLAIM-V3');
insert into public.safety_sources(id, report_id, source_type)
values ('81818181-8181-4818-8818-818181818181',
        '51515151-5151-4515-8515-515151515151', 'DOCUMENTARY');
insert into public.safety_claim_sources(claim_id, source_id, role)
values ('71717171-7171-4717-8717-717171717171',
        '81818181-8181-4818-8818-818181818181', 'SUPPORTING');

set role authenticated;
select set_config('request.jwt.claim.sub', '55555555-5555-4555-8555-555555555555', false);
select set_config('request.jwt.claims', '{"sub":"55555555-5555-4555-8555-555555555555","aal":"aal2"}', false);
select public.safety_recommend_claim_publication_v1(
  '71717171-7171-4717-8717-717171717171', 'READY_TO_PUBLISH',
  'DOCUMENTED_SOURCE', 'First cross-report source review'
) as source_candidate_one \gset
reset role;
set role authenticated;
select set_config('request.jwt.claim.sub', '66666666-6666-4666-8666-666666666666', false);
select set_config('request.jwt.claims', '{"sub":"66666666-6666-4666-8666-666666666666","aal":"aal2"}', false);
select public.safety_finalize_claim_publication_v1(
  :'source_candidate_one'::uuid, 'PUBLISH', 'LEGAL_REVIEW',
  '91919191-9191-4919-8919-919191919191');
reset role;
do $$ begin
  if not exists (select 1 from public.safety_public_points
                 where claim_id = '71717171-7171-4717-8717-717171717171') then
    raise exception 'Cross-report source fixture did not publish';
  end if;
end $$;

update public.safety_sources set source_type = 'ANONYMOUS'
where id = '81818181-8181-4818-8818-818181818181';
do $$ begin
  if exists (select 1 from public.safety_public_points
             where claim_id = '71717171-7171-4717-8717-717171717171')
     or not exists (select 1 from safety_private.claim_reevaluation_v3
                    where claim_id = '71717171-7171-4717-8717-717171717171'
                      and reason_code = 'SOURCE_CHANGED' and status = 'PENDING') then
    raise exception 'Source mutation did not revoke point and request reevaluation';
  end if;
end $$;

-- A new independent decision can publish only after source metadata is
-- restored and reviewed again.
update public.safety_sources set source_type = 'DOCUMENTARY'
where id = '81818181-8181-4818-8818-818181818181';
set role authenticated;
select set_config('request.jwt.claim.sub', '55555555-5555-4555-8555-555555555555', false);
select set_config('request.jwt.claims', '{"sub":"55555555-5555-4555-8555-555555555555","aal":"aal2"}', false);
select public.safety_recommend_claim_publication_v1(
  '71717171-7171-4717-8717-717171717171', 'READY_TO_PUBLISH',
  'SOURCE_REVIEWED', 'Second cross-report source review'
) as source_candidate_two \gset
reset role;
set role authenticated;
select set_config('request.jwt.claim.sub', '66666666-6666-4666-8666-666666666666', false);
select set_config('request.jwt.claims', '{"sub":"66666666-6666-4666-8666-666666666666","aal":"aal2"}', false);
select public.safety_finalize_claim_publication_v1(
  :'source_candidate_two'::uuid, 'PUBLISH', 'LEGAL_REVIEW',
  'a1a1a1a1-a1a1-4a1a-8a1a-a1a1a1a1a1a1');
reset role;

set role authenticated;
select set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', false);
select public.safety_withdraw_report_v1(
  '51515151-5151-4515-8515-515151515151',
  'b1b1b1b1-b1b1-4b1b-8b1b-b1b1b1b1b1b1');
reset role;
do $$ begin
  if exists (select 1 from public.safety_public_points
             where claim_id = '71717171-7171-4717-8717-717171717171')
     or not exists (select 1 from safety_private.claim_reevaluation_v3
                    where claim_id = '71717171-7171-4717-8717-717171717171'
                      and reason_code = 'REPORT_WITHDRAWN' and status = 'PENDING') then
    raise exception 'Source report withdrawal left a cross-report public point';
  end if;
end $$;

set role authenticated;
select set_config('request.jwt.claim.sub', '55555555-5555-4555-8555-555555555555', false);
select set_config('request.jwt.claims', '{"sub":"55555555-5555-4555-8555-555555555555","aal":"aal2"}', false);
select public.safety_recommend_claim_publication_v1(
  '71717171-7171-4717-8717-717171717171', 'READY_TO_PUBLISH',
  'SOURCE_WITHDRAWN', 'Review after the supporting report withdrawal'
) as source_candidate_three \gset
select set_config('test.source_candidate_three', :'source_candidate_three', false);
reset role;
set role authenticated;
select set_config('request.jwt.claim.sub', '66666666-6666-4666-8666-666666666666', false);
select set_config('request.jwt.claims', '{"sub":"66666666-6666-4666-8666-666666666666","aal":"aal2"}', false);
do $$ begin
  begin
    perform public.safety_finalize_claim_publication_v1(
      current_setting('test.source_candidate_three')::uuid, 'PUBLISH', 'LEGAL_REVIEW',
      'c1c1c1c1-c1c1-4c1c-8c1c-c1c1c1c1c1c1');
    raise exception 'Withdrawn source report supported a new public point';
  exception when others then
    if sqlerrm <> 'PUBLICATION_REQUIRES_PROVENANCE' then raise; end if;
  end;
end $$;
reset role;
