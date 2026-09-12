-- Complete the manual SINPE review loop with an auditable, idempotent receipt.
-- Only the platform owner at AAL2 can mint a confirmed top-up ledger credit.

create or replace function public.ride_owner_decide_wallet_topup_v1(
  p_topup_id uuid,
  p_decision text,
  p_reason text
) returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_topup public.ride_wallet_topups%rowtype;
  v_owner uuid := (select auth.uid());
  v_ledger_entry_id uuid;
  v_posted bigint := 0;
  v_reserved bigint := 0;
  v_was_pending boolean;
begin
  if not public.meet_session_has_aal2() or not public.meet_is_platform_owner() then
    raise exception using errcode='42501', message='OWNER_AAL2_REQUIRED';
  end if;
  if p_decision not in ('APPROVED','REJECTED') then
    raise exception using errcode='22023', message='INVALID_DECISION';
  end if;
  if length(trim(coalesce(p_reason, ''))) < 8 then
    raise exception using errcode='22023', message='DECISION_REASON_REQUIRED';
  end if;

  select * into strict v_topup
    from public.ride_wallet_topups
   where id = p_topup_id
   for update;
  v_was_pending := v_topup.status = 'PENDING_REVIEW';

  if v_was_pending then
    update public.ride_wallet_topups
       set status = p_decision,
           decision_reason = trim(p_reason),
           reviewed_at = now(),
           reviewed_by = v_owner
     where id = p_topup_id;
    v_topup.status := p_decision;
  elsif v_topup.status <> p_decision then
    raise exception using errcode='P0001', message='TOPUP_ALREADY_DECIDED';
  end if;

  if v_topup.status = 'APPROVED' then
    insert into public.ride_wallets(driver_id, currency)
    values(v_topup.driver_id, v_topup.currency)
    on conflict do nothing;

    insert into public.ride_wallet_ledger(
      driver_id, idempotency_key, entry_type, amount_minor,
      currency, direction, withdrawable, metadata
    ) values(
      v_topup.driver_id,
      'topup-credit:' || v_topup.id,
      'TOP_UP_CONFIRMED',
      v_topup.amount_minor,
      v_topup.currency,
      'CREDIT',
      false,
      jsonb_build_object(
        'topup_id', v_topup.id,
        'reviewed_by', coalesce(v_topup.reviewed_by, v_owner),
        'proof_sha256', v_topup.proof_sha256
      )
    ) on conflict (idempotency_key) do nothing
    returning id into v_ledger_entry_id;

    if v_ledger_entry_id is null then
      select id into v_ledger_entry_id
        from public.ride_wallet_ledger
       where idempotency_key = 'topup-credit:' || v_topup.id;
    end if;
  end if;

  select coalesce(sum(case
      when l.direction = 'CREDIT' then l.amount_minor
      when l.direction = 'DEBIT' and l.entry_type <> 'COMMISSION_RESERVED' then -l.amount_minor
      else 0 end), 0)
    into v_posted
    from public.ride_wallet_ledger l
   where l.driver_id = v_topup.driver_id
     and l.currency = v_topup.currency;

  select coalesce(sum(r.amount_minor), 0)
    into v_reserved
    from public.ride_commission_reservations r
   where r.driver_id = v_topup.driver_id
     and r.currency = v_topup.currency
     and r.state = 'RESERVED';

  return jsonb_build_object(
    'id', v_topup.id,
    'status', v_topup.status,
    'amount_minor', v_topup.amount_minor,
    'currency', v_topup.currency,
    'ledger_entry_id', v_ledger_entry_id,
    'credited_minor', case when v_topup.status = 'APPROVED' then v_topup.amount_minor else 0 end,
    'available_minor', greatest(0, v_posted - v_reserved),
    'idempotent', not v_was_pending
  );
exception
  when no_data_found then
    raise exception using errcode='P0002', message='TOPUP_NOT_FOUND';
end;
$$;

revoke all on function public.ride_owner_decide_wallet_topup_v1(uuid,text,text) from public;
grant execute on function public.ride_owner_decide_wallet_topup_v1(uuid,text,text) to authenticated;

comment on function public.ride_owner_decide_wallet_topup_v1(uuid,text,text) is
  'AAL2 owner-only manual top-up decision. Approval creates exactly one immutable ledger credit and returns the resulting balance.';
