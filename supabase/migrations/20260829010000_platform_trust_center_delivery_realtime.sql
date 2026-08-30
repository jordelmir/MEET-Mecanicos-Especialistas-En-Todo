-- Trust Center delivery V2: every registration enters one durable queue and
-- Realtime is only a wake-up signal for an RLS-protected authoritative reload.

alter table public.service_verification_applications
    add column if not exists correlation_id uuid not null default gen_random_uuid();

alter table public.service_verification_audit_events
    add column if not exists correlation_id uuid;

update public.service_verification_audit_events e
   set correlation_id = a.correlation_id
  from public.service_verification_applications a
 where e.application_id = a.id
   and e.correlation_id is null;

alter table public.service_verification_audit_events
    alter column correlation_id set not null;

alter table public.service_verification_applications
    drop constraint if exists service_verification_applications_service_type_check;
alter table public.service_verification_applications
    add constraint service_verification_applications_service_type_check check(service_type in (
        'PASSENGER','RIDE_DRIVER','TOW_TRUCK','MECHANIC','PARTS_STORE','SERVICE_PROVIDER',
        'WORKSHOP','AUTO_LOCKSMITH','LAWYER','NOTARY','PROPERTY_BROKER','PROPERTY_SELLER',
        'FUEL_STATION_STAFF','FLEET_OPERATOR'
    ));

