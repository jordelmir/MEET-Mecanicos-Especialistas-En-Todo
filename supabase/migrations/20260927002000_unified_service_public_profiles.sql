begin;
create index if not exists universal_service_provider_state_idx on public.universal_service_requests(assigned_provider_id,state,updated_at);
create index if not exists universal_service_client_updated_idx on public.universal_service_requests(client_id,updated_at);
create index if not exists universal_service_ratings_provider_idx on public.universal_service_ratings(provider_id);
create or replace function public.universal_service_provider_summary_v1(p_provider_id uuid)
returns jsonb language sql stable security definer set search_path='' as $$
 select jsonb_build_object(
 'provider_id',p_provider_id,
 'name',(select p.business_name from public.provider_profiles p join public.user_profiles u on u.id=p.user_profile_id
 where u.auth_user_id=p_provider_id and p.is_active and p.is_verified and p.status='active'
 and p.provider_type in ('service_provider','SERVICE_PROVIDER') order by p.id limit 1),
 'completed', (select count(*) from public.universal_service_requests where assigned_provider_id=p_provider_id and state='COMPLETED'),
 'reviews',(select count(*) from public.universal_service_ratings where provider_id=p_provider_id),
 'rating',(select avg(stars) from public.universal_service_ratings where provider_id=p_provider_id),
 'balance_minor',case when p_provider_id=auth.uid() then (select balance_minor from public.service_provider_wallets where user_id=p_provider_id and provider_id=p_provider_id::text) else null end,
 'eligible',public.universal_service_provider_eligible_v1(p_provider_id));
$$;
revoke all on function public.universal_service_provider_summary_v1(uuid) from public,anon;
grant execute on function public.universal_service_provider_summary_v1(uuid) to authenticated;
create or replace function public.universal_service_provider_summaries_v1(p_provider_ids uuid[])
returns jsonb language plpgsql stable security definer set search_path='' as $$
declare result jsonb;
begin
 if auth.uid() is null then raise exception 'AUTHENTICATION_REQUIRED' using errcode='42501'; end if;
 if coalesce(cardinality(p_provider_ids),0)>200 then raise exception 'PROFILE_BATCH_LIMIT'; end if;
 select coalesce(jsonb_agg(public.universal_service_provider_summary_v1(id)),'[]'::jsonb) into result
 from (select distinct unnest(p_provider_ids) as id) p where id is not null;
 return result;
end; $$;
revoke all on function public.universal_service_provider_summaries_v1(uuid[]) from public,anon;
grant execute on function public.universal_service_provider_summaries_v1(uuid[]) to authenticated;
commit;
