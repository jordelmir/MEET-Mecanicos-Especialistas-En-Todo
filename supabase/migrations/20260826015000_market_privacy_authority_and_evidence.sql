-- Privacy, supervised authority, consent and evidence access closure.
-- External authorities remain the source of truth; MEET records observations
-- and never lets a professional self-promote a credential to ACTIVE.

create table public.market_authority_verification_events (
    verification_event_id uuid primary key default gen_random_uuid(),
    credential_id uuid not null references public.market_professional_credentials(credential_id),
    authority text not null,
    source_reference text not null,
    source_payload_hash text not null check (source_payload_hash ~ '^[a-f0-9]{64}$'),
    decision text not null check (decision in ('ACTIVE','SUSPENDED','EXPIRED','REVOKED','NOT_FOUND','MISMATCH')),
    observed_at timestamptz not null,
    valid_until timestamptz,
    provider_run_id text not null,
    created_at timestamptz not null default now(),
    unique(authority, provider_run_id)
);

create table public.market_access_events (
    access_event_id uuid primary key default gen_random_uuid(),
    actor_principal_id uuid not null references auth.users(id),
    organization_id uuid references public.market_organizations(organization_id),
    resource_type text not null,
    resource_id uuid not null,
    action text not null,
    purpose text not null,
    decision text not null check (decision in ('ALLOWED','DENIED')),
    occurred_at timestamptz not null default now(),
    metadata jsonb not null default '{}'
);
create index market_access_events_resource_idx
    on public.market_access_events(resource_type, resource_id, occurred_at desc);

create table public.market_ai_consents (
    consent_id uuid primary key default gen_random_uuid(),
    principal_id uuid not null references auth.users(id),
    aggregate_type text not null,
    aggregate_id uuid not null,
    purpose text not null,
    consent_version text not null,
    allow_assistance boolean not null,
    allow_training boolean not null default false,
    allow_indexing boolean not null default false,
    granted_at timestamptz not null default now(),
    revoked_at timestamptz,
    unique(principal_id, aggregate_type, aggregate_id, purpose)
);

create table public.market_contracts (
    contract_id uuid primary key default gen_random_uuid(),
    organization_id uuid references public.market_organizations(organization_id),
    aggregate_type text not null,
    aggregate_id uuid not null,
    customer_principal_id uuid not null references auth.users(id),
    provider_principal_id uuid references auth.users(id),
    document_storage_ref text not null,
    document_sha256 text not null check (document_sha256 ~ '^[a-f0-9]{64}$'),
    state text not null default 'DRAFT' check (state in ('DRAFT','OFFERED','SIGNED','VOIDED','COMPLETED')),
    signed_at timestamptz,
    version bigint not null default 0,
    created_at timestamptz not null default now()
);

do $$
declare t text;
begin
  foreach t in array array[
    'market_authority_verification_events','market_access_events',
    'market_ai_consents','market_contracts'
  ] loop
    execute format('alter table public.%I enable row level security', t);
    execute format('revoke all on table public.%I from anon, authenticated', t);
    execute format('grant select on table public.%I to authenticated', t);
  end loop;
end $$;

create policy market_authority_event_subject_read on public.market_authority_verification_events
for select to authenticated using (exists(
  select 1 from public.market_professional_credentials c
  where c.credential_id=market_authority_verification_events.credential_id
    and (c.principal_id=(select auth.uid()) or
      (c.organization_id is not null and market_private.is_org_member(
        c.organization_id,(select auth.uid()),array['OWNER','ADMIN','COMPLIANCE','AUDITOR'])))
));
create policy market_access_event_scoped_read on public.market_access_events
for select to authenticated using (
  actor_principal_id=(select auth.uid()) or
  (organization_id is not null and market_private.is_org_member(
    organization_id,(select auth.uid()),array['OWNER','ADMIN','COMPLIANCE','AUDITOR']))
);
create policy market_ai_consent_self_read on public.market_ai_consents
for select to authenticated using (principal_id=(select auth.uid()));
create policy market_contract_party_read on public.market_contracts
for select to authenticated using (
  customer_principal_id=(select auth.uid()) or provider_principal_id=(select auth.uid()) or
  (organization_id is not null and market_private.is_org_member(organization_id,(select auth.uid()),null))
);

