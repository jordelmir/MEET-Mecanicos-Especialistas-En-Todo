\echo 'Server byte verification custody, replay, and append-only authorization'
insert into auth.users(id) values ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
insert into public.safety_reports(id,reporter_user_id,category)
values('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','OTHER');
insert into public.safety_evidence_objects(id,report_id,uploader_user_id,storage_path,content_sha256,mime_type,byte_count)
values('cccccccc-cccc-4ccc-8ccc-cccccccccccc','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','laboratory-fixture',repeat('a',64),'application/pdf',10);

set role authenticated;
do $$ begin
  begin
    perform public.safety_record_evidence_verification_v2('cccccccc-cccc-4ccc-8ccc-cccccccccccc','MATCH',repeat('a',64),10,'TEST');
    raise exception 'Citizen forged server verification';
  exception when insufficient_privilege then null; end;
end $$;
reset role;

set role service_role;
do $$ begin
  begin
    perform public.safety_record_evidence_verification_v2('cccccccc-cccc-4ccc-8ccc-cccccccccccc','MATCH',repeat('b',64),10,'TEST');
    raise exception 'False MATCH was accepted';
  exception when raise_exception then
    if sqlerrm <> 'FALSE_MATCH_REJECTED' then raise; end if;
  end;
end $$;
select public.safety_record_evidence_verification_v2('cccccccc-cccc-4ccc-8ccc-cccccccccccc','MATCH',repeat('a',64),10,'TEST');
select public.safety_record_evidence_verification_v2('cccccccc-cccc-4ccc-8ccc-cccccccccccc','MATCH',repeat('a',64),10,'TEST');
select public.safety_record_evidence_verification_v2('cccccccc-cccc-4ccc-8ccc-cccccccccccc','MISMATCH',repeat('b',64),10,'TEST');
reset role;
do $$ declare v_first public.safety_evidence_custody%rowtype; v_second public.safety_evidence_custody%rowtype; v_hash text; begin
  if (select count(*) from public.safety_evidence_verifications where evidence_id='cccccccc-cccc-4ccc-8ccc-cccccccccccc') <> 2 then raise exception 'Replay duplicated receipt'; end if;
  select * into v_first from public.safety_evidence_custody where evidence_id='cccccccc-cccc-4ccc-8ccc-cccccccccccc' and custody_sequence=1;
  select * into v_second from public.safety_evidence_custody where evidence_id='cccccccc-cccc-4ccc-8ccc-cccccccccccc' and custody_sequence=2;
  if v_second.previous_event_hash is distinct from v_first.event_hash then raise exception 'Custody chain broken'; end if;
  v_hash:=encode(extensions.digest(convert_to(concat_ws(chr(31),'MEET-SAFETY-CUSTODY-V2',v_second.id::text,
    v_second.evidence_id::text,coalesce(v_second.previous_event_hash,''),v_second.event_type,'SERVER',v_second.reason_code,
    v_second.server_sha256,to_char(v_second.created_at at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"')),'UTF8'),'sha256'),'hex');
  if v_hash <> v_second.event_hash then raise exception 'Custody canonical hash drift'; end if;
  if public.safety_evidence_is_verified_v2('cccccccc-cccc-4ccc-8ccc-cccccccccccc') then raise exception 'Quarantine did not revoke MATCH'; end if;
  begin
    update public.safety_evidence_verifications set verifier_version='tamper';
    raise exception 'Immutable ledger updated';
  exception when raise_exception then
    if sqlerrm = 'Immutable ledger updated' then raise; end if;
  end;
end $$;
set role service_role;
do $$ begin
  begin
    perform public.safety_record_evidence_verification_v2('cccccccc-cccc-4ccc-8ccc-cccccccccccc','MATCH',repeat('a',64),10,'NEW_TEST');
    raise exception 'Quarantine silently cleared';
  exception when raise_exception then
    if sqlerrm <> 'EVIDENCE_QUARANTINED_REVIEW_REQUIRED' then raise; end if;
  end;
end $$;
reset role;
\echo 'Safety evidence verification integration: PASS'
