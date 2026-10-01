begin;

-- V3 decisions are append-only. Historical report decisions remain labelled
-- LEGACY; they cannot authorize a V3 public projection.
alter table public.safety_publication_decisions
    add column if not exists claim_id uuid references public.safety_claims(id) on delete restrict,
    add column if not exists case_id uuid references public.safety_cases(id) on delete restrict,
    add column if not exists supersedes_decision_id uuid
        references public.safety_publication_decisions(id) on delete restrict,
    add column if not exists reason_code text,
    add column if not exists public_summary text,
    add column if not exists policy_version text,
    add column if not exists decision_phase text not null default 'LEGACY',
    add column if not exists recommendation text,
    add column if not exists claim_state_version bigint,
    add column if not exists report_state_version bigint;
alter table public.safety_publication_decisions alter column report_id drop not null;
alter table public.safety_publication_decisions
    add constraint safety_publication_v3_one_subject_chk
    check (num_nonnulls(report_id, claim_id, case_id) = 1),
    add constraint safety_publication_v3_phase_chk
    check (decision_phase in ('LEGACY', 'CANDIDATE', 'FINAL')),
    add constraint safety_publication_v3_recommendation_chk
    check (recommendation is null or recommendation in
        ('READY_TO_PUBLISH', 'READY_TO_REDACT', 'HOLD', 'REJECT')),
    add constraint safety_publication_v3_authority_chk
    check (decision_phase = 'LEGACY' or (
        claim_id is not null and reviewer_id is not null
        and reason_code is not null and char_length(reason_code) between 3 and 80
        and policy_version = 'SAFETY-PUBLICATION-V3'
        and claim_state_version is not null and claim_state_version > 0
        and report_state_version is not null and report_state_version > 0
        and (
            (decision_phase = 'CANDIDATE' and recommendation is not null
             and supersedes_decision_id is null and decision in ('HOLD', 'REJECT'))
            or
            (decision_phase = 'FINAL' and recommendation is null
             and supersedes_decision_id is not null
             and decision in ('PUBLISH', 'REDACT', 'REJECT'))
        )
    ));
create index if not exists safety_publication_v3_claim_idx
    on public.safety_publication_decisions(claim_id, created_at desc)
    where claim_id is not null;
create unique index if not exists safety_publication_v3_one_final_per_candidate_uidx
    on public.safety_publication_decisions(supersedes_decision_id)
    where supersedes_decision_id is not null;

-- The citizen-facing owner policy exposed reviewer IDs. A status RPC below
-- returns only the reporter's report state.
drop policy if exists safety_publication_decisions_owner_read
    on public.safety_publication_decisions;
revoke select on public.safety_publication_decisions from authenticated;
revoke insert, update, delete on public.safety_publication_decisions
    from anon, authenticated, service_role;
revoke insert, update, delete on public.safety_publication_reviews
    from anon, authenticated, service_role;

create or replace function public.safety_reject_publication_history_mutation()
returns trigger language plpgsql set search_path = '' as $$
begin
    raise exception using errcode = '42501',
        message = 'SAFETY_PUBLICATION_HISTORY_IMMUTABLE';
end;
$$;
revoke all on function public.safety_reject_publication_history_mutation()
    from public, anon, authenticated, service_role;
create trigger safety_publication_decisions_immutable
    before update or delete on public.safety_publication_decisions
    for each row execute function public.safety_reject_publication_history_mutation();
create trigger safety_publication_reviews_immutable
    before update or delete on public.safety_publication_reviews
    for each row execute function public.safety_reject_publication_history_mutation();

create or replace function public.safety_is_reviewer()
returns boolean language sql stable security definer set search_path = '' as $$
    select (select auth.uid()) is not null
        and public.meet_has_platform_authority('TRUST_REVIEWER')
        and public.meet_session_has_aal2();
$$;
create or replace function public.safety_is_publisher()
returns boolean language sql stable security definer set search_path = '' as $$
    select (select auth.uid()) is not null
        and public.meet_has_platform_authority('LEGAL_REVIEWER')
        and public.meet_session_has_aal2();
