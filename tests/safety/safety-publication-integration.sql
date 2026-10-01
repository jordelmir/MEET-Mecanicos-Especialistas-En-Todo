\echo 'CitizenReportCannotBecomePublicWithoutIndependentPublicationAuthority'

insert into auth.users(id) values
  ('11111111-1111-1111-1111-111111111111'),
  ('22222222-2222-2222-2222-222222222222'),
  ('55555555-5555-4555-8555-555555555555'),
  ('66666666-6666-4666-8666-666666666666');

insert into public.platform_authority_grants(user_id, role) values
  ('55555555-5555-4555-8555-555555555555', 'TRUST_REVIEWER'),
  ('55555555-5555-4555-8555-555555555555', 'LEGAL_REVIEWER'),
  ('66666666-6666-4666-8666-666666666666', 'LEGAL_REVIEWER');

-- Exercise the production RPC with a citizen JWT, including a real location.
set role authenticated;
select set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', false);
select public.safety_create_report_v2(
  '33333333-3333-4333-8333-333333333333'::uuid,
  '44444444-4444-4444-8444-444444444444'::uuid,
  'VIOLENT_INCIDENT',
  'A citizen allegation requiring independent review.',
  now(), 9.93333, -84.08333, 25.0,
  'DIRECT_WITNESS',
  repeat('a', 64)
);
reset role;
reset request.jwt.claim.sub;

do $$
begin
  if (select count(*) from public.safety_reports where id = '33333333-3333-4333-8333-333333333333') <> 1 then
    raise exception 'CitizenReportCannotBecomePublicWithoutIndependentPublicationAuthority: report RPC did not persist';
  end if;
  if (select count(*) from safety_private.report_content where report_id = '33333333-3333-4333-8333-333333333333') <> 1 then
    raise exception 'CitizenReportCannotBecomePublicWithoutIndependentPublicationAuthority: private content did not persist';
  end if;
  if (select count(*) from public.safety_publication_decisions where report_id = '33333333-3333-4333-8333-333333333333') <> 0 then
    raise exception 'CitizenReportCannotBecomePublicWithoutIndependentPublicationAuthority: fixture unexpectedly has a publication decision';
  end if;
end $$;

-- Another citizen must see no public projection before independent authority.
set role authenticated;
select set_config('request.jwt.claim.sub', '22222222-2222-2222-2222-222222222222', false);
do $$
begin
  if (select count(*) from public.safety_public_points) <> 0 then
    raise exception 'CitizenReportCannotBecomePublicWithoutIndependentPublicationAuthority: citizen report became public without an independent PUBLISH decision';
  end if;
end $$;
reset role;
reset request.jwt.claim.sub;

-- The private linkage and public table must also be empty when viewed as DB owner.
do $$
begin
  if exists (select 1 from safety_private.public_point_report_links where report_id = '33333333-3333-4333-8333-333333333333')
     or exists (select 1 from public.safety_public_points) then
    raise exception 'CitizenReportCannotBecomePublicWithoutIndependentPublicationAuthority: a public point exists without publication authority';
  end if;
end $$;

-- Moderation fixtures are server-side records, never citizen-authored claims.
insert into public.safety_claims(id, report_id, predicate, state, methodology_version)
values ('77777777-7777-4777-8777-777777777777',
        '33333333-3333-4333-8333-333333333333',
        'Location requires independent documentation', 'ALLEGED',
        'SAFETY-CLAIM-V3');

-- A reviewer with verified authority and AAL2 still cannot recommend an
-- allegation that has not reached DOCUMENTED through evidence review.
set role authenticated;
select set_config('request.jwt.claim.sub', '55555555-5555-4555-8555-555555555555', false);
select set_config('request.jwt.claims', '{"sub":"55555555-5555-4555-8555-555555555555","aal":"aal2"}', false);
do $$
begin
  begin
    perform public.safety_recommend_claim_publication_v1(
      '77777777-7777-4777-8777-777777777777', 'READY_TO_PUBLISH',
      'DOCUMENTED_SOURCE', 'A documented public summary with no private details');
    raise exception 'ALLEGED claim was accepted';
  exception when others then
    if sqlerrm <> 'CLAIM_NOT_PUBLICATION_ELIGIBLE' then raise; end if;
  end;
