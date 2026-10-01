-- Server-authorized calls using the existing Realtime audio engine, not an unconfigured token endpoint.
begin;
alter table public.communication_call_sessions add column if not exists media_key text;
alter table public.communication_call_sessions add column if not exists answered_by uuid references auth.users(id);
alter table public.communication_call_sessions add column if not exists last_activity_at timestamptz not null default now();
create unique index if not exists communication_one_open_call_per_conversation on public.communication_call_sessions(conversation_id) where state in ('RINGING','ACTIVE');
create or replace function public.communication_call_allowed_v1(p_conversation_id uuid)
returns boolean language sql stable security definer set search_path=public,pg_temp as $$
select auth.uid() is not null and is_communication_participant(p_conversation_id)
 and exists(select 1 from communication_conversations where id=p_conversation_id and request_state='ACCEPTED')
 and (select count(*) from communication_participants where conversation_id=p_conversation_id and membership_state='ACTIVE')=2
 and not exists(select 1 from communication_participants a join communication_participants b on b.conversation_id=a.conversation_id
 join communication_blocks k on k.blocker_id=a.principal_id and k.blocked_id=b.principal_id where a.conversation_id=p_conversation_id);
$$;
create or replace function public.communication_start_call_v1(p_conversation_id uuid,p_call_id uuid)
returns setof public.communication_call_sessions language plpgsql security definer set search_path=public,pg_temp as $$
declare v_peer uuid; v_permission text; v_existing public.communication_call_sessions;
begin
 if not communication_call_allowed_v1(p_conversation_id) then raise exception 'CALL_UNAVAILABLE' using errcode='42501'; end if;
 select principal_id into v_peer from communication_participants where conversation_id=p_conversation_id and membership_state='ACTIVE' and principal_id<>auth.uid();
 select call_permission into v_permission from communication_privacy_settings where principal_id=v_peer;
 if coalesce(v_permission,'CONTACTS') not in ('EVERYONE','CONTACTS') or (coalesce(v_permission,'CONTACTS')='CONTACTS' and not communication_are_contacts(auth.uid(),v_peer)) then raise exception 'CALL_PRIVACY_REJECTED' using errcode='42501'; end if;
 perform pg_advisory_xact_lock(hashtextextended('communication-call:'||p_conversation_id::text,0));
 select * into v_existing from communication_call_sessions where id=p_call_id;
 if found then
  if v_existing.initiated_by<>auth.uid() or v_existing.conversation_id<>p_conversation_id then raise exception 'CALL_IDEMPOTENCY_VIOLATION'; end if;
  return next v_existing;return;
 end if;
 update communication_call_sessions set state=case when state='RINGING' then 'MISSED' else 'ENDED' end,ended_at=now(),media_key=null
 where conversation_id=p_conversation_id and state in ('RINGING','ACTIVE') and last_activity_at<now()-interval '2 minutes';
 if exists(select 1 from communication_call_sessions where conversation_id=p_conversation_id and state in ('RINGING','ACTIVE')) then raise exception 'CALL_BUSY'; end if;
 return query insert into communication_call_sessions(id,conversation_id,initiated_by,media_type,state,livekit_room_name,media_key)
 values(p_call_id,p_conversation_id,auth.uid(),'AUDIO','RINGING','elysium_call_'||gen_random_uuid()::text,encode(extensions.gen_random_bytes(32),'base64')) returning *;
end $$;
create or replace function public.communication_answer_call_v1(p_call_id uuid,p_accept boolean)
returns setof public.communication_call_sessions language plpgsql security definer set search_path=public,pg_temp as $$
declare v_call public.communication_call_sessions;
begin
 select * into v_call from communication_call_sessions where id=p_call_id for update;
 if not found or not communication_call_allowed_v1(v_call.conversation_id) or auth.uid()=v_call.initiated_by then raise exception 'CALL_RESPONSE_FORBIDDEN' using errcode='42501'; end if;
 if v_call.state='ACTIVE' and v_call.answered_by=auth.uid() and p_accept then return next v_call;return; end if;
 if v_call.state='DECLINED' and not p_accept then return next v_call;return; end if;
 if v_call.state<>'RINGING' or v_call.created_at<now()-interval '60 seconds' then raise exception 'CALL_NO_LONGER_RINGING'; end if;
 return query update communication_call_sessions set state=case when p_accept then 'ACTIVE' else 'DECLINED' end,answered_by=auth.uid(),answered_at=case when p_accept then now() else null end,ended_at=case when p_accept then null else now() end,media_key=case when p_accept then media_key else null end,last_activity_at=now() where id=p_call_id returning *;
end $$;
create or replace function public.communication_end_call_v1(p_call_id uuid)
returns setof public.communication_call_sessions language plpgsql security definer set search_path=public,pg_temp as $$
declare v_call public.communication_call_sessions;
begin
 select * into v_call from communication_call_sessions where id=p_call_id for update;
 if not found or not is_communication_participant(v_call.conversation_id) then raise exception 'CALL_UNAVAILABLE' using errcode='42501'; end if;
 return query update communication_call_sessions set state=case when state in ('RINGING','ACTIVE') then 'ENDED' else state end,ended_at=coalesce(ended_at,now()),media_key=null where id=p_call_id returning *;
end $$;
create or replace function public.communication_refresh_call_v1(p_call_id uuid)
returns setof public.communication_call_sessions language plpgsql security definer set search_path=public,pg_temp as $$
declare v_call public.communication_call_sessions;
begin
 select * into v_call from communication_call_sessions where id=p_call_id for update;
 if not found or not is_communication_participant(v_call.conversation_id) then raise exception 'CALL_UNAVAILABLE' using errcode='42501'; end if;
 if (v_call.state='RINGING' and v_call.created_at<now()-interval '60 seconds') or (v_call.state='ACTIVE' and (v_call.last_activity_at<now()-interval '2 minutes' or not communication_call_allowed_v1(v_call.conversation_id))) then
  update communication_call_sessions set state=case when state='RINGING' then 'MISSED' else 'ENDED' end,ended_at=now(),media_key=null where id=p_call_id;
 elsif v_call.state='ACTIVE' then update communication_call_sessions set last_activity_at=now() where id=p_call_id;
 end if;
 return query select * from communication_call_sessions where id=p_call_id;
end $$;
revoke all on function public.communication_call_allowed_v1(uuid),public.communication_start_call_v1(uuid,uuid),public.communication_answer_call_v1(uuid,boolean),public.communication_end_call_v1(uuid),public.communication_refresh_call_v1(uuid) from public,anon;
grant execute on function public.communication_call_allowed_v1(uuid),public.communication_start_call_v1(uuid,uuid),public.communication_answer_call_v1(uuid,boolean),public.communication_end_call_v1(uuid),public.communication_refresh_call_v1(uuid) to authenticated;
commit;