$$;
revoke all on function public.safety_is_reviewer() from public, anon, service_role;
revoke all on function public.safety_is_publisher() from public, anon, service_role;
grant execute on function public.safety_is_reviewer() to authenticated;
grant execute on function public.safety_is_publisher() to authenticated;

create or replace function public.safety_recommend_claim_publication_v1(
    p_claim_id uuid, p_recommendation text, p_reason_code text,
    p_public_summary text
) returns uuid language plpgsql security definer set search_path = '' as $$
declare
    v_actor uuid := auth.uid();
    v_claim public.safety_claims%rowtype;
    v_report public.safety_reports%rowtype;
    v_report_id uuid;
    v_decision_id uuid;
begin
    if v_actor is null then
        raise exception using errcode = '28000', message = 'AUTHENTICATION_REQUIRED';
    end if;
    if not public.safety_is_reviewer() then
        raise exception using errcode = '42501', message = 'SAFETY_REVIEWER_AAL2_REQUIRED';
    end if;
    if p_claim_id is null
       or p_recommendation is null
       or p_recommendation not in ('READY_TO_PUBLISH', 'READY_TO_REDACT', 'HOLD', 'REJECT')
       or char_length(btrim(coalesce(p_reason_code, ''))) not between 3 and 80
       or char_length(btrim(coalesce(p_public_summary, ''))) not between 10 and 500 then
        raise exception using errcode = '22023', message = 'INVALID_PUBLICATION_RECOMMENDATION';
    end if;
    select report_id into v_report_id from public.safety_claims where id = p_claim_id;
    if not found then
        raise exception using errcode = '22023', message = 'CLAIM_NOT_PUBLICATION_ELIGIBLE';
    end if;
    select * into v_report from public.safety_reports
    where id = v_report_id for share;
    select * into v_claim from public.safety_claims where id = p_claim_id for share;
    if not found or v_claim.state not in
        ('DOCUMENTED', 'CORROBORATED', 'STRONGLY_CORROBORATED') then
        raise exception using errcode = '22023', message = 'CLAIM_NOT_PUBLICATION_ELIGIBLE';
    end if;
    if v_claim.report_id is distinct from v_report.id
       or v_report.state = 'WITHDRAWN' then
        raise exception using errcode = '22023', message = 'REPORT_NOT_PUBLICATION_ELIGIBLE';
    end if;
    insert into public.safety_publication_decisions(
        claim_id, decision, reviewer_id, reviewed_at, reason_code,
        public_summary, policy_version, decision_phase, recommendation,
        claim_state_version, report_state_version
    ) values (
        p_claim_id, case when p_recommendation = 'REJECT' then 'REJECT' else 'HOLD' end,
        v_actor, now(), btrim(p_reason_code), btrim(p_public_summary),
        'SAFETY-PUBLICATION-V3', 'CANDIDATE', p_recommendation,
        v_claim.state_version, v_report.state_version
    ) returning id into v_decision_id;
    insert into public.safety_publication_reviews(
        decision_id, review_action, reviewer_id, notes
    ) values (v_decision_id, 'SUBMITTED', v_actor, btrim(p_reason_code));
    return v_decision_id;
end;
$$;
revoke all on function public.safety_recommend_claim_publication_v1(
    uuid, text, text, text) from public, anon, service_role;
grant execute on function public.safety_recommend_claim_publication_v1(
    uuid, text, text, text) to authenticated;

-- A point must be tied to a V3 final decision. The V1 report-linked rows
-- were quarantined by the preceding migration.
alter table public.safety_public_points
    add column if not exists publication_decision_id uuid
        references public.safety_publication_decisions(id) on delete restrict;
alter table public.safety_public_points alter column claim_id set not null;
alter table public.safety_public_points alter column publication_decision_id set not null;
alter table public.safety_public_points
    add constraint safety_public_points_v3_claim_fk
    foreign key (claim_id) references public.safety_claims(id) on delete restrict;
create unique index if not exists safety_public_points_claim_v3_uidx
    on public.safety_public_points(claim_id);
revoke insert, update, delete on public.safety_public_points
    from anon, authenticated, service_role;

create table if not exists safety_private.public_point_history_v3 (
    public_point_id uuid not null,
    server_version bigint not null,
    snapshot jsonb not null,
    archived_at timestamptz not null default now(),
    primary key (public_point_id, server_version)
);
revoke all on safety_private.public_point_history_v3
    from public, anon, authenticated;
