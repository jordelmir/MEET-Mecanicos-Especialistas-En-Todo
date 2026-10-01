-- Minimal disposable fixture; does not replace replay of the complete migration chain.
create schema safety_private;
create table public.runtime_feature_gates(key text primary key,enabled boolean);
insert into public.runtime_feature_gates values('safety_reporting',true);
create table public.safety_reports(id uuid primary key,reporter_user_id uuid,category text,state text,occurred_at timestamptz,
reported_victim_count integer,reported_victim_female integer,reported_victim_male integer);
create table safety_private.report_content(report_id uuid primary key,narrative text,source_relation text,
latitude double precision,longitude double precision,accuracy_meters real,client_payload_sha256 text,server_payload_sha256 text);
create table public.safety_command_dedup(idempotency_key uuid primary key,actor_id uuid,aggregate_id uuid,command_type text,
server_payload_sha256 text,result jsonb);
