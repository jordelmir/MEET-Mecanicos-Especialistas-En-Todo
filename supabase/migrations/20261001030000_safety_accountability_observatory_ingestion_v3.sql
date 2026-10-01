begin;
create table safety_private.institutional_events (
 id uuid primary key default gen_random_uuid(), case_id uuid not null references public.safety_cases(id),
 institution_ref text not null check(institution_ref ~ '^[A-Z0-9][A-Z0-9_.:-]{2,79}$'),
 event_type text not null check(event_type in('REPORT_SENT','DELIVERY_CONFIRMED','REFERENCE_RECEIVED','FOLLOW_UP_SENT','RESPONSE_DOCUMENTED','PUBLIC_ACTION_FOUND','RESULT_DOCUMENTED')),
 occurred_at timestamptz not null, source_evidence_id uuid not null references public.safety_evidence_objects(id),
 reference_digest text not null check(reference_digest ~ '^[a-f0-9]{64}$'), recorded_by uuid,
 machine_id uuid, state_version bigint not null default 1 check(state_version>0),
 supersedes_event_id uuid unique references safety_private.institutional_events(id),
 recorded_at timestamptz not null default now(), check(num_nonnulls(recorded_by,machine_id)=1)
);
create table safety_private.institutional_publication_reviews (
 id uuid primary key default gen_random_uuid(), event_id uuid not null references safety_private.institutional_events(id),
 reviewer_id uuid not null references auth.users(id), reason_code text not null check(length(reason_code) between 3 and 80),
 event_version bigint not null, case_version bigint not null, created_at timestamptz not null default now()
);
create table safety_private.institutional_publication_decisions (
 id uuid primary key default gen_random_uuid(), review_id uuid not null unique references safety_private.institutional_publication_reviews(id),
 publisher_id uuid not null references auth.users(id), decision text not null check(decision in('PUBLISH','REJECT')),
 reason_code text not null check(length(reason_code) between 3 and 80), policy_version text not null default 'SAFETY-ACCOUNTABILITY-V3' check(policy_version='SAFETY-ACCOUNTABILITY-V3'),
 created_at timestamptz not null default now()
);
create table safety_private.institutional_event_voids (
 event_id uuid primary key references safety_private.institutional_events(id), reason_code text not null,
 actor_id uuid not null references auth.users(id), created_at timestamptz not null default now()
);
alter table public.safety_public_accountability_projection add column publication_decision_id uuid not null references safety_private.institutional_publication_decisions(id);
revoke all on public.safety_public_accountability_projection from anon,authenticated,service_role;
grant select on public.safety_public_accountability_projection to authenticated;
drop policy if exists safety_public_accountability_projection_v3_closed on public.safety_public_accountability_projection;
drop policy if exists safety_accountability_v3_published_read on public.safety_public_accountability_projection;
create policy safety_accountability_v3_published_read on public.safety_public_accountability_projection for select to authenticated using(
 exists(select 1 from public.runtime_feature_gates where key='safety_accountability' and enabled)
 and exists(select 1 from public.safety_public_case_projection where case_id=safety_public_accountability_projection.case_id)
);
create or replace function public.safety_record_institutional_event_v3(
 p_id uuid,p_case_id uuid,p_institution_ref text,p_event_type text,p_occurred_at timestamptz,
 p_evidence_id uuid,p_reference_digest text,p_supersedes_id uuid default null
) returns uuid language plpgsql security definer set search_path='' as $$
declare v_existing safety_private.institutional_events%rowtype; v_version bigint:=1;
begin
 if not public.safety_is_reviewer() then raise exception 'REVIEWER_AAL2_REQUIRED'; end if;
 perform 1 from public.safety_cases where id=p_case_id for update;
 if not found then raise exception 'CASE_NOT_FOUND'; end if;
 if p_occurred_at>now() or p_occurred_at is null then raise exception 'INVALID_EVENT_TIME'; end if;
 if not public.safety_evidence_is_verified_v2(p_evidence_id) then raise exception 'SERVER_VERIFIED_EVIDENCE_REQUIRED'; end if;
 if not exists(select 1 from public.safety_case_events ce join public.safety_event_claims ec on ec.event_id=ce.event_id
 join public.safety_claims c on c.id=ec.claim_id join public.safety_evidence_objects e on e.report_id=c.report_id
 where ce.case_id=p_case_id and e.id=p_evidence_id) then raise exception 'CASE_EVIDENCE_BINDING_REQUIRED'; end if;
 select * into v_existing from safety_private.institutional_events where id=p_id;
 if found then
 if row(v_existing.case_id,v_existing.institution_ref,v_existing.event_type,v_existing.occurred_at,v_existing.source_evidence_id,v_existing.reference_digest,v_existing.recorded_by,v_existing.supersedes_event_id)
 is distinct from row(p_case_id,p_institution_ref,p_event_type,p_occurred_at,p_evidence_id,p_reference_digest,auth.uid(),p_supersedes_id)
 then raise exception 'IDEMPOTENCY_PROTOCOL_VIOLATION'; end if;
 return p_id;
 end if;
 if p_supersedes_id is not null then
 select * into v_existing from safety_private.institutional_events where id=p_supersedes_id for update;
 if not found or v_existing.case_id<>p_case_id or v_existing.institution_ref<>p_institution_ref then raise exception 'INVALID_EVENT_REVISION'; end if;
 v_version:=v_existing.state_version+1;
 delete from public.safety_public_accountability_projection where event_id=p_supersedes_id;
 end if;
 insert into safety_private.institutional_events(id,case_id,institution_ref,event_type,occurred_at,source_evidence_id,reference_digest,recorded_by,state_version,supersedes_event_id)
 values(p_id,p_case_id,p_institution_ref,p_event_type,p_occurred_at,p_evidence_id,p_reference_digest,auth.uid(),v_version,p_supersedes_id);
 return p_id;
