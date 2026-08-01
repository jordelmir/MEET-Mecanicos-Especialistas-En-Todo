begin;

-- Collaborative reports are trip evidence. A verified driver may create one
-- only while assigned to the referenced trip and while the authoritative trip
-- state is IN_PROGRESS. The mobile gate improves UX; this RLS policy is the
-- actual server boundary and prevents stale or modified clients bypassing it.
drop policy if exists ride_incidents_driver_insert on public.ride_road_incidents;

create policy ride_incidents_driver_insert
on public.ride_road_incidents for insert to authenticated
with check (
    reporter_id = (select auth.uid()) and
    trip_id is not null and
    expires_at > now() and
    expires_at <= now() + interval '6 hours' and
    exists (
        select 1
        from public.ride_requests r
        join public.ride_driver_vehicles v
          on v.id = r.assigned_vehicle_id
         and v.driver_id = r.assigned_driver_id
        where r.id = trip_id
          and r.assigned_driver_id = (select auth.uid())
          and r.state = 'IN_PROGRESS'
          and v.is_active
          and v.verification_status = 'VERIFIED'
    )
);

commit;