grant select on safety_private.public_point_history_v3 to service_role;
create or replace function public.safety_archive_public_point_v3()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
    insert into safety_private.public_point_history_v3(
        public_point_id, server_version, snapshot
    ) values (old.id, old.server_version, to_jsonb(old))
    on conflict (public_point_id, server_version) do nothing;
    if tg_op = 'UPDATE' then
        return new;
    end if;
    return old;
end;
$$;
revoke all on function public.safety_archive_public_point_v3()
    from public, anon, authenticated, service_role;
create trigger safety_archive_public_point_v3
    before update or delete on public.safety_public_points
    for each row execute function public.safety_archive_public_point_v3();

-- Keep the table safe even if a future server path gains write privileges.
create or replace function public.safety_guard_public_point_v3()
returns trigger language plpgsql security definer set search_path = '' as $$
declare
    v_final public.safety_publication_decisions%rowtype;
    v_candidate public.safety_publication_decisions%rowtype;
    v_content safety_private.report_content%rowtype;
    v_claim public.safety_claims%rowtype;
    v_report public.safety_reports%rowtype;
begin
    select * into v_final from public.safety_publication_decisions
    where id = new.publication_decision_id;
    select * into v_candidate from public.safety_publication_decisions
    where id = v_final.supersedes_decision_id;
    select * into v_claim from public.safety_claims where id = new.claim_id;
    select * into v_report from public.safety_reports where id = v_claim.report_id;
    select * into v_content from safety_private.report_content
    where report_id = v_claim.report_id;
    if v_final.decision_phase is distinct from 'FINAL'
       or v_final.decision is distinct from 'PUBLISH'
       or v_final.claim_id is distinct from new.claim_id
       or v_candidate.decision_phase is distinct from 'CANDIDATE'
       or v_candidate.id is distinct from v_final.supersedes_decision_id
       or v_candidate.reviewer_id is not distinct from v_final.reviewer_id
       or v_candidate.recommendation is distinct from 'READY_TO_PUBLISH'
       or v_claim.state is distinct from new.claim_state
       or v_report.state = 'WITHDRAWN'
       or v_claim.state_version is distinct from v_final.claim_state_version
       or v_report.state_version is distinct from v_final.report_state_version
       or not exists (
           select 1 from public.safety_claim_sources cs
           join public.safety_sources s on s.id = cs.source_id
           join public.safety_reports sr on sr.id = s.report_id
           where cs.claim_id = new.claim_id
             and cs.role in ('SUPPORTING', 'CORROBORATING', 'PRIMARY')
             and s.source_type not in ('ANONYMOUS', 'UNKNOWN')
             and sr.state <> 'WITHDRAWN'
       )
       or new.claim_state not in ('DOCUMENTED', 'CORROBORATED', 'STRONGLY_CORROBORATED')
       or new.independent_source_count < 1
       or new.geo_disclosure <> 'COARSE_GRID_25KM_PLUS'
       or new.location_accuracy_meters is null
       or new.location_accuracy_meters < 25000
       or v_content.latitude is null or v_content.longitude is null
       or abs(v_content.latitude) > 80
       or new.display_latitude is distinct from
           (round(v_content.latitude::numeric * 4) / 4)::double precision
       or new.display_longitude is distinct from
           (round(v_content.longitude::numeric * 4) / 4)::double precision
       or (new.display_latitude = v_content.latitude
           and new.display_longitude = v_content.longitude)
       or new.neighborhood_name is not null or new.street_name is not null then
        raise exception using errcode = '42501',
            message = 'SAFETY_PUBLIC_POINT_AUTHORITY_REQUIRED';
    end if;
    return new;
end;
$$;
revoke all on function public.safety_guard_public_point_v3()
    from public, anon, authenticated, service_role;
create trigger safety_guard_public_point_v3
    before insert or update on public.safety_public_points
    for each row execute function public.safety_guard_public_point_v3();