end $$;
reset role;

-- A server-reviewed DOCUMENTED claim has a separate supporting source.
insert into public.safety_sources(id, report_id, source_type, reliability_score, independence_score)
values ('88888888-8888-4888-8888-888888888888',
        '33333333-3333-4333-8333-333333333333', 'DOCUMENTARY', 0.8, 0.9);
insert into public.safety_claim_sources(claim_id, source_id, role)
values ('77777777-7777-4777-8777-777777777777',
        '88888888-8888-4888-8888-888888888888', 'SUPPORTING');
update public.safety_claims set state = 'DOCUMENTED', state_version = state_version + 1
where id = '77777777-7777-4777-8777-777777777777';

-- Authority grant alone is insufficient without the current session AAL2.
set role authenticated;
select set_config('request.jwt.claim.sub', '55555555-5555-4555-8555-555555555555', false);
select set_config('request.jwt.claims', '{"sub":"55555555-5555-4555-8555-555555555555","aal":"aal1"}', false);
do $$
begin
  begin
    perform public.safety_recommend_claim_publication_v1(
      '77777777-7777-4777-8777-777777777777', 'READY_TO_PUBLISH',
      'DOCUMENTED_SOURCE', 'A documented public summary with no private details');
    raise exception 'AAL1 reviewer was accepted';
  exception when others then
    if sqlerrm <> 'SAFETY_REVIEWER_AAL2_REQUIRED' then raise; end if;
  end;
end $$;
select set_config('request.jwt.claims', '{"sub":"55555555-5555-4555-8555-555555555555","aal":"aal2"}', false);
select public.safety_recommend_claim_publication_v1(
  '77777777-7777-4777-8777-777777777777', 'READY_TO_PUBLISH',
  'DOCUMENTED_SOURCE', 'A documented public summary with no private details'
) as candidate_id \gset
select set_config('test.candidate_id', :'candidate_id', false);
reset role;

do $$
begin
  if (select count(*) from public.safety_publication_decisions
      where claim_id = '77777777-7777-4777-8777-777777777777'
        and decision_phase = 'CANDIDATE') <> 1
     or (select count(*) from public.safety_public_points) <> 0 then
    raise exception 'Reviewer recommendation changed public projection before finalization';
  end if;
end $$;

-- The recommending reviewer has both roles, so this specifically proves
-- separation of duties rather than merely a missing publisher grant.
set role authenticated;
select set_config('request.jwt.claim.sub', '55555555-5555-4555-8555-555555555555', false);
select set_config('request.jwt.claims', '{"sub":"55555555-5555-4555-8555-555555555555","aal":"aal2"}', false);
do $$
begin
  begin
    perform public.safety_finalize_claim_publication_v1(
      current_setting('test.candidate_id')::uuid, 'PUBLISH',
      'INDEPENDENT_REVIEW', '99999999-9999-4999-8999-999999999999');
    raise exception 'Same reviewer finalized their own recommendation';
  exception when others then
    if sqlerrm <> 'SEPARATION_OF_DUTIES_REQUIRED' then raise; end if;
  end;
end $$;
reset role;

set role authenticated;
select set_config('request.jwt.claim.sub', '66666666-6666-4666-8666-666666666666', false);
select set_config('request.jwt.claims', '{"sub":"66666666-6666-4666-8666-666666666666","aal":"aal1"}', false);
do $$
begin
  begin
    perform public.safety_finalize_claim_publication_v1(
      current_setting('test.candidate_id')::uuid, 'PUBLISH',
      'INDEPENDENT_REVIEW', '99999999-9999-4999-8999-999999999999');
    raise exception 'AAL1 publisher was accepted';
  exception when others then
    if sqlerrm <> 'SAFETY_PUBLISHER_AAL2_REQUIRED' then raise; end if;
  end;
