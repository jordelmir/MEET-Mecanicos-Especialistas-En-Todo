-- Passenger motorcycles are distinct from parcel/food delivery.
-- Existing vehicles and requests remain CAR until their owner explicitly registers a motorcycle.
alter table public.ride_driver_vehicles
  add column if not exists vehicle_kind text not null default 'CAR';
alter table public.ride_driver_vehicles
  add constraint ride_driver_vehicle_kind_valid
  check (vehicle_kind in ('CAR', 'MOTORCYCLE') and
         (vehicle_kind <> 'MOTORCYCLE' or seats = 1));

alter table public.ride_requests
  add column if not exists requested_vehicle_kind text not null default 'CAR';
alter table public.ride_requests
  add constraint ride_requested_vehicle_kind_valid
  check (requested_vehicle_kind in ('CAR', 'MOTORCYCLE'));

create or replace function public.ride_request_vehicle_kind_guard()
returns trigger language plpgsql set search_path = '' as $$
declare
  v_preferences jsonb;
  v_kind text;
  v_vehicle public.ride_driver_vehicles%rowtype;
begin
  if tg_op = 'INSERT' or new.fare_breakdown is distinct from old.fare_breakdown then
    v_preferences := coalesce(new.fare_breakdown -> 'preferences', '{}'::jsonb);
    v_kind := upper(coalesce(v_preferences ->> 'vehicleKind', v_preferences ->> 'vehicle_kind', 'CAR'));
    if v_kind not in ('CAR', 'MOTORCYCLE') then
      raise exception 'INVALID_VEHICLE_KIND';
    end if;
    if v_kind = 'MOTORCYCLE' and (
      coalesce(v_preferences ->> 'kidsCount', '0') <> '0' or
      coalesce(v_preferences ->> 'fivePassengers', 'false') = 'true' or
      upper(coalesce(v_preferences ->> 'pet', 'NONE')) <> 'NONE'
    ) then
      raise exception 'MOTORCYCLE_ONE_PASSENGER_ONLY';
    end if;
    new.requested_vehicle_kind := v_kind;
  end if;
  if new.assigned_vehicle_id is not null then
    select * into v_vehicle from public.ride_driver_vehicles where id = new.assigned_vehicle_id;
    if not found or v_vehicle.vehicle_kind <> new.requested_vehicle_kind or
       (new.requested_vehicle_kind = 'MOTORCYCLE' and v_vehicle.seats <> 1) then
      raise exception 'RIDE_VEHICLE_KIND_MISMATCH';
    end if;
  end if;
  return new;
end $$;

drop trigger if exists ride_request_vehicle_kind_guard_trigger on public.ride_requests;
create trigger ride_request_vehicle_kind_guard_trigger
before insert or update of fare_breakdown, assigned_vehicle_id on public.ride_requests
for each row execute function public.ride_request_vehicle_kind_guard();

create or replace function public.ride_offer_vehicle_kind_guard()
returns trigger language plpgsql set search_path = '' as $$
begin
  if not exists (
    select 1 from public.ride_requests r
    join public.ride_driver_vehicles v on v.id = new.vehicle_id
    where r.id = new.request_id and v.driver_id = new.driver_id
      and r.requested_vehicle_kind = v.vehicle_kind
      and (v.vehicle_kind <> 'MOTORCYCLE' or v.seats = 1)
  ) then raise exception 'RIDE_VEHICLE_KIND_MISMATCH'; end if;
  return new;
end $$;

drop trigger if exists ride_offer_vehicle_kind_guard_trigger on public.ride_offers;
create trigger ride_offer_vehicle_kind_guard_trigger
before insert or update of request_id, vehicle_id on public.ride_offers
for each row execute function public.ride_offer_vehicle_kind_guard();

create or replace function public.ride_upsert_driver_vehicle_v2(
  p_vehicle_id text, p_display_name text, p_seats integer,
  p_make text, p_model text, p_model_year integer, p_color text,
  p_plate_masked text, p_vehicle_kind text, p_fleet_name text default null
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  v_user_id uuid := (select auth.uid());
  v_id uuid;
begin
  if v_user_id is null then
    return public.ride_command_error('UNAUTHENTICATED', 'Autenticación requerida', false);
  end if;
  if upper(coalesce(p_vehicle_kind, '')) not in ('CAR', 'MOTORCYCLE') or
     (upper(p_vehicle_kind) = 'MOTORCYCLE' and p_seats <> 1) or
     p_seats not between 1 and 16 or p_model_year not between 1900 and 2200 or
     char_length(trim(coalesce(p_vehicle_id, ''))) not between 1 and 120 or
     char_length(trim(coalesce(p_display_name, ''))) not between 1 and 160 or
     trim(coalesce(p_make, '')) = '' or trim(coalesce(p_model, '')) = '' or
     trim(coalesce(p_color, '')) = '' or trim(coalesce(p_plate_masked, '')) = '' then
    return public.ride_command_error('VALIDATION_ERROR', 'Datos del vehículo inválidos', false);
  end if;
  insert into public.ride_driver_vehicles (
    driver_id, vehicle_id, display_name, seats, vehicle_kind,
    verification_status, is_active, make, model, model_year, color,
    plate_masked, fleet_name, document_review_status
  ) values (
    v_user_id, trim(p_vehicle_id), trim(p_display_name), p_seats,
    upper(p_vehicle_kind), 'PENDING', false, trim(p_make), trim(p_model),
    p_model_year, trim(p_color), upper(trim(p_plate_masked)),
    nullif(trim(p_fleet_name), ''), 'PENDING'
  ) returning id into v_id;
  return public.ride_command_success(jsonb_build_object(
    'status', 'PENDING_REVIEW', 'vehicle_id', v_id,
    'vehicle_kind', upper(p_vehicle_kind)
  ));
end $$;

revoke all on function public.ride_upsert_driver_vehicle_v2(
  text,text,integer,text,text,integer,text,text,text,text) from public;
grant execute on function public.ride_upsert_driver_vehicle_v2(
  text,text,integer,text,text,integer,text,text,text,text) to authenticated;

-- Drivers should only see requests that their active verified vehicle can carry.
drop policy if exists ride_requests_participant_select on public.ride_requests;
create policy ride_requests_participant_select on public.ride_requests
for select to authenticated using (
  passenger_id = (select auth.uid()) or assigned_driver_id = (select auth.uid()) or
  (
    state in ('SEARCHING', 'OFFERED') and
    (
      exists (
        select 1 from public.ride_driver_vehicles v
        where v.driver_id = (select auth.uid())
          and v.tenant_id = ride_requests.tenant_id
          and v.is_active and v.verification_status = 'VERIFIED'
          and v.vehicle_kind = ride_requests.requested_vehicle_kind
          and (v.vehicle_kind <> 'MOTORCYCLE' or v.seats = 1)
      ) or public.ride_is_active_tenant_member(
        ride_requests.tenant_id, array['TENANT_ADMIN', 'DISPATCHER']
      )
    )
  )
);
