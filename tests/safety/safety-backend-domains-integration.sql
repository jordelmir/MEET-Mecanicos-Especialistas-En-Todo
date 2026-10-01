-- Adversarial domain proof using real PostgreSQL functions. All data is synthetic.
begin;
insert into auth.users(id) values
 ('a1000000-0000-4000-8000-000000000001'),('a1000000-0000-4000-8000-000000000002'),('a1000000-0000-4000-8000-000000000003');
insert into public.platform_authority_grants(user_id,role) values
 ('a1000000-0000-4000-8000-000000000002','TRUST_REVIEWER'),('a1000000-0000-4000-8000-000000000002','LEGAL_REVIEWER'),('a1000000-0000-4000-8000-000000000003','LEGAL_REVIEWER');
insert into public.runtime_feature_gates(key,enabled) values('safety_reporting',true),('safety_observatory',true),('safety_public_cases',true),('safety_accountability',true) on conflict(key) do update set enabled=excluded.enabled;
update safety_private.intake_policy set max_submissions=1;
select set_config('request.jwt.claim.sub','a1000000-0000-4000-8000-000000000001',true);
select set_config('request.jwt.claims','{"aal":"aal1"}',true);
set local role authenticated;
select public.safety_create_report_v3('a2000000-0000-4000-8000-000000000001','a3000000-0000-4000-8000-000000000001','DRUG_SALE_ACTIVITY','Synthetic private intake narrative.','2026-01-01Z',null,null,null,'DOCUMENTARY',repeat('a',64),'NONE',null,null,null);
-- At quota, replay succeeds and returns exactly the reserved result.
select public.safety_create_report_v3('a2000000-0000-4000-8000-000000000001','a3000000-0000-4000-8000-000000000001','DRUG_SALE_ACTIVITY','Synthetic private intake narrative.','2026-01-01Z',null,null,null,'DOCUMENTARY',repeat('a',64),'NONE',null,null,null);
do $$ begin
 begin
 perform public.safety_create_report_v3('a2000000-0000-4000-8000-000000000004','a3000000-0000-4000-8000-000000000004','DRUG_SALE_ACTIVITY','Synthetic different narrative.','2026-01-01Z',null,null,null,'DOCUMENTARY',repeat('a',64),'NONE',null,null,null);
 raise exception 'Rate limit failed'; exception when others then if sqlerrm<>'SAFETY_RATE_LIMIT_EXCEEDED' then raise; end if; end;
 if has_function_privilege('authenticated','public.safety_record_device_verdict_v1(uuid,uuid,text,text,boolean,text,text)','EXECUTE') then raise exception 'Client can assert device trust'; end if;
 if has_function_privilege('authenticated','public.safety_institutional_gateway_v1(text,text,text[],text,jsonb)','EXECUTE') then raise exception 'Client can impersonate institutional principal'; end if;
end $$;
reset role;
do $$ declare c jsonb; begin
 if (select sum(submission_count) from safety_private.intake_rate_windows where actor_id='a1000000-0000-4000-8000-000000000001')<>1 then raise exception 'Idempotent replay charged quota'; end if;
 c:=public.safety_issue_device_challenge_v1();
 begin perform public.safety_record_device_verdict_v1((c->>'challenge_id')::uuid,'a1000000-0000-4000-8000-000000000002',c->>'request_hash','com.elysium369.meet',true,repeat('a',64),'TEST_VERDICT');raise exception 'Actor binding failed'; exception when others then if sqlerrm<>'DEVICE_CHALLENGE_BINDING_MISMATCH' then raise; end if; end;
 perform public.safety_record_device_verdict_v1((c->>'challenge_id')::uuid,'a1000000-0000-4000-8000-000000000001',c->>'request_hash','com.elysium369.meet',false,repeat('b',64),'TEST_REJECTED');
 begin perform public.safety_record_device_verdict_v1((c->>'challenge_id')::uuid,'a1000000-0000-4000-8000-000000000001',c->>'request_hash','com.elysium369.meet',true,repeat('a',64),'TEST_VERDICT');raise exception 'Consumed challenge accepted'; exception when others then if sqlerrm<>'DEVICE_CHALLENGE_ALREADY_CONSUMED' then raise; end if; end;
