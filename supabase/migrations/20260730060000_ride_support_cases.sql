-- Elysium Viajes support cases. Case creation is actor-bound and idempotent.
-- Financial corrections must use compensating ledger transactions; support
-- never mutates journal entries.

alter table public.ride_command_receipts
    drop constraint if exists ride_command_receipts_command_type_check;
alter table public.ride_command_receipts
    add constraint ride_command_receipts_command_type_check check (
        command_type in (
            'CREATE_REQUEST', 'SUBMIT_OFFER', 'ACCEPT_OFFER', 'CLAIM',
            'DRIVER_EN_ROUTE', 'DRIVER_ARRIVED', 'ISSUE_BOARDING_PIN',
            'VERIFY_BOARDING_PIN', 'START', 'CANCEL', 'COMPLETE',
            'ENROLL_DRIVER_PILOT', 'SAFETY_SIGNAL', 'OPEN_SUPPORT_CASE'
        )
    );

create table if not exists public.ride_support_cases (
    id uuid primary key default extensions.gen_random_uuid(),
    trip_id uuid not null references public.ride_requests(id) on delete restrict,
    opened_by uuid not null references auth.users(id) on delete restrict,
    category text not null check (
        category in (
            'LOST_ITEM', 'WRONG_CHARGE', 'WRONG_DRIVER', 'WRONG_PASSENGER',
            'ROUTE_ISSUE', 'ACCIDENT', 'CANCELLATION', 'PAYMENT',
            'COMMISSION', 'DOCUMENT', 'BEHAVIOR', 'OTHER'
        )
    ),
    severity text not null check (severity in ('STANDARD', 'PRIORITY', 'SAFETY')),
    issue_summary text not null check (char_length(issue_summary) between 10 and 1000),
    evidence_manifest_sha256 text check (
        evidence_manifest_sha256 is null or
        evidence_manifest_sha256 ~ '^[a-f0-9]{64}$'
    ),
    status text not null default 'OPEN' check (status = 'OPEN'),
    assignee_id uuid references auth.users(id) on delete set null,
    resolution_code text,
    financial_adjustment_transaction_id uuid references
        public.ride_ledger_transactions(id) on delete restrict,
    idempotency_key text not null check (
        char_length(idempotency_key) between 16 and 128 and
        idempotency_key ~ '^[A-Za-z0-9._:-]+$'
    ),
    created_at timestamptz not null default now(),
    unique (opened_by, idempotency_key)
);

create index if not exists ride_support_cases_trip_created_idx
    on public.ride_support_cases(trip_id, created_at desc);

create table if not exists public.ride_support_case_timeline (
    id bigint generated always as identity primary key,
    case_id uuid not null references public.ride_support_cases(id) on delete restrict,
    actor_id uuid references auth.users(id) on delete set null,
    event_type text not null check (char_length(event_type) between 1 and 80),
    payload jsonb not null default '{}'::jsonb,
    idempotency_key text not null unique,
    created_at timestamptz not null default now()
);

create index if not exists ride_support_timeline_case_idx
    on public.ride_support_case_timeline(case_id, id);

