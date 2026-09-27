-- Run only in a fresh, disposable local database.
create schema auth;
create schema extensions;
create extension pgcrypto with schema extensions;
create table auth.users(id uuid primary key, email text);
create function auth.uid() returns uuid language sql stable as $$ select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid $$;
create schema storage;
create table storage.buckets(id text primary key,name text,public boolean,file_size_limit bigint,allowed_mime_types text[]);
create table storage.objects(bucket_id text,name text,metadata jsonb);
create function storage.foldername(text) returns text[] language sql immutable as $$ select string_to_array($1,'/') $$;
