-- Durable, reviewable Trust Center evidence and non-recursive parts RLS.

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'trust-verification-evidence', 'trust-verification-evidence', false,
  12582912, array['image/jpeg','image/png','image/webp']
)
on conflict (id) do update set
  public = false,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

create table if not exists public.service_verification_evidence (
  id uuid primary key default gen_random_uuid(),
  application_id uuid not null references public.service_verification_applications(id) on delete cascade,
  applicant_user_id uuid not null,
  evidence_kind text not null check (evidence_kind ~ '^[a-z0-9_]{2,40}$'),
  storage_path text not null,
  content_sha256 text not null check (content_sha256 ~ '^[a-f0-9]{64}$'),
  byte_count bigint not null check (byte_count between 1 and 12582912),
  mime_type text not null check (mime_type in ('image/jpeg','image/png','image/webp')),
  created_at timestamptz not null default now(),
  unique (application_id, evidence_kind),
  unique (storage_path)
);

alter table public.service_verification_evidence enable row level security;
revoke all on public.service_verification_evidence from anon, authenticated;
grant select on public.service_verification_evidence to authenticated;

drop policy if exists service_verification_evidence_read on public.service_verification_evidence;
create policy service_verification_evidence_read
on public.service_verification_evidence for select to authenticated
using (applicant_user_id = auth.uid() or public.meet_is_platform_owner());

drop policy if exists trust_evidence_insert_own on storage.objects;
drop policy if exists trust_evidence_read_authorized on storage.objects;
create policy trust_evidence_insert_own on storage.objects
for insert to authenticated
with check (
  bucket_id = 'trust-verification-evidence'
  and (storage.foldername(name))[1] = auth.uid()::text
);
create policy trust_evidence_read_authorized on storage.objects
for select to authenticated
using (
  bucket_id = 'trust-verification-evidence'
  and (
    (storage.foldername(name))[1] = auth.uid()::text
    or public.meet_is_platform_owner()
  )
);
drop policy if exists trust_evidence_update_own on storage.objects;
create policy trust_evidence_update_own on storage.objects
for update to authenticated
using (
  bucket_id = 'trust-verification-evidence'
  and (storage.foldername(name))[1] = auth.uid()::text
)
with check (
  bucket_id = 'trust-verification-evidence'
  and (storage.foldername(name))[1] = auth.uid()::text
);

create or replace function public.meet_submit_service_verification_v3(
  p_service_type text,
  p_profile_reference text,
  p_display_name text,
  p_business_name text default null,
  p_phone text default null,
  p_location_label text default null,
  p_license_reference text default null,
  p_evidence_manifest_sha256 text default null,
  p_correlation_id uuid default gen_random_uuid(),
  p_evidence jsonb default '[]'::jsonb
) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
  v_actor uuid := (select auth.uid());
  v_receipt jsonb;
  v_application_id uuid;
  v_item jsonb;
  v_kind text;
  v_path text;
  v_required text[];
  v_seen text[] := array[]::text[];
