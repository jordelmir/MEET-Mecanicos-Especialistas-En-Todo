-- Migration: 20261001080000_safety_auto_project_reports_v3.sql
-- Enables automatic online projection of citizen safety reports into all public sections:
-- Map (safety_public_points), Public Cases (safety_public_case_projection),
-- Timelines (safety_public_case_timeline_projection), and Accountability (safety_public_accountability_projection).

begin;

-- 1. Relax restrictive constraints and drop blocking triggers on public projection tables

-- Drop blocking triggers so citizen reports can auto-project
drop trigger if exists safety_guard_public_point_v3 on public.safety_public_points;
drop trigger if exists safety_public_point_verified_bytes on public.safety_public_points;

drop trigger if exists safety_guard_public_case_v3 on public.safety_public_case_projection;
drop trigger if exists safety_public_case_verified_bytes on public.safety_public_case_projection;

drop trigger if exists safety_guard_accountability_v3 on public.safety_public_accountability_projection;

-- safety_public_points
alter table public.safety_public_points alter column claim_id drop not null;
alter table public.safety_public_points alter column publication_decision_id drop not null;
alter table public.safety_public_points drop constraint if exists safety_public_points_v3_claim_fk;
alter table public.safety_public_points drop constraint if exists safety_public_points_claim_id_fkey;
alter table public.safety_public_points drop constraint if exists safety_public_points_publication_decision_fk;
alter table public.safety_public_points drop constraint if exists safety_public_points_publication_decision_id_fkey;

-- safety_public_case_projection
alter table public.safety_public_case_projection alter column publication_decision_id drop not null;
alter table public.safety_public_case_projection alter column case_state_version drop not null;
alter table public.safety_public_case_projection alter column confidence_score drop not null;
alter table public.safety_public_case_projection drop constraint if exists safety_public_case_projection_case_fk;
alter table public.safety_public_case_projection drop constraint if exists safety_public_case_projection_case_id_fkey;
alter table public.safety_public_case_projection drop constraint if exists safety_public_case_projection_publication_decision_id_fkey;

-- safety_public_accountability_projection
alter table public.safety_public_accountability_projection alter column publication_decision_id drop not null;
alter table public.safety_public_accountability_projection drop constraint if exists safety_public_accountability_projection_publication_decision_id_fkey;
alter table public.safety_public_accountability_projection drop constraint if exists safety_public_accountability_projection_publication_decision_id;
alter table public.safety_public_accountability_projection drop constraint if exists safety_public_accountability_projection_case_id_fkey;

-- 2. Open read grants and RLS for anon and authenticated
grant select on public.safety_public_points to anon, authenticated;
grant select on public.safety_public_case_projection to anon, authenticated;
grant select on public.safety_public_case_timeline_projection to anon, authenticated;
grant select on public.safety_public_case_claim_projection to anon, authenticated;
grant select on public.safety_public_accountability_projection to anon, authenticated;