end $$;
create or replace function public.safety_review_institutional_event_v3(p_event_id uuid,p_reason_code text)
returns uuid language plpgsql security definer set search_path='' as $$
declare v_event safety_private.institutional_events%rowtype; v_case_version bigint; v_id uuid;
begin
 if not public.safety_is_reviewer() then raise exception 'REVIEWER_AAL2_REQUIRED'; end if;
 select * into strict v_event from safety_private.institutional_events where id=p_event_id for update;
 select state_version into strict v_case_version from public.safety_cases where id=v_event.case_id for update;
 if not public.safety_evidence_is_verified_v2(v_event.source_evidence_id) then raise exception 'SERVER_VERIFIED_EVIDENCE_REQUIRED'; end if;
 if exists(select 1 from safety_private.institutional_event_voids where event_id=p_event_id)
 or exists(select 1 from safety_private.institutional_events where supersedes_event_id=p_event_id) then raise exception 'EVENT_RETIRED'; end if;
 insert into safety_private.institutional_publication_reviews(event_id,reviewer_id,reason_code,event_version,case_version)
 values(p_event_id,auth.uid(),p_reason_code,v_event.state_version,v_case_version) returning id into v_id;
 return v_id;
end $$;
create or replace function public.safety_finalize_institutional_event_v3(p_review_id uuid,p_decision text,p_reason_code text)
returns jsonb language plpgsql security definer set search_path='' as $$
declare v_review safety_private.institutional_publication_reviews%rowtype; v_event safety_private.institutional_events%rowtype;
 v_case public.safety_public_case_projection%rowtype; v_decision safety_private.institutional_publication_decisions%rowtype;
begin
 if not public.safety_is_publisher() then raise exception 'PUBLISHER_AAL2_REQUIRED'; end if;
 select * into strict v_review from safety_private.institutional_publication_reviews where id=p_review_id for update;
 if v_review.reviewer_id=auth.uid() then raise exception 'TWO_PERSON_PUBLICATION_REQUIRED'; end if;
 select * into v_decision from safety_private.institutional_publication_decisions where review_id=p_review_id;
 if found then
 if v_decision.publisher_id<>auth.uid() or v_decision.decision<>p_decision or v_decision.reason_code<>p_reason_code then raise exception 'IDEMPOTENCY_PROTOCOL_VIOLATION'; end if;
 return jsonb_build_object('decision_id',v_decision.id,'decision',v_decision.decision);
 end if;
 select * into strict v_event from safety_private.institutional_events where id=v_review.event_id for update;
 select * into v_case from public.safety_public_case_projection where case_id=v_event.case_id for share;
 if p_decision='PUBLISH' and (not found or v_case.case_state_version<>v_review.case_version
 or not public.safety_evidence_is_verified_v2(v_event.source_evidence_id)
 or exists(select 1 from safety_private.institutional_event_voids where event_id=v_event.id)
 or exists(select 1 from safety_private.institutional_events where supersedes_event_id=v_event.id)) then raise exception 'STALE_OR_UNVERIFIED_PUBLICATION'; end if;
 insert into safety_private.institutional_publication_decisions(review_id,publisher_id,decision,reason_code)
 values(p_review_id,auth.uid(),p_decision,p_reason_code) returning * into v_decision;
 if p_decision='PUBLISH' then
 if not exists(select 1 from public.runtime_feature_gates where key='safety_accountability' and enabled) then raise exception 'SAFETY_ACCOUNTABILITY_DISABLED'; end if;
 insert into public.safety_public_accountability_projection(event_id,case_id,case_title,institution_ref,event_type,occurred_at,published_at,server_version,publication_decision_id)
 values(v_event.id,v_event.case_id,v_case.title,v_event.institution_ref,v_event.event_type,v_event.occurred_at,now(),v_event.state_version,v_decision.id)
 on conflict(event_id) do nothing;
 end if;
 return jsonb_build_object('decision_id',v_decision.id,'decision',v_decision.decision);