begin
  if v_actor is null then
    raise exception using errcode='42501', message='UNAUTHENTICATED';
  end if;
  if jsonb_typeof(p_evidence) <> 'array' or jsonb_array_length(p_evidence) not between 1 and 20 then
    raise exception using errcode='22023', message='INVALID_EVIDENCE_SET';
  end if;

  v_receipt := public.meet_submit_service_verification_v2(
    p_service_type, p_profile_reference, p_display_name, p_business_name,
    p_phone, p_location_label, p_license_reference,
    p_evidence_manifest_sha256, p_correlation_id
  );
  v_application_id := (v_receipt->>'id')::uuid;

  delete from public.service_verification_evidence where application_id = v_application_id;
  for v_item in select value from jsonb_array_elements(p_evidence) loop
    v_kind := v_item->>'kind';
    v_path := v_item->>'storage_path';
    if v_kind !~ '^[a-z0-9_]{2,40}$'
       or v_path !~ ('^' || v_actor::text || '/[a-z0-9_]+/[a-z0-9_-]+/')
       or coalesce(v_item->>'content_sha256','') !~ '^[a-f0-9]{64}$'
       or coalesce((v_item->>'byte_count')::bigint,0) not between 1 and 12582912
       or coalesce(v_item->>'mime_type','') not in ('image/jpeg','image/png','image/webp')
       or v_kind = any(v_seen)
       or not exists (
         select 1 from storage.objects o
          where o.bucket_id='trust-verification-evidence' and o.name=v_path
       )
    then
      raise exception using errcode='22023', message='INVALID_OR_MISSING_EVIDENCE_OBJECT';
    end if;
    v_seen := array_append(v_seen, v_kind);
    insert into public.service_verification_evidence(
      application_id, applicant_user_id, evidence_kind, storage_path,
      content_sha256, byte_count, mime_type
    ) values (
      v_application_id, v_actor, v_kind, v_path,
      v_item->>'content_sha256', (v_item->>'byte_count')::bigint, v_item->>'mime_type'
    );
  end loop;

  v_required := case upper(p_service_type)
    when 'PASSENGER' then array['profile','id_front','selfie_with_id']::text[]
    when 'RIDE_DRIVER' then array[
      'license_front','license_back','id_front','id_back','criminal_record',
      'marchamo','inspection','insurance','profile','selfie_with_id',
      'selfie_with_license','vehicle_front','vehicle_back','vehicle_interior'
    ]::text[]
    else array[]::text[]
  end;
  if cardinality(v_required) > 0 and not v_required <@ v_seen then
    raise exception using errcode='22023', message='REQUIRED_EVIDENCE_INCOMPLETE';
  end if;
  return v_receipt || jsonb_build_object('evidence_count', cardinality(v_seen));
end;
$$;

create or replace function public.meet_owner_verification_queue_v3(
  p_status text default 'PENDING', p_limit integer default 100
) returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare v_items jsonb; v_counts jsonb;
begin
  if not public.meet_is_platform_owner() then
    raise exception using errcode='42501', message='PLATFORM_OWNER_REQUIRED';
  end if;
  if p_status not in ('ALL','PENDING','APPROVED','REJECTED','SUSPENDED') or p_limit not between 1 and 200 then
    raise exception using errcode='22023', message='INVALID_QUEUE_FILTER';
  end if;
  select jsonb_build_object(
    'PENDING',count(*) filter(where status='PENDING'),
    'APPROVED',count(*) filter(where status='APPROVED'),
    'REJECTED',count(*) filter(where status='REJECTED'),
    'SUSPENDED',count(*) filter(where status='SUSPENDED'),'ALL',count(*)
  ) into v_counts from public.service_verification_applications;
  select coalesce(jsonb_agg(jsonb_build_object(
    'id',a.id,'applicant_user_id',a.applicant_user_id,'applicant_email',u.email,
    'service_type',a.service_type,'profile_reference',a.profile_reference,
    'display_name',a.display_name,'business_name',a.business_name,'phone',a.phone,
    'location_label',a.location_label,'license_reference',a.license_reference,
    'evidence_manifest_sha256',a.evidence_manifest_sha256,'status',a.status,
    'decision_reason',a.decision_reason,'submitted_at',a.submitted_at,
    'reviewed_at',a.reviewed_at,'correlation_id',a.correlation_id,
    'evidence',coalesce((select jsonb_agg(jsonb_build_object(
      'kind',e.evidence_kind,'storage_path',e.storage_path,
      'content_sha256',e.content_sha256,'byte_count',e.byte_count,'mime_type',e.mime_type
    ) order by e.evidence_kind) from public.service_verification_evidence e
      where e.application_id=a.id),'[]'::jsonb)
  ) order by a.submitted_at,a.id),'[]'::jsonb) into v_items
  from (select * from public.service_verification_applications
        where p_status='ALL' or status=p_status
        order by submitted_at,id limit p_limit) a
  join auth.users u on u.id=a.applicant_user_id;
  return jsonb_build_object('items',v_items,'counts',v_counts,'status',p_status,'server_timestamp',now());
end;
$$;

