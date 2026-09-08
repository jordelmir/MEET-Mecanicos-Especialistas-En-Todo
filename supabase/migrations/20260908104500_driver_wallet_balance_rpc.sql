-- Read-only wallet projection used by the driver dashboard.
-- Values are whole CRC units, matching ride_wallet_ledger and fare amounts.
create or replace function public.ride_wallet_balance_v1()
returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare
  v_driver uuid := (select auth.uid());
  v_currency text := 'CRC';
  v_posted bigint := 0;
  v_reserved bigint := 0;
begin
  if v_driver is null then
    raise exception using errcode='42501', message='UNAUTHENTICATED';
  end if;
  select coalesce(max(currency), 'CRC') into v_currency
    from public.ride_wallets where driver_id = v_driver;
  select coalesce(sum(case
    when l.direction = 'CREDIT' then l.amount_minor
    when l.direction = 'DEBIT' and l.entry_type <> 'COMMISSION_RESERVED' then -l.amount_minor
    else 0 end), 0)
    into v_posted
    from public.ride_wallet_ledger l
   where l.driver_id = v_driver and l.currency = v_currency;
  select coalesce(sum(r.amount_minor), 0)
    into v_reserved
    from public.ride_commission_reservations r
   where r.driver_id = v_driver and r.currency = v_currency and r.state = 'RESERVED';
  return jsonb_build_object(
    'currency', v_currency,
    'posted_minor', v_posted,
    'reserved_minor', v_reserved,
    'available_minor', greatest(0, v_posted - v_reserved)
  );
end; $$;
revoke all on function public.ride_wallet_balance_v1() from public;
grant execute on function public.ride_wallet_balance_v1() to authenticated;

create or replace function public.ride_driver_has_offer_balance(
  p_driver_id uuid,
  p_fare_minor bigint,
  p_currency text
)
returns boolean language sql stable security definer set search_path = '' as $$
  select greatest(0, coalesce((
    select sum(case
      when l.direction = 'CREDIT' then l.amount_minor
      when l.direction = 'DEBIT' and l.entry_type <> 'COMMISSION_RESERVED' then -l.amount_minor
      else 0 end)
    from public.ride_wallet_ledger l
    where l.driver_id = p_driver_id and l.currency = p_currency
  ), 0) - coalesce((
    select sum(r.amount_minor)
    from public.ride_commission_reservations r
    where r.driver_id = p_driver_id and r.currency = p_currency and r.state = 'RESERVED'
  ), 0)) >= round(coalesce(p_fare_minor, 0)::numeric * 500 / 10000)::bigint;
$$;

create or replace function public.ride_offer_wallet_guard()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if new.state = 'PENDING'
     and not public.ride_driver_has_offer_balance(new.driver_id, new.fare_minor, new.currency) then
    raise exception using errcode = 'P0001', message = 'INSUFFICIENT_BALANCE';
  end if;
  return new;
end; $$;

drop trigger if exists ride_offer_wallet_guard on public.ride_offers;
create trigger ride_offer_wallet_guard
before insert or update of driver_id, fare_minor, currency, state on public.ride_offers
for each row execute function public.ride_offer_wallet_guard();

revoke all on function public.ride_driver_has_offer_balance(uuid,bigint,text) from public;
grant execute on function public.ride_driver_has_offer_balance(uuid,bigint,text) to authenticated;
revoke all on function public.ride_offer_wallet_guard() from public;

-- A passenger may only see a pending offer when the driver can reserve its 5%
-- commission. Accepted/rejected history remains visible for trip continuity.
drop policy if exists ride_offers_participant_select on public.ride_offers;
create policy ride_offers_participant_select on public.ride_offers
for select to authenticated
using (
  driver_id = (select auth.uid()) or
  exists (
    select 1 from public.ride_requests r
    where r.id = ride_offers.request_id
      and r.passenger_id = (select auth.uid())
      and (
        ride_offers.state <> 'PENDING'
        or public.ride_driver_has_offer_balance(ride_offers.driver_id, ride_offers.fare_minor, ride_offers.currency)
      )
  )
);
