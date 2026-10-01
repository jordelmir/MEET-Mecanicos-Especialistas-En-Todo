\set ON_ERROR_STOP on
begin;
insert into auth.users(id,email) values ('11111111-1111-4111-8111-111111111111','media_a@test.invalid'),('22222222-2222-4222-8222-222222222222','media_b@test.invalid'),('33333333-3333-4333-8333-333333333333','media_c@test.invalid');
insert into communication_identity_profiles(principal_id,elysium_id,display_name) select id,'media_'||left(id::text,1),'Media Test' from auth.users where id in ('11111111-1111-4111-8111-111111111111','22222222-2222-4222-8222-222222222222','33333333-3333-4333-8333-333333333333');
insert into communication_privacy_settings(principal_id) values ('11111111-1111-4111-8111-111111111111'),('22222222-2222-4222-8222-222222222222');
set local role authenticated;
do $$
declare c uuid; e uuid:=gen_random_uuid(); call_id uuid:=gen_random_uuid(); envelope text; failed boolean; r communication_call_sessions;
begin
 perform set_config('request.jwt.claim.sub','11111111-1111-4111-8111-111111111111',true);
 perform communication_register_transport_device('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',repeat('A',500));
 c:=communication_create_direct_request('22222222-2222-4222-8222-222222222222');
 perform set_config('request.jwt.claim.sub','22222222-2222-4222-8222-222222222222',true);
 perform communication_register_transport_device('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',repeat('B',500));
 perform communication_respond_message_request(c,true);
 perform set_config('request.jwt.claim.sub','11111111-1111-4111-8111-111111111111',true);
 envelope:=jsonb_build_object('version',1,'nonce','opaque','ciphertext','opaque','recipients',jsonb_build_object('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','opaque','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb','opaque'))::text;
 assert (select event_type='AUDIO' from communication_publish_event_v2(e,c,'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',envelope,null,'2026-09-26T00:00:00Z','AUDIO'));
 assert (select count(*)=1 from communication_publish_event_v2(e,c,'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',envelope,null,'2026-09-26T00:00:00Z','AUDIO'));
 failed:=false;begin perform communication_publish_event_v2(e,c,'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',envelope,null,'2026-09-26T00:00:00Z','IMAGE');exception when others then failed:=true;end;assert failed,'Media type cannot change on replay';
 assert (select event_type='IMAGE' from communication_publish_event_v2(gen_random_uuid(),c,'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',envelope,null,now(),'IMAGE'));
 select * into r from communication_start_call_v1(c,call_id);assert r.state='RINGING' and length(r.media_key)>40;
 assert (select count(*)=1 from communication_start_call_v1(c,call_id));
 failed:=false;begin perform communication_answer_call_v1(call_id,true);exception when insufficient_privilege then failed:=true;end;assert failed,'Caller cannot answer own invitation';
 perform set_config('request.jwt.claim.sub','33333333-3333-4333-8333-333333333333',true);
 failed:=false;begin perform communication_refresh_call_v1(call_id);exception when insufficient_privilege then failed:=true;end;assert failed,'Third party cannot access call key';
 failed:=false;begin perform communication_end_call_v1(call_id);exception when insufficient_privilege then failed:=true;end;assert failed,'Third party cannot hang up';
 perform set_config('request.jwt.claim.sub','22222222-2222-4222-8222-222222222222',true);
 select * into r from communication_answer_call_v1(call_id,true);assert r.state='ACTIVE' and r.answered_by=auth.uid();
 assert (select state='ACTIVE' from communication_answer_call_v1(call_id,true));
 perform set_config('request.jwt.claim.sub','11111111-1111-4111-8111-111111111111',true);
 assert (select state='ACTIVE' from communication_refresh_call_v1(call_id));
 select * into r from communication_end_call_v1(call_id);assert r.state='ENDED' and r.media_key is null;
end $$;
reset role;
rollback;
