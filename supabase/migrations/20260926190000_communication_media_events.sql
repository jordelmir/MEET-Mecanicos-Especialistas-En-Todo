-- Typed encrypted media; old text RPC remains compatible.
begin;
create or replace function public.communication_publish_event_v2(
    p_event_id uuid,p_conversation_id uuid,p_device_id uuid,p_envelope text,p_reply_to uuid,p_created_at timestamptz,p_event_type text
) returns setof public.communication_events
language plpgsql security definer set search_path=public,pg_temp as $$
declare v_existing public.communication_events; v_json jsonb;
begin
    if not public.communication_can_send(p_conversation_id,p_device_id) then raise exception 'COMMUNICATION_UNAVAILABLE' using errcode='42501'; end if;
    if p_event_type not in ('TEXT','AUDIO','IMAGE') then raise exception 'INVALID_EVENT_TYPE'; end if;
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
        if v_existing.event_type<>p_event_type or v_existing.sender_id<>auth.uid() or v_existing.sender_device_id<>p_device_id
            or v_existing.conversation_id<>p_conversation_id or v_existing.encrypted_envelope<>p_envelope
            or v_existing.reply_to_event_id is distinct from p_reply_to or v_existing.client_created_at is distinct from p_created_at then raise exception 'IDEMPOTENCY_VIOLATION'; end if;
        return next v_existing;return;
    end if;
    return query insert into communication_events(event_id,conversation_id,sender_id,sender_device_id,event_type,
        encrypted_envelope,reply_to_event_id,idempotency_key,client_created_at)
    values(p_event_id,p_conversation_id,auth.uid(),p_device_id,p_event_type,p_envelope,p_reply_to,p_event_id,p_created_at) returning *;
end; $$;
revoke insert on public.communication_events from authenticated;
revoke all on function public.communication_publish_event_v2(uuid,uuid,uuid,text,uuid,timestamptz,text) from public,anon;
grant execute on function public.communication_publish_event_v2(uuid,uuid,uuid,text,uuid,timestamptz,text) to authenticated;


commit;