create or replace function public.record_market_ai_consent_v1(
  p_aggregate_type text,p_aggregate_id uuid,p_purpose text,p_consent_version text,
  p_allow_assistance boolean,p_allow_training boolean,p_allow_indexing boolean,p_idempotency_key uuid
) returns jsonb language plpgsql security definer set search_path='' as $$
declare v_actor uuid:=(select auth.uid()); v_id uuid:=gen_random_uuid(); v_result jsonb; v_replay jsonb;
begin
  if v_actor is null then raise exception 'AUTH_REQUIRED' using errcode='28000'; end if;
  v_replay:=market_private.command_replay(p_idempotency_key,v_actor); if v_replay is not null then return v_replay; end if;
  if trim(p_purpose)='' or trim(p_consent_version)='' then raise exception 'INVALID_CONSENT'; end if;
  insert into public.market_ai_consents(consent_id,principal_id,aggregate_type,aggregate_id,purpose,consent_version,allow_assistance,allow_training,allow_indexing)
  values(v_id,v_actor,p_aggregate_type,p_aggregate_id,trim(p_purpose),trim(p_consent_version),p_allow_assistance,p_allow_training,p_allow_indexing)
  on conflict(principal_id,aggregate_type,aggregate_id,purpose) do update set
    consent_version=excluded.consent_version,allow_assistance=excluded.allow_assistance,
    allow_training=excluded.allow_training,allow_indexing=excluded.allow_indexing,
    granted_at=now(),revoked_at=null returning consent_id into v_id;
  v_result:=jsonb_build_object('consent_id',v_id,'allow_assistance',p_allow_assistance,
    'allow_training',p_allow_training,'allow_indexing',p_allow_indexing);
  perform market_private.record_command(p_idempotency_key,v_actor,p_aggregate_type,p_aggregate_id,
    'RECORD_AI_CONSENT',0,jsonb_build_object('purpose',p_purpose,'consent_version',p_consent_version),v_result);
  return v_result;
end $$;

create or replace function public.get_property_exact_address_v1(
  p_property_id uuid,p_purpose text
) returns jsonb language plpgsql security definer set search_path='' as $$
declare v_actor uuid:=(select auth.uid()); v_asset public.property_assets%rowtype; v_allowed boolean:=false;
begin
  if v_actor is null then raise exception 'AUTH_REQUIRED' using errcode='28000'; end if;
  select * into v_asset from public.property_assets where property_id=p_property_id;
  if not found then raise exception 'PROPERTY_NOT_FOUND' using errcode='P0002'; end if;
  v_allowed:=v_asset.created_by=v_actor or exists(
    select 1 from public.property_address_grants g where g.property_id=p_property_id
      and g.grantee_principal_id=v_actor and g.revoked_at is null and g.expires_at>=now()
      and g.purpose=p_purpose
  );
  insert into public.market_access_events(actor_principal_id,resource_type,resource_id,action,purpose,decision)
  values(v_actor,'PROPERTY',p_property_id,'READ_EXACT_ADDRESS',p_purpose,case when v_allowed then 'ALLOWED' else 'DENIED' end);
  if not v_allowed then
    return jsonb_build_object('property_id',p_property_id,'allowed',false,'error','EXACT_ADDRESS_GRANT_REQUIRED');
  end if;
  return jsonb_build_object('property_id',p_property_id,'allowed',true,'exact_address_ciphertext',v_asset.exact_address_ciphertext);
end $$;

create or replace function public.request_legal_document_access_v1(
  p_document_id uuid,p_purpose text
) returns jsonb language plpgsql security definer set search_path='' as $$
declare v_actor uuid:=(select auth.uid()); v_document public.legal_documents%rowtype; v_allowed boolean:=false; v_org uuid;
begin
  if v_actor is null then raise exception 'AUTH_REQUIRED' using errcode='28000'; end if;
  select * into v_document from public.legal_documents where document_id=p_document_id;
  if not found then raise exception 'DOCUMENT_NOT_FOUND' using errcode='P0002'; end if;
  v_allowed:=market_private.has_legal_access(v_document.matter_id,v_actor);
  select e.organization_id into v_org from public.legal_engagements e where e.matter_id=v_document.matter_id;
  insert into public.market_access_events(actor_principal_id,organization_id,resource_type,resource_id,action,purpose,decision)
  values(v_actor,v_org,'LEGAL_DOCUMENT',p_document_id,'REQUEST_DOWNLOAD',p_purpose,case when v_allowed then 'ALLOWED' else 'DENIED' end);
  if not v_allowed then
    return jsonb_build_object('document_id',p_document_id,'allowed',false,'error','LEGAL_DOCUMENT_ACCESS_DENIED');
  end if;
  return jsonb_build_object('document_id',p_document_id,'storage_ref',v_document.storage_ref,
    'sha256',v_document.sha256,'classification',v_document.classification,'allowed',true);
end $$;

