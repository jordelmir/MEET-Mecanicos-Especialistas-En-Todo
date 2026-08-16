-- MEET identity: authenticated onboarding usage roles.
-- A role selection records intent. It never self-approves a service provider.

alter table public.user_profiles
    drop constraint if exists user_profiles_primary_role_check;
alter table public.user_profiles
    add constraint user_profiles_primary_role_check check (primary_role in (
        'driver', 'enthusiast', 'pro_user', 'mechanic', 'workshop_owner',
        'parts_store', 'tow_provider', 'ride_passenger', 'ride_driver',
        'fleet_manager', 'verified_company', 'creator', 'admin',
        'super_admin', 'support_agent', 'trust_safety_reviewer'
    ));

alter table public.user_roles
    drop constraint if exists user_roles_role_name_check;
alter table public.user_roles
    add constraint user_roles_role_name_check check (role_name in (
        'driver', 'enthusiast', 'pro_user', 'mechanic', 'workshop_owner',
        'parts_store', 'tow_provider', 'ride_passenger', 'ride_driver',
        'fleet_manager', 'verified_company', 'creator', 'admin',
        'super_admin', 'support_agent', 'trust_safety_reviewer'
    ));

create table if not exists public.usage_profile_activation_events (
    id bigint generated always as identity primary key,
    user_id uuid not null references auth.users(id) on delete cascade,
    usage_profile text not null check (usage_profile in (
        'owner', 'mechanic', 'workshop', 'fleet',
        'ride_passenger', 'ride_driver'
    )),
    resulting_role text not null,
    verification_required boolean not null,
    idempotency_key text not null check (
        char_length(idempotency_key) between 16 and 180
    ),
    created_at timestamptz not null default now(),
    unique (user_id, idempotency_key)
);

create index if not exists usage_profile_events_user_created_idx
    on public.usage_profile_activation_events(user_id, created_at desc);

alter table public.usage_profile_activation_events enable row level security;
revoke all on table public.usage_profile_activation_events from anon, authenticated;
grant select on table public.usage_profile_activation_events to authenticated;

drop policy if exists usage_profile_events_self_select
    on public.usage_profile_activation_events;
create policy usage_profile_events_self_select
on public.usage_profile_activation_events
for select to authenticated
using (user_id = (select auth.uid()));

create or replace function public.meet_activate_usage_profile_v1(
    p_usage_profile text,
    p_idempotency_key text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := (select auth.uid());
    v_role text;
    v_mobility_role text;
    v_current_mobility_role text;
    v_display_name text;
    v_verification_required boolean;
    v_profile_id uuid;
begin
    if v_user_id is null then
        return jsonb_build_object(
            'ok', false,
            'error', jsonb_build_object(
                'code', 'UNAUTHENTICATED',
                'message', 'Autenticación requerida'
            )
        );
    end if;
    if coalesce(p_usage_profile, '') not in (
        'owner', 'mechanic', 'workshop', 'fleet',
        'ride_passenger', 'ride_driver'
    ) or coalesce(p_idempotency_key, '') !~ '^[A-Za-z0-9._:@-]{16,180}$'
    then
        return jsonb_build_object(
            'ok', false,
            'error', jsonb_build_object(
                'code', 'VALIDATION_ERROR',
                'message', 'Perfil de uso inválido'
            )
        );
    end if;

    v_role := case p_usage_profile
        when 'owner' then 'driver'
        when 'mechanic' then 'mechanic'
        when 'workshop' then 'workshop_owner'
        when 'fleet' then 'fleet_manager'
        when 'ride_passenger' then 'ride_passenger'
        when 'ride_driver' then 'ride_driver'
    end;
    v_mobility_role := case p_usage_profile
        when 'ride_passenger' then 'PASSENGER'
        when 'ride_driver' then 'DRIVER'
        else null
    end;
    v_verification_required := p_usage_profile in (
        'mechanic', 'workshop', 'ride_driver'
    );

    v_display_name := coalesce(
        nullif(split_part(current_setting('request.jwt.claim.email', true), '@', 1), ''),
        'Cuenta MEET'
    );

    insert into public.user_profiles(
        auth_user_id, display_name, primary_role, updated_at
    ) values (
        v_user_id, v_display_name, v_role, now()
    )
    on conflict (auth_user_id) do update
       set primary_role = excluded.primary_role,
           updated_at = now()
    returning id into v_profile_id;

    insert into public.user_roles(
        user_profile_id, role_name, granted_by, is_active, metadata, updated_at
    ) values (
        v_profile_id, v_role, v_user_id, true,
        jsonb_build_object(
            'source', 'authenticated_onboarding',
            'verification_required', v_verification_required
        ),
        now()
    )
    on conflict (user_profile_id, role_name) do update
       set is_active = true,
           metadata = excluded.metadata,
           updated_at = now();

    if v_mobility_role is not null then
        select p.mobility_role
          into v_current_mobility_role
          from public.ride_profiles p
         where p.user_id = v_user_id;

        insert into public.ride_profiles(
            user_id, mobility_role, country_code, preferred_currency,
            display_name, updated_at
        ) values (
            v_user_id, v_mobility_role, 'CR', 'CRC', v_display_name, now()
        )
        on conflict (user_id) do update
           set mobility_role = case
                   when public.ride_profiles.mobility_role = excluded.mobility_role
                       then public.ride_profiles.mobility_role
                   else 'BOTH'
               end,
               display_name = excluded.display_name,
               updated_at = now()
        returning mobility_role into v_current_mobility_role;
    end if;

    insert into public.usage_profile_activation_events(
        user_id, usage_profile, resulting_role,
        verification_required, idempotency_key
    ) values (
        v_user_id, p_usage_profile, v_role,
        v_verification_required, p_idempotency_key
    )
    on conflict (user_id, idempotency_key) do nothing;

    return jsonb_build_object(
        'ok', true,
        'data', jsonb_build_object(
            'role', v_role,
            'mobility_role', v_current_mobility_role,
            'verification_required', v_verification_required
        )
    );
end;
$$;

revoke all on function public.meet_activate_usage_profile_v1(text, text)
    from public, anon, authenticated;
grant execute on function public.meet_activate_usage_profile_v1(text, text)
    to authenticated;
