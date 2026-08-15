-- MEET Trust Center: one server-authoritative owner account, one review queue
-- for passengers, ride drivers and universal-service providers.

create table if not exists public.platform_authorities (
    user_id uuid primary key references auth.users(id) on delete restrict,
    role text not null check (role = 'PLATFORM_OWNER'),
    email_snapshot text not null,
    active boolean not null default true,
    granted_at timestamptz not null default now()
);

alter table public.platform_authorities enable row level security;
revoke all on table public.platform_authorities from anon, authenticated;

create or replace function public.meet_bootstrap_platform_owner()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    if lower(coalesce(new.email, '')) = 'jordelmir@gmail.com' and
       new.email_confirmed_at is not null
    then
        insert into public.platform_authorities(
            user_id, role, email_snapshot, active
        ) values (
            new.id, 'PLATFORM_OWNER', lower(new.email), true
        )
        on conflict (user_id) do update
           set email_snapshot = excluded.email_snapshot,
               active = true;
    end if;
    return new;
end;
$$;

drop trigger if exists meet_bootstrap_platform_owner_trigger on auth.users;
create trigger meet_bootstrap_platform_owner_trigger
after insert or update of email, email_confirmed_at on auth.users
for each row execute function public.meet_bootstrap_platform_owner();

insert into public.platform_authorities(user_id, role, email_snapshot, active)
select id, 'PLATFORM_OWNER', lower(email), true
  from auth.users
 where lower(coalesce(email, '')) = 'jordelmir@gmail.com'
   and email_confirmed_at is not null
on conflict (user_id) do update
   set email_snapshot = excluded.email_snapshot,
       active = true;

create or replace function public.meet_is_platform_owner()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
          from public.platform_authorities a
          join auth.users u on u.id = a.user_id
         where a.user_id = (select auth.uid())
           and a.role = 'PLATFORM_OWNER'
           and a.active
           and lower(coalesce(u.email, '')) = 'jordelmir@gmail.com'
           and u.email_confirmed_at is not null
    );
$$;

revoke all on function public.meet_bootstrap_platform_owner() from public;
revoke all on function public.meet_is_platform_owner() from public;
grant execute on function public.meet_is_platform_owner() to authenticated;

create table if not exists public.service_verification_applications (
    id uuid primary key default gen_random_uuid(),
    applicant_user_id uuid not null references auth.users(id) on delete restrict,
    service_type text not null check (service_type in (
        'PASSENGER', 'RIDE_DRIVER', 'TOW_TRUCK', 'MECHANIC', 'PARTS_STORE',
        'SERVICE_PROVIDER'
    )),
    profile_reference text not null,
    display_name text not null check (char_length(display_name) between 2 and 120),
    business_name text check (business_name is null or char_length(business_name) <= 160),
    phone text check (phone is null or char_length(phone) <= 40),
    location_label text check (location_label is null or char_length(location_label) <= 240),
    license_reference text check (license_reference is null or char_length(license_reference) <= 120),
    evidence_manifest_sha256 text check (
        evidence_manifest_sha256 is null or
        evidence_manifest_sha256 ~ '^[a-f0-9]{64}$'
    ),
    status text not null default 'PENDING' check (
        status in ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED')
    ),
    decision_reason text check (
        decision_reason is null or char_length(decision_reason) between 3 and 500
    ),
    submitted_at timestamptz not null default now(),
    reviewed_at timestamptz,
    reviewed_by uuid references auth.users(id) on delete restrict,
    updated_at timestamptz not null default now(),
    unique (applicant_user_id, service_type, profile_reference)
);

create index if not exists service_verification_queue_idx
    on public.service_verification_applications(status, submitted_at, id);

create table if not exists public.service_verification_audit_events (
    id bigint generated always as identity primary key,
    application_id uuid not null references public.service_verification_applications(id) on delete restrict,
    actor_id uuid not null references auth.users(id) on delete restrict,
    event_type text not null check (event_type in ('SUBMITTED', 'RESUBMITTED', 'APPROVED', 'REJECTED', 'SUSPENDED')),
    from_status text,
    to_status text not null,
    reason text,
    created_at timestamptz not null default now()
);

alter table public.service_verification_applications enable row level security;
alter table public.service_verification_audit_events enable row level security;

drop policy if exists service_verification_applicant_read on public.service_verification_applications;
create policy service_verification_applicant_read
on public.service_verification_applications
for select to authenticated
using (
    applicant_user_id = (select auth.uid()) or
    public.meet_is_platform_owner()
);

drop policy if exists service_verification_owner_audit_read on public.service_verification_audit_events;
create policy service_verification_owner_audit_read
on public.service_verification_audit_events
for select to authenticated
using (public.meet_is_platform_owner());

grant select on public.service_verification_applications to authenticated;
grant select on public.service_verification_audit_events to authenticated;

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
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := (select auth.uid());
    v_previous_status text;
    v_application public.service_verification_applications%rowtype;
