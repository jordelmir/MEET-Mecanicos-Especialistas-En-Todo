begin;

-- Case decisions reuse the same append-only, two-person publication ledger.
-- Claim decisions keep their V3 requirements; legacy rows remain labelled.
alter table public.safety_publication_decisions
    add column if not exists case_state_version bigint;
alter table public.safety_publication_decisions
    drop constraint safety_publication_v3_authority_chk;
alter table public.safety_publication_decisions
    add constraint safety_publication_v3_authority_chk check (
        decision_phase = 'LEGACY' or (
            reviewer_id is not null
            and reason_code is not null
            and char_length(reason_code) between 3 and 80
            and policy_version is not null
            and policy_version = 'SAFETY-PUBLICATION-V3'
            and (
                (claim_id is not null
                 and claim_state_version is not null
                 and claim_state_version > 0
                 and report_state_version is not null
                 and report_state_version > 0
                 and case_state_version is null)
                or
                (case_id is not null
                 and case_state_version is not null
                 and case_state_version > 0
                 and claim_state_version is null
                 and report_state_version is null)
            )
            and (
                (decision_phase = 'CANDIDATE'
                 and recommendation is not null
                 and supersedes_decision_id is null
                 and decision in ('HOLD', 'REJECT'))
                or
                (decision_phase = 'FINAL'
                 and recommendation is null
                 and supersedes_decision_id is not null
                 and decision in ('PUBLISH', 'REDACT', 'REJECT'))
            )
        )
    );
create index if not exists safety_publication_v3_case_idx
    on public.safety_publication_decisions(case_id, created_at desc)
    where case_id is not null;

-- Evidence eligibility is derived from server-owned case/event/claim/source
-- relationships. A case title, source count supplied by a caller, or a lone
-- report never satisfies this predicate.
create or replace function public.safety_case_has_publication_provenance_v3(
    p_case_id uuid
) returns boolean language sql stable security definer set search_path = '' as $$
    select exists (
        select 1
        from public.safety_case_events ce
        join public.safety_events e on e.id = ce.event_id
        join public.safety_event_claims ec on ec.event_id = e.id
        join public.safety_claims c on c.id = ec.claim_id
        join public.safety_reports r on r.id = c.report_id
        join public.safety_claim_sources cs on cs.claim_id = c.id
        join public.safety_sources s on s.id = cs.source_id
        join public.safety_reports sr on sr.id = s.report_id
        where ce.case_id = p_case_id
          and ec.relation_type in ('SUPPORTS', 'IMPLIES')
          and c.state in ('DOCUMENTED', 'CORROBORATED', 'STRONGLY_CORROBORATED')
          and r.state <> 'WITHDRAWN'
          and sr.state <> 'WITHDRAWN'
          and cs.role in ('SUPPORTING', 'CORROBORATING', 'PRIMARY')
          and s.source_type not in ('ANONYMOUS', 'UNKNOWN')
    );
