-- Enforce 5% penalty fee if driver cancels after arriving at pickup.
-- If the passenger cancels, no penalty is charged to the driver.

create or replace function public.ride_cancel_trip_v2(
    p_trip_id uuid,
    p_expected_version bigint,
    p_reason_code text,
    p_detail text,
    p_idempotency_key text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := (select auth.uid());
    v_request public.ride_requests%rowtype;
    v_reservation public.ride_commission_reservations%rowtype;
    v_reason text := upper(trim(p_reason_code));
    v_request_hash text;
    v_replay jsonb;
    v_response jsonb;
    v_safety boolean;
    v_from_state text;
    v_reservation_found boolean := false;
    v_reservation_released boolean := false;
    v_driver_cancelling_penalty boolean := false;
    v_penalty_minor bigint := 0;
begin
    if v_user_id is null then
        return public.ride_command_error(
            'UNAUTHENTICATED', 'Autenticación requerida', false
        );
    end if;
    if coalesce(p_expected_version, 0) <= 0 or
       coalesce(p_idempotency_key, '') !~ '^[A-Za-z0-9._:-]{16,128}$' or
       coalesce(v_reason, '') not in (
           'SAFETY_CONCERN', 'UNACCOMPANIED_MINOR', 'CHILD_SEAT_REQUIRED',
           'TOO_MANY_PASSENGERS', 'IDENTITY_MISMATCH', 'VEHICLE_MISMATCH',
           'HARASSMENT', 'PROHIBITED_ITEM_OR_ACTIVITY', 'DANGEROUS_PICKUP',
           'MEDICAL_EMERGENCY', 'UNSAFE_VEHICLE_CONDITION',
           'PASSENGER_NO_SHOW', 'DRIVER_NO_SHOW', 'EXCESSIVE_WAIT',
           'INCORRECT_PICKUP', 'INCORRECT_DESTINATION', 'CHANGE_OF_PLANS',
           'DUPLICATE_OR_ACCIDENTAL', 'OTHER'
       ) or
       char_length(coalesce(p_detail, '')) > 500 or
       (v_reason = 'OTHER' and nullif(trim(coalesce(p_detail, '')), '') is null)
    then
        return public.ride_command_error(
            'VALIDATION_ERROR', 'Datos de cancelación inválidos', false
        );
    end if;

    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(
            v_user_id::text || ':' || p_idempotency_key,
            0
        )
    );

    v_request_hash := public.ride_command_hash(jsonb_build_object(
        'command', 'CANCEL',
        'trip_id', p_trip_id,
        'expected_version', p_expected_version,
        'reason_code', v_reason,
        'detail', nullif(trim(coalesce(p_detail, '')), '')
    ));
    v_replay := public.ride_command_replay(
        v_user_id, p_idempotency_key, v_request_hash
    );
    if v_replay is not null then
        return v_replay;
    end if;

    select r.*
      into v_request
      from public.ride_requests r
     where r.id = p_trip_id
     for update;

    if not found then
        return public.ride_command_error('NOT_FOUND', 'Viaje no encontrado', false);
    end if;
    if v_user_id <> v_request.passenger_id and
       v_user_id is distinct from v_request.assigned_driver_id then
        return public.ride_command_error(
            'FORBIDDEN', 'Actor no autorizado para este viaje', false
        );
    end if;
    if v_request.version <> p_expected_version then
        return public.ride_command_error(
            'VERSION_CONFLICT', 'La versión del viaje cambió', true,
            jsonb_build_object('current_version', v_request.version)
        );
    end if;
    if v_request.state in ('COMPLETED', 'CANCELLED', 'EXPIRED', 'DISPUTED') then
        return public.ride_command_error(
            'TERMINAL_STATE', 'El viaje está en un estado terminal', false
        );
    end if;
    v_from_state := v_request.state;

    v_safety := v_reason in (
        'SAFETY_CONCERN', 'UNACCOMPANIED_MINOR', 'CHILD_SEAT_REQUIRED',
        'TOO_MANY_PASSENGERS', 'IDENTITY_MISMATCH', 'VEHICLE_MISMATCH',
        'HARASSMENT', 'PROHIBITED_ITEM_OR_ACTIVITY', 'DANGEROUS_PICKUP',
        'MEDICAL_EMERGENCY', 'UNSAFE_VEHICLE_CONDITION'
    );

    insert into public.ride_cancellations(
        trip_id, actor_id, reason_code, detail, requires_safety_review
    )
    values (
        p_trip_id, v_user_id, v_reason,
        nullif(trim(coalesce(p_detail, '')), ''), v_safety
    );

    if v_safety then
        insert into public.ride_operational_holds(
            trip_id, hold_type, reason_code, requested_by, source_state, metadata
        )
        values (
            p_trip_id, 'SAFETY_REVIEW', v_reason, v_user_id, v_from_state,
            jsonb_build_object(
                'source', 'ride_cancel_trip_v2',
                'detail_provided',
                    nullif(trim(coalesce(p_detail, '')), '') is not null
            )
        );
    end if;

    select r.*
      into v_reservation
     from public.ride_commission_reservations r
     where r.trip_id = p_trip_id
     for update;
    v_reservation_found := found;

    -- Check if driver is cancelling the trip (penalty applies to all driver cancellations)
    v_driver_cancelling_penalty := (
        v_user_id = v_request.assigned_driver_id
    );

    if v_driver_cancelling_penalty then
        -- Driver cancelled: capture 5% penalty fee
        v_penalty_minor := coalesce(
            v_reservation.amount_minor,
            round(coalesce(v_request.final_fare_minor, v_request.offered_fare_minor, 0) * 0.05)::bigint
        );

        if v_reservation_found and v_reservation.state = 'RESERVED' then
            update public.ride_commission_reservations
               set state = 'CAPTURED',
                   settlement_idempotency_key = p_idempotency_key || ':penalty-capture',
                   settled_at = now()
             where trip_id = p_trip_id;
        end if;

        insert into public.ride_wallet_ledger(
            driver_id, idempotency_key, entry_type, amount_minor, currency,
            direction, trip_id, withdrawable, metadata
        )
        values (
            v_request.assigned_driver_id,
            p_idempotency_key || ':ledger-cancel-penalty',
            'COMMISSION_CAPTURED',
            v_penalty_minor,
            v_request.currency,
            'DEBIT',
            p_trip_id,
            false,
            jsonb_build_object(
                'commission_policy_version', 'ride-commission-v1',
                'reason', 'driver_cancellation_penalty_5pct',
                'cancellation_reason', v_reason,
                'from_state', v_from_state
            )
        );
    else
        -- Passenger cancelled OR driver cancelled before arrival: release reservation without deduction
        if v_reservation_found and v_reservation.state = 'RESERVED' then
            update public.ride_commission_reservations
               set state = 'RELEASED',
                   settlement_idempotency_key = p_idempotency_key || ':release',
                   settled_at = now()
             where trip_id = p_trip_id;

            insert into public.ride_wallet_ledger(
                driver_id, idempotency_key, entry_type, amount_minor, currency,
                direction, trip_id, withdrawable, metadata
            )
            values (
                v_reservation.driver_id,
                p_idempotency_key || ':ledger-release',
                'COMMISSION_RELEASED',
                v_reservation.amount_minor,
                v_reservation.currency,
                'CREDIT',
                p_trip_id,
                false,
                jsonb_build_object(
                    'commission_policy_version', 'ride-commission-v1',
                    'reason', 'trip_cancelled_released'
                )
            );
            v_reservation_released := true;
        end if;
    end if;

    update public.ride_requests
       set state = 'CANCELLED',
           version = version + 1,
           updated_at = now(),
           cancelled_at = now()
     where id = p_trip_id
       and version = p_expected_version
    returning * into v_request;

    if not found then
        raise exception using
            errcode = '40001',
            message = 'Concurrent cancellation invariant violated';
    end if;

    insert into public.ride_trip_events(
        trip_id, actor_id, event_type, from_state, to_state,
        payload, idempotency_key
    )
    values (
        p_trip_id, v_user_id, 'TRIP_CANCELLED',
        v_from_state, 'CANCELLED',
        jsonb_build_object(
            'reason_code', v_reason,
            'requires_safety_review', v_safety,
            'automatic_fee_minor', v_penalty_minor,
            'driver_penalty_applied', v_driver_cancelling_penalty,
            'version', v_request.version
        ),
        p_idempotency_key
    );

    v_response := public.ride_command_success(jsonb_build_object(
        'status', 'CANCELLED',
        'trip_id', p_trip_id,
        'version', v_request.version,
        'reservation_released', v_reservation_released,
        'driver_penalty_minor', v_penalty_minor
    ));
    return public.ride_record_command_receipt(
        v_user_id, p_trip_id, 'CANCEL', p_idempotency_key,
        v_request_hash, v_response
    );
end;
$$;

revoke all on function public.ride_cancel_trip_v2(uuid, bigint, text, text, text) from public, anon;
grant execute on function public.ride_cancel_trip_v2(uuid, bigint, text, text, text) to authenticated;