create table if not exists safety_private.claim_reevaluation_v3 (
    claim_id uuid primary key references public.safety_claims(id) on delete restrict,
    report_id uuid not null references public.safety_reports(id) on delete restrict,
    reason_code text not null,
    requested_at timestamptz not null default now(),
    status text not null default 'PENDING'
        check (status in ('PENDING', 'REVIEWED'))
);
revoke all on safety_private.claim_reevaluation_v3
    from public, anon, authenticated;
grant select on safety_private.claim_reevaluation_v3 to service_role;

create or replace function public.safety_invalidate_changed_claim_v3()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
    -- Epistemologically relevant columns only. state_version is a monotonic
    -- counter that accompanies content changes but is NOT independently
    -- epistemological — bumping it alone (e.g. to invalidate a stale
    -- publication candidate) must NOT revoke existing published points.
    if row(
        new.report_id,
        new.subject_ref,
        new.predicate,
        new.state,
        new.methodology_version
    ) is distinct from row(
        old.report_id,
        old.subject_ref,
        old.predicate,
        old.state,
        old.methodology_version
    ) then
        insert into safety_private.claim_reevaluation_v3(
            claim_id,
            report_id,
            reason_code,
            requested_at,
            status
        ) values (
            new.id,
            new.report_id,
            'CLAIM_AUTHORITY_CHANGED',
            now(),
            'PENDING'
        ) on conflict(claim_id) do update set
            report_id = excluded.report_id,
            reason_code = excluded.reason_code,
            requested_at = excluded.requested_at,
            status = 'PENDING';
        delete from public.safety_public_points where claim_id = new.id;
    end if;
    return new;
end;
$$;
revoke all on function public.safety_invalidate_changed_claim_v3()
    from public, anon, authenticated, service_role;
create trigger safety_invalidate_changed_claim_v3
    after update of report_id, subject_ref, predicate, state, methodology_version on public.safety_claims
    for each row execute function public.safety_invalidate_changed_claim_v3();

-- A source's metadata is part of the publication proof. Changing it revokes
-- every point it supports, including points owned by another report.
create or replace function public.safety_invalidate_changed_source_point_v3()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
    if (new.report_id, new.source_type, new.cluster_id,
        new.reliability_score, new.independence_score) is distinct from
       (old.report_id, old.source_type, old.cluster_id,
        old.reliability_score, old.independence_score) then
        insert into safety_private.claim_reevaluation_v3(
            claim_id, report_id, reason_code, requested_at, status
        )
        select distinct c.id, c.report_id, 'SOURCE_CHANGED', now(), 'PENDING'
        from public.safety_claim_sources cs
        join public.safety_claims c on c.id = cs.claim_id
        where cs.source_id = new.id
          and exists (select 1 from public.safety_public_points p
                      where p.claim_id = c.id)
        on conflict (claim_id) do update set
            reason_code = excluded.reason_code,
            requested_at = excluded.requested_at,
            status = 'PENDING';
        delete from public.safety_public_points p
        using public.safety_claim_sources cs
        where p.claim_id = cs.claim_id and cs.source_id = new.id;
    end if;
    return new;
end;
$$;
revoke all on function public.safety_invalidate_changed_source_point_v3()
    from public, anon, authenticated, service_role;
create trigger safety_invalidate_changed_source_point_v3
    after update on public.safety_sources
    for each row execute function public.safety_invalidate_changed_source_point_v3();

create or replace function public.safety_finalize_claim_publication_v1(
    p_candidate_decision_id uuid, p_final_decision text,
    p_reason_code text, p_idempotency_key uuid
) returns jsonb language plpgsql security definer
set search_path = '' set timezone = 'UTC' as $$
declare
    v_actor uuid := auth.uid();
    v_candidate public.safety_publication_decisions%rowtype;
    v_claim public.safety_claims%rowtype;
    v_report public.safety_reports%rowtype;
    v_report_id uuid;
    v_content safety_private.report_content%rowtype;
    v_reserved uuid;
    v_existing public.safety_command_dedup%rowtype;
    v_digest text;
    v_final_id uuid;
    v_point_id uuid;
    v_independent integer;
    v_civil integer;
    v_journalistic integer;
    v_public_record integer;
    v_documentary integer;
    v_institutional integer;
    v_result jsonb;
