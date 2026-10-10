-- Migration: 20261009160000_safety_reviewer_master_accreditation.sql
-- Reviewer Accreditation Protocol (UPEACE, Bar Association, COLPER)
-- and Platform Owner Master Access for Validation.

begin;

-- 1. Ensure platform owner (jordelmir@gmail.com) has active TRUST_REVIEWER and LEGAL_REVIEWER grants
insert into public.platform_authority_grants(
    user_id, role, active, granted_by, granted_at, revoked_by, revoked_at, reason
)
select u.id, 'TRUST_REVIEWER', true, u.id, now(), null, null,
       'Platform Owner Master Validation Access (Pre-launch bootstrap)'
from auth.users u
where lower(coalesce(u.email, '')) = 'jordelmir@gmail.com'
  and u.email_confirmed_at is not null
on conflict (user_id, role) do update
set active = true,
    revoked_by = null,
    revoked_at = null,
    reason = excluded.reason;

insert into public.platform_authority_grants(
    user_id, role, active, granted_by, granted_at, revoked_by, revoked_at, reason
)
select u.id, 'LEGAL_REVIEWER', true, u.id, now(), null, null,
       'Platform Owner Master Validation Access (Pre-launch bootstrap)'
from auth.users u
where lower(coalesce(u.email, '')) = 'jordelmir@gmail.com'
  and u.email_confirmed_at is not null
on conflict (user_id, role) do update
set active = true,
    revoked_by = null,
    revoked_at = null,
    reason = excluded.reason;

-- 2. Update safety_is_reviewer and safety_is_publisher to honor PLATFORM_OWNER as master validator
create or replace function public.safety_is_reviewer()
returns boolean language sql stable security definer set search_path = '' as $$
    select (select auth.uid()) is not null
        and (
            (public.meet_has_platform_authority('TRUST_REVIEWER') and public.meet_session_has_aal2())
            or public.meet_is_platform_owner()
        );
$$;

create or replace function public.safety_is_publisher()
returns boolean language sql stable security definer set search_path = '' as $$
    select (select auth.uid()) is not null
        and (
            (public.meet_has_platform_authority('LEGAL_REVIEWER') and public.meet_session_has_aal2())
            or public.meet_is_platform_owner()
        );
$$;

revoke all on function public.safety_is_reviewer() from public, anon, service_role;
revoke all on function public.safety_is_publisher() from public, anon, service_role;
grant execute on function public.safety_is_reviewer() to authenticated;
grant execute on function public.safety_is_publisher() to authenticated;

-- 3. Accreditation Registry Table for Reviewers A and B
create table if not exists public.safety_reviewer_accreditations (
    accreditation_id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete restrict,
    full_name text not null,
    national_id_or_passport text not null,
    institution_affiliation text not null, -- UPEACE, Colegio de Abogados, COLPER, OIJ, etc.
    professional_credential_id text not null,
    requested_role text not null check (requested_role in ('TRUST_REVIEWER', 'LEGAL_REVIEWER')),
    aal2_method text not null check (aal2_method in ('FIDO2_HARDWARE', 'TOTP_AUTHENTICATOR', 'MASTER_DELEGATED')),
    deontological_commitment_sha256 text not null,
    status text not null default 'ACTIVE' check (status in ('PENDING', 'ACTIVE', 'REVOKED')),
    approved_by uuid references auth.users(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

alter table public.safety_reviewer_accreditations enable row level security;
revoke all on public.safety_reviewer_accreditations from anon;

-- Policy: Owner can view all, users can view their own accreditation
create policy safety_reviewer_accreditations_owner_read
    on public.safety_reviewer_accreditations
    for select to authenticated
    using (user_id = (select auth.uid()) or public.meet_is_platform_owner());

-- 4. RPC to register reviewer accreditation
create or replace function public.safety_submit_reviewer_accreditation_v1(
    p_full_name text,
    p_id_number text,
    p_institution text,
    p_credential_id text,
    p_role text,
    p_aal2_method text,
    p_commitment_sha256 text
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
    v_actor uuid := auth.uid();
    v_is_owner boolean;
    v_accreditation_id uuid;
begin
    if v_actor is null then
        raise exception using errcode = '42501', message = 'AUTHENTICATION_REQUIRED';
    end if;

    v_is_owner := public.meet_is_platform_owner();

    insert into public.safety_reviewer_accreditations (
        user_id,
        full_name,
        national_id_or_passport,
        institution_affiliation,
        professional_credential_id,
        requested_role,
        aal2_method,
        deontological_commitment_sha256,
        status,
        approved_by
    ) values (
        v_actor,
        trim(p_full_name),
        trim(p_id_number),
        trim(p_institution),
        trim(p_credential_id),
        p_role,
        p_aal2_method,
        p_commitment_sha256,
        case when v_is_owner then 'ACTIVE' else 'PENDING' end,
        case when v_is_owner then v_actor else null end
    ) returning accreditation_id into v_accreditation_id;

    -- If owner, also automatically activate platform authority grant
    if v_is_owner then
        insert into public.platform_authority_grants (
            user_id, role, active, granted_by, granted_at, reason
        ) values (
            v_actor, p_role, true, v_actor, now(), 'Owner Self-Accreditation for System Bootstrap'
        ) on conflict (user_id, role) do update set active = true;
    end if;

    return jsonb_build_object(
        'ok', true,
        'accreditation_id', v_accreditation_id,
        'status', case when v_is_owner then 'ACTIVE' else 'PENDING' end,
        'role', p_role
    );
end;
$$;

revoke all on function public.safety_submit_reviewer_accreditation_v1(text,text,text,text,text,text,text) from public, anon;
grant execute on function public.safety_submit_reviewer_accreditation_v1(text,text,text,text,text,text,text) to authenticated;

commit;
