begin;

-- Receipt records are immutable. Byte verification is a separate server ledger.
create table public.safety_evidence_verifications (
    id uuid primary key default gen_random_uuid(),
    evidence_id uuid not null references public.safety_evidence_objects(id) on delete restrict,
    verification_state text not null check (verification_state in ('MATCH','MISMATCH','QUARANTINED','ERROR')),
    declared_sha256 text not null check (declared_sha256 ~ '^[a-f0-9]{64}$'),
    server_sha256 text check (server_sha256 ~ '^[a-f0-9]{64}$'),
    declared_byte_count bigint not null check (declared_byte_count > 0),
    server_byte_count bigint check (server_byte_count >= 0),
    verification_method text not null check (verification_method = 'STORAGE_BYTES_SHA256'),
    verifier_version text not null check (length(verifier_version) between 1 and 80),
    verified_at timestamptz not null default now(),
    check (verification_state not in ('MATCH','MISMATCH') or
           (server_sha256 is not null and server_byte_count is not null)),
    check (verification_state <> 'MATCH' or
           (server_sha256 = declared_sha256 and server_byte_count = declared_byte_count))
);
create index safety_evidence_verifications_evidence on public.safety_evidence_verifications(evidence_id, verified_at);
alter table public.safety_evidence_verifications enable row level security;
revoke all on public.safety_evidence_verifications from public, anon, authenticated, service_role;
grant select on public.safety_evidence_verifications to service_role, authenticated;
create policy safety_verifications_owner_read on public.safety_evidence_verifications
for select to authenticated using (exists (
    select 1 from public.safety_evidence_objects e
    where e.id = evidence_id and e.uploader_user_id = auth.uid()
));
create trigger safety_evidence_verifications_immutable before update or delete
on public.safety_evidence_verifications for each row execute function public.safety_reject_evidence_mutation();

-- Sequence is authoritative; random UUID ordering and wall-clock order are not.
alter table public.safety_evidence_custody add column custody_sequence bigint;
alter table public.safety_evidence_custody add column verification_id uuid
    references public.safety_evidence_verifications(id) on delete restrict;
alter table public.safety_evidence_custody add column canonical_version text;
alter table public.safety_evidence_custody add column server_sha256 text;
create unique index safety_custody_sequence_unique
on public.safety_evidence_custody(evidence_id, custody_sequence) where custody_sequence is not null;
create unique index safety_custody_verification_unique
on public.safety_evidence_custody(verification_id) where verification_id is not null;
revoke insert, update, delete on public.safety_evidence_custody from service_role;

