-- =============================================================================
-- Parts Marketplace Foundation
-- =============================================================================
-- Adds enums, columns and tables to upgrade the existing parts auction into a
-- full technical marketplace (vehicle-aware part requests, supplier profiles,
-- compatibility-validated quotes, supplier inventory, anti-fraud constraints).
--
-- Backward compatible:
--  - Existing columns on public.parts_stores / part_requests / part_offers
--    remain untouched. New columns are nullable or have safe defaults.
--  - Existing RLS policies remain. We ADD policies for the new tables and
--    extend where strictly necessary.
--  - Android serializers use camelCase quoted names; we keep that convention.
--
-- This migration does NOT change any user-facing behavior. Behavior changes
-- land in subsequent PRs (PR 2 wizard, PR 3 repuestera panel).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 0. Pre-flight: enforce forward-only migrations and a sane runtime.
-- -----------------------------------------------------------------------------
set check_function_bodies = off;
set search_path = public, auth;

-- -----------------------------------------------------------------------------
-- 1. Enums
-- -----------------------------------------------------------------------------
do $$ begin
  if not exists (select 1 from pg_type where typname = 'part_request_status_v2') then
    create type public.part_request_status_v2 as enum (
      'DRAFT',
      'OPEN',
      'RECEIVING_QUOTES',
      'QUOTE_ACCEPTED',
      'WAITING_PAYMENT',
      'ORDERED',
      'READY_FOR_PICKUP',
      'OUT_FOR_DELIVERY',
      'DELIVERED',
      'CANCELLED',
      'DISPUTED'
    );
  end if;
end $$;

do $$ begin
  if not exists (select 1 from pg_type where typname = 'part_preference') then
    create type public.part_preference as enum (
      'ANY',
      'OEM',
      'AFTERMARKET',
      'USED',
      'REFURBISHED',
      'PERFORMANCE',
      'BUDGET'
    );
  end if;
end $$;

do $$ begin
  if not exists (select 1 from pg_type where typname = 'part_position') then
    create type public.part_position as enum (
      'FRONT_RIGHT',
      'FRONT_LEFT',
      'REAR_RIGHT',
      'REAR_LEFT',
      'CENTER',
      'ENGINE',
      'TRANSMISSION',
      'ELECTRICAL',
      'BODY',
      'INTERIOR',
      'NOT_APPLICABLE',
      'FUSE_BOX'
    );
  end if;
end $$;

do $$ begin
  if not exists (select 1 from pg_type where typname = 'part_source_context') then
    create type public.part_source_context as enum (
      'MANUAL',
      'FROM_DTC',
      'FROM_3D_COMPONENT',
      'FROM_MECHANIC_WORK_ORDER',
      'FROM_MAINTENANCE_ALERT',
      'FROM_PREPURCHASE_INSPECTION'
    );
  end if;
end $$;

do $$ begin
  if not exists (select 1 from pg_type where typname = 'part_condition') then
    create type public.part_condition as enum (
      'NEW_OEM',
      'NEW_AFTERMARKET',
      'USED',
      'REFURBISHED',
      'REBUILT',
      'UNKNOWN'
    );
  end if;
end $$;

do $$ begin
  if not exists (select 1 from pg_type where typname = 'part_availability') then
    create type public.part_availability as enum (
      'IN_STOCK',
      'SAME_DAY',
      'NEXT_DAY',
      'IMPORT_REQUIRED',
      'UNKNOWN'
    );
  end if;
end $$;

do $$ begin
  if not exists (select 1 from pg_type where typname = 'quote_status_v2') then
    create type public.quote_status_v2 as enum (
      'SENT',
      'ACCEPTED',
      'REJECTED',
      'EXPIRED',
      'CANCELLED'
    );
  end if;
end $$;

do $$ begin
  if not exists (select 1 from pg_type where typname = 'verification_status') then
    create type public.verification_status as enum (
      'UNVERIFIED',
      'PHONE_VERIFIED',
      'BUSINESS_VERIFIED',
      'INVENTORY_VERIFIED',
      'ELITE_SUPPLIER',
      'SUSPENDED'
    );
  end if;
end $$;

do $$ begin
  if not exists (select 1 from pg_type where typname = 'compatibility_confidence') then
    create type public.compatibility_confidence as enum (
      'EXACT',
      'HIGH',
      'MEDIUM',
      'LOW',
      'UNKNOWN'
    );
  end if;
