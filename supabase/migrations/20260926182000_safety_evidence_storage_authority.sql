begin;

-- Standard private storage, not recipient-only encryption. Keep octet-stream
-- uploads compatible until Android supplies an explicit HTTP Content-Type.
update storage.buckets set public = false, file_size_limit = 20971520
where id = 'safety-evidence-original';

-- Policy helper only; the existing registration RPC remains the custody authority.
create or replace function public.safety_can_upload_original(p_path text)
returns boolean language plpgsql security definer set search_path = '' as $$
declare v_actor uuid := auth.uid(); v_parts text[]; v_report uuid;
begin
    if v_actor is null or not exists (
        select 1 from public.runtime_feature_gates where key='safety_foundation' and enabled
    ) or not exists (
        select 1 from public.runtime_feature_gates where key='safety_evidence_upload' and enabled
    ) then return false; end if;
    if p_path is null or p_path !~ '^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\.original$' then return false; end if;
    v_parts := string_to_array(p_path,'/');
    if v_parts[1] <> v_actor::text then return false; end if;
    v_report := v_parts[2]::uuid;
    return exists(select 1 from public.safety_reports where id=v_report and reporter_user_id=v_actor);
end $$;
revoke all on function public.safety_can_upload_original(text) from public, anon;
grant execute on function public.safety_can_upload_original(text) to authenticated;
drop policy if exists "Authenticated users can upload safety evidence originals" on storage.objects;
create policy "Authenticated users can upload safety evidence originals" on storage.objects
for insert to authenticated with check (
    bucket_id='safety-evidence-original'
    and coalesce(owner_id,owner::text) = auth.uid()::text
    and public.safety_can_upload_original(name)
);

create or replace function public.safety_register_evidence_v1(
    p_evidence_id uuid, p_report_id uuid, p_storage_path text,
    p_content_sha256 text, p_mime_type text, p_byte_count bigint,
    p_captured_at timestamptz
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
    v_actor uuid := auth.uid(); v_expected_path text; v_object record;
    v_existing public.safety_evidence_objects%rowtype;
    v_event_id uuid; v_event_hash text; v_size bigint; v_transport_mime text;
begin
    if v_actor is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
    if not exists(select 1 from public.runtime_feature_gates where key='safety_foundation' and enabled)
       or not exists(select 1 from public.runtime_feature_gates where key='safety_evidence_upload' and enabled)
    then raise exception 'SAFETY_EVIDENCE_UPLOAD_DISABLED'; end if;
    if p_evidence_id is null or p_report_id is null then raise exception 'REQUIRED_ARGUMENT_MISSING'; end if;
    -- Serializes quota, replay and custody creation for every evidence of this report.
    perform 1 from public.safety_reports where id=p_report_id and reporter_user_id=v_actor for update;
    if not found then raise exception 'SAFETY_REPORT_NOT_OWNED'; end if;
    if p_content_sha256 is null or p_content_sha256 !~ '^[a-f0-9]{64}$' then raise exception 'INVALID_SHA256'; end if;
    if p_byte_count is null or p_byte_count <= 0 or p_byte_count > 20971520 then raise exception 'INVALID_BYTE_COUNT'; end if;
    if p_mime_type is null or p_mime_type not in (
        'image/jpeg','image/png','image/webp','video/mp4','video/webm',
        'audio/mpeg','audio/mp4','audio/aac','audio/ogg','application/pdf'
    ) then raise exception 'UNSUPPORTED_MIME_TYPE'; end if;
    v_expected_path := v_actor::text || '/' || p_report_id::text || '/' || p_evidence_id::text || '.original';
    if p_storage_path is distinct from v_expected_path then raise exception 'INVALID_STORAGE_PATH'; end if;
    select coalesce(owner_id,owner::text) as principal_owner, metadata into v_object from storage.objects
    where bucket_id='safety-evidence-original' and name=p_storage_path for share;
    if not found then raise exception 'EVIDENCE_OBJECT_NOT_FOUND'; end if;
    if v_object.principal_owner is distinct from v_actor::text then raise exception 'EVIDENCE_OBJECT_NOT_OWNED'; end if;
    if v_object.metadata->>'size' is null or (v_object.metadata->>'size') !~ '^[0-9]{1,9}$'
    then raise exception 'EVIDENCE_OBJECT_METADATA_INVALID'; end if;
    v_size := (v_object.metadata->>'size')::bigint;
    if v_size <> p_byte_count or v_size <= 0 or v_size > 20971520 then raise exception 'EVIDENCE_OBJECT_SIZE_MISMATCH'; end if;
    v_transport_mime := lower(split_part(coalesce(v_object.metadata->>'mimetype',''),';',1));
    -- Legacy worker selects .original, for which Storage may infer octet-stream.
    -- This verifies transport metadata, not magic bytes nor the claimed content digest.
    if v_transport_mime not in (p_mime_type,'application/octet-stream') then raise exception 'EVIDENCE_OBJECT_MIME_MISMATCH'; end if;
    select * into v_existing from public.safety_evidence_objects where id=p_evidence_id;
    if found then
        if v_existing.report_id is distinct from p_report_id
           or v_existing.uploader_user_id is distinct from v_actor
           or v_existing.storage_path is distinct from p_storage_path
           or v_existing.content_sha256 is distinct from p_content_sha256
           or v_existing.mime_type is distinct from p_mime_type
           or v_existing.byte_count is distinct from p_byte_count
           or v_existing.captured_at is distinct from p_captured_at
        then raise exception 'EVIDENCE_IDEMPOTENCY_VIOLATION'; end if;
    else
        if (select count(*) from public.safety_evidence_objects where report_id=p_report_id) >= 5
        then raise exception 'EVIDENCE_ATTACHMENT_LIMIT'; end if;
        insert into public.safety_evidence_objects(id,report_id,uploader_user_id,storage_path,content_sha256,mime_type,byte_count,captured_at)
        values(p_evidence_id,p_report_id,v_actor,p_storage_path,p_content_sha256,p_mime_type,p_byte_count,p_captured_at);
    end if;
    if not exists(select 1 from public.safety_evidence_custody where evidence_id=p_evidence_id and event_type='SERVER_RECEIVED') then
        v_event_id := gen_random_uuid();
        v_event_hash := encode(extensions.digest(convert_to(concat_ws(chr(31),'MEET-SAFETY-CUSTODY-V1',v_event_id::text,p_evidence_id::text,'SERVER_RECEIVED',v_actor::text,p_content_sha256),'UTF8'),'sha256'),'hex');
        insert into public.safety_evidence_custody(id,evidence_id,event_type,actor_id,reason_code,previous_event_hash,event_hash)
        values(v_event_id,p_evidence_id,'SERVER_RECEIVED',v_actor,'ORIGINAL_UPLOAD',null,v_event_hash);
    end if;
    return jsonb_build_object('ok',true,'evidence_id',p_evidence_id,'storage_path',p_storage_path,'content_sha256',p_content_sha256,
        'byte_count',p_byte_count,'mime_type',p_mime_type,'protection_profile','STANDARD_PRIVATE_STORAGE',
        'integrity_level','CLIENT_DIGEST_REGISTERED','storage_metadata_checked',true);
end $$;
revoke all on function public.safety_register_evidence_v1(uuid,uuid,text,text,text,bigint,timestamptz) from public, anon;
grant execute on function public.safety_register_evidence_v1(uuid,uuid,text,text,text,bigint,timestamptz) to authenticated;
commit;
