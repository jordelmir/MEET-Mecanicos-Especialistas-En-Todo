-- Android online transport and explicit, one-use in-person pairing.
begin;
create table public.communication_pairing_tokens (
    token_hash text primary key,
    issuer_id uuid not null references auth.users(id) on delete cascade,
    expires_at timestamptz not null default now() + interval '5 minutes',
    consumed_by uuid references auth.users(id),
    consumed_at timestamptz,
    conversation_id uuid references public.communication_conversations(id),
    created_at timestamptz not null default now()
);
alter table public.communication_pairing_tokens enable row level security;
revoke all on public.communication_pairing_tokens from public, anon, authenticated;

alter table public.communication_devices add column if not exists transport_protocol text;
revoke insert,update,delete on public.communication_devices from authenticated;

create or replace function public.communication_register_transport_device(p_device_id uuid, p_public_key text)
returns void language plpgsql security definer set search_path = public, pg_temp as $$
begin
    if auth.uid() is null then raise exception 'AUTHENTICATION_REQUIRED' using errcode='42501'; end if;
    if p_public_key is null or length(p_public_key) not between 400 and 2048 or p_public_key !~ '^[A-Za-z0-9+/]+={0,2}$' then raise exception 'INVALID_PUBLIC_KEY'; end if;
    insert into communication_devices(device_id, principal_id, display_name, identity_key, signed_prekey)
    values(p_device_id, auth.uid(), 'Android', p_public_key, p_public_key)
    on conflict(device_id) do nothing;
    if not exists(select 1 from communication_devices where device_id=p_device_id
        and principal_id=auth.uid() and identity_key=p_public_key and revoked_at is null
        and verification_state <> 'REVOKED') then
        raise exception 'DEVICE_KEY_MISMATCH_OR_REVOKED' using errcode='42501';
    end if;
    update communication_devices set transport_protocol='RSA_OAEP_AES_GCM_V1',last_seen_at=now() where device_id=p_device_id and principal_id=auth.uid();
end; $$;

create or replace function public.communication_conversation_device_keys(p_conversation_id uuid)
returns table(device_id uuid, principal_id uuid, identity_key text)
language plpgsql security definer set search_path = public, pg_temp as $$
begin
    if auth.uid() is null or not is_communication_participant(p_conversation_id) then
        raise exception 'CONVERSATION_UNAVAILABLE' using errcode='42501';
    end if;
    if not exists(select 1 from communication_conversations where id=p_conversation_id and request_state='ACCEPTED') then
        raise exception 'CONVERSATION_PENDING' using errcode='42501';
    end if;
    if exists(select 1 from communication_participants a join communication_participants b on b.conversation_id=a.conversation_id
        join communication_blocks k on k.blocker_id=a.principal_id and k.blocked_id=b.principal_id
        where a.conversation_id=p_conversation_id) then
        raise exception 'COMMUNICATION_BLOCKED' using errcode='42501';
    end if;
    return query select d.device_id,d.principal_id,d.identity_key from communication_devices d
    join communication_participants p on p.principal_id=d.principal_id
    where p.conversation_id=p_conversation_id and p.membership_state='ACTIVE'
      and d.revoked_at is null and d.verification_state <> 'REVOKED' and d.transport_protocol='RSA_OAEP_AES_GCM_V1';
end; $$;

create or replace function public.communication_issue_pairing_token()
returns text language plpgsql security definer set search_path = public, pg_temp as $$
declare v_token text := encode(extensions.gen_random_bytes(32),'hex');
begin
    if auth.uid() is null then raise exception 'AUTHENTICATION_REQUIRED' using errcode='42501'; end if;
    if not exists(select 1 from communication_identity_profiles where principal_id=auth.uid()) then
        raise exception 'IDENTITY_REQUIRED';
    end if;
    if (select count(*) from communication_pairing_tokens where issuer_id=auth.uid() and created_at>now()-interval '1 minute')>=5 then
        raise exception 'PAIRING_RATE_LIMITED';
    end if;
    insert into communication_pairing_tokens(token_hash,issuer_id)
    values(encode(extensions.digest(v_token,'sha256'),'hex'),auth.uid());
    return v_token;
end; $$;

