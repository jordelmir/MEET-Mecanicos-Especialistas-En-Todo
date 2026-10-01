\echo 'CaseRequiresIndependentPublicationAuthority'

-- The preceding claim integration fixture owns the active report and its
-- documented source. Case graph edges are inserted as server-side records.
insert into public.safety_cases(id, title, status)
values ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
        'PRIVATE TITLE WITH IDENTITY', 'UNDER_REVIEW');
insert into public.safety_events(id, report_id, event_type)
values ('11111111-aaaa-4111-8111-111111111111',
        '33333333-3333-4333-8333-333333333333', 'REVIEWED_EVENT');
insert into public.safety_claims(
    id, report_id, predicate, state, methodology_version
) values (
    '22222222-aaaa-4222-8222-222222222222',
    '33333333-3333-4333-8333-333333333333',
    'Private claim', 'DOCUMENTED', 'SAFETY-CLAIM-V3'
);
insert into public.safety_case_events(case_id, event_id)
values ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
        '11111111-aaaa-4111-8111-111111111111');
insert into public.safety_event_claims(event_id, claim_id, relation_type)
values ('11111111-aaaa-4111-8111-111111111111',
        '22222222-aaaa-4222-8222-222222222222', 'SUPPORTS');
insert into public.safety_claim_sources(claim_id, source_id, role)
values ('22222222-aaaa-4222-8222-222222222222',
        '88888888-8888-4888-8888-888888888888', 'SUPPORTING');

-- AAL1 cannot recommend even with the TRUST_REVIEWER grant.
set role authenticated;
select set_config('request.jwt.claim.sub',
                  '55555555-5555-4555-8555-555555555555', false);
select set_config('request.jwt.claims',
                  '{"sub":"55555555-5555-4555-8555-555555555555","aal":"aal1"}', false);
do $$
begin
    begin
        perform public.safety_recommend_case_publication_v1(
            'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'READY_TO_PUBLISH',
            'DOCUMENTED_CASE', '33333333-aaaa-4333-8333-333333333333');
        raise exception 'AAL1 reviewer was accepted';
    exception when others then
        if sqlerrm <> 'SAFETY_REVIEWER_AAL2_REQUIRED' then raise; end if;
    end;
end $$;
select set_config('request.jwt.claims',
                  '{"sub":"55555555-5555-4555-8555-555555555555","aal":"aal2"}', false);
select public.safety_recommend_case_publication_v1(
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'READY_TO_PUBLISH',
    'DOCUMENTED_CASE', '33333333-aaaa-4333-8333-333333333333'
) as case_candidate_id \gset
select set_config('test.case_candidate', :'case_candidate_id', false);
reset role;

do $$
begin
    if exists (select 1 from public.safety_public_case_projection) then
        raise exception 'Case recommendation became public';
    end if;
end $$;

-- The reviewer also holds LEGAL_REVIEWER, so this proves separation of
-- persons rather than failure to obtain a role.
set role authenticated;
select set_config('request.jwt.claim.sub',
                  '55555555-5555-4555-8555-555555555555', false);
select set_config('request.jwt.claims',
                  '{"sub":"55555555-5555-4555-8555-555555555555","aal":"aal2"}', false);
do $$
begin
    begin
        perform public.safety_finalize_case_publication_v1(
            current_setting('test.case_candidate')::uuid, 'PUBLISH',
            'SECOND_REVIEW', '44444444-aaaa-4444-8444-444444444444');
        raise exception 'Reviewer finalized their own case recommendation';
    exception when others then
        if sqlerrm <> 'SEPARATION_OF_DUTIES_REQUIRED' then raise; end if;
    end;
end $$;
reset role;

-- A distinct publisher still needs AAL2 and the operator's case gate.
set role authenticated;
select set_config('request.jwt.claim.sub',
                  '66666666-6666-4666-8666-666666666666', false);