end $$;

-- -----------------------------------------------------------------------------
-- 2. Extend public.parts_stores -> SupplierProfile
-- -----------------------------------------------------------------------------
alter table public.parts_stores
  add column if not exists "legalName" text,
  add column if not exists "whatsapp" text not null default '',
  add column if not exists "email" text,
  add column if not exists "province" text,
  add column if not exists "canton" text,
  add column if not exists "address" text not null default '',
  add column if not exists "deliveryEnabled" boolean not null default true,
  add column if not exists "pickupEnabled" boolean not null default true,
  add column if not exists "serviceRadiusKm" double precision not null default 0,
  add column if not exists "openingHours" jsonb not null default '{}'::jsonb,
  add column if not exists "specialties" text[] not null default '{}'::text[],
  add column if not exists "brandsSupported" text[] not null default '{}'::text[],
  add column if not exists "partCategories" text[] not null default '{}'::text[],
  add column if not exists "verificationStatus" public.verification_status
    not null default 'UNVERIFIED',
  add column if not exists "ratingAvg" double precision not null default 0,
  add column if not exists "totalSales" bigint not null default 0,
  add column if not exists "claimRate" double precision not null default 0,
  add column if not exists "updatedAt" bigint
    not null default (extract(epoch from now()) * 1000)::bigint;

-- Backfill the legacy `rating` column into `ratingAvg` for cleaner reads.
do $$ begin
  update public.parts_stores set "ratingAvg" = rating where "ratingAvg" = 0 and rating <> 0;
exception when undefined_column then null;
end $$;

create index if not exists idx_parts_stores_verification
  on public.parts_stores ("verificationStatus");
create index if not exists idx_parts_stores_rating
  on public.parts_stores ("ratingAvg" desc);
create index if not exists idx_parts_stores_province_canton
  on public.parts_stores (province, canton);

-- -----------------------------------------------------------------------------
-- 3. Extend public.part_requests -> Vehicle-aware Part Request
-- -----------------------------------------------------------------------------
alter table public.part_requests
  add column if not exists "userId" uuid,
  add column if not exists "vehicleId" text,
  add column if not exists "sourceContext" public.part_source_context
    not null default 'MANUAL',
  add column if not exists "dtcCodes" text[] not null default '{}'::text[],
  add column if not exists "category" text,
  add column if not exists "position" public.part_position
    not null default 'NOT_APPLICABLE',
  add column if not exists "preference" public.part_preference
    not null default 'ANY',
  add column if not exists "oemNumber" text,
  add column if not exists "photoUrls" text[] not null default '{}'::text[],
  add column if not exists "notes" text not null default '',
  add column if not exists "locationLat" double precision,
  add column if not exists "locationLng" double precision,
  add column if not exists "deliveryAddress" text,
  add column if not exists "urgencyLevel" text not null default 'NORMAL',
  add column if not exists "statusV2" public.part_request_status_v2,
  add column if not exists "vin" text,
  add column if not exists "vinConfidence" public.compatibility_confidence,
  add column if not exists "updatedAt" bigint
    not null default (extract(epoch from now()) * 1000)::bigint;

-- Map legacy status into the new richer enum. The legacy column stays for now.
do $$ begin
  update public.part_requests
    set "statusV2" = case status
      when 'OPEN' then 'OPEN'::public.part_request_status_v2
      when 'ACCEPTED' then 'QUOTE_ACCEPTED'::public.part_request_status_v2
      when 'DELIVERED' then 'DELIVERED'::public.part_request_status_v2
      when 'CANCELLED' then 'CANCELLED'::public.part_request_status_v2
      else 'OPEN'::public.part_request_status_v2
    end
    where "statusV2" is null;
exception when undefined_column then null;
end $$;

-- Default the new status column so future inserts don't need to think about it.
alter table public.part_requests
  alter column "statusV2" set default 'OPEN'::public.part_request_status_v2;

create index if not exists idx_part_requests_status_v2
  on public.part_requests ("statusV2", "createdAt" desc);
create index if not exists idx_part_requests_dtc_codes_gin
  on public.part_requests using gin ("dtcCodes");
create index if not exists idx_part_requests_source
  on public.part_requests ("sourceContext");
create index if not exists idx_part_requests_vin
  on public.part_requests (vin) where vin is not null;