end $$;

-- RepretelHistoricalCaseV1: 100 records, same batch receipts, immutable source17 revision.
insert into public.safety_external_providers(id,provider_code,display_name,provider_type,active) values('a4000000-0000-4000-8000-000000000001','REPRETEL_TEST','Synthetic Repretel fixture','JOURNALISTIC',true);
do $$ declare records jsonb; first_receipt jsonb; replay jsonb; changed jsonb; begin
 select jsonb_agg(jsonb_build_object('external_record_id','source-'||i,'canonical_url','https://example.invalid/news/'||i,'title','Synthetic source '||i,'source_published_at','2026-01-01T00:00:00Z','content_sha256',encode(extensions.digest(i::text,'sha256'),'hex')) order by i) into records from generate_series(1,100)i;
 first_receipt:=public.safety_import_source_batch_v1('a4000000-0000-4000-8000-000000000001','a5000000-0000-4000-8000-000000000001',records);
 replay:=public.safety_import_source_batch_v1('a4000000-0000-4000-8000-000000000001','a5000000-0000-4000-8000-000000000001',records);
 if (first_receipt->>'new_records')::int<>100 or (replay->>'new_records')::int<>0 or first_receipt->'receipts'<>replay->'receipts' then raise exception 'RepretelHistoricalCaseV1 replay failed'; end if;
 changed:=jsonb_build_array(jsonb_set(records->16,'{content_sha256}',to_jsonb(repeat('e',64))));
 perform public.safety_import_source_batch_v1('a4000000-0000-4000-8000-000000000001','a5000000-0000-4000-8000-000000000002',changed);
 if (select count(*) from safety_private.external_source_revisions where external_record_id='source-17')<>2 then raise exception 'RepretelHistoricalCaseV1 no immutable revision'; end if;
 if exists(select 1 from public.safety_public_case_projection) then raise exception 'Import silently created public cases'; end if;
 begin update safety_private.external_source_revisions set title='Silent overwrite';raise exception 'Source mutable'; exception when others then if sqlerrm<>'SAFETY_PUBLICATION_HISTORY_IMMUTABLE' then raise; end if; end;
end $$;

-- No raw citizen report contributes to Observatory or counternarcotics output.
select set_config('request.jwt.claim.sub','a1000000-0000-4000-8000-000000000001',true);
set local role authenticated;
do $$ declare result jsonb; begin
 result:=public.safety_observatory_query_v3('2026-01-05Z','2026-02-02Z');
 if result->'cells'<>'[]'::jsonb then raise exception 'Raw report leaked into Observatory'; end if;
 result:=public.safety_counternarcotics_patterns_v1('2026-01-05Z','2026-02-02Z');
 if result->'patterns'<>'[]'::jsonb then raise exception 'Raw report treated as narcotics fact'; end if;
 begin perform public.safety_observatory_query_v3('2026-01-06Z','2026-02-02Z');raise exception 'Fine-grained slice permitted'; exception when others then if sqlerrm<>'INVALID_OR_UNSAFE_OBSERVATORY_RANGE' then raise; end if; end;
