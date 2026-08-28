-- MEET Vanguard Convergence V5
-- Universal principal, server-managed platform authority, legal triage,
-- exposure/case clocks, and immutable metrics foundations.

-- Retire the historical email bootstrap. Existing grants are migrated once;
-- every future grant is an explicit AAL2-protected server decision.
drop trigger if exists meet_bootstrap_platform_owner_trigger on auth.users;
drop function if exists public.meet_bootstrap_platform_owner();

create table if not exists public.platform_authority_grants (
    user_id uuid not null references auth.users(id) on delete restrict,
    role text not null check (role in (
        'PLATFORM_OWNER','SUPER_ADMIN','TRUST_REVIEWER','DRIVER_REVIEWER',
        'PROVIDER_REVIEWER','LEGAL_REVIEWER','SUPPORT_ADMIN','FINANCE_ADMIN',
        'OBSERVABILITY_ADMIN'
    )),
    active boolean not null default true,
    granted_by uuid references auth.users(id) on delete restrict,
    granted_at timestamptz not null default now(),
    revoked_by uuid references auth.users(id) on delete restrict,
    revoked_at timestamptz,
    reason text not null check (char_length(reason) between 3 and 500),
    primary key (user_id, role),
    check ((active and revoked_at is null) or (not active and revoked_at is not null))
);

insert into public.platform_authority_grants(user_id, role, active, granted_by, granted_at, reason)
select user_id, role, active, user_id, granted_at, 'Migrated controlled platform authority'
from public.platform_authorities
where active
on conflict (user_id, role) do nothing;

alter table public.platform_authority_grants enable row level security;
revoke all on table public.platform_authority_grants from anon, authenticated;

create or replace function public.meet_has_platform_authority(p_role text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1 from public.platform_authority_grants g
        where g.user_id = (select auth.uid()) and g.role = p_role and g.active
    );
$$;

create or replace function public.meet_is_platform_owner()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$ select public.meet_has_platform_authority('PLATFORM_OWNER'); $$;

create or replace function public.meet_session_has_aal2()
returns boolean
language sql
stable
set search_path = ''
as $$ select coalesce((select auth.jwt())->>'aal', '') = 'aal2'; $$;

create table if not exists public.platform_authority_audit_events (
    event_id bigint generated always as identity primary key,
    target_user_id uuid not null references auth.users(id) on delete restrict,
    role text not null,
    active boolean not null,
    actor_id uuid not null references auth.users(id) on delete restrict,
    reason text not null,
    occurred_at timestamptz not null default now()
);
alter table public.platform_authority_audit_events enable row level security;
revoke all on table public.platform_authority_audit_events from anon, authenticated;