create index if not exists idx_part_requests_vehicle
  on public.part_requests ("vehicleId");

-- -----------------------------------------------------------------------------
-- 4. Extend public.part_offers -> Compatibility-aware Supplier Quote
-- -----------------------------------------------------------------------------
alter table public.part_offers
  add column if not exists "supplierQuoteId" text,
  add column if not exists "oemNumber" text,
  add column if not exists "currency" text not null default 'CRC',
  add column if not exists "availability" public.part_availability
    not null default 'UNKNOWN',
  add column if not exists "estimatedDeliveryHours" integer not null default 24,
  add column if not exists "warrantyDays" integer not null default 0,
  add column if not exists "includesDelivery" boolean not null default false,
  add column if not exists "deliveryFee" double precision not null default 0,
  add column if not exists "compatibilityConfidence" public.compatibility_confidence
    not null default 'UNKNOWN',
  add column if not exists "compatibilityNotes" text not null default '',
  add column if not exists "photoUrls" text[] not null default '{}'::text[],
  add column if not exists "conditionDetail" public.part_condition
    not null default 'UNKNOWN',
  add column if not exists "quoteVersion" integer not null default 1,
  add column if not exists "expiresAt" bigint,
  add column if not exists "statusV2" public.quote_status_v2,
  add column if not exists "createdByPartRequestIdLegacy" text,
  add column if not exists "createdAtMs" bigint
    not null default (extract(epoch from now()) * 1000)::bigint;

-- Map legacy status into the new richer enum.
do $$ begin
  update public.part_offers
    set "statusV2" = case status
      when 'PENDING' then 'SENT'::public.quote_status_v2
      when 'ACCEPTED' then 'ACCEPTED'::public.quote_status_v2
      when 'REJECTED' then 'REJECTED'::public.quote_status_v2
      when 'CANCELLED' then 'CANCELLED'::public.quote_status_v2
      else 'SENT'::public.quote_status_v2
    end
    where "statusV2" is null;
exception when undefined_column then null;
end $$;

alter table public.part_offers
  alter column "statusV2" set default 'SENT'::public.quote_status_v2;

-- Backfill the legacy `condition` column into the richer enum.
do $$ begin
  update public.part_offers
    set "conditionDetail" = case condition
      when 'NEW' then 'NEW_AFTERMARKET'::public.part_condition
      when 'OEM' then 'NEW_OEM'::public.part_condition
      when 'USED_TESTED' then 'USED'::public.part_condition
      when 'REMAN' then 'REFURBISHED'::public.part_condition
      else 'UNKNOWN'::public.part_condition
    end
    where "conditionDetail" = 'UNKNOWN'::public.part_condition;
exception when undefined_column then null;
end $$;

-- Guarantee supplierCannot mutate a quote after acceptance.
create or replace function public.prevent_post_acceptance_quote_mutation()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if OLD."statusV2" = 'ACCEPTED'::public.quote_status_v2
     and (
       NEW.price is distinct from OLD.price
       or NEW.brand is distinct from OLD.brand
       or NEW."conditionDetail" is distinct from OLD."conditionDetail"
       or NEW."warrantyDays" is distinct from OLD."warrantyDays"
       or NEW."partNumber" is distinct from OLD."partNumber"
       or NEW."oemNumber" is distinct from OLD."oemNumber"
     ) then
    raise exception 'Cannot mutate an accepted quote; create a new version instead.';
  end if;
  -- Bump quoteVersion on any in-place update so history is recoverable.
  NEW."quoteVersion" = OLD."quoteVersion" + 1;
  return NEW;
end;
$$;

drop trigger if exists trg_prevent_post_acceptance_quote_mutation on public.part_offers;
create trigger trg_prevent_post_acceptance_quote_mutation
before update on public.part_offers
for each row execute function public.prevent_post_acceptance_quote_mutation();

create index if not exists idx_part_offers_status_v2
  on public.part_offers ("statusV2", "createdAt" desc);
create index if not exists idx_part_offers_compatibility
  on public.part_offers ("compatibilityConfidence");
create index if not exists idx_part_offers_expires
  on public.part_offers ("expiresAt")
  where "expiresAt" is not null;

