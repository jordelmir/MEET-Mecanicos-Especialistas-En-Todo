-- Driver wallet onboarding and manual SINPE top-up review.
-- A receipt is evidence for review only; it never credits a wallet by itself.

insert into storage.buckets(id, name, public, file_size_limit, allowed_mime_types)
values (
  'ride-wallet-proofs', 'ride-wallet-proofs', false, 12582912,
  array['image/jpeg','image/png','image/webp','application/pdf']
)
on conflict (id) do update set
  public = false,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

create table if not exists public.ride_wallet_policy (
  policy_id boolean primary key default true check (policy_id),
  currency text not null default 'CRC' check (currency = 'CRC'),
  starter_credit_minor bigint not null default 15000 check (starter_credit_minor >= 0),
  commission_basis_points integer not null default 500 check (commission_basis_points between 0 and 10000),
  sinpe_phone text not null default '63194029',
  sinpe_recipient_name text not null default 'Jorge David Del Valle Miranda',
  updated_at timestamptz not null default now()
);

insert into public.ride_wallet_policy(policy_id)
values (true)
on conflict (policy_id) do update set
  starter_credit_minor = 15000,
  commission_basis_points = 500,
  sinpe_phone = '63194029',
  sinpe_recipient_name = 'Jorge David Del Valle Miranda',
  updated_at = now();

create table if not exists public.ride_wallet_topups (
  id uuid primary key default gen_random_uuid(),
  driver_id uuid not null references auth.users(id) on delete restrict,
  amount_minor bigint not null check (amount_minor between 100 and 1000000000),
  currency text not null default 'CRC' check (currency = 'CRC'),
  sender_phone text,
  transfer_reference text,
  proof_storage_path text not null,
  proof_sha256 text not null check (proof_sha256 ~ '^[a-f0-9]{64}$'),
  proof_byte_count bigint not null check (proof_byte_count between 1 and 12582912),
  proof_mime_type text not null check (proof_mime_type in ('image/jpeg','image/png','image/webp','application/pdf')),
  status text not null default 'PENDING_REVIEW' check (status in ('PENDING_REVIEW','APPROVED','REJECTED')),
  decision_reason text,
  idempotency_key text not null unique check (idempotency_key ~ '^[A-Za-z0-9._:-]{16,128}$'),
  correlation_id uuid not null default gen_random_uuid(),
  submitted_at timestamptz not null default now(),
  reviewed_at timestamptz,
  reviewed_by uuid references auth.users(id) on delete set null
);

create index if not exists ride_wallet_topups_queue_idx
  on public.ride_wallet_topups(status, submitted_at, id);
alter table public.ride_wallet_topups enable row level security;
revoke all on public.ride_wallet_topups from anon, authenticated;
grant select, insert on public.ride_wallet_topups to authenticated;

drop policy if exists ride_wallet_topups_owner_read on public.ride_wallet_topups;
create policy ride_wallet_topups_owner_read on public.ride_wallet_topups
for select to authenticated using (driver_id = auth.uid() or public.meet_is_platform_owner());
drop policy if exists ride_wallet_topups_driver_insert on public.ride_wallet_topups;
create policy ride_wallet_topups_driver_insert on public.ride_wallet_topups
for insert to authenticated with check (driver_id = auth.uid());

drop policy if exists ride_wallet_proof_insert on storage.objects;
create policy ride_wallet_proof_insert on storage.objects
for insert to authenticated with check (
  bucket_id = 'ride-wallet-proofs' and (storage.foldername(name))[1] = auth.uid()::text
);
drop policy if exists ride_wallet_proof_read on storage.objects;
create policy ride_wallet_proof_read on storage.objects
for select to authenticated using (
  bucket_id = 'ride-wallet-proofs' and ((storage.foldername(name))[1] = auth.uid()::text or public.meet_is_platform_owner())
);

create or replace function public.ride_wallet_policy_v1()
returns jsonb language sql stable security definer set search_path = '' as $$
  select jsonb_build_object(
    'currency', currency, 'starter_credit_minor', starter_credit_minor,
    'commission_basis_points', commission_basis_points,
    'sinpe_phone', sinpe_phone, 'sinpe_recipient_name', sinpe_recipient_name
  ) from public.ride_wallet_policy where policy_id = true;
$$;

create or replace function public.ride_wallet_ensure_starter_credit_v1()
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_driver uuid := (select auth.uid()); v_amount bigint; v_verified boolean;
begin
  if v_driver is null then raise exception using errcode='42501', message='UNAUTHENTICATED'; end if;
  select exists(select 1 from public.ride_driver_vehicles v where v.driver_id=v_driver and v.is_active and v.verification_status='VERIFIED') into v_verified;
  if not v_verified then raise exception using errcode='42501', message='VERIFIED_DRIVER_REQUIRED'; end if;
  select starter_credit_minor into v_amount from public.ride_wallet_policy where policy_id=true;
  insert into public.ride_wallets(driver_id,currency) values(v_driver,'CRC') on conflict do nothing;
  insert into public.ride_wallet_ledger(driver_id,idempotency_key,entry_type,amount_minor,currency,direction,withdrawable,metadata)
  values(v_driver,'starter-credit:'||v_driver,'PROMOTIONAL_GRANT',v_amount,'CRC','CREDIT',false,jsonb_build_object('policy','driver-onboarding-v2'))
  on conflict (idempotency_key) do nothing;
  return jsonb_build_object('credited_minor',v_amount,'currency','CRC');