end $$;
create or replace function public.safety_void_institutional_event_v3(p_event_id uuid,p_reason_code text)
returns void language plpgsql security definer set search_path='' as $$
begin
 if not public.safety_is_publisher() then raise exception 'PUBLISHER_AAL2_REQUIRED'; end if;
 if p_reason_code is null or length(p_reason_code) not between 3 and 80 then raise exception 'INVALID_REASON_CODE'; end if;
 perform 1 from safety_private.institutional_events where id=p_event_id for update;
 insert into safety_private.institutional_event_voids values(p_event_id,p_reason_code,auth.uid(),now()) on conflict do nothing;
 delete from public.safety_public_accountability_projection where event_id=p_event_id;
end $$;
-- Defense in depth: direct writes, even by privileged service workers, cannot bypass review.
create or replace function public.safety_guard_accountability_v3()
returns trigger language plpgsql security definer set search_path='' as $$
begin
 if not exists(select 1 from safety_private.institutional_publication_decisions d
 join safety_private.institutional_publication_reviews r on r.id=d.review_id
 join safety_private.institutional_events e on e.id=r.event_id
 join public.safety_public_case_projection c on c.case_id=e.case_id
 where d.id=new.publication_decision_id and d.decision='PUBLISH' and d.publisher_id<>r.reviewer_id
 and e.id=new.event_id and e.case_id=new.case_id and e.institution_ref=new.institution_ref
 and e.event_type=new.event_type and e.occurred_at=new.occurred_at and e.state_version=new.server_version
 and c.title is not distinct from new.case_title and c.case_state_version=r.case_version
 and public.safety_evidence_is_verified_v2(e.source_evidence_id)
 and not exists(select 1 from safety_private.institutional_event_voids where event_id=e.id)
 and not exists(select 1 from safety_private.institutional_events where supersedes_event_id=e.id)) then raise exception 'ACCOUNTABILITY_PUBLICATION_AUTHORITY_REQUIRED'; end if;
 return new;
end $$;
create trigger safety_guard_accountability_v3 before insert or update on public.safety_public_accountability_projection
for each row execute function public.safety_guard_accountability_v3();
create or replace function public.safety_invalidate_unverified_accountability_v3()
returns trigger language plpgsql security definer set search_path='' as $$
begin
 if new.verification_state in('MISMATCH','QUARANTINED') then
 delete from public.safety_public_accountability_projection p using safety_private.institutional_events e
 where p.event_id=e.id and e.source_evidence_id=new.evidence_id;
 end if;
 return new;
end $$;
create trigger safety_invalidate_unverified_accountability_v3 after insert on public.safety_evidence_verifications
for each row execute function public.safety_invalidate_unverified_accountability_v3();

