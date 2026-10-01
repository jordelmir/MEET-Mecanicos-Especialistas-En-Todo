begin;

-- Forward-only closure of PR review findings; historical deployed migrations
-- and Room migration versions retain their original identities.
update public.runtime_feature_gates
set enabled = false, reason = 'Safety V3 authoritative public projections not yet reopened', updated_at = now()
where key in ('safety_public_map', 'safety_public_cases', 'safety_accountability', 'safety_observatory', 'safety_realtime');

-- Retire any point produced with the previous misleading geographic label.
delete from public.safety_public_points where geo_disclosure <> 'COARSE_GRID_25KM_PLUS';

alter table safety_private.public_case_history_v3
add column if not exists publication_decision_id uuid
references public.safety_publication_decisions(id) on delete restrict;
update safety_private.public_case_history_v3
set publication_decision_id = nullif(snapshot->>'publication_decision_id', '')::uuid
where publication_decision_id is null;


alter table public.safety_publication_decisions
    add column if not exists claim_authority_fingerprint text
    check (claim_authority_fingerprint is null or claim_authority_fingerprint ~ '^[a-f0-9]{64}$');

-- The version is only one component: reparenting, wording, subject and
-- methodology changes also invalidate the immutable decision proof.

alter table public.safety_publication_decisions
    add column if not exists case_authority_fingerprint text
    check (case_authority_fingerprint is null or case_authority_fingerprint ~ '^[a-f0-9]{64}$');

create or replace function public.safety_case_authority_fingerprint_v3(p_case_id uuid)
returns text language sql stable security definer set search_path = '' as $$
    select encode(extensions.digest(convert_to(jsonb_build_object(
        'case', to_jsonb(c),
        'events', coalesce((select jsonb_agg(to_jsonb(e) order by e.id)
            from public.safety_events e join public.safety_case_events ce on ce.event_id = e.id
            where ce.case_id = c.id), '[]'::jsonb),
        'claims', coalesce((select jsonb_agg(jsonb_build_object(
            'edge', to_jsonb(ec), 'claim', to_jsonb(cl),
            'report', to_jsonb(r),
            'sources', coalesce((select jsonb_agg(jsonb_build_object(
                'edge', to_jsonb(cs), 'source', to_jsonb(src), 'report', to_jsonb(sr)) order by cs.source_id)
                from public.safety_claim_sources cs join public.safety_sources src on src.id = cs.source_id
                join public.safety_reports sr on sr.id = src.report_id where cs.claim_id = cl.id), '[]'::jsonb)
        ) order by ec.event_id, ec.claim_id)
            from public.safety_case_events ce join public.safety_event_claims ec on ec.event_id = ce.event_id
            join public.safety_claims cl on cl.id = ec.claim_id
            join public.safety_reports r on r.id = cl.report_id
            where ce.case_id = c.id), '[]'::jsonb)
    )::text, 'UTF8'), 'sha256'), 'hex')
    from public.safety_cases c where c.id = p_case_id
$$;
revoke all on function public.safety_case_authority_fingerprint_v3(uuid)
    from public, anon, authenticated, service_role;

-- Edges are part of authority too: inserting/deleting a link cannot preserve a
-- header approved for another graph. Rows are reconstructible public caches.
create or replace function public.safety_invalidate_changed_case_graph_v3()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
    delete from public.safety_public_case_projection p
    using public.safety_publication_decisions d
    where d.id = p.publication_decision_id
      and d.case_authority_fingerprint is distinct from public.safety_case_authority_fingerprint_v3(p.case_id);
    return null;
end;
$$;
revoke all on function public.safety_invalidate_changed_case_graph_v3()
    from public, anon, authenticated, service_role;
create trigger safety_case_edge_authority_invalidation_v3
    after insert or update or delete on public.safety_case_events
    for each statement execute function public.safety_invalidate_changed_case_graph_v3();
create trigger safety_event_claim_edge_authority_invalidation_v3
    after insert or update or delete on public.safety_event_claims
    for each statement execute function public.safety_invalidate_changed_case_graph_v3();
