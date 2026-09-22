begin;

-- PostgreSQL text cannot contain NUL. Hash the version prefix separator as bytea.
create or replace function public.safety_create_report_v2(
    p_report_id uuid,
    p_idempotency_key uuid,
    p_category text,
    p_narrative text,
    p_occurred_at timestamptz,
    p_latitude double precision,
    p_longitude double precision,
    p_accuracy_meters real,
    p_source_relation text,
    p_client_payload_sha256 text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
set timezone = 'UTC'
as $$
declare
    v_actor uuid;
    v_server_sha256 text;
    v_server_payload jsonb;
    v_reserved uuid;
    v_existing record;
begin
    -- 1. Authenticate
    v_actor := auth.uid();
    if v_actor is null then
        raise exception using
            errcode = '28000',
            message = 'AUTHENTICATION_REQUIRED';
    end if;

    if not exists (select 1 from public.runtime_feature_gates where key = 'safety_reporting' and enabled) then
        raise exception 'SAFETY_REPORTING_DISABLED';
    end if;
    if p_report_id is null or p_idempotency_key is null or p_category is null or p_source_relation is null then
        raise exception 'REQUIRED_ARGUMENT_MISSING';
    end if;
    if p_client_payload_sha256 is null or p_client_payload_sha256 !~ '^[a-f0-9]{64}$' then
        raise exception 'INVALID_CLIENT_DIGEST';
    end if;
    if length(p_narrative) > 10000 then raise exception 'NARRATIVE_TOO_LONG'; end if;
    if (p_latitude is null) <> (p_longitude is null)
       or (p_latitude is not null and not (p_latitude between -90 and 90))
       or (p_longitude is not null and not (p_longitude between -180 and 180))
       or (p_accuracy_meters is not null and not (p_accuracy_meters > 0 and p_accuracy_meters < 'Infinity'::real)) then
        raise exception 'INVALID_LOCATION';
    end if;
    -- 2. Validate
    if p_category not in (
        'HOMICIDE','VIOLENT_INCIDENT','DRUG_SALE_ACTIVITY',
        'THREAT','MISSING_PERSON','INSTITUTIONAL_CONDUCT','OTHER'
    ) then
        raise exception using
            errcode = '22023',
            message = 'INVALID_CATEGORY';
    end if;

    if p_narrative is null or length(trim(p_narrative)) < 10 then
        raise exception using
            errcode = '22023',
            message = 'NARRATIVE_TOO_SHORT';
    end if;

    if p_source_relation not in (
        'DIRECT_WITNESS','FAMILY_OR_NEIGHBOR','SECOND_HAND',
        'DOCUMENTARY','JOURNALISTIC','PUBLIC_RECORD','UNKNOWN'
    ) then
        raise exception using
            errcode = '22023',
            message = 'INVALID_SOURCE_RELATION';
    end if;

    -- 3. Canonicalize server payload (includes Android-provided report_id)
    v_server_payload := jsonb_build_object(
        'report_id', p_report_id,
        'category', p_category,
        'narrative', trim(p_narrative),
        'occurred_at', p_occurred_at,
        'latitude', p_latitude,
        'longitude', p_longitude,
        'accuracy_meters', p_accuracy_meters,
        'source_relation', p_source_relation
    );

    -- 4. Server-authoritative SHA-256
    v_server_sha256 := encode(
        extensions.digest(
            convert_to('MEET-SAFETY-REPORT-V2', 'UTF8') || decode('00', 'hex') || convert_to(v_server_payload::text, 'UTF8'),
            'sha256'
        ),
        'hex'
    );

    -- 5. Atomic idempotency reserve
    insert into public.safety_command_dedup (
        idempotency_key, actor_id, aggregate_id,
        command_type, server_payload_sha256, result
    ) values (
        p_idempotency_key, v_actor, p_report_id,
        'CREATE_REPORT', v_server_sha256, null
    )
    on conflict (idempotency_key) do nothing
    returning idempotency_key into v_reserved;

    if v_reserved is null then
        -- Duplicate key: check for protocol violation
        select * into v_existing
        from public.safety_command_dedup
        where idempotency_key = p_idempotency_key;

        if v_existing.actor_id <> v_actor
           or v_existing.command_type <> 'CREATE_REPORT'
           or v_existing.server_payload_sha256 <> v_server_sha256
        then
            raise exception using
                errcode = '23505',
                message = 'IDEMPOTENCY_PROTOCOL_VIOLATION';
        end if;

        if v_existing.result is null then
            raise exception using
                errcode = '40001',
                message = 'COMMAND_RESERVATION_INCOMPLETE';
        end if;

        return v_existing.result;
    end if;

    -- 6. Insert report (using Android-provided report_id as canonical)
    insert into public.safety_reports (
        id, reporter_user_id, category, state, occurred_at
    ) values (
        p_report_id, v_actor, p_category, 'RECEIVED', p_occurred_at
    );

    -- 7. Insert private content
    insert into safety_private.report_content (
        report_id, narrative, source_relation,
        latitude, longitude, accuracy_meters,
        client_payload_sha256, server_payload_sha256
    ) values (
        p_report_id, trim(p_narrative), p_source_relation,
        p_latitude, p_longitude, p_accuracy_meters,
        p_client_payload_sha256, v_server_sha256
    );

    -- 8. Build result
    declare
        v_result jsonb;
    begin
        v_result := jsonb_build_object(
            'report_id', p_report_id,
            'state', 'RECEIVED',
            'server_version', 1,
            'server_payload_sha256', v_server_sha256,
            'correlation_id', null::text
        );

        -- 9. Persist result in dedup
        update public.safety_command_dedup
        set result = v_result
        where idempotency_key = p_idempotency_key;

        return v_result;
    end;
end;
$$;
revoke all on function public.safety_create_report_v2(uuid,uuid,text,text,timestamptz,double precision,double precision,real,text,text) from public, anon;
grant execute on function public.safety_create_report_v2(uuid,uuid,text,text,timestamptz,double precision,double precision,real,text,text) to authenticated;
-- Reports enter only via the validated/idempotent RPC.
revoke insert, update, delete on public.safety_reports from authenticated;

-- Challenges are append-only counterclaims. They never mutate the published claim.
create table if not exists public.safety_counterclaims (
    id uuid primary key,
    case_id uuid not null references public.safety_cases(id) on delete restrict,
    claim_id uuid references public.safety_claims(id) on delete restrict,
    submitter_user_id uuid not null,
    kind text not null check (kind in ('REPORT_ERROR','CONTRARY_EVIDENCE','RECTIFICATION')),
    narrative text not null check (length(trim(narrative)) between 10 and 5000),
    source_url text,
    client_payload_sha256 text not null check (client_payload_sha256 ~ '^[a-f0-9]{64}$'),
    state text not null default 'RECEIVED' check (state in ('RECEIVED','UNDER_REVIEW','ACCEPTED','REJECTED','SUPERSEDED')),
    state_version bigint not null default 1 check (state_version > 0),
    created_at timestamptz not null default now()
);
alter table public.safety_counterclaims enable row level security;
revoke all on public.safety_counterclaims from anon, authenticated;
grant select on public.safety_counterclaims to authenticated;
create policy safety_counterclaims_owner_read on public.safety_counterclaims
for select to authenticated using (submitter_user_id = (select auth.uid()));

create or replace function public.safety_submit_counterclaim_v1(
    p_idempotency_key uuid,
    p_case_id uuid,
    p_claim_id uuid,
    p_kind text,
    p_narrative text,
    p_source_url text,
    p_client_payload_sha256 text
) returns jsonb
language plpgsql security definer set search_path = ''
as $$
declare
    v_actor uuid := auth.uid();
    v_counterclaim_id uuid := gen_random_uuid();
    v_digest text;
    v_existing record;
    v_result jsonb;
begin
    if v_actor is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
    if not exists (select 1 from public.runtime_feature_gates where key='safety_foundation' and enabled) then
        raise exception 'SAFETY_FOUNDATION_DISABLED';
    end if;
    if p_kind not in ('REPORT_ERROR','CONTRARY_EVIDENCE','RECTIFICATION') then raise exception 'INVALID_CHALLENGE_KIND'; end if;
    if length(trim(p_narrative)) not between 10 and 5000 then raise exception 'INVALID_CHALLENGE_LENGTH'; end if;
    if p_source_url is not null and p_source_url !~ '^https?://[^[:space:]]+$' then raise exception 'INVALID_SOURCE_URL'; end if;
    if p_client_payload_sha256 !~ '^[a-f0-9]{64}$' then raise exception 'INVALID_CLIENT_DIGEST'; end if;
    if not exists (select 1 from public.safety_public_case_projection where case_id=p_case_id) then raise exception 'CASE_NOT_PUBLIC'; end if;
    if p_claim_id is not null and not exists (
        select 1 from public.safety_public_case_claim_projection where case_id=p_case_id and claim_id=p_claim_id
    ) then raise exception 'CLAIM_NOT_PUBLIC_IN_CASE'; end if;

    v_digest := encode(extensions.digest(
        convert_to('MEET-SAFETY-COUNTERCLAIM-V1','UTF8') || decode('00','hex') ||
        convert_to(jsonb_build_object('case_id',p_case_id,'claim_id',p_claim_id,'kind',p_kind,
            'narrative',trim(p_narrative),'source_url',p_source_url)::text,'UTF8'), 'sha256'), 'hex');
    insert into public.safety_command_dedup(idempotency_key,actor_id,aggregate_id,command_type,server_payload_sha256,result)
    values(p_idempotency_key,v_actor,p_case_id,'SUBMIT_COUNTERCLAIM',v_digest,null)
    on conflict(idempotency_key) do nothing;
    if not found then
        select * into v_existing from public.safety_command_dedup where idempotency_key=p_idempotency_key;
        if v_existing.actor_id<>v_actor or v_existing.aggregate_id<>p_case_id
           or v_existing.command_type<>'SUBMIT_COUNTERCLAIM' or v_existing.server_payload_sha256<>v_digest then
            raise exception 'IDEMPOTENCY_PROTOCOL_VIOLATION';
        end if;
        if v_existing.result is null then raise exception 'COMMAND_RESERVATION_INCOMPLETE'; end if;
        return v_existing.result;
    end if;
    insert into public.safety_counterclaims(id,case_id,claim_id,submitter_user_id,kind,narrative,source_url,client_payload_sha256)
    values(v_counterclaim_id,p_case_id,p_claim_id,v_actor,p_kind,trim(p_narrative),p_source_url,p_client_payload_sha256);
    v_result := jsonb_build_object('counterclaim_id',v_counterclaim_id,'state','RECEIVED','server_version',1,'correlation_id',null);
    update public.safety_command_dedup set result=v_result where idempotency_key=p_idempotency_key;
    return v_result;
end;
$$;
revoke all on function public.safety_submit_counterclaim_v1(uuid,uuid,uuid,text,text,text,text) from public, anon;
grant execute on function public.safety_submit_counterclaim_v1(uuid,uuid,uuid,text,text,text,text) to authenticated;

create or replace function public.safety_gate_evidence_insert() returns trigger
language plpgsql set search_path = '' as $$
begin
    if auth.uid() is not null and not exists (
        select 1 from public.runtime_feature_gates where key='safety_evidence_upload' and enabled
    ) then raise exception 'SAFETY_EVIDENCE_UPLOAD_DISABLED'; end if;
    return new;
end;
$$;
revoke all on function public.safety_gate_evidence_insert() from public;
create trigger safety_evidence_feature_gate before insert on public.safety_evidence_objects
for each row execute function public.safety_gate_evidence_insert();

-- Original evidence and custody events are append-only, including service-role access.
create or replace function public.safety_reject_evidence_mutation() returns trigger
language plpgsql set search_path = '' as $$
begin raise exception 'SAFETY_EVIDENCE_IMMUTABLE'; end;
$$;
revoke all on function public.safety_reject_evidence_mutation() from public;
create trigger safety_evidence_immutable before update or delete on public.safety_evidence_objects
for each row execute function public.safety_reject_evidence_mutation();
create trigger safety_custody_immutable before update or delete on public.safety_evidence_custody
for each row execute function public.safety_reject_evidence_mutation();

-- Complete the projection publication; no private tables are added.
do $$ declare t text; begin
foreach t in array array['safety_public_case_timeline_projection','safety_public_case_claim_projection'] loop
 if not exists(select 1 from pg_publication_tables where pubname='supabase_realtime' and schemaname='public' and tablename=t) then
  execute format('alter publication supabase_realtime add table public.%I',t);
 end if;
end loop;
end $$;
commit;