create or replace function public.communication_consume_pairing_token(p_token text)
returns uuid language plpgsql security definer set search_path = public, pg_temp as $$
declare v_issuer uuid; v_conversation uuid; v_token_row record;
begin
    if auth.uid() is null then raise exception 'AUTHENTICATION_REQUIRED' using errcode='42501'; end if;
    if p_token !~ '^[a-f0-9]{64}$' then raise exception 'INVALID_PAIRING_TOKEN'; end if;
    select * into v_token_row from communication_pairing_tokens
    where token_hash=encode(extensions.digest(p_token,'sha256'),'hex') for update;
    if not found then raise exception 'PAIRING_UNAVAILABLE'; end if;
    if v_token_row.consumed_at is not null then
        if v_token_row.consumed_by=auth.uid() then return v_token_row.conversation_id; end if;
        raise exception 'PAIRING_UNAVAILABLE';
    end if;
    v_issuer:=v_token_row.issuer_id;
    if v_token_row.expires_at<=now() or v_issuer=auth.uid() then raise exception 'PAIRING_UNAVAILABLE'; end if;
    v_conversation := communication_create_direct_request(v_issuer);
    if exists(select 1 from communication_conversations where id=v_conversation and request_state='BLOCKED') then
        raise exception 'COMMUNICATION_BLOCKED';
    end if;
    update communication_conversations set request_state='ACCEPTED',updated_at=now() where id=v_conversation;
    update communication_message_requests set state='ACCEPTED',responded_at=now() where conversation_id=v_conversation;
    update communication_pairing_tokens set consumed_by=auth.uid(),consumed_at=now(),conversation_id=v_conversation
    where token_hash=encode(extensions.digest(p_token,'sha256'),'hex');
    return v_conversation;
end; $$;

-- Also protect sequence order for trusted/admin imports that bypass the client RPC.
create or replace function public.communication_assign_commit_ordered_sequence()
returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
begin
    perform pg_advisory_xact_lock(hashtextextended('communication:'||new.conversation_id::text,0));
    new.server_sequence := nextval(pg_get_serial_sequence('public.communication_events','server_sequence'));
    return new;
end; $$;
revoke all on function public.communication_assign_commit_ordered_sequence() from public,anon,authenticated;
create trigger communication_commit_ordered_sequence before insert on public.communication_events
for each row execute function public.communication_assign_commit_ordered_sequence();

-- A per-conversation transaction lock orders allocation AND commit of server_sequence.
-- All event writes enter through this RPC; callers cannot bypass the lock with direct INSERT.
create or replace function public.communication_publish_event(
    p_event_id uuid,p_conversation_id uuid,p_device_id uuid,p_envelope text,p_reply_to uuid,p_created_at timestamptz
) returns setof public.communication_events
language plpgsql security definer set search_path=public,pg_temp as $$
declare v_existing public.communication_events; v_json jsonb;
begin
    if not public.communication_can_send(p_conversation_id,p_device_id) then raise exception 'COMMUNICATION_UNAVAILABLE' using errcode='42501'; end if;
    if p_event_id is null or p_created_at is null or length(p_envelope) not between 32 and 2097152 then raise exception 'INVALID_MESSAGE'; end if;
    v_json:=p_envelope::jsonb;
    if coalesce(v_json->>'version','') <> '1' or coalesce(jsonb_typeof(v_json->'recipients'),'') <> 'object'
       or v_json->>'ciphertext' is null or v_json->>'nonce' is null then raise exception 'INVALID_ENVELOPE'; end if;
    if not (v_json->'recipients' ? p_device_id::text)
       or exists(select 1 from jsonb_object_keys(v_json->'recipients') k where not exists(
           select 1 from communication_devices d join communication_participants p on p.principal_id=d.principal_id
           where d.device_id::text=k and p.conversation_id=p_conversation_id and p.membership_state='ACTIVE'
               and d.revoked_at is null and d.verification_state<>'REVOKED' and d.transport_protocol='RSA_OAEP_AES_GCM_V1'))
       or exists(select 1 from communication_participants p where p.conversation_id=p_conversation_id and p.membership_state='ACTIVE'
           and not exists(select 1 from communication_devices d where d.principal_id=p.principal_id
               and d.revoked_at is null and d.verification_state<>'REVOKED' and d.transport_protocol='RSA_OAEP_AES_GCM_V1'
               and v_json->'recipients' ? d.device_id::text)) then raise exception 'RECIPIENT_KEYS_REQUIRED'; end if;
    if p_reply_to is not null and not exists(select 1 from communication_events where event_id=p_reply_to and conversation_id=p_conversation_id) then
        raise exception 'INVALID_REPLY';
    end if;
    perform pg_advisory_xact_lock(hashtextextended('communication:'||p_conversation_id::text,0));
    select * into v_existing from communication_events where event_id=p_event_id;
    if found then
        if v_existing.sender_id<>auth.uid() or v_existing.sender_device_id<>p_device_id
            or v_existing.conversation_id<>p_conversation_id or v_existing.encrypted_envelope<>p_envelope
            or v_existing.reply_to_event_id is distinct from p_reply_to or v_existing.client_created_at is distinct from p_created_at then raise exception 'IDEMPOTENCY_VIOLATION'; end if;
        return next v_existing;return;
    end if;
    return query insert into communication_events(event_id,conversation_id,sender_id,sender_device_id,event_type,
        encrypted_envelope,reply_to_event_id,idempotency_key,client_created_at)
    values(p_event_id,p_conversation_id,auth.uid(),p_device_id,'TEXT',p_envelope,p_reply_to,p_event_id,p_created_at) returning *;
