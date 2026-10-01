\echo 'Safety real-role RLS and concurrency setup'

insert into auth.users(id) values
  ('11111111-1111-4111-8111-111111111111'),
  ('22222222-2222-4222-8222-222222222222'),
  ('33333333-3333-4333-8333-333333333333'),
  ('44444444-4444-4444-8444-444444444444');
insert into public.platform_authority_grants(user_id, role) values
  ('33333333-3333-4333-8333-333333333333', 'TRUST_REVIEWER'),
  ('44444444-4444-4444-8444-444444444444', 'LEGAL_REVIEWER');

-- Anonymous callers have no report RPC privilege.
set role anon;
do $$
begin
  begin
    perform public.safety_create_report_v2(
      'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
      'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
      'OTHER', 'Anonymous report must be rejected', now(), null, null,
      null, 'UNKNOWN', repeat('a', 64));
    raise exception 'Anon created a report';
  exception when insufficient_privilege then null;
  end;
end $$;
reset role;

-- B owns this report and evidence; A must see neither row.
set role authenticated;
select set_config('request.jwt.claim.sub', '22222222-2222-4222-8222-222222222222', false);
select public.safety_create_report_v2(
  'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
  'dddddddd-dddd-4ddd-8ddd-dddddddddddd',
  'OTHER', 'Private report owned by the second citizen', now(),
  null, null, null, 'DOCUMENTARY', repeat('b', 64));
reset role;
reset request.jwt.claim.sub;
insert into public.safety_evidence_objects(
  id, report_id, uploader_user_id, storage_path, content_sha256,
  mime_type, byte_count
) values (
  'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee',
  'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
  '22222222-2222-4222-8222-222222222222',
  'safety-evidence-original/owner-b/private.bin', repeat('c', 64),
  'application/octet-stream', 16
);

set role authenticated;
select set_config('request.jwt.claim.sub', '11111111-1111-4111-8111-111111111111', false);
do $$
begin
  if exists (select 1 from public.safety_reports
             where id = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc')
     or exists (select 1 from public.safety_evidence_objects
                where id = 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee') then
    raise exception 'User A read User B private report or evidence';
  end if;
end $$;

-- Direct writes are forbidden even when the caller is authenticated.
do $$
begin
  begin
    insert into public.safety_reports(id, reporter_user_id, category)
    values ('ffffffff-ffff-4fff-8fff-ffffffffffff',
            '11111111-1111-4111-8111-111111111111', 'OTHER');
    raise exception 'Authenticated user inserted report directly';
  exception when insufficient_privilege then null;
  end;
  begin
    insert into public.safety_public_points(
      claim_id, publication_decision_id, category,
      display_latitude, display_longitude, geo_disclosure, label,
      claim_state, last_reviewed_at
    ) values (
      'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
      'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
      'OTHER', 10.0, -84.0, 'APPROXIMATE_1000M',
      'unauthorized', 'ALLEGED', now()
    );
    raise exception 'Authenticated user inserted point directly';
  exception when insufficient_privilege then null;
  end;
  begin
    update public.safety_claims set state = 'DOCUMENTED' where false;
    raise exception 'Authenticated user could mutate claims';
  exception when insufficient_privilege then null;
  end;
  begin
    update public.safety_publication_decisions set decision = 'PUBLISH' where false;
    raise exception 'Authenticated user could mutate decisions';
  exception when insufficient_privilege then null;
  end;
end $$;
reset role;

-- Seed one eligible claim for a revoked-reviewer check and the race fixture.
set role authenticated;
select set_config('request.jwt.claim.sub', '11111111-1111-4111-8111-111111111111', false);
select public.safety_create_report_v2(
  '12121212-1212-4212-8212-121212121212',
  '13131313-1313-4313-8313-131313131313',
  'VIOLENT_INCIDENT', 'Private location requiring independent publication',
  now(), 9.93217, -84.08377, 25.0, 'DIRECT_WITNESS', repeat('d', 64));
reset role;
insert into public.safety_claims(id, report_id, predicate, state, methodology_version)
values ('14141414-1414-4414-8414-141414141414',
        '12121212-1212-4212-8212-121212121212',
        'Documented source location', 'DOCUMENTED', 'SAFETY-CLAIM-V3');
insert into public.safety_sources(id, report_id, source_type)
values ('15151515-1515-4515-8515-151515151515',
        '12121212-1212-4212-8212-121212121212', 'DOCUMENTARY');
insert into public.safety_claim_sources(claim_id, source_id, role)
values ('14141414-1414-4414-8414-141414141414',
        '15151515-1515-4515-8515-151515151515', 'SUPPORTING');
update public.runtime_feature_gates set enabled = true
where key = 'safety_public_map';

update public.platform_authority_grants set active = false
where user_id = '33333333-3333-4333-8333-333333333333'
  and role = 'TRUST_REVIEWER';
set role authenticated;
select set_config('request.jwt.claim.sub', '33333333-3333-4333-8333-333333333333', false);
select set_config('request.jwt.claims', '{"sub":"33333333-3333-4333-8333-333333333333","aal":"aal2"}', false);
do $$
begin
  begin
    perform public.safety_recommend_claim_publication_v1(
      '14141414-1414-4414-8414-141414141414', 'READY_TO_PUBLISH',
      'DOCUMENTED_SOURCE', 'A reviewed public summary with no private facts');
    raise exception 'Revoked reviewer recommended publication';
  exception when others then
    if sqlerrm <> 'SAFETY_REVIEWER_AAL2_REQUIRED' then raise; end if;
  end;
end $$;
reset role;
update public.platform_authority_grants set active = true
where user_id = '33333333-3333-4333-8333-333333333333'
  and role = 'TRUST_REVIEWER';

-- Supabase service_role is a trusted actor but cannot write a point directly.
set role service_role;
do $$
begin
  begin
    insert into public.safety_public_points(
      claim_id, publication_decision_id, category,
      display_latitude, display_longitude, geo_disclosure, label,
      claim_state, last_reviewed_at
    ) values (
      '14141414-1414-4414-8414-141414141414',
      'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
      'OTHER', 10.0, -84.0, 'APPROXIMATE_1000M',
      'unauthorized', 'DOCUMENTED', now()
    );
    raise exception 'Service role inserted point directly';
  exception when insufficient_privilege then null;
  end;
end $$;
reset role;

set role authenticated;
select set_config('request.jwt.claim.sub', '33333333-3333-4333-8333-333333333333', false);
select set_config('request.jwt.claims', '{"sub":"33333333-3333-4333-8333-333333333333","aal":"aal2"}', false);
select public.safety_recommend_claim_publication_v1(
  '14141414-1414-4414-8414-141414141414', 'READY_TO_PUBLISH',
  'DOCUMENTED_SOURCE', 'A reviewed public summary with no private facts'
) as candidate_id \gset
reset role;
create table public.test_safety_concurrency_candidate(id uuid primary key);
insert into public.test_safety_concurrency_candidate(id) values (:'candidate_id');
