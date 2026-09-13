create or replace function public.ride_list_trusted_drivers_v1()
returns jsonb
language sql
security definer
set search_path = ''
stable
as $$
    select coalesce(jsonb_agg(row_data order by last_completed_at desc), '[]'::jsonb)
    from (
        select jsonb_build_object(
            'driver_id', r.assigned_driver_id,
            'display_name', max(p.display_name),
            'completed_trips', count(*),
            'last_completed_at', max(r.completed_at),
            'vehicle_name', max(v.display_name),
            'is_available', coalesce(bool_or(
                v.is_active
                and v.verification_status = 'VERIFIED'
                and presence.availability in ('AVAILABLE', 'OFFERING', 'FINISHING_CURRENT_TRIP')
                and presence.last_seen_at >= now() - interval '5 minutes'
            ), false)
        ) as row_data,
        max(r.completed_at) as last_completed_at
        from public.ride_requests r
        join public.ride_profiles p on p.user_id = r.assigned_driver_id
        left join public.ride_driver_vehicles v
          on v.driver_id = r.assigned_driver_id and v.is_active
        left join public.ride_driver_presence presence
          on presence.driver_id = r.assigned_driver_id
        where r.passenger_id = auth.uid()
          and r.state = 'COMPLETED'
          and r.assigned_driver_id is not null
          and r.assigned_driver_id <> auth.uid()
        group by r.assigned_driver_id
    ) trusted;
$$;

revoke all on function public.ride_list_trusted_drivers_v1() from public, anon;
grant execute on function public.ride_list_trusted_drivers_v1() to authenticated, service_role;
