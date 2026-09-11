-- One authenticated account may operate as passenger and driver concurrently,
-- but it may never supply both parties of the same ride.

create or replace function public.ride_reject_self_assignment()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
    if new.assigned_driver_id is not null
       and new.assigned_driver_id = new.passenger_id
    then
        raise exception using errcode = '23514', message = 'SELF_RIDE_ASSIGNMENT_FORBIDDEN';
    end if;
    return new;
end;
$$;

drop trigger if exists ride_requests_reject_self_assignment on public.ride_requests;
create trigger ride_requests_reject_self_assignment
before insert or update of passenger_id, assigned_driver_id on public.ride_requests
for each row execute function public.ride_reject_self_assignment();

create or replace function public.ride_reject_self_offer()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    if exists (
        select 1 from public.ride_requests request
         where request.id = new.request_id
           and request.passenger_id = new.driver_id
    ) then
        raise exception using errcode = '23514', message = 'SELF_RIDE_OFFER_FORBIDDEN';
    end if;
    return new;
end;
$$;

revoke all on function public.ride_reject_self_assignment() from public;
revoke all on function public.ride_reject_self_offer() from public;

drop trigger if exists ride_offers_reject_self_offer on public.ride_offers;
create trigger ride_offers_reject_self_offer
before insert or update of request_id, driver_id on public.ride_offers
for each row execute function public.ride_reject_self_offer();

comment on function public.ride_reject_self_assignment() is
    'Prevents one principal from being passenger and assigned driver on the same ride.';
comment on function public.ride_reject_self_offer() is
    'Prevents a driver from offering on a ride requested by the same principal.';
