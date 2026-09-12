-- Keep the platform owner queue and driver status synchronized immediately.
do $$
begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime'
      and schemaname = 'public'
      and tablename = 'ride_wallet_topups'
  ) then
    alter publication supabase_realtime add table public.ride_wallet_topups;
  end if;
end;
$$;
