-- Production authority repair: prevent self-claiming and preserve one immutable completion event.
-- Every command remains receipt-idempotent; the event key is the completion command key.

create or replace function public.ride_claim_request_v2(
    p_request_id uuid,
    p_vehicle_id uuid,
    p_expected_version bigint,
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
    v_vehicle public.ride_driver_vehicles%rowtype;
    v_commission bigint;
    v_posted bigint;
    v_reserved bigint;
    v_request_hash text;
    v_replay jsonb;
    v_response jsonb;
    v_quote_version bigint;
    v_from_state text;
begin
    if v_user_id is null then
        return public.ride_command_error(
            'UNAUTHENTICATED', 'Autenticación requerida', false
        );
    end if;
    if coalesce(p_expected_version, 0) <= 0 or
       coalesce(p_idempotency_key, '') !~ '^[A-Za-z0-9._:-]{16,128}$' then
        return public.ride_command_error(
            'VALIDATION_ERROR', 'Versión o idempotency key inválida', false
        );
    end if;

    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(
            v_user_id::text || ':' || p_idempotency_key,
            0
        )
    );

    v_request_hash := public.ride_command_hash(jsonb_build_object(
        'command', 'CLAIM',
        'trip_id', p_request_id,
        'vehicle_id', p_vehicle_id,
        'expected_version', p_expected_version
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
     where r.id = p_request_id
     for update;

    if not found then
        return public.ride_command_error('NOT_FOUND', 'Viaje no encontrado', false);
    end if;
    if v_request.passenger_id = v_user_id then
        return public.ride_command_error(
            'SELF_CLAIM_FORBIDDEN',
            'El pasajero no puede aceptar su propio viaje',
            false
        );
    end if;
    if v_request.assigned_driver_id is not null or
       v_request.state not in ('SEARCHING', 'OFFERED') then
        return public.ride_command_error(
            'ALREADY_ASSIGNED', 'Otro conductor obtuvo el viaje', false
        );
    end if;
    if v_request.version <> p_expected_version then
        return public.ride_command_error(
            'VERSION_CONFLICT',
            'La versión del viaje cambió',
            true,
            jsonb_build_object('current_version', v_request.version)
        );
    end if;
    v_from_state := v_request.state;

    select v.*
      into v_vehicle
      from public.ride_driver_vehicles v
     where v.id = p_vehicle_id
       and v.driver_id = v_user_id
       and v.is_active
       and v.verification_status = 'VERIFIED'
     for update;

    if not found then
        return public.ride_command_error(
            'VEHICLE_NOT_VERIFIED',
            'Se requiere un vehículo activo y verificado',
            false
        );
    end if;

    v_commission := round(
        v_request.offered_fare_minor::numeric * 500::numeric / 10000::numeric
    )::bigint;

    select coalesce(sum(
        case
            when l.direction = 'CREDIT' then l.amount_minor
            when l.direction = 'DEBIT' and
                 l.entry_type <> 'COMMISSION_RESERVED' then -l.amount_minor
            else 0
        end
    ), 0)
      into v_posted
      from public.ride_wallet_ledger l
     where l.driver_id = v_user_id
       and l.currency = v_request.currency;

    select coalesce(sum(r.amount_minor), 0)
      into v_reserved
      from public.ride_commission_reservations r
     where r.driver_id = v_user_id
       and r.currency = v_request.currency
       and r.state = 'RESERVED';

    if v_posted - v_reserved < v_commission then
        return public.ride_command_error(
            'INSUFFICIENT_BALANCE',
            'Saldo insuficiente para aceptar un viaje nuevo',
            false,
            jsonb_build_object(
                'required_minor', v_commission,
                'available_minor', greatest(0, v_posted - v_reserved),
                'currency', v_request.currency
            )
        );
    end if;

    insert into public.ride_commission_calculations(
        trip_id, calculation_kind, idempotency_key,
        commission_policy_version, commission_basis_points,
        commissionable_base_minor, commission_amount_minor,
        rounding_mode, currency, metadata
    )
    values (
        p_request_id, 'ESTIMATE', p_idempotency_key || ':estimate',
        'ride-commission-v1', 500, v_request.offered_fare_minor,
        v_commission, 'HALF_UP', v_request.currency,
        jsonb_build_object('source', 'passenger_offered_fare')
    );

    if v_commission > 0 then
        insert into public.ride_commission_reservations(
            trip_id, driver_id, amount_minor, currency, state,
            reserve_idempotency_key
        )
        values (
            p_request_id, v_user_id, v_commission, v_request.currency,
            'RESERVED', p_idempotency_key || ':reserve'
        );

        insert into public.ride_wallet_ledger(
            driver_id, idempotency_key, entry_type, amount_minor, currency,
            direction, trip_id, withdrawable, metadata
        )
        values (
            v_user_id, p_idempotency_key || ':ledger-reserve',
            'COMMISSION_RESERVED', v_commission, v_request.currency,
            'DEBIT', p_request_id, false,
            jsonb_build_object(
                'commission_policy_version', 'ride-commission-v1',
                'commission_basis_points', 500,
                'commissionable_base_minor', v_request.offered_fare_minor
            )
        );
    end if;

    select coalesce(max(q.quote_version), 0) + 1
      into v_quote_version
      from public.ride_fare_quotes q
     where q.trip_id = p_request_id;

    insert into public.ride_fare_quotes(
        trip_id, quote_version, currency, transport_fare_minor,
        created_by, accepted_by, idempotency_key, payload_version
    )
    values (
        p_request_id, v_quote_version, v_request.currency,
        v_request.offered_fare_minor, v_request.passenger_id,
        v_request.passenger_id, p_idempotency_key || ':accepted-quote', 1
    );

    update public.ride_requests
       set assigned_driver_id = v_user_id,
           assigned_vehicle_id = p_vehicle_id,
           state = 'ASSIGNED',
           version = version + 1,
           updated_at = now()
     where id = p_request_id
       and version = p_expected_version
       and assigned_driver_id is null
       and state in ('SEARCHING', 'OFFERED')
    returning * into v_request;

    if not found then
        raise exception using
            errcode = '40001',
            message = 'Concurrent claim invariant violated';
    end if;

    update public.ride_offers
       set state = case
           when driver_id = v_user_id then 'ACCEPTED'
           else 'REJECTED'
       end,
       updated_at = now()
     where request_id = p_request_id
       and state = 'PENDING';

    insert into public.ride_trip_events(
        trip_id, actor_id, event_type, from_state, to_state,
        payload, idempotency_key
    )
    values (
        p_request_id, v_user_id, 'DRIVER_CLAIMED',
        v_from_state, 'ASSIGNED',
        jsonb_build_object(
            'vehicle_id', p_vehicle_id,
            'commission_reserved_minor', v_commission,
            'commission_basis_points', 500,
            'commission_policy_version', 'ride-commission-v1',
            'currency', v_request.currency,
            'version', v_request.version
        ),
        p_idempotency_key
    );

    v_response := public.ride_command_success(jsonb_build_object(
        'status', 'CLAIMED',
        'trip_id', p_request_id,
        'version', v_request.version,
        'commission_reserved_minor', v_commission,
        'currency', v_request.currency
    ));
    return public.ride_record_command_receipt(
        v_user_id, p_request_id, 'CLAIM', p_idempotency_key,
        v_request_hash, v_response
    );
end;
$$;

revoke all on function public.ride_claim_request_v2(uuid, uuid, bigint, text)
    from public, anon;
grant execute on function public.ride_claim_request_v2(uuid, uuid, bigint, text)
    to authenticated;

-- Resilient 5% commission capture and trip completion for all ride fare modes (metered & open bid).
create or replace function public.ride_complete_trip_v2(
    p_trip_id uuid,
    p_expected_version bigint,
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
    v_quote public.ride_fare_quotes%rowtype;
    v_quote_found boolean := false;
    v_reservation public.ride_commission_reservations%rowtype;
    v_gross numeric;
    v_reductions numeric;
    v_customer_total_numeric numeric;
    v_base bigint;
    v_customer_total bigint;
    v_commission bigint;
    v_delta bigint;
    v_request_hash text;
    v_replay jsonb;
    v_response jsonb;
    v_reservation_found boolean := false;
begin
    if v_user_id is null then
        return public.ride_command_error(
            'UNAUTHENTICATED', 'Autenticación requerida', false
        );
    end if;
    if coalesce(p_expected_version, 0) <= 0 or
       coalesce(p_idempotency_key, '') !~ '^[A-Za-z0-9._:-]{16,128}$' then
        return public.ride_command_error(
            'VALIDATION_ERROR', 'Versión o idempotency key inválida', false
        );
    end if;

    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(
            v_user_id::text || ':' || p_idempotency_key,
            0
        )
    );

    v_request_hash := public.ride_command_hash(jsonb_build_object(
        'command', 'COMPLETE',
        'trip_id', p_trip_id,
        'expected_version', p_expected_version
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
    if v_request.assigned_driver_id <> v_user_id then
        return public.ride_command_error(
            'FORBIDDEN', 'Se requiere el conductor asignado', false
        );
    end if;
    if v_request.version <> p_expected_version then
        return public.ride_command_error(
            'VERSION_CONFLICT', 'La versión del viaje cambió', true,
            jsonb_build_object('current_version', v_request.version)
        );
    end if;
    if v_request.state <> 'IN_PROGRESS' then
        return public.ride_command_error(
            'INVALID_TRANSITION',
            'El viaje debe estar IN_PROGRESS para completar',
            false
        );
    end if;

    select q.*
      into v_quote
      from public.ride_fare_quotes q
     where q.trip_id = p_trip_id
     order by q.quote_version desc
     limit 1
     for share;
    v_quote_found := found;

    if v_quote_found then
        if v_quote.currency <> v_request.currency then
            return public.ride_command_error(
                'CURRENCY_MISMATCH', 'La moneda de la tarifa no coincide', false
            );
        end if;

        v_gross :=
            v_quote.transport_fare_minor::numeric +
            v_quote.approved_wait_minor::numeric +
            v_quote.approved_stops_minor::numeric +
            v_quote.approved_surcharges_minor::numeric +
            v_quote.collected_cancellation_fee_minor::numeric;
        v_reductions :=
            v_quote.driver_funded_discount_minor::numeric +
            v_quote.refunded_transport_minor::numeric;
        v_customer_total_numeric := greatest(
            0::numeric,
            v_gross +
            v_quote.tip_minor::numeric +
            v_quote.tolls_minor::numeric +
            v_quote.taxes_minor::numeric -
            v_reductions -
            v_quote.platform_promotion_minor::numeric
        );

        if v_gross > 9223372036854775807::numeric or
           v_reductions > 9223372036854775807::numeric or
           v_customer_total_numeric > 9223372036854775807::numeric then
            return public.ride_command_error(
                'AMOUNT_OVERFLOW', 'Los importes exceden el rango permitido', false
            );
        end if;

        v_base := greatest(0::numeric, v_gross - v_reductions)::bigint;
        v_customer_total := v_customer_total_numeric::bigint;
    else
        -- Resilient fallback for open bid or direct-negotiated rides without explicit fare quote row
        v_base := greatest(0, coalesce(v_request.final_fare_minor, v_request.offered_fare_minor, 0));
        v_customer_total := v_base;
        v_gross := v_base::numeric;
        v_reductions := 0::numeric;
    end if;

    -- 500 basis points = 5% platform commission
    v_commission := round(
        v_base::numeric * 500::numeric / 10000::numeric
    )::bigint;

    select r.*
      into v_reservation
      from public.ride_commission_reservations r
     where r.trip_id = p_trip_id
     for update;
    v_reservation_found := found;

    if v_reservation_found and
       (
           v_reservation.driver_id <> v_user_id or
           v_reservation.currency <> v_request.currency
       )
    then
        raise exception using
            errcode = '23514',
            message = 'Commission reservation ownership invariant violated';
    end if;
    if v_reservation_found and v_reservation.state <> 'RESERVED' then
        return public.ride_command_error(
            'ALREADY_SETTLED', 'La comisión ya fue liquidada', false
        );
    end if;

    insert into public.ride_commission_calculations(
        trip_id, calculation_kind, idempotency_key,
        commission_policy_version, commission_basis_points,
        commissionable_base_minor, commission_amount_minor,
        rounding_mode, currency, settled_at, metadata
    )
    values (
        p_trip_id, 'FINAL', p_idempotency_key || ':final-calculation',
        'ride-commission-v1', 500, v_base, v_commission,
        'HALF_UP', v_request.currency, now(),
        jsonb_build_object(
            'quote_id', v_quote.id,
            'quote_version', v_quote.quote_version,
            'tip_minor_excluded', coalesce(v_quote.tip_minor, 0),
            'tolls_minor_excluded', coalesce(v_quote.tolls_minor, 0),
            'taxes_minor_excluded', coalesce(v_quote.taxes_minor, 0),
            'platform_promotion_minor_excluded', coalesce(v_quote.platform_promotion_minor, 0),
            'quote_fallback_used', not v_quote_found
        )
    );

    if v_commission > 0 then
        if not v_reservation_found then
            insert into public.ride_commission_reservations(
                trip_id, driver_id, amount_minor, currency, state,
                reserve_idempotency_key
            )
            values (
                p_trip_id, v_user_id, v_commission, v_request.currency,
                'RESERVED', p_idempotency_key || ':late-reserve'
            )
            returning * into v_reservation;

            insert into public.ride_wallet_ledger(
                driver_id, idempotency_key, entry_type, amount_minor,
                currency, direction, trip_id, withdrawable, metadata
            )
            values (
                v_user_id, p_idempotency_key || ':ledger-late-reserve',
                'COMMISSION_RESERVED', v_commission, v_request.currency,
                'DEBIT', p_trip_id, false,
                jsonb_build_object(
                    'commission_policy_version', 'ride-commission-v1',
                    'commissionable_base_minor', v_base
                )
            );
        elsif v_reservation.amount_minor <> v_commission then
            v_delta := abs(v_reservation.amount_minor - v_commission);
            insert into public.ride_wallet_ledger(
                driver_id, idempotency_key, entry_type, amount_minor,
                currency, direction, trip_id, withdrawable, metadata
            )
            values (
                v_user_id,
                p_idempotency_key || case
                    when v_commission > v_reservation.amount_minor
                        then ':ledger-reserve-increase'
                    else ':ledger-reserve-release'
                end,
                case
                    when v_commission > v_reservation.amount_minor
                        then 'COMMISSION_RESERVED'
                    else 'COMMISSION_RELEASED'
                end,
                v_delta,
                v_request.currency,
                case
                    when v_commission > v_reservation.amount_minor
                        then 'DEBIT'
                    else 'CREDIT'
                end,
                p_trip_id,
                false,
                jsonb_build_object(
                    'commission_policy_version', 'ride-commission-v1',
                    'commissionable_base_minor', v_base,
                    'adjustment', true
                )
            );
        end if;

        update public.ride_commission_reservations
           set amount_minor = v_commission,
               state = 'CAPTURED',
               settlement_idempotency_key = p_idempotency_key || ':capture',
               settled_at = now()
         where trip_id = p_trip_id;

        insert into public.ride_wallet_ledger(
            driver_id, idempotency_key, entry_type, amount_minor, currency,
            direction, trip_id, withdrawable, metadata
        )
        values (
            v_user_id, p_idempotency_key || ':ledger-capture',
            'COMMISSION_CAPTURED', v_commission, v_request.currency,
            'DEBIT', p_trip_id, false,
            jsonb_build_object(
                'commission_policy_version', 'ride-commission-v1',
                'commission_basis_points', 500,
                'commissionable_base_minor', v_base,
                'quote_id', v_quote.id
            )
        );
    elsif v_reservation_found and v_reservation.state = 'RESERVED' then
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
            v_user_id, p_idempotency_key || ':ledger-release',
            'COMMISSION_RELEASED', v_reservation.amount_minor,
            v_request.currency, 'CREDIT', p_trip_id, false,
            jsonb_build_object('reason', 'final_commission_zero')
        );
    end if;

    update public.ride_requests
       set state = 'COMPLETED',
           final_fare_minor = v_customer_total,
           version = version + 1,
           updated_at = now(),
           completed_at = now()
     where id = p_trip_id
       and version = p_expected_version
    returning * into v_request;

    if not found then
        return public.ride_command_error(
            'VERSION_CONFLICT', 'La versión del viaje cambió al completar', true
        );
    end if;

    insert into public.ride_trip_events(
        trip_id, actor_id, event_type, from_state, to_state,
        payload, idempotency_key
    )
    values (
        p_trip_id, v_user_id, 'TRIP_COMPLETED', 'IN_PROGRESS', 'COMPLETED',
        jsonb_build_object(
            'quote_id', v_quote.id,
            'final_fare_minor', v_request.final_fare_minor,
            'customer_total_minor', v_customer_total,
            'commission_minor', v_commission,
            'commission_basis_points', 500,
            'commission_policy_version', 'ride-commission-v1',
            'version', v_request.version
        ),
        p_idempotency_key
    );

    v_response := public.ride_command_success(jsonb_build_object(
        'status', 'COMPLETED',
        'state', v_request.state,
        'version', v_request.version,
        'trip_id', v_request.id,
        'final_fare_minor', v_request.final_fare_minor,
        'customer_total_minor', v_customer_total,
        'commission_minor', v_commission,
        'completed_at', v_request.completed_at
    ));
    return public.ride_record_command_receipt(
        v_user_id, p_trip_id, 'COMPLETE', p_idempotency_key,
        v_request_hash, v_response
    );
end;
$$;

revoke all on function public.ride_complete_trip_v2(uuid, bigint, text) from public, anon;
grant execute on function public.ride_complete_trip_v2(uuid, bigint, text) to authenticated;


-- Preserve historical evidence without rewriting a completed trip. Existing malformed
-- self-assigned rows remain auditable; the NOT VALID constraint protects all new writes.
alter table public.ride_requests
    drop constraint if exists ride_requests_no_self_assignment;
alter table public.ride_requests
    add constraint ride_requests_no_self_assignment
    check (assigned_driver_id is null or assigned_driver_id <> passenger_id)
    not valid;

-- Repair historical completions that predate the atomic event append. This is idempotent.
insert into public.ride_trip_events(
    trip_id, actor_id, event_type, from_state, to_state, payload, idempotency_key
)
select
    r.id, r.assigned_driver_id, 'TRIP_COMPLETED', 'IN_PROGRESS', 'COMPLETED',
    jsonb_build_object(
        'backfilled', true, 'final_fare_minor', r.final_fare_minor,
        'completed_at', r.completed_at,
        'source', '20260920110000_ride_authority_and_completion_audit'
    ),
    'system:completion-backfill:' || r.id::text
from public.ride_requests r
where r.state = 'COMPLETED'
  and r.assigned_driver_id is not null
  and not exists (
      select 1 from public.ride_trip_events e
      where e.trip_id = r.id and e.event_type = 'TRIP_COMPLETED'
  );
