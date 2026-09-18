-- Preserve older clients while making the financial close authoritative.
-- The v2 command captures the 5% commission and changes the trip in one transaction.
create or replace function public.complete_ride(
    p_ride_id uuid,
    p_expected_version bigint,
    p_idempotency_key uuid
)
returns jsonb
language plpgsql
security invoker
set search_path = ''
as $$
begin
    return public.ride_complete_trip_v2(
        p_ride_id,
        p_expected_version,
        p_idempotency_key::text
    );
end;
$$;

revoke all on function public.complete_ride(uuid, bigint, uuid) from public, anon;
grant execute on function public.complete_ride(uuid, bigint, uuid) to authenticated;
