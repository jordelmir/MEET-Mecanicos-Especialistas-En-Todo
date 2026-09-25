-- ══════════════════════════════════════════════════════════════════════
-- MEET COMMERCE & TRIANGULAR DELIVERY ECOSYSTEM (PULPERIAS & SODAS)
-- Date: 2026-09-25
-- Enables:
-- 1. Neighborhood Pulperías & Sodas to sell locally.
-- 2. Clients to order with exact CRC prices & transparent breakdown.
-- 3. MEET Drivers/Couriers to transport orders with live GPS & 4-digit PIN.
-- 4. Dual Escrow Release & SHA-256 Forensic Integrity Verification.
-- ══════════════════════════════════════════════════════════════════════

-- 1. STORES TABLE
create table if not exists public.commerce_stores (
  store_id uuid primary key default gen_random_uuid(),
  store_type text not null check (store_type in ('PULPERIA', 'SODA_RESTAURANT', 'MINISUPER')),
  name text not null,
  owner_id uuid references auth.users(id) on delete set null,
  phone text not null default '',
  address text not null,
  latitude double precision not null default 9.9333,
  longitude double precision not null default -84.0833,
  rating numeric(3,2) not null default 5.0,
  is_active boolean not null default true,
  catalog_json jsonb not null default '[]'::jsonb,
  created_at timestamptz not null default now()
);

create index if not exists idx_commerce_stores_type on public.commerce_stores(store_type);
create index if not exists idx_commerce_stores_active on public.commerce_stores(is_active);

-- 2. ORDERS TABLE
create table if not exists public.commerce_orders (
  order_id uuid primary key default gen_random_uuid(),
  commerce_type text not null check (commerce_type in ('PULPERIA', 'SODA_RESTAURANT')),
  store_id uuid references public.commerce_stores(store_id) on delete restrict,
  customer_id uuid references auth.users(id) on delete set null,
  customer_name text not null,
  customer_phone text not null,
  delivery_address text not null,
  delivery_latitude double precision not null default 9.9333,
  delivery_longitude double precision not null default -84.0833,
  items_json jsonb not null default '[]'::jsonb,
  items_subtotal_minor bigint not null default 0,
  delivery_fee_minor bigint not null default 0,
  total_amount_minor bigint not null default 0,
  currency text not null default 'CRC',
  payment_method text not null check (payment_method in ('SINPE', 'CASH', 'CARD')),
  payment_status text not null check (payment_status in ('PENDING', 'ESCROW_HELD', 'RELEASED', 'REFUNDED')) default 'PENDING',
  courier_id uuid references auth.users(id) on delete set null,
  courier_name text,
  courier_phone text,
  courier_vehicle text,
  delivery_pin text not null,
  status text not null check (status in (
    'PLACED', 'CONFIRMED', 'PREPARING', 'READY_FOR_PICKUP',
    'COURIER_ASSIGNED', 'IN_TRANSIT', 'ARRIVED', 'DELIVERED', 'CANCELLED'
  )) default 'PLACED',
  created_at timestamptz not null default now(),
  ready_at timestamptz,
  picked_up_at timestamptz,
  delivered_at timestamptz,
  completed_at timestamptz,
  integrity_hash text,
  rating_stars integer check (rating_stars between 1 and 5),
  review_notes text
);

create index if not exists idx_commerce_orders_status on public.commerce_orders(status);
create index if not exists idx_commerce_orders_customer on public.commerce_orders(customer_id);
create index if not exists idx_commerce_orders_store on public.commerce_orders(store_id);
create index if not exists idx_commerce_orders_courier on public.commerce_orders(courier_id);