-- Public queries never read raw reports, private coordinates or reporter demographics.
create table safety_private.observatory_policy (
 singleton boolean primary key default true check(singleton), min_cell_count integer not null check(min_cell_count>=5),
 publication_delay interval not null check(publication_delay>=interval '7 days'),
 cell_degrees numeric not null check(cell_degrees>=0.1 and cell_degrees<=1), policy_version text not null
);
insert into safety_private.observatory_policy values(true,5,interval '7 days',0.1,'SAFETY-OBSERVATORY-V3');
create or replace function public.safety_observatory_query_v3(p_from timestamptz,p_until timestamptz,p_category text default null,p_country_code text default null,p_admin1_code text default null,p_admin2_code text default null)
returns jsonb language plpgsql stable security definer set search_path='' as $$
declare v_policy safety_private.observatory_policy%rowtype; v_result jsonb;
begin
 if auth.uid() is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
 if not exists(select 1 from public.runtime_feature_gates where key='safety_observatory' and enabled) then raise exception 'SAFETY_OBSERVATORY_DISABLED'; end if;
 select * into strict v_policy from safety_private.observatory_policy where singleton;
 -- Restrict to whole UTC weeks and a bounded interval to prevent differencing tiny time slices.
 if p_from is null or p_until is null or p_until<=p_from or p_until-p_from>interval '366 days'
 or p_from is distinct from (date_trunc('week',p_from at time zone 'UTC') at time zone 'UTC')
 or p_until is distinct from (date_trunc('week',p_until at time zone 'UTC') at time zone 'UTC')
 or p_until>now()-v_policy.publication_delay then raise exception 'INVALID_OR_UNSAFE_OBSERVATORY_RANGE'; end if;
 select coalesce(jsonb_agg(to_jsonb(cells)),'[]'::jsonb) into v_result from (
 select floor(p.display_latitude/v_policy.cell_degrees)::text||':'||floor(p.display_longitude/v_policy.cell_degrees)::text as public_cell_id,
 p.category,date_trunc('week',p.first_documented_at at time zone 'UTC') as period_start,count(distinct p.claim_id) as documented_claim_count
 from public.safety_public_points p join public.safety_publication_decisions d on d.id=p.publication_decision_id
 where d.decision_phase='FINAL' and d.decision in('PUBLISH','REDACT') and d.policy_version='SAFETY-PUBLICATION-V3'
 and p.claim_state in('DOCUMENTED','CORROBORATED','STRONGLY_CORROBORATED') and p.first_documented_at>=p_from and p.first_documented_at<p_until
 and p.published_at<=now()-v_policy.publication_delay and (p_category is null or p.category=p_category)
 and (p_country_code is null or p.country_code=p_country_code) and (p_admin1_code is null or p.admin1_code=p_admin1_code) and (p_admin2_code is null or p.admin2_code=p_admin2_code)
 group by 1,2,3 having count(distinct p.claim_id)>=v_policy.min_cell_count
 ) cells;
 return jsonb_build_object('policy_version',v_policy.policy_version,'cells',v_result,'suppression','SMALL_CELLS_OMITTED','missing_records_imply_inaction',false);