create trigger safety_claim_source_edge_authority_invalidation_v3
    after insert or update or delete on public.safety_claim_sources
    for each statement execute function public.safety_invalidate_changed_case_graph_v3();
create trigger safety_event_authority_invalidation_v3
    after update or delete on public.safety_events
    for each statement execute function public.safety_invalidate_changed_case_graph_v3();

create or replace function public.safety_claim_authority_fingerprint_v3(p_claim_id uuid)
returns text language sql stable security definer set search_path = '' as $$
    select encode(extensions.digest(convert_to(jsonb_build_array(
        c.report_id, c.subject_ref, c.predicate, c.state,
        c.methodology_version, c.state_version
    )::text, 'UTF8'), 'sha256'), 'hex')
    from public.safety_claims c where c.id = p_claim_id
$$;
revoke all on function public.safety_claim_authority_fingerprint_v3(uuid)
    from public, anon, authenticated, service_role;

create or replace function public.safety_invalidate_changed_claim_v3()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
    if row(new.report_id, new.subject_ref, new.predicate, new.state,
           new.methodology_version, new.state_version) is distinct from
       row(old.report_id, old.subject_ref, old.predicate, old.state,
           old.methodology_version, old.state_version) then
        delete from public.safety_public_points where claim_id = new.id;
        insert into safety_private.claim_reevaluation_v3(
            claim_id, report_id, reason_code, requested_at, status
        ) values (new.id, new.report_id, 'CLAIM_AUTHORITY_CHANGED', now(), 'PENDING')
        on conflict (claim_id) do update set
            report_id = excluded.report_id,
            reason_code = excluded.reason_code,
            requested_at = excluded.requested_at,
            status = 'PENDING';
    end if;
    return new;
end;
$$;

create or replace function public.safety_archive_public_case_v3()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
    insert into safety_private.public_case_history_v3(
        case_id, server_version, snapshot, publication_decision_id
    ) values (old.case_id, old.server_version, to_jsonb(old), old.publication_decision_id)
    on conflict (case_id, server_version) do nothing;
    if tg_op = 'UPDATE' then return new; end if;
    return old;
end;
$$;

create or replace function public.safety_guard_public_case_v3()
returns trigger language plpgsql security definer set search_path = '' as $$
declare
    v_final public.safety_publication_decisions%rowtype;
    v_candidate public.safety_publication_decisions%rowtype;
    v_case public.safety_cases%rowtype;
begin
    select * into v_final from public.safety_publication_decisions
    where id = new.publication_decision_id;
    select * into v_candidate from public.safety_publication_decisions
    where id = v_final.supersedes_decision_id;
    select * into v_case from public.safety_cases where id = new.case_id;
    if v_final.decision_phase is distinct from 'FINAL'
       or v_final.decision is distinct from 'PUBLISH'
       or v_final.case_id is distinct from new.case_id
       or v_final.case_state_version is distinct from new.case_state_version
       or v_candidate.decision_phase is distinct from 'CANDIDATE'
       or v_candidate.case_id is distinct from new.case_id
       or v_candidate.case_state_version is distinct from new.case_state_version
       or v_candidate.reviewer_id is not distinct from v_final.reviewer_id
       or v_candidate.recommendation is distinct from 'READY_TO_PUBLISH'
       or v_final.case_authority_fingerprint is distinct from public.safety_case_authority_fingerprint_v3(v_case.id)
       or v_candidate.case_authority_fingerprint is distinct from v_final.case_authority_fingerprint
       or v_case.state_version is distinct from new.case_state_version
       or v_case.status not in ('UNDER_REVIEW', 'CLOSED')
       or not public.safety_case_has_publication_provenance_v3(new.case_id)
       or new.case_type is distinct from 'SAFETY_CASE'
       or new.title is distinct from 'Caso con revisión independiente'
       or new.public_summary is distinct from ''
       or new.lifecycle is distinct from v_case.status
       or new.confidence_score is not null
       or new.event_count is not null or new.claim_count is not null
       or new.source_count is not null or new.evidence_count is not null then
        raise exception using errcode = '42501',
            message = 'SAFETY_PUBLIC_CASE_AUTHORITY_REQUIRED';
    end if;
    if tg_op = 'INSERT' and new.server_version is distinct from
       (select coalesce(max(h.server_version), 0) + 1
        from safety_private.public_case_history_v3 h where h.case_id = new.case_id) then
        raise exception using errcode = '42501', message = 'SAFETY_PUBLIC_CASE_VERSION_REQUIRED';
    end if;
    if tg_op = 'UPDATE' then
        if new.case_id is distinct from old.case_id
           or new.server_version is distinct from old.server_version + 1
           or new.published_at is distinct from old.published_at then
            raise exception using errcode = '42501',
                message = 'SAFETY_PUBLIC_CASE_VERSION_REQUIRED';
        end if;
    end if;
    return new;
