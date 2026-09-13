-- Complete the authoritative Viajes realtime projection. RLS remains the
-- visibility boundary; publication only wakes clients so they can catch up.
do $$
declare
    v_table text;
begin
    if not exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
        return;
    end if;

    foreach v_table in array array[
        'ride_offers',
        'ride_request_stops',
        'ride_driver_vehicles'
    ] loop
        if to_regclass('public.' || v_table) is not null
           and not exists (
               select 1
                 from pg_publication_tables
                where pubname = 'supabase_realtime'
                  and schemaname = 'public'
                  and tablename = v_table
           ) then
            execute format('alter publication supabase_realtime add table public.%I', v_table);
        end if;
    end loop;
end;
$$;
