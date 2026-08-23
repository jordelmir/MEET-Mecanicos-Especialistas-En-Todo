-- Elysium Communications Core: participant-bound metadata and append-only
-- encrypted envelopes. This is a control/data contract; it does not claim that
-- Matrix, LiveKit, TURN or device key exchange have been deployed.

create table if not exists public.communication_devices (
    device_id uuid primary key,
    principal_id uuid not null references auth.users(id) on delete cascade,
    display_name text not null check (char_length(display_name) between 1 and 120),
    identity_key text not null check (char_length(identity_key) between 32 and 16384),
    signed_prekey text not null check (char_length(signed_prekey) between 32 and 16384),
    key_version integer not null default 1 check (key_version > 0),
    verification_state text not null default 'UNVERIFIED'
        check (verification_state in ('UNVERIFIED', 'VERIFIED', 'REVOKED')),
    created_at timestamptz not null default now(),
    last_seen_at timestamptz not null default now(),
    revoked_at timestamptz,
    unique(principal_id, device_id)
);

create table if not exists public.communication_conversations (
    id uuid primary key default gen_random_uuid(),
    kind text not null check (kind in ('DIRECT', 'SERVICE', 'GROUP', 'PERSONAL')),
    title text not null check (char_length(title) between 1 and 160),
    service_vertical text,
    service_reference_id uuid,
    created_by uuid not null references auth.users(id) on delete restrict,
    request_state text not null default 'ACCEPTED'
        check (request_state in ('PENDING', 'ACCEPTED', 'REJECTED', 'BLOCKED')),
    proof_state text not null default 'SERVER_AUTHORITATIVE'
        check (proof_state in ('MODEL_EXISTS', 'CLIENT_IMPLEMENTED', 'SERVER_AUTHORITATIVE', 'PHYSICALLY_VERIFIED')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    check (
        (kind = 'SERVICE' and service_vertical is not null and service_reference_id is not null) or
        (kind <> 'SERVICE' and service_vertical is null and service_reference_id is null)
    )
);

create unique index if not exists communication_service_reference_unique
    on public.communication_conversations(service_vertical, service_reference_id)
    where kind = 'SERVICE';

create table if not exists public.communication_participants (
    conversation_id uuid not null references public.communication_conversations(id) on delete cascade,
    principal_id uuid not null references auth.users(id) on delete restrict,
    role text not null check (role in ('OWNER', 'CUSTOMER', 'SERVICE_PROVIDER', 'MEMBER', 'ADMIN')),
    membership_state text not null default 'ACTIVE'
        check (membership_state in ('INVITED', 'ACTIVE', 'LEFT', 'REVOKED', 'BLOCKED')),
    joined_at timestamptz not null default now(),
    revoked_at timestamptz,
    primary key(conversation_id, principal_id)
);

create table if not exists public.communication_events (
    event_id uuid primary key,
    conversation_id uuid not null references public.communication_conversations(id) on delete cascade,
    sender_id uuid not null references auth.users(id) on delete restrict,
    sender_device_id uuid not null,
    event_type text not null
        check (event_type in ('TEXT', 'IMAGE', 'AUDIO', 'FILE', 'REACTION', 'EDIT', 'REDACTION', 'MEMBERSHIP')),
    encrypted_envelope text not null
        check (char_length(encrypted_envelope) between 32 and 2097152),
    reply_to_event_id uuid references public.communication_events(event_id) on delete set null,
    idempotency_key uuid not null,
    client_created_at timestamptz not null,
    server_sequence bigint generated always as identity,
    received_at timestamptz not null default now(),
    unique(conversation_id, sender_device_id, idempotency_key),
    unique(conversation_id, server_sequence),
    foreign key(sender_id, sender_device_id)
        references public.communication_devices(principal_id, device_id) on delete restrict
);

create index if not exists communication_events_timeline_idx
    on public.communication_events(conversation_id, server_sequence);

create table if not exists public.communication_receipts (
    conversation_id uuid not null references public.communication_conversations(id) on delete cascade,
    event_id uuid not null references public.communication_events(event_id) on delete cascade,
    reader_id uuid not null references auth.users(id) on delete cascade,
    receipt_type text not null check (receipt_type in ('DELIVERED', 'READ')),
    created_at timestamptz not null default now(),
    primary key(event_id, reader_id, receipt_type)
);

create table if not exists public.communication_blocks (
    blocker_id uuid not null references auth.users(id) on delete cascade,
    blocked_id uuid not null references auth.users(id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key(blocker_id, blocked_id),
    check (blocker_id <> blocked_id)
);

create table if not exists public.communication_call_sessions (
    id uuid primary key default gen_random_uuid(),
    conversation_id uuid not null references public.communication_conversations(id) on delete cascade,
    initiated_by uuid not null references auth.users(id) on delete restrict,
    media_type text not null check (media_type in ('AUDIO', 'VIDEO')),
    state text not null default 'RINGING'
        check (state in ('RINGING', 'ACTIVE', 'ENDED', 'DECLINED', 'MISSED', 'FAILED')),
    livekit_room_name text not null unique,
    created_at timestamptz not null default now(),
    answered_at timestamptz,
    ended_at timestamptz
);

create index if not exists communication_calls_conversation_created_idx
    on public.communication_call_sessions(conversation_id, created_at desc);

create or replace function public.is_communication_participant(p_conversation_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public, pg_temp
as $$
    select exists (
        select 1 from public.communication_participants p
        where p.conversation_id = p_conversation_id
          and p.principal_id = auth.uid()
          and p.membership_state = 'ACTIVE'
    );
$$;

revoke all on function public.is_communication_participant(uuid) from public;
grant execute on function public.is_communication_participant(uuid) to authenticated;

-- Storage policies must fail closed for malformed object names instead of
-- throwing on an unsafe text-to-uuid cast.
create or replace function public.communication_storage_conversation_id(p_object_name text)
returns uuid
language plpgsql
immutable
security invoker
set search_path = public, pg_temp
as $$
begin
    return ((storage.foldername(p_object_name))[1])::uuid;
exception when invalid_text_representation or array_subscript_error then
    return null;
end;
$$;

revoke all on function public.communication_storage_conversation_id(text) from public;
grant execute on function public.communication_storage_conversation_id(text) to authenticated;

alter table public.communication_devices enable row level security;
alter table public.communication_conversations enable row level security;
alter table public.communication_participants enable row level security;
alter table public.communication_events enable row level security;
alter table public.communication_receipts enable row level security;
alter table public.communication_blocks enable row level security;
alter table public.communication_call_sessions enable row level security;

create policy communication_devices_owner_select on public.communication_devices
for select to authenticated using (principal_id = auth.uid());
create policy communication_devices_owner_insert on public.communication_devices
for insert to authenticated with check (principal_id = auth.uid() and verification_state = 'UNVERIFIED');
-- Verification and revocation are server-authoritative. There is deliberately
-- no direct owner UPDATE policy: otherwise a client could self-verify or clear
-- a revocation timestamp.

create policy communication_conversations_participant_select on public.communication_conversations
for select to authenticated using (public.is_communication_participant(id));

create policy communication_participants_participant_select on public.communication_participants
for select to authenticated using (public.is_communication_participant(conversation_id));

create policy communication_events_participant_select on public.communication_events
for select to authenticated using (public.is_communication_participant(conversation_id));
create policy communication_events_participant_insert on public.communication_events
for insert to authenticated with check (
    sender_id = auth.uid()
    and public.is_communication_participant(conversation_id)
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

create policy communication_receipts_participant_select on public.communication_receipts
for select to authenticated using (public.is_communication_participant(conversation_id));
create policy communication_receipts_reader_insert on public.communication_receipts
for insert to authenticated with check (
    reader_id = auth.uid() and public.is_communication_participant(conversation_id)
);

create policy communication_blocks_owner_select on public.communication_blocks
for select to authenticated using (blocker_id = auth.uid());
create policy communication_blocks_owner_insert on public.communication_blocks
for insert to authenticated with check (blocker_id = auth.uid());
create policy communication_blocks_owner_delete on public.communication_blocks
for delete to authenticated using (blocker_id = auth.uid());

create policy communication_calls_participant_select on public.communication_call_sessions
for select to authenticated using (public.is_communication_participant(conversation_id));

-- Service conversations can only be created by participant-bound RPCs. Clients
-- receive no direct INSERT/UPDATE/DELETE policy on conversations or membership.
create or replace function public.ensure_ride_communication(p_ride_request_id uuid)
returns uuid
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_passenger uuid;
    v_driver uuid;
    v_conversation uuid;
begin
    select passenger_id, assigned_driver_id
      into v_passenger, v_driver
      from public.ride_requests
     where id = p_ride_request_id;

    if v_passenger is null or v_driver is null or auth.uid() not in (v_passenger, v_driver) then
        raise exception 'ACTIVE_RIDE_PARTICIPANTS_REQUIRED' using errcode = '42501';
    end if;

    insert into public.communication_conversations(
        kind, title, service_vertical, service_reference_id, created_by
    ) values ('SERVICE', 'Viaje Elysium', 'ride', p_ride_request_id, auth.uid())
    on conflict do nothing;

    select id into v_conversation
      from public.communication_conversations
     where service_vertical = 'ride' and service_reference_id = p_ride_request_id;

    insert into public.communication_participants(conversation_id, principal_id, role)
    values
        (v_conversation, v_passenger, 'CUSTOMER'),
        (v_conversation, v_driver, 'SERVICE_PROVIDER')
    on conflict (conversation_id, principal_id) do nothing;

    return v_conversation;
end;
$$;

revoke all on function public.ensure_ride_communication(uuid) from public;
grant execute on function public.ensure_ride_communication(uuid) to authenticated;

create or replace function public.ensure_universal_service_communication(p_request_id uuid)
returns uuid
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_client uuid;
    v_provider uuid;
    v_title text;
    v_conversation uuid;
begin
    select client_id, assigned_provider_id, title
      into v_client, v_provider, v_title
      from public.universal_service_requests
     where id = p_request_id and state in ('ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'DISPUTED');

    if v_client is null or v_provider is null or auth.uid() not in (v_client, v_provider) then
        raise exception 'ASSIGNED_SERVICE_PARTICIPANTS_REQUIRED' using errcode = '42501';
    end if;

    insert into public.communication_conversations(
        kind, title, service_vertical, service_reference_id, created_by
    ) values ('SERVICE', left(v_title, 160), 'universal', p_request_id, auth.uid())
    on conflict do nothing;

    select id into v_conversation
      from public.communication_conversations
     where service_vertical = 'universal' and service_reference_id = p_request_id;

    insert into public.communication_participants(conversation_id, principal_id, role)
    values
        (v_conversation, v_client, 'CUSTOMER'),
        (v_conversation, v_provider, 'SERVICE_PROVIDER')
    on conflict (conversation_id, principal_id) do nothing;

    return v_conversation;
end;
$$;

revoke all on function public.ensure_universal_service_communication(uuid) from public;
grant execute on function public.ensure_universal_service_communication(uuid) to authenticated;

insert into storage.buckets(id, name, public, file_size_limit, allowed_mime_types)
values (
    'communication-ciphertext',
    'communication-ciphertext',
    false,
    104857600,
    array['application/octet-stream']
)
on conflict (id) do update set
    public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

create policy communication_ciphertext_participant_select on storage.objects
for select to authenticated using (
    bucket_id = 'communication-ciphertext'
    and public.is_communication_participant(public.communication_storage_conversation_id(name))
);

create policy communication_ciphertext_participant_insert on storage.objects
for insert to authenticated with check (
    bucket_id = 'communication-ciphertext'
    and (storage.foldername(name))[2] = auth.uid()::text
    and public.is_communication_participant(public.communication_storage_conversation_id(name))
    and metadata->>'mimetype' = 'application/octet-stream'
);
