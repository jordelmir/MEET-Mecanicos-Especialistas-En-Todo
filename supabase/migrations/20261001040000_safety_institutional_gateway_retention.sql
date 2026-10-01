begin;
-- Machine principals are provisioned out of band by the operator. Institutional
-- actors receive OAuth scopes, never database credentials or service role keys.
create table safety_private.institutional_clients (
 id uuid primary key default gen_random_uuid(),issuer text not null,subject text not null,
 institution_ref text not null check(institution_ref ~ '^[A-Z0-9][A-Z0-9_.:-]{2,79}$'),
 scopes text[] not null check(scopes <@ array['safety:submit_source','safety:read_public','safety:submit_institutional_event','safety:read_restricted','safety:export_audit_bundle']::text[]),
 provider_id uuid references public.safety_external_providers(id),active boolean not null default false,
 expires_at timestamptz not null,unique(issuer,subject)
);
create table safety_private.institutional_resource_grants (
 client_id uuid not null references safety_private.institutional_clients(id),report_id uuid not null references public.safety_reports(id),
 purpose_code text not null check(length(purpose_code) between 3 and 80),expires_at timestamptz not null,primary key(client_id,report_id,purpose_code)
);
create table safety_private.institutional_access_audit (
 id uuid primary key default gen_random_uuid(),client_id uuid not null references safety_private.institutional_clients(id),
 scope text not null,resource_id uuid,purpose_code text,request_digest text not null,accessed_at timestamptz not null default now()
);
create or replace function public.safety_institutional_gateway_v1(p_issuer text,p_subject text,p_scopes text[],p_action text,p_payload jsonb)
returns jsonb language plpgsql security definer set search_path='' as $$
declare v_client safety_private.institutional_clients%rowtype;v_scope text;v_result jsonb;v_report uuid;v_event safety_private.institutional_events%rowtype;
 v_evidence uuid;v_case uuid;v_id uuid;
