insert into auth.users(id) values('d1000000-0000-4000-8000-000000000001');
update safety_private.intake_policy set max_submissions=1,window_seconds=86400;
insert into public.runtime_feature_gates(key,enabled) values('safety_reporting',true) on conflict(key) do update set enabled=true;