create or replace function public.meet_submit_service_verification_v2(
    p_service_type text,
    p_profile_reference text,
    p_display_name text,
    p_business_name text default null,
    p_phone text default null,
    p_location_label text default null,
    p_license_reference text default null,
    p_evidence_manifest_sha256 text default null,
    p_correlation_id uuid default gen_random_uuid()
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := (select auth.uid());
    v_service_type text := upper(trim(coalesce(p_service_type, '')));
    v_previous_status text;
    v_correlation_id uuid := coalesce(p_correlation_id, gen_random_uuid());
    v_application public.service_verification_applications%rowtype;
begin
    if v_user_id is null then
        raise exception using errcode = '42501', message = 'UNAUTHENTICATED';
    end if;
    if v_service_type not in (
           'PASSENGER','RIDE_DRIVER','TOW_TRUCK','MECHANIC','PARTS_STORE','SERVICE_PROVIDER',
           'WORKSHOP','AUTO_LOCKSMITH','LAWYER','NOTARY','PROPERTY_BROKER','PROPERTY_SELLER',
           'FUEL_STATION_STAFF','FLEET_OPERATOR'
       ) or
       char_length(trim(coalesce(p_profile_reference, ''))) not between 1 and 160 or
       char_length(trim(coalesce(p_display_name, ''))) not between 2 and 120 or
       char_length(trim(coalesce(p_business_name, ''))) > 160 or
       char_length(trim(coalesce(p_phone, ''))) > 40 or
       char_length(trim(coalesce(p_location_label, ''))) > 240 or
       char_length(trim(coalesce(p_license_reference, ''))) > 120 or
       (p_evidence_manifest_sha256 is not null and
        p_evidence_manifest_sha256 !~ '^[a-f0-9]{64}$')
    then
        raise exception using errcode = '22023', message = 'INVALID_VERIFICATION_APPLICATION';
    end if;

    select status into v_previous_status
      from public.service_verification_applications
     where applicant_user_id = v_user_id
       and service_type = v_service_type
       and profile_reference = trim(p_profile_reference)
     for update;

    insert into public.service_verification_applications(
        applicant_user_id, service_type, profile_reference, display_name,
        business_name, phone, location_label, license_reference,
        evidence_manifest_sha256, status, decision_reason, reviewed_at,
        reviewed_by, submitted_at, updated_at, correlation_id
    ) values (
        v_user_id, v_service_type, trim(p_profile_reference), trim(p_display_name),
        nullif(trim(coalesce(p_business_name, '')), ''),
        nullif(trim(coalesce(p_phone, '')), ''),
        nullif(trim(coalesce(p_location_label, '')), ''),
        nullif(trim(coalesce(p_license_reference, '')), ''),
        p_evidence_manifest_sha256, 'PENDING', null, null, null, now(), now(),
        v_correlation_id
    )
    on conflict (applicant_user_id, service_type, profile_reference) do update
       set display_name = excluded.display_name,
           business_name = excluded.business_name,
           phone = excluded.phone,
           location_label = excluded.location_label,
           license_reference = excluded.license_reference,
           evidence_manifest_sha256 = excluded.evidence_manifest_sha256,
           status = 'PENDING',
           decision_reason = null,
           reviewed_at = null,
           reviewed_by = null,
           submitted_at = now(),
           updated_at = now(),
           correlation_id = excluded.correlation_id
    returning * into v_application;

    insert into public.service_verification_audit_events(
        application_id, actor_id, event_type, from_status, to_status, reason,
        correlation_id
    ) values (
        v_application.id, v_user_id,
        case when v_previous_status is null then 'SUBMITTED' else 'RESUBMITTED' end,
        v_previous_status, 'PENDING', 'Application submitted for human review',
        v_correlation_id
    );

    if v_service_type <> 'PASSENGER' then
        insert into public.principal_capabilities(
            principal_id, capability, activation_state, verified_at, updated_at
        ) values (v_user_id, v_service_type, 'SUBMITTED', null, now())
        on conflict (principal_id, capability) do update
           set activation_state = 'SUBMITTED',
               verified_at = null,
               updated_at = now();
    end if;

    return jsonb_build_object(
        'id', v_application.id,
        'status', v_application.status,
        'service_type', v_application.service_type,
        'correlation_id', v_application.correlation_id,
        'submitted_at', v_application.submitted_at
    );
end;
$$;

-- Old APKs remain compatible while gaining the same durable V2 behavior.
create or replace function public.meet_submit_service_verification_v1(
    p_service_type text,
    p_profile_reference text,
    p_display_name text,
    p_business_name text default null,
    p_phone text default null,
    p_location_label text default null,
    p_license_reference text default null,
    p_evidence_manifest_sha256 text default null
)
returns jsonb
language sql
security definer
set search_path = ''
as $$
    select public.meet_submit_service_verification_v2(
        p_service_type, p_profile_reference, p_display_name, p_business_name,
        p_phone, p_location_label, p_license_reference,
        p_evidence_manifest_sha256, gen_random_uuid()
    );
$$;

create or replace function public.meet_submit_capability_application_v1(
    p_capability text,
    p_profile_reference text,
    p_display_name text,
    p_evidence_manifest_sha256 text
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_result jsonb;
begin
    v_result := public.meet_submit_service_verification_v2(
        p_capability, p_profile_reference, p_display_name, null, null, null,
        null, p_evidence_manifest_sha256, gen_random_uuid()
    );
    return (v_result->>'id')::uuid;
end;
$$;

create or replace function public.meet_sync_ride_driver_review_queue()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_application_id uuid;
    v_previous_status text;
    v_correlation_id uuid := gen_random_uuid();
begin
    if new.document_review_status in ('SUBMITTED', 'UNDER_REVIEW') then
        select status into v_previous_status
          from public.service_verification_applications
         where applicant_user_id = new.driver_id
           and service_type = 'RIDE_DRIVER'
           and profile_reference = new.id::text
         for update;

        insert into public.service_verification_applications(
            applicant_user_id, service_type, profile_reference, display_name,
            evidence_manifest_sha256, status, submitted_at, updated_at,
            correlation_id
        ) values (
            new.driver_id, 'RIDE_DRIVER', new.id::text, new.display_name,
            new.evidence_manifest_sha256, 'PENDING', now(), now(),
            v_correlation_id
        )
        on conflict (applicant_user_id, service_type, profile_reference) do update
           set display_name = excluded.display_name,
               evidence_manifest_sha256 = excluded.evidence_manifest_sha256,
               status = 'PENDING',
               decision_reason = null,
               reviewed_at = null,
               reviewed_by = null,
               submitted_at = now(),
               updated_at = now(),
               correlation_id = excluded.correlation_id
        returning id into v_application_id;

        insert into public.service_verification_audit_events(
            application_id, actor_id, event_type, from_status, to_status,
            reason, correlation_id
        ) values (
            v_application_id, new.driver_id,
            case when v_previous_status is null then 'SUBMITTED' else 'RESUBMITTED' end,
            v_previous_status, 'PENDING', 'Driver evidence submitted for human review',
            v_correlation_id
        );

        insert into public.principal_capabilities(
            principal_id, capability, activation_state, verified_at, updated_at
        ) values (new.driver_id, 'RIDE_DRIVER', 'SUBMITTED', null, now())
        on conflict (principal_id, capability) do update
           set activation_state = 'SUBMITTED', verified_at = null, updated_at = now();
    end if;
    return new;
end;
$$;

create or replace function public.meet_own_verification_applications_v1()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_actor uuid := (select auth.uid());
    v_items jsonb;
begin
    if v_actor is null then
        raise exception using errcode = '42501', message = 'UNAUTHENTICATED';
    end if;

    select coalesce(jsonb_agg(jsonb_build_object(
        'id', a.id,
        'applicant_user_id', a.applicant_user_id,
        'applicant_email', u.email,
        'service_type', a.service_type,
        'profile_reference', a.profile_reference,
        'display_name', a.display_name,
        'business_name', a.business_name,
        'phone', a.phone,
        'location_label', a.location_label,
        'license_reference', a.license_reference,
        'evidence_manifest_sha256', a.evidence_manifest_sha256,
        'status', a.status,
        'decision_reason', a.decision_reason,
        'submitted_at', a.submitted_at,
        'reviewed_at', a.reviewed_at,
        'correlation_id', a.correlation_id
    ) order by a.submitted_at desc, a.id), '[]'::jsonb)
      into v_items
      from public.service_verification_applications a
      join auth.users u on u.id = a.applicant_user_id
     where a.applicant_user_id = v_actor;

    return jsonb_build_object(
        'items', v_items,
        'server_timestamp', now()
    );
end;
$$;

create or replace function public.meet_owner_verification_queue_v2(
    p_status text default 'PENDING',
    p_limit integer default 100
)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_items jsonb;
    v_counts jsonb;
begin
    if not public.meet_is_platform_owner() then
        raise exception using errcode = '42501', message = 'PLATFORM_OWNER_REQUIRED';
    end if;
    if p_status not in ('ALL','PENDING','APPROVED','REJECTED','SUSPENDED') or
       p_limit not between 1 and 200
    then
        raise exception using errcode = '22023', message = 'INVALID_QUEUE_FILTER';
    end if;

    select jsonb_build_object(
        'PENDING', count(*) filter (where status = 'PENDING'),
        'APPROVED', count(*) filter (where status = 'APPROVED'),
        'REJECTED', count(*) filter (where status = 'REJECTED'),
        'SUSPENDED', count(*) filter (where status = 'SUSPENDED'),
        'ALL', count(*)
    ) into v_counts
      from public.service_verification_applications;

    select coalesce(jsonb_agg(jsonb_build_object(
        'id', a.id,
        'applicant_user_id', a.applicant_user_id,
        'applicant_email', u.email,
        'service_type', a.service_type,
        'profile_reference', a.profile_reference,
        'display_name', a.display_name,
        'business_name', a.business_name,
        'phone', a.phone,
        'location_label', a.location_label,
        'license_reference', a.license_reference,
        'evidence_manifest_sha256', a.evidence_manifest_sha256,
        'status', a.status,
        'decision_reason', a.decision_reason,
        'submitted_at', a.submitted_at,
        'reviewed_at', a.reviewed_at,
        'correlation_id', a.correlation_id
    ) order by a.submitted_at asc, a.id), '[]'::jsonb)
      into v_items
      from (
          select *
            from public.service_verification_applications
           where p_status = 'ALL' or status = p_status
           order by submitted_at asc, id
           limit p_limit
      ) a
      join auth.users u on u.id = a.applicant_user_id;

    return jsonb_build_object(
        'items', v_items,
        'counts', v_counts,
        'status', p_status,
        'server_timestamp', now()
    );
end;
$$;

create or replace function public.meet_owner_decide_verification_v1(
    p_application_id uuid,
    p_decision text,
    p_reason text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_owner uuid := (select auth.uid());
    v_application public.service_verification_applications%rowtype;
    v_previous_status text;
begin
    if not public.meet_is_platform_owner() then
        raise exception using errcode = '42501', message = 'PLATFORM_OWNER_REQUIRED';
    end if;
    if p_decision not in ('APPROVED', 'REJECTED', 'SUSPENDED') or
       char_length(trim(coalesce(p_reason, ''))) not between 3 and 500
    then
        raise exception using errcode = '22023', message = 'INVALID_REVIEW_DECISION';
    end if;

    select * into v_application
      from public.service_verification_applications
     where id = p_application_id
     for update;
    if not found then
        raise exception using errcode = 'P0002', message = 'APPLICATION_NOT_FOUND';
    end if;
    v_previous_status := v_application.status;

    update public.service_verification_applications
       set status = p_decision,
           decision_reason = trim(p_reason),
           reviewed_at = now(),
           reviewed_by = v_owner,
           updated_at = now()
     where id = p_application_id
    returning * into v_application;

    insert into public.service_verification_audit_events(
        application_id, actor_id, event_type, from_status, to_status, reason,
        correlation_id
    ) values (
        v_application.id, v_owner, p_decision, v_previous_status, p_decision,
        trim(p_reason), v_application.correlation_id
    );

    if v_application.service_type = 'RIDE_DRIVER' then
        update public.ride_driver_vehicles
           set verification_status = case
                   when p_decision = 'APPROVED' then 'VERIFIED'
                   when p_decision = 'REJECTED' then 'REJECTED'
                   else 'SUSPENDED'
               end,
               verification_method = 'MANUAL_REVIEW',
               document_review_status = p_decision,
               is_active = case when p_decision = 'APPROVED' then is_active else false end,
               updated_at = now()
         where id::text = v_application.profile_reference
           and driver_id = v_application.applicant_user_id;
    end if;

    return jsonb_build_object(
        'id', v_application.id,
        'status', v_application.status,
        'correlation_id', v_application.correlation_id,
        'reviewed_at', v_application.reviewed_at
    );
end;
$$;

-- The public review surface always requires an AAL2 session and projects the
-- human decision into the shared capability authority table atomically.
create or replace function public.meet_owner_decide_verification_v2(
    p_application_id uuid,
    p_decision text,
    p_reason text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_result jsonb;
    v_application public.service_verification_applications%rowtype;
begin
    if not public.meet_session_has_aal2() then
        raise exception using errcode = '42501', message = 'AAL2_REQUIRED';
    end if;

    v_result := public.meet_owner_decide_verification_v1(
        p_application_id, p_decision, p_reason
    );
    select * into strict v_application
      from public.service_verification_applications
     where id = p_application_id;

    if v_application.service_type <> 'PASSENGER' then
        insert into public.principal_capabilities(
            principal_id, capability, activation_state, verified_at, updated_at
        ) values (
            v_application.applicant_user_id,
            v_application.service_type,
            case p_decision
                when 'APPROVED' then 'APPROVED'
                when 'REJECTED' then 'REJECTED'
                else 'SUSPENDED'
            end,
            case when p_decision = 'APPROVED' then now() end,
            now()
        ) on conflict (principal_id, capability) do update
           set activation_state = excluded.activation_state,
               verified_at = excluded.verified_at,
               updated_at = now();
    end if;

    return v_result;
end;
$$;

do $$
begin
    if exists (select 1 from pg_publication where pubname = 'supabase_realtime')
       and not exists (
           select 1
             from pg_publication_tables
            where pubname = 'supabase_realtime'
              and schemaname = 'public'
              and tablename = 'service_verification_applications'
       )
    then
        alter publication supabase_realtime add table public.service_verification_applications;
    end if;
end;
$$;

revoke all on function public.meet_submit_service_verification_v2(
    text,text,text,text,text,text,text,text,uuid
) from public;
revoke all on function public.meet_submit_service_verification_v1(
    text,text,text,text,text,text,text,text
) from public;
revoke all on function public.meet_submit_capability_application_v1(
    text,text,text,text
) from public;
revoke all on function public.meet_sync_ride_driver_review_queue() from public;
revoke all on function public.meet_own_verification_applications_v1() from public;
revoke all on function public.meet_owner_verification_queue_v2(text,integer) from public;
revoke all on function public.meet_owner_decide_verification_v1(uuid,text,text) from public;
revoke all on function public.meet_owner_decide_verification_v2(uuid,text,text) from public;

grant execute on function public.meet_submit_service_verification_v2(
    text,text,text,text,text,text,text,text,uuid
) to authenticated;
grant execute on function public.meet_submit_service_verification_v1(
    text,text,text,text,text,text,text,text
) to authenticated;
grant execute on function public.meet_submit_capability_application_v1(
    text,text,text,text
) to authenticated;
grant execute on function public.meet_own_verification_applications_v1() to authenticated;
grant execute on function public.meet_owner_verification_queue_v2(text,integer) to authenticated;
grant execute on function public.meet_owner_decide_verification_v2(uuid,text,text) to authenticated;

comment on function public.meet_submit_service_verification_v2(
    text,text,text,text,text,text,text,text,uuid
) is 'Unified idempotent registration delivery with correlation and human-review authority.';
comment on function public.meet_owner_verification_queue_v2(text,integer) is
    'Owner-only queue snapshot with status counts. Realtime events are wake-up hints only.';