begin
 select * into strict v_client from safety_private.institutional_clients
 where issuer=p_issuer and subject=p_subject and active and expires_at>now() for share;
 v_scope:=case p_action when 'submit_source' then 'safety:submit_source' when 'read_public' then 'safety:read_public'
 when 'submit_institutional_event' then 'safety:submit_institutional_event' when 'read_restricted' then 'safety:read_restricted'
 when 'export_audit_bundle' then 'safety:export_audit_bundle' else null end;
 if v_scope is null or not (v_scope=any(v_client.scopes)) or not (v_scope=any(p_scopes)) then raise exception 'INSTITUTIONAL_SCOPE_DENIED'; end if;
 if p_action='submit_source' then
 if v_client.provider_id is null then raise exception 'PROVIDER_BINDING_REQUIRED'; end if;
 v_result:=public.safety_import_source_batch_v1(v_client.provider_id,(p_payload->>'batch_id')::uuid,p_payload->'records');
 elsif p_action='submit_institutional_event' then
 v_id:=(p_payload->>'id')::uuid;v_case:=(p_payload->>'case_id')::uuid;v_evidence:=(p_payload->>'source_evidence_id')::uuid;
 perform 1 from public.safety_cases where id=v_case for update;
 if not found or not public.safety_evidence_is_verified_v2(v_evidence) then raise exception 'CASE_AND_VERIFIED_EVIDENCE_REQUIRED'; end if;
 if not exists(select 1 from public.safety_case_events ce join public.safety_event_claims ec on ec.event_id=ce.event_id
 join public.safety_claims c on c.id=ec.claim_id join public.safety_evidence_objects e on e.report_id=c.report_id
 join safety_private.institutional_resource_grants g on g.report_id=c.report_id and g.client_id=v_client.id
 where ce.case_id=v_case and e.id=v_evidence and g.purpose_code=p_payload->>'purpose_code' and g.expires_at>now()) then raise exception 'INSTITUTIONAL_CASE_RESOURCE_DENIED'; end if;
 if (p_payload->>'occurred_at')::timestamptz>now() then raise exception 'INVALID_EVENT_TIME'; end if;
 select * into v_event from safety_private.institutional_events where id=v_id;
 if found then
 if row(v_event.case_id,v_event.institution_ref,v_event.machine_id,v_event.event_type,v_event.source_evidence_id,v_event.reference_digest,v_event.occurred_at)
 is distinct from row(v_case,v_client.institution_ref,v_client.id,p_payload->>'event_type',v_evidence,p_payload->>'reference_digest',(p_payload->>'occurred_at')::timestamptz)
 then raise exception 'IDEMPOTENCY_PROTOCOL_VIOLATION'; end if;
 else
 insert into safety_private.institutional_events(id,case_id,institution_ref,event_type,occurred_at,source_evidence_id,reference_digest,machine_id)
 values(v_id,v_case,v_client.institution_ref,p_payload->>'event_type',(p_payload->>'occurred_at')::timestamptz,v_evidence,p_payload->>'reference_digest',v_client.id);
 end if;
 v_result:=jsonb_build_object('event_id',v_id,'state','RECEIVED_UNDER_REVIEW','public',false);
 elsif p_action='read_public' then
 if not exists(select 1 from public.runtime_feature_gates where key='safety_public_cases' and enabled) then raise exception 'SAFETY_PUBLIC_CASES_DISABLED'; end if;
 select jsonb_build_object('cases',coalesce(jsonb_agg(jsonb_build_object('case_id',case_id,'title',title,'public_summary',public_summary,'lifecycle',lifecycle,'server_version',server_version)),'[]'::jsonb)) into v_result
 from (select * from public.safety_public_case_projection order by published_at desc limit 100) p;
 elsif p_action='read_restricted' then
 v_report:=(p_payload->>'report_id')::uuid;
 if not exists(select 1 from safety_private.institutional_resource_grants where client_id=v_client.id and report_id=v_report
 and purpose_code=p_payload->>'purpose_code' and expires_at>now()) then raise exception 'INSTITUTIONAL_RESOURCE_DENIED'; end if;
 -- Purpose-limited receipt metadata only; no narrative/GPS/reporter identity,
 -- object paths or signed download URLs are returned by this endpoint.
 select jsonb_build_object('report_id',v_report,'evidence_receipts',coalesce(jsonb_agg(jsonb_build_object('evidence_id',id,'content_sha256',content_sha256,'verified',public.safety_evidence_is_verified_v2(id),'byte_count',byte_count)),'[]'::jsonb)) into v_result
 from public.safety_evidence_objects where report_id=v_report;
 elsif p_action='export_audit_bundle' then
 select jsonb_build_object('institution_ref',v_client.institution_ref,'policy_version','SAFETY-INSTITUTIONAL-GATEWAY-V1',
 'access_events',coalesce(jsonb_agg(jsonb_build_object('scope',scope,'accessed_at',accessed_at,'request_digest',request_digest)),'[]'::jsonb)) into v_result
 from (select * from safety_private.institutional_access_audit where client_id=v_client.id order by accessed_at desc limit 500) a;
 end if;
 insert into safety_private.institutional_access_audit(client_id,scope,resource_id,purpose_code,request_digest)
 values(v_client.id,v_scope,v_report,p_payload->>'purpose_code',encode(extensions.digest(convert_to(p_payload::text,'UTF8'),'sha256'),'hex'));
 return v_result;
end $$;
alter table safety_private.institutional_events add constraint safety_institutional_event_machine_fk foreign key(machine_id) references safety_private.institutional_clients(id);

