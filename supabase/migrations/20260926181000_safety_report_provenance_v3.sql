begin;
-- Required even when the older observatory migration has not been deployed.
-- These are private reporter statements, never documented/public victim counts.
alter table public.safety_reports
    add column if not exists reported_victim_count integer,
    add column if not exists reported_victim_female integer,
    add column if not exists reported_victim_male integer;
alter table safety_private.report_content add column if not exists location_source text not null default 'NONE'
    check (location_source in ('NONE','DEVICE','MAP_SELECTION','USER_DESCRIPTION'));
comment on column safety_private.report_content.location_source is 'Reporter-supplied provenance; never permission to publish exact coordinates.';

-- PostgreSQL text cannot contain NUL. Hash the version prefix separator as bytea.
create or replace function public.safety_create_report_v3(
    p_report_id uuid,
    p_idempotency_key uuid,
    p_category text,
    p_narrative text,
    p_occurred_at timestamptz,
    p_latitude double precision,
    p_longitude double precision,
    p_accuracy_meters real,
    p_source_relation text,
    p_client_payload_sha256 text,
    p_location_source text,
    p_reported_victim_count integer,
    p_reported_victim_female integer,
    p_reported_victim_male integer
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
    if p_location_source is null or p_location_source not in ('NONE','DEVICE','MAP_SELECTION','USER_DESCRIPTION') then
        raise exception 'INVALID_LOCATION_SOURCE';
    end if;
    if p_location_source='NONE' and p_latitude is not null then raise exception 'LOCATION_SOURCE_REQUIRED'; end if;
    if p_location_source in ('DEVICE','MAP_SELECTION') and p_latitude is null then raise exception 'LOCATION_REQUIRED'; end if;
    if p_reported_victim_count < 0 or p_reported_victim_female < 0 or p_reported_victim_male < 0
       or p_reported_victim_count > 100000 or p_reported_victim_female > 100000 or p_reported_victim_male > 100000
       or ((p_reported_victim_female is not null or p_reported_victim_male is not null) and p_reported_victim_count is null)
       or coalesce(p_reported_victim_female,0)+coalesce(p_reported_victim_male,0)>p_reported_victim_count then
        raise exception 'INVALID_VICTIM_DEMOGRAPHICS';
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
        'DOCUMENTARY','JOURNALISTIC','PUBLIC_RECORD','INSTITUTIONAL','UNKNOWN'
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
        'source_relation', p_source_relation,
        'location_source', p_location_source,
        'reported_victim_count', p_reported_victim_count,
        'reported_victim_female', p_reported_victim_female,
        'reported_victim_male', p_reported_victim_male
    );

    -- 4. Server-authoritative SHA-256
    v_server_sha256 := encode(
        extensions.digest(
            convert_to('MEET-SAFETY-REPORT-V3', 'UTF8') || decode('00', 'hex') || convert_to(v_server_payload::text, 'UTF8'),
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
        id, reporter_user_id, category, state, occurred_at, reported_victim_count, reported_victim_female, reported_victim_male
    ) values (
        p_report_id, v_actor, p_category, 'RECEIVED', p_occurred_at, p_reported_victim_count, p_reported_victim_female, p_reported_victim_male
    );

    -- 7. Insert private content
    insert into safety_private.report_content (
        report_id, narrative, source_relation,
        latitude, longitude, accuracy_meters, location_source,
        client_payload_sha256, server_payload_sha256
    ) values (
        p_report_id, trim(p_narrative), p_source_relation,
        p_latitude, p_longitude, p_accuracy_meters, p_location_source,
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
revoke all on function public.safety_create_report_v3(uuid,uuid,text,text,timestamptz,double precision,double precision,real,text,text,text,integer,integer,integer) from public, anon;
grant execute on function public.safety_create_report_v3(uuid,uuid,text,text,timestamptz,double precision,double precision,real,text,text,text,integer,integer,integer) to authenticated;

commit;
