\echo 'New source attachment atomically revokes publication until a new independent decision'
begin;
insert into auth.users(id) values
 ('f1000000-0000-4000-8000-000000000001'),
 ('f1000000-0000-4000-8000-000000000002'),
 ('f1000000-0000-4000-8000-000000000003');
insert into public.platform_authority_grants(user_id,role) values
 ('f1000000-0000-4000-8000-000000000002','TRUST_REVIEWER'),
 ('f1000000-0000-4000-8000-000000000002','LEGAL_REVIEWER'),
 ('f1000000-0000-4000-8000-000000000003','LEGAL_REVIEWER');
insert into public.runtime_feature_gates(key,enabled)
values('safety_public_map',true),('safety_public_cases',true)
on conflict(key) do update set enabled=true;
insert into public.safety_reports(id,reporter_user_id,category) values
 ('f2000000-0000-4000-8000-000000000001','f1000000-0000-4000-8000-000000000001','OTHER'),
 ('f2000000-0000-4000-8000-000000000002','f1000000-0000-4000-8000-000000000001','OTHER');
insert into safety_private.report_content(report_id,narrative,source_relation,latitude,longitude,accuracy_meters,server_payload_sha256)
values('f2000000-0000-4000-8000-000000000001','Synthetic test narrative','DIRECT_WITNESS',9.93333,-84.08333,25,repeat('f',64));
insert into public.safety_claims(id,report_id,predicate,state,methodology_version)
values('f3000000-0000-4000-8000-000000000001','f2000000-0000-4000-8000-000000000001','Synthetic documented event','DOCUMENTED','SAFETY-CLAIM-V3');
insert into public.safety_sources(id,report_id,source_type,reliability_score,independence_score)
values('f4000000-0000-4000-8000-000000000001','f2000000-0000-4000-8000-000000000002','DOCUMENTARY',0.8,0.9);
insert into public.safety_claim_sources(claim_id,source_id,role)
values('f3000000-0000-4000-8000-000000000001','f4000000-0000-4000-8000-000000000001','SUPPORTING');
insert into public.safety_cases(id,title,status)
values('f5000000-0000-4000-8000-000000000001','Private synthetic case','UNDER_REVIEW');
insert into public.safety_events(id,report_id,event_type)
values('f6000000-0000-4000-8000-000000000001','f2000000-0000-4000-8000-000000000001','REVIEWED_EVENT');
insert into public.safety_case_events(case_id,event_id)
values('f5000000-0000-4000-8000-000000000001','f6000000-0000-4000-8000-000000000001');
insert into public.safety_event_claims(event_id,claim_id,relation_type)
values('f6000000-0000-4000-8000-000000000001','f3000000-0000-4000-8000-000000000001','SUPPORTS');

set role authenticated;
select set_config('request.jwt.claim.sub','f1000000-0000-4000-8000-000000000002',true);
select set_config('request.jwt.claims','{"sub":"f1000000-0000-4000-8000-000000000002","aal":"aal2"}',true);
select public.safety_recommend_claim_publication_v1(
 'f3000000-0000-4000-8000-000000000001','READY_TO_PUBLISH','SOURCE_REVIEWED','Independently reviewed synthetic source') as claim_candidate \gset
select public.safety_recommend_case_publication_v1(
 'f5000000-0000-4000-8000-000000000001','READY_TO_PUBLISH','SOURCE_REVIEWED','f7000000-0000-4000-8000-000000000001') as case_candidate \gset
select set_config('request.jwt.claim.sub','f1000000-0000-4000-8000-000000000003',true);
select set_config('request.jwt.claims','{"sub":"f1000000-0000-4000-8000-000000000003","aal":"aal2"}',true);
select public.safety_finalize_claim_publication_v1(:'claim_candidate'::uuid,'PUBLISH','SECOND_REVIEW','f7000000-0000-4000-8000-000000000002');
select public.safety_finalize_case_publication_v1(:'case_candidate'::uuid,'PUBLISH','SECOND_REVIEW','f7000000-0000-4000-8000-000000000003');
reset role;
do $$ begin
 if not exists(select 1 from public.safety_public_points where claim_id='f3000000-0000-4000-8000-000000000001')
 or not exists(select 1 from public.safety_public_case_projection where case_id='f5000000-0000-4000-8000-000000000001') then
   raise exception 'Independent publication fixture did not publish';
 end if;
end $$;

-- The attachment is on another report used as a source, not the claim/event's report.
insert into public.safety_evidence_objects(id,report_id,uploader_user_id,storage_path,content_sha256,mime_type,byte_count)
values('f8000000-0000-4000-8000-000000000001','f2000000-0000-4000-8000-000000000002',
 'f1000000-0000-4000-8000-000000000001','synthetic-source-attachment',repeat('e',64),'application/pdf',10);
do $$ begin
 if exists(select 1 from public.safety_public_points where claim_id='f3000000-0000-4000-8000-000000000001')
 or exists(select 1 from public.safety_public_case_projection where case_id='f5000000-0000-4000-8000-000000000001') then
   raise exception 'Pending cross-report source evidence left a published projection';
 end if;
 if not exists(select 1 from safety_private.claim_reevaluation_v3
    where claim_id='f3000000-0000-4000-8000-000000000001' and reason_code='EVIDENCE_VERIFICATION_PENDING' and status='PENDING') then
   raise exception 'Pending source evidence did not request claim reevaluation';
 end if;
 if not exists(select 1 from safety_private.public_case_history_v3
    where case_id='f5000000-0000-4000-8000-000000000001' and publication_decision_id is not null) then
   raise exception 'Evidence invalidation lost immutable case publication history';
 end if;
end $$;
set role service_role;
select public.safety_record_evidence_verification_v2('f8000000-0000-4000-8000-000000000001','MATCH',repeat('e',64),10,'INDEPENDENT_TEST');
reset role;
do $$ begin
 if exists(select 1 from public.safety_public_points where claim_id='f3000000-0000-4000-8000-000000000001')
 or exists(select 1 from public.safety_public_case_projection where case_id='f5000000-0000-4000-8000-000000000001') then
   raise exception 'Server byte MATCH automatically republished a revoked projection';
 end if;
end $$;
rollback;
\echo 'Evidence source-publication invalidation: PASS'