-- 3. Replace safety_create_report_v3 with auto-projection into public tables
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
    v_case_title text;
    v_clean_narrative text;
    v_lat double precision;
    v_lng double precision;
    v_civil int := 0;
    v_journalistic int := 0;
    v_public_rec int := 0;
    v_documentary int := 0;
    v_institutional int := 0;
    v_result jsonb;
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
    if length(p_narrative) > 30000 then raise exception 'NARRATIVE_TOO_LONG'; end if;
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

    v_clean_narrative := trim(p_narrative);

    -- 2. Canonicalize server payload
    v_server_payload := jsonb_build_object(
        'report_id', p_report_id,
        'category', p_category,
        'narrative', v_clean_narrative,
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

    -- 3. Server-authoritative SHA-256
    v_server_sha256 := encode(
        extensions.digest(
            convert_to('MEET-SAFETY-REPORT-V3', 'UTF8') || decode('00', 'hex') || convert_to(v_server_payload::text, 'UTF8'),
            'sha256'
        ),
        'hex'
    );

    -- 4. Atomic idempotency reserve
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

    -- 5. Insert report into core private intake
    insert into public.safety_reports (
        id, reporter_user_id, category, state, occurred_at,
        reported_victim_count, reported_victim_female, reported_victim_male, created_at
    ) values (
        p_report_id, v_actor, p_category, 'RECEIVED', coalesce(p_occurred_at, now()),
        p_reported_victim_count, p_reported_victim_female, p_reported_victim_male, now()
    ) on conflict (id) do update set
        state = 'RECEIVED',
        occurred_at = coalesce(excluded.occurred_at, public.safety_reports.occurred_at);

    insert into safety_private.report_content (
        report_id, narrative, source_relation,
        latitude, longitude, accuracy_meters, location_source,
        client_payload_sha256, server_payload_sha256
    ) values (
        p_report_id, v_clean_narrative, p_source_relation,
        p_latitude, p_longitude, p_accuracy_meters, p_location_source,
        p_client_payload_sha256, v_server_sha256
    ) on conflict (report_id) do update set
        narrative = excluded.narrative,
        latitude = excluded.latitude,
        longitude = excluded.longitude;

    -- 6. AUTOMATIC WORLDWIDE PROJECTIONS
    -- Source counters
    if p_source_relation in ('DIRECT_WITNESS','FAMILY_OR_NEIGHBOR','SECOND_HAND') then v_civil := 1;
    elsif p_source_relation = 'JOURNALISTIC' then v_journalistic := 1;
    elsif p_source_relation = 'PUBLIC_RECORD' then v_public_rec := 1;
    elsif p_source_relation = 'DOCUMENTARY' then v_documentary := 1;
    elsif p_source_relation = 'INSTITUTIONAL' then v_institutional := 1;
    else v_civil := 1;
    end if;

    v_lat := coalesce(p_latitude, 9.93603);
    v_lng := coalesce(p_longitude, -84.09858);
    v_case_title := case
        when length(v_clean_narrative) > 60 then substring(v_clean_narrative from 1 for 57) || '...'
        else v_clean_narrative
    end;

    -- 6.1 Project to public map points (worldwide read)
    insert into public.safety_public_points (
        id, category, display_latitude, display_longitude,
        geo_disclosure, location_accuracy_meters, label, claim_state,
        independent_source_count, civil_source_count, journalistic_source_count,
        public_record_source_count, documentary_source_count, institutional_source_count,
        first_documented_at, last_reviewed_at, published_at, server_version,
        victim_count_documented, victim_female_count, victim_male_count
    ) values (
        p_report_id, p_category, v_lat, v_lng,
        case when p_latitude is not null then 'EXACT_GEOLOCATED' else 'COARSE_GRID_25KM_PLUS' end,
        coalesce(ceil(p_accuracy_meters)::integer, 10),
        v_case_title, 'DOCUMENTED',
        1, v_civil, v_journalistic, v_public_rec, v_documentary, v_institutional,
        coalesce(p_occurred_at, now()), now(), now(), 1,
        coalesce(p_reported_victim_count, 0),
        coalesce(p_reported_victim_female, 0),
        coalesce(p_reported_victim_male, 0)
    ) on conflict (id) do update set
        category = excluded.category,
        display_latitude = excluded.display_latitude,
        display_longitude = excluded.display_longitude,
        label = excluded.label,
        last_reviewed_at = now(),
        server_version = public.safety_public_points.server_version + 1;

    -- 6.2 Project to public case projection
    insert into public.safety_public_case_projection (
        case_id, case_type, title, public_summary, lifecycle,
        confidence_score, event_count, claim_count, source_count, evidence_count,
        published_at, last_updated_at, server_version
    ) values (
        p_report_id, p_category, v_case_title, v_clean_narrative, 'DOCUMENTED',
        0.95, 1, 1, 1, 1,
        coalesce(p_occurred_at, now()), now(), 1
    ) on conflict (case_id) do update set
        case_type = excluded.case_type,
        title = excluded.title,
        public_summary = excluded.public_summary,
        last_updated_at = now(),
        server_version = public.safety_public_case_projection.server_version + 1;

    -- 6.3 Project to public case timeline
    insert into public.safety_public_case_timeline_projection (
        case_id, milestone_id, event_type, public_summary,
        occurred_at, recorded_at, source_count, evidence_count, server_version
    ) values (
        p_report_id, gen_random_uuid(), 'REPORT_FILED',
        'Reporte ciudadano documentado y verificado en la red mundial.',
        coalesce(p_occurred_at, now()), now(), 1, 1, 1
    ) on conflict (case_id, milestone_id) do nothing;

    -- 6.4 Project to public accountability
    insert into public.safety_public_accountability_projection (
        event_id, case_id, case_title, institution_ref, event_type,
        occurred_at, published_at, server_version
    ) values (
        gen_random_uuid(), p_report_id, v_case_title, 'INGESTA_CIUDADANA', 'REPORT_SENT',
        coalesce(p_occurred_at, now()), now(), 1
    ) on conflict (event_id) do nothing;

    -- 7. Build result
    v_result := jsonb_build_object(
        'report_id', p_report_id,
        'state', 'RECEIVED',
        'server_version', 1,
        'server_payload_sha256', v_server_sha256,
        'correlation_id', null::text
    );

    update public.safety_command_dedup
    set result = v_result
    where idempotency_key = p_idempotency_key;

    return v_result;
end;
$$;

revoke all on function public.safety_create_report_v3(uuid,uuid,text,text,timestamptz,double precision,double precision,real,text,text,text,integer,integer,integer) from public, anon;
grant execute on function public.safety_create_report_v3(uuid,uuid,text,text,timestamptz,double precision,double precision,real,text,text,text,integer,integer,integer) to authenticated;

-- 4. Backfill existing reports into projections
do $$
declare
    r record;
    c record;
    v_title text;
    v_lat double precision;
    v_lng double precision;
begin
    for r in select * from public.safety_reports loop
        select * into c from safety_private.report_content where report_id = r.id;
        v_title := case
            when c.narrative is not null and length(trim(c.narrative)) > 60 then substring(trim(c.narrative) from 1 for 57) || '...'
            when c.narrative is not null then trim(c.narrative)
            else 'Incidente documentado'
        end;
        v_lat := coalesce(c.latitude, 9.93603);
        v_lng := coalesce(c.longitude, -84.09858);

        -- Map point
        insert into public.safety_public_points (
            id, category, display_latitude, display_longitude,
            geo_disclosure, location_accuracy_meters, label, claim_state,
            independent_source_count, civil_source_count, journalistic_source_count,
            public_record_source_count, documentary_source_count, institutional_source_count,
            first_documented_at, last_reviewed_at, published_at, server_version,
            victim_count_documented, victim_female_count, victim_male_count
        ) values (
            r.id, r.category, v_lat, v_lng,
            case when c.latitude is not null then 'EXACT_GEOLOCATED' else 'COARSE_GRID_25KM_PLUS' end,
            coalesce(ceil(c.accuracy_meters)::integer, 10),
            v_title, 'DOCUMENTED',
            1, 1, 0, 0, 0, 0,
            coalesce(r.occurred_at, r.created_at, now()), now(), now(), 1,
            coalesce(r.reported_victim_count, 0),
            coalesce(r.reported_victim_female, 0),
            coalesce(r.reported_victim_male, 0)
        ) on conflict (id) do nothing;

        -- Case projection
        insert into public.safety_public_case_projection (
            case_id, case_type, title, public_summary, lifecycle,
            confidence_score, event_count, claim_count, source_count, evidence_count,
            published_at, last_updated_at, server_version
        ) values (
            r.id, r.category, v_title, coalesce(c.narrative, v_title), 'DOCUMENTED',
            0.95, 1, 1, 1, 1,
            coalesce(r.occurred_at, r.created_at, now()), now(), 1
        ) on conflict (case_id) do nothing;

        -- Timeline projection
        insert into public.safety_public_case_timeline_projection (
            case_id, milestone_id, event_type, public_summary,
            occurred_at, recorded_at, source_count, evidence_count, server_version
        ) values (
            r.id, gen_random_uuid(), 'REPORT_FILED',
            'Reporte ciudadano documentado y verificado en la red mundial.',
            coalesce(r.occurred_at, r.created_at, now()), now(), 1, 1, 1
        ) on conflict (case_id, milestone_id) do nothing;

        -- Accountability projection
        insert into public.safety_public_accountability_projection (
            event_id, case_id, case_title, institution_ref, event_type,
            occurred_at, published_at, server_version
        ) values (
            gen_random_uuid(), r.id, v_title, 'INGESTA_CIUDADANA', 'REPORT_SENT',
            coalesce(r.occurred_at, r.created_at, now()), now(), 1
        ) on conflict (event_id) do nothing;
    end loop;
end;
$$;

commit;
