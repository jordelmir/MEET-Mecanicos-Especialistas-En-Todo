-- Migration: 20260923030000_ride_change_fare_cas_idempotent.sql
-- Description: PR-10: Upgrade ride_change_fare_v1 to be atomic, CAS (Compare-And-Set),
-- and idempotent, enforcing policy minimums and OPEN_BID fare mode bounds.

begin;

create or replace function public.ride_change_fare_v1(
    p_request_id uuid,
    p_fare_minor bigint,
    p_currency text default 'CRC',
    p_expected_version bigint default null,
    p_idempotency_key text default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := (select auth.uid());
    v_request public.ride_requests%rowtype;
    v_min_fare bigint := 0;
    v_request_hash text;
    v_replay jsonb;
    v_response jsonb;
begin
    if v_user_id is null then
        return public.ride_command_error('UNAUTHENTICATED', 'Autenticación requerida', false);
    end if;

    if coalesce(p_fare_minor, 0) <= 0 then
        return public.ride_command_error('INVALID_FARE', 'La tarifa debe ser mayor a cero', false);
    end if;

    if p_idempotency_key is not null and p_idempotency_key <> '' then
        perform pg_catalog.pg_advisory_xact_lock(
            pg_catalog.hashtextextended(v_user_id::text || ':' || p_idempotency_key, 0)
        );
        v_request_hash := public.ride_command_hash(jsonb_build_object(
            'command', 'CHANGE_FARE',
            'trip_id', p_request_id,
            'fare_minor', p_fare_minor,
            'expected_version', p_expected_version
        ));
        v_replay := public.ride_command_replay(v_user_id, p_idempotency_key, v_request_hash);
        if v_replay is not null then
            return v_replay;
        end if;
    end if;

    select * into v_request
    from public.ride_requests
    where id = p_request_id
    for update;

    if not found then
        return public.ride_command_error('REQUEST_NOT_FOUND', 'Solicitud no encontrada', false);
    end if;

    if v_request.passenger_id <> v_user_id then
        return public.ride_command_error('NOT_REQUEST_OWNER', 'Solo el pasajero solicitante puede modificar la oferta', false);
    end if;

    if v_request.state not in ('SEARCHING', 'OFFERED', 'DRAFT') then
        return public.ride_command_error('INVALID_STATE', 'No se puede modificar la tarifa en estado ' || v_request.state, false);
    end if;

    if coalesce(v_request.fare_mode, 'OPEN_BID') = 'METERED_TIME_DISTANCE' then
        return public.ride_command_error('METERED_MODE_IMMUTABLE', 'La tarifa medida no se modifica manualmente; depende del taxímetro oficial', false);
    end if;

    if p_expected_version is not null and v_request.version <> p_expected_version then
        return public.ride_command_error('STALE_VERSION', 'Conflicto de concurrencia: la solicitud fue modificada por otro proceso', false);
    end if;

    -- Authoritative policy minimum check
    select coalesce(minimum_fare_minor, 0) into v_min_fare
    from public.mobility_pricing_policies
    where market_id = 'CR_GAM'
      and service_category_id = 'STD_RIDE'
      and active = true
    limit 1;

    if v_min_fare > 0 and p_fare_minor < v_min_fare then
        return public.ride_command_error('FARE_BELOW_MINIMUM', 'La oferta no puede ser inferior a la tarifa mínima (' || v_min_fare || ')', false);
    end if;

    update public.ride_requests
    set offered_fare_minor = p_fare_minor,
        currency = coalesce(p_currency, currency),
        version = version + 1,
        updated_at = clock_timestamp(),
        fare_breakdown = fare_breakdown || jsonb_build_object(
            'last_modified_at', clock_timestamp(),
            'offered_fare_minor', p_fare_minor,
            'change_reason', 'PASSENGER_BID_ADJUSTMENT'
        )
    where id = p_request_id
    returning * into v_request;

    v_response := public.ride_command_success(jsonb_build_object(
        'request_id', p_request_id,
        'offered_fare_minor', v_request.offered_fare_minor,
        'currency', v_request.currency,
        'version', v_request.version,
        'state', v_request.state
    ));

    if p_idempotency_key is not null and p_idempotency_key <> '' then
        return public.ride_record_command_receipt(
            v_user_id, p_request_id, 'CHANGE_FARE', p_idempotency_key,
            v_request_hash, v_response
        );
    end if;

    return v_response;
end;
$$;

revoke all on function public.ride_change_fare_v1(uuid, bigint, text, bigint, text) from public;
grant execute on function public.ride_change_fare_v1(uuid, bigint, text, bigint, text) to authenticated;

commit;
