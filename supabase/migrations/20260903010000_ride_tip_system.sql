-- Ride tip system: voluntary post-ride tip with audit trail
create table if not exists public.ride_tips (
  id uuid primary key default gen_random_uuid(),
  ride_id text not null,
  passenger_id uuid not null references auth.users(id) on delete restrict,
  driver_id uuid not null references auth.users(id) on delete restrict,
  tip_minor bigint not null check (tip_minor > 0),
  currency text not null default 'CRC',
  created_at timestamptz not null default now()
);

alter table public.ride_tips enable row level security;

create or replace function public.ride_submit_tip_v1(
  p_ride_id text,
  p_tip_minor bigint,
  p_currency text default 'CRC'
) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
  v_actor uuid := (select auth.uid());
  v_ride record;
begin
  if v_actor is null then
    raise exception using errcode='42501', message='UNAUTHENTICATED';
  end if;
  if p_tip_minor <= 0 or p_tip_minor > 100000 then
    raise exception using errcode='22023', message='INVALID_TIP_AMOUNT';
  end if;

  select id, passenger_id, assigned_driver_id, status, currency
    into v_ride
    from public.ride_requests
   where request_id = p_ride_id;

  if not found then
    raise exception using errcode='P0002', message='RIDE_NOT_FOUND';
  end if;

  if v_ride.status <> 'COMPLETED' then
    raise exception using errcode='22023', message='RIDE_NOT_COMPLETED';
  end if;

  if v_ride.passenger_id <> v_actor then
    raise exception using errcode='42501', message='NOT_PASSENGER';
  end if;

  if exists (select 1 from public.ride_tips where ride_id = p_ride_id) then
    raise exception using errcode='23505', message='TIP_ALREADY_SUBMITTED';
  end if;

  insert into public.ride_tips(ride_id, passenger_id, driver_id, tip_minor, currency)
  values (p_ride_id, v_actor, v_ride.assigned_driver_id, p_tip_minor, coalesce(p_currency, v_ride.currency));

  return jsonb_build_object('ok', true, 'tip_minor', p_tip_minor, 'currency', coalesce(p_currency, v_ride.currency));
end;
$$;

revoke all on function public.ride_submit_tip_v1(text, bigint, text) from public;
grant execute on function public.ride_submit_tip_v1(text, bigint, text) to authenticated;

-- RLS: passenger can read their own tips, driver can read tips received
create policy ride_tips_read_own on public.ride_tips
for select to authenticated
using (passenger_id = auth.uid() or driver_id = auth.uid());
