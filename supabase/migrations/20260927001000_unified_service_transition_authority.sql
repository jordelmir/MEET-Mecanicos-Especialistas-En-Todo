begin;
-- A single authority for Services Elysium. Clients cannot assign providers,
-- manufacture completion, or choose the amount charged to a provider wallet.
create or replace function public.universal_service_provider_eligible_v1(p_actor uuid)
returns boolean language sql stable security definer set search_path='' as $$
 select exists(select 1 from public.provider_profiles p
 join public.user_profiles u on u.id=p.user_profile_id
 where u.auth_user_id=p_actor and p.is_active and p.is_verified and p.status='active'
 and p.provider_type in ('service_provider','SERVICE_PROVIDER'));
$$;
revoke all on function public.universal_service_provider_eligible_v1(uuid) from public,anon;
grant execute on function public.universal_service_provider_eligible_v1(uuid) to authenticated,service_role;

drop policy if exists universal_requests_client_insert on public.universal_service_requests;
drop policy if exists universal_requests_client_update on public.universal_service_requests;
drop policy if exists universal_requests_client_delete on public.universal_service_requests;
revoke update,delete on public.universal_service_requests from authenticated;
drop policy if exists universal_requests_client_all on public.universal_service_requests;
create policy universal_requests_client_read on public.universal_service_requests
for select to authenticated using(client_id=auth.uid());
create policy universal_requests_client_create on public.universal_service_requests
for insert to authenticated with check(client_id=auth.uid() and state='OPEN'
 and assigned_provider_id is null and accepted_offer_id is null and final_price_minor is null
 and payment_state='NOT_STARTED' and version=1);
drop policy if exists universal_offers_provider_write on public.universal_service_offers;
create policy universal_offers_provider_create on public.universal_service_offers
for insert to authenticated with check(provider_id=auth.uid() and state='PENDING'
 and public.universal_service_provider_eligible_v1(auth.uid())
 and exists(select 1 from public.universal_service_requests r where r.id=request_id
 and r.state='OPEN' and r.client_id<>auth.uid() and r.currency=universal_service_offers.currency));

create or replace function public.universal_service_transition_v1(
 p_request_id uuid,p_action text,p_offer_id uuid default null
) returns public.universal_service_requests
language plpgsql security definer set search_path='' as $$
declare
 a uuid:=auth.uid(); r public.universal_service_requests%rowtype;
 o public.universal_service_offers%rowtype;
