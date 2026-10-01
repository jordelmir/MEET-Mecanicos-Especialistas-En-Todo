-- Garage ownership stays account scoped; vehicle kind is synchronized across devices.
alter table public.cloud_vehicles
  add column if not exists vehicle_kind text not null default 'CAR';
alter table public.cloud_vehicles
  add column if not exists displacement_cc integer not null default 0;
alter table public.cloud_vehicles
  add column if not exists engine_tech text not null default '';
alter table public.cloud_vehicles
  add column if not exists transmission_type text not null default '';
alter table public.cloud_vehicles
  add column if not exists transmission_subtype text not null default '';
alter table public.cloud_vehicles
  add column if not exists fuel_type text not null default '';
alter table public.cloud_vehicles
  drop constraint if exists cloud_vehicle_kind_valid;
alter table public.cloud_vehicles
  add constraint cloud_vehicle_kind_valid
  check (vehicle_kind in ('CAR', 'MOTORCYCLE'));
