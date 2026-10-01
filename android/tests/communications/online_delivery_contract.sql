\set ON_ERROR_STOP on
begin;
grant usage on schema auth to meet_communication_test_authenticated;
grant select,insert on public.communication_blocks to meet_communication_test_authenticated;
grant select on public.communication_events to meet_communication_test_authenticated;
grant select on public.communication_conversations,public.communication_participants,public.communication_devices to meet_communication_test_authenticated;
insert into auth.users(id,email) values
('11111111-1111-4111-8111-111111111111','a@test.invalid'),
('22222222-2222-4222-8222-222222222222','b@test.invalid'),
('33333333-3333-4333-8333-333333333333','c@test.invalid');
insert into public.communication_identity_profiles(principal_id,elysium_id,display_name)
select id, case left(id::text,1) when '1' then 'user_one' when '2' then 'user_two' else 'user_three' end,'Test' from auth.users;
set local role meet_communication_test_authenticated;
do $$
declare t text; c uuid; n int; failed boolean:=false; seq bigint;
begin
    perform set_config('request.jwt.claim.sub','11111111-1111-4111-8111-111111111111',true);
    perform public.communication_register_transport_device('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',repeat('A',500));
    t:=public.communication_issue_pairing_token();
    perform set_config('request.jwt.claim.sub','22222222-2222-4222-8222-222222222222',true);
    perform public.communication_register_transport_device('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',repeat('B',500));
    c:=public.communication_consume_pairing_token(t);
    select count(*) into n from public.communication_contact_profiles();
    assert n=1,'Paired contact profile must project';
    perform set_config('request.jwt.claim.sub','11111111-1111-4111-8111-111111111111',true);
    perform public.communication_publish_event('eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee',c,
        'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','{"version":1,"nonce":"nonce","ciphertext":"encrypted","recipients":{"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa":"wrapped","bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb":"wrapped"}}',null,'2026-09-26T00:00:00Z');
    select server_sequence into seq from public.communication_events where event_id='eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee';
    assert seq>0,'Server assigns authoritative sequence';
    perform set_config('request.jwt.claim.sub','22222222-2222-4222-8222-222222222222',true);
    select count(*) into n from public.communication_events where conversation_id=c;
    assert n=1,'Second account receives persisted envelope';
    perform set_config('request.jwt.claim.sub','33333333-3333-4333-8333-333333333333',true);
    select count(*) into n from public.communication_events where conversation_id=c;
    assert n=0,'Third account cannot read envelopes';
    begin
        insert into public.communication_events(event_id,conversation_id,sender_id,sender_device_id,event_type,encrypted_envelope,idempotency_key,client_created_at)
        values(gen_random_uuid(),c,'11111111-1111-4111-8111-111111111111','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','TEXT',repeat('forged',10),gen_random_uuid(),now());
    exception when insufficient_privilege then failed:=true; end;
    assert failed,'Third account cannot impersonate sender';
    perform set_config('request.jwt.claim.sub','11111111-1111-4111-8111-111111111111',true);
    insert into public.communication_blocks(blocker_id,blocked_id) values(auth.uid(),'22222222-2222-4222-8222-222222222222');
    perform set_config('request.jwt.claim.sub','22222222-2222-4222-8222-222222222222',true);
    assert not public.communication_can_send(c,'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'),'Recipient-issued block must deny sender';
    failed:=false;
    begin
        insert into public.communication_events(event_id,conversation_id,sender_id,sender_device_id,event_type,encrypted_envelope,idempotency_key,client_created_at)
        values(gen_random_uuid(),c,auth.uid(),'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb','TEXT',repeat('blocked',10),gen_random_uuid(),now());
    exception when insufficient_privilege then failed:=true; end;
    assert failed,'Blocked actor must not bypass client through direct insert';

end $$;
reset role;
rollback;
