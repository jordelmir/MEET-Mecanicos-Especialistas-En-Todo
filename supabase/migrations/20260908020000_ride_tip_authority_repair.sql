-- A tip records a voluntary instruction, not a provider-confirmed payment.
-- Lock the canonical ride so concurrent retries cannot insert a second tip.
create or replace function public.ride_submit_tip_v1(
  p_ride_id text, p_tip_minor bigint, p_currency text default 'CRC'
) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
  v_actor uuid := auth.uid();
  v_ride public.ride_requests%rowtype;
  v_tip public.ride_tips%rowtype;
begin
  if v_actor is null then
    raise exception using errcode='42501', message='UNAUTHENTICATED';
  end if;
  if p_tip_minor is null or p_tip_minor <= 0 or p_tip_minor > 100000 then
    raise exception using errcode='22023', message='INVALID_TIP_AMOUNT';
  end if;
  select * into v_ride from public.ride_requests
    where id = p_ride_id::uuid for update;
  if not found then
    raise exception using errcode='P0002', message='RIDE_NOT_FOUND';
  end if;
  if v_ride.passenger_id is distinct from v_actor then
    raise exception using errcode='42501', message='NOT_PASSENGER';
  end if;
  if v_ride.state <> 'COMPLETED' or v_ride.assigned_driver_id is null then
    raise exception using errcode='22023', message='RIDE_NOT_COMPLETED';
  end if;
  if p_currency is null or p_currency not in ('CRC', 'USD') or p_currency <> v_ride.currency then
    raise exception using errcode='22023', message='CURRENCY_MISMATCH';
  end if;
  select * into v_tip from public.ride_tips where ride_id = v_ride.id::text;
  if found then
    if v_tip.tip_minor <> p_tip_minor or v_tip.currency <> p_currency then
      raise exception using errcode='22023', message='TIP_ALREADY_RECORDED_WITH_DIFFERENT_AMOUNT';
    end if;
  else
    insert into public.ride_tips(ride_id, passenger_id, driver_id, tip_minor, currency)
      values (v_ride.id::text, v_actor, v_ride.assigned_driver_id, p_tip_minor, p_currency);
  end if;
  return jsonb_build_object('ok', true, 'tip_minor', p_tip_minor,
    'currency', p_currency, 'payment_status', 'PENDING');
end;
$$;
revoke all on function public.ride_submit_tip_v1(text, bigint, text) from public, anon;
grant execute on function public.ride_submit_tip_v1(text, bigint, text) to authenticated;