end $$;
reset role;
-- Establish evidence/case binding without public publication.
insert into public.safety_claims(id,report_id,predicate,state,methodology_version) values('a6000000-0000-4000-8000-000000000001','a2000000-0000-4000-8000-000000000001','Synthetic documented proposition','DOCUMENTED','SYNTHETIC-V1');
insert into public.safety_cases(id,title,status) values('a7000000-0000-4000-8000-000000000001','Synthetic case','UNDER_REVIEW');
insert into public.safety_events(id,report_id,event_type) values('a8000000-0000-4000-8000-000000000001','a2000000-0000-4000-8000-000000000001','SYNTHETIC');
insert into public.safety_event_claims(event_id,claim_id) values('a8000000-0000-4000-8000-000000000001','a6000000-0000-4000-8000-000000000001');
insert into public.safety_case_events(case_id,event_id) values('a7000000-0000-4000-8000-000000000001','a8000000-0000-4000-8000-000000000001');
insert into public.safety_evidence_objects(id,report_id,uploader_user_id,storage_path,content_sha256,mime_type,byte_count) values('a9000000-0000-4000-8000-000000000001','a2000000-0000-4000-8000-000000000001','a1000000-0000-4000-8000-000000000001','synthetic/evidence',repeat('a',64),'text/plain',20);
select set_config('request.jwt.claim.sub','a1000000-0000-4000-8000-000000000002',true);
select set_config('request.jwt.claims','{"aal":"aal2"}',true);
set local role authenticated;
do $$ begin
 begin perform public.safety_record_institutional_event_v3('b1000000-0000-4000-8000-000000000001','a7000000-0000-4000-8000-000000000001','TEST_INSTITUTION','DELIVERY_CONFIRMED','2026-01-01Z','a9000000-0000-4000-8000-000000000001',repeat('c',64));raise exception 'Unverified receipt promoted'; exception when others then if sqlerrm<>'SERVER_VERIFIED_EVIDENCE_REQUIRED' then raise; end if; end;
end $$;
reset role;
select public.safety_record_evidence_verification_v2('a9000000-0000-4000-8000-000000000001','MATCH',repeat('a',64),20,'SYNTHETIC-WORKER');
select public.safety_record_institutional_event_v3('b1000000-0000-4000-8000-000000000001','a7000000-0000-4000-8000-000000000001','TEST_INSTITUTION','DELIVERY_CONFIRMED','2026-01-01Z','a9000000-0000-4000-8000-000000000001',repeat('c',64));
do $$ declare review uuid; begin
 review:=public.safety_review_institutional_event_v3('b1000000-0000-4000-8000-000000000001','DOCUMENTED_DELIVERY');
 begin perform public.safety_finalize_institutional_event_v3(review,'PUBLISH','DOCUMENTED_DELIVERY');raise exception 'Self-publication allowed'; exception when others then if sqlerrm<>'TWO_PERSON_PUBLICATION_REQUIRED' then raise; end if; end;
 perform set_config('request.jwt.claim.sub','a1000000-0000-4000-8000-000000000003',true);
 begin perform public.safety_finalize_institutional_event_v3(review,'PUBLISH','DOCUMENTED_DELIVERY');raise exception 'Nonpublic case published'; exception when others then if sqlerrm<>'STALE_OR_UNVERIFIED_PUBLICATION' then raise; end if; end;