create or replace function public.meet_owner_decide_verification_v3(
  p_application_id uuid, p_decision text, p_reason text
) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare v_type text; v_required text[]; v_actual text[];
begin
  if not public.meet_session_has_aal2() then
    raise exception using errcode='42501', message='AAL2_REQUIRED';
  end if;
  select service_type into strict v_type from public.service_verification_applications
   where id=p_application_id for update;
  if p_decision='APPROVED' then
    v_required := case v_type
      when 'PASSENGER' then array['profile','id_front','selfie_with_id']::text[]
      when 'RIDE_DRIVER' then array[
        'license_front','license_back','id_front','id_back','criminal_record',
        'marchamo','inspection','insurance','profile','selfie_with_id',
        'selfie_with_license','vehicle_front','vehicle_back','vehicle_interior'
      ]::text[] else array[]::text[] end;
    select coalesce(array_agg(e.evidence_kind),'{}') into v_actual
      from public.service_verification_evidence e
      join storage.objects o on o.bucket_id='trust-verification-evidence' and o.name=e.storage_path
     where e.application_id=p_application_id;
    if cardinality(v_required)>0 and not v_required <@ v_actual then
      raise exception using errcode='42501', message='REVIEWABLE_EVIDENCE_REQUIRED';
    end if;
  end if;
  return public.meet_owner_decide_verification_v2(p_application_id,p_decision,p_reason);
end;
$$;

revoke all on function public.meet_submit_service_verification_v3(text,text,text,text,text,text,text,text,uuid,jsonb) from public;
revoke all on function public.meet_owner_verification_queue_v3(text,integer) from public;
revoke all on function public.meet_owner_decide_verification_v3(uuid,text,text) from public;
grant execute on function public.meet_submit_service_verification_v3(text,text,text,text,text,text,text,text,uuid,jsonb) to authenticated;
grant execute on function public.meet_owner_verification_queue_v3(text,integer) to authenticated;
grant execute on function public.meet_owner_decide_verification_v3(uuid,text,text) to authenticated;

-- Break the part_requests <-> part_offers RLS recursion with narrow definer helpers.
create or replace function public.meet_part_request_has_offer_owned_by(p_request_id text, p_actor uuid)
returns boolean language sql stable security definer set search_path='' as $$
  select exists(select 1 from public.part_offers where "partRequestId"=p_request_id and store_owner_id=p_actor)
$$;
create or replace function public.meet_part_request_owned_by(p_request_id text, p_actor uuid)
returns boolean language sql stable security definer set search_path='' as $$
  select exists(select 1 from public.part_requests where "requestId"=p_request_id and customer_id=p_actor)
$$;
create or replace function public.meet_part_request_is_open(p_request_id text)
returns boolean language sql stable security definer set search_path='' as $$
  select exists(select 1 from public.part_requests where "requestId"=p_request_id and status='OPEN')
$$;
revoke all on function public.meet_part_request_has_offer_owned_by(text,uuid) from public;
revoke all on function public.meet_part_request_owned_by(text,uuid) from public;
revoke all on function public.meet_part_request_is_open(text) from public;
grant execute on function public.meet_part_request_has_offer_owned_by(text,uuid) to authenticated;
grant execute on function public.meet_part_request_owned_by(text,uuid) to authenticated;
grant execute on function public.meet_part_request_is_open(text) to authenticated;

drop policy if exists part_requests_read_marketplace on public.part_requests;
create policy part_requests_read_marketplace on public.part_requests for select to authenticated
using (status='OPEN' or customer_id=auth.uid() or public.meet_part_request_has_offer_owned_by("requestId",auth.uid()));
drop policy if exists part_offers_read_participants on public.part_offers;
create policy part_offers_read_participants on public.part_offers for select to authenticated
using (store_owner_id=auth.uid() or public.meet_part_request_owned_by("partRequestId",auth.uid()));
drop policy if exists part_offers_insert_store_owner on public.part_offers;
create policy part_offers_insert_store_owner on public.part_offers for insert to authenticated
with check (store_owner_id=auth.uid() and public.meet_part_request_is_open("partRequestId"));
drop policy if exists part_offers_update_request_customer on public.part_offers;
create policy part_offers_update_request_customer on public.part_offers for update to authenticated
using (public.meet_part_request_owned_by("partRequestId",auth.uid()))
with check (public.meet_part_request_owned_by("partRequestId",auth.uid()));
