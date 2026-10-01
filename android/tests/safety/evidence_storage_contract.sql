\set ON_ERROR_STOP on
begin;
select set_config('request.jwt.claim.sub','11111111-1111-4111-8111-111111111111',false);
do $$ declare i int; eid uuid; path text; r jsonb; again jsonb; failed boolean;
begin
 assert (select file_size_limit=20971520 and not public and allowed_mime_types is null from storage.buckets where id='safety-evidence-original');
 assert not public.safety_can_upload_original('bad/path.original');
 assert not public.safety_can_upload_original('22222222-2222-4222-8222-222222222222/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb.original');
 for i in 1..6 loop
  eid := ('bbbbbbbb-bbbb-4bbb-8bbb-'||lpad(i::text,12,'0'))::uuid;
  path := auth.uid()::text || '/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/' || eid::text || '.original';
  assert public.safety_can_upload_original(path);
  insert into storage.objects(bucket_id,name,owner,metadata) values('safety-evidence-original',path,auth.uid(),'{"size":16,"mimetype":"application/octet-stream"}');
  failed:=false;
  begin r:=public.safety_register_evidence_v1(eid,'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',path,repeat('a',64),'image/jpeg',16,null);
  exception when others then if sqlerrm='EVIDENCE_ATTACHMENT_LIMIT' then failed:=true; else raise; end if; end;
  if i=6 then assert failed,'Sixth evidence must reject'; else
   assert not failed;
   assert r->>'integrity_level'='CLIENT_DIGEST_REGISTERED';
   again:=public.safety_register_evidence_v1(eid,'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',path,repeat('a',64),'image/jpeg',16,null);
   assert again=r,'Replay stable';
   failed:=false;
   begin perform public.safety_register_evidence_v1(eid,'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',path,repeat('a',64),'image/png',16,null);
   exception when others then assert sqlerrm='EVIDENCE_IDEMPOTENCY_VIOLATION'; failed:=true; end;
   assert failed,'Changed MIME must reject';
  end if;
 end loop;
 assert (select count(*)=5 from public.safety_evidence_custody),'One custody event per accepted evidence';
 update public.runtime_feature_gates set enabled=false where key='safety_foundation';
 assert not public.safety_can_upload_original(path),'Foundation kill switch';
 failed:=false;
 begin perform public.safety_register_evidence_v1(eid,'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',path,repeat('a',64),'image/jpeg',16,null);
 exception when others then assert sqlerrm='SAFETY_EVIDENCE_UPLOAD_DISABLED'; failed:=true; end;
 assert failed;
end $$;
rollback;

begin;
select set_config('request.jwt.claim.sub','11111111-1111-4111-8111-111111111111',false);
set local role authenticated;
do $$ declare failed boolean:=false; begin
 insert into storage.objects(bucket_id,name,owner,metadata) values('safety-evidence-original',auth.uid()::text||'/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/cccccccc-cccc-4ccc-8ccc-cccccccccccc.original',auth.uid(),'{"size":16,"mimetype":"image/jpeg"}');
 begin insert into storage.objects(bucket_id,name,owner,metadata) values('safety-evidence-original',auth.uid()::text||'/dddddddd-dddd-4ddd-8ddd-dddddddddddd/cccccccc-cccc-4ccc-8ccc-cccccccccccc.original',auth.uid(),'{"size":16}');
 exception when insufficient_privilege then failed:=true; end;
 assert failed,'RLS must reject non-owned report';
end $$;
reset role;
update public.runtime_feature_gates set enabled=false where key='safety_evidence_upload';
set local role authenticated;
do $$ declare failed boolean:=false; begin
 begin insert into storage.objects(bucket_id,name,owner,metadata) values('safety-evidence-original',auth.uid()::text||'/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee.original',auth.uid(),'{"size":16}');
 exception when insufficient_privilege then failed:=true; end;
 assert failed,'RLS gate must reject upload before plaintext reaches storage';
end $$;
reset role;
rollback;

begin;
select set_config('request.jwt.claim.sub','11111111-1111-4111-8111-111111111111',false);
insert into storage.objects(bucket_id,name,owner,metadata) values('safety-evidence-original','11111111-1111-4111-8111-111111111111/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/cccccccc-cccc-4ccc-8ccc-cccccccccccc.original',auth.uid(),'{"size":16,"mimetype":"image/jpeg"}');
do $$ declare expected text; failed boolean; begin
 foreach expected in array array['INVALID_SHA256','INVALID_BYTE_COUNT','UNSUPPORTED_MIME_TYPE','EVIDENCE_OBJECT_SIZE_MISMATCH','EVIDENCE_OBJECT_MIME_MISMATCH'] loop
  failed:=false;
  begin
   perform public.safety_register_evidence_v1('cccccccc-cccc-4ccc-8ccc-cccccccccccc','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',auth.uid()::text||'/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/cccccccc-cccc-4ccc-8ccc-cccccccccccc.original',
    case when expected='INVALID_SHA256' then null else repeat('a',64) end,
    case when expected='UNSUPPORTED_MIME_TYPE' then 'text/html' when expected='EVIDENCE_OBJECT_MIME_MISMATCH' then 'image/png' else 'image/jpeg' end,
    case when expected='INVALID_BYTE_COUNT' then 20971521 when expected='EVIDENCE_OBJECT_SIZE_MISMATCH' then 17 else 16 end,null);
  exception when others then assert sqlerrm=expected,format('Expected %s, got %s',expected,sqlerrm); failed:=true; end;
  assert failed,expected||' must reject';
 end loop;
end $$;
rollback;