end;
$$;

create or replace function public.safety_recommend_case_publication_v1(
    p_case_id uuid, p_recommendation text, p_reason_code text,
    p_idempotency_key uuid
) returns uuid language plpgsql security definer
set search_path = '' set timezone = 'UTC' as $$
declare
    v_actor uuid := auth.uid();
    v_case public.safety_cases%rowtype;
    v_digest text;
    v_reserved uuid;
    v_existing public.safety_command_dedup%rowtype;
    v_decision_id uuid;
    v_result jsonb;
begin
    if v_actor is null then
        raise exception using errcode = '28000', message = 'AUTHENTICATION_REQUIRED';
    end if;
    if not public.safety_is_reviewer() then
        raise exception using errcode = '42501', message = 'SAFETY_REVIEWER_AAL2_REQUIRED';
    end if;
    if p_case_id is null or p_idempotency_key is null
       or p_recommendation is null
       or p_recommendation not in ('READY_TO_PUBLISH', 'HOLD', 'REJECT')
       or char_length(btrim(coalesce(p_reason_code, ''))) not between 3 and 80 then
        raise exception using errcode = '22023', message = 'INVALID_CASE_PUBLICATION_RECOMMENDATION';
    end if;
    v_digest := encode(extensions.digest(convert_to(
        jsonb_build_object('case', p_case_id, 'recommendation', p_recommendation,
                           'reason', btrim(p_reason_code))::text, 'UTF8'),
        'sha256'), 'hex');
    insert into public.safety_command_dedup(
        idempotency_key, actor_id, aggregate_id, command_type,
        server_payload_sha256, result
    ) values (
        p_idempotency_key, v_actor, p_case_id,
        'RECOMMEND_CASE_PUBLICATION', v_digest, null
    ) on conflict (idempotency_key) do nothing
    returning idempotency_key into v_reserved;
    if v_reserved is null then
        select * into v_existing from public.safety_command_dedup
        where idempotency_key = p_idempotency_key;
        if v_existing.actor_id is distinct from v_actor
           or v_existing.aggregate_id is distinct from p_case_id
           or v_existing.command_type is distinct from 'RECOMMEND_CASE_PUBLICATION'
           or v_existing.server_payload_sha256 is distinct from v_digest then
            raise exception using errcode = '23505',
                message = 'IDEMPOTENCY_PROTOCOL_VIOLATION';
        end if;
        if v_existing.result is null then
            raise exception using errcode = '40001',
                message = 'COMMAND_RESERVATION_INCOMPLETE';
        end if;
        return (v_existing.result->>'decision_id')::uuid;
    end if;
    select * into v_case from public.safety_cases
    where id = p_case_id for share;
    if not found or v_case.status not in ('UNDER_REVIEW', 'CLOSED')
       or v_case.state_version < 1 then
        raise exception using errcode = '22023', message = 'CASE_NOT_PUBLICATION_ELIGIBLE';
    end if;
    if p_recommendation = 'READY_TO_PUBLISH'
       and not public.safety_case_has_publication_provenance_v3(p_case_id) then
        raise exception using errcode = '22023', message = 'CASE_REQUIRES_DOCUMENTED_PROVENANCE';
    end if;
    insert into public.safety_publication_decisions(
        case_id, decision, reviewer_id, reviewed_at, reason_code,
        policy_version, decision_phase, recommendation, case_state_version, case_authority_fingerprint
    ) values (
        p_case_id, case when p_recommendation = 'REJECT' then 'REJECT' else 'HOLD' end,
        v_actor, now(), btrim(p_reason_code), 'SAFETY-PUBLICATION-V3',
        'CANDIDATE', p_recommendation, v_case.state_version, public.safety_case_authority_fingerprint_v3(v_case.id)
    ) returning id into v_decision_id;
    insert into public.safety_publication_reviews(
        decision_id, review_action, reviewer_id, notes
    ) values (v_decision_id, 'SUBMITTED', v_actor, btrim(p_reason_code));
    v_result := jsonb_build_object('decision_id', v_decision_id);
    update public.safety_command_dedup set result = v_result
    where idempotency_key = p_idempotency_key;
    return v_decision_id;
