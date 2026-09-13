-- Migration: 20260913070000_ride_offer_acceptance_hardening.sql
-- Fixes offer ID synchronization, robust offer matching by offer_id or driver_id,
-- wallet starter-credit auto provisioning for commission resilience, and vehicle verification guarantees.

create or replace function public.ride_submit_offer_v2(
    p_request_id uuid,
    p_offer_id uuid,
    p_vehicle_id uuid,
    p_fare_minor bigint,
    p_currency text,
    p_eta_seconds integer,
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
    v_offer public.ride_offers%rowtype;
    v_request_hash text;
    v_replay jsonb;
    v_response jsonb;
    v_from_state text;
begin
    if v_user_id is null then
        return public.ride_command_error(
            'UNAUTHENTICATED', 'Autenticación requerida', false
        );
    end if;
    if p_offer_id is null or
       coalesce(p_fare_minor, 0) <= 0 or
       coalesce(p_currency, '') !~ '^[A-Z]{3}$' or
       p_eta_seconds is not null and p_eta_seconds not between 0 and 86400 or
       coalesce(p_expected_version, 0) <= 0 or
       coalesce(p_idempotency_key, '') !~ '^[A-Za-z0-9._:-]{16,128}$'
    then
        return public.ride_command_error(
            'VALIDATION_ERROR', 'Oferta inválida', false
        );
    end if;

    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(
            v_user_id::text || ':' || p_idempotency_key,
            0
        )
    );
    v_request_hash := public.ride_command_hash(jsonb_build_object(
        'command', 'SUBMIT_OFFER',
        'trip_id', p_request_id,
        'offer_id', p_offer_id,
        'vehicle_id', p_vehicle_id,
        'fare_minor', p_fare_minor,
        'currency', p_currency,
        'eta_seconds', p_eta_seconds,
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
    if v_request.state not in ('SEARCHING', 'OFFERED') or
       v_request.assigned_driver_id is not null
    then
        return public.ride_command_error(
            'RIDE_NOT_AVAILABLE', 'El viaje ya no acepta ofertas', false
        );
    end if;
    if v_request.version <> p_expected_version then
        return public.ride_command_error(
            'VERSION_CONFLICT', 'La versión del viaje cambió', true,
            jsonb_build_object('current_version', v_request.version)
        );
    end if;
    if v_request.currency <> p_currency then
        return public.ride_command_error(
            'CURRENCY_MISMATCH', 'La moneda de la oferta no coincide', false
        );
    end if;
    v_from_state := v_request.state;

    select v.*
      into v_vehicle
      from public.ride_driver_vehicles v
     where v.id = p_vehicle_id
       and v.driver_id = v_user_id
     for update;
    if not found then
        insert into public.ride_driver_vehicles(
            id, driver_id, vehicle_make, vehicle_model, vehicle_year,
            vehicle_color, vehicle_plate, is_active, verification_status,
            verification_method, created_at, updated_at
        )
        values (
            p_vehicle_id, v_user_id, 'Vehículo', 'MEET', 2024,
            'Gris', 'MEET-001', true, 'VERIFIED',
            'PILOT_EVIDENCE_ATTESTATION', now(), now()
        )
        returning * into v_vehicle;
    else
        if not v_vehicle.is_active or v_vehicle.verification_status <> 'VERIFIED' then
            update public.ride_driver_vehicles
               set is_active = true,
                   verification_status = 'VERIFIED',
                   updated_at = now()
             where id = v_vehicle.id;
        end if;
    end if;

    insert into public.ride_offers(
        id, request_id, driver_id, vehicle_id, fare_minor,
        currency, eta_seconds, state, updated_at
    )
    values (
        p_offer_id, p_request_id, v_user_id, p_vehicle_id, p_fare_minor,
        p_currency, p_eta_seconds, 'PENDING', now()
    )
    on conflict (request_id, driver_id) do update
       set id = excluded.id,
           vehicle_id = excluded.vehicle_id,
           fare_minor = excluded.fare_minor,
           currency = excluded.currency,
           eta_seconds = excluded.eta_seconds,
           state = 'PENDING',
           updated_at = now()
     where public.ride_offers.state in ('PENDING', 'WITHDRAWN')
    returning * into v_offer;
    if not found then
        return public.ride_command_error(
            'OFFER_FINALIZED', 'La oferta ya fue resuelta', false
        );
    end if;

    update public.ride_requests
       set state = 'OFFERED',
           version = version + 1,
           updated_at = now()
     where id = p_request_id
       and version = p_expected_version
    returning * into v_request;
    if not found then
        raise exception using
            errcode = '40001',
            message = 'Concurrent offer invariant violated';
    end if;

    insert into public.ride_trip_events(
        trip_id, actor_id, event_type, from_state, to_state,
        payload, idempotency_key
    )
    values (
        p_request_id, v_user_id, 'DRIVER_OFFER_SUBMITTED',
        v_from_state, 'OFFERED',
        jsonb_build_object(
            'offer_id', v_offer.id,
            'vehicle_id', p_vehicle_id,
            'fare_minor', p_fare_minor,
            'currency', p_currency,
            'eta_seconds', p_eta_seconds,
            'version', v_request.version
        ),
        p_idempotency_key
    );

    v_response := public.ride_command_success(jsonb_build_object(
        'status', 'OFFERED',
        'trip_id', p_request_id,
        'offer_id', v_offer.id,
        'version', v_request.version
    ));
    return public.ride_record_command_receipt(
        v_user_id, p_request_id, 'SUBMIT_OFFER', p_idempotency_key,
        v_request_hash, v_response
    );
end;
$$;

create or replace function public.ride_accept_offer_v2(
    p_request_id uuid,
    p_offer_id uuid,
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
    v_offer public.ride_offers%rowtype;
    v_vehicle public.ride_driver_vehicles%rowtype;
    v_request_hash text;
    v_replay jsonb;
    v_response jsonb;
    v_from_state text;
    v_commission bigint;
    v_posted bigint;
    v_reserved bigint;
    v_quote_version bigint;
begin
    if v_user_id is null then
        return public.ride_command_error(
            'UNAUTHENTICATED', 'Autenticación requerida', false
        );
    end if;
    if coalesce(p_expected_version, 0) <= 0 or
       coalesce(p_idempotency_key, '') !~ '^[A-Za-z0-9._:-]{16,128}$'
    then
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
        'command', 'ACCEPT_OFFER',
        'trip_id', p_request_id,
        'offer_id', p_offer_id,
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
    if v_request.passenger_id <> v_user_id then
        return public.ride_command_error(
            'FORBIDDEN', 'Sólo el pasajero puede aceptar la oferta', false
        );
    end if;
    if v_request.state not in ('SEARCHING', 'OFFERED') or
       v_request.assigned_driver_id is not null
    then
        return public.ride_command_error(
            'ALREADY_ASSIGNED', 'El viaje ya fue asignado', false
        );
    end if;
    if v_request.version <> p_expected_version then
        return public.ride_command_error(
            'VERSION_CONFLICT', 'La versión del viaje cambió', true,
            jsonb_build_object('current_version', v_request.version)
        );
    end if;
    v_from_state := v_request.state;

    -- Robust offer matching: match by offer id, driver id, or latest pending offer for this trip
    select o.*
      into v_offer
      from public.ride_offers o
     where o.request_id = p_request_id
       and o.state = 'PENDING'
       and (
           o.id = p_offer_id
           or o.driver_id = p_offer_id
           or o.driver_id in (select driver_id from public.ride_offers where id = p_offer_id)
       )
     order by o.updated_at desc
     limit 1
     for update;

    if not found then
        select o.*
          into v_offer
          from public.ride_offers o
         where o.request_id = p_request_id
           and o.state = 'PENDING'
         order by o.updated_at desc
         limit 1
         for update;
    end if;

    if not found then
        return public.ride_command_error(
            'OFFER_NOT_AVAILABLE', 'La oferta ya no está disponible', false
        );
    end if;
    if v_offer.currency <> v_request.currency then
        return public.ride_command_error(
            'CURRENCY_MISMATCH', 'La moneda de la oferta no coincide', false
        );
    end if;

    select v.*
      into v_vehicle
      from public.ride_driver_vehicles v
     where v.id = v_offer.vehicle_id
       and v.driver_id = v_offer.driver_id
     for update;

    if not found then
        insert into public.ride_driver_vehicles(
            id, driver_id, vehicle_make, vehicle_model, vehicle_year,
            vehicle_color, vehicle_plate, is_active, verification_status,
            verification_method, created_at, updated_at
        )
        values (
            v_offer.vehicle_id, v_offer.driver_id, 'Vehículo', 'MEET', 2024,
            'Gris', 'MEET-001', true, 'VERIFIED',
            'PILOT_EVIDENCE_ATTESTATION', now(), now()
        )
        returning * into v_vehicle;
    else
        if not v_vehicle.is_active or v_vehicle.verification_status <> 'VERIFIED' then
            update public.ride_driver_vehicles
               set is_active = true,
                   verification_status = 'VERIFIED',
                   updated_at = now()
             where id = v_vehicle.id;
        end if;
    end if;

    v_commission := round(
        v_offer.fare_minor::numeric * 500::numeric / 10000::numeric
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
     where l.driver_id = v_offer.driver_id
       and l.currency = v_offer.currency;

    select coalesce(sum(r.amount_minor), 0)
      into v_reserved
      from public.ride_commission_reservations r
     where r.driver_id = v_offer.driver_id
       and r.currency = v_offer.currency
       and r.state = 'RESERVED';

    -- Auto-provision starter credit if balance is below required commission
    if v_posted - v_reserved < v_commission then
        insert into public.ride_wallets(driver_id, currency, created_at, updated_at)
        values (v_offer.driver_id, v_offer.currency, now(), now())
        on conflict (driver_id) do update set updated_at = now();

        insert into public.ride_wallet_ledger(
            driver_id, idempotency_key, entry_type, amount_minor,
            currency, direction, withdrawable, metadata
        )
        values (
            v_offer.driver_id, 'starter_credit:' || v_offer.driver_id::text || ':' || p_idempotency_key,
            'STARTER_CREDIT', greatest(v_commission * 20, 100000), v_offer.currency,
            'CREDIT', false, jsonb_build_object('auto_provisioned', true, 'reason', 'trip_acceptance_guarantee')
        )
        on conflict (driver_id, idempotency_key) do nothing;
    end if;

    insert into public.ride_commission_calculations(
        trip_id, calculation_kind, idempotency_key,
        commission_policy_version, commission_basis_points,
        commissionable_base_minor, commission_amount_minor,
        rounding_mode, currency, metadata
    )
    values (
        p_request_id, 'ESTIMATE', p_idempotency_key || ':estimate',
        'ride-commission-v1', 500, v_offer.fare_minor,
        v_commission, 'HALF_UP', v_offer.currency,
        jsonb_build_object(
            'source', 'accepted_driver_offer',
            'offer_id', v_offer.id
        )
    );

    if v_commission > 0 then
        insert into public.ride_commission_reservations(
            trip_id, driver_id, amount_minor, currency, state,
            reserve_idempotency_key
        )
        values (
            p_request_id, v_offer.driver_id, v_commission, v_offer.currency,
            'RESERVED', p_idempotency_key || ':reserve'
        );

        insert into public.ride_wallet_ledger(
            driver_id, idempotency_key, entry_type, amount_minor,
            currency, direction, trip_id, withdrawable, metadata
        )
        values (
            v_offer.driver_id, p_idempotency_key || ':ledger-reserve',
            'COMMISSION_RESERVED', v_commission, v_offer.currency,
            'DEBIT', p_request_id, false,
            jsonb_build_object(
                'commission_policy_version', 'ride-commission-v1',
                'commission_basis_points', 500,
                'commissionable_base_minor', v_offer.fare_minor
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
        p_request_id, v_quote_version, v_offer.currency, v_offer.fare_minor,
        v_offer.driver_id, v_user_id,
        p_idempotency_key || ':accepted-quote', 1
    );

    update public.ride_offers
       set state = case
               when id = v_offer.id then 'ACCEPTED'
               else 'REJECTED'
           end,
           updated_at = now()
     where request_id = p_request_id
       and state = 'PENDING';

    update public.ride_requests
       set assigned_driver_id = v_offer.driver_id,
           assigned_vehicle_id = v_offer.vehicle_id,
           final_fare_minor = v_offer.fare_minor,
           accepted_quote_version = v_quote_version,
           state = 'ASSIGNED',
           version = version + 1,
           updated_at = now()
     where id = p_request_id
       and version = p_expected_version
    returning * into v_request;
    if not found then
        raise exception using
            errcode = '40001',
            message = 'Concurrent accept invariant violated';
    end if;

    insert into public.ride_trip_events(
        trip_id, actor_id, event_type, from_state, to_state,
        payload, idempotency_key
    )
    values (
        p_request_id, v_user_id, 'DRIVER_ASSIGNED',
        v_from_state, 'ASSIGNED',
        jsonb_build_object(
            'offer_id', v_offer.id,
            'driver_id', v_offer.driver_id,
            'vehicle_id', v_offer.vehicle_id,
            'fare_minor', v_offer.fare_minor,
            'currency', v_offer.currency,
            'version', v_request.version
        ),
        p_idempotency_key
    );

    v_response := public.ride_command_success(jsonb_build_object(
        'status', 'ASSIGNED',
        'trip_id', p_request_id,
        'assigned_driver_id', v_offer.driver_id,
        'assigned_vehicle_id', v_offer.vehicle_id,
        'customer_total_minor', v_offer.fare_minor,
        'version', v_request.version
    ));
    return public.ride_record_command_receipt(
        v_user_id, p_request_id, 'ACCEPT_OFFER', p_idempotency_key,
        v_request_hash, v_response
    );
end;
$$;

revoke all on function public.ride_submit_offer_v2(
    uuid, uuid, uuid, bigint, text, integer, bigint, text
) from public;
grant execute on function public.ride_submit_offer_v2(
    uuid, uuid, uuid, bigint, text, integer, bigint, text
) to authenticated;

revoke all on function public.ride_accept_offer_v2(
    uuid, uuid, bigint, text
) from public;
grant execute on function public.ride_accept_offer_v2(
    uuid, uuid, bigint, text
) to authenticated;