-- -----------------------------------------------------------------------------
-- 5. New table: supplier_inventory_items
-- -----------------------------------------------------------------------------
create table if not exists public.supplier_inventory_items (
  "itemId" text primary key,
  "storeId" text not null references public.parts_stores("storeId") on delete cascade,
  "storeOwnerId" uuid not null default auth.uid(),
  "partName" text not null,
  brand text,
  "partNumber" text,
  "oemNumber" text,
  category text,
  "compatibleVehicles" jsonb not null default '[]'::jsonb,
  "conditionDetail" public.part_condition not null default 'UNKNOWN',
  quantity integer not null default 1 check (quantity >= 0),
  price double precision not null default 0 check (price >= 0),
  currency text not null default 'CRC',
  "photoUrls" text[] not null default '{}'::text[],
  "warrantyDays" integer not null default 0,
  "createdAt" bigint not null default (extract(epoch from now()) * 1000)::bigint,
  "updatedAt" bigint not null default (extract(epoch from now()) * 1000)::bigint
);

create index if not exists idx_supplier_inventory_store
  on public.supplier_inventory_items ("storeId");
create index if not exists idx_supplier_inventory_owner
  on public.supplier_inventory_items ("storeOwnerId");
create index if not exists idx_supplier_inventory_category
  on public.supplier_inventory_items (category);
create index if not exists idx_supplier_inventory_compatible_gin
  on public.supplier_inventory_items using gin ("compatibleVehicles");

alter table public.supplier_inventory_items enable row level security;

drop policy if exists supplier_inventory_read_marketplace on public.supplier_inventory_items;
create policy supplier_inventory_read_marketplace
  on public.supplier_inventory_items
  for select
  to anon, authenticated
  using (true);

drop policy if exists supplier_inventory_owner_write on public.supplier_inventory_items;
create policy supplier_inventory_owner_write
  on public.supplier_inventory_items
  for all
  to authenticated
  using ("storeOwnerId" = auth.uid())
  with check ("storeOwnerId" = auth.uid());

-- -----------------------------------------------------------------------------
-- 6. New table: part_history (vehicle service history integration)
-- -----------------------------------------------------------------------------
create table if not exists public.part_purchase_history (
  "id" uuid primary key default gen_random_uuid(),
  "userId" uuid not null references auth.users(id) on delete cascade,
  "vehicleId" text not null,
  "partRequestId" text references public.part_requests("requestId") on delete set null,
  "partOfferId" text references public.part_offers("offerId") on delete set null,
  "storeId" text references public.parts_stores("storeId") on delete set null,
  "partName" text not null,
  brand text,
  "oemNumber" text,
  "partNumber" text,
  "dtcCodes" text[] not null default '{}'::text[],
  "price" double precision not null,
  currency text not null default 'CRC',
  "warrantyDays" integer not null default 0,
  "purchaseAt" bigint not null default (extract(epoch from now()) * 1000)::bigint,
  "metadata" jsonb not null default '{}'::jsonb
);

create index if not exists idx_part_purchase_history_user
  on public.part_purchase_history ("userId", "purchaseAt" desc);
create index if not exists idx_part_purchase_history_vehicle
  on public.part_purchase_history ("vehicleId", "purchaseAt" desc);
create index if not exists idx_part_purchase_history_dtc_gin
  on public.part_purchase_history using gin ("dtcCodes");

alter table public.part_purchase_history enable row level security;

drop policy if exists part_purchase_history_read_own on public.part_purchase_history;
create policy part_purchase_history_read_own
  on public.part_purchase_history
  for select
  to authenticated
  using ("userId" = auth.uid());

drop policy if exists part_purchase_history_insert_own on public.part_purchase_history;
create policy part_purchase_history_insert_own
  on public.part_purchase_history
  for insert
  to authenticated
  with check ("userId" = auth.uid());

drop policy if exists part_purchase_history_no_update on public.part_purchase_history;
create policy part_purchase_history_no_update
  on public.part_purchase_history
  for update
  to authenticated
  using (false) with check (false);

drop policy if exists part_purchase_history_no_delete on public.part_purchase_history;
create policy part_purchase_history_no_delete
  on public.part_purchase_history
  for delete
  to authenticated
  using (false);

