\set ON_ERROR_STOP on

insert into auth.users(id, email, email_confirmed_at) values
    ('a1111111-1111-1111-1111-111111111111', 'jordelmir@gmail.com', now()),
    ('a2222222-2222-2222-2222-222222222222', 'applicant@example.com', now());

select set_config(
    'request.jwt.claim.sub',
    'a2222222-2222-2222-2222-222222222222',
    false
);

select public.meet_submit_service_verification_v1(
    'TOW_TRUCK',
    'tow-profile-test',
    'Proveedor Test',
    'Grúas Test',
    '+506 8000 0000',
    'San José',
    'LIC-TEST-001',
    null
);

do $test$
begin
    if public.meet_is_platform_owner() then
        raise exception 'ordinary applicant gained platform owner authority';
    end if;
    begin
        perform public.meet_owner_verification_queue_v1('PENDING', 100);
        raise exception 'ordinary applicant read owner queue';
    exception
        when insufficient_privilege then null;
    end;
end;
$test$;

select set_config(
    'request.jwt.claim.sub',
    'a1111111-1111-1111-1111-111111111111',
    false
);

do $test$
declare
    v_application_id uuid;
begin
    if not public.meet_is_platform_owner() then
        raise exception 'confirmed master account did not gain platform owner authority';
    end if;

    select id into v_application_id
      from public.service_verification_applications
     where applicant_user_id = 'a2222222-2222-2222-2222-222222222222'
       and service_type = 'TOW_TRUCK';

    perform public.meet_owner_decide_verification_v1(
        v_application_id,
        'APPROVED',
        'Licencia comprobada en fuente oficial de prueba'
    );

    if not exists (
        select 1
          from public.service_verification_applications
         where id = v_application_id
           and status = 'APPROVED'
           and reviewed_by = 'a1111111-1111-1111-1111-111111111111'
    ) then
        raise exception 'owner decision was not persisted';
    end if;
    if not exists (
        select 1
          from public.service_verification_audit_events
         where application_id = v_application_id
           and event_type = 'APPROVED'
    ) then
        raise exception 'owner decision audit event missing';
    end if;
end;
$test$;

select 'platform trust center integration: PASS' as result;
