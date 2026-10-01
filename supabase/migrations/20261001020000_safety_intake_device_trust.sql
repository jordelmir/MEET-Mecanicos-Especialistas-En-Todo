begin;
-- Private server configuration is deliberately not writable by application clients.
create table safety_private.intake_policy (
 singleton boolean primary key default true check(singleton),
 max_submissions integer not null check(max_submissions between 1 and 1000),
 window_seconds integer not null check(window_seconds between 30 and 86400),
 require_verified_device boolean not null default false,
 policy_version text not null
);
insert into safety_private.intake_policy values(true,6,60,false,'SAFETY-INTAKE-V1');
create table safety_private.intake_rate_windows (
 actor_id uuid not null references auth.users(id), window_start timestamptz not null,
 submission_count integer not null check(submission_count > 0), primary key(actor_id,window_start)
);
create table safety_private.device_trust_challenges (
 id uuid primary key default gen_random_uuid(), actor_id uuid not null references auth.users(id),
 nonce text not null unique, request_hash text not null unique,
 package_name text not null, expires_at timestamptz not null, created_at timestamptz not null default now()
);
create index safety_device_challenge_actor_time on safety_private.device_trust_challenges(actor_id,created_at);
create table safety_private.device_trust_verifications (
 challenge_id uuid primary key references safety_private.device_trust_challenges(id),
 actor_id uuid not null references auth.users(id), provider text not null check(provider='PLAY_INTEGRITY'),
 result text not null check(result in('VERIFIED','REJECTED')),
 response_digest text not null check(response_digest ~ '^[a-f0-9]{64}$'),
 verified_at timestamptz not null default now(), valid_until timestamptz not null,
 reason_code text not null check(length(reason_code) between 3 and 80)
);
create or replace function public.safety_issue_device_challenge_v1()
returns jsonb language plpgsql security definer set search_path='' as $$
declare v_actor uuid:=auth.uid(); v_id uuid:=gen_random_uuid(); v_nonce text; v_hash text; v_count int;
begin
 if v_actor is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
 -- Serialization makes the challenge issuance budget concurrency-safe too.
 perform pg_advisory_xact_lock(hashtextextended('safety-device:'||v_actor::text,0));
 select count(*) into v_count from safety_private.device_trust_challenges
 where actor_id=v_actor and created_at>now()-interval '1 minute';
 if v_count>=6 then raise exception 'SAFETY_CHALLENGE_RATE_LIMIT'; end if;
 v_nonce:=encode(extensions.gen_random_bytes(32),'hex');
 -- Android sends exactly this opaque requestHash to Google's standard request.
 v_hash:=translate(rtrim(encode(extensions.digest(convert_to(v_id::text||':'||v_actor::text||':'||v_nonce,'UTF8'),'sha256'),'base64'),'='),'+/','-_');
 insert into safety_private.device_trust_challenges(id,actor_id,nonce,request_hash,package_name,expires_at)
 values(v_id,v_actor,v_nonce,v_hash,'com.elysium369.meet',now()+interval '5 minutes');
 return jsonb_build_object('challenge_id',v_id,'request_hash',v_hash,'expires_at',now()+interval '5 minutes','provider','PLAY_INTEGRITY');