end $$;
reset role;

-- The operator opens the read gate only after V3 publication authority exists.
update public.runtime_feature_gates set enabled = true
where key = 'safety_public_map';
set role authenticated;
select set_config('request.jwt.claim.sub', '66666666-6666-4666-8666-666666666666', false);
select set_config('request.jwt.claims', '{"sub":"66666666-6666-4666-8666-666666666666","aal":"aal2"}', false);
select public.safety_finalize_claim_publication_v1(
  current_setting('test.candidate_id')::uuid, 'PUBLISH',
  'INDEPENDENT_REVIEW', '99999999-9999-4999-8999-999999999999'
) as final_result \gset
select set_config('test.final_result', :'final_result', false);

-- Exact retries return the same receipt; changing the payload for the same
-- idempotency key must be rejected as a protocol violation.
do $$
declare v_retry jsonb;
begin
  v_retry := public.safety_finalize_claim_publication_v1(
    current_setting('test.candidate_id')::uuid, 'PUBLISH',
    'INDEPENDENT_REVIEW', '99999999-9999-4999-8999-999999999999');
  if v_retry is distinct from current_setting('test.final_result')::jsonb then
    raise exception 'Duplicate publication did not return the original receipt';
  end if;
  begin
    perform public.safety_finalize_claim_publication_v1(
      current_setting('test.candidate_id')::uuid, 'PUBLISH',
      'CHANGED_REASON', '99999999-9999-4999-8999-999999999999');
    raise exception 'Altered payload reused a publication idempotency key';
  exception when others then
    if sqlerrm <> 'IDEMPOTENCY_PROTOCOL_VIOLATION' then raise; end if;
  end;
end $$;
reset role;

-- A different citizen sees exactly one coarse, provenanced projection.
set role authenticated;
select set_config('request.jwt.claim.sub', '22222222-2222-2222-2222-222222222222', false);
select set_config('request.jwt.claims', '{"sub":"22222222-2222-2222-2222-222222222222","aal":"aal1"}', false);
do $$
declare v_point public.safety_public_points%rowtype;
begin
  select * into v_point from public.safety_public_points;
  if not found or (select count(*) from public.safety_public_points) <> 1
     or v_point.claim_id <> '77777777-7777-4777-8777-777777777777'
     or v_point.publication_decision_id is null
     or v_point.independent_source_count <> 1
     or v_point.geo_disclosure <> 'COARSE_GRID_25KM_PLUS'
     or v_point.location_accuracy_meters < 25000
     or v_point.display_latitude = 9.93333
     or v_point.display_longitude = -84.08333
     or v_point.label <> 'Reporte documentado con revisión independiente' then
    raise exception 'Published point is absent, duplicated, exact, or lacks provenance';
  end if;
end $$;
do $$
begin
  begin
    perform 1 from public.safety_publication_decisions limit 1;
    raise exception 'Citizen read reviewer IDs from publication decisions';
  exception when insufficient_privilege then null;
  end;
end $$;
reset role;

-- Even the database owner cannot rewrite decision history after publication.
do $$
begin
  begin
    update public.safety_publication_decisions set reason_code = 'REWRITTEN'
    where id = (current_setting('test.final_result')::jsonb->>'decision_id')::uuid;
    raise exception 'Publication decision history was mutable';
  exception when others then
    if sqlerrm <> 'SAFETY_PUBLICATION_HISTORY_IMMUTABLE' then raise; end if;
  end;
  begin
    delete from public.safety_publication_reviews
    where decision_id = (current_setting('test.final_result')::jsonb->>'decision_id')::uuid;
    raise exception 'Publication review history was mutable';
  exception when others then
    if sqlerrm <> 'SAFETY_PUBLICATION_HISTORY_IMMUTABLE' then raise; end if;
  end;
end $$;