end;
$$;

create or replace function public.safety_finalize_case_publication_v1(
    p_candidate_decision_id uuid, p_final_decision text,
    p_reason_code text, p_idempotency_key uuid
) returns jsonb language plpgsql security definer
set search_path = '' set timezone = 'UTC' as $$
declare
    v_actor uuid := auth.uid();
    v_candidate public.safety_publication_decisions%rowtype;
    v_case public.safety_cases%rowtype;
    v_reserved uuid;
    v_existing public.safety_command_dedup%rowtype;
    v_digest text;
    v_final_id uuid;
    v_eligible_claim_id uuid;
    v_result jsonb;
    v_next_server_version bigint;
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
        raise exception using errcode = '22023', message = 'INVALID_CASE_PUBLICATION_DECISION';
    end if;
    select * into v_candidate from public.safety_publication_decisions
    where id = p_candidate_decision_id for update;
    if not found or v_candidate.decision_phase <> 'CANDIDATE'
       or v_candidate.case_id is null
       or v_candidate.recommendation <> 'READY_TO_PUBLISH' then
        raise exception using errcode = '22023', message = 'CANDIDATE_NOT_PUBLICATION_READY';
    end if;
    if v_candidate.reviewer_id = v_actor then
        raise exception using errcode = '42501', message = 'SEPARATION_OF_DUTIES_REQUIRED';
    end if;
    v_digest := encode(extensions.digest(convert_to(
        jsonb_build_object('candidate', p_candidate_decision_id,
                           'decision', p_final_decision,
                           'reason', btrim(p_reason_code))::text, 'UTF8'),
        'sha256'), 'hex');
    insert into public.safety_command_dedup(
        idempotency_key, actor_id, aggregate_id, command_type,
        server_payload_sha256, result
    ) values (
        p_idempotency_key, v_actor, v_candidate.case_id,
        'FINALIZE_CASE_PUBLICATION', v_digest, null
    ) on conflict (idempotency_key) do nothing
    returning idempotency_key into v_reserved;
    if v_reserved is null then
        select * into v_existing from public.safety_command_dedup
        where idempotency_key = p_idempotency_key;
        if v_existing.actor_id is distinct from v_actor
           or v_existing.aggregate_id is distinct from v_candidate.case_id
           or v_existing.command_type is distinct from 'FINALIZE_CASE_PUBLICATION'
           or v_existing.server_payload_sha256 is distinct from v_digest then
            raise exception using errcode = '23505',
                message = 'IDEMPOTENCY_PROTOCOL_VIOLATION';
        end if;
        if v_existing.result is null then
            raise exception using errcode = '40001',
                message = 'COMMAND_RESERVATION_INCOMPLETE';
        end if;
        return v_existing.result;
    end if;
    select * into v_case from public.safety_cases
    where id = v_candidate.case_id for update;
    if not found or v_case.status not in ('UNDER_REVIEW', 'CLOSED')
       or v_case.state_version is distinct from v_candidate.case_state_version
       or v_candidate.case_authority_fingerprint is distinct from public.safety_case_authority_fingerprint_v3(v_case.id) then
        raise exception using errcode = '22023', message = 'CASE_VERSION_OR_STATE_CHANGED';
    end if;
    if exists (select 1 from public.safety_publication_decisions
               where supersedes_decision_id = v_candidate.id) then
        raise exception using errcode = '23505', message = 'CANDIDATE_ALREADY_FINALIZED';
    end if;
    if p_final_decision = 'PUBLISH' then
        if not exists (select 1 from public.runtime_feature_gates
                       where key = 'safety_public_cases' and enabled) then
            raise exception using errcode = '42501', message = 'SAFETY_PUBLIC_CASES_DISABLED';
        end if;
        if not public.safety_case_has_publication_provenance_v3(v_case.id) then
            raise exception using errcode = '22023', message = 'CASE_REQUIRES_DOCUMENTED_PROVENANCE';
        end if;
        -- Serialize the qualifying claim/source/report rows with withdrawal or
        -- retraction. If those changes happen after commit, the invalidation
        -- triggers below remove the published header in their transaction.
        select c.id into v_eligible_claim_id
        from public.safety_case_events ce
        join public.safety_events e on e.id = ce.event_id
        join public.safety_event_claims ec on ec.event_id = e.id
        join public.safety_claims c on c.id = ec.claim_id
        join public.safety_reports r on r.id = c.report_id
        join public.safety_claim_sources cs on cs.claim_id = c.id
        join public.safety_sources s on s.id = cs.source_id
        join public.safety_reports sr on sr.id = s.report_id
        where ce.case_id = v_case.id
          and ec.relation_type in ('SUPPORTS', 'IMPLIES')
          and c.state in ('DOCUMENTED', 'CORROBORATED', 'STRONGLY_CORROBORATED')
          and r.state <> 'WITHDRAWN' and sr.state <> 'WITHDRAWN'
          and cs.role in ('SUPPORTING', 'CORROBORATING', 'PRIMARY')
          and s.source_type not in ('ANONYMOUS', 'UNKNOWN')
        limit 1 for share of ce, e, ec, cs, c, r, s, sr;
        if v_eligible_claim_id is null then
            raise exception using errcode = '22023',
                message = 'CASE_REQUIRES_DOCUMENTED_PROVENANCE';
        end if;
    end if;
    insert into public.safety_publication_decisions(
        case_id, decision, reviewer_id, reviewed_at,
        supersedes_decision_id, reason_code, policy_version,
        decision_phase, case_state_version, case_authority_fingerprint
    ) values (
        v_case.id, p_final_decision, v_actor, now(), v_candidate.id,
        btrim(p_reason_code), 'SAFETY-PUBLICATION-V3', 'FINAL',
        v_case.state_version, v_candidate.case_authority_fingerprint
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
        select greatest(
            coalesce((select max(h.server_version) from safety_private.public_case_history_v3 h where h.case_id = v_case.id), 0),
            coalesce((select p.server_version from public.safety_public_case_projection p where p.case_id = v_case.id), 0)
        ) + 1 into v_next_server_version;
        insert into public.safety_public_case_projection(
            case_id, case_type, title, public_summary, lifecycle,
            confidence_score, event_count, claim_count, source_count,
            evidence_count, published_at, last_updated_at, server_version,
            publication_decision_id, case_state_version
        ) values (
            v_case.id, 'SAFETY_CASE', 'Caso con revisión independiente', '',
            v_case.status, null, null, null, null, null,
            now(), now(), v_next_server_version, v_final_id, v_case.state_version
        ) on conflict (case_id) do update set
            lifecycle = excluded.lifecycle,
            last_updated_at = now(),
            publication_decision_id = excluded.publication_decision_id,
            case_state_version = excluded.case_state_version,
            server_version = excluded.server_version;
    else
        delete from public.safety_public_case_projection
        where case_id = v_case.id;
    end if;
    v_result := jsonb_build_object(
        'case_id', v_case.id, 'decision_id', v_final_id,
        'decision', p_final_decision, 'policy_version', 'SAFETY-PUBLICATION-V3'
    );
    update public.safety_command_dedup set result = v_result
    where idempotency_key = p_idempotency_key;
    return v_result;
end;
$$;

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
       or v_final.claim_authority_fingerprint is distinct from
          public.safety_claim_authority_fingerprint_v3(v_claim.id)
       or v_candidate.claim_authority_fingerprint is distinct from v_final.claim_authority_fingerprint
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
        claim_state_version, report_state_version, claim_authority_fingerprint
    ) values (
        p_claim_id, case when p_recommendation = 'REJECT' then 'REJECT' else 'HOLD' end,
        v_actor, now(), btrim(p_reason_code), btrim(p_public_summary),
        'SAFETY-PUBLICATION-V3', 'CANDIDATE', p_recommendation,
        v_claim.state_version, v_report.state_version, public.safety_claim_authority_fingerprint_v3(v_claim.id)
    ) returning id into v_decision_id;
    insert into public.safety_publication_reviews(
        decision_id, review_action, reviewer_id, notes
    ) values (v_decision_id, 'SUBMITTED', v_actor, btrim(p_reason_code));
    return v_decision_id;