end $$;
create or replace function public.safety_counternarcotics_patterns_v1(p_from timestamptz,p_until timestamptz,p_country_code text default null,p_admin1_code text default null,p_admin2_code text default null)
returns jsonb language plpgsql stable security definer set search_path='' as $$
declare v_base jsonb; v_policy safety_private.observatory_policy%rowtype; v_result jsonb;
begin
 v_base:=public.safety_observatory_query_v3(p_from,p_until,'DRUG_SALE_ACTIVITY',p_country_code,p_admin1_code,p_admin2_code);
 select * into strict v_policy from safety_private.observatory_policy where singleton;
 select coalesce(jsonb_agg(to_jsonb(patterns)),'[]'::jsonb) into v_result from (
 select cell->>'public_cell_id' as public_cell_id,cell->>'period_start' as period_start,
 (cell->>'documented_claim_count')::int as documented_claim_count,
 count(distinct s.cluster_id) as independent_source_clusters,
 count(distinct date_trunc('week',p.first_documented_at at time zone 'UTC')) as active_weeks,
 count(distinct s.id) filter(where s.source_type='JOURNALISTIC') as journalistic_sources,
 count(distinct s.id) filter(where s.source_type='PUBLIC_RECORD') as public_record_sources,
 count(distinct s.id) filter(where s.source_type='INSTITUTIONAL') as institutional_sources,
 count(distinct accountability.event_id) as institutional_response_events,
 case when count(distinct s.cluster_id)>=2 and bool_and(p.claim_state in('CORROBORATED','STRONGLY_CORROBORATED'))
 then 'CORROBORATED_PATTERN' else 'DOCUMENTED_PATTERN' end as truth_state,
 'SAFETY-COUNTERNARCOTICS-V1' as policy_version
 from jsonb_array_elements(v_base->'cells') cell
 join public.safety_public_points p on floor(p.display_latitude/v_policy.cell_degrees)::text||':'||floor(p.display_longitude/v_policy.cell_degrees)::text=cell->>'public_cell_id'
 and to_char(date_trunc('week',p.first_documented_at at time zone 'UTC'),'YYYY-MM-DD"T"HH24:MI:SS')=cell->>'period_start'
 join public.safety_claim_sources cs on cs.claim_id=p.claim_id and cs.role in('SUPPORTING','CORROBORATING','PRIMARY')
 join public.safety_sources s on s.id=cs.source_id and s.source_type in('JOURNALISTIC','PUBLIC_RECORD','INSTITUTIONAL') and s.cluster_id is not null and s.independence_score>=0.7 and s.reliability_score>=0.5
 left join public.safety_event_claims ec on ec.claim_id=p.claim_id
 left join public.safety_case_events ce on ce.event_id=ec.event_id
 left join public.safety_public_accountability_projection accountability on accountability.case_id=ce.case_id
 and accountability.published_at<=now()-v_policy.publication_delay
 and date_trunc('week',accountability.occurred_at at time zone 'UTC')=date_trunc('week',p.first_documented_at at time zone 'UTC')
 where p.category='DRUG_SALE_ACTIVITY' and p.published_at<=now()-v_policy.publication_delay
 and (p_country_code is null or p.country_code=p_country_code) and (p_admin1_code is null or p.admin1_code=p_admin1_code) and (p_admin2_code is null or p.admin2_code=p_admin2_code)
 group by cell having count(distinct p.claim_id)>=v_policy.min_cell_count
 ) patterns;
 return jsonb_build_object('patterns',v_result,'interpretation','DOCUMENTED_SOURCE_PATTERNS_ONLY; ARREST_IS_NOT_GUILT; NO_PERSON_INFERENCE','missing_records_imply_inaction',false);
end $$;

create table public.safety_external_providers (
 id uuid primary key default gen_random_uuid(),provider_code text not null unique check(provider_code ~ '^[A-Z0-9_]{3,80}$'),
 display_name text not null,provider_type text not null check(provider_type in('JOURNALISTIC','INSTITUTIONAL','PUBLIC_RECORD','RESEARCH')),
 active boolean not null default false,created_at timestamptz not null default now()
);
create table safety_private.external_source_revisions (
 id uuid primary key default gen_random_uuid(),provider_id uuid not null references public.safety_external_providers(id),
 external_record_id text not null check(length(external_record_id) between 1 and 200),
 canonical_url text not null check(canonical_url ~ '^https://[^[:space:]]+$'),
 title text not null check(length(title) between 1 and 500), source_published_at timestamptz not null,
 content_sha256 text not null check(content_sha256 ~ '^[a-f0-9]{64}$'),
 revision integer not null check(revision>0),supersedes_id uuid references safety_private.external_source_revisions(id),
 ingestion_state text not null default 'UNDER_REVIEW' check(ingestion_state='UNDER_REVIEW'),received_at timestamptz not null default now(),
 unique(provider_id,external_record_id,content_sha256),unique(provider_id,external_record_id,revision)
);
create table safety_private.external_revision_source_links (
 revision_id uuid not null references safety_private.external_source_revisions(id),source_id uuid not null references public.safety_sources(id),
 reviewer_id uuid not null references auth.users(id),reason_code text not null,created_at timestamptz not null default now(),primary key(revision_id,source_id)
);
create table safety_private.import_batches (
 batch_id uuid primary key,provider_id uuid not null references public.safety_external_providers(id),input_hash text not null,
 record_count int not null,accepted_count int not null default 0 check(accepted_count=0),duplicate_count int not null,rejected_count int not null default 0,
 receipts jsonb not null,started_at timestamptz not null,completed_at timestamptz not null
);
create or replace function public.safety_import_source_batch_v1(p_provider_id uuid,p_batch_id uuid,p_records jsonb)
returns jsonb language plpgsql security definer set search_path='' as $$
declare v_hash text;v_batch safety_private.import_batches%rowtype;v_item jsonb;v_existing safety_private.external_source_revisions%rowtype;
 v_latest safety_private.external_source_revisions%rowtype;v_id uuid;v_receipts jsonb:='[]';v_duplicates int:=0;v_new int:=0;v_started timestamptz:=now();