create table safety_private.legal_holds (
 id uuid primary key default gen_random_uuid(),subject_type text not null check(subject_type in('REPORT','EVIDENCE','CASE')),
 subject_id uuid not null,reason_code text not null check(length(reason_code) between 3 and 80),
 authority_reference_digest text not null check(authority_reference_digest ~ '^[a-f0-9]{64}$'),
 created_by uuid not null references auth.users(id),created_at timestamptz not null default now()
);
create index safety_legal_holds_subject on safety_private.legal_holds(subject_type,subject_id);
create table safety_private.legal_hold_releases (
 hold_id uuid primary key references safety_private.legal_holds(id),released_by uuid not null references auth.users(id),
 reason_code text not null check(length(reason_code) between 3 and 80),released_at timestamptz not null default now()
);
create table safety_private.retention_classifications (
 id uuid primary key default gen_random_uuid(),report_id uuid not null references public.safety_reports(id),
 data_class text not null check(data_class in('ERASABLE','PSEUDONYMIZABLE','RETENTION_REQUIRED','PUBLIC_RECORD_REFERENCE','IMMUTABLE_AUDIT_METADATA')),
 classified_by uuid not null references auth.users(id),reason_code text not null check(length(reason_code) between 3 and 80),created_at timestamptz not null default clock_timestamp()
);
create table safety_private.retention_policy (
 singleton boolean primary key default true check(singleton),minimum_age interval not null check(minimum_age>=interval '90 days'),
 policy_version text not null
);
insert into safety_private.retention_policy values(true,interval '90 days','SAFETY-RETENTION-V1');
create table safety_private.retention_operations (
 id uuid primary key,report_id uuid not null references public.safety_reports(id),operation text not null check(operation in('ERASE_CONTENT','PSEUDONYMIZE_CONTENT')),
 authorized_by uuid not null references auth.users(id),reason_code text not null check(length(reason_code) between 3 and 80),
 prior_content_digest text,policy_version text not null,performed_at timestamptz not null default now()
);
create or replace function public.safety_place_legal_hold_v1(p_subject_type text,p_subject_id uuid,p_reason_code text,p_authority_digest text)
returns uuid language plpgsql security definer set search_path='' as $$
declare v_id uuid;
begin
 if not public.safety_is_publisher() then raise exception 'LEGAL_AUTHORITY_AAL2_REQUIRED'; end if;
 -- A shared transaction lock prevents placing a hold concurrently with erasure,
 -- including indirect CASE/EVIDENCE holds covering a report.
 perform pg_advisory_xact_lock(hashtextextended('SAFETY-LEGAL-RETENTION',0));
 if (p_subject_type='REPORT' and not exists(select 1 from public.safety_reports where id=p_subject_id))
 or (p_subject_type='EVIDENCE' and not exists(select 1 from public.safety_evidence_objects where id=p_subject_id))
 or (p_subject_type='CASE' and not exists(select 1 from public.safety_cases where id=p_subject_id)) then raise exception 'HOLD_SUBJECT_NOT_FOUND'; end if;
 insert into safety_private.legal_holds(subject_type,subject_id,reason_code,authority_reference_digest,created_by)
 values(p_subject_type,p_subject_id,p_reason_code,p_authority_digest,auth.uid()) returning id into v_id;
 return v_id;
end $$;
create or replace function public.safety_release_legal_hold_v1(p_hold_id uuid,p_reason_code text)
returns void language plpgsql security definer set search_path='' as $$
declare v_hold safety_private.legal_holds%rowtype;
begin
 if not public.safety_is_publisher() then raise exception 'LEGAL_AUTHORITY_AAL2_REQUIRED'; end if;
 perform pg_advisory_xact_lock(hashtextextended('SAFETY-LEGAL-RETENTION',0));
 select * into strict v_hold from safety_private.legal_holds where id=p_hold_id;
 if v_hold.created_by=auth.uid() then raise exception 'INDEPENDENT_HOLD_RELEASE_REQUIRED'; end if;
 insert into safety_private.legal_hold_releases(hold_id,released_by,reason_code) values(p_hold_id,auth.uid(),p_reason_code) on conflict do nothing;
