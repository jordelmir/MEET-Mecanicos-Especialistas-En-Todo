begin;
create role anon;
create role authenticated;
create role service_role;
create function auth.role() returns text language sql stable as $$ select current_user::text $$;
create table public.user_profiles(id uuid primary key,auth_user_id uuid);
create table public.provider_profiles(id uuid primary key,user_profile_id uuid,provider_type text,is_active boolean,is_verified boolean,status text);
create table public.service_definitions(id text primary key);
create table if not exists public.universal_service_requests (
    id uuid primary key default gen_random_uuid(),
    client_id uuid not null references auth.users(id) on delete restrict,
    service_definition_id text not null references public.service_definitions(id),
    modality text not null check (modality in ('PHYSICAL', 'DIGITAL', 'HYBRID')),
    title text not null check (char_length(title) between 3 and 160),
    description text not null check (char_length(description) between 10 and 5000),
    intake jsonb not null default '{}'::jsonb,
    location text,
    location_label text,
    offered_price_minor bigint not null check (offered_price_minor > 0),
    final_price_minor bigint check (final_price_minor is null or final_price_minor > 0),
    currency text not null check (currency ~ '^[A-Z]{3}$'),
    state text not null default 'OPEN'
        check (state in ('DRAFT', 'OPEN', 'ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'DISPUTED')),
    assigned_provider_id uuid references auth.users(id) on delete restrict,
    accepted_offer_id uuid,
    payment_state text not null default 'NOT_STARTED'
        check (payment_state in ('NOT_STARTED', 'PENDING', 'AUTHORIZED', 'CAPTURED', 'REFUNDED', 'FAILED')),
    version bigint not null default 1,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.universal_service_offers (
    id uuid primary key default gen_random_uuid(),
    request_id uuid not null references public.universal_service_requests(id) on delete cascade,
    provider_id uuid not null references auth.users(id) on delete restrict,
    price_minor bigint not null check (price_minor > 0),
    currency text not null check (currency ~ '^[A-Z]{3}$'),
    eta_minutes integer check (eta_minutes is null or eta_minutes between 0 and 43200),
    warranty_days integer not null default 0 check (warranty_days between 0 and 3650),
    scope jsonb not null default '{}'::jsonb,
    state text not null default 'PENDING'
        check (state in ('PENDING', 'ACCEPTED', 'REJECTED', 'WITHDRAWN')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique(request_id, provider_id)
);

alter table public.universal_service_requests
    add constraint universal_service_requests_accepted_offer_fk
    foreign key (accepted_offer_id) references public.universal_service_offers(id)
    deferrable initially deferred;

create unique index if not exists universal_service_one_accepted_offer
    on public.universal_service_offers(request_id) where state = 'ACCEPTED';

-- ══════════════════════════════════════════════════════════════════════
-- MEET / ELYSIUM SERVICE PROVIDER WALLETS & CONSTITUTIONAL 5% FEE
-- Date: 2026-09-25
-- Enables:
-- 1. 5% Constitutional Commission (500 bps) across all service verticals
--    (Mechanics, Tow Trucks, Hardware, Locksmiths, Plumbers, Electricians, Sodas, Pulperías).
-- 2. Initial Starter Balance Grant of ₡5,000 CRC for all service providers.
-- 3. Pre-paid balance wallet guard: providers must hold sufficient balance to accept jobs.
-- 4. Immutable double-entry ledger with transparent transaction history.
-- ══════════════════════════════════════════════════════════════════════

-- 1. SERVICE PROVIDER WALLETS TABLE
create table if not exists public.service_provider_wallets (
  provider_id text primary key,
  user_id uuid references auth.users(id) on delete cascade,
  business_name text not null default 'Proveedor de Servicios',
  category text not null default 'AUTOMOTIVE_MECHANIC',
  balance_minor bigint not null default 5000, -- ₡5,000 CRC Starter Bonus
  currency text not null default 'CRC',
  status text not null check (status in ('ACTIVE', 'SUSPENDED_LOW_BALANCE', 'FROZEN')) default 'ACTIVE',
  min_balance_threshold_minor bigint not null default 500, -- ₡500 CRC minimum floor
  commission_bps bigint not null default 500, -- Strict 5.0% Constitutional Platform Fee
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists idx_sp_wallets_user on public.service_provider_wallets(user_id);
create index if not exists idx_sp_wallets_status on public.service_provider_wallets(status);
create index if not exists idx_sp_wallets_category on public.service_provider_wallets(category);

-- 2. SERVICE PROVIDER WALLET LEDGER (IMMUTABLE AUDIT TRAIL)
create table if not exists public.service_provider_wallet_ledger (
  id uuid primary key default gen_random_uuid(),
  provider_id text not null references public.service_provider_wallets(provider_id) on delete cascade,
  idempotency_key text unique not null,
  entry_type text not null check (entry_type in (
    'WELCOME_BONUS',
    'CONSTITUTIONAL_FEE_5_PERCENT',
    'TOPUP_SINPE_CONFIRMED',
    'TOPUP_MANUAL_CONFIRMED',
    'ADJUSTMENT_CREDIT',
    'FEE_REFUND'
  )),
  amount_minor bigint not null,
  currency text not null default 'CRC',
  direction text not null check (direction in ('CREDIT', 'DEBIT')),
  reference_id text,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create index if not exists idx_sp_ledger_provider on public.service_provider_wallet_ledger(provider_id);
create index if not exists idx_sp_ledger_created on public.service_provider_wallet_ledger(created_at desc);

-- 3. RLS POLICIES
alter table public.service_provider_wallets enable row level security;
alter table public.service_provider_wallet_ledger enable row level security;

-- Read wallet: Provider owner or service role
create policy "Providers can view own wallet"
  on public.service_provider_wallets
  for select
  using (
    auth.uid() = user_id
    or auth.role() = 'service_role'
  );

-- Read ledger: Provider owner or service role
create policy "Providers can view own ledger entries"
  on public.service_provider_wallet_ledger
  for select
  using (
    exists (
      select 1 from public.service_provider_wallets w
      where w.provider_id = service_provider_wallet_ledger.provider_id
      and (w.user_id = auth.uid() or auth.role() = 'service_role')
    )
  );

-- 4. AUTHORITATIVE FUNCTIONS (ATOMIC WALLET OPERATIONS)

-- A. Ensure Starter Balance Credit (₡5,000 CRC Initial Bonus)
create or replace function public.service_provider_wallet_ensure_starter_credit_v1(
  p_provider_id text,
  p_business_name text default 'Proveedor de Servicios',
  p_category text default 'AUTOMOTIVE_MECHANIC'
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  v_uid uuid := (select auth.uid());
  v_wallet public.service_provider_wallets%rowtype;
  v_starter_bonus bigint := 5000; -- ₡5,000 CRC
begin
  -- Upsert wallet record
  insert into public.service_provider_wallets (
    provider_id, user_id, business_name, category, balance_minor, currency, status
  ) values (
    p_provider_id, coalesce(v_uid, gen_random_uuid()), p_business_name, p_category, v_starter_bonus, 'CRC', 'ACTIVE'
  )
  on conflict (provider_id) do nothing;

  -- Record welcome bonus in ledger idempotently
  insert into public.service_provider_wallet_ledger (
    provider_id, idempotency_key, entry_type, amount_minor, currency, direction, metadata
  ) values (
    p_provider_id,
    'starter-bonus:' || p_provider_id,
    'WELCOME_BONUS',
    v_starter_bonus,
    'CRC',
    'CREDIT',
    jsonb_build_object(
      'description', 'Bono Constitucional de Bienvenida para Servicios MEET (₡5,000 CRC)',
      'initial_balance', v_starter_bonus,
      'currency', 'CRC'
    )
  )
  on conflict (idempotency_key) do nothing;

  select * into v_wallet from public.service_provider_wallets where provider_id = p_provider_id;

  return jsonb_build_object(
    'provider_id', v_wallet.provider_id,
    'balance_minor', v_wallet.balance_minor,
    'currency', v_wallet.currency,
    'status', v_wallet.status,
    'commission_bps', v_wallet.commission_bps
  );
end; $$;

-- B. Deduct Constitutional 5% Fee for Service Acceptance / Completion
create or replace function public.service_provider_wallet_deduct_fee_v1(
  p_provider_id text,
  p_service_id text,
  p_gross_amount_minor bigint,
  p_service_category text default 'GENERAL_SERVICE'
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  v_wallet public.service_provider_wallets%rowtype;
  v_fee_minor bigint;
  v_idempotency_key text := 'fee:service:' || p_service_id || ':' || p_provider_id;
begin
  if p_gross_amount_minor <= 0 then
    raise exception using errcode='22023', message='INVALID_SERVICE_AMOUNT';
  end if;

  -- Calculate constitutional 5% (500 bps = 5.0%)
  v_fee_minor := (p_gross_amount_minor * 500) / 10000;
  if v_fee_minor <= 0 then
    v_fee_minor := 1; -- Minimum 1 CRC
  end if;

  -- Lock wallet row
  select * into v_wallet from public.service_provider_wallets
  where provider_id = p_provider_id for update;

  if not found then
    -- Ensure wallet exists with starter grant first
    perform public.service_provider_wallet_ensure_starter_credit_v1(p_provider_id);
    select * into v_wallet from public.service_provider_wallets
    where provider_id = p_provider_id for update;
  end if;

  -- Check if fee was already deducted (Idempotency)
  if exists (
    select 1 from public.service_provider_wallet_ledger
    where idempotency_key = v_idempotency_key
  ) then
    return jsonb_build_object(
      'status', 'ALREADY_DEDUCTED',
      'fee_minor', v_fee_minor,
      'current_balance_minor', v_wallet.balance_minor
    );
  end if;

  -- Validate sufficient funds
  if v_wallet.balance_minor < v_fee_minor then
    raise exception using errcode='P0001',
      message='INSUFFICIENT_BALANCE_FOR_CONSTITUTIONAL_FEE: Saldo insuficiente (₡' || v_wallet.balance_minor || ' CRC) para cubrir la comisión del 5% (₡' || v_fee_minor || ' CRC). Recarga tu saldo para operar.';
  end if;

  -- Deduct fee from balance
  update public.service_provider_wallets
  set balance_minor = balance_minor - v_fee_minor,
      updated_at = now()
  where provider_id = p_provider_id;

  -- Record debit in ledger
  insert into public.service_provider_wallet_ledger (
    provider_id, idempotency_key, entry_type, amount_minor, currency, direction, reference_id, metadata
  ) values (
    p_provider_id,
    v_idempotency_key,
    'CONSTITUTIONAL_FEE_5_PERCENT',
    v_fee_minor,
    'CRC',
    'DEBIT',
    p_service_id,
    jsonb_build_object(
      'gross_amount_minor', p_gross_amount_minor,
      'commission_bps', 500,
      'category', p_service_category,
      'fee_crc', v_fee_minor
    )
  );

  return jsonb_build_object(
    'status', 'SUCCESS',
    'fee_minor', v_fee_minor,
    'remaining_balance_minor', v_wallet.balance_minor - v_fee_minor,
    'currency', 'CRC'
  );
end; $$;

-- C. Top-up Wallet Balance (SINPE Móvil / Recarga)
create or replace function public.service_provider_wallet_topup_v1(
  p_provider_id text,
  p_amount_minor bigint,
  p_reference text default 'SINPE-MOVIL'
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  v_wallet public.service_provider_wallets%rowtype;
  v_idempotency_key text := 'topup:' || p_provider_id || ':' || gen_random_uuid();
begin
  if p_amount_minor < 500 then
    raise exception using errcode='22023', message='MINIMUM_TOPUP_500_CRC';
  end if;

  select * into v_wallet from public.service_provider_wallets
  where provider_id = p_provider_id for update;

  if not found then
    perform public.service_provider_wallet_ensure_starter_credit_v1(p_provider_id);
    select * into v_wallet from public.service_provider_wallets
    where provider_id = p_provider_id for update;
  end if;

  update public.service_provider_wallets
  set balance_minor = balance_minor + p_amount_minor,
      status = 'ACTIVE',
      updated_at = now()
  where provider_id = p_provider_id;

  insert into public.service_provider_wallet_ledger (
    provider_id, idempotency_key, entry_type, amount_minor, currency, direction, reference_id, metadata
  ) values (
    p_provider_id,
    v_idempotency_key,
    'TOPUP_SINPE_CONFIRMED',
    p_amount_minor,
    'CRC',
    'CREDIT',
    p_reference,
    jsonb_build_object('reference', p_reference, 'topup_crc', p_amount_minor)
  );

  return jsonb_build_object(
    'status', 'TOPUP_SUCCESS',
    'new_balance_minor', v_wallet.balance_minor + p_amount_minor,
    'currency', 'CRC'
  );
end; $$;

-- Financial mutations are internal server operations. No anonymous/end-user credit RPC.
revoke all on function public.service_provider_wallet_ensure_starter_credit_v1(text,text,text) from public, anon, authenticated;
revoke all on function public.service_provider_wallet_deduct_fee_v1(text,text,bigint,text) from public, anon, authenticated;
revoke all on function public.service_provider_wallet_topup_v1(text,bigint,text) from public, anon, authenticated;
grant execute on function public.service_provider_wallet_ensure_starter_credit_v1(text,text,text) to service_role;
grant execute on function public.service_provider_wallet_deduct_fee_v1(text,text,bigint,text) to service_role;
grant execute on function public.service_provider_wallet_topup_v1(text,bigint,text) to service_role;


alter table public.universal_service_requests enable row level security;
alter table public.universal_service_offers enable row level security;
create policy universal_requests_client_insert on public.universal_service_requests for insert to authenticated with check(client_id=auth.uid());
create policy universal_requests_client_update on public.universal_service_requests for update to authenticated using(client_id=auth.uid());
create policy universal_requests_client_delete on public.universal_service_requests for delete to authenticated using(client_id=auth.uid());
create table public.ride_wallets(driver_id uuid primary key,currency text default 'CRC');
create table public.ride_wallet_ledger(id uuid primary key default gen_random_uuid(),driver_id uuid,idempotency_key text unique,entry_type text,amount_minor bigint,currency text,direction text,withdrawable boolean,metadata jsonb);
create table public.ride_commission_reservations(driver_id uuid,currency text,amount_minor bigint,state text);
CREATE OR REPLACE FUNCTION public.ride_wallet_balance_v1()
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_driver uuid := (select auth.uid());
    v_currency text := 'CRC';
    v_posted bigint := 0;
    v_reserved bigint := 0;
BEGIN
    IF v_driver IS NULL THEN
        RAISE EXCEPTION USING errcode='42501', message='UNAUTHENTICATED';
    END IF;

    -- Acquire row lock to serialize concurrent wallet operations
    PERFORM 1 FROM public.ride_wallets
    WHERE driver_id = v_driver
    FOR UPDATE;

    SELECT coalesce(max(currency), 'CRC') INTO v_currency
    FROM public.ride_wallets WHERE driver_id = v_driver;

    SELECT coalesce(sum(CASE
        WHEN l.direction = 'CREDIT' THEN l.amount_minor
        WHEN l.direction = 'DEBIT' AND l.entry_type <> 'COMMISSION_RESERVED' THEN -l.amount_minor
        ELSE 0 END), 0)
    INTO v_posted
    FROM public.ride_wallet_ledger l
    WHERE l.driver_id = v_driver AND l.currency = v_currency;

    SELECT coalesce(sum(r.amount_minor), 0)
    INTO v_reserved
    FROM public.ride_commission_reservations r
    WHERE r.driver_id = v_driver AND r.currency = v_currency AND r.state = 'RESERVED';

    RETURN jsonb_build_object(
        'currency', v_currency,
        'posted_minor', v_posted,
        'reserved_minor', v_reserved,
        'available_minor', greatest(0, v_posted - v_reserved)
    );
END;
$$;
