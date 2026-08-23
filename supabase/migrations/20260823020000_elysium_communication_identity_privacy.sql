-- Elysium identity, exact discovery, privacy, requests, presence and blocking.
-- Telephone aliases are optional and never used for authentication or account
-- recovery. No SMS dependency is introduced by this migration.

create table if not exists public.communication_identity_profiles (
    principal_id uuid primary key references auth.users(id) on delete cascade,
    elysium_id text not null check (
        elysium_id = lower(elysium_id) and
        elysium_id ~ '^[a-z0-9][a-z0-9._-]{2,31}$'
    ),
    display_name text not null check (char_length(display_name) between 1 and 120),
    about text not null default '' check (char_length(about) <= 280),
    identity_state text not null default 'SERVER_AUTHORITATIVE'
        check (identity_state in ('LOCAL_ONLY', 'SERVER_AUTHORITATIVE', 'REVOKED')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index if not exists communication_identity_elysium_id_unique
    on public.communication_identity_profiles(lower(elysium_id));

create table if not exists public.communication_identity_aliases (
    principal_id uuid not null references auth.users(id) on delete cascade,
    medium text not null check (medium in ('EMAIL', 'PHONE')),
    normalized_value text not null check (char_length(normalized_value) between 3 and 320),
    verification_state text not null
        check (verification_state in ('VERIFIED', 'DECLARED', 'REVOKED')),
    discovery_visibility text not null default 'NOBODY'
        check (discovery_visibility in ('EVERYONE', 'CONTACTS', 'NOBODY')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key(principal_id, medium)
);

create index if not exists communication_identity_alias_lookup
    on public.communication_identity_aliases(medium, lower(normalized_value));

create table if not exists public.communication_privacy_settings (
    principal_id uuid primary key references auth.users(id) on delete cascade,
    find_by_elysium_id text not null default 'EVERYONE'
        check (find_by_elysium_id in ('EVERYONE', 'NOBODY')),
    find_by_email text not null default 'NOBODY'
        check (find_by_email in ('EVERYONE', 'CONTACTS', 'NOBODY')),
    find_by_phone text not null default 'NOBODY'
        check (find_by_phone in ('EVERYONE', 'CONTACTS', 'NOBODY')),
    profile_photo_visibility text not null default 'CONTACTS'
        check (profile_photo_visibility in ('EVERYONE', 'CONTACTS', 'CONTACTS_EXCEPT', 'NOBODY')),
    profile_visibility text not null default 'CONTACTS'
        check (profile_visibility in ('EVERYONE', 'CONTACTS', 'CONTACTS_EXCEPT', 'NOBODY')),
    last_active_visibility text not null default 'CONTACTS'
        check (last_active_visibility in ('EVERYONE', 'CONTACTS', 'CONTACTS_EXCEPT', 'NOBODY')),
    online_visibility text not null default 'SAME_AS_LAST_ACTIVE'
        check (online_visibility in ('EVERYONE', 'SAME_AS_LAST_ACTIVE')),
    read_receipts_enabled boolean not null default true,
    typing_indicators_enabled boolean not null default true,
    call_permission text not null default 'CONTACTS'
        check (call_permission in ('EVERYONE', 'CONTACTS', 'CONTACTS_EXCEPT', 'NOBODY')),
    group_invite_permission text not null default 'CONTACTS'
        check (group_invite_permission in ('EVERYONE', 'CONTACTS', 'CONTACTS_EXCEPT', 'NOBODY')),
    mesh_discoverability text not null default 'OFF'
        check (mesh_discoverability in ('OFF', 'CONTACTS', 'COMMUNITIES', 'NEARBY_REQUESTS')),
    relay_participation text not null default 'OFF'
        check (relay_participation in ('OFF', 'CONTACTS_ONLY', 'COMMUNITY')),
    relay_only_while_charging boolean not null default false,
    relay_minimum_battery_percent integer not null default 25
        check (relay_minimum_battery_percent between 10 and 90),
    updated_at timestamptz not null default now()
);

create table if not exists public.communication_direct_links (
    principal_low uuid not null references auth.users(id) on delete cascade,
    principal_high uuid not null references auth.users(id) on delete cascade,
    conversation_id uuid not null unique references public.communication_conversations(id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key(principal_low, principal_high),
    check (principal_low::text < principal_high::text)
);

create table if not exists public.communication_message_requests (
    conversation_id uuid primary key references public.communication_conversations(id) on delete cascade,
    requester_id uuid not null references auth.users(id) on delete cascade,
    recipient_id uuid not null references auth.users(id) on delete cascade,
    state text not null default 'PENDING'
        check (state in ('PENDING', 'ACCEPTED', 'REJECTED', 'BLOCKED', 'EXPIRED')),
    created_at timestamptz not null default now(),
    responded_at timestamptz,
    check (requester_id <> recipient_id)
);

create table if not exists public.communication_presence_leases (
    principal_id uuid primary key references auth.users(id) on delete cascade,
    reachability text not null check (reachability in ('INTERNET', 'NEARBY', 'MESH', 'UNAVAILABLE')),
    device_id uuid,
    lease_expires_at timestamptz not null,
    updated_at timestamptz not null default now()
);

create table if not exists public.communication_discovery_attempts (
    id bigint generated always as identity primary key,
    requester_id uuid not null references auth.users(id) on delete cascade,
    medium text not null check (medium in ('ELYSIUM_ID', 'EMAIL', 'PHONE')),
    attempted_at timestamptz not null default now()
);

create index if not exists communication_discovery_rate_idx
    on public.communication_discovery_attempts(requester_id, attempted_at desc);

alter table public.communication_identity_profiles enable row level security;
alter table public.communication_identity_aliases enable row level security;
alter table public.communication_privacy_settings enable row level security;
alter table public.communication_direct_links enable row level security;
alter table public.communication_message_requests enable row level security;
alter table public.communication_presence_leases enable row level security;
alter table public.communication_discovery_attempts enable row level security;

create policy communication_identity_owner_select on public.communication_identity_profiles
for select to authenticated using (principal_id = auth.uid());
create policy communication_identity_owner_insert on public.communication_identity_profiles
for insert to authenticated with check (principal_id = auth.uid());
create policy communication_identity_owner_update on public.communication_identity_profiles
for update to authenticated using (principal_id = auth.uid()) with check (principal_id = auth.uid());

create policy communication_alias_owner_select on public.communication_identity_aliases
for select to authenticated using (principal_id = auth.uid());

create policy communication_privacy_owner_select on public.communication_privacy_settings
for select to authenticated using (principal_id = auth.uid());

create policy communication_direct_link_participant_select on public.communication_direct_links
for select to authenticated using (auth.uid() in (principal_low, principal_high));

create policy communication_request_participant_select on public.communication_message_requests
for select to authenticated using (auth.uid() in (requester_id, recipient_id));

create policy communication_presence_owner_select on public.communication_presence_leases
for select to authenticated using (principal_id = auth.uid());

create or replace function public.communication_are_contacts(p_left uuid, p_right uuid)
returns boolean
language sql
stable
security definer
set search_path = public, pg_temp
as $$
    select exists (
        select 1
          from public.communication_direct_links l
          join public.communication_conversations c on c.id = l.conversation_id
         where l.principal_low = least(p_left, p_right)
           and l.principal_high = greatest(p_left, p_right)
           and c.request_state = 'ACCEPTED'
    );
$$;

revoke all on function public.communication_are_contacts(uuid, uuid) from public;
grant execute on function public.communication_are_contacts(uuid, uuid) to authenticated;

create or replace function public.communication_ensure_identity(
    p_elysium_id text,
    p_display_name text,
    p_about text default '',
    p_phone text default null,
    p_phone_discovery text default 'NOBODY'
)
returns uuid
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    v_user auth.users%rowtype;
    v_id text := lower(trim(both '@' from trim(coalesce(p_elysium_id, ''))));
    v_phone text := regexp_replace(coalesce(p_phone, ''), '[^0-9+]', '', 'g');
begin
    if auth.uid() is null then
        raise exception 'AUTHENTICATION_REQUIRED' using errcode = '42501';
    end if;
    if v_id !~ '^[a-z0-9][a-z0-9._-]{2,31}$' then
        raise exception 'INVALID_ELYSIUM_ID' using errcode = '22023';
    end if;
    if char_length(trim(coalesce(p_display_name, ''))) not between 1 and 120 then
        raise exception 'INVALID_DISPLAY_NAME' using errcode = '22023';
    end if;
    if p_phone_discovery not in ('EVERYONE', 'CONTACTS', 'NOBODY') then
        raise exception 'INVALID_DISCOVERY_POLICY' using errcode = '22023';
    end if;

    select * into strict v_user from auth.users where id = auth.uid();

    insert into public.communication_identity_profiles(
        principal_id, elysium_id, display_name, about, updated_at
    ) values (
        auth.uid(), v_id, trim(p_display_name), left(coalesce(p_about, ''), 280), now()
    )
    on conflict (principal_id) do update
       set elysium_id = excluded.elysium_id,
           display_name = excluded.display_name,
           about = excluded.about,
           updated_at = now();

    insert into public.communication_privacy_settings(principal_id)
    values (auth.uid()) on conflict (principal_id) do nothing;

    if v_user.email is not null then
        insert into public.communication_identity_aliases(
            principal_id, medium, normalized_value, verification_state,
            discovery_visibility, updated_at
        ) values (
            auth.uid(), 'EMAIL', lower(v_user.email),
            case when v_user.email_confirmed_at is null then 'DECLARED' else 'VERIFIED' end,
            'NOBODY', now()
        )
        on conflict (principal_id, medium) do update
           set normalized_value = excluded.normalized_value,
               verification_state = excluded.verification_state,
               updated_at = now();
    end if;

    if v_phone <> '' then
        if v_phone !~ '^\+?[0-9]{7,15}$' then
            raise exception 'INVALID_PHONE_ALIAS' using errcode = '22023';
        end if;
        insert into public.communication_identity_aliases(
            principal_id, medium, normalized_value, verification_state,
            discovery_visibility, updated_at
        ) values (
            auth.uid(), 'PHONE', v_phone, 'DECLARED', p_phone_discovery, now()
        )
        on conflict (principal_id, medium) do update
           set normalized_value = excluded.normalized_value,
               verification_state = 'DECLARED',
               discovery_visibility = excluded.discovery_visibility,
               updated_at = now();
    end if;

    return auth.uid();
exception when unique_violation then
    raise exception 'ELYSIUM_ID_UNAVAILABLE' using errcode = '23505';
end;
$$;

revoke all on function public.communication_ensure_identity(text, text, text, text, text) from public;
grant execute on function public.communication_ensure_identity(text, text, text, text, text) to authenticated;

create or replace function public.communication_lookup_identity_exact(
    p_medium text,
    p_value text
)
returns table(
    principal_id uuid,
    elysium_id text,
    display_name text,
    matched_medium text,
    alias_proof_state text
)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_medium text := upper(trim(coalesce(p_medium, '')));
    v_value text := lower(trim(both '@' from trim(coalesce(p_value, ''))));
begin
    if auth.uid() is null then
        raise exception 'AUTHENTICATION_REQUIRED' using errcode = '42501';
    end if;
    if v_medium not in ('ELYSIUM_ID', 'EMAIL', 'PHONE') or char_length(v_value) not between 3 and 320 then
        raise exception 'INVALID_DISCOVERY_QUERY' using errcode = '22023';
    end if;
    if (
        select count(*) >= 20
          from public.communication_discovery_attempts a
         where a.requester_id = auth.uid()
           and a.attempted_at >= now() - interval '10 minutes'
    ) then
        raise exception 'DISCOVERY_RATE_LIMITED' using errcode = '57014';
    end if;

    insert into public.communication_discovery_attempts(requester_id, medium)
    values (auth.uid(), v_medium);

    if v_medium = 'ELYSIUM_ID' then
        return query
        select p.principal_id, p.elysium_id, p.display_name, 'ELYSIUM_ID'::text, 'VERIFIED'::text
          from public.communication_identity_profiles p
          join public.communication_privacy_settings s on s.principal_id = p.principal_id
         where lower(p.elysium_id) = v_value
           and s.find_by_elysium_id = 'EVERYONE'
           and p.principal_id <> auth.uid()
           and not exists (
               select 1 from public.communication_blocks b
                where (b.blocker_id = auth.uid() and b.blocked_id = p.principal_id)
                   or (b.blocker_id = p.principal_id and b.blocked_id = auth.uid())
           )
         limit 1;
    else
        return query
        select p.principal_id, p.elysium_id, p.display_name, a.medium, a.verification_state
          from public.communication_identity_aliases a
          join public.communication_identity_profiles p on p.principal_id = a.principal_id
         where a.medium = v_medium
           and lower(a.normalized_value) = v_value
           and a.principal_id <> auth.uid()
           and (
               a.discovery_visibility = 'EVERYONE' or
               (a.discovery_visibility = 'CONTACTS' and public.communication_are_contacts(auth.uid(), a.principal_id))
           )
           and not exists (
               select 1 from public.communication_blocks b
                where (b.blocker_id = auth.uid() and b.blocked_id = a.principal_id)
                   or (b.blocker_id = a.principal_id and b.blocked_id = auth.uid())
           )
         order by (a.verification_state = 'VERIFIED') desc, p.created_at asc
         limit 5;
    end if;
end;
$$;

revoke all on function public.communication_lookup_identity_exact(text, text) from public;
grant execute on function public.communication_lookup_identity_exact(text, text) to authenticated;

create or replace function public.communication_create_direct_request(p_target_id uuid)
returns uuid
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_low uuid := least(auth.uid(), p_target_id);
    v_high uuid := greatest(auth.uid(), p_target_id);
    v_conversation uuid;
begin
    if auth.uid() is null then
        raise exception 'AUTHENTICATION_REQUIRED' using errcode = '42501';
    end if;
    if p_target_id = auth.uid() or not exists (
        select 1 from public.communication_identity_profiles where principal_id = p_target_id
    ) then
        raise exception 'TARGET_NOT_AVAILABLE' using errcode = '22023';
    end if;
    if exists (
        select 1 from public.communication_blocks b
         where (b.blocker_id = auth.uid() and b.blocked_id = p_target_id)
            or (b.blocker_id = p_target_id and b.blocked_id = auth.uid())
    ) then
        raise exception 'COMMUNICATION_BLOCKED' using errcode = '42501';
    end if;

    select conversation_id into v_conversation
      from public.communication_direct_links
     where principal_low = v_low and principal_high = v_high;

    if v_conversation is null then
        insert into public.communication_conversations(
            kind, title, created_by, request_state
        ) values ('DIRECT', 'Conversación Elysium', auth.uid(), 'PENDING')
        returning id into v_conversation;

        insert into public.communication_participants(conversation_id, principal_id, role)
        values
            (v_conversation, auth.uid(), 'OWNER'),
            (v_conversation, p_target_id, 'MEMBER');

        insert into public.communication_direct_links(principal_low, principal_high, conversation_id)
        values (v_low, v_high, v_conversation);

        insert into public.communication_message_requests(
            conversation_id, requester_id, recipient_id
        ) values (v_conversation, auth.uid(), p_target_id);
    end if;

    return v_conversation;
end;
$$;

revoke all on function public.communication_create_direct_request(uuid) from public;
grant execute on function public.communication_create_direct_request(uuid) to authenticated;

create or replace function public.communication_respond_message_request(
    p_conversation_id uuid,
    p_accept boolean
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    update public.communication_message_requests
       set state = case when p_accept then 'ACCEPTED' else 'REJECTED' end,
           responded_at = now()
     where conversation_id = p_conversation_id
       and recipient_id = auth.uid()
       and state = 'PENDING';
    if not found then
        raise exception 'REQUEST_NOT_AVAILABLE' using errcode = '42501';
    end if;
    update public.communication_conversations
       set request_state = case when p_accept then 'ACCEPTED' else 'REJECTED' end,
           updated_at = now()
     where id = p_conversation_id;
end;
$$;

revoke all on function public.communication_respond_message_request(uuid, boolean) from public;
grant execute on function public.communication_respond_message_request(uuid, boolean) to authenticated;

create or replace function public.communication_set_privacy(p_settings jsonb)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    if auth.uid() is null then
        raise exception 'AUTHENTICATION_REQUIRED' using errcode = '42501';
    end if;
    insert into public.communication_privacy_settings(
        principal_id, find_by_elysium_id, find_by_email, find_by_phone,
        profile_photo_visibility, profile_visibility, last_active_visibility,
        online_visibility, read_receipts_enabled, typing_indicators_enabled,
        call_permission, group_invite_permission, mesh_discoverability,
        relay_participation, relay_only_while_charging,
        relay_minimum_battery_percent, updated_at
    ) values (
        auth.uid(),
        coalesce(p_settings->>'findByElysiumId', 'EVERYONE'),
        coalesce(p_settings->>'findByEmail', 'NOBODY'),
        coalesce(p_settings->>'findByPhone', 'NOBODY'),
        coalesce(p_settings->>'profilePhotoVisibility', 'CONTACTS'),
        coalesce(p_settings->>'profileVisibility', 'CONTACTS'),
        coalesce(p_settings->>'lastActiveVisibility', 'CONTACTS'),
        coalesce(p_settings->>'onlineVisibility', 'SAME_AS_LAST_ACTIVE'),
        coalesce((p_settings->>'readReceiptsEnabled')::boolean, true),
        coalesce((p_settings->>'typingIndicatorsEnabled')::boolean, true),
        coalesce(p_settings->>'callPermission', 'CONTACTS'),
        coalesce(p_settings->>'groupInvitePermission', 'CONTACTS'),
        coalesce(p_settings->>'meshDiscoverability', 'OFF'),
        coalesce(p_settings->>'relayParticipation', 'OFF'),
        coalesce((p_settings->>'relayOnlyWhileCharging')::boolean, false),
        coalesce((p_settings->>'relayMinimumBatteryPercent')::integer, 25),
        now()
    ) on conflict (principal_id) do update set
        find_by_elysium_id = excluded.find_by_elysium_id,
        find_by_email = excluded.find_by_email,
        find_by_phone = excluded.find_by_phone,
        profile_photo_visibility = excluded.profile_photo_visibility,
        profile_visibility = excluded.profile_visibility,
        last_active_visibility = excluded.last_active_visibility,
        online_visibility = excluded.online_visibility,
        read_receipts_enabled = excluded.read_receipts_enabled,
        typing_indicators_enabled = excluded.typing_indicators_enabled,
        call_permission = excluded.call_permission,
        group_invite_permission = excluded.group_invite_permission,
        mesh_discoverability = excluded.mesh_discoverability,
        relay_participation = excluded.relay_participation,
        relay_only_while_charging = excluded.relay_only_while_charging,
        relay_minimum_battery_percent = excluded.relay_minimum_battery_percent,
        updated_at = now();

    update public.communication_identity_aliases
       set discovery_visibility = case medium
           when 'EMAIL' then coalesce(p_settings->>'findByEmail', 'NOBODY')
           else coalesce(p_settings->>'findByPhone', 'NOBODY')
       end,
       updated_at = now()
     where principal_id = auth.uid();
end;
$$;

revoke all on function public.communication_set_privacy(jsonb) from public;
grant execute on function public.communication_set_privacy(jsonb) to authenticated;

create or replace function public.communication_block_principal(p_target_id uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    if auth.uid() is null or p_target_id = auth.uid() then
        raise exception 'INVALID_BLOCK_TARGET' using errcode = '22023';
    end if;
    insert into public.communication_blocks(blocker_id, blocked_id)
    values (auth.uid(), p_target_id) on conflict do nothing;

    update public.communication_conversations c
       set request_state = 'BLOCKED', updated_at = now()
      from public.communication_direct_links l
     where l.conversation_id = c.id
       and l.principal_low = least(auth.uid(), p_target_id)
       and l.principal_high = greatest(auth.uid(), p_target_id);

    update public.communication_message_requests r
       set state = 'BLOCKED', responded_at = now()
      from public.communication_direct_links l
     where l.conversation_id = r.conversation_id
       and l.principal_low = least(auth.uid(), p_target_id)
       and l.principal_high = greatest(auth.uid(), p_target_id);
end;
$$;

revoke all on function public.communication_block_principal(uuid) from public;
grant execute on function public.communication_block_principal(uuid) to authenticated;

create or replace function public.communication_presence_heartbeat(
    p_reachability text,
    p_device_id uuid default null
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    if p_reachability not in ('INTERNET', 'NEARBY', 'MESH', 'UNAVAILABLE') then
        raise exception 'INVALID_REACHABILITY' using errcode = '22023';
    end if;
    insert into public.communication_presence_leases(
        principal_id, reachability, device_id, lease_expires_at, updated_at
    ) values (
        auth.uid(), p_reachability, p_device_id, now() + interval '90 seconds', now()
    ) on conflict (principal_id) do update set
        reachability = excluded.reachability,
        device_id = excluded.device_id,
        lease_expires_at = excluded.lease_expires_at,
        updated_at = now();
end;
$$;

revoke all on function public.communication_presence_heartbeat(text, uuid) from public;
grant execute on function public.communication_presence_heartbeat(text, uuid) to authenticated;

-- Direct messages remain fail-closed until the recipient accepts the request.
drop policy if exists communication_events_participant_insert on public.communication_events;
create policy communication_events_participant_insert on public.communication_events
for insert to authenticated with check (
    sender_id = auth.uid()
    and public.is_communication_participant(conversation_id)
    and exists (
        select 1 from public.communication_conversations c
         where c.id = conversation_id and c.request_state = 'ACCEPTED'
    )
    and exists (
        select 1 from public.communication_devices d
        where d.principal_id = auth.uid()
          and d.device_id = sender_device_id
          and d.revoked_at is null
          and d.verification_state <> 'REVOKED'
    )
    and not exists (
        select 1
        from public.communication_participants me
        join public.communication_participants other
          on other.conversation_id = me.conversation_id
        join public.communication_blocks b
          on (b.blocker_id = me.principal_id and b.blocked_id = other.principal_id)
          or (b.blocker_id = other.principal_id and b.blocked_id = me.principal_id)
        where me.conversation_id = communication_events.conversation_id
          and me.principal_id = auth.uid()
          and other.membership_state = 'ACTIVE'
    )
);