-- A disabled public-map gate must close direct reads and every legacy
-- SECURITY DEFINER observatory entry point, even with a point stored.
update public.runtime_feature_gates set enabled = false
where key = 'safety_public_map';
set role authenticated;
select set_config('request.jwt.claim.sub', '22222222-2222-2222-2222-222222222222', false);
select set_config('request.jwt.claims', '{"sub":"22222222-2222-2222-2222-222222222222","aal":"aal1"}', false);
do $$
declare v_count integer;
begin
  begin
    select count(*) into v_count from public.safety_public_points;
    if v_count <> 0 then raise exception 'Disabled map gate exposed public table rows'; end if;
  exception when insufficient_privilege then null;
  end;
  begin
    v_count := (public.safety_observatory_query_v1()->>'public_point_count')::integer;
    if v_count <> 0 then raise exception 'Disabled map gate exposed V1 observatory count'; end if;
  exception when insufficient_privilege then null;
  end;
  begin
    v_count := (public.safety_observatory_query_v2()->>'public_point_count')::integer;
    if v_count <> 0 then raise exception 'Disabled map gate exposed V2 observatory count'; end if;
  exception when insufficient_privilege then null;
  end;
end $$;
reset role;
update public.runtime_feature_gates set enabled = true
where key = 'safety_public_map';

-- Savepoint rollback proves each authority field revokes the same public fixture.
insert into public.safety_reports(id, reporter_user_id, category)
values ('45454545-4545-4545-8545-454545454545', '11111111-1111-1111-1111-111111111111', 'OTHER');
do $$
declare v_assignment text; v_candidate uuid;
begin
 foreach v_assignment in array array[
   'report_id = ''45454545-4545-4545-8545-454545454545''::uuid',
   'subject_ref = ''changed-subject''', 'predicate = ''changed-predicate''',
   'methodology_version = ''changed-methodology''', 'state_version = state_version + 1',
   'state = ''CORROBORATED'''
 ] loop
   begin
     perform set_config('request.jwt.claim.sub', '55555555-5555-4555-8555-555555555555', true);
     perform set_config('request.jwt.claims', '{"sub":"55555555-5555-4555-8555-555555555555","aal":"aal2"}', true);
     v_candidate := public.safety_recommend_claim_publication_v1(
       '77777777-7777-4777-8777-777777777777', 'READY_TO_PUBLISH', 'MUTATION_REVIEW', 'Authority mutation review fixture');
     execute 'update public.safety_claims set ' || v_assignment ||
       ' where id = ''77777777-7777-4777-8777-777777777777''';
     if exists (select 1 from public.safety_public_points where claim_id = '77777777-7777-4777-8777-777777777777') then
       raise exception 'CLAIM_MUTATION_DID_NOT_REVOKE: %', v_assignment;
     end if;
     if not exists (select 1 from safety_private.claim_reevaluation_v3
       where claim_id = '77777777-7777-4777-8777-777777777777'
         and reason_code = 'CLAIM_AUTHORITY_CHANGED' and status = 'PENDING') then
       raise exception 'CLAIM_MUTATION_DID_NOT_REQUEST_REVIEW: %', v_assignment;
     end if;
     perform set_config('request.jwt.claim.sub', '66666666-6666-4666-8666-666666666666', true);
     perform set_config('request.jwt.claims', '{"sub":"66666666-6666-4666-8666-666666666666","aal":"aal2"}', true);
     begin
       perform public.safety_finalize_claim_publication_v1(v_candidate, 'PUBLISH', 'MUTATION_REVIEW', gen_random_uuid());
       raise exception 'STALE_AUTHORITY_FINGERPRINT_WAS_ACCEPTED';
     exception when serialization_failure then
       if sqlerrm <> 'PUBLICATION_CANDIDATE_STALE' then raise; end if;
     end;
     raise exception using errcode = 'P0002', message = 'ROLLBACK_MUTATION_FIXTURE';
   exception when no_data_found then
     if sqlerrm <> 'ROLLBACK_MUTATION_FIXTURE' then raise; end if;
   end;
 end loop;
end $$;