begin
 if a is null then raise exception 'AUTHENTICATION_REQUIRED' using errcode='42501'; end if;
 select * into r from public.universal_service_requests where id=p_request_id for update;
 if not found then raise exception 'SERVICE_NOT_FOUND' using errcode='P0002'; end if;
 if p_action='ACCEPT' then
  if a<>r.client_id then raise exception 'CLIENT_REQUIRED' using errcode='42501'; end if;
  if r.state='ASSIGNED' and r.accepted_offer_id=p_offer_id then return r; end if;
  if r.state<>'OPEN' then raise exception 'SERVICE_NOT_OPEN'; end if;
  select * into o from public.universal_service_offers where id=p_offer_id and request_id=r.id for update;
  if not found or o.state<>'PENDING' or o.currency<>r.currency or o.provider_id=a
   or not public.universal_service_provider_eligible_v1(o.provider_id) then raise exception 'OFFER_NOT_ELIGIBLE'; end if;
  -- Existing wallet contract is CRC. Other currencies are unavailable until a
  -- matching authoritative wallet exists; no silent conversion is permitted.
  if r.currency<>'CRC' then raise exception 'WALLET_CURRENCY_UNAVAILABLE'; end if;
  insert into public.service_provider_wallets(provider_id,user_id,balance_minor,currency)
   values(o.provider_id::text,o.provider_id,5000,r.currency) on conflict(provider_id) do nothing;
  perform public.service_provider_wallet_ensure_starter_credit_v1(o.provider_id::text);
  if not exists(select 1 from public.service_provider_wallets
   where provider_id=o.provider_id::text and user_id=o.provider_id and currency=r.currency and status='ACTIVE') then
   raise exception 'PROVIDER_WALLET_UNAVAILABLE'; end if;
  perform public.service_provider_wallet_deduct_fee_v1(o.provider_id::text,r.id::text,o.price_minor,r.service_definition_id);
  update public.universal_service_offers set state=case when id=o.id then 'ACCEPTED' else 'REJECTED' end,updated_at=now()
   where request_id=r.id and state='PENDING';
  update public.universal_service_requests set state='ASSIGNED',assigned_provider_id=o.provider_id,
   accepted_offer_id=o.id,final_price_minor=o.price_minor,version=version+1,updated_at=now()
   where id=r.id returning * into r;
 elsif p_action='START' then
  if a is distinct from r.assigned_provider_id then raise exception 'PROVIDER_REQUIRED' using errcode='42501'; end if;
  if r.state='IN_PROGRESS' then return r; end if;
  if r.state<>'ASSIGNED' then raise exception 'SERVICE_NOT_ASSIGNED'; end if;
  update public.universal_service_requests set state='IN_PROGRESS',version=version+1,updated_at=now() where id=r.id returning * into r;
 elsif p_action='COMPLETE' then
  -- Client acknowledgement closes physical service; it does not imply payment.
  if a<>r.client_id then raise exception 'CLIENT_REQUIRED' using errcode='42501'; end if;
  if r.state='COMPLETED' then return r; end if;
  if r.state<>'IN_PROGRESS' then raise exception 'SERVICE_NOT_IN_PROGRESS'; end if;
  update public.universal_service_requests set state='COMPLETED',version=version+1,updated_at=now() where id=r.id returning * into r;
 elsif p_action='CANCEL' then
  if a<>r.client_id then raise exception 'CLIENT_REQUIRED' using errcode='42501'; end if;
  if r.state='CANCELLED' then return r; end if;
  if r.state not in ('DRAFT','OPEN') then raise exception 'ASSIGNED_SERVICE_REQUIRES_RESOLUTION'; end if;
  update public.universal_service_requests set state='CANCELLED',version=version+1,updated_at=now() where id=r.id returning * into r;
 else raise exception 'UNKNOWN_SERVICE_ACTION'; end if;
 return r;
end; $$;
revoke all on function public.universal_service_transition_v1(uuid,text,uuid) from public,anon;
grant execute on function public.universal_service_transition_v1(uuid,text,uuid) to authenticated;

create table if not exists public.universal_service_ratings(
 request_id uuid primary key references public.universal_service_requests(id),
 client_id uuid not null references auth.users(id),provider_id uuid not null references auth.users(id),
 stars integer not null check(stars between 1 and 5),created_at timestamptz not null default now()
);
alter table public.universal_service_ratings enable row level security;
grant select on public.universal_service_ratings to authenticated;
create policy universal_ratings_read on public.universal_service_ratings for select to authenticated using(client_id=auth.uid() or provider_id=auth.uid());
create or replace function public.universal_service_rate_v1(p_request_id uuid,p_stars integer)
returns public.universal_service_ratings language plpgsql security definer set search_path='' as $$
declare r public.universal_service_requests%rowtype; rating public.universal_service_ratings%rowtype;
begin
 select * into r from public.universal_service_requests where id=p_request_id for update;
 if not found or auth.uid() is distinct from r.client_id then raise exception 'CLIENT_REQUIRED' using errcode='42501'; end if;
 if r.state<>'COMPLETED' or r.assigned_provider_id is null then raise exception 'COMPLETED_SERVICE_REQUIRED'; end if;
 if p_stars is null or p_stars not between 1 and 5 then raise exception 'INVALID_RATING'; end if;
 select * into rating from public.universal_service_ratings where request_id=r.id;
 if found then
  if rating.stars<>p_stars then raise exception 'RATING_IMMUTABLE'; end if;
  return rating;
 end if;
 insert into public.universal_service_ratings(request_id,client_id,provider_id,stars)
 values(r.id,r.client_id,r.assigned_provider_id,p_stars) returning * into rating;
 return rating;
