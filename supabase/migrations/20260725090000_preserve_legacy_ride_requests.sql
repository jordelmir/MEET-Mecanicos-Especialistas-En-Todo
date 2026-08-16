-- Preserve the original Vanguard repair-adjacent ride request contract before
-- installing the authoritative MEET rides platform. Renaming is lossless and
-- keeps dependent PostgreSQL objects attached to the legacy relation by OID.

do $$
begin
    if to_regclass('public.ride_requests') is not null
       and not exists (
           select 1
             from information_schema.columns
            where table_schema = 'public'
              and table_name = 'ride_requests'
              and column_name = 'passenger_id'
       )
       and to_regclass('public.vanguard_legacy_ride_requests') is null
    then
        alter table public.ride_requests
            rename to vanguard_legacy_ride_requests;
        comment on table public.vanguard_legacy_ride_requests is
            'Preserved pre-rides Vanguard request contract. Do not use for new MEET mobility trips.';
    end if;
end;
$$;
