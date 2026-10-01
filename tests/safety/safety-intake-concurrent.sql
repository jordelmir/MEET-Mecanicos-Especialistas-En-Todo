set role authenticated;
select set_config('request.jwt.claim.sub','d1000000-0000-4000-8000-000000000001',false);
select public.safety_create_report_v3(gen_random_uuid(),gen_random_uuid(),'OTHER','Synthetic concurrent intake proof.','2026-01-01Z',null,null,null,'DOCUMENTARY',repeat('a',64),'NONE',null,null,null);