select set_config('request.jwt.claims',
                  '{"sub":"66666666-6666-4666-8666-666666666666","aal":"aal1"}', false);
do $$
begin
    begin
        perform public.safety_finalize_case_publication_v1(
            current_setting('test.case_candidate')::uuid, 'PUBLISH',
            'SECOND_REVIEW', '44444444-aaaa-4444-8444-444444444444');
        raise exception 'AAL1 case publisher was accepted';
    exception when others then
        if sqlerrm <> 'SAFETY_PUBLISHER_AAL2_REQUIRED' then raise; end if;
    end;
end $$;
select set_config('request.jwt.claims',
                  '{"sub":"66666666-6666-4666-8666-666666666666","aal":"aal2"}', false);
do $$
begin
    begin
        perform public.safety_finalize_case_publication_v1(
            current_setting('test.case_candidate')::uuid, 'PUBLISH',
            'SECOND_REVIEW', '44444444-aaaa-4444-8444-444444444444');
        raise exception 'Closed case gate allowed publication';
    exception when others then
        if sqlerrm <> 'SAFETY_PUBLIC_CASES_DISABLED' then raise; end if;
    end;
end $$;
reset role;

update public.runtime_feature_gates set enabled = true
where key = 'safety_public_cases';
set role authenticated;
select set_config('request.jwt.claim.sub',
                  '66666666-6666-4666-8666-666666666666', false);
select set_config('request.jwt.claims',
                  '{"sub":"66666666-6666-4666-8666-666666666666","aal":"aal2"}', false);
select public.safety_finalize_case_publication_v1(
    current_setting('test.case_candidate')::uuid, 'PUBLISH',
    'SECOND_REVIEW', '44444444-aaaa-4444-8444-444444444444'
) as case_final_result \gset
select set_config('test.case_final', :'case_final_result', false);
do $$
declare v_retry jsonb;
begin
    v_retry := public.safety_finalize_case_publication_v1(
        current_setting('test.case_candidate')::uuid, 'PUBLISH',
        'SECOND_REVIEW', '44444444-aaaa-4444-8444-444444444444');
    if v_retry is distinct from current_setting('test.case_final')::jsonb then
        raise exception 'Case publication retry changed receipt';
    end if;
end $$;
reset role;

-- An unrelated citizen sees one generic header and no raw case title,
-- private graph counts, timeline, claims, or accountability details.
set role authenticated;
select set_config('request.jwt.claim.sub',
                  '22222222-2222-2222-2222-222222222222', false);
do $$
declare v_header public.safety_public_case_projection%rowtype;
begin
    select * into v_header from public.safety_public_case_projection
    where case_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
    if not found
       or v_header.title <> 'Caso con revisión independiente'
       or v_header.public_summary <> ''
       or v_header.case_type <> 'SAFETY_CASE'
       or v_header.publication_decision_id is null
       or v_header.confidence_score is not null
       or v_header.event_count is not null
       or v_header.claim_count is not null
       or v_header.source_count is not null
       or v_header.evidence_count is not null
       or exists (select 1 from public.safety_public_case_claim_projection)
       or exists (select 1 from public.safety_public_case_timeline_projection)
       or exists (select 1 from public.safety_public_accountability_projection) then
        raise exception 'Case publication exposed unreviewed details';
    end if;
end $$;
reset role;

update public.runtime_feature_gates set enabled = false
where key = 'safety_public_cases';
set role authenticated;
select set_config('request.jwt.claim.sub',
                  '22222222-2222-2222-2222-222222222222', false);
do $$
begin
    if exists (select 1 from public.safety_public_case_projection) then
        raise exception 'Disabled case gate exposed public header';
    end if;
end $$;
reset role;

update public.runtime_feature_gates set enabled = true where key = 'safety_public_cases';