begin
    if v_actor is null then
        raise exception using errcode = '28000', message = 'AUTHENTICATION_REQUIRED';
    end if;
    if not public.safety_is_publisher() then
        raise exception using errcode = '42501', message = 'SAFETY_PUBLISHER_AAL2_REQUIRED';
    end if;
    if p_candidate_decision_id is null or p_idempotency_key is null
       or p_final_decision is null
       or p_final_decision not in ('PUBLISH', 'REDACT', 'REJECT')
       or char_length(btrim(coalesce(p_reason_code, ''))) not between 3 and 80 then
        raise exception using errcode = '22023', message = 'INVALID_PUBLICATION_DECISION';
    end if;
    select * into v_candidate from public.safety_publication_decisions
    where id = p_candidate_decision_id for update;
    if not found or v_candidate.decision_phase <> 'CANDIDATE'
       or v_candidate.claim_id is null
       or v_candidate.recommendation not in ('READY_TO_PUBLISH', 'READY_TO_REDACT')
       or (v_candidate.recommendation = 'READY_TO_REDACT'
           and p_final_decision = 'PUBLISH') then
        raise exception using errcode = '22023', message = 'CANDIDATE_NOT_PUBLICATION_READY';
    end if;
    if v_candidate.reviewer_id = v_actor then
        raise exception using errcode = '42501', message = 'SEPARATION_OF_DUTIES_REQUIRED';
    end if;
    v_digest := encode(extensions.digest(
        convert_to(jsonb_build_object(
            'candidate', p_candidate_decision_id, 'decision', p_final_decision,
            'reason', btrim(p_reason_code))::text, 'UTF8'), 'sha256'), 'hex');
    insert into public.safety_command_dedup(
        idempotency_key, actor_id, aggregate_id, command_type,
        server_payload_sha256, result
    ) values (
        p_idempotency_key, v_actor, v_candidate.claim_id, 'FINALIZE_CLAIM_PUBLICATION',
        v_digest, null
    ) on conflict (idempotency_key) do nothing
    returning idempotency_key into v_reserved;
    if v_reserved is null then
        select * into v_existing from public.safety_command_dedup
        where idempotency_key = p_idempotency_key;
        if v_existing.actor_id is distinct from v_actor
           or v_existing.aggregate_id is distinct from v_candidate.claim_id
           or v_existing.command_type <> 'FINALIZE_CLAIM_PUBLICATION'
           or v_existing.server_payload_sha256 <> v_digest then
            raise exception using errcode = '23505',
                message = 'IDEMPOTENCY_PROTOCOL_VIOLATION';
        end if;
        if v_existing.result is null then
            raise exception using errcode = '40001',
                message = 'COMMAND_RESERVATION_INCOMPLETE';
        end if;
        return v_existing.result;
    end if;
    select report_id into v_report_id from public.safety_claims
    where id = v_candidate.claim_id;
    if not found then
        raise exception using errcode = '22023', message = 'CLAIM_NOT_PUBLICATION_ELIGIBLE';
    end if;
    select * into v_report from public.safety_reports
    where id = v_report_id for update;
    select * into v_claim from public.safety_claims
    where id = v_candidate.claim_id for update;
    if not found or v_claim.state not in
        ('DOCUMENTED', 'CORROBORATED', 'STRONGLY_CORROBORATED') then
        raise exception using errcode = '22023', message = 'CLAIM_NOT_PUBLICATION_ELIGIBLE';
    end if;
    if v_claim.report_id is distinct from v_report.id
       or v_report.state = 'WITHDRAWN' then
        raise exception using errcode = '22023', message = 'REPORT_NOT_PUBLICATION_ELIGIBLE';
    end if;
    if v_claim.state_version <> v_candidate.claim_state_version
       or v_report.state_version <> v_candidate.report_state_version then
        raise exception using errcode = '40001', message = 'PUBLICATION_CANDIDATE_STALE';
    end if;
    if exists (
        select 1 from public.safety_publication_decisions d
        where d.supersedes_decision_id = v_candidate.id
    ) then
        raise exception using errcode = '23505', message = 'CANDIDATE_ALREADY_FINALIZED';
    end if;
    -- The publication gate is operator controlled and remains disabled after
    -- this migration. REJECT/REDACT record decisions without creating a point.
    if p_final_decision = 'PUBLISH' and not exists (
        select 1 from public.runtime_feature_gates
        where key = 'safety_public_map' and enabled
    ) then
        raise exception using errcode = '42501', message = 'SAFETY_PUBLIC_MAP_DISABLED';
    end if;
    select * into v_content from safety_private.report_content
    where report_id = v_report.id;
    if not found then
        raise exception using errcode = '22023', message = 'PRIVATE_REPORT_CONTENT_MISSING';
    end if;
    -- Lock source rows and their owning reports through the projection insert.
    -- A concurrent source edit or source-report withdrawal then either waits
    -- and revokes the point, or completes first and fails this eligibility check.
    perform 1 from public.safety_claim_sources cs
      join public.safety_sources s on s.id = cs.source_id
      join public.safety_reports sr on sr.id = s.report_id
     where cs.claim_id = v_claim.id
       and cs.role in ('SUPPORTING', 'CORROBORATING', 'PRIMARY')
     order by s.id for share of s, sr;
    select count(distinct coalesce(s.cluster_id, s.id)),
           count(*) filter (where s.source_type in
               ('DIRECT_WITNESS', 'FAMILY_OR_NEIGHBOR', 'SECOND_HAND')),
           count(*) filter (where s.source_type = 'JOURNALISTIC'),
           count(*) filter (where s.source_type = 'PUBLIC_RECORD'),
           count(*) filter (where s.source_type = 'DOCUMENTARY'),
           count(*) filter (where s.source_type = 'INSTITUTIONAL')
      into v_independent, v_civil, v_journalistic, v_public_record,
           v_documentary, v_institutional
      from public.safety_claim_sources cs
      join public.safety_sources s on s.id = cs.source_id
      join public.safety_reports sr on sr.id = s.report_id
     where cs.claim_id = v_claim.id
       and cs.role in ('SUPPORTING', 'CORROBORATING', 'PRIMARY')
       and s.source_type not in ('ANONYMOUS', 'UNKNOWN')
       and sr.state <> 'WITHDRAWN';
    if p_final_decision = 'PUBLISH' and v_independent < 1 then
        raise exception using errcode = '22023', message = 'PUBLICATION_REQUIRES_PROVENANCE';
    end if;
    if p_final_decision = 'PUBLISH' and
       (v_content.latitude is null or v_content.longitude is null
        or abs(v_content.latitude) > 80
        or (v_content.latitude =
                (round(v_content.latitude::numeric * 4) / 4)::double precision
            and v_content.longitude =
                (round(v_content.longitude::numeric * 4) / 4)::double precision)) then
        raise exception using errcode = '22023', message = 'PUBLIC_LOCATION_SUPPRESSED';
    end if;
    insert into public.safety_publication_decisions(
        claim_id, decision, reviewer_id, reviewed_at, supersedes_decision_id,
        reason_code, public_summary, policy_version, decision_phase,
        claim_state_version, report_state_version
    ) values (
        v_claim.id, p_final_decision, v_actor, now(), v_candidate.id,
        btrim(p_reason_code), v_candidate.public_summary,
        'SAFETY-PUBLICATION-V3', 'FINAL',
        v_claim.state_version, v_report.state_version
    ) returning id into v_final_id;
    insert into public.safety_publication_reviews(
        decision_id, review_action, reviewer_id, notes
    ) values (
        v_final_id,
        case when p_final_decision = 'REJECT'
             then 'MANUAL_REJECTED' else 'MANUAL_APPROVED' end,
        v_actor, btrim(p_reason_code)
    );
    if p_final_decision = 'PUBLISH' then
        insert into public.safety_public_points(
            claim_id, category, display_latitude, display_longitude,
            geo_disclosure, location_accuracy_meters, label, claim_state,
            independent_source_count, civil_source_count,
            journalistic_source_count, public_record_source_count,
            documentary_source_count, institutional_source_count,
            first_documented_at, last_reviewed_at, published_at,
            server_version, publication_decision_id
        ) values (
            v_claim.id, v_report.category,
            round(v_content.latitude::numeric * 4) / 4,
            round(v_content.longitude::numeric * 4) / 4,
            'COARSE_GRID_25KM_PLUS',
            greatest(coalesce(ceil(v_content.accuracy_meters)::integer, 0), 25000),
            'Reporte documentado con revisión independiente', v_claim.state,
            v_independent, v_civil, v_journalistic, v_public_record,
            v_documentary, v_institutional,
            coalesce(v_report.occurred_at, v_report.created_at),
            now(), now(), 1, v_final_id
        ) on conflict (claim_id) do update set
            category = excluded.category,
            display_latitude = excluded.display_latitude,
            display_longitude = excluded.display_longitude,
            geo_disclosure = excluded.geo_disclosure,
            location_accuracy_meters = excluded.location_accuracy_meters,
            label = excluded.label,
            claim_state = excluded.claim_state,
            independent_source_count = excluded.independent_source_count,
            civil_source_count = excluded.civil_source_count,
            journalistic_source_count = excluded.journalistic_source_count,
            public_record_source_count = excluded.public_record_source_count,
            documentary_source_count = excluded.documentary_source_count,
            institutional_source_count = excluded.institutional_source_count,
            last_reviewed_at = now(),
            publication_decision_id = excluded.publication_decision_id,
            server_version = public.safety_public_points.server_version + 1
        returning id into v_point_id;
    else
        -- Redaction and rejection withdraw any previous public projection.
        -- The archive trigger retains its last published version privately.
        delete from public.safety_public_points where claim_id = v_claim.id;
    end if;
    v_result := jsonb_build_object(
        'claim_id', v_claim.id, 'decision_id', v_final_id,
        'public_point_id', v_point_id, 'decision', p_final_decision,
        'policy_version', 'SAFETY-PUBLICATION-V3');
    update public.safety_command_dedup set result = v_result
    where idempotency_key = p_idempotency_key;
    update safety_private.claim_reevaluation_v3 set status = 'REVIEWED'
    where claim_id = v_claim.id;
    return v_result;
