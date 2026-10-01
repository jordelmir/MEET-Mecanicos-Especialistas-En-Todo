-- All records below are isolated local fixtures. Never execute against production.
insert into auth.users values('00000000-0000-0000-0000-000000000001'),('00000000-0000-0000-0000-000000000002'),('00000000-0000-0000-0000-000000000003'),('00000000-0000-0000-0000-000000000004');
insert into user_profiles(id,auth_user_id) select id,id from auth.users;
insert into provider_profiles(id,user_profile_id,is_active,is_verified,status,provider_type)
values('10000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000002',true,true,'active','service_provider'),
('10000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000003',true,false,'pending','service_provider'),
('10000000-0000-0000-0000-000000000004','00000000-0000-0000-0000-000000000004',true,true,'active','mechanic');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',false);
select public.elysium_provider_wallet_ensure_v1(auth.uid()) from generate_series(1,100);
do $$begin
 if (select count(*) from provider_starter_grants where principal_id=auth.uid())<>1
 or (select sum(amount_minor) from ride_wallet_ledger where driver_id=auth.uid() and entry_type='PROMOTIONAL_GRANT')<>5000 then raise exception 'DUPLICATE_GRANT'; end if;
 begin perform public.elysium_provider_wallet_ensure_v1('00000000-0000-0000-0000-000000000003'); raise exception 'UNVERIFIED_GRANTED'; exception when insufficient_privilege then null; end;
 begin perform public.service_provider_wallet_topup_v1(auth.uid()::text,10000,'made-up'); raise exception 'FAKE_SINPE_CREDIT'; exception when insufficient_privilege then null; end;
 if public.elysium_commission_minor_v1(10,500)<>1 or public.elysium_commission_minor_v1(9,500)<>0 or public.elysium_commission_minor_v1(100000,500)<>5000 then raise exception 'ROUNDING'; end if;
end $$;
-- Historic 15000 remains immutable and suppresses the new grant.
insert into ride_wallets(driver_id,currency) values('00000000-0000-0000-0000-000000000004','CRC');
insert into ride_wallet_ledger(driver_id,idempotency_key,entry_type,amount_minor,currency,direction,withdrawable)
values('00000000-0000-0000-0000-000000000004','historic','PROMOTIONAL_GRANT',15000,'CRC','CREDIT',false);
select public.elysium_provider_wallet_ensure_v1('00000000-0000-0000-0000-000000000004');
do $$begin if (select sum(amount_minor) from ride_wallet_ledger where driver_id='00000000-0000-0000-0000-000000000004')<>15000 then raise exception 'HISTORY_REWRITTEN'; end if; end $$;
-- CASH customer has no wallet. SINPE selection is not escrow or payment proof.
insert into universal_service_requests(id,client_id,currency,state,intake,service_definition_id)
values('20000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001','CRC','OPEN','{"payment_method":"CASH"}','mechanic'),
('20000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000001','CRC','OPEN','{"payment_method":"SINPE"}','mechanic');
insert into universal_service_offers(id,request_id,provider_id,price_minor,currency,state)
values('30000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002',10000,'CRC','PENDING'),
('30000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000002',10000,'CRC','PENDING');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',false);
select public.universal_service_transition_v1('20000000-0000-0000-0000-000000000001','ACCEPT','30000000-0000-0000-0000-000000000001');
select public.universal_service_transition_v1('20000000-0000-0000-0000-000000000002','ACCEPT','30000000-0000-0000-0000-000000000002');
do $$declare b jsonb;begin
 b:=public.elysium_provider_balance_v1('00000000-0000-0000-0000-000000000002');
 if (b->>'available_minor')::bigint<>4000 or (b->>'reserved_minor')::bigint<>1000 then raise exception 'RESERVE_WRONG'; end if;
 if exists(select 1 from ride_wallets where driver_id=auth.uid()) then raise exception 'CUSTOMER_WALLET_REQUIRED'; end if;
 if exists(select 1 from universal_service_requests where payment_state in ('ESCROW_HELD','PAID')) then raise exception 'FAKE_PAYMENT'; end if;
end $$;
select public.universal_service_transition_v1('20000000-0000-0000-0000-000000000002','CANCEL');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',false);
select public.universal_service_transition_v1('20000000-0000-0000-0000-000000000001','START');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',false);
do $$begin
 begin
  perform public.universal_service_transition_v1('20000000-0000-0000-0000-000000000001','COMPLETE');
  raise exception 'CUSTOMER_COMPLETED_WITHOUT_PROVIDER_ATTESTATION';
 exception when others then
  if sqlerrm <> 'PROVIDER_DELIVERY_AND_PAYMENT_ATTESTATION_REQUIRED' then raise; end if;
 end;
 begin
  perform public.universal_service_transition_v1('20000000-0000-0000-0000-000000000001','FINISH');
  raise exception 'CUSTOMER_IMPERSONATED_PROVIDER';
 exception when others then
  if sqlerrm <> 'PROVIDER_REQUIRED' then raise; end if;
 end;