end; $$;
revoke insert on public.communication_events from authenticated;
revoke all on function public.communication_publish_event(uuid,uuid,uuid,text,uuid,timestamptz) from public,anon;
grant execute on function public.communication_publish_event(uuid,uuid,uuid,text,uuid,timestamptz) to authenticated;

-- Block checks must run with authority: caller-visible block rows omit blocks issued by a peer.
create or replace function public.communication_can_send(p_conversation_id uuid,p_device_id uuid)
returns boolean language sql stable security definer set search_path = public, pg_temp as $$
    select auth.uid() is not null and is_communication_participant(p_conversation_id)
    and exists(select 1 from communication_conversations where id=p_conversation_id and request_state='ACCEPTED')
    and exists(select 1 from communication_devices where device_id=p_device_id and principal_id=auth.uid()
        and revoked_at is null and verification_state<>'REVOKED')
    and not exists(select 1 from communication_participants me join communication_participants other on other.conversation_id=me.conversation_id
        join communication_blocks b on (b.blocker_id=me.principal_id and b.blocked_id=other.principal_id)
            or (b.blocker_id=other.principal_id and b.blocked_id=me.principal_id)
        where me.conversation_id=p_conversation_id and me.principal_id=auth.uid() and other.membership_state='ACTIVE');
$$;
revoke all on function public.communication_can_send(uuid,uuid) from public,anon;
grant execute on function public.communication_can_send(uuid,uuid) to authenticated;
drop policy if exists communication_events_participant_insert on public.communication_events;
create policy communication_events_participant_insert on public.communication_events for insert to authenticated
with check(sender_id=auth.uid() and public.communication_can_send(conversation_id,sender_device_id));

create or replace function public.communication_contact_profiles()
returns table(principal_id uuid, elysium_id text, display_name text)
language sql stable security definer set search_path = public, pg_temp as $$
    select distinct i.principal_id,i.elysium_id,i.display_name
    from communication_identity_profiles i
    join communication_participants other on other.principal_id=i.principal_id and other.membership_state='ACTIVE'
    join communication_participants me on me.conversation_id=other.conversation_id and me.principal_id=auth.uid() and me.membership_state='ACTIVE'
    join communication_conversations c on c.id=me.conversation_id and c.kind='DIRECT' and c.request_state='ACCEPTED'
    where i.principal_id<>auth.uid() and not exists(select 1 from communication_blocks b
        where (b.blocker_id=auth.uid() and b.blocked_id=i.principal_id) or (b.blocker_id=i.principal_id and b.blocked_id=auth.uid()));
$$;
revoke all on function public.communication_contact_profiles() from public,anon;
grant execute on function public.communication_contact_profiles() to authenticated;

revoke all on function public.communication_register_transport_device(uuid,text),
    public.communication_conversation_device_keys(uuid), public.communication_issue_pairing_token(),
    public.communication_consume_pairing_token(text) from public,anon;
grant execute on function public.communication_register_transport_device(uuid,text),
    public.communication_conversation_device_keys(uuid), public.communication_issue_pairing_token(),
    public.communication_consume_pairing_token(text) to authenticated;
commit;