end;
$$;
revoke all on function public.safety_finalize_claim_publication_v1(
    uuid, text, text, uuid) from public, anon, service_role;
grant execute on function public.safety_finalize_claim_publication_v1(
    uuid, text, text, uuid) to authenticated;

-- Direct reads stay controlled by the feature gate even when the API queries
-- the projection table rather than using the Android UI.
drop policy if exists safety_public_points_v3_closed on public.safety_public_points;
drop policy if exists safety_public_points_v3_published_read on public.safety_public_points;
create policy safety_public_points_v3_published_read
on public.safety_public_points for select to authenticated using (
    exists (select 1 from public.runtime_feature_gates
            where key = 'safety_public_map' and enabled)
);

create or replace function public.safety_my_report_publication_status_v1(
    p_report_id uuid
) returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare v_actor uuid := auth.uid(); v_report public.safety_reports%rowtype;
begin
    select * into v_report from public.safety_reports
    where id = p_report_id and reporter_user_id = v_actor;
    if not found then
        raise exception using errcode = '42501', message = 'REPORT_NOT_OWNED';
    end if;
    return jsonb_build_object('report_id', v_report.id, 'state', v_report.state,
                              'updated_at', v_report.updated_at);
end;
$$;
revoke all on function public.safety_my_report_publication_status_v1(uuid)
    from public, anon, service_role;
