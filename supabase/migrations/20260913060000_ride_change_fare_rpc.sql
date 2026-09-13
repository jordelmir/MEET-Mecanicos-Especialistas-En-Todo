-- =============================================================================
-- Migration: 20260913060000_ride_change_fare_rpc.sql
-- Description: Authoritative RPC for passenger in-flight fare/bid adjustments
-- =============================================================================

set check_function_bodies = off;
set search_path = public, auth;

create or replace function public.ride_change_fare_v1(
    p_request_id uuid,
    p_fare_minor bigint,
    p_currency text default 'CRC'
)
returns jsonb
language plpgsql
security definer
as $$
declare
    v_request public.ride_requests%rowtype;
    v_user_id uuid := auth.uid();
begin
    if v_user_id is null then
        return jsonb_build_object('ok', false, 'error', 'NOT_AUTHENTICATED');
    end if;

    select * into v_request
    from public.ride_requests
    where id = p_request_id;

    if not found then
        return jsonb_build_object('ok', false, 'error', 'REQUEST_NOT_FOUND');
    end if;

    if v_request.passenger_id <> v_user_id then
        return jsonb_build_object('ok', false, 'error', 'NOT_REQUEST_OWNER');
    end if;

    if v_request.state not in ('SEARCHING', 'OFFERED') then
        return jsonb_build_object('ok', false, 'error', 'INVALID_STATE', 'state', v_request.state);
    end if;

    if p_fare_minor <= 0 then
        return jsonb_build_object('ok', false, 'error', 'INVALID_FARE');
    end if;

    update public.ride_requests
    set offered_fare_minor = p_fare_minor,
        currency = coalesce(p_currency, currency),
        version = version + 1,
        updated_at = now()
    where id = p_request_id
    returning * into v_request;

    return jsonb_build_object(
        'ok', true,
        'request_id', p_request_id,
        'offered_fare_minor', v_request.offered_fare_minor,
        'version', v_request.version
    );
end;
$$;

grant execute on function public.ride_change_fare_v1(uuid, bigint, text) to authenticated;