begin
 if jsonb_typeof(p_records) is distinct from 'array' or jsonb_array_length(p_records) not between 1 and 1000 then raise exception 'INVALID_BATCH_SIZE'; end if;
 if exists(select 1 from jsonb_array_elements(p_records) r group by r->>'external_record_id' having count(*)>1) then raise exception 'DUPLICATE_EXTERNAL_ID_IN_BATCH'; end if;
 perform 1 from public.safety_external_providers where id=p_provider_id and active for update;
 if not found then raise exception 'ACTIVE_PROVIDER_REQUIRED'; end if;
 select encode(extensions.digest(convert_to(jsonb_agg(r order by r->>'external_record_id')::text,'UTF8'),'sha256'),'hex') into v_hash from jsonb_array_elements(p_records) r;
 perform pg_advisory_xact_lock(hashtextextended('safety-batch:'||p_batch_id::text,0));
 select * into v_batch from safety_private.import_batches where batch_id=p_batch_id;
 if found then
 if v_batch.provider_id<>p_provider_id or v_batch.input_hash<>v_hash then raise exception 'IDEMPOTENCY_PROTOCOL_VIOLATION'; end if;
 return jsonb_build_object('batch_id',p_batch_id,'new_records',0,'receipts',v_batch.receipts,'replayed',true);
 end if;
 for v_item in select value from jsonb_array_elements(p_records) loop
 if v_item->>'external_record_id' is null or v_item->>'canonical_url' is null or v_item->>'title' is null
 or v_item->>'source_published_at' is null or v_item->>'content_sha256' is null then raise exception 'INVALID_SOURCE_RECORD'; end if;
 select * into v_existing from safety_private.external_source_revisions where provider_id=p_provider_id
 and external_record_id=v_item->>'external_record_id' and content_sha256=v_item->>'content_sha256';
 if found then
 if row(v_existing.canonical_url,v_existing.title,v_existing.source_published_at) is distinct from row(v_item->>'canonical_url',v_item->>'title',(v_item->>'source_published_at')::timestamptz) then raise exception 'SOURCE_DIGEST_METADATA_CONFLICT'; end if;
 v_id:=v_existing.id;v_duplicates:=v_duplicates+1;
 else
 select * into v_latest from safety_private.external_source_revisions where provider_id=p_provider_id and external_record_id=v_item->>'external_record_id' order by revision desc limit 1;
 insert into safety_private.external_source_revisions(provider_id,external_record_id,canonical_url,title,source_published_at,content_sha256,revision,supersedes_id)
 values(p_provider_id,v_item->>'external_record_id',v_item->>'canonical_url',v_item->>'title',(v_item->>'source_published_at')::timestamptz,v_item->>'content_sha256',coalesce(v_latest.revision,0)+1,v_latest.id) returning id into v_id;
 v_new:=v_new+1;
 if v_latest.id is not null then
 insert into safety_private.claim_reevaluation_v3(claim_id,report_id,reason_code,requested_at,status)
 select c.id,c.report_id,'EXTERNAL_SOURCE_REVISION',now(),'PENDING' from safety_private.external_revision_source_links l
 join public.safety_claim_sources cs on cs.source_id=l.source_id join public.safety_claims c on c.id=cs.claim_id where l.revision_id=v_latest.id
 on conflict(claim_id) do update set reason_code=excluded.reason_code,requested_at=excluded.requested_at,status='PENDING';
 delete from public.safety_public_points p using public.safety_claim_sources cs,safety_private.external_revision_source_links l
 where p.claim_id=cs.claim_id and cs.source_id=l.source_id and l.revision_id=v_latest.id;
 delete from public.safety_public_case_projection p using public.safety_case_events ce,public.safety_event_claims ec,public.safety_claim_sources cs,safety_private.external_revision_source_links l
 where p.case_id=ce.case_id and ce.event_id=ec.event_id and ec.claim_id=cs.claim_id and cs.source_id=l.source_id and l.revision_id=v_latest.id;
 end if;
 end if;
 v_receipts:=v_receipts||jsonb_build_array(jsonb_build_object('external_record_id',v_item->>'external_record_id','revision_id',v_id,'ingestion_state','UNDER_REVIEW'));
 end loop;
 insert into safety_private.import_batches(batch_id,provider_id,input_hash,record_count,duplicate_count,receipts,started_at,completed_at)
 values(p_batch_id,p_provider_id,v_hash,jsonb_array_length(p_records),v_duplicates,v_receipts,v_started,now());
 return jsonb_build_object('batch_id',p_batch_id,'new_records',v_new,'receipts',v_receipts,'replayed',false,'accepted_count',0);
