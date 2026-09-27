-- Runs inside the disposable fixture transaction, never against production.
insert into auth.users(id,email) values
('11111111-1111-4111-8111-111111111111','service_client@test.invalid'),
('22222222-2222-4222-8222-222222222222','service_provider@test.invalid'),
('33333333-3333-4333-8333-333333333333','service_other@test.invalid');
insert into public.user_profiles(id,auth_user_id) values('22222222-2222-4222-8222-222222222222','22222222-2222-4222-8222-222222222222');
insert into public.provider_profiles values('22222222-2222-4222-8222-222222222222','22222222-2222-4222-8222-222222222222','service_provider',true,true,'active');
insert into public.service_definitions values('plumbing');
insert into public.universal_service_requests(id,client_id,service_definition_id,modality,title,description,offered_price_minor,currency)
values('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','11111111-1111-4111-8111-111111111111','plumbing','PHYSICAL','Plomería','Reparación de tubería',10000,'CRC');
insert into public.universal_service_offers(id,request_id,provider_id,price_minor,currency)
values('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','22222222-2222-4222-8222-222222222222',12000,'CRC');
do $$ declare r public.universal_service_requests; denied boolean; begin
 perform set_config('request.jwt.claim.sub','33333333-3333-4333-8333-333333333333',true);
 denied:=false;begin perform public.universal_service_transition_v1('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','ACCEPT','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');exception when insufficient_privilege then denied:=true;end;assert denied;
 perform set_config('request.jwt.claim.sub','11111111-1111-4111-8111-111111111111',true);
 select * into r from public.universal_service_transition_v1('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','ACCEPT','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
 assert r.state='ASSIGNED' and r.final_price_minor=12000 and r.version=2;
 assert (select balance_minor=4400 and user_id='22222222-2222-4222-8222-222222222222'::uuid from public.service_provider_wallets where provider_id='22222222-2222-4222-8222-222222222222');
 perform public.universal_service_transition_v1(r.id,'ACCEPT','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
 assert (select count(*)=1 from public.service_provider_wallet_ledger where entry_type='CONSTITUTIONAL_FEE_5_PERCENT');
 denied:=false;begin perform public.universal_service_transition_v1(r.id,'START');exception when insufficient_privilege then denied:=true;end;assert denied;
 perform set_config('request.jwt.claim.sub','22222222-2222-4222-8222-222222222222',true);
 select * into r from public.universal_service_transition_v1(r.id,'START'); assert r.state='IN_PROGRESS';
 denied:=false;begin perform public.universal_service_transition_v1(r.id,'COMPLETE');exception when insufficient_privilege then denied:=true;end;assert denied;
 perform set_config('request.jwt.claim.sub','11111111-1111-4111-8111-111111111111',true);
 select * into r from public.universal_service_transition_v1(r.id,'COMPLETE'); assert r.state='COMPLETED' and r.payment_state='NOT_STARTED';
 perform public.universal_service_rate_v1(r.id,5); perform public.universal_service_rate_v1(r.id,5);
 assert (select count(*)=1 from public.universal_service_ratings);
 denied:=false;begin perform public.universal_service_rate_v1(r.id,1);exception when others then denied:=true;end;assert denied;
end $$;

insert into public.ride_wallets(driver_id) values('22222222-2222-4222-8222-222222222222');
insert into public.ride_wallet_ledger(driver_id,idempotency_key,entry_type,amount_minor,currency,direction)
values('22222222-2222-4222-8222-222222222222','approved-test','TOP_UP_CONFIRMED',2000,'CRC','CREDIT');
do $$ declare denied boolean; begin
 perform set_config('request.jwt.claim.sub','22222222-2222-4222-8222-222222222222',true);
 perform public.universal_service_wallet_transfer_v1('cccccccc-cccc-4ccc-8ccc-cccccccccccc',1000);
 perform public.universal_service_wallet_transfer_v1('cccccccc-cccc-4ccc-8ccc-cccccccccccc',1000);
 assert (select balance_minor=5400 from public.service_provider_wallets where provider_id=auth.uid()::text);
 assert (public.ride_wallet_balance_v1()->>'available_minor')::bigint=1000;
 denied:=false;begin perform public.universal_service_wallet_transfer_v1('dddddddd-dddd-4ddd-8ddd-dddddddddddd',2000);exception when others then denied:=true;end;assert denied;
 denied:=false;begin perform public.universal_service_wallet_transfer_v1('cccccccc-cccc-4ccc-8ccc-cccccccccccc',2000);exception when others then denied:=true;end;assert denied;
end $$;
grant usage on schema public,auth to authenticated;
grant select,insert on public.universal_service_requests to authenticated;
set local role authenticated;
do $$ declare denied boolean; begin
 perform set_config('request.jwt.claim.sub','11111111-1111-4111-8111-111111111111',true);
 denied:=false;begin update public.universal_service_requests set state='COMPLETED';exception when insufficient_privilege then denied:=true;end;assert denied;
 denied:=false;begin
 insert into public.universal_service_requests(client_id,service_definition_id,modality,title,description,offered_price_minor,currency,state)
 values(auth.uid(),'plumbing','PHYSICAL','Fake','Fake completed request',1,'CRC','COMPLETED');
 exception when insufficient_privilege then denied:=true;end;assert denied;
end $$;
reset role;