end; $$;

create or replace function public.ride_submit_wallet_topup_v1(
  p_amount_minor bigint, p_proof_storage_path text, p_proof_sha256 text,
  p_proof_byte_count bigint, p_proof_mime_type text, p_idempotency_key text,
  p_sender_phone text default null, p_transfer_reference text default null
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_driver uuid := (select auth.uid()); v_id uuid;
begin
  if v_driver is null then raise exception using errcode='42501', message='UNAUTHENTICATED'; end if;
  if p_amount_minor not between 100 and 1000000000 or p_proof_sha256 !~ '^[a-f0-9]{64}$'
     or p_proof_storage_path !~ ('^' || v_driver::text || '/[A-Za-z0-9._/-]+$')
     or not exists(select 1 from storage.objects where bucket_id='ride-wallet-proofs' and name=p_proof_storage_path)
  then raise exception using errcode='22023', message='INVALID_TOPUP_PROOF'; end if;
  insert into public.ride_wallet_topups(driver_id,amount_minor,sender_phone,transfer_reference,proof_storage_path,proof_sha256,proof_byte_count,proof_mime_type,idempotency_key)
  values(v_driver,p_amount_minor,nullif(trim(p_sender_phone),''),nullif(trim(p_transfer_reference),''),p_proof_storage_path,p_proof_sha256,p_proof_byte_count,p_proof_mime_type,p_idempotency_key)
  on conflict (idempotency_key) do update set proof_storage_path=excluded.proof_storage_path
  returning id into v_id;
  return jsonb_build_object('id',v_id,'status','PENDING_REVIEW');
end; $$;

create or replace function public.ride_owner_wallet_topup_queue_v1(p_status text default 'PENDING_REVIEW', p_limit integer default 100)
returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare v_items jsonb; begin
  if not public.meet_is_platform_owner() then raise exception using errcode='42501', message='PLATFORM_OWNER_REQUIRED'; end if;
  if p_status not in ('ALL','PENDING_REVIEW','APPROVED','REJECTED') or p_limit not between 1 and 200 then raise exception using errcode='22023', message='INVALID_QUEUE_FILTER'; end if;
  select coalesce(jsonb_agg(to_jsonb(t) order by t.submitted_at,t.id),'[]'::jsonb) into v_items
  from (select * from public.ride_wallet_topups where p_status='ALL' or status=p_status order by submitted_at,id limit p_limit) t;
  return jsonb_build_object('items',v_items,'server_timestamp',now());
end; $$;

create or replace function public.ride_owner_decide_wallet_topup_v1(p_topup_id uuid,p_decision text,p_reason text)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_topup public.ride_wallet_topups%rowtype; v_owner uuid := (select auth.uid());
begin
  if not public.meet_session_has_aal2() or not public.meet_is_platform_owner() then raise exception using errcode='42501', message='OWNER_AAL2_REQUIRED'; end if;
  if p_decision not in ('APPROVED','REJECTED') then raise exception using errcode='22023', message='INVALID_DECISION'; end if;
  select * into strict v_topup from public.ride_wallet_topups where id=p_topup_id for update;
  if v_topup.status <> 'PENDING_REVIEW' then return jsonb_build_object('id',p_topup_id,'status',v_topup.status); end if;
  update public.ride_wallet_topups set status=p_decision,decision_reason=nullif(trim(p_reason),''),reviewed_at=now(),reviewed_by=v_owner where id=p_topup_id;
  if p_decision='APPROVED' then
    insert into public.ride_wallets(driver_id,currency) values(v_topup.driver_id,'CRC') on conflict do nothing;
    insert into public.ride_wallet_ledger(driver_id,idempotency_key,entry_type,amount_minor,currency,direction,withdrawable,metadata)
    values(v_topup.driver_id,'topup-credit:'||v_topup.id,'TOP_UP_CONFIRMED',v_topup.amount_minor,'CRC','CREDIT',false,jsonb_build_object('topup_id',v_topup.id,'reviewed_by',v_owner))
    on conflict (idempotency_key) do nothing;
  end if;
  return jsonb_build_object('id',v_topup.id,'status',p_decision,'amount_minor',v_topup.amount_minor);
end; $$;

revoke all on function public.ride_wallet_policy_v1() from public;
revoke all on function public.ride_wallet_ensure_starter_credit_v1() from public;
revoke all on function public.ride_submit_wallet_topup_v1(bigint,text,text,bigint,text,text,text,text) from public;
revoke all on function public.ride_owner_wallet_topup_queue_v1(text,integer) from public;
revoke all on function public.ride_owner_decide_wallet_topup_v1(uuid,text,text) from public;
grant execute on function public.ride_wallet_policy_v1() to authenticated;
grant execute on function public.ride_wallet_ensure_starter_credit_v1() to authenticated;
grant execute on function public.ride_submit_wallet_topup_v1(bigint,text,text,bigint,text,text,text,text) to authenticated;
grant execute on function public.ride_owner_wallet_topup_queue_v1(text,integer) to authenticated;
grant execute on function public.ride_owner_decide_wallet_topup_v1(uuid,text,text) to authenticated;
