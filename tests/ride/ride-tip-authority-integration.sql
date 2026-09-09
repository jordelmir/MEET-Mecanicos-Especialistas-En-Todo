\set ON_ERROR_STOP on
-- The preceding passenger/driver journey completes this real canonical row.
do $test$
declare
  r public.ride_requests%rowtype;
  receipt jsonb;
  outsider uuid := '89999999-9999-9999-9999-999999999999';
begin
  select * into strict r from public.ride_requests where state = 'COMPLETED' limit 1;
  perform set_config('request.jwt.claim.sub', r.passenger_id::text, false);
  receipt := public.ride_submit_tip_v1(r.id::text, 500, r.currency);
  if receipt->>'ok' <> 'true' or receipt->>'payment_status' <> 'PENDING' then
    raise exception 'Tip must record pending payment: %', receipt;
  end if;
  perform public.ride_submit_tip_v1(upper(r.id::text), 500, r.currency);
  if (select count(*) from public.ride_tips where ride_id = r.id::text) <> 1 then
    raise exception 'Retry duplicated tip';
  end if;
  begin
    perform public.ride_submit_tip_v1(r.id::text, 600, r.currency);
    raise exception 'Conflicting retry accepted';
  exception when invalid_parameter_value then null; end;
  begin
    perform public.ride_submit_tip_v1(r.id::text, null, r.currency);
    raise exception 'Null amount accepted';
  exception when invalid_parameter_value then null; end;
  begin
    perform public.ride_submit_tip_v1(r.id::text, 500, case when r.currency='CRC' then 'USD' else 'CRC' end);
    raise exception 'Wrong currency accepted';
  exception when invalid_parameter_value then null; end;
  perform set_config('request.jwt.claim.sub', outsider::text, false);
  begin
    perform public.ride_submit_tip_v1(r.id::text, 500, r.currency);
    raise exception 'Outsider accepted';
  exception when insufficient_privilege then null; end;
  perform set_config('request.jwt.claim.sub', '', false);
  begin
    perform public.ride_submit_tip_v1(r.id::text, 500, r.currency);
    raise exception 'Anonymous tip accepted';
  exception when insufficient_privilege then null; end;
end;
$test$;