end $$;
create or replace function public.safety_classify_retention_v1(p_report_id uuid,p_data_class text,p_reason_code text)
returns uuid language plpgsql security definer set search_path='' as $$
declare v_id uuid;
begin
 if not public.safety_is_publisher() then raise exception 'LEGAL_AUTHORITY_AAL2_REQUIRED'; end if;
 perform pg_advisory_xact_lock(hashtextextended('SAFETY-LEGAL-RETENTION',0));
 insert into safety_private.retention_classifications(report_id,data_class,classified_by,reason_code)
 values(p_report_id,p_data_class,auth.uid(),p_reason_code) returning id into v_id;
 return v_id;
end $$;
-- One complete case provenance graph is shared by hold checks and data disposal.
create or replace function safety_private.case_report_ids_v1(p_case_id uuid)
returns table(report_id uuid) language sql stable security definer set search_path='' as $$
 select e.report_id from public.safety_case_events ce join public.safety_events e on e.id=ce.event_id where ce.case_id=p_case_id
 union
 select c.report_id from public.safety_case_events ce join public.safety_event_claims ec on ec.event_id=ce.event_id join public.safety_claims c on c.id=ec.claim_id where ce.case_id=p_case_id
 union
 select s.report_id from public.safety_case_events ce join public.safety_event_claims ec on ec.event_id=ce.event_id join public.safety_claim_sources cs on cs.claim_id=ec.claim_id join public.safety_sources s on s.id=cs.source_id where ce.case_id=p_case_id
$$;
create or replace function public.safety_report_is_held_v1(p_report_id uuid)
returns boolean language sql stable security definer set search_path='' as $$
 select exists(select 1 from safety_private.legal_holds h where not exists(select 1 from safety_private.legal_hold_releases where hold_id=h.id)
 and ((h.subject_type='REPORT' and h.subject_id=p_report_id)
 or (h.subject_type='EVIDENCE' and exists(select 1 from public.safety_evidence_objects where id=h.subject_id and report_id=p_report_id))
 or (h.subject_type='CASE' and exists(select 1 from safety_private.case_report_ids_v1(h.subject_id) g where g.report_id=p_report_id))))
$$;
revoke all on function safety_private.case_report_ids_v1(uuid),public.safety_report_is_held_v1(uuid) from public,anon,authenticated,service_role;
create or replace function public.safety_apply_retention_v1(p_operation_id uuid,p_report_id uuid,p_operation text,p_reason_code text)
returns jsonb language plpgsql security definer set search_path='' as $$
declare v_class text;v_policy safety_private.retention_policy%rowtype;v_existing safety_private.retention_operations%rowtype;
 v_report public.safety_reports%rowtype;v_digest text;
