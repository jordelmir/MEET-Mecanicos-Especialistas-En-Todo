-- The driver can rate the passenger only after authoritative completion.
-- Keep this separate from passenger-to-driver reputation and its ledger.
create table if not exists public.ride_passenger_feedback (
    trip_id uuid primary key references public.ride_requests(id) on delete restrict,
    driver_id uuid not null references auth.users(id) on delete restrict,
    passenger_id uuid not null references auth.users(id) on delete restrict,
    rating smallint not null check (rating between 1 and 5),
    created_at timestamptz not null default now()
);

alter table public.ride_passenger_feedback enable row level security;
revoke all on public.ride_passenger_feedback from public, anon, authenticated;

create or replace function public.ride_record_passenger_feedback_v1(
    p_trip_id uuid,
    p_rating smallint
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_actor uuid := (select auth.uid());
    v_ride public.ride_requests%rowtype;
    v_recorded_rating smallint;
begin
    if v_actor is null then
        raise exception using errcode = '42501', message = 'AUTHENTICATION_REQUIRED';
    end if;
    if p_rating is null or p_rating not between 1 and 5 then
        raise exception using errcode = '22023', message = 'RATING_OUT_OF_RANGE';
    end if;

    select r.* into v_ride
      from public.ride_requests r
     where r.id = p_trip_id
       and r.assigned_driver_id = v_actor
       and r.state = 'COMPLETED'
     for update;
    if not found then
        raise exception using errcode = '42501', message = 'TRIP_NOT_ELIGIBLE_FOR_FEEDBACK';
    end if;

    insert into public.ride_passenger_feedback(trip_id, driver_id, passenger_id, rating)
    values (p_trip_id, v_actor, v_ride.passenger_id, p_rating)
    on conflict (trip_id) do nothing;

    select f.rating into v_recorded_rating
      from public.ride_passenger_feedback f
     where f.trip_id = p_trip_id;
    if v_recorded_rating <> p_rating then
        raise exception using errcode = '23505', message = 'FEEDBACK_ALREADY_RECORDED';
    end if;
    return jsonb_build_object('success', true, 'trip_id', p_trip_id);
end;
$$;

revoke all on function public.ride_record_passenger_feedback_v1(uuid, smallint) from public, anon;
grant execute on function public.ride_record_passenger_feedback_v1(uuid, smallint) to authenticated;
