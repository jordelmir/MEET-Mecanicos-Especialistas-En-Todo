-- The online courier category is for goods only. Ride passenger requests use ride_requests.
-- Motorcycle courier limits are validated at the database boundary, not by labels in the APK.
create or replace function public.universal_courier_package_guard()
returns trigger language plpgsql set search_path = '' as $$
declare
  v_kind text;
  v_weight numeric;
  v_length numeric;
  v_width numeric;
  v_height numeric;
begin
  if new.service_definition_id <> 'courier' then return new; end if;
  v_kind := upper(coalesce(new.intake ->> 'vehicleKind', ''));
  if v_kind not in ('CAR', 'MOTORCYCLE') or
     coalesce(jsonb_typeof(new.intake -> 'weightKg'), '') <> 'number' or
     coalesce(jsonb_typeof(new.intake -> 'lengthCm'), '') <> 'number' or
     coalesce(jsonb_typeof(new.intake -> 'widthCm'), '') <> 'number' or
     coalesce(jsonb_typeof(new.intake -> 'heightCm'), '') <> 'number' then
    raise exception 'COURIER_PACKAGE_DETAILS_REQUIRED';
  end if;
  v_weight := (new.intake ->> 'weightKg')::numeric;
  v_length := (new.intake ->> 'lengthCm')::numeric;
  v_width := (new.intake ->> 'widthCm')::numeric;
  v_height := (new.intake ->> 'heightCm')::numeric;
  if v_weight <= 0 or v_length <= 0 or v_width <= 0 or v_height <= 0 or
     (v_kind = 'MOTORCYCLE' and (v_weight > 10 or v_length > 45 or v_width > 35 or v_height > 35)) or
     (v_kind = 'CAR' and (v_weight > 20 or v_length > 80 or v_width > 60 or v_height > 60)) then
    raise exception 'COURIER_PACKAGE_EXCEEDS_VEHICLE_LIMIT';
  end if;
  return new;
end $$;

drop trigger if exists universal_courier_package_guard_trigger on public.universal_service_requests;
create trigger universal_courier_package_guard_trigger
before insert or update of service_definition_id, intake on public.universal_service_requests
for each row execute function public.universal_courier_package_guard();

create or replace function public.universal_courier_provider_guard()
returns trigger language plpgsql set search_path = '' as $$
declare
  v_kind text;
begin
  select upper(r.intake ->> 'vehicleKind') into v_kind
  from public.universal_service_requests r
  where r.id = new.request_id and r.service_definition_id = 'courier';
  if not found then return new; end if;
  if not exists (
    select 1 from public.ride_driver_vehicles v
    where v.driver_id = new.provider_id and v.vehicle_kind = v_kind
      and v.is_active and v.verification_status = 'VERIFIED'
      and (v_kind <> 'MOTORCYCLE' or v.seats = 1)
  ) then raise exception 'COURIER_VERIFIED_VEHICLE_REQUIRED'; end if;
  return new;
end $$;

drop trigger if exists universal_courier_provider_guard_trigger on public.universal_service_offers;
create trigger universal_courier_provider_guard_trigger
before insert or update of request_id, provider_id on public.universal_service_offers
for each row execute function public.universal_courier_provider_guard();

-- Recheck when a client accepts an offer, because a provider can change vehicles
-- between offering and assignment.
create or replace function public.universal_courier_assignment_guard()
returns trigger language plpgsql set search_path = '' as $$
declare
  v_kind text;
begin
  if new.service_definition_id <> 'courier' or new.assigned_provider_id is null then return new; end if;
  v_kind := upper(new.intake ->> 'vehicleKind');
  if not exists (
    select 1 from public.ride_driver_vehicles v
    where v.driver_id = new.assigned_provider_id and v.vehicle_kind = v_kind
      and v.is_active and v.verification_status = 'VERIFIED'
      and (v_kind <> 'MOTORCYCLE' or v.seats = 1)
  ) then raise exception 'COURIER_VERIFIED_VEHICLE_REQUIRED'; end if;
  return new;
end $$;

drop trigger if exists universal_courier_assignment_guard_trigger on public.universal_service_requests;
create trigger universal_courier_assignment_guard_trigger
before insert or update of assigned_provider_id, state on public.universal_service_requests
for each row execute function public.universal_courier_assignment_guard();