$$;
revoke all on function public.safety_case_has_publication_provenance_v3(uuid)
    from public, anon, authenticated, service_role;

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
        policy_version, decision_phase, recommendation, case_state_version
    ) values (
        p_case_id, case when p_recommendation = 'REJECT' then 'REJECT' else 'HOLD' end,
        v_actor, now(), btrim(p_reason_code), 'SAFETY-PUBLICATION-V3',
        'CANDIDATE', p_recommendation, v_case.state_version
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
revoke all on function public.safety_recommend_case_publication_v1(
    uuid, text, text, uuid) from public, anon, service_role;
grant execute on function public.safety_recommend_case_publication_v1(
    uuid, text, text, uuid) to authenticated;

-- A public case row must carry its final, independent V3 decision. The
-- preceding migration quarantined all historical rows before these NOT NULLs.
alter table public.safety_public_case_projection
    add column if not exists publication_decision_id uuid
        references public.safety_publication_decisions(id) on delete restrict,
    add column if not exists case_state_version bigint;
alter table public.safety_public_case_projection
    add constraint safety_public_case_projection_case_fk
        foreign key (case_id) references public.safety_cases(id) on delete restrict;
alter table public.safety_public_case_projection
    alter column publication_decision_id set not null,
    alter column case_state_version set not null,
    alter column confidence_score drop not null,
    alter column event_count drop not null,
    alter column claim_count drop not null,
    alter column source_count drop not null,
    alter column evidence_count drop not null;
revoke insert, update, delete on public.safety_public_case_projection
    from anon, authenticated, service_role;

create table if not exists safety_private.public_case_history_v3 (
    case_id uuid not null,
    server_version bigint not null,
    snapshot jsonb not null,
    archived_at timestamptz not null default now(),
    primary key (case_id, server_version)
);
revoke all on safety_private.public_case_history_v3
    from public, anon, authenticated;
grant select on safety_private.public_case_history_v3 to service_role;
create or replace function public.safety_archive_public_case_v3()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
    insert into safety_private.public_case_history_v3(
        case_id, server_version, snapshot
    ) values (old.case_id, old.server_version, to_jsonb(old))
    on conflict (case_id, server_version) do nothing;
    if tg_op = 'UPDATE' then return new; end if;
    return old;
end;
$$;
revoke all on function public.safety_archive_public_case_v3()
    from public, anon, authenticated, service_role;
create trigger safety_archive_public_case_v3
    before update or delete on public.safety_public_case_projection
    for each row execute function public.safety_archive_public_case_v3();

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
revoke all on function public.safety_guard_public_case_v3()
    from public, anon, authenticated, service_role;
create trigger safety_guard_public_case_v3
    before insert or update on public.safety_public_case_projection
    for each row execute function public.safety_guard_public_case_v3();

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
       or v_case.state_version is distinct from v_candidate.case_state_version then
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
        limit 1 for share of c, r, s, sr;
        if v_eligible_claim_id is null then
            raise exception using errcode = '22023',
                message = 'CASE_REQUIRES_DOCUMENTED_PROVENANCE';
        end if;
    end if;
    insert into public.safety_publication_decisions(
        case_id, decision, reviewer_id, reviewed_at,
        supersedes_decision_id, reason_code, policy_version,
        decision_phase, case_state_version
    ) values (
        v_case.id, p_final_decision, v_actor, now(), v_candidate.id,
        btrim(p_reason_code), 'SAFETY-PUBLICATION-V3', 'FINAL',
        v_case.state_version
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
        insert into public.safety_public_case_projection(
            case_id, case_type, title, public_summary, lifecycle,
            confidence_score, event_count, claim_count, source_count,
            evidence_count, published_at, last_updated_at, server_version,
            publication_decision_id, case_state_version
        ) values (
            v_case.id, 'SAFETY_CASE', 'Caso con revisión independiente', '',
            v_case.status, null, null, null, null, null,
            now(), now(), 1, v_final_id, v_case.state_version
        ) on conflict (case_id) do update set
            lifecycle = excluded.lifecycle,
            last_updated_at = now(),
            publication_decision_id = excluded.publication_decision_id,
            case_state_version = excluded.case_state_version,
            server_version = public.safety_public_case_projection.server_version + 1;
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
revoke all on function public.safety_finalize_case_publication_v1(
    uuid, text, text, uuid) from public, anon, service_role;
grant execute on function public.safety_finalize_case_publication_v1(
    uuid, text, text, uuid) to authenticated;

drop policy if exists safety_public_case_projection_v3_closed
on public.safety_public_case_projection;
create policy safety_public_case_projection_v3_published_read
on public.safety_public_case_projection for select to authenticated using (
    exists (select 1 from public.runtime_feature_gates
            where key = 'safety_public_cases' and enabled)
);

-- Changes to the case or any provenance that qualified its public header
-- revoke that header in the same transaction. A new pair of reviewers must
-- publish again from the new authoritative state.
create or replace function public.safety_invalidate_public_case_v3()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
    if tg_table_name = 'safety_cases' then
        delete from public.safety_public_case_projection
        where case_id = new.id;
    elsif tg_table_name = 'safety_claims' then
        delete from public.safety_public_case_projection p
        using public.safety_case_events ce, public.safety_event_claims ec
        where p.case_id = ce.case_id
          and ce.event_id = ec.event_id
          and ec.claim_id = new.id;
    elsif tg_table_name = 'safety_reports' then
        delete from public.safety_public_case_projection p
        where exists (
            select 1 from public.safety_case_events ce
            join public.safety_event_claims ec on ec.event_id = ce.event_id
            join public.safety_claims c on c.id = ec.claim_id
            left join public.safety_claim_sources cs on cs.claim_id = c.id
            left join public.safety_sources s on s.id = cs.source_id
            where ce.case_id = p.case_id
              and (c.report_id = new.id or s.report_id = new.id)
        );
    elsif tg_table_name = 'safety_sources' then
        delete from public.safety_public_case_projection p
        where exists (
            select 1 from public.safety_case_events ce
            join public.safety_event_claims ec on ec.event_id = ce.event_id
            join public.safety_claim_sources cs on cs.claim_id = ec.claim_id
            where ce.case_id = p.case_id and cs.source_id = new.id
        );
    end if;
    return new;
end;
$$;
revoke all on function public.safety_invalidate_public_case_v3()
    from public, anon, authenticated, service_role;
create trigger safety_case_publication_invalidate_v3
    after update on public.safety_cases
    for each row execute function public.safety_invalidate_public_case_v3();
create trigger safety_claim_case_publication_invalidate_v3
    after update on public.safety_claims
    for each row execute function public.safety_invalidate_public_case_v3();
create trigger safety_report_case_publication_invalidate_v3
    after update on public.safety_reports
    for each row execute function public.safety_invalidate_public_case_v3();
create trigger safety_source_case_publication_invalidate_v3
    after update on public.safety_sources
    for each row execute function public.safety_invalidate_public_case_v3();

commit;
