begin;
-- Withdrawal removes public authority, while an active hold preserves original private content.
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
    perform pg_advisory_xact_lock(hashtextextended('SAFETY-LEGAL-RETENTION',0));
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
        -- Direct event reports as well as claim/source reports can qualify a case.
        delete from public.safety_public_case_projection p
        where exists(select 1 from safety_private.case_report_ids_v1(p.case_id) g where g.report_id=p_report_id);
        update public.safety_reports
        set state = 'WITHDRAWN', state_version = state_version + 1,
            updated_at = now()
        where id = p_report_id
        returning state_version into v_version;
        if not public.safety_report_is_held_v1(p_report_id) then
            update safety_private.report_content
            set narrative = '', source_relation = 'UNKNOWN',
                latitude = null, longitude = null, accuracy_meters = null,
                location_source = 'NONE'
            where report_id = p_report_id;
        end if;
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