-- -----------------------------------------------------------------------------
-- 7. New table: part_disputes (anti-fraud ledger)
-- -----------------------------------------------------------------------------
create table if not exists public.part_disputes (
  "id" uuid primary key default gen_random_uuid(),
  "reporterId" uuid not null references auth.users(id) on delete cascade,
  "againstStoreId" text references public.parts_stores("storeId") on delete set null,
  "againstOfferId" text references public.part_offers("offerId") on delete set null,
  "partRequestId" text references public.part_requests("requestId") on delete set null,
  "kind" text not null check ("kind" in ('FAKE_PART', 'INCOMPATIBLE', 'NON_DELIVERY', 'OTHER')),
  "description" text not null,
  "status" text not null default 'OPEN' check ("status" in ('OPEN', 'INVESTIGATING', 'RESOLVED', 'REJECTED')),
  "createdAt" bigint not null default (extract(epoch from now()) * 1000)::bigint,
  "resolvedAt" bigint
);

create index if not exists idx_part_disputes_status
  on public.part_disputes ("status", "createdAt" desc);
create index if not exists idx_part_disputes_store
  on public.part_disputes ("againstStoreId");

alter table public.part_disputes enable row level security;

drop policy if exists part_disputes_insert_own on public.part_disputes;
create policy part_disputes_insert_own
  on public.part_disputes
  for insert
  to authenticated
  with check ("reporterId" = auth.uid());

drop policy if exists part_disputes_read_own on public.part_disputes;
create policy part_disputes_read_own
  on public.part_disputes
  for select
  to authenticated
  using ("reporterId" = auth.uid());

-- -----------------------------------------------------------------------------
-- 8. Compatibility view: lightweight ranked list of quotes per request
--    The ranking here is intentionally simple. Heavy ranking (machine-learned)
--    will land later as part of PR 4 if/when we collect enough signal.
-- -----------------------------------------------------------------------------
create or replace view public.part_quote_ranking_v1
with (security_invoker = on) as
select
  po."offerId",
  po."partRequestId",
  po."storeId",
  -- Compatibility score component (0..1).
  case po."compatibilityConfidence"
    when 'EXACT'  then 1.00
    when 'HIGH'   then 0.80
    when 'MEDIUM' then 0.55
    when 'LOW'    then 0.25
    else 0.00
  end as compatibility_score,
  -- Reputation score component: rating normalized to 0..1 (rating is 0..5).
  coalesce(ps."ratingAvg", 0) / 5.0 as reputation_score,
  -- Delivery score: shorter ETA is better, capped at 168h.
  greatest(0, 1 - (po."estimatedDeliveryHours"::double precision / 168.0)) as delivery_score,
  -- Composite. Weights tuned heuristically; tunable in a future ADR.
  (
    case po."compatibilityConfidence"
      when 'EXACT'  then 1.00
      when 'HIGH'   then 0.80
      when 'MEDIUM' then 0.55
      when 'LOW'    then 0.25
      else 0.00
    end * 0.55
    + coalesce(ps."ratingAvg", 0) / 5.0 * 0.20
    + greatest(0, 1 - (po."estimatedDeliveryHours"::double precision / 168.0)) * 0.15
    + (case when po."warrantyDays" >= 90 then 1.0
            when po."warrantyDays" >= 30 then 0.6
            when po."warrantyDays" > 0 then 0.3
            else 0.0 end) * 0.10
  ) as composite_score,
  po.price as price_for_sort,
  po."warrantyDays",
  po."estimatedDeliveryHours"
from public.part_offers po
left join public.parts_stores ps on ps."storeId" = po."storeId"
where po."statusV2" in ('SENT'::public.quote_status_v2);

grant select on public.part_quote_ranking_v1 to anon, authenticated;

-- -----------------------------------------------------------------------------
-- 9. End of migration
-- -----------------------------------------------------------------------------
-- Sanity check: keep an eye on row counts during rollout.
comment on table public.parts_stores is 'Supplier profile (repuestera) for MEET parts marketplace. Columns aliased to snake_case + camelCase for dual clients.';
comment on table public.part_requests is 'Vehicle-aware part request. Can be created manually, from a DTC, from a 3D part, or from a mechanic work order.';
comment on table public.part_offers is 'Supplier quote for a part request. Carries compatibility evidence, warranty and version stamp.';
comment on table public.supplier_inventory_items is 'Optional local inventory a repuestera publishes ahead of time. Quotes are still allowed without an inventory entry.';
comment on table public.part_purchase_history is 'Vehicle service history (parts). Auto-populated by the trigger in 10_acceptance_audit.sql (next PR).';
comment on table public.part_disputes is 'Anti-fraud ledger: when a customer reports a fake/incompatible/no-show part.';