end $$;
create or replace function public.safety_link_external_revision_v1(p_revision_id uuid,p_source_id uuid,p_reason_code text)
returns void language plpgsql security definer set search_path='' as $$
begin
 if not public.safety_is_reviewer() then raise exception 'REVIEWER_AAL2_REQUIRED'; end if;
 if p_reason_code is null or length(p_reason_code) not between 3 and 80 then raise exception 'INVALID_REASON_CODE'; end if;
 if not exists(select 1 from safety_private.external_source_revisions r join public.safety_external_providers p on p.id=r.provider_id
 join public.safety_sources s on s.id=p_source_id and s.source_type=p.provider_type
 where r.id=p_revision_id and p.active and not exists(select 1 from safety_private.external_source_revisions where supersedes_id=r.id)) then raise exception 'CURRENT_MATCHING_SOURCE_REVISION_REQUIRED'; end if;
 insert into safety_private.external_revision_source_links(revision_id,source_id,reviewer_id,reason_code)
 values(p_revision_id,p_source_id,auth.uid(),p_reason_code) on conflict do nothing;
end $$;
-- All private authority tables are immutable ledgers with no direct client writes.
do $$ declare t text; begin
 foreach t in array array['institutional_events','institutional_publication_reviews','institutional_publication_decisions','institutional_event_voids','external_source_revisions','external_revision_source_links','import_batches'] loop
 execute format('alter table safety_private.%I enable row level security',t);
 execute format('revoke all on safety_private.%I from public,anon,authenticated,service_role',t);
 execute format('create trigger %I before update or delete on safety_private.%I for each row execute function public.safety_reject_publication_history_mutation()',t||'_immutable',t);
 end loop;
end $$;
alter table safety_private.observatory_policy enable row level security;
revoke all on safety_private.observatory_policy from public,anon,authenticated,service_role;
alter table public.safety_external_providers enable row level security;
revoke all on public.safety_external_providers from public,anon,authenticated,service_role;
-- Explicit function ACLs; no SQL function inherits PUBLIC execution.
revoke all on function public.safety_record_institutional_event_v3(uuid,uuid,text,text,timestamptz,uuid,text,uuid),public.safety_review_institutional_event_v3(uuid,text),public.safety_finalize_institutional_event_v3(uuid,text,text),public.safety_void_institutional_event_v3(uuid,text),public.safety_observatory_query_v3(timestamptz,timestamptz,text,text,text,text),public.safety_counternarcotics_patterns_v1(timestamptz,timestamptz,text,text,text),public.safety_link_external_revision_v1(uuid,uuid,text) from public,anon,service_role;
grant execute on function public.safety_record_institutional_event_v3(uuid,uuid,text,text,timestamptz,uuid,text,uuid),public.safety_review_institutional_event_v3(uuid,text),public.safety_finalize_institutional_event_v3(uuid,text,text),public.safety_void_institutional_event_v3(uuid,text),public.safety_observatory_query_v3(timestamptz,timestamptz,text,text,text,text),public.safety_counternarcotics_patterns_v1(timestamptz,timestamptz,text,text,text),public.safety_link_external_revision_v1(uuid,uuid,text) to authenticated;
revoke all on function public.safety_import_source_batch_v1(uuid,uuid,jsonb) from public,anon,authenticated;
grant execute on function public.safety_import_source_batch_v1(uuid,uuid,jsonb) to service_role;
revoke all on function public.safety_guard_accountability_v3(),public.safety_invalidate_unverified_accountability_v3() from public,anon,authenticated,service_role;
revoke all on safety_private.institutional_events from public,anon,authenticated,service_role;
revoke all on safety_private.institutional_publication_reviews from public,anon,authenticated,service_role;
revoke all on safety_private.institutional_publication_decisions from public,anon,authenticated,service_role;
revoke all on safety_private.institutional_event_voids from public,anon,authenticated,service_role;
revoke all on safety_private.external_source_revisions from public,anon,authenticated,service_role;
revoke all on safety_private.external_revision_source_links from public,anon,authenticated,service_role;
revoke all on safety_private.import_batches from public,anon,authenticated,service_role;
commit;