-- 3. AUDIT & EVENT TIMELINE
create table if not exists public.commerce_order_events (
  event_id uuid primary key default gen_random_uuid(),
  order_id uuid not null references public.commerce_orders(order_id) on delete cascade,
  actor_id uuid references auth.users(id) on delete set null,
  actor_role text not null check (actor_role in ('CUSTOMER', 'MERCHANT', 'COURIER', 'SYSTEM')),
  event_type text not null,
  payload jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create index if not exists idx_commerce_events_order on public.commerce_order_events(order_id);

-- 4. RLS POLICIES
alter table public.commerce_stores enable row level security;
alter table public.commerce_orders enable row level security;
alter table public.commerce_order_events enable row level security;

-- Stores: Anyone authenticated can read active stores
create policy "Allow read active stores"
  on public.commerce_stores for select
  using (is_active = true);

-- Orders: Customer can read their orders
create policy "Allow customer read orders"
  on public.commerce_orders for select
  using (auth.uid() = customer_id or auth.uid() = courier_id or exists (
    select 1 from public.commerce_stores s where s.store_id = commerce_orders.store_id and s.owner_id = auth.uid()
  ));

-- Orders: Couriers can see ready for pickup orders
create policy "Allow courier view available missions"
  on public.commerce_orders for select
  using (status in ('READY_FOR_PICKUP', 'COURIER_ASSIGNED', 'IN_TRANSIT', 'ARRIVED'));

-- Events: Participants can read events
create policy "Allow order participants read events"
  on public.commerce_order_events for select
  using (exists (
    select 1 from public.commerce_orders o
    where o.order_id = commerce_order_events.order_id
      and (o.customer_id = auth.uid() or o.courier_id = auth.uid() or exists (
        select 1 from public.commerce_stores s where s.store_id = o.store_id and s.owner_id = auth.uid()
      ))
  ));

-- 5. AUTHORITATIVE RPCS

-- A) Place Order
create or replace function public.place_commerce_order_v1(
  p_commerce_type text,
  p_store_id uuid,
  p_customer_name text,
  p_customer_phone text,
  p_delivery_address text,
  p_delivery_lat double precision,
  p_delivery_lng double precision,
  p_items_json jsonb,
  p_items_subtotal_minor bigint,
  p_delivery_fee_minor bigint,
  p_payment_method text
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  v_actor uuid := auth.uid();
  v_order_id uuid := gen_random_uuid();
  v_pin text := lpad(floor(random() * 9000 + 1000)::text, 4, '0');
  v_total bigint := p_items_subtotal_minor + p_delivery_fee_minor;
  v_result jsonb;
begin
  if p_items_subtotal_minor <= 0 or p_delivery_fee_minor < 0 then
    raise exception 'INVALID_AMOUNTS';
  end if;

  insert into public.commerce_orders (
    order_id, commerce_type, store_id, customer_id, customer_name, customer_phone,
    delivery_address, delivery_latitude, delivery_longitude, items_json,
    items_subtotal_minor, delivery_fee_minor, total_amount_minor, currency,
    payment_method, payment_status, delivery_pin, status
  ) values (
    v_order_id, p_commerce_type, p_store_id, v_actor, p_customer_name, p_customer_phone,
    p_delivery_address, p_delivery_lat, p_delivery_lng, p_items_json,
    p_items_subtotal_minor, p_delivery_fee_minor, v_total, 'CRC',
    p_payment_method, case when p_payment_method = 'SINPE' then 'ESCROW_HELD' else 'PENDING' end,
    v_pin, 'PLACED'
  );

  insert into public.commerce_order_events (order_id, actor_id, actor_role, event_type, payload)
  values (v_order_id, v_actor, 'CUSTOMER', 'ORDER_PLACED', jsonb_build_object(
    'total_amount_minor', v_total,
    'payment_method', p_payment_method
  ));

  v_result := jsonb_build_object(
    'order_id', v_order_id,
    'status', 'PLACED',
    'delivery_pin', v_pin,
    'total_amount_minor', v_total
  );

  return v_result;
end $$;

-- B) Merchant Status Progression
create or replace function public.advance_merchant_order_v1(
  p_order_id uuid,
  p_new_status text
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  v_actor uuid := auth.uid();
  v_order public.commerce_orders%rowtype;
begin
  select * into v_order from public.commerce_orders where order_id = p_order_id;
  if not found then raise exception 'ORDER_NOT_FOUND'; end if;

  if p_new_status not in ('CONFIRMED', 'PREPARING', 'READY_FOR_PICKUP') then
    raise exception 'INVALID_MERCHANT_STATUS';
  end if;

  update public.commerce_orders
  set status = p_new_status,
      ready_at = case when p_new_status = 'READY_FOR_PICKUP' then now() else ready_at end
  where order_id = p_order_id;

  insert into public.commerce_order_events (order_id, actor_id, actor_role, event_type, payload)
  values (p_order_id, v_actor, 'MERCHANT', 'STATUS_UPDATED', jsonb_build_object('new_status', p_new_status));

  return jsonb_build_object('order_id', p_order_id, 'status', p_new_status);
end $$;

-- C) Courier Mission Assignment
create or replace function public.assign_courier_mission_v1(
  p_order_id uuid,
  p_courier_name text,
  p_courier_phone text,
  p_courier_vehicle text
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  v_actor uuid := auth.uid();
  v_order public.commerce_orders%rowtype;
begin
  select * into v_order from public.commerce_orders where order_id = p_order_id;
  if not found then raise exception 'ORDER_NOT_FOUND'; end if;
  if v_order.status not in ('READY_FOR_PICKUP', 'PREPARING') then
    raise exception 'ORDER_NOT_READY_FOR_DISPATCH';
  end if;

  update public.commerce_orders
  set status = 'COURIER_ASSIGNED',
      courier_id = v_actor,
      courier_name = p_courier_name,
      courier_phone = p_courier_phone,
      courier_vehicle = p_courier_vehicle
  where order_id = p_order_id;

  insert into public.commerce_order_events (order_id, actor_id, actor_role, event_type, payload)
  values (p_order_id, v_actor, 'COURIER', 'COURIER_ASSIGNED', jsonb_build_object(
    'courier_name', p_courier_name,
    'courier_vehicle', p_courier_vehicle
  ));

  return jsonb_build_object('order_id', p_order_id, 'status', 'COURIER_ASSIGNED');
end $$;

-- D) Courier Transit Advancement
create or replace function public.advance_courier_delivery_v1(
  p_order_id uuid,
  p_new_status text
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  v_actor uuid := auth.uid();
begin
  if p_new_status not in ('IN_TRANSIT', 'ARRIVED') then
    raise exception 'INVALID_COURIER_STATUS';
  end if;

  update public.commerce_orders
  set status = p_new_status,
      picked_up_at = case when p_new_status = 'IN_TRANSIT' and picked_up_at is null then now() else picked_up_at end
  where order_id = p_order_id and courier_id = v_actor;

  if not found then raise exception 'UNAUTHORIZED_OR_NOT_FOUND'; end if;

  insert into public.commerce_order_events (order_id, actor_id, actor_role, event_type, payload)
  values (p_order_id, v_actor, 'COURIER', 'TRANSIT_STATUS_UPDATED', jsonb_build_object('new_status', p_new_status));

  return jsonb_build_object('order_id', p_order_id, 'status', p_new_status);
end $$;

-- E) PIN Verification & Escrow Release
create or replace function public.verify_delivery_pin_and_complete_v1(
  p_order_id uuid,
  p_entered_pin text,
  p_rating integer default 5,
  p_review text default ''
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  v_actor uuid := auth.uid();
  v_order public.commerce_orders%rowtype;
  v_hash text;
  v_now timestamptz := now();
begin
  select * into v_order from public.commerce_orders where order_id = p_order_id;
  if not found then raise exception 'ORDER_NOT_FOUND'; end if;
  if v_order.status = 'DELIVERED' then raise exception 'ALREADY_DELIVERED'; end if;
  if v_order.delivery_pin != trim(p_entered_pin) then
    raise exception 'INVALID_DELIVERY_PIN';
  end if;

  -- Generate Forensic SHA-256 Integrity Hash
  v_hash := encode(digest(
    p_order_id::text || '|' || v_order.total_amount_minor::text || '|' ||
    v_order.customer_id::text || '|' || coalesce(v_order.courier_id::text, '') || '|' ||
    extract(epoch from v_now)::text || '|PIN_VERIFIED_ESCROW_RELEASED',
    'sha256'
  ), 'hex');

  update public.commerce_orders
  set status = 'DELIVERED',
      payment_status = 'RELEASED',
      delivered_at = v_now,
      completed_at = v_now,
      integrity_hash = v_hash,
      rating_stars = p_rating,
      review_notes = p_review
  where order_id = p_order_id;

  insert into public.commerce_order_events (order_id, actor_id, actor_role, event_type, payload)
  values (p_order_id, v_actor, 'COURIER', 'DELIVERY_PIN_VERIFIED', jsonb_build_object(
    'integrity_hash', v_hash,
    'rating', p_rating,
    'completed_at', v_now
  ));

  return jsonb_build_object(
    'order_id', p_order_id,
    'status', 'DELIVERED',
    'integrity_hash', v_hash,
    'escrow_status', 'RELEASED'
  );
end $$;

-- F) Safe Cancel
create or replace function public.cancel_commerce_order_v1(
  p_order_id uuid,
  p_reason text
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  v_actor uuid := auth.uid();
  v_order public.commerce_orders%rowtype;
begin
  select * into v_order from public.commerce_orders where order_id = p_order_id;
  if not found then raise exception 'ORDER_NOT_FOUND'; end if;
  if v_order.status in ('DELIVERED', 'CANCELLED') then
    raise exception 'ORDER_IN_TERMINAL_STATE';
  end if;

  update public.commerce_orders
  set status = 'CANCELLED',
      payment_status = 'REFUNDED'
  where order_id = p_order_id;

  insert into public.commerce_order_events (order_id, actor_id, actor_role, event_type, payload)
  values (p_order_id, v_actor, 'SYSTEM', 'ORDER_CANCELLED', jsonb_build_object('reason', p_reason));

  return jsonb_build_object('order_id', p_order_id, 'status', 'CANCELLED', 'payment_status', 'REFUNDED');
end $$;

-- 6. SEED POPULAR COSTA RICAN PULPERIAS & SODAS
insert into public.commerce_stores (store_type, name, phone, address, latitude, longitude, rating, catalog_json)
values
  ('PULPERIA', 'Pulpería El Sol (Abarrotes & Panadería)', '+506 2225-1020', 'San Pedro de Montes de Oca, 200m Este de la Iglesia', 9.9325, -84.0512, 4.95,
   '[{"name":"Pan Baguette Fresco","price":450,"unit":"barra"},{"name":"Leche Dos Pinos 1L Semidescremada","price":950,"unit":"caja"},{"name":"Huevos de Granja (Cartón 15u)","price":1850,"unit":"carton"},{"name":"Arroz Tío Pelón 99% (1.8kg)","price":1950,"unit":"bolsa"},{"name":"Frijoles Negros Don Pedro (800g)","price":1350,"unit":"bolsa"},{"name":"Refresco Coca-Cola 600ml fría","price":1000,"unit":"botella"}]'::jsonb),

  ('PULPERIA', 'Pulpería La Amistad (Canasta Básica & Lácteos)', '+506 2271-4488', 'Curridabat Centro, frente al Parque', 9.9142, -84.0321, 4.90,
   '[{"name":"Natilla Dos Pinos 220g","price":750,"unit":"bolsa"},{"name":"Queso Turrialba 500g","price":2400,"unit":"bloque"},{"name":"Bolsa de Pan Bollo (8u)","price":800,"unit":"paquete"},{"name":"Café Rey 250g molido","price":1650,"unit":"bolsa"},{"name":"Azúcar Doña María 1kg","price":920,"unit":"bolsa"},{"name":"Galletas Chiky Chocolate","price":400,"unit":"paquete"}]'::jsonb),

  ('SODA_RESTAURANT', 'Soda Criolla Doña María (Casados & Desayunos)', '+506 2283-9090', 'Barrio Dent, San José', 9.9360, -84.0620, 4.98,
   '[{"name":"Casado con Carne Mechada (Arroz, frijoles, maduro, ensalada, picadillo)","price":3800,"unit":"plato"},{"name":"Casado con Chuleta Frita Encebollada","price":3900,"unit":"plato"},{"name":"Casado con Pechuga a la Plancha","price":3600,"unit":"plato"},{"name":"Gallo Pinto con Huevos, Queso Frito y Natilla","price":2800,"unit":"desayuno"},{"name":"Empanada Arreglada de Carne o Chicharrón","price":1600,"unit":"unidad"},{"name":"Batido Natural en Leche (Mora, Guanábana o Fresa)","price":1400,"unit":"vaso"}]'::jsonb),

  ('SODA_RESTAURANT', 'Soda El Parque (Comida Casera & Olla de Carne)', '+506 2253-1234', 'Zapote, 100m Sur de la Rotonda', 9.9215, -84.0545, 4.88,
   '[{"name":"Olla de Carne Criolla Especial con Verduras","price":4200,"unit":"tazón grande"},{"name":"Casado con Bistec Encebollado","price":3800,"unit":"plato"},{"name":"Arroz con Pollo Criollo con Papas y Ensalada","price":3500,"unit":"plato"},{"name":"Chifrijo Auténtico Especial","price":3500,"unit":"tazón"},{"name":"Gallo Pinto Especial con Carne en Salsa","price":3200,"unit":"desayuno"},{"name":"Agua Dulce Caliente con Leche","price":900,"unit":"taza"}]'::jsonb)
on conflict do nothing;