-- A pending review cannot approve another graph even when the case version
-- remains unchanged; restore each fixture through subtransaction rollback.
do $$
declare v_candidate uuid; v_mutation text;
begin
 foreach v_mutation in array array[
   'update public.safety_claims set predicate = ''changed graph predicate'' where id = ''22222222-aaaa-4222-8222-222222222222''',
   'delete from public.safety_event_claims where event_id = ''11111111-aaaa-4111-8111-111111111111'''
 ] loop
  begin
   perform set_config('request.jwt.claim.sub', '55555555-5555-4555-8555-555555555555', true);
   perform set_config('request.jwt.claims', '{"sub":"55555555-5555-4555-8555-555555555555","aal":"aal2"}', true);
   v_candidate := public.safety_recommend_case_publication_v1(
      'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'READY_TO_PUBLISH', 'GRAPH_REVIEW', gen_random_uuid());
   execute v_mutation;
   if exists (select 1 from public.safety_public_case_projection where case_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa') then
      raise exception 'Changed graph retained published case';
   end if;
   perform set_config('request.jwt.claim.sub', '66666666-6666-4666-8666-666666666666', true);
   perform set_config('request.jwt.claims', '{"sub":"66666666-6666-4666-8666-666666666666","aal":"aal2"}', true);
   begin
      perform public.safety_finalize_case_publication_v1(v_candidate, 'PUBLISH', 'GRAPH_REVIEW', gen_random_uuid());
      raise exception 'Stale graph candidate was accepted';
   exception when invalid_parameter_value then
      if sqlerrm <> 'CASE_VERSION_OR_STATE_CHANGED' then raise; end if;
   end;
   raise exception using errcode = 'P0002', message = 'ROLLBACK_CASE_GRAPH_FIXTURE';
  exception when no_data_found then
   if sqlerrm <> 'ROLLBACK_CASE_GRAPH_FIXTURE' then raise; end if;
  end;
 end loop;
end $$;

update public.runtime_feature_gates set enabled = true where key = 'safety_public_cases';

-- A later case change retires the header and retains its last public version.
update public.safety_cases set state_version = state_version + 1
where id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
do $$
begin
    if exists (select 1 from public.safety_public_case_projection
               where case_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa')
       or not exists (select 1 from safety_private.public_case_history_v3
                      where case_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa') then
        raise exception 'Case update did not invalidate and archive header';
    end if;
end $$;

\echo 'CaseRepublishingUsesMonotonicVersionAndAuditableDecisionHistory'
update public.runtime_feature_gates set enabled = true where key = 'safety_public_cases';
set role authenticated;
select set_config('request.jwt.claim.sub', '55555555-5555-4555-8555-555555555555', false);
select set_config('request.jwt.claims', '{"sub":"55555555-5555-4555-8555-555555555555","aal":"aal2"}', false);
select public.safety_recommend_case_publication_v1(
 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'READY_TO_PUBLISH', 'REVIEWED_AGAIN',
 '88888888-aaaa-4888-8888-888888888888') as second_case_candidate \gset
reset role;
set role authenticated;
select set_config('request.jwt.claim.sub', '66666666-6666-4666-8666-666666666666', false);
select set_config('request.jwt.claims', '{"sub":"66666666-6666-4666-8666-666666666666","aal":"aal2"}', false);
select public.safety_finalize_case_publication_v1(
 :'second_case_candidate'::uuid, 'PUBLISH', 'SECOND_INDEPENDENT_REVIEW',
 '99999999-aaaa-4999-8999-999999999999');
reset role;
do $$ begin
 if (select server_version from public.safety_public_case_projection
     where case_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa') <> 2 then
   raise exception 'Republication reset server version';
 end if;
end $$;
update public.safety_cases set state_version = state_version + 1
where id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
do $$ begin
 if (select count(*) from safety_private.public_case_history_v3
     where case_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
       and publication_decision_id is not null) <> 2 then
   raise exception 'Publication history lost a version or decision reference';
 end if;
end $$;