grant execute on function public.safety_my_report_publication_status_v1(uuid)
    to authenticated;

create table if not exists safety_private.report_withdrawal_events_v3 (
    report_id uuid primary key references public.safety_reports(id) on delete restrict,
    actor_id uuid not null,
    idempotency_key uuid not null unique,
    original_payload_sha256 text not null,
    state_version bigint not null,
    withdrawn_at timestamptz not null default now()
);
revoke all on safety_private.report_withdrawal_events_v3
    from public, anon, authenticated;
grant select on safety_private.report_withdrawal_events_v3 to service_role;
create trigger safety_report_withdrawal_events_immutable
    before update or delete on safety_private.report_withdrawal_events_v3
    for each row execute function public.safety_reject_publication_history_mutation();

create or replace function public.safety_withdraw_report_v1(
    p_report_id uuid, p_idempotency_key uuid
) returns jsonb language plpgsql security definer
set search_path = '' set timezone = 'UTC' as $$
declare
    v_actor uuid := auth.uid();
    v_report public.safety_reports%rowtype;
    v_content safety_private.report_content%rowtype;
    v_existing public.safety_command_dedup%rowtype;
    v_reserved uuid;
    v_digest text;
    v_version bigint;
    v_result jsonb;
begin
    if v_actor is null then
        raise exception using errcode = '28000', message = 'AUTHENTICATION_REQUIRED';
    end if;
    if p_report_id is null or p_idempotency_key is null then
        raise exception using errcode = '22023', message = 'REQUIRED_ARGUMENT_MISSING';
    end if;
    select * into v_report from public.safety_reports
    where id = p_report_id for update;
    if not found or v_report.reporter_user_id <> v_actor then
        raise exception using errcode = '42501', message = 'REPORT_NOT_OWNED';
    end if;
    v_digest := encode(extensions.digest(
        convert_to(jsonb_build_object(
            'command', 'WITHDRAW_REPORT', 'report_id', p_report_id)::text,
            'UTF8'), 'sha256'), 'hex');
    insert into public.safety_command_dedup(
        idempotency_key, actor_id, aggregate_id, command_type,
        server_payload_sha256, result
    ) values (
        p_idempotency_key, v_actor, p_report_id, 'WITHDRAW_REPORT',
        v_digest, null
    ) on conflict (idempotency_key) do nothing
    returning idempotency_key into v_reserved;
    if v_reserved is null then
        select * into v_existing from public.safety_command_dedup
        where idempotency_key = p_idempotency_key;
        if v_existing.actor_id is distinct from v_actor
           or v_existing.aggregate_id is distinct from p_report_id
           or v_existing.command_type <> 'WITHDRAW_REPORT'
           or v_existing.server_payload_sha256 <> v_digest then
            raise exception using errcode = '23505',
                message = 'IDEMPOTENCY_PROTOCOL_VIOLATION';
        end if;
        if v_existing.result is null then
            raise exception using errcode = '40001',
                message = 'COMMAND_RESERVATION_INCOMPLETE';
        end if;
        return v_existing.result;
    end if;
    v_version := v_report.state_version;
    if v_report.state <> 'WITHDRAWN' then
        select * into v_content from safety_private.report_content
        where report_id = p_report_id;
        insert into safety_private.claim_reevaluation_v3(
            claim_id, report_id, reason_code, requested_at, status
        ) select distinct c.id, c.report_id, 'REPORT_WITHDRAWN', now(), 'PENDING'
          from public.safety_claims c
          left join public.safety_claim_sources cs on cs.claim_id = c.id
          left join public.safety_sources s on s.id = cs.source_id
         where c.report_id = p_report_id or s.report_id = p_report_id
        on conflict (claim_id) do update set
            reason_code = excluded.reason_code,
            requested_at = excluded.requested_at,
            status = 'PENDING';
        delete from public.safety_public_points
        where claim_id in (
            select c.id from public.safety_claims c
            left join public.safety_claim_sources cs on cs.claim_id = c.id
            left join public.safety_sources s on s.id = cs.source_id
            where c.report_id = p_report_id or s.report_id = p_report_id
        );
        update public.safety_reports
        set state = 'WITHDRAWN', state_version = state_version + 1,
            updated_at = now()
        where id = p_report_id
        returning state_version into v_version;
        update safety_private.report_content
        set narrative = '', source_relation = 'UNKNOWN',
            latitude = null, longitude = null, accuracy_meters = null
        where report_id = p_report_id;
        insert into safety_private.report_withdrawal_events_v3(
            report_id, actor_id, idempotency_key, original_payload_sha256,
            state_version
        ) values (
            p_report_id, v_actor, p_idempotency_key,
            v_content.server_payload_sha256, v_version
        );
    end if;
    v_result := jsonb_build_object(
        'report_id', p_report_id, 'state', 'WITHDRAWN',
        'server_version', v_version, 'correlation_id', null
    );
    update public.safety_command_dedup set result = v_result
    where idempotency_key = p_idempotency_key;
    return v_result;
end;
$$;
revoke all on function public.safety_withdraw_report_v1(uuid, uuid)
    from public, anon, service_role;
grant execute on function public.safety_withdraw_report_v1(uuid, uuid)
    to authenticated;

commit;
