\set ON_ERROR_STOP on

set role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',false);
select set_config(
  'request.jwt.claims',
  '{"sub":"00000000-0000-0000-0000-000000000002","aal":"aal1"}',
  false
);

do $test$
declare
  v_type text;
  v_snapshot jsonb;
begin
  foreach v_type in array array[
    'PASSENGER','RIDE_DRIVER','TOW_TRUCK','MECHANIC','PARTS_STORE',
    'SERVICE_PROVIDER','WORKSHOP','AUTO_LOCKSMITH','LAWYER','NOTARY',
    'PROPERTY_BROKER','PROPERTY_SELLER','FUEL_STATION_STAFF','FLEET_OPERATOR'
  ] loop
    perform public.meet_submit_service_verification_v2(
      v_type,
      'delivery-' || lower(v_type),
      'Solicitante de prueba'
    );
  end loop;

  v_snapshot := public.meet_own_verification_applications_v1();
  if jsonb_array_length(v_snapshot->'items') < 14 or not exists(
    select 1
      from public.service_verification_applications
     where applicant_user_id='00000000-0000-0000-0000-000000000002'
       and profile_reference like 'delivery-%'
     group by applicant_user_id
    having count(*)=14
  ) then
    raise exception 'own queue did not reconcile every category';
  end if;

  begin
    perform public.meet_owner_verification_queue_v2('PENDING',100);
    raise exception 'ordinary applicant read owner queue';
  exception when insufficient_privilege then
    if sqlerrm <> 'PLATFORM_OWNER_REQUIRED' then raise; end if;
  end;
end;
$test$;

select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',false);
select set_config(
  'request.jwt.claims',
  '{"sub":"00000000-0000-0000-0000-000000000001","aal":"aal1"}',
  false
);

do $test$
declare
  v_application_id uuid;
  v_snapshot jsonb;
begin
  v_snapshot := public.meet_owner_verification_queue_v2('PENDING',100);
  if (v_snapshot #>> '{counts,PENDING}')::integer < 14 or
     jsonb_array_length(v_snapshot->'items') < 14 then
    raise exception 'owner queue snapshot or counts are incomplete';
  end if;

  select id into strict v_application_id
    from public.service_verification_applications
   where applicant_user_id='00000000-0000-0000-0000-000000000002'
     and service_type='MECHANIC';

  begin
    perform public.meet_owner_decide_verification_v2(
      v_application_id,'APPROVED','AAL1 must remain blocked'
    );
    raise exception 'AAL1 unexpectedly approved an application';
  exception when insufficient_privilege then
    if sqlerrm <> 'AAL2_REQUIRED' then raise; end if;
  end;

  if has_function_privilege(
    'authenticated',
    'public.meet_owner_decide_verification_v1(uuid,text,text)',
    'EXECUTE'
  ) then
    raise exception 'legacy review RPC became client executable';
  end if;
end;
$test$;

select set_config(
  'request.jwt.claims',
  '{"sub":"00000000-0000-0000-0000-000000000001","aal":"aal2"}',
  false
);

do $test$
declare
  v_application_id uuid;
begin
  select id into strict v_application_id
    from public.service_verification_applications
   where applicant_user_id='00000000-0000-0000-0000-000000000002'
     and service_type='MECHANIC';

  perform public.meet_owner_decide_verification_v2(
    v_application_id,'APPROVED','Evidencia comprobada por revisión humana'
  );
end;
$test$;

reset role;

do $test$
declare
  v_application_id uuid;
  v_correlation_id uuid;
  v_capability_state text;
  v_verified_at timestamptz;
begin
  select id,correlation_id into strict v_application_id,v_correlation_id
    from public.service_verification_applications
   where applicant_user_id='00000000-0000-0000-0000-000000000002'
     and service_type='MECHANIC';

  select activation_state,verified_at
    into v_capability_state,v_verified_at
    from public.principal_capabilities
   where principal_id='00000000-0000-0000-0000-000000000002'
     and capability='MECHANIC';
  if v_capability_state is distinct from 'APPROVED' or v_verified_at is null then
    raise exception 'approved decision did not activate the capability: state=%, verified_at=%',
      v_capability_state,v_verified_at;
  end if;

  if not exists(
    select 1 from public.service_verification_audit_events
     where application_id=v_application_id
       and event_type='APPROVED'
       and correlation_id=v_correlation_id
  ) then
    raise exception 'decision audit lost its submission correlation';
  end if;

  if not exists(
    select 1 from pg_publication_tables
     where pubname='supabase_realtime'
       and schemaname='public'
       and tablename='service_verification_applications'
  ) then
    raise exception 'trust queue is absent from Realtime publication';
  end if;
end;
$test$;

select 'Trust Center delivery, AAL2 and Realtime publication: PASS' as result;