-- A candidate records claim/report versions. A later version change must
-- invalidate that recommendation before a distinct publisher can finalize it.
set role authenticated;
select set_config('request.jwt.claim.sub', '55555555-5555-4555-8555-555555555555', false);
select set_config('request.jwt.claims', '{"sub":"55555555-5555-4555-8555-555555555555","aal":"aal2"}', false);
select public.safety_recommend_claim_publication_v1(
  '77777777-7777-4777-8777-777777777777', 'READY_TO_PUBLISH',
  'VERSION_REVIEW', 'A second review candidate that will become stale'
) as stale_candidate_id \gset
select set_config('test.stale_candidate_id', :'stale_candidate_id', false);
reset role;
update public.safety_claims set state_version = state_version + 1
where id = '77777777-7777-4777-8777-777777777777';
set role authenticated;
select set_config('request.jwt.claim.sub', '66666666-6666-4666-8666-666666666666', false);
select set_config('request.jwt.claims', '{"sub":"66666666-6666-4666-8666-666666666666","aal":"aal2"}', false);
do $$
begin
  begin
    perform public.safety_finalize_claim_publication_v1(
      current_setting('test.stale_candidate_id')::uuid, 'PUBLISH',
      'VERSION_REVIEW', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
    raise exception 'Stale publication candidate finalized';
  exception when others then
    if sqlerrm <> 'PUBLICATION_CANDIDATE_STALE' then raise; end if;
  end;
end $$;
reset role;
do $$
begin
  if (select count(*) from public.safety_public_points
      where claim_id = '77777777-7777-4777-8777-777777777777') <> 0 then
    raise exception 'Changed claim authority left a stale prior public point';
  end if;
end $$;

-- A disputed claim revokes its point, archives the published snapshot, and
-- opens fresh review instead of leaving the old projection on the map.
update public.safety_claims
set state = 'DISPUTED', state_version = state_version + 1
where id = '77777777-7777-4777-8777-777777777777';
do $$
declare v_point_id uuid := (current_setting('test.final_result')::jsonb->>'public_point_id')::uuid;
begin
  if exists (select 1 from public.safety_public_points
             where claim_id = '77777777-7777-4777-8777-777777777777') then
    raise exception 'Disputed claim remained public';
  end if;
  if not exists (select 1 from safety_private.public_point_history_v3
                 where public_point_id = v_point_id
                   and snapshot->>'claim_id' = '77777777-7777-4777-8777-777777777777') then
    raise exception 'Disputed public point was not archived';
  end if;
  if not exists (select 1 from safety_private.claim_reevaluation_v3
                 where claim_id = '77777777-7777-4777-8777-777777777777'
                   and reason_code = 'CLAIM_AUTHORITY_CHANGED' and status = 'PENDING') then
    raise exception 'Disputed claim did not request reevaluation';
  end if;
end $$;

-- A separate fully published report exercises owner withdrawal after a real
-- independent recommendation and finalization.
set role authenticated;
select set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', false);
select set_config('request.jwt.claims', '{"sub":"11111111-1111-1111-1111-111111111111","aal":"aal1"}', false);
select public.safety_create_report_v2(
  'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
  'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
  'VIOLENT_INCIDENT', 'Second private citizen narrative before withdrawal.',
  now(), 10.12345, -84.12345, 30.0, 'DIRECT_WITNESS', repeat('b', 64));
reset role;
insert into public.safety_claims(id, report_id, predicate, state, methodology_version)
values ('dddddddd-dddd-4ddd-8ddd-dddddddddddd',
        'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
        'A second independently documented location', 'DOCUMENTED',
        'SAFETY-CLAIM-V3');
insert into public.safety_sources(id, report_id, source_type)
values ('eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee',
        'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'DOCUMENTARY');
insert into public.safety_claim_sources(claim_id, source_id, role)
values ('dddddddd-dddd-4ddd-8ddd-dddddddddddd',
        'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee', 'SUPPORTING');
set role authenticated;
select set_config('request.jwt.claim.sub', '55555555-5555-4555-8555-555555555555', false);
select set_config('request.jwt.claims', '{"sub":"55555555-5555-4555-8555-555555555555","aal":"aal2"}', false);
select public.safety_recommend_claim_publication_v1(
  'dddddddd-dddd-4ddd-8ddd-dddddddddddd', 'READY_TO_PUBLISH',
  'DOCUMENTED_SOURCE', 'Second public summary approved for review'
) as withdrawal_candidate_id \gset
select set_config('test.withdrawal_candidate_id', :'withdrawal_candidate_id', false);
reset role;
set role authenticated;
select set_config('request.jwt.claim.sub', '66666666-6666-4666-8666-666666666666', false);
select set_config('request.jwt.claims', '{"sub":"66666666-6666-4666-8666-666666666666","aal":"aal2"}', false);
select public.safety_finalize_claim_publication_v1(
  current_setting('test.withdrawal_candidate_id')::uuid, 'PUBLISH',
  'INDEPENDENT_REVIEW', 'ffffffff-ffff-4fff-8fff-ffffffffffff'
) as withdrawal_final_result \gset
select set_config('test.withdrawal_final_result', :'withdrawal_final_result', false);
reset role;
do $$
begin
  if (select count(*) from public.safety_public_points
      where claim_id = 'dddddddd-dddd-4ddd-8ddd-dddddddddddd') <> 1 then
    raise exception 'Withdrawal fixture was not published before withdrawal';
  end if;
end $$;
set role authenticated;
select set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', false);
select set_config('request.jwt.claims', '{"sub":"11111111-1111-1111-1111-111111111111","aal":"aal1"}', false);
select public.safety_withdraw_report_v1(
  'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
  '12121212-1212-4212-8212-121212121212'
) as withdrawal_result \gset
select set_config('test.withdrawal_result', :'withdrawal_result', false);
do $$
declare v_retry jsonb;
begin
  v_retry := public.safety_withdraw_report_v1(
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
    '12121212-1212-4212-8212-121212121212');
  if v_retry is distinct from current_setting('test.withdrawal_result')::jsonb then
    raise exception 'Withdrawal retry changed the receipt';
  end if;
  v_retry := public.safety_withdraw_report_v1(
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
    '13131313-1313-4313-8313-131313131313');
  if v_retry->>'server_version' <> '2' then
    raise exception 'Repeat withdrawal advanced the server version';
  end if;
end $$;
reset role;
do $$
declare v_point_id uuid :=
    (current_setting('test.withdrawal_final_result')::jsonb->>'public_point_id')::uuid;
begin
  if (select state from public.safety_reports
      where id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb') <> 'WITHDRAWN'
     or (select state_version from public.safety_reports
         where id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb') <> 2
     or exists (select 1 from public.safety_public_points
                where claim_id = 'dddddddd-dddd-4ddd-8ddd-dddddddddddd') then
    raise exception 'Withdrawal did not remove the published point and freeze version';
  end if;
  if not exists (select 1 from safety_private.report_content
                 where report_id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'
                   and narrative = '' and source_relation = 'UNKNOWN'
                   and latitude is null and longitude is null) then
    raise exception 'Withdrawal did not redact private narrative and location';
  end if;
  if not exists (select 1 from safety_private.report_withdrawal_events_v3
                 where report_id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'
                   and actor_id = '11111111-1111-1111-1111-111111111111'
                   and state_version = 2 and original_payload_sha256 ~ '^[a-f0-9]{64}$') then
    raise exception 'Withdrawal evidence event was not preserved';
  end if;
  if not exists (select 1 from safety_private.public_point_history_v3
                 where public_point_id = v_point_id)
     or not exists (select 1 from safety_private.claim_reevaluation_v3
                    where claim_id = 'dddddddd-dddd-4ddd-8ddd-dddddddddddd'
                      and reason_code = 'REPORT_WITHDRAWN' and status = 'PENDING') then
    raise exception 'Withdrawal did not archive point and request reevaluation';
  end if;
end $$;
