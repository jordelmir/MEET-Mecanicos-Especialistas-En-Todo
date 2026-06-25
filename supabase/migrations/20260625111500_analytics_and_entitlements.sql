-- Analytics, Google Play receipts, and entitlements.
-- Clients can insert analytics, but cannot read the analytics stream.

create table if not exists public.analytics_events (
  id uuid primary key,
  event_name text not null,
  anonymous_id text not null,
  user_id uuid null,
  session_id text not null,
  event_timestamp timestamptz not null,
  app_version text,
  route text,
  referrer text,
  user_agent text,
  device_type text,
  viewport_width int,
  viewport_height int,
  locale text,
  timezone text,
  properties jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create index if not exists idx_analytics_events_name on public.analytics_events(event_name);
create index if not exists idx_analytics_events_user on public.analytics_events(user_id);
create index if not exists idx_analytics_events_anon on public.analytics_events(anonymous_id);
create index if not exists idx_analytics_events_timestamp on public.analytics_events(event_timestamp);
create index if not exists idx_analytics_events_route on public.analytics_events(route);
create index if not exists idx_analytics_events_properties_gin on public.analytics_events using gin(properties);

alter table public.analytics_events enable row level security;

drop policy if exists "analytics insert only anon authenticated" on public.analytics_events;
create policy "analytics insert only anon authenticated"
on public.analytics_events
for insert
to anon, authenticated
with check (true);

drop policy if exists "analytics no client select" on public.analytics_events;
create policy "analytics no client select"
on public.analytics_events
for select
to anon, authenticated
using (false);

create table if not exists public.billing_products (
  product_id text primary key,
  product_type text not null check (product_type in ('inapp', 'subs')),
  entitlement_key text not null,
  display_name text not null,
  tier text not null default 'pro',
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

insert into public.billing_products (product_id, product_type, entitlement_key, display_name, tier)
values
  ('pro_monthly', 'subs', 'pro', 'MEET PRO Mensual', 'pro'),
  ('pro_yearly', 'subs', 'pro', 'MEET PRO Anual', 'pro'),
  ('workshop_monthly', 'subs', 'workshop', 'MEET Taller Mensual', 'business'),
  ('pro_lifetime', 'inapp', 'pro_lifetime', 'MEET PRO Lifetime', 'lifetime'),
  ('gauge_pack_elite', 'inapp', 'gauge_pack_elite', 'Paquete Elite de Gauges', 'pro'),
  ('report_pack', 'inapp', 'report_pack', 'Paquete de Reportes PDF', 'pro')
on conflict (product_id) do update
set product_type = excluded.product_type,
    entitlement_key = excluded.entitlement_key,
    display_name = excluded.display_name,
    tier = excluded.tier,
    updated_at = now();

insert into public.billing_products (product_id, product_type, entitlement_key, display_name, tier)
select
  'gauge_tier_' || tier::text,
  'inapp',
  'gauge_marketplace_purchase',
  'Gauge Marketplace Tier ' || tier::text,
  'marketplace'
from generate_series(1, 10) as tier
on conflict (product_id) do update
set product_type = excluded.product_type,
    entitlement_key = excluded.entitlement_key,
    display_name = excluded.display_name,
    tier = excluded.tier,
    updated_at = now();

create table if not exists public.google_play_purchase_receipts (
  id uuid primary key default gen_random_uuid(),
  user_id uuid null references auth.users(id) on delete set null,
  product_id text not null references public.billing_products(product_id),
  product_type text not null check (product_type in ('inapp', 'subs')),
  purchase_token_hash text not null unique,
  order_id text,
  purchase_state text not null default 'unknown',
  acknowledgement_state text,
  consumption_state text,
  expiry_time timestamptz,
  raw_response jsonb not null default '{}'::jsonb,
  verified_at timestamptz not null default now(),
  created_at timestamptz not null default now()
);

create index if not exists idx_google_play_receipts_user on public.google_play_purchase_receipts(user_id);
create index if not exists idx_google_play_receipts_product on public.google_play_purchase_receipts(product_id);
create index if not exists idx_google_play_receipts_expiry on public.google_play_purchase_receipts(expiry_time);

alter table public.google_play_purchase_receipts enable row level security;

drop policy if exists "receipts readable by owner" on public.google_play_purchase_receipts;
create policy "receipts readable by owner"
on public.google_play_purchase_receipts
for select
to authenticated
using (auth.uid() = user_id);

drop policy if exists "receipts no client write" on public.google_play_purchase_receipts;
create policy "receipts no client write"
on public.google_play_purchase_receipts
for all
to anon, authenticated
using (false)
with check (false);

create table if not exists public.user_entitlements (
  id uuid primary key default gen_random_uuid(),
  user_id uuid null references auth.users(id) on delete cascade,
  anonymous_id text,
  entitlement_key text not null,
  product_id text not null references public.billing_products(product_id),
  source text not null default 'google_play',
  status text not null check (status in ('active', 'grace_period', 'on_hold', 'paused', 'expired', 'revoked')),
  starts_at timestamptz not null default now(),
  expires_at timestamptz,
  latest_receipt_id uuid references public.google_play_purchase_receipts(id) on delete set null,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint entitlements_identity_check check (user_id is not null or anonymous_id is not null)
);

create unique index if not exists idx_user_entitlements_unique_user_product
on public.user_entitlements(user_id, product_id)
where user_id is not null;

create unique index if not exists idx_user_entitlements_unique_anon_product
on public.user_entitlements(anonymous_id, product_id)
where anonymous_id is not null;

create index if not exists idx_user_entitlements_user_status on public.user_entitlements(user_id, status);
create index if not exists idx_user_entitlements_key_status on public.user_entitlements(entitlement_key, status);

alter table public.user_entitlements enable row level security;

drop policy if exists "entitlements readable by owner" on public.user_entitlements;
create policy "entitlements readable by owner"
on public.user_entitlements
for select
to authenticated
using (auth.uid() = user_id);

drop policy if exists "entitlements no client write" on public.user_entitlements;
create policy "entitlements no client write"
on public.user_entitlements
for all
to anon, authenticated
using (false)
with check (false);
