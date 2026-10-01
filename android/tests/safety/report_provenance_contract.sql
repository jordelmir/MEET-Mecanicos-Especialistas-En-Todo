\set ON_ERROR_STOP on
begin;
set local role meet_communication_test_authenticated;
do $$
declare r jsonb; duplicate jsonb; failed boolean:=false;
begin
    perform set_config('request.jwt.claim.sub','11111111-1111-4111-8111-111111111111',true);
    r := public.safety_create_report_v3('aaaaaaaa-1111-4111-8111-111111111111','bbbbbbbb-1111-4111-8111-111111111111',
        'HOMICIDE','Narrativa de prueba protegida',null,9.9,-84.1,null,'DIRECT_WITNESS',repeat('a',64),'MAP_SELECTION',3,1,1);
    assert (r->>'server_version')::int=1,'Authority must return version';
    duplicate := public.safety_create_report_v3('aaaaaaaa-1111-4111-8111-111111111111','bbbbbbbb-1111-4111-8111-111111111111',
        'HOMICIDE','Narrativa de prueba protegida',null,9.9,-84.1,null,'DIRECT_WITNESS',repeat('a',64),'MAP_SELECTION',3,1,1);
    assert duplicate=r,'Idempotent replay must return same receipt';
    begin
        perform public.safety_create_report_v3('aaaaaaaa-1111-4111-8111-111111111111','bbbbbbbb-1111-4111-8111-111111111111',
            'HOMICIDE','Narrativa de prueba protegida',null,9.9,-84.1,null,'DIRECT_WITNESS',repeat('a',64),'MAP_SELECTION',4,1,1);
    exception when unique_violation then failed:=true; end;
    assert failed,'Changing demographics on signed receipt must reject';
    failed:=false;
    begin
        perform public.safety_create_report_v3('aaaaaaaa-2222-4222-8222-222222222222','bbbbbbbb-2222-4222-8222-222222222222',
            'HOMICIDE','Narrativa de prueba protegida',null,null,null,null,'DIRECT_WITNESS',repeat('a',64),'NONE',1,2,0);
    exception when others then failed:=true; end;
    assert failed,'Impossible counts must reject';
end $$;
reset role;
do $$ begin
    assert exists(select 1 from safety_private.report_content where location_source='MAP_SELECTION' and latitude=9.9 and longitude=-84.1),'Private pin/provenance must survive';
    assert exists(select 1 from public.safety_reports where reported_victim_count=3 and reported_victim_female=1 and reported_victim_male=1),'Reported demographics must survive';
end $$;
rollback;