end; $$;
revoke all on function public.universal_service_rate_v1(uuid,integer) from public,anon;
grant execute on function public.universal_service_rate_v1(uuid,integer) to authenticated;
create or replace function public.universal_service_provider_summary_v1(p_provider_id uuid)
returns jsonb language sql stable security definer set search_path='' as $$
 select jsonb_build_object(
 'completed', (select count(*) from public.universal_service_requests where assigned_provider_id=p_provider_id and state='COMPLETED'),
 'reviews',(select count(*) from public.universal_service_ratings where provider_id=p_provider_id),
 'rating',(select avg(stars) from public.universal_service_ratings where provider_id=p_provider_id),
 'balance_minor',case when p_provider_id=auth.uid() then (select balance_minor from public.service_provider_wallets where user_id=p_provider_id and provider_id=p_provider_id::text) else null end,
 'eligible',public.universal_service_provider_eligible_v1(p_provider_id));
$$;
revoke all on function public.universal_service_provider_summary_v1(uuid) from public,anon;
grant execute on function public.universal_service_provider_summary_v1(uuid) to authenticated;
create or replace function public.universal_service_wallet_transfer_v1(p_transfer_id uuid,p_amount_minor bigint)
returns jsonb language plpgsql security definer set search_path='' as $$
declare a uuid:=auth.uid(); balance jsonb; prior public.ride_wallet_ledger%rowtype; key text;
begin
 if a is null or not public.universal_service_provider_eligible_v1(a) then raise exception 'VERIFIED_PROVIDER_REQUIRED' using errcode='42501'; end if;
 if p_transfer_id is null or p_amount_minor is null or p_amount_minor<=0 then raise exception 'INVALID_AMOUNT'; end if;
 key:='service-wallet-transfer:'||a::text||':'||p_transfer_id::text;
 perform 1 from public.ride_wallets where driver_id=a for update;
 if not found then raise exception 'APPROVED_BALANCE_REQUIRED'; end if;
 select * into prior from public.ride_wallet_ledger where idempotency_key=key;
 if found then
  if prior.driver_id<>a or prior.amount_minor<>p_amount_minor then raise exception 'IDEMPOTENCY_PAYLOAD_MISMATCH'; end if;
  return jsonb_build_object('status','ALREADY_TRANSFERRED');
 end if;
 balance:=public.ride_wallet_balance_v1();
 if balance->>'currency'<>'CRC' or (balance->>'available_minor')::bigint<p_amount_minor then raise exception 'INSUFFICIENT_APPROVED_BALANCE'; end if;
 perform public.service_provider_wallet_ensure_starter_credit_v1(a::text);
 perform 1 from public.service_provider_wallets where provider_id=a::text and user_id=a and currency='CRC' and status in ('ACTIVE','SUSPENDED_LOW_BALANCE') for update;
 if not found then raise exception 'PROVIDER_WALLET_UNAVAILABLE'; end if;
 insert into public.ride_wallet_ledger(driver_id,idempotency_key,entry_type,amount_minor,currency,direction,withdrawable,metadata)
 values(a,key,'ADJUSTMENT',p_amount_minor,'CRC','DEBIT',false,jsonb_build_object('reason','TRANSFER_TO_SERVICE_WALLET','transfer_id',p_transfer_id));
 insert into public.service_provider_wallet_ledger(provider_id,idempotency_key,entry_type,amount_minor,currency,direction,reference_id,metadata)
 values(a::text,key,'ADJUSTMENT_CREDIT',p_amount_minor,'CRC','CREDIT',p_transfer_id::text,jsonb_build_object('source','APPROVED_SINPE_WALLET_TRANSFER'));
 update public.service_provider_wallets set balance_minor=balance_minor+p_amount_minor,status=case when balance_minor+p_amount_minor>=min_balance_threshold_minor then 'ACTIVE' else status end,updated_at=now() where provider_id=a::text;
 return jsonb_build_object('status','TRANSFERRED','amount_minor',p_amount_minor,'currency','CRC');
end; $$;
revoke all on function public.universal_service_wallet_transfer_v1(uuid,bigint) from public,anon;
grant execute on function public.universal_service_wallet_transfer_v1(uuid,bigint) to authenticated;

commit;