end $$;
-- A second report is the provenance source, distinct from the claim/event report.
select set_config('request.jwt.claim.sub','a1000000-0000-4000-8000-000000000002',true);
select public.safety_create_report_v3('e2000000-0000-4000-8000-000000000001','e3000000-0000-4000-8000-000000000001','OTHER','Synthetic independently held source document.','2026-01-01Z',null,null,null,'DOCUMENTARY',repeat('a',64),'NONE',null,null,null);
insert into public.safety_evidence_objects(id,report_id,uploader_user_id,storage_path,content_sha256,mime_type,byte_count) values('e9000000-0000-4000-8000-000000000001','e2000000-0000-4000-8000-000000000001','a1000000-0000-4000-8000-000000000002','synthetic/source-evidence',repeat('a',64),'text/plain',20);
select public.safety_record_evidence_verification_v2('e9000000-0000-4000-8000-000000000001','MATCH',repeat('a',64),20,'SYNTHETIC-WORKER');
-- Scope is the intersection of a verified OAuth token and server provisioning;
-- machine source intake never publishes, and restricted reads require purpose grants.
insert into safety_private.institutional_clients(id,issuer,subject,institution_ref,scopes,provider_id,active,expires_at)
values('f1000000-0000-4000-8000-000000000001','https://identity.example.invalid','SYNTHETIC_MACHINE','TEST_INSTITUTION',array['safety:read_public','safety:submit_source','safety:read_restricted'],'a4000000-0000-4000-8000-000000000001',true,now()+interval '1 hour');
insert into safety_private.institutional_resource_grants(client_id,report_id,purpose_code,expires_at)
values('f1000000-0000-4000-8000-000000000001','a2000000-0000-4000-8000-000000000001','SYNTHETIC_CASE_REVIEW',now()+interval '1 hour');
set local role service_role;
do $$ declare receipt jsonb; begin
 receipt:=public.safety_institutional_gateway_v1('https://identity.example.invalid','SYNTHETIC_MACHINE',array['safety:read_restricted'],'read_restricted',jsonb_build_object('report_id','a2000000-0000-4000-8000-000000000001','purpose_code','SYNTHETIC_CASE_REVIEW'));
 if jsonb_array_length(receipt->'evidence_receipts')<>1 or receipt::text like '%storage_path%' or receipt::text like '%narrative%' or receipt::text like '%reporter_user_id%' then raise exception 'Restricted gateway exposed excessive data'; end if;
 begin perform public.safety_institutional_gateway_v1('https://identity.example.invalid','SYNTHETIC_MACHINE',array['safety:read_public'],'read_restricted',jsonb_build_object('report_id','a2000000-0000-4000-8000-000000000001','purpose_code','SYNTHETIC_CASE_REVIEW'));raise exception 'OAuth scope bypass';exception when others then if sqlerrm<>'INSTITUTIONAL_SCOPE_DENIED' then raise;end if;end;
 begin perform public.safety_institutional_gateway_v1('https://identity.example.invalid','SYNTHETIC_MACHINE',array['safety:read_restricted'],'read_restricted',jsonb_build_object('report_id','a2000000-0000-4000-8000-000000000001','purpose_code','UNAUTHORIZED_PURPOSE'));raise exception 'Purpose scope bypass';exception when others then if sqlerrm<>'INSTITUTIONAL_RESOURCE_DENIED' then raise;end if;end;
 receipt:=public.safety_institutional_gateway_v1('https://identity.example.invalid','SYNTHETIC_MACHINE',array['safety:submit_source'],'submit_source',jsonb_build_object('batch_id','f2000000-0000-4000-8000-000000000001','records',jsonb_build_array(jsonb_build_object('external_record_id','machine-source','canonical_url','https://example.invalid/machine-source','title','Synthetic machine source','source_published_at','2026-01-01Z','content_sha256',repeat('a',64)))));
 if receipt->>'accepted_count' is distinct from '0' then raise exception 'Machine source silently accepted'; end if;
end $$;
reset role;
do $$ begin if (select count(*) from safety_private.institutional_access_audit where client_id='f1000000-0000-4000-8000-000000000001')<>2 then raise exception 'Successful sensitive institutional operations not audited'; end if; end $$;
-- Real publication fixtures exercise small-cell suppression and the seven-day
-- publication delay. Owner backdating models already-aged authoritative rows.
update safety_private.report_content set latitude=9.93333,longitude=-84.08333,accuracy_meters=25,location_source='DEVICE' where report_id='a2000000-0000-4000-8000-000000000001';
insert into public.runtime_feature_gates(key,enabled) values('safety_public_map',true) on conflict(key) do update set enabled=true;
insert into public.safety_source_clusters(id,methodology_version) values('c3000000-0000-4000-8000-000000000001','SYNTHETIC');
insert into public.safety_sources(id,report_id,source_type,cluster_id,reliability_score,independence_score) values('c4000000-0000-4000-8000-000000000001','e2000000-0000-4000-8000-000000000001','JOURNALISTIC','c3000000-0000-4000-8000-000000000001',1,1);
do $$ declare v_claim_id uuid; candidate uuid;result jsonb;i int; begin
 for i in 1..5 loop
 v_claim_id:=gen_random_uuid();
 insert into public.safety_claims(id,report_id,predicate,state,methodology_version) values(v_claim_id,'a2000000-0000-4000-8000-000000000001','Synthetic source pattern '||i,'DOCUMENTED','SYNTHETIC');
 insert into public.safety_claim_sources(claim_id,source_id) values(v_claim_id,'c4000000-0000-4000-8000-000000000001');
 perform set_config('request.jwt.claim.sub','a1000000-0000-4000-8000-000000000002',true);
 candidate:=public.safety_recommend_claim_publication_v1(v_claim_id,'READY_TO_PUBLISH','SYNTHETIC_DOCUMENTED','Synthetic generic public summary');
 perform set_config('request.jwt.claim.sub','a1000000-0000-4000-8000-000000000003',true);
 perform public.safety_finalize_claim_publication_v1(candidate,'PUBLISH','SECOND_REVIEW',gen_random_uuid());
 -- Fresh publication must remain invisible to the delayed Observatory.
 result:=public.safety_observatory_query_v3('2025-12-29Z','2026-02-02Z');
 if i=1 and result->'cells'<>'[]'::jsonb then raise exception 'Delay was bypassed'; end if;
 update public.safety_public_points set published_at='2026-01-01Z' where safety_public_points.claim_id=v_claim_id;
 result:=public.safety_observatory_query_v3('2025-12-29Z','2026-02-02Z');
 if i<5 and result->'cells'<>'[]'::jsonb then raise exception 'Small cell exposed'; end if;
 if i=5 and (result->'cells'->0->>'documented_claim_count')::int is distinct from 5 then raise exception 'Safe cell not released'; end if;
 end loop;
 result:=public.safety_counternarcotics_patterns_v1('2025-12-29Z','2026-02-02Z');
 if (result->'patterns'->0->>'documented_claim_count')::int is distinct from 5 or result->'patterns'->0->>'truth_state' is distinct from 'DOCUMENTED_PATTERN' then raise exception 'Documented pattern projection incorrect'; end if;
