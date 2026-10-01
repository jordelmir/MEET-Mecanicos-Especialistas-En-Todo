-- Disposable fixture only. Full deployed migration-chain validation remains separate.
create role anon; create role authenticated;
create schema auth; create schema storage; create schema extensions;
create extension pgcrypto with schema extensions;
create function auth.uid() returns uuid language sql stable as $$ select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid $$;
create table public.runtime_feature_gates(key text primary key,enabled boolean);
insert into public.runtime_feature_gates values('safety_foundation',true),('safety_evidence_upload',true);
create table public.safety_reports(id uuid primary key,reporter_user_id uuid);
insert into public.safety_reports values('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','11111111-1111-4111-8111-111111111111');
create table storage.buckets(id text primary key,public boolean,file_size_limit bigint,allowed_mime_types text[]);
insert into storage.buckets values('safety-evidence-original',false,null,null);
create table storage.objects(id uuid primary key default gen_random_uuid(),bucket_id text,name text,owner uuid,owner_id text,metadata jsonb,unique(bucket_id,name));
alter table storage.objects enable row level security;
grant usage on schema auth,storage to authenticated;
grant insert on storage.objects to authenticated;
create table public.safety_evidence_objects(id uuid primary key,report_id uuid,uploader_user_id uuid,storage_path text,content_sha256 text,mime_type text,byte_count bigint,captured_at timestamptz);
create table public.safety_evidence_custody(id uuid primary key,evidence_id uuid,event_type text,actor_id uuid,reason_code text,previous_event_hash text,event_hash text);
