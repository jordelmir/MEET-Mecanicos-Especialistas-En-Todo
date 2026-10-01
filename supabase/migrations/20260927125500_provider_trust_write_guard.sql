-- A provider owns their profile data, but never their verification decision.
-- This guard runs even when a client explicitly submits trust fields through
-- an older API. Administrative verification must use service_role authority.
begin;

create or replace function public.provider_profile_trust_write_guard_v1()
returns trigger language plpgsql security invoker set search_path = public, pg_temp as $$
begin
  if auth.role() in ('authenticated', 'anon') then
    if tg_op = 'INSERT' then
      if new.is_verified is distinct from false or new.status is distinct from 'pending' then
        raise exception 'PROVIDER_TRUST_REQUIRES_SERVER_REVIEW' using errcode = '42501';
      end if;
    elsif new.is_verified is distinct from old.is_verified
       or new.status is distinct from old.status then
      raise exception 'PROVIDER_TRUST_REQUIRES_SERVER_REVIEW' using errcode = '42501';
    end if;
  end if;
  return new;
end $$;

revoke all on function public.provider_profile_trust_write_guard_v1() from public, anon, authenticated;

drop trigger if exists provider_profile_trust_write_guard_v1 on public.provider_profiles;
create trigger provider_profile_trust_write_guard_v1
before insert or update on public.provider_profiles
for each row execute function public.provider_profile_trust_write_guard_v1();

commit;