end $$;
-- Publish an eligible case through the existing V3 authority, then publish its
-- independently reviewed institutional event through the separate V3 ledger.
insert into public.safety_source_clusters(id,methodology_version) values('b3000000-0000-4000-8000-000000000001','SYNTHETIC');
insert into public.safety_sources(id,report_id,source_type,cluster_id,reliability_score,independence_score) values('b4000000-0000-4000-8000-000000000001','e2000000-0000-4000-8000-000000000001','JOURNALISTIC','b3000000-0000-4000-8000-000000000001',1,1);
insert into public.safety_claim_sources(claim_id,source_id) values('a6000000-0000-4000-8000-000000000001','b4000000-0000-4000-8000-000000000001');
do $$ declare candidate uuid; review uuid; receipt jsonb; begin
 perform set_config('request.jwt.claim.sub','a1000000-0000-4000-8000-000000000002',true);
 candidate:=public.safety_recommend_case_publication_v1('a7000000-0000-4000-8000-000000000001','READY_TO_PUBLISH','DOCUMENTED_SOURCE','b5000000-0000-4000-8000-000000000001');
 perform set_config('request.jwt.claim.sub','a1000000-0000-4000-8000-000000000003',true);
 perform public.safety_finalize_case_publication_v1(candidate,'PUBLISH','SECOND_REVIEW','b5000000-0000-4000-8000-000000000002');
 perform set_config('request.jwt.claim.sub','a1000000-0000-4000-8000-000000000002',true);
 review:=public.safety_review_institutional_event_v3('b1000000-0000-4000-8000-000000000001','DOCUMENTED_DELIVERY');
 perform set_config('request.jwt.claim.sub','a1000000-0000-4000-8000-000000000003',true);
 receipt:=public.safety_finalize_institutional_event_v3(review,'PUBLISH','SECOND_REVIEW');
 if not exists(select 1 from public.safety_public_accountability_projection where event_id='b1000000-0000-4000-8000-000000000001') then raise exception 'Valid institutional publication absent'; end if;
 if receipt is distinct from public.safety_finalize_institutional_event_v3(review,'PUBLISH','SECOND_REVIEW') then raise exception 'Institutional replay changed'; end if;
 perform public.safety_record_evidence_verification_v2('e9000000-0000-4000-8000-000000000001','MISMATCH',repeat('b',64),20,'SYNTHETIC-WORKER');
 if exists(select 1 from public.safety_public_points) or exists(select 1 from public.safety_public_case_projection) or exists(select 1 from public.safety_public_accountability_projection) then raise exception 'Supporting-source evidence mismatch remained public'; end if;
 perform public.safety_record_evidence_verification_v2('a9000000-0000-4000-8000-000000000001','QUARANTINED',null,null,'SYNTHETIC-WORKER');
 if exists(select 1 from public.safety_public_accountability_projection) then raise exception 'Quarantined institutional evidence remained public'; end if;
