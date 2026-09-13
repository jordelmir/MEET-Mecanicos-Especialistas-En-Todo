-- Converge existing databases on the canonical Mobility trips.state contract.
create or replace function public.ride_trip_terminal_guard()
returns trigger
language plpgsql
set search_path = pg_catalog, public
as $$
begin
  if (old.state = 'DISPUTED' and new.state is distinct from old.state)
     or (
       old.state in ('COMPLETED', 'CANCELLED')
       and new.state is distinct from old.state
       and new.state <> 'DISPUTED'
     ) then
    raise exception
      'TERMINAL_TRIP_TRANSITION_FORBIDDEN: trip % % -> %',
      old.trip_id, old.state, new.state
      using errcode = '23514';
  end if;
  return new;
end;
$$;

drop trigger if exists ride_trip_terminal_guard on public.trips;
create trigger ride_trip_terminal_guard
before update of state on public.trips
for each row execute function public.ride_trip_terminal_guard();

comment on function public.ride_trip_terminal_guard() is
  'Completed/cancelled trips may only escalate to disputed; disputed is irreversible.';