end $$;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',false);
select public.universal_service_transition_v1('20000000-0000-0000-0000-000000000001','FINISH') from generate_series(1,2);
do $$begin
 if (select provider_payment_attested_at from service_financial_contracts where aggregate_id='20000000-0000-0000-0000-000000000001') is null then raise exception 'PROVIDER_ATTESTATION_NOT_PERSISTED'; end if;
end $$;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',false);
do $$begin
 begin
  perform public.universal_service_transition_v1('20000000-0000-0000-0000-000000000001','CANCEL');
  raise exception 'ATTESTED_SERVICE_CANCELLED_WITHOUT_DISPUTE';
 exception when others then
  if sqlerrm <> 'ATTESTED_SERVICE_REQUIRES_DISPUTE' then raise; end if;
 end;
end $$;
select public.universal_service_transition_v1('20000000-0000-0000-0000-000000000001','COMPLETE') from generate_series(1,20);
do $$declare b jsonb;begin
 b:=public.elysium_provider_balance_v1('00000000-0000-0000-0000-000000000002');
 if (b->>'available_minor')::bigint<>4500 or (b->>'reserved_minor')::bigint<>0 then raise exception 'SETTLEMENT_WRONG'; end if;
 if (select count(*) from ride_wallet_ledger where entry_type='COMMISSION_CAPTURED')<>1 then raise exception 'DOUBLE_CAPTURE'; end if;
 if (select customer_confirmed_at from service_financial_contracts where aggregate_id='20000000-0000-0000-0000-000000000001') is null then raise exception 'CUSTOMER_CONFIRMATION_NOT_PERSISTED'; end if;
 if exists(select transaction_id from ride_ledger_postings group by transaction_id having sum(case when direction='DEBIT' then amount_minor else -amount_minor end)<>0) then raise exception 'UNBALANCED_JOURNAL'; end if;
 if has_function_privilege('authenticated','public.elysium_provider_wallet_ensure_v1(uuid)','EXECUTE') or has_table_privilege('authenticated','ride_wallet_ledger','INSERT') then raise exception 'PRIVILEGE_ESCAPE'; end if;
end $$;
-- Acceptance historically invented arbitrary starter credits per retry key.
insert into ride_requests(id,passenger_id,state,currency,version) values('40000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001','OFFERED','CRC',1);
insert into ride_driver_vehicles(id,driver_id,is_active,verification_status) values('50000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000002',true,'VERIFIED');
insert into ride_offers(id,request_id,driver_id,vehicle_id,fare_minor,currency,state) values('60000000-0000-0000-0000-000000000001','40000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002','50000000-0000-0000-0000-000000000002',200000,'CRC','PENDING');
do $$declare n integer; response jsonb; before_count bigint;begin
 select count(*) into before_count from ride_wallet_ledger;
 for n in 1..20 loop
  response:=public.ride_accept_offer_v2('40000000-0000-0000-0000-000000000001','60000000-0000-0000-0000-000000000001',1,'accept-abuse-key:'||n);
  if response->>'code'<>'INSUFFICIENT_PROVIDER_COMMISSION_BALANCE' then raise exception 'ACCEPT_BALANCE_BYPASS %',response; end if;
 end loop;
 if (select count(*) from ride_wallet_ledger)<>before_count or (select state from ride_requests where id='40000000-0000-0000-0000-000000000001')<>'OFFERED' then raise exception 'ACCEPT_CREATED_MONEY_OR_ASSIGNED'; end if;
 update ride_driver_vehicles set verification_status='PENDING' where id='50000000-0000-0000-0000-000000000002';
 response:=public.ride_accept_offer_v2('40000000-0000-0000-0000-000000000001','60000000-0000-0000-0000-000000000001',1,'accept-unverified-key');
 if response->>'code'<>'VERIFIED_ACTIVE_VEHICLE_REQUIRED' then raise exception 'ACCEPT_FAKE_TRUST'; end if;
end $$;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',false);
do $$declare response jsonb;begin
 response:=public.ride_submit_offer_v2('40000000-0000-0000-0000-000000000001','60000000-0000-0000-0000-000000000099','50000000-0000-0000-0000-000000000099',10000,'CRC',60,1,'submit-missing-vehicle');
 if response->>'code'<>'VERIFIED_ACTIVE_VEHICLE_REQUIRED' or exists(select 1 from ride_driver_vehicles where id='50000000-0000-0000-0000-000000000099') then raise exception 'SUBMIT_FAKE_TRUST'; end if;
end $$;