end $$;
-- Legal hold blocks authorized content retention, including indirect CASE hold.
update public.safety_reports set created_at='2025-01-01Z' where id in('a2000000-0000-4000-8000-000000000001','e2000000-0000-4000-8000-000000000001');
select set_config('request.jwt.claim.sub','a1000000-0000-4000-8000-000000000002',true);
do $$ declare hold uuid; begin
 perform public.safety_classify_retention_v1('a2000000-0000-4000-8000-000000000001','ERASABLE','SYNTHETIC_POLICY');
 hold:=public.safety_place_legal_hold_v1('CASE','a7000000-0000-4000-8000-000000000001','SYNTHETIC_HOLD',repeat('c',64));
 perform public.safety_classify_retention_v1('e2000000-0000-4000-8000-000000000001','ERASABLE','SYNTHETIC_POLICY');
 begin perform public.safety_apply_retention_v1('e1000000-0000-4000-8000-000000000001','e2000000-0000-4000-8000-000000000001','ERASE_CONTENT','SYNTHETIC_RETENTION');raise exception 'Held supporting source erased'; exception when others then if sqlerrm<>'ACTIVE_LEGAL_HOLD' then raise; end if; end;
 perform set_config('request.jwt.claim.sub','a1000000-0000-4000-8000-000000000002',true);
 perform public.safety_withdraw_report_v1('e2000000-0000-4000-8000-000000000001','e1000000-0000-4000-8000-000000000002');
 if (select narrative from safety_private.report_content where report_id='e2000000-0000-4000-8000-000000000001') is distinct from 'Synthetic independently held source document.' then raise exception 'Held supporting source withdrawal scrubbed content'; end if;
 perform set_config('request.jwt.claim.sub','a1000000-0000-4000-8000-000000000001',true);
 perform public.safety_withdraw_report_v1('a2000000-0000-4000-8000-000000000001','e1000000-0000-4000-8000-000000000003');
 if (select narrative from safety_private.report_content where report_id='a2000000-0000-4000-8000-000000000001') is distinct from 'Synthetic private intake narrative.' then raise exception 'Held claim report withdrawal scrubbed content'; end if;
 perform set_config('request.jwt.claim.sub','a1000000-0000-4000-8000-000000000002',true);

 begin perform public.safety_apply_retention_v1('b2000000-0000-4000-8000-000000000001','a2000000-0000-4000-8000-000000000001','ERASE_CONTENT','SYNTHETIC_RETENTION');raise exception 'Held report erased'; exception when others then if sqlerrm<>'ACTIVE_LEGAL_HOLD' then raise; end if; end;
 begin perform public.safety_release_legal_hold_v1(hold,'SYNTHETIC_RELEASE');raise exception 'Self hold release accepted'; exception when others then if sqlerrm<>'INDEPENDENT_HOLD_RELEASE_REQUIRED' then raise; end if; end;
 perform set_config('request.jwt.claim.sub','a1000000-0000-4000-8000-000000000003',true);
 perform public.safety_release_legal_hold_v1(hold,'SYNTHETIC_RELEASE');
 perform public.safety_apply_retention_v1('b2000000-0000-4000-8000-000000000001','a2000000-0000-4000-8000-000000000001','ERASE_CONTENT','SYNTHETIC_RETENTION');
 if exists(select 1 from safety_private.report_content where report_id='a2000000-0000-4000-8000-000000000001') then raise exception 'Content was not erased'; end if;
 if not exists(select 1 from public.safety_evidence_objects where id='a9000000-0000-4000-8000-000000000001') then raise exception 'Immutable evidence deleted'; end if;
end $$;
rollback;