create or replace function public.ride_open_support_case_v2(
    p_trip_id uuid,
    p_expected_version bigint,
    p_category text,
    p_issue_summary text,
    p_evidence_manifest_sha256 text,
    p_idempotency_key text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := (select auth.uid());
    v_request public.ride_requests%rowtype;
    v_category text := upper(trim(coalesce(p_category, '')));
    v_summary text := trim(coalesce(p_issue_summary, ''));
    v_severity text;
    v_request_hash text;
    v_replay jsonb;
    v_response jsonb;
    v_case_id uuid;
begin
    if v_user_id is null then
        return public.ride_command_error(
            'UNAUTHENTICATED', 'Autenticación requerida', false
        );
    end if;
    if coalesce(p_expected_version, 0) <= 0 or
       coalesce(p_idempotency_key, '') !~ '^[A-Za-z0-9._:-]{16,128}$' or
       v_category not in (
           'LOST_ITEM', 'WRONG_CHARGE', 'WRONG_DRIVER', 'WRONG_PASSENGER',
           'ROUTE_ISSUE', 'ACCIDENT', 'CANCELLATION', 'PAYMENT',
           'COMMISSION', 'DOCUMENT', 'BEHAVIOR', 'OTHER'
       ) or
       char_length(v_summary) not between 10 and 1000 or
       (
           p_evidence_manifest_sha256 is not null and
           p_evidence_manifest_sha256 !~ '^[a-f0-9]{64}$'
       )
    then
        return public.ride_command_error(
            'VALIDATION_ERROR', 'Caso de soporte inválido', false
        );
    end if;

    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(
            v_user_id::text || ':' || p_idempotency_key,
            0
        )
    );

    v_request_hash := public.ride_command_hash(jsonb_build_object(
        'command', 'OPEN_SUPPORT_CASE',
        'trip_id', p_trip_id,
        'expected_version', p_expected_version,
        'category', v_category,
        'issue_summary', v_summary,
        'evidence_manifest_sha256', p_evidence_manifest_sha256
    ));
    v_replay := public.ride_command_replay(
        v_user_id, p_idempotency_key, v_request_hash
    );
    if v_replay is not null then
        return v_replay;
    end if;

    select r.*
      into v_request
      from public.ride_requests r
     where r.id = p_trip_id
     for share;

    if not found then
        return public.ride_command_error('NOT_FOUND', 'Viaje no encontrado', false);
    end if;
    if v_user_id <> v_request.passenger_id and
       v_user_id is distinct from v_request.assigned_driver_id
    then
        return public.ride_command_error(
            'FORBIDDEN', 'Actor no autorizado para este viaje', false
        );
    end if;
    if v_request.version <> p_expected_version then
        return public.ride_command_error(
            'VERSION_CONFLICT', 'La versión del viaje cambió', true,
            jsonb_build_object('current_version', v_request.version)
        );
    end if;

    v_severity := case
        when v_category in ('ACCIDENT', 'WRONG_DRIVER', 'WRONG_PASSENGER', 'BEHAVIOR')
            then 'SAFETY'
        when v_category in ('WRONG_CHARGE', 'PAYMENT', 'COMMISSION', 'DOCUMENT')
            then 'PRIORITY'
        else 'STANDARD'
    end;

    insert into public.ride_support_cases(
        trip_id, opened_by, category, severity, issue_summary,
        evidence_manifest_sha256, idempotency_key
    )
    values (
        p_trip_id, v_user_id, v_category, v_severity, v_summary,
        p_evidence_manifest_sha256, p_idempotency_key
    )
    returning id into v_case_id;

    insert into public.ride_support_case_timeline(
        case_id, actor_id, event_type, payload, idempotency_key
    )
    values (
        v_case_id, v_user_id, 'CASE_OPENED',
        jsonb_build_object(
            'category', v_category,
            'severity', v_severity,
            'trip_version', v_request.version,
            'evidence_manifest_provided',
                p_evidence_manifest_sha256 is not null
        ),
        p_idempotency_key || ':timeline'
    );

    insert into public.ride_trip_events(
        trip_id, actor_id, event_type, from_state, to_state,
        payload, idempotency_key
    )
    values (
        p_trip_id, v_user_id, 'SUPPORT_CASE_OPENED',
        v_request.state, v_request.state,
        jsonb_build_object(
            'case_id', v_case_id,
            'category', v_category,
            'severity', v_severity,
            'version', v_request.version
        ),
        p_idempotency_key
    );

    v_response := public.ride_command_success(jsonb_build_object(
        'status', 'SUPPORT_CASE_OPENED',
        'trip_id', p_trip_id,
        'version', v_request.version,
        'case_id', v_case_id,
        'category', v_category,
        'severity', v_severity
    ));
    return public.ride_record_command_receipt(
        v_user_id, p_trip_id, 'OPEN_SUPPORT_CASE', p_idempotency_key,
        v_request_hash, v_response
    );
end;
$$;

drop trigger if exists ride_support_cases_no_delete
    on public.ride_support_cases;
create trigger ride_support_cases_no_delete
before delete on public.ride_support_cases
for each row execute function public.ride_reject_immutable_change();

drop trigger if exists ride_support_timeline_immutable
    on public.ride_support_case_timeline;
create trigger ride_support_timeline_immutable
before update or delete on public.ride_support_case_timeline
for each row execute function public.ride_reject_immutable_change();

alter table public.ride_support_cases enable row level security;
alter table public.ride_support_case_timeline enable row level security;

drop policy if exists ride_support_cases_participant_select
    on public.ride_support_cases;
create policy ride_support_cases_participant_select
on public.ride_support_cases for select to authenticated
using (public.ride_is_participant(trip_id));

drop policy if exists ride_support_timeline_participant_select
    on public.ride_support_case_timeline;
create policy ride_support_timeline_participant_select
on public.ride_support_case_timeline for select to authenticated
using (
    exists (
        select 1
          from public.ride_support_cases c
         where c.id = case_id
           and public.ride_is_participant(c.trip_id)
    )
);

revoke all on public.ride_support_cases from anon, authenticated;
revoke all on public.ride_support_case_timeline from anon, authenticated;
grant select on public.ride_support_cases to authenticated;
grant select on public.ride_support_case_timeline to authenticated;

revoke all on function public.ride_open_support_case_v2(
    uuid, bigint, text, text, text, text
) from public;
grant execute on function public.ride_open_support_case_v2(
    uuid, bigint, text, text, text, text
) to authenticated;

comment on column public.ride_support_cases.financial_adjustment_transaction_id is
    'Optional reference to a separately authorized compensating ledger transaction; support never edits ledger postings.';