begin
    if v_user_id is null then
        raise exception using errcode = '42501', message = 'UNAUTHENTICATED';
    end if;
    if p_service_type not in ('PASSENGER', 'RIDE_DRIVER', 'TOW_TRUCK', 'MECHANIC', 'PARTS_STORE', 'SERVICE_PROVIDER') or
       char_length(trim(coalesce(p_profile_reference, ''))) not between 1 and 160 or
       char_length(trim(coalesce(p_display_name, ''))) not between 2 and 120 or
       (p_evidence_manifest_sha256 is not null and p_evidence_manifest_sha256 !~ '^[a-f0-9]{64}$')
    then
        raise exception using errcode = '22023', message = 'INVALID_VERIFICATION_APPLICATION';
    end if;

    select status into v_previous_status
      from public.service_verification_applications
     where applicant_user_id = v_user_id
       and service_type = p_service_type
       and profile_reference = trim(p_profile_reference)
     for update;

    insert into public.service_verification_applications(
        applicant_user_id, service_type, profile_reference, display_name,
        business_name, phone, location_label, license_reference,
        evidence_manifest_sha256, status, decision_reason, reviewed_at,
        reviewed_by, submitted_at, updated_at
    ) values (
        v_user_id, p_service_type, trim(p_profile_reference), trim(p_display_name),
        nullif(trim(coalesce(p_business_name, '')), ''),
        nullif(trim(coalesce(p_phone, '')), ''),
        nullif(trim(coalesce(p_location_label, '')), ''),
        nullif(trim(coalesce(p_license_reference, '')), ''),
        p_evidence_manifest_sha256, 'PENDING', null, null, null, now(), now()
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
           updated_at = now()
    returning * into v_application;

    insert into public.service_verification_audit_events(
        application_id, actor_id, event_type, from_status, to_status
    ) values (
        v_application.id, v_user_id,
        case when v_previous_status is null then 'SUBMITTED' else 'RESUBMITTED' end,
        v_previous_status, 'PENDING'
    );

    return jsonb_build_object(
        'id', v_application.id,
        'status', v_application.status,
        'submitted_at', v_application.submitted_at
    );
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
begin
    if new.document_review_status in ('SUBMITTED', 'UNDER_REVIEW') then
        select status into v_previous_status
          from public.service_verification_applications
         where applicant_user_id = new.driver_id
           and service_type = 'RIDE_DRIVER'
           and profile_reference = new.id::text;
        insert into public.service_verification_applications(
            applicant_user_id, service_type, profile_reference, display_name,
            evidence_manifest_sha256, status, submitted_at, updated_at
        ) values (
            new.driver_id, 'RIDE_DRIVER', new.id::text, new.display_name,
            new.evidence_manifest_sha256, 'PENDING', now(), now()
        )
        on conflict (applicant_user_id, service_type, profile_reference) do update
           set display_name = excluded.display_name,
               evidence_manifest_sha256 = excluded.evidence_manifest_sha256,
               status = 'PENDING',
               decision_reason = null,
               reviewed_at = null,
               reviewed_by = null,
               submitted_at = now(),
               updated_at = now()
        returning id into v_application_id;

        insert into public.service_verification_audit_events(
            application_id, actor_id, event_type, from_status, to_status
        ) values (
            v_application_id, new.driver_id,
            case when v_previous_status is null then 'SUBMITTED' else 'RESUBMITTED' end,
            v_previous_status, 'PENDING'
        );
    end if;
    return new;
end;
$$;

drop trigger if exists meet_sync_ride_driver_review_queue_trigger on public.ride_driver_vehicles;
create trigger meet_sync_ride_driver_review_queue_trigger
after insert or update of document_review_status, evidence_manifest_sha256
on public.ride_driver_vehicles
for each row execute function public.meet_sync_ride_driver_review_queue();

create or replace function public.meet_owner_verification_queue_v1(
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
begin
    if not public.meet_is_platform_owner() then
        raise exception using errcode = '42501', message = 'PLATFORM_OWNER_REQUIRED';
    end if;
    if p_status not in ('ALL', 'PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED') or
       p_limit not between 1 and 200
    then
        raise exception using errcode = '22023', message = 'INVALID_QUEUE_FILTER';
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
        'reviewed_at', a.reviewed_at
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

    return jsonb_build_object('items', v_items, 'status', p_status);
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
        application_id, actor_id, event_type, from_status, to_status, reason
    ) values (
        v_application.id, v_owner, p_decision,
        v_previous_status, p_decision, trim(p_reason)
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
        'reviewed_at', v_application.reviewed_at
    );
end;
$$;

revoke all on function public.meet_submit_service_verification_v1(
    text, text, text, text, text, text, text, text
) from public;
revoke all on function public.meet_sync_ride_driver_review_queue() from public;
revoke all on function public.meet_owner_verification_queue_v1(text, integer) from public;
revoke all on function public.meet_owner_decide_verification_v1(uuid, text, text) from public;

grant execute on function public.meet_submit_service_verification_v1(
    text, text, text, text, text, text, text, text
) to authenticated;
grant execute on function public.meet_owner_verification_queue_v1(text, integer) to authenticated;
grant execute on function public.meet_owner_decide_verification_v1(uuid, text, text) to authenticated;

comment on function public.meet_is_platform_owner() is
    'Fail-closed authority check. Only the confirmed jordelmir@gmail.com account is bootstrapped as platform owner.';
comment on table public.service_verification_audit_events is
    'Append-only audit trail for registration review decisions.';

-- Supersede temporary pilot dispatch: local evidence attestation may populate
-- the review queue but cannot grant production dispatch authority.
create or replace function public.ride_vehicle_dispatch_eligible(
    p_vehicle_id uuid,
    p_driver_id uuid
)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
          from public.ride_driver_vehicles v
         where v.id = p_vehicle_id
           and v.driver_id = p_driver_id
           and v.is_active
           and v.verification_status = 'VERIFIED'
           and (
               v.verification_method = 'LEGACY_REVIEW' or
               (
                   v.verification_method in ('IDENTITY_PROVIDER', 'MANUAL_REVIEW') and
                   v.document_review_status = 'APPROVED'
               )
           )
    );
$$;

revoke all on function public.ride_vehicle_dispatch_eligible(uuid, uuid) from public;
grant execute on function public.ride_vehicle_dispatch_eligible(uuid, uuid) to authenticated;
