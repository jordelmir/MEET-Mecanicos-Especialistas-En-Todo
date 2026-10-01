-- A provider may edit their own profile details, never the server's trust decision.
insert into auth.users(id) values ('00000000-0000-0000-0000-000000000098');
insert into public.user_profiles(id,auth_user_id) values
 ('00000000-0000-0000-0000-000000000098','00000000-0000-0000-0000-000000000098');

select set_config('request.jwt.claim.role','authenticated',false);
insert into public.provider_profiles(id,user_profile_id,provider_type)
values ('00000000-0000-0000-0000-000000000098','00000000-0000-0000-0000-000000000098','service_provider');
update public.provider_profiles set business_name='Allowed profile edit'
where id='00000000-0000-0000-0000-000000000098';

do $$ begin
  begin
    update public.provider_profiles set is_verified=true,status='active'
    where id='00000000-0000-0000-0000-000000000098';
    raise exception 'ASSERT_CLIENT_TRUST_UPDATE_WAS_ALLOWED';
  exception when insufficient_privilege then
    if sqlerrm <> 'PROVIDER_TRUST_REQUIRES_SERVER_REVIEW' then raise; end if;
  end;
  begin
    insert into public.provider_profiles(id,user_profile_id,provider_type,is_verified,status)
    values (gen_random_uuid(),'00000000-0000-0000-0000-000000000098','mechanic',true,'active');
    raise exception 'ASSERT_CLIENT_TRUST_INSERT_WAS_ALLOWED';
  exception when insufficient_privilege then
    if sqlerrm <> 'PROVIDER_TRUST_REQUIRES_SERVER_REVIEW' then raise; end if;
  end;
end $$;

select set_config('request.jwt.claim.role','service_role',false);
update public.provider_profiles set is_verified=true,status='active'
where id='00000000-0000-0000-0000-000000000098';
do $$ begin
 if not exists(select 1 from public.provider_profiles
   where id='00000000-0000-0000-0000-000000000098' and is_verified and status='active')
 then raise exception 'ASSERT_SERVER_REVIEW_BLOCKED'; end if;
end $$;