end $$;
-- This boundary is callable only by the trusted decoder worker. The worker must
-- independently verify Google signature/server decoded verdict; JWT clients cannot assert trust.
create or replace function public.safety_record_device_verdict_v1(
 p_challenge_id uuid,p_actor_id uuid,p_request_hash text,p_package_name text,
 p_verified boolean,p_response_digest text,p_reason_code text
) returns jsonb language plpgsql security definer set search_path='' as $$
declare v_challenge safety_private.device_trust_challenges%rowtype; v_row safety_private.device_trust_verifications%rowtype;
begin
 select * into v_challenge from safety_private.device_trust_challenges where id=p_challenge_id for update;
 if not found or v_challenge.actor_id is distinct from p_actor_id or v_challenge.request_hash is distinct from p_request_hash
 or v_challenge.package_name is distinct from p_package_name then raise exception 'DEVICE_CHALLENGE_BINDING_MISMATCH'; end if;
 select * into v_row from safety_private.device_trust_verifications where challenge_id=p_challenge_id;
 if found then
  if v_row.response_digest is distinct from p_response_digest then raise exception 'DEVICE_CHALLENGE_ALREADY_CONSUMED'; end if;
  return jsonb_build_object('result',v_row.result,'valid_until',v_row.valid_until);
 end if;
 if v_challenge.expires_at<=now() then raise exception 'DEVICE_CHALLENGE_EXPIRED'; end if;
 insert into safety_private.device_trust_verifications(challenge_id,actor_id,provider,result,response_digest,valid_until,reason_code)
 values(p_challenge_id,p_actor_id,'PLAY_INTEGRITY',case when p_verified then 'VERIFIED' else 'REJECTED' end,p_response_digest,
 case when p_verified then now()+interval '24 hours' else now() end,p_reason_code) returning * into v_row;
 return jsonb_build_object('result',v_row.result,'valid_until',v_row.valid_until);
end $$;
create or replace function public.safety_intake_budget_guard_v1()
returns trigger language plpgsql security definer set search_path='' as $$
declare v_policy safety_private.intake_policy%rowtype; v_window timestamptz; v_count int;
begin
 select * into strict v_policy from safety_private.intake_policy where singleton;
 if v_policy.require_verified_device and not exists(select 1 from safety_private.device_trust_verifications
 where actor_id=new.reporter_user_id and result='VERIFIED' and valid_until>now()) then raise exception 'SAFETY_DEVICE_TRUST_REQUIRED'; end if;
 v_window:=to_timestamp(floor(extract(epoch from now())/v_policy.window_seconds)*v_policy.window_seconds);
 insert into safety_private.intake_rate_windows(actor_id,window_start,submission_count) values(new.reporter_user_id,v_window,1)
 on conflict(actor_id,window_start) do update set submission_count=safety_private.intake_rate_windows.submission_count+1
 returning submission_count into v_count;
 if v_count>v_policy.max_submissions then raise exception using errcode='P0001',message='SAFETY_RATE_LIMIT_EXCEEDED'; end if;
 return new;
end $$;
-- Existing V3 idempotency checks precede report insertion. Replays consume zero quota.
-- A denied transaction rolls both its dedup reservation and increment back atomically.
create trigger safety_intake_budget_guard before insert on public.safety_reports
for each row execute function public.safety_intake_budget_guard_v1();
create trigger safety_device_challenges_immutable before update or delete on safety_private.device_trust_challenges
for each row execute function public.safety_reject_publication_history_mutation();
create trigger safety_device_verdicts_immutable before update or delete on safety_private.device_trust_verifications
for each row execute function public.safety_reject_publication_history_mutation();
alter table safety_private.intake_policy enable row level security;
alter table safety_private.intake_rate_windows enable row level security;
alter table safety_private.device_trust_challenges enable row level security;
alter table safety_private.device_trust_verifications enable row level security;
revoke all on safety_private.intake_policy,safety_private.intake_rate_windows,safety_private.device_trust_challenges,safety_private.device_trust_verifications from public,anon,authenticated,service_role;
revoke all on function public.safety_intake_budget_guard_v1() from public,anon,authenticated,service_role;
revoke all on function public.safety_issue_device_challenge_v1() from public,anon,service_role;
grant execute on function public.safety_issue_device_challenge_v1() to authenticated;
revoke all on function public.safety_record_device_verdict_v1(uuid,uuid,text,text,boolean,text,text) from public,anon,authenticated;
grant execute on function public.safety_record_device_verdict_v1(uuid,uuid,text,text,boolean,text,text) to service_role;
commit;