create or replace function public.meet_owner_set_platform_authority_v1(
    p_target_user_id uuid,
    p_role text,
    p_active boolean,
    p_reason text
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare v_actor uuid := (select auth.uid());
begin
    if v_actor is null or not public.meet_is_platform_owner() then
        raise exception using errcode='42501', message='PLATFORM_OWNER_REQUIRED';
    end if;
    if not public.meet_session_has_aal2() then
        raise exception using errcode='42501', message='AAL2_REQUIRED';
    end if;
    if p_role not in ('PLATFORM_OWNER','SUPER_ADMIN','TRUST_REVIEWER','DRIVER_REVIEWER',
        'PROVIDER_REVIEWER','LEGAL_REVIEWER','SUPPORT_ADMIN','FINANCE_ADMIN','OBSERVABILITY_ADMIN')
       or char_length(trim(coalesce(p_reason,''))) not between 3 and 500 then
        raise exception using errcode='22023', message='INVALID_AUTHORITY_MUTATION';
    end if;
    if p_target_user_id = v_actor and p_role = 'PLATFORM_OWNER' and not p_active then
        raise exception using errcode='22023', message='OWNER_SELF_REVOCATION_FORBIDDEN';
    end if;

    insert into public.platform_authority_grants(
        user_id, role, active, granted_by, granted_at, revoked_by, revoked_at, reason
    ) values (
        p_target_user_id, p_role, p_active,
        case when p_active then v_actor end, now(),
        case when not p_active then v_actor end, case when not p_active then now() end,
        trim(p_reason)
    ) on conflict (user_id, role) do update set
        active=excluded.active,
        granted_by=case when excluded.active then v_actor else public.platform_authority_grants.granted_by end,
        granted_at=case when excluded.active then now() else public.platform_authority_grants.granted_at end,
        revoked_by=case when excluded.active then null else v_actor end,
        revoked_at=case when excluded.active then null else now() end,
        reason=excluded.reason;

    insert into public.platform_authority_audit_events(
        target_user_id, role, active, actor_id, reason
    ) values (p_target_user_id, p_role, p_active, v_actor, trim(p_reason));
end;
$$;

-- Keep the original decision implementation private and expose only an AAL2 wrapper.
revoke all on function public.meet_owner_decide_verification_v1(uuid,text,text) from authenticated;
create or replace function public.meet_owner_decide_verification_v2(
    p_application_id uuid, p_decision text, p_reason text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
begin
    if not public.meet_session_has_aal2() then
        raise exception using errcode='42501', message='AAL2_REQUIRED';
    end if;
    return public.meet_owner_decide_verification_v1(p_application_id,p_decision,p_reason);
end;
$$;

revoke all on function public.meet_has_platform_authority(text) from public;
revoke all on function public.meet_is_platform_owner() from public;
revoke all on function public.meet_session_has_aal2() from public;
revoke all on function public.meet_owner_set_platform_authority_v1(uuid,text,boolean,text) from public;
revoke all on function public.meet_owner_decide_verification_v2(uuid,text,text) from public;
grant execute on function public.meet_has_platform_authority(text) to authenticated;
grant execute on function public.meet_is_platform_owner() to authenticated;
grant execute on function public.meet_session_has_aal2() to authenticated;
grant execute on function public.meet_owner_set_platform_authority_v1(uuid,text,boolean,text) to authenticated;
grant execute on function public.meet_owner_decide_verification_v2(uuid,text,text) to authenticated;

-- One authenticated Principal shared by every vertical.
create table if not exists public.principals (
    principal_id uuid primary key references auth.users(id) on delete restrict,
    status text not null default 'ACTIVE' check (status in ('ACTIVE','SUSPENDED','DELETED','REVIEW_REQUIRED')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
create table if not exists public.principal_profiles (
    principal_id uuid primary key references public.principals(principal_id) on delete cascade,
    display_name text check (display_name is null or char_length(display_name) between 2 and 120),
    locale text,
    updated_at timestamptz not null default now()
);
create table if not exists public.principal_capabilities (
    principal_id uuid not null references public.principals(principal_id) on delete cascade,
    capability text not null,
    activation_state text not null default 'NOT_REQUESTED' check (activation_state in (
        'NOT_REQUESTED','DRAFT','SUBMITTED','UNDER_REVIEW','APPROVED','REJECTED','SUSPENDED','EXPIRED'
    )),
    verified_at timestamptz,
    expires_at timestamptz,
    updated_at timestamptz not null default now(),
    primary key (principal_id, capability),
    check (activation_state <> 'APPROVED' or verified_at is not null)
);
create table if not exists public.principal_roles (
    principal_id uuid not null references public.principals(principal_id) on delete cascade,
    role text not null,
    selected_at timestamptz not null default now(),
    primary key(principal_id,role)
);
create table if not exists public.principal_devices (
    device_id uuid primary key default gen_random_uuid(),
    principal_id uuid not null references public.principals(principal_id) on delete cascade,
    device_key_hash text not null check (device_key_hash ~ '^[a-f0-9]{64}$'),
    provisioned_at timestamptz not null default now(),
    last_seen_at timestamptz not null default now(),
    revoked_at timestamptz,
    unique (principal_id, device_key_hash)
);
create table if not exists public.principal_consents (
    consent_id uuid primary key default gen_random_uuid(),
    principal_id uuid not null references public.principals(principal_id) on delete cascade,
    consent_type text not null,
    policy_version text not null,
    granted boolean not null,
    recorded_at timestamptz not null default now()
);

create or replace function public.meet_bootstrap_principal()
returns trigger language plpgsql security definer set search_path='' as $$
begin
    insert into public.principals(principal_id) values(new.id) on conflict do nothing;
    insert into public.principal_profiles(principal_id) values(new.id) on conflict do nothing;
    return new;
end; $$;
drop trigger if exists meet_bootstrap_principal_trigger on auth.users;
create trigger meet_bootstrap_principal_trigger after insert on auth.users
for each row execute function public.meet_bootstrap_principal();
insert into public.principals(principal_id) select id from auth.users on conflict do nothing;
insert into public.principal_profiles(principal_id) select id from auth.users on conflict do nothing;

alter table public.principals enable row level security;
alter table public.principal_profiles enable row level security;
alter table public.principal_capabilities enable row level security;
alter table public.principal_roles enable row level security;
alter table public.principal_devices enable row level security;
alter table public.principal_consents enable row level security;
create policy principal_self_read on public.principals for select to authenticated using(principal_id=(select auth.uid()));
create policy principal_profile_self on public.principal_profiles for all to authenticated using(principal_id=(select auth.uid())) with check(principal_id=(select auth.uid()));
create policy principal_capability_self_read on public.principal_capabilities for select to authenticated using(principal_id=(select auth.uid()));
create policy principal_role_self on public.principal_roles for all to authenticated using(principal_id=(select auth.uid())) with check(principal_id=(select auth.uid()));
create policy principal_device_self on public.principal_devices for all to authenticated using(principal_id=(select auth.uid())) with check(principal_id=(select auth.uid()));
create policy principal_consent_self on public.principal_consents for all to authenticated using(principal_id=(select auth.uid())) with check(principal_id=(select auth.uid()));
grant select on public.principals, public.principal_capabilities to authenticated;
grant select,insert,delete on public.principal_roles to authenticated;
grant select,insert,update on public.principal_profiles, public.principal_devices, public.principal_consents to authenticated;

alter table public.service_verification_applications
    drop constraint if exists service_verification_applications_service_type_check;
alter table public.service_verification_applications
    add constraint service_verification_applications_service_type_check check(service_type in (
        'PASSENGER','RIDE_DRIVER','TOW_TRUCK','MECHANIC','PARTS_STORE','SERVICE_PROVIDER',
        'WORKSHOP','LAWYER','NOTARY','PROPERTY_BROKER','PROPERTY_SELLER',
        'FUEL_STATION_STAFF','FLEET_OPERATOR'
    ));

create or replace function public.meet_submit_capability_application_v1(
    p_capability text,
    p_profile_reference text,
    p_display_name text,
    p_evidence_manifest_sha256 text
)
returns uuid language plpgsql security definer set search_path='' as $$
declare v_actor uuid := (select auth.uid()); v_application_id uuid;
begin
    if v_actor is null then raise exception using errcode='42501', message='UNAUTHENTICATED'; end if;
    if p_capability not in ('RIDE_DRIVER','TOW_TRUCK','MECHANIC','PARTS_STORE','SERVICE_PROVIDER',
        'WORKSHOP','LAWYER','NOTARY','PROPERTY_BROKER','PROPERTY_SELLER','FUEL_STATION_STAFF','FLEET_OPERATOR')
       or char_length(trim(coalesce(p_profile_reference,''))) not between 1 and 160
       or char_length(trim(coalesce(p_display_name,''))) not between 2 and 120
       or p_evidence_manifest_sha256 !~ '^[a-f0-9]{64}$' then
        raise exception using errcode='22023', message='INVALID_CAPABILITY_APPLICATION';
    end if;
    insert into public.service_verification_applications(
        applicant_user_id,service_type,profile_reference,display_name,evidence_manifest_sha256,status
    ) values(v_actor,p_capability,trim(p_profile_reference),trim(p_display_name),p_evidence_manifest_sha256,'PENDING')
    on conflict(applicant_user_id,service_type,profile_reference) do update set
        evidence_manifest_sha256=excluded.evidence_manifest_sha256,
        display_name=excluded.display_name,status='PENDING',decision_reason=null,
        reviewed_at=null,reviewed_by=null,updated_at=now()
    returning id into v_application_id;
    insert into public.principal_capabilities(principal_id,capability,activation_state,updated_at)
    values(v_actor,p_capability,'SUBMITTED',now())
    on conflict(principal_id,capability) do update set activation_state='SUBMITTED',verified_at=null,updated_at=now();
    insert into public.service_verification_audit_events(
        application_id,actor_id,event_type,from_status,to_status,reason
    ) values(v_application_id,v_actor,'SUBMITTED',null,'PENDING','Capability evidence submitted');
    return v_application_id;
end; $$;

create or replace function public.meet_owner_decide_verification_v2(
    p_application_id uuid, p_decision text, p_reason text
)
returns jsonb language plpgsql security definer set search_path='' as $$
declare v_result jsonb; v_application public.service_verification_applications%rowtype;
begin
    if not public.meet_session_has_aal2() then
        raise exception using errcode='42501', message='AAL2_REQUIRED';
    end if;
    v_result := public.meet_owner_decide_verification_v1(p_application_id,p_decision,p_reason);
    select * into strict v_application from public.service_verification_applications where id=p_application_id;
    insert into public.principal_capabilities(
        principal_id,capability,activation_state,verified_at,updated_at
    ) values(
        v_application.applicant_user_id,v_application.service_type,
        case p_decision when 'APPROVED' then 'APPROVED' when 'REJECTED' then 'REJECTED' else 'SUSPENDED' end,
        case when p_decision='APPROVED' then now() end,now()
    ) on conflict(principal_id,capability) do update set
        activation_state=excluded.activation_state,verified_at=excluded.verified_at,updated_at=now();
    return v_result;
end; $$;

revoke all on function public.meet_submit_capability_application_v1(text,text,text,text) from public;
grant execute on function public.meet_submit_capability_application_v1(text,text,text,text) to authenticated;

-- Derived AI triage reuses the existing authoritative Market OS taxonomy.
create table if not exists public.legal_triage_results (
    triage_id uuid primary key default gen_random_uuid(),
    principal_id uuid not null references public.principals(principal_id) on delete restrict,
    primary_category_code text not null,
    alternative_category_codes text[] not null default '{}',
    confidence numeric(5,4) not null check(confidence between 0 and 1),
    urgency text not null check(urgency in ('NORMAL','HUMAN_REVIEW','TIME_CRITICAL')),
    jurisdiction_hint text not null,
    follow_up_questions jsonb not null default '[]',
    risk_flags text[] not null default '{}',
    rationale_code text not null,
    taxonomy_version_id uuid not null references public.market_taxonomy_versions(taxonomy_version_id),
    model_provider text not null,
    model_name text not null,
    model_version text not null,
    prompt_version text not null,
    state text not null default 'AI_SUGGESTED' check(state in ('AI_SUGGESTED','USER_CONFIRMED','LAWYER_RECLASSIFIED')),
    consent_id uuid not null references public.principal_consents(consent_id) on delete restrict,
    narrative_digest text not null check(narrative_digest ~ '^[a-f0-9]{64}$'),
    created_at timestamptz not null default now()
);

create or replace function public.meet_validate_legal_triage_taxonomy()
returns trigger language plpgsql set search_path='' as $$
begin
    if not exists(
        select 1 from public.market_taxonomy_versions v
        where v.taxonomy_version_id=new.taxonomy_version_id and v.vertical='LEGAL'
          and v.jurisdiction='CR' and v.published_at is not null
    ) or not exists(
        select 1 from public.market_service_categories c
        where c.taxonomy_version_id=new.taxonomy_version_id
          and c.code=new.primary_category_code and c.active
    ) or exists(
        select 1 from unnest(new.alternative_category_codes) as alt(code)
        where not exists(
            select 1 from public.market_service_categories c
            where c.taxonomy_version_id=new.taxonomy_version_id and c.code=alt.code and c.active
        )
    ) then
        raise exception using errcode='23514', message='UNKNOWN_LEGAL_TAXONOMY_CODE';
    end if;
    return new;
end; $$;
create trigger meet_validate_legal_triage_taxonomy_trigger
before insert or update on public.legal_triage_results
for each row execute function public.meet_validate_legal_triage_taxonomy();

alter table public.legal_triage_results enable row level security;
create policy legal_triage_owner_read on public.legal_triage_results for select to authenticated using(principal_id=(select auth.uid()));
grant select on public.legal_triage_results to authenticated;

create table if not exists public.legal_matter_exposures (
    exposure_id uuid primary key default gen_random_uuid(),
    matter_id uuid not null references public.legal_matters(matter_id) on delete cascade,
    professional_principal_id uuid not null references auth.users(id) on delete restrict,
    disclosure_level text not null default 'ANONYMIZED' check(disclosure_level in ('ANONYMIZED','CONFLICT_CLEAR','ENGAGED')),
    exposed_at timestamptz not null default now(),
    expires_at timestamptz not null,
    unique(matter_id,professional_principal_id)
);

alter table public.legal_offers
    add column if not exists earliest_start timestamptz,
    add column if not exists first_action_estimate_min_hours integer check(first_action_estimate_min_hours is null or first_action_estimate_min_hours >= 0),
    add column if not exists first_action_estimate_max_hours integer check(first_action_estimate_max_hours is null or first_action_estimate_max_hours >= first_action_estimate_min_hours),
    add column if not exists estimated_duration_min_days integer check(estimated_duration_min_days is null or estimated_duration_min_days >= 0),
    add column if not exists estimated_duration_max_days integer check(estimated_duration_max_days is null or estimated_duration_max_days >= estimated_duration_min_days),
    add column if not exists milestone_plan jsonb not null default '[]',
    add column if not exists category_experience_snapshot jsonb not null default '{}',
    add column if not exists workload_snapshot jsonb not null default '{}';

alter table public.market_reviews
    add column if not exists visible_after timestamptz not null default (now() + interval '7 days'),
    add column if not exists version bigint not null default 0,
    add column if not exists updated_at timestamptz not null default now();

create table if not exists public.market_review_events (
    event_id bigint generated always as identity primary key,
    review_id uuid not null references public.market_reviews(review_id) on delete restrict,
    actor_id uuid not null references auth.users(id) on delete restrict,
    event_type text not null check(event_type in ('SUBMITTED','PUBLISHED','UNDER_REVIEW','REDACTED','VOIDED')),
    previous_state text,
    new_state text not null,
    reason_code text,
    occurred_at timestamptz not null default now()
);
create table if not exists public.legal_offer_events (
    event_id bigint generated always as identity primary key,
    offer_id uuid not null references public.legal_offers(offer_id) on delete restrict,
    actor_id uuid not null references auth.users(id) on delete restrict,
    event_type text not null,
    occurred_at timestamptz not null default now()
);
create table if not exists public.legal_engagement_events (
    event_id bigint generated always as identity primary key,
    engagement_id uuid not null references public.legal_engagements(engagement_id) on delete restrict,
    actor_id uuid not null references auth.users(id) on delete restrict,
    event_type text not null,
    occurred_at timestamptz not null default now()
);
create table if not exists public.legal_case_clock_segments (
    segment_id uuid primary key default gen_random_uuid(),
    engagement_id uuid not null references public.legal_engagements(engagement_id) on delete cascade,
    clock_type text not null check(clock_type in ('PROFESSIONAL_ACTION','COURT_WAIT','CLIENT_WAIT','COUNTERPARTY_WAIT','AUTHORITY_WAIT','THIRD_PARTY_WAIT','PAUSED','UNKNOWN')),
    started_at timestamptz not null,
    ended_at timestamptz,
    source_event_id bigint,
    check(ended_at is null or ended_at >= started_at)
);

create table if not exists public.principal_service_metrics (
    principal_id uuid not null references public.principals(principal_id) on delete cascade,
    vertical text not null,
    category text not null,
    started_count bigint not null default 0,
    active_count bigint not null default 0,
    completed_count bigint not null default 0,
    provider_cancelled_count bigint not null default 0,
    customer_cancelled_count bigint not null default 0,
    system_cancelled_count bigint not null default 0,
    disputed_count bigint not null default 0,
    rating_count bigint not null default 0,
    rating_mean numeric(5,4),
    rating_bayesian numeric(5,4),
    response_p50_ms bigint,
    response_p90_ms bigint,
    first_action_p50_ms bigint,
    first_action_p90_ms bigint,
    completion_duration_p50_ms bigint,
    completion_duration_p90_ms bigint,
    on_time_milestone_rate numeric(5,4),
    updated_at timestamptz not null default now(),
    primary key(principal_id,vertical,category)
);

alter table public.legal_matter_exposures enable row level security;
alter table public.legal_offer_events enable row level security;
alter table public.legal_engagement_events enable row level security;
alter table public.legal_case_clock_segments enable row level security;
alter table public.principal_service_metrics enable row level security;
alter table public.market_review_events enable row level security;
revoke all on table public.legal_matter_exposures, public.legal_offer_events,
  public.legal_engagement_events, public.legal_case_clock_segments,
  public.principal_service_metrics, public.market_review_events from anon, authenticated;
grant select on public.principal_service_metrics to authenticated;
create policy principal_service_metrics_read on public.principal_service_metrics
for select to authenticated using(rating_count >= 1 or principal_id=(select auth.uid()));

comment on table public.platform_authority_grants is 'Server-authoritative roles; Android visibility never grants authority.';
comment on table public.legal_triage_results is 'AI-derived suggestion evidence; never legal advice or authority.';
comment on table public.principal_service_metrics is 'Server-derived projection. Clients have SELECT only and cannot mutate metrics.';
