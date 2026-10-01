create schema auth;
create schema extensions;
create function auth.uid() returns uuid language sql stable as $$
 select nullif(current_setting('test.uid',true),'')::uuid;
$$;
create function extensions.st_distance(text,text) returns numeric language sql immutable as $$select 100::numeric$$;
create table public.ride_requests (
 id uuid primary key, passenger_id uuid not null, assigned_driver_id uuid,
 fare_mode text, state text not null, version bigint not null, currency text not null,
 fare_breakdown jsonb not null default '{}'::jsonb,
 distance_rate_minor_per_km bigint not null default 0,
 time_rate_minor_per_minute bigint not null default 0,
 fare_rate_card_version bigint not null default 1,
 final_fare_minor bigint
);
create table public.ride_fare_quotes (
 id uuid primary key default gen_random_uuid(), trip_id uuid not null,
 quote_version bigint not null, currency text not null, transport_fare_minor bigint not null,
 approved_wait_minor bigint not null default 0, approved_stops_minor bigint not null default 0,
 approved_surcharges_minor bigint not null default 0,
 collected_cancellation_fee_minor bigint not null default 0,
 driver_funded_discount_minor bigint not null default 0,
 refunded_transport_minor bigint not null default 0, tip_minor bigint not null default 0,
 tolls_minor bigint not null default 0, taxes_minor bigint not null default 0,
 platform_promotion_minor bigint not null default 0,
 created_by uuid not null,accepted_by uuid not null,supersedes_quote_id uuid,
 idempotency_key text not null unique,payload_version integer not null default 1,
 unique(trip_id,quote_version)
);
create table public.ride_commission_reservations(trip_id uuid primary key,amount_minor bigint not null);
create table public.ride_trip_events(trip_id uuid,to_state text,created_at timestamptz not null);
create table public.ride_location_breadcrumbs(
 trip_id uuid,driver_id uuid,location text,recorded_at timestamptz,
 accuracy_m numeric,seq bigint
);
create function public.elysium_provider_balance_v1(uuid) returns jsonb
 language sql as $$select '{"available_minor":5000}'::jsonb$$;
create function public.ride_command_error(text,text,boolean)
 returns jsonb language sql as $$select jsonb_build_object('ok',false,'error',jsonb_build_object('code',$1))$$;
create function public.ride_complete_trip_v2(uuid,bigint,text) returns jsonb
 language plpgsql as $$
declare fare bigint;
begin
 if $1 = '00000000-0000-0000-0000-000000000005'::uuid then
   return jsonb_build_object('ok',false,'error',jsonb_build_object('code','DOWNSTREAM_REJECTED'));
 end if;
 select transport_fare_minor into fare from public.ride_fare_quotes
 where trip_id=$1 order by quote_version desc limit 1;
 update public.ride_requests set state='COMPLETED',final_fare_minor=fare,version=version+1 where id=$1;
 insert into public.ride_trip_events(trip_id,to_state,created_at) values($1,'COMPLETED',now());
 return jsonb_build_object('ok',true,'fare',fare);
end; $$;
create function public.ride_shared_meter_snapshot_v1(uuid) returns jsonb
 language sql as $$select '{}'::jsonb$$;
