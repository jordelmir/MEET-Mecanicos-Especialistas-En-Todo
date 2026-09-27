\set ON_ERROR_STOP on
begin;
insert into auth.users(id,email) values
('11111111-1111-4111-8111-111111111111','a@test.invalid'),
('22222222-2222-4222-8222-222222222222','b@test.invalid'),
('33333333-3333-4333-8333-333333333333','c@test.invalid');
insert into public.communication_identity_profiles(principal_id,elysium_id,display_name)
select id, case left(id::text,1) when '1' then 'user_one' when '2' then 'user_two' else 'user_three' end,'Test' from auth.users;
set local role meet_communication_test_authenticated;
do $$
declare v_token text; v_id uuid; v_failed boolean := false; v_count int;
begin
    perform set_config('request.jwt.claim.sub','',true);
    begin perform public.communication_issue_pairing_token(); exception when insufficient_privilege then v_failed:=true; end;
    assert v_failed,'Unauthenticated pairing must fail';
    perform set_config('request.jwt.claim.sub','11111111-1111-4111-8111-111111111111',true);
    perform public.communication_register_transport_device('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',repeat('A',500));
    v_token:=public.communication_issue_pairing_token();
    v_failed:=false;
    begin perform public.communication_consume_pairing_token(v_token); exception when others then v_failed:=true; end;
    assert v_failed,'Self pairing must fail';
    perform set_config('request.jwt.claim.sub','22222222-2222-4222-8222-222222222222',true);
    perform public.communication_register_transport_device('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',repeat('B',500));
    v_id:=public.communication_consume_pairing_token(v_token);
    select count(*) into v_count from public.communication_conversation_device_keys(v_id);
    assert v_count=2,'Both device keys available after authoritative pairing';
    assert public.communication_consume_pairing_token(v_token)=v_id,'Same consumer retry returns original receipt';
    v_failed:=false;
    begin perform public.communication_register_transport_device('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',repeat('X',500)); exception when insufficient_privilege then v_failed:=true; end;
    assert v_failed,'Device key replacement must fail';
    perform set_config('request.jwt.claim.sub','33333333-3333-4333-8333-333333333333',true);
    v_failed:=false;
    begin perform public.communication_consume_pairing_token(v_token); exception when others then v_failed:=true; end;
    assert v_failed,'Different consumer cannot reuse token';
    v_failed:=false;
    begin perform public.communication_conversation_device_keys(v_id); exception when insufficient_privilege then v_failed:=true; end;
    assert v_failed,'Third party must not read device keys';
end $$;
reset role;
rollback;