create or replace function public.safety_record_evidence_verification_v2(
    p_evidence_id uuid, p_state text, p_server_sha256 text,
    p_server_byte_count bigint, p_verifier_version text
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
    v_evidence public.safety_evidence_objects%rowtype;
    v_existing public.safety_evidence_verifications%rowtype;
    v_verification_id uuid := gen_random_uuid();
    v_event_id uuid := gen_random_uuid();
    v_previous_hash text;
    v_sequence bigint;
    v_now timestamptz := clock_timestamp();
    v_event_type text;
    v_reason text;
    v_hash text;
begin
    select * into v_evidence from public.safety_evidence_objects
    where id = p_evidence_id for update;
    if not found then raise exception 'EVIDENCE_NOT_FOUND'; end if;
    if p_state is null or p_state not in ('MATCH','MISMATCH','QUARANTINED','ERROR')
       or p_verifier_version is null or length(p_verifier_version) not between 1 and 80 then
        raise exception 'INVALID_VERIFICATION';
    end if;
    if p_state in ('MATCH','MISMATCH') and
       (p_server_sha256 is null or p_server_sha256 !~ '^[a-f0-9]{64}$'
        or p_server_byte_count is null or p_server_byte_count < 0) then
        raise exception 'INVALID_SERVER_DIGEST';
    end if;
    if p_state = 'MATCH' and (p_server_sha256 <> v_evidence.content_sha256
        or p_server_byte_count <> v_evidence.byte_count) then
        raise exception 'FALSE_MATCH_REJECTED';
    end if;
    if p_state = 'MISMATCH' and p_server_sha256 = v_evidence.content_sha256
       and p_server_byte_count = v_evidence.byte_count then
        raise exception 'FALSE_MISMATCH_REJECTED';
    end if;
    -- Quarantine is sticky. A previous mismatch needs explicit review, never a silent MATCH.
    if p_state = 'MATCH' and exists (select 1 from public.safety_evidence_verifications
        where evidence_id = p_evidence_id and verification_state in ('MISMATCH','QUARANTINED')) then
        raise exception 'EVIDENCE_QUARANTINED_REVIEW_REQUIRED';
    end if;
    -- Repeated identical verification returns the same receipt, under the row lock.
    select * into v_existing from public.safety_evidence_verifications
    where evidence_id = p_evidence_id and verification_state = p_state
      and server_sha256 is not distinct from p_server_sha256
      and server_byte_count is not distinct from p_server_byte_count
      and verifier_version = p_verifier_version order by verified_at desc limit 1;
    if found then return jsonb_build_object('ok',true,'verification_id',v_existing.id,
        'verification_state',v_existing.verification_state); end if;
    insert into public.safety_evidence_verifications(id,evidence_id,verification_state,
        declared_sha256,server_sha256,declared_byte_count,server_byte_count,
        verification_method,verifier_version,verified_at)
    values(v_verification_id,p_evidence_id,p_state,v_evidence.content_sha256,
        p_server_sha256,v_evidence.byte_count,p_server_byte_count,
        'STORAGE_BYTES_SHA256',p_verifier_version,v_now);
    select coalesce(max(custody_sequence),0)+1 into v_sequence
    from public.safety_evidence_custody where evidence_id = p_evidence_id;
    select event_hash into v_previous_hash from public.safety_evidence_custody
    where evidence_id = p_evidence_id order by custody_sequence desc nulls last, created_at desc, id desc limit 1;
    v_event_type := case p_state when 'MATCH' then 'SERVER_VERIFIED'
        when 'MISMATCH' then 'SERVER_MISMATCH' when 'QUARANTINED' then 'QUARANTINED'
        else 'VERIFICATION_ERROR' end;
    v_reason := case p_state when 'MATCH' then 'BYTE_MATCH' when 'MISMATCH' then 'BYTE_MISMATCH'
        when 'QUARANTINED' then 'SIZE_OR_POLICY_LIMIT' else 'STORAGE_READ_FAILED' end;
    v_hash := encode(extensions.digest(convert_to(concat_ws(chr(31),
        'MEET-SAFETY-CUSTODY-V2',v_event_id::text,p_evidence_id::text,
        coalesce(v_previous_hash,''),v_event_type,'SERVER',v_reason,
        coalesce(p_server_sha256,''),
        to_char(v_now at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"')), 'UTF8'),'sha256'),'hex');
    insert into public.safety_evidence_custody(id,evidence_id,event_type,actor_id,
        reason_code,created_at,previous_event_hash,event_hash,custody_sequence,
        verification_id,canonical_version,server_sha256)
    values(v_event_id,p_evidence_id,v_event_type,null,v_reason,v_now,
        v_previous_hash,v_hash,v_sequence,v_verification_id,'MEET-SAFETY-CUSTODY-V2',p_server_sha256);
    if p_state in ('MISMATCH','QUARANTINED') then
        insert into safety_private.claim_reevaluation_v3(claim_id,report_id,reason_code,status)
        select c.id,c.report_id,'EVIDENCE_QUARANTINED','PENDING' from public.safety_claims c
        where c.report_id = v_evidence.report_id or exists (
          select 1 from public.safety_claim_sources cs join public.safety_sources src on src.id=cs.source_id
          where cs.claim_id=c.id and src.report_id=v_evidence.report_id)
        on conflict(claim_id) do update set reason_code=excluded.reason_code,status='PENDING',requested_at=now();
        delete from public.safety_public_points where claim_id in (
            select c.id from public.safety_claims c where c.report_id=v_evidence.report_id
              or exists(select 1 from public.safety_claim_sources cs join public.safety_sources src on src.id=cs.source_id
                        where cs.claim_id=c.id and src.report_id=v_evidence.report_id));
        delete from public.safety_public_case_projection where case_id in (
            select ce.case_id from public.safety_case_events ce
            join public.safety_events ev on ev.id=ce.event_id
            left join public.safety_event_claims ec on ec.event_id=ev.id
            left join public.safety_claims c on c.id=ec.claim_id
            left join public.safety_claim_sources cs on cs.claim_id=c.id
            left join public.safety_sources src on src.id=cs.source_id
            where ev.report_id=v_evidence.report_id or c.report_id=v_evidence.report_id or src.report_id=v_evidence.report_id);
    end if;
    return jsonb_build_object('ok',true,'verification_id',v_verification_id,
        'verification_state',p_state,'custody_event_hash',v_hash,'custody_sequence',v_sequence);
end;
$$;
revoke all on function public.safety_record_evidence_verification_v2(uuid,text,text,bigint,text)
from public, anon, authenticated;
grant execute on function public.safety_record_evidence_verification_v2(uuid,text,text,bigint,text) to service_role;

-- Publication sees verified bytes only. A receipt or an old MATCH followed by
-- quarantine can never qualify as verified evidence.
create or replace function public.safety_evidence_is_verified_v2(p_evidence_id uuid)
returns boolean language sql stable security definer set search_path = '' as $$
    select exists(select 1 from public.safety_evidence_verifications
                  where evidence_id=p_evidence_id and verification_state='MATCH')
       and not exists(select 1 from public.safety_evidence_verifications
                      where evidence_id=p_evidence_id and verification_state in ('MISMATCH','QUARANTINED'))
$$;
revoke all on function public.safety_evidence_is_verified_v2(uuid) from public, anon, authenticated;
grant execute on function public.safety_evidence_is_verified_v2(uuid) to service_role;

-- Serializes initial receipt registration too, so an upload-response replay
-- cannot create two genesis custody events.
alter function public.safety_register_evidence_v1(uuid,uuid,text,text,text,bigint,timestamptz)
rename to safety_register_evidence_internal_v1;
revoke all on function public.safety_register_evidence_internal_v1(uuid,uuid,text,text,text,bigint,timestamptz)
from public,anon,authenticated,service_role;
create function public.safety_register_evidence_v1(p_evidence_id uuid,p_report_id uuid,
    p_storage_path text,p_content_sha256 text,p_mime_type text,p_byte_count bigint,
    p_captured_at timestamptz default null)
returns jsonb language plpgsql security definer set search_path = '' as $$
begin
    if auth.uid() is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
    perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(p_evidence_id::text,0));
    return public.safety_register_evidence_internal_v1(p_evidence_id,p_report_id,
        p_storage_path,p_content_sha256,p_mime_type,p_byte_count,p_captured_at);
end;
$$;
revoke all on function public.safety_register_evidence_v1(uuid,uuid,text,text,text,bigint,timestamptz)
from public,anon,service_role;
grant execute on function public.safety_register_evidence_v1(uuid,uuid,text,text,text,bigint,timestamptz) to authenticated;

-- Byte proof is required for every attachment supporting publication. Sources
-- without a private attachment still use the independently reviewed V3 provenance.
create function public.safety_publication_verified_bytes_guard_v2()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
    if tg_table_name='safety_public_points' then
        perform 1 from public.safety_evidence_objects e
        where e.report_id in (
            select c.report_id from public.safety_claims c where c.id=new.claim_id
            union select src.report_id from public.safety_claim_sources cs
                  join public.safety_sources src on src.id=cs.source_id where cs.claim_id=new.claim_id)
        for share;
        if exists(select 1 from public.safety_evidence_objects e
            where e.report_id in (
              select c.report_id from public.safety_claims c where c.id=new.claim_id
              union select src.report_id from public.safety_claim_sources cs
                join public.safety_sources src on src.id=cs.source_id where cs.claim_id=new.claim_id)
            and not public.safety_evidence_is_verified_v2(e.id)) then
            raise exception 'PUBLICATION_UNVERIFIED_EVIDENCE';
        end if;
    else
        perform 1 from public.safety_evidence_objects e where e.report_id in (
            select ev.report_id from public.safety_case_events ce join public.safety_events ev on ev.id=ce.event_id where ce.case_id=new.case_id
            union select c.report_id from public.safety_case_events ce join public.safety_event_claims ec on ec.event_id=ce.event_id
                  join public.safety_claims c on c.id=ec.claim_id where ce.case_id=new.case_id
            union select src.report_id from public.safety_case_events ce join public.safety_event_claims ec on ec.event_id=ce.event_id
                  join public.safety_claim_sources cs on cs.claim_id=ec.claim_id join public.safety_sources src on src.id=cs.source_id where ce.case_id=new.case_id
        ) for share;
        if exists(select 1 from public.safety_evidence_objects e where e.report_id in (
            select ev.report_id from public.safety_case_events ce join public.safety_events ev on ev.id=ce.event_id where ce.case_id=new.case_id
            union select c.report_id from public.safety_case_events ce join public.safety_event_claims ec on ec.event_id=ce.event_id
                  join public.safety_claims c on c.id=ec.claim_id where ce.case_id=new.case_id
            union select src.report_id from public.safety_case_events ce join public.safety_event_claims ec on ec.event_id=ce.event_id
                  join public.safety_claim_sources cs on cs.claim_id=ec.claim_id join public.safety_sources src on src.id=cs.source_id where ce.case_id=new.case_id
        ) and not public.safety_evidence_is_verified_v2(e.id)) then
            raise exception 'PUBLICATION_UNVERIFIED_EVIDENCE';
        end if;
    end if;
    return new;
end;
$$;
revoke all on function public.safety_publication_verified_bytes_guard_v2() from public,anon,authenticated,service_role;
create trigger safety_public_point_verified_bytes before insert or update
on public.safety_public_points for each row execute function public.safety_publication_verified_bytes_guard_v2();
create trigger safety_public_case_verified_bytes before insert or update
on public.safety_public_case_projection for each row execute function public.safety_publication_verified_bytes_guard_v2();

-- A new unverified attachment changes publication support immediately; no
-- published projection may survive waiting for a client to ask for hashing.
create function public.safety_invalidate_new_evidence_v2()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
        insert into safety_private.claim_reevaluation_v3(claim_id,report_id,reason_code,status)
        select c.id,c.report_id,'EVIDENCE_VERIFICATION_PENDING','PENDING' from public.safety_claims c
        where c.report_id = new.report_id or exists (
          select 1 from public.safety_claim_sources cs join public.safety_sources src on src.id=cs.source_id
          where cs.claim_id=c.id and src.report_id=new.report_id)
        on conflict(claim_id) do update set reason_code=excluded.reason_code,status='PENDING',requested_at=now();
        delete from public.safety_public_points where claim_id in (
            select c.id from public.safety_claims c where c.report_id=new.report_id
              or exists(select 1 from public.safety_claim_sources cs join public.safety_sources src on src.id=cs.source_id
                        where cs.claim_id=c.id and src.report_id=new.report_id));
        delete from public.safety_public_case_projection where case_id in (
            select ce.case_id from public.safety_case_events ce
            join public.safety_events ev on ev.id=ce.event_id
            left join public.safety_event_claims ec on ec.event_id=ev.id
            left join public.safety_claims c on c.id=ec.claim_id
            left join public.safety_claim_sources cs on cs.claim_id=c.id
            left join public.safety_sources src on src.id=cs.source_id
            where ev.report_id=new.report_id or c.report_id=new.report_id or src.report_id=new.report_id);
    return new;
end;
$$;
revoke all on function public.safety_invalidate_new_evidence_v2() from public,anon,authenticated,service_role;
create trigger safety_invalidate_new_evidence_v2 after insert on public.safety_evidence_objects
for each row execute function public.safety_invalidate_new_evidence_v2();
commit;