end;
$$;

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
       or v_report.state_version <> v_candidate.report_state_version
       or v_candidate.claim_authority_fingerprint is distinct from
          public.safety_claim_authority_fingerprint_v3(v_claim.id) then
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
        claim_state_version, report_state_version, claim_authority_fingerprint
    ) values (
        v_claim.id, p_final_decision, v_actor, now(), v_candidate.id,
        btrim(p_reason_code), v_candidate.public_summary,
        'SAFETY-PUBLICATION-V3', 'FINAL',
        v_claim.state_version, v_report.state_version, v_candidate.claim_authority_fingerprint
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

drop trigger if exists safety_invalidate_changed_claim_v3 on public.safety_claims;
create trigger safety_invalidate_changed_claim_v3
    after update of report_id, subject_ref, predicate, state, methodology_version, state_version
    on public.safety_claims for each row
    execute function public.safety_invalidate_changed_claim_v3();

revoke all on function public.safety_invalidate_changed_claim_v3(),
    public.safety_archive_public_case_v3(), public.safety_guard_public_case_v3(),
    public.safety_guard_public_point_v3()
from public, anon, authenticated, service_role;

-- A new graph edge must serialize with a case publication, even though it is
-- not visible to that publication's initial MVCC snapshot (phantom insert).
create or replace function public.safety_lock_case_graph_before_change_v3()
returns trigger language plpgsql security definer set search_path = '' as $$
declare
    v_old jsonb := case when tg_op <> 'INSERT' then to_jsonb(old) else '{}'::jsonb end;
    v_new jsonb := case when tg_op <> 'DELETE' then to_jsonb(new) else '{}'::jsonb end;
begin
    perform c.id from public.safety_cases c
    where (tg_table_name = 'safety_case_events' and
           c.id::text in (v_old->>'case_id', v_new->>'case_id'))
       or exists (
           select 1 from public.safety_case_events ce
           left join public.safety_event_claims ec on ec.event_id = ce.event_id
           where ce.case_id = c.id and (
               (tg_table_name = 'safety_event_claims' and ce.event_id::text in (v_old->>'event_id',v_new->>'event_id'))
               or (tg_table_name = 'safety_claim_sources' and ec.claim_id::text in (v_old->>'claim_id',v_new->>'claim_id'))
           )
       )
    order by c.id for update of c;
    if tg_op = 'DELETE' then return old; end if;
    return new;
end;
$$;
revoke all on function public.safety_lock_case_graph_before_change_v3()
    from public,anon,authenticated,service_role;
create trigger safety_lock_case_edge_v3 before insert or update or delete
on public.safety_case_events for each row execute function public.safety_lock_case_graph_before_change_v3();
create trigger safety_lock_event_claim_edge_v3 before insert or update or delete
on public.safety_event_claims for each row execute function public.safety_lock_case_graph_before_change_v3();
create trigger safety_lock_claim_source_edge_v3 before insert or update or delete
on public.safety_claim_sources for each row execute function public.safety_lock_case_graph_before_change_v3();

-- A previously finalized case lacks the new graph binding and requires two
-- fresh independent decisions before it can be exposed again.
delete from public.safety_public_case_projection p
where not exists (select 1 from public.safety_publication_decisions d
    where d.id = p.publication_decision_id and d.case_authority_fingerprint is not null);

commit;
