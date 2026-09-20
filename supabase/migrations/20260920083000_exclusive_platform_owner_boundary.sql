-- Exclusive owner boundary for Trust Center and executive Command Center.
-- The public email is used only inside a SECURITY DEFINER authority check;
-- Android never treats a local email comparison as authorization.

delete from public.platform_authority_grants g
where g.role = 'PLATFORM_OWNER'
  and not exists (
      select 1
      from auth.users u
      where u.id = g.user_id
        and lower(coalesce(u.email, '')) = 'jordelmir@gmail.com'
        and u.email_confirmed_at is not null
  );

insert into public.platform_authority_grants(
    user_id, role, active, granted_by, granted_at, revoked_by, revoked_at, reason
)
select u.id, 'PLATFORM_OWNER', true, u.id, now(), null, null,
       'Exclusive confirmed platform owner'
from auth.users u
where lower(coalesce(u.email, '')) = 'jordelmir@gmail.com'
  and u.email_confirmed_at is not null
on conflict (user_id, role) do update
set active = true,
    revoked_by = null,
    revoked_at = null,
    reason = excluded.reason;

create or replace function public.meet_is_platform_owner()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.platform_authority_grants g
        join auth.users u on u.id = g.user_id
        where g.user_id = (select auth.uid())
          and g.role = 'PLATFORM_OWNER'
          and g.active
          and lower(coalesce(u.email, '')) = 'jordelmir@gmail.com'
          and u.email_confirmed_at is not null
    );
$$;

create or replace function public.meet_protect_exclusive_platform_owner()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    if new.role = 'PLATFORM_OWNER' and not exists (
        select 1 from auth.users u
        where u.id = new.user_id
          and lower(coalesce(u.email, '')) = 'jordelmir@gmail.com'
          and u.email_confirmed_at is not null
    ) then
        raise exception using errcode = '42501', message = 'EXCLUSIVE_PLATFORM_OWNER_REQUIRED';
    end if;
    return new;
end;
$$;

drop trigger if exists meet_protect_exclusive_platform_owner_trigger
on public.platform_authority_grants;
create trigger meet_protect_exclusive_platform_owner_trigger
before insert or update of user_id, role, active
on public.platform_authority_grants
for each row execute function public.meet_protect_exclusive_platform_owner();

revoke all on function public.meet_is_platform_owner() from public;
grant execute on function public.meet_is_platform_owner() to authenticated;
revoke all on function public.meet_protect_exclusive_platform_owner() from public;

comment on function public.meet_is_platform_owner() is
    'Fail-closed exclusive authority for the confirmed jordelmir@gmail.com account.';