begin
 if not public.safety_is_publisher() then raise exception 'LEGAL_AUTHORITY_AAL2_REQUIRED'; end if;
 perform pg_advisory_xact_lock(hashtextextended('SAFETY-LEGAL-RETENTION',0));
 select * into v_existing from safety_private.retention_operations where id=p_operation_id;
 if found then
 if row(v_existing.report_id,v_existing.operation,v_existing.authorized_by,v_existing.reason_code) is distinct from row(p_report_id,p_operation,auth.uid(),p_reason_code)
 then raise exception 'IDEMPOTENCY_PROTOCOL_VIOLATION'; end if;
 return jsonb_build_object('operation_id',v_existing.id,'operation',v_existing.operation,'replayed',true);
 end if;
 select * into strict v_report from public.safety_reports where id=p_report_id for update;
 select * into strict v_policy from safety_private.retention_policy where singleton;
 select data_class into v_class from safety_private.retention_classifications where report_id=p_report_id order by created_at desc,id desc limit 1;
 if v_report.created_at>now()-v_policy.minimum_age then raise exception 'RETENTION_MINIMUM_AGE_NOT_REACHED'; end if;
 if p_operation is null or not ((p_operation='ERASE_CONTENT' and v_class='ERASABLE') or (p_operation='PSEUDONYMIZE_CONTENT' and v_class='PSEUDONYMIZABLE')) then raise exception 'RETENTION_CLASS_OPERATION_DENIED'; end if;
 if public.safety_report_is_held_v1(p_report_id) then raise exception 'ACTIVE_LEGAL_HOLD'; end if;
 -- Original evidence blobs/custody are retention-required and deliberately cannot
 -- be deleted through this content operation. Blob disposal needs separate review.
 select server_payload_sha256 into v_digest from safety_private.report_content where report_id=p_report_id;
 insert into safety_private.retention_operations(id,report_id,operation,authorized_by,reason_code,prior_content_digest,policy_version)
 values(p_operation_id,p_report_id,p_operation,auth.uid(),p_reason_code,v_digest,v_policy.policy_version);
 delete from public.safety_public_case_projection p where exists(select 1 from safety_private.case_report_ids_v1(p.case_id) g where g.report_id=p_report_id);
 delete from public.safety_public_points p where p.claim_id in(
 select c.id from public.safety_claims c left join public.safety_claim_sources cs on cs.claim_id=c.id left join public.safety_sources source on source.id=cs.source_id
 where c.report_id=p_report_id or source.report_id=p_report_id);
 update public.safety_reports set state='WITHDRAWN',state_version=state_version+1,updated_at=now() where id=p_report_id;
 if p_operation='ERASE_CONTENT' then delete from safety_private.report_content where report_id=p_report_id;
 else update safety_private.report_content set narrative='Content removed under authorized retention policy',latitude=null,longitude=null,accuracy_meters=null,location_source='NONE' where report_id=p_report_id; end if;
 return jsonb_build_object('operation_id',p_operation_id,'operation',p_operation,'replayed',false,'evidence_disposal','SEPARATE_REVIEW_REQUIRED');
end $$;
do $$ declare t text; begin
 foreach t in array array['institutional_clients','institutional_resource_grants','institutional_access_audit','legal_holds','legal_hold_releases','retention_classifications','retention_policy','retention_operations'] loop
 execute format('alter table safety_private.%I enable row level security',t);
 execute format('revoke all on safety_private.%I from public,anon,authenticated,service_role',t);
 if t not in('institutional_clients','institutional_resource_grants','retention_policy') then
 execute format('create trigger %I before update or delete on safety_private.%I for each row execute function public.safety_reject_publication_history_mutation()',t||'_immutable',t);
 end if;
 end loop;
end $$;
revoke all on function public.safety_institutional_gateway_v1(text,text,text[],text,jsonb) from public,anon,authenticated;
grant execute on function public.safety_institutional_gateway_v1(text,text,text[],text,jsonb) to service_role;
revoke all on function public.safety_place_legal_hold_v1(text,uuid,text,text),public.safety_release_legal_hold_v1(uuid,text),public.safety_classify_retention_v1(uuid,text,text),public.safety_apply_retention_v1(uuid,uuid,text,text) from public,anon,service_role;
grant execute on function public.safety_place_legal_hold_v1(text,uuid,text,text),public.safety_release_legal_hold_v1(uuid,text),public.safety_classify_retention_v1(uuid,text,text),public.safety_apply_retention_v1(uuid,uuid,text,text) to authenticated;
revoke all on safety_private.institutional_clients from public,anon,authenticated,service_role;
revoke all on safety_private.institutional_resource_grants from public,anon,authenticated,service_role;
revoke all on safety_private.institutional_access_audit from public,anon,authenticated,service_role;
revoke all on safety_private.legal_holds from public,anon,authenticated,service_role;
revoke all on safety_private.legal_hold_releases from public,anon,authenticated,service_role;
revoke all on safety_private.retention_classifications from public,anon,authenticated,service_role;
revoke all on safety_private.retention_policy from public,anon,authenticated,service_role;
revoke all on safety_private.retention_operations from public,anon,authenticated,service_role;
commit;