create or replace function public.match_legal_professionals_v1(
  p_category_code text,p_jurisdiction text,p_language text,p_budget_minor bigint,p_limit integer default 25
) returns table(principal_id uuid,organization_id uuid,public_display_name text,score integer,reasons jsonb)
language sql stable security definer set search_path='' as $$
  select p.principal_id,p.organization_id,p.public_display_name,
    (50 + least(p.demonstrated_matter_count,25) + case when cap.truth_state='AUTHORITY_VERIFIED' then 25 else 0 end)::integer,
    jsonb_build_array(
      jsonb_build_object('factor','credential','value','CAAB_ACTIVE'),
      jsonb_build_object('factor','category','value',p_category_code),
      jsonb_build_object('factor','jurisdiction','value',p_jurisdiction),
      jsonb_build_object('factor','language','value',p_language),
      jsonb_build_object('factor','budget_minor','value',p_budget_minor),
      jsonb_build_object('factor','demonstrated_matters','value',p.demonstrated_matter_count)
    )
  from public.legal_professional_profiles p
  join public.market_professional_credentials c on c.principal_id=p.principal_id and c.authority='CAAB'
  left join public.market_professional_capabilities cap on cap.principal_id=p.principal_id
    and cap.vertical='LEGAL' and cap.category_code=p_category_code
  where p.accepting_matters and p_category_code=any(p.declared_capabilities)
    and c.status='ACTIVE' and c.checked_at>=now()-interval '30 days'
    and (c.expires_at is null or c.expires_at>=now())
  order by 4 desc,p.public_display_name limit least(greatest(p_limit,1),50)
$$;

-- Callable only through the Supabase service role used by supervised authority
-- providers. The event is immutable and keeps the external evidence digest.
create or replace function public.record_authority_verification_v1(
  p_credential_id uuid,p_source_reference text,p_source_payload_hash text,p_decision text,
  p_observed_at timestamptz,p_valid_until timestamptz,p_provider_run_id text
) returns jsonb language plpgsql security definer set search_path='' as $$
declare v_credential public.market_professional_credentials%rowtype; v_event uuid:=gen_random_uuid();
begin
  select * into v_credential from public.market_professional_credentials where credential_id=p_credential_id for update;
  if not found then raise exception 'CREDENTIAL_NOT_FOUND' using errcode='P0002'; end if;
  if p_decision not in('ACTIVE','SUSPENDED','EXPIRED','REVOKED','NOT_FOUND','MISMATCH')
     or p_source_payload_hash!~'^[a-f0-9]{64}$' or p_observed_at>now()+interval '5 minutes' then
    raise exception 'INVALID_AUTHORITY_OBSERVATION';
  end if;
  insert into public.market_authority_verification_events(verification_event_id,credential_id,authority,
    source_reference,source_payload_hash,decision,observed_at,valid_until,provider_run_id)
  values(v_event,p_credential_id,v_credential.authority,p_source_reference,p_source_payload_hash,
    p_decision,p_observed_at,p_valid_until,p_provider_run_id);
  update public.market_professional_credentials set
    status=case when p_decision='NOT_FOUND' or p_decision='MISMATCH' then 'UNVERIFIED' else p_decision end,
    checked_at=p_observed_at,expires_at=p_valid_until,evidence_ref=p_source_reference,
    source_version=p_provider_run_id,version=version+1
  where credential_id=p_credential_id;
  update public.market_professional_capabilities set
    truth_state=case when p_decision='ACTIVE' then 'AUTHORITY_VERIFIED' else 'DECLARED' end,
    verified_at=case when p_decision='ACTIVE' then p_observed_at else null end,
    expires_at=case when p_decision='ACTIVE' then p_valid_until else null end,version=version+1
  where principal_id=v_credential.principal_id and vertical='LEGAL';
  return jsonb_build_object('verification_event_id',v_event,'status',
    case when p_decision in('NOT_FOUND','MISMATCH') then 'UNVERIFIED' else p_decision end);
end $$;

revoke all on function public.record_market_ai_consent_v1(text,uuid,text,text,boolean,boolean,boolean,uuid) from public,anon;
revoke all on function public.get_property_exact_address_v1(uuid,text) from public,anon;
revoke all on function public.request_legal_document_access_v1(uuid,text) from public,anon;
revoke all on function public.match_legal_professionals_v1(text,text,text,bigint,integer) from public;
revoke all on function public.record_authority_verification_v1(uuid,text,text,text,timestamptz,timestamptz,text) from public,anon,authenticated;
grant execute on function public.record_market_ai_consent_v1(text,uuid,text,text,boolean,boolean,boolean,uuid) to authenticated;
grant execute on function public.get_property_exact_address_v1(uuid,text) to authenticated;
grant execute on function public.request_legal_document_access_v1(uuid,text) to authenticated;
grant execute on function public.match_legal_professionals_v1(text,text,text,bigint,integer) to authenticated;
grant execute on function public.record_authority_verification_v1(uuid,text,text,text,timestamptz,timestamptz,text) to service_role;

-- Storage is configured only where Supabase Storage is installed. Buckets stay
-- private; object paths start with the authoritative matter/property UUID.
do $$
begin
  if to_regclass('storage.buckets') is not null then
    insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types) values
      ('market-legal-vault','market-legal-vault',false,52428800,array['application/pdf','image/jpeg','image/png','audio/mpeg','audio/mp4']),
      ('market-property-media','market-property-media',false,209715200,array['application/pdf','image/jpeg','image/png','video/mp4'])
    on conflict(id) do update set public=false,file_size_limit=excluded.file_size_limit,allowed_mime_types=excluded.allowed_mime_types;
  end if;
end $$;
