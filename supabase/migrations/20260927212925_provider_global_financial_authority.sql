-- Existing wallet and append-only double-entry journal become principal-global.
-- FK broadening changes eligibility storage, not provider trust.
alter table public.ride_wallets drop constraint if exists ride_wallets_driver_id_fkey;
alter table public.ride_wallets add constraint ride_wallets_driver_id_fkey foreign key(driver_id) references auth.users(id) on delete restrict;
alter table public.service_provider_wallets alter column balance_minor set default 0;
update public.ride_wallet_policy set starter_credit_minor=5000,commission_basis_points=500 where policy_id=true;

create table if not exists public.provider_starter_grants (
 principal_id uuid primary key references auth.users(id) on delete restrict,
 policy_code text not null default 'ELY_PROVIDER_STARTER_V1',
 state text not null check(state in ('GRANTED','ALREADY_GRANTED')),
 amount_minor bigint not null check(amount_minor>=0),
 currency text not null default 'CRC' check(currency='CRC'),
 source text not null,
 created_at timestamptz not null default now()
);
create table if not exists public.service_financial_contracts (
 contract_id uuid primary key default gen_random_uuid(),
 vertical text not null check(vertical in ('UNIVERSAL_SERVICE','COMMERCE_DELIVERY')),
 aggregate_id uuid not null,
 customer_id uuid not null references auth.users(id),
 provider_id uuid not null references auth.users(id),
 currency text not null check(currency='CRC'),
 gross_service_minor bigint not null check(gross_service_minor>=0),
 commissionable_base_minor bigint not null check(commissionable_base_minor>=0),
 commission_basis_points integer not null default 500 check(commission_basis_points=500),
 commission_minor bigint not null check(commission_minor>=0),
 payment_method text not null check(payment_method in ('CASH','SINPE_MOVIL')),
 policy_code text not null default 'ELY_PROVIDER_COMMISSION_V1',
 state text not null check(state in ('RESERVED','CAPTURED','RELEASED','LEGACY_CAPTURED')),
 created_at timestamptz not null default now(),
 settled_at timestamptz,
 unique(vertical,aggregate_id)
);
-- Feature flags default fail-closed for unimplemented payment rails.
create table if not exists public.elysium_financial_capabilities (
 capability text primary key,
 enabled boolean not null default false,
 updated_at timestamptz not null default now()
);
insert into public.elysium_financial_capabilities(capability,enabled) values
 ('PROVIDER_COMMISSION_WALLET',true),('PROVIDER_STARTER_GRANTS',true),
 ('GENERAL_STORED_VALUE',false),('P2P',false),('CASH_OUT',false),
 ('COMMERCE_PAYMENTS',false),('SINPE_AUTOMATIC_CONFIRMATION',false)
on conflict(capability) do nothing;

alter table public.provider_starter_grants enable row level security;
alter table public.service_financial_contracts enable row level security;
alter table public.elysium_financial_capabilities enable row level security;
revoke all on public.provider_starter_grants,public.service_financial_contracts,public.elysium_financial_capabilities from public,anon,authenticated;
grant select on public.provider_starter_grants,public.service_financial_contracts,public.elysium_financial_capabilities to authenticated;
drop policy if exists provider_starter_owner_read on public.provider_starter_grants;
create policy provider_starter_owner_read on public.provider_starter_grants for select to authenticated using(principal_id=auth.uid());
drop policy if exists service_financial_participant_read on public.service_financial_contracts;
create policy service_financial_participant_read on public.service_financial_contracts for select to authenticated using(customer_id=auth.uid() or provider_id=auth.uid());
drop policy if exists financial_capabilities_read on public.elysium_financial_capabilities;
create policy financial_capabilities_read on public.elysium_financial_capabilities for select to authenticated using(true);

-- Existing grants consume the once-per-principal entitlement without changing their amounts.
insert into public.provider_starter_grants(principal_id,state,amount_minor,source)
select driver_id,'ALREADY_GRANTED',sum(amount_minor),'HISTORICAL_RIDE_LEDGER'
from public.ride_wallet_ledger where direction='CREDIT' and entry_type='PROMOTIONAL_GRANT'
group by driver_id on conflict(principal_id) do nothing;
insert into public.provider_starter_grants(principal_id,state,amount_minor,source)
select w.user_id,'ALREADY_GRANTED',sum(l.amount_minor),'HISTORICAL_SERVICE_LEDGER'
from public.service_provider_wallet_ledger l join public.service_provider_wallets w using(provider_id)
where l.direction='CREDIT' and l.entry_type='WELCOME_BONUS'
group by w.user_id on conflict(principal_id) do nothing;

create or replace function public.elysium_provider_eligible_v1(p_principal uuid)
returns boolean language sql stable security definer set search_path='' as $$
 select exists(select 1 from public.provider_profiles p join public.user_profiles u on u.id=p.user_profile_id
 where u.auth_user_id=p_principal and p.is_verified and p.is_active and p.status='active');
$$;

create or replace function public.elysium_provider_wallet_ensure_v1(p_principal uuid)
returns jsonb language plpgsql security definer set search_path='' as $$
declare g public.provider_starter_grants%rowtype;
begin
 if not exists(select 1 from public.elysium_financial_capabilities where capability='PROVIDER_COMMISSION_WALLET' and enabled) then raise exception 'PROVIDER_WALLET_DISABLED'; end if;
 if p_principal is null or not public.elysium_provider_eligible_v1(p_principal) then
  raise exception 'VERIFIED_PROVIDER_REQUIRED' using errcode='42501'; end if;
 perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('provider-finance:'||p_principal::text,0));
 insert into public.ride_wallets(driver_id,currency) values(p_principal,'CRC') on conflict(driver_id) do nothing;
 perform 1 from public.ride_wallets where driver_id=p_principal and currency='CRC' for update;
 if not found then raise exception 'WALLET_CURRENCY_UNAVAILABLE'; end if;
 -- Recheck history inside the lock, including legacy entitlement created before this migration.
 if not exists(select 1 from public.provider_starter_grants where principal_id=p_principal) then
  if exists(select 1 from public.ride_wallet_ledger where driver_id=p_principal and direction='CREDIT' and entry_type='PROMOTIONAL_GRANT')
   or exists(select 1 from public.service_provider_wallet_ledger l join public.service_provider_wallets w using(provider_id)
      where w.user_id=p_principal and l.direction='CREDIT' and l.entry_type='WELCOME_BONUS') then
   insert into public.provider_starter_grants(principal_id,state,amount_minor,source) values(p_principal,'ALREADY_GRANTED',0,'HISTORICAL_ENTITLEMENT');
  else
   if not exists(select 1 from public.elysium_financial_capabilities where capability='PROVIDER_STARTER_GRANTS' and enabled) then raise exception 'STARTER_GRANTS_DISABLED'; end if;
   insert into public.provider_starter_grants(principal_id,state,amount_minor,source) values(p_principal,'GRANTED',5000,'ELY_PROVIDER_STARTER_V1');
   insert into public.ride_wallet_ledger(driver_id,idempotency_key,entry_type,amount_minor,currency,direction,withdrawable,metadata)
   values(p_principal,'provider-starter-v1:'||p_principal::text,'PROMOTIONAL_GRANT',5000,'CRC','CREDIT',false,
    jsonb_build_object('policy','ELY_PROVIDER_STARTER_V1','fund_class','PROMOTIONAL','payment_eligible',false,'transferable',false,'withdrawable',false));
  end if;
 end if;
 select * into g from public.provider_starter_grants where principal_id=p_principal;
 return jsonb_build_object('status',g.state,'credited_minor',case when g.state='GRANTED' then g.amount_minor else 0 end,'currency','CRC');
end; $$;

-- Internal projection includes cross-vertical reserves. Release is not a fresh credit:
-- reservation entries were never subtracted from posted balance in the compatibility ledger.
create or replace function public.elysium_provider_balance_v1(p_principal uuid)
returns jsonb language plpgsql security definer set search_path='' as $$
declare posted bigint; reserved bigint; promotional bigint; consumed bigint;
begin
 perform 1 from public.ride_wallets where driver_id=p_principal and currency='CRC' for update;
 select coalesce(sum(case when direction='CREDIT' and entry_type<>'COMMISSION_RELEASED' then amount_minor
 when direction='DEBIT' and entry_type<>'COMMISSION_RESERVED' then -amount_minor else 0 end),0),
 coalesce(sum(case when direction='CREDIT' and entry_type='PROMOTIONAL_GRANT' then amount_minor else 0 end),0),
 coalesce(sum(case when direction='DEBIT' and entry_type<>'COMMISSION_RESERVED' then amount_minor else 0 end),0)
 into posted,promotional,consumed from public.ride_wallet_ledger where driver_id=p_principal and currency='CRC';
 select coalesce(sum(amount_minor),0) into reserved from public.ride_commission_reservations where driver_id=p_principal and currency='CRC' and state='RESERVED';
 reserved:=reserved+coalesce((select sum(commission_minor) from public.service_financial_contracts where provider_id=p_principal and state='RESERVED'),0);
 promotional:=greatest(0,promotional-consumed);
 return jsonb_build_object('currency','CRC','posted_minor',posted,'reserved_minor',reserved,'available_minor',greatest(0,posted-reserved),
 'promotional_available_minor',greatest(0,promotional-reserved),'funded_available_minor',greatest(0,posted-greatest(promotional,reserved)));
end; $$;
create or replace function public.ride_wallet_balance_v1() returns jsonb language plpgsql security definer set search_path='' as $$
begin
 if auth.uid() is null then raise exception 'UNAUTHENTICATED' using errcode='42501'; end if;
 return public.elysium_provider_balance_v1(auth.uid());
end; $$;
create or replace function public.ride_wallet_ensure_starter_credit_v1() returns jsonb language plpgsql security definer set search_path='' as $$
begin
 if auth.uid() is null then raise exception 'UNAUTHENTICATED' using errcode='42501'; end if;
 return public.elysium_provider_wallet_ensure_v1(auth.uid());
end; $$;
create or replace function public.service_provider_wallet_ensure_starter_credit_v1(p_provider_id text,p_business_name text default 'Proveedor de Servicios',p_category text default 'AUTOMOTIVE_MECHANIC')
returns jsonb language plpgsql security definer set search_path='' as $$
begin
 if auth.uid() is null or p_provider_id<>auth.uid()::text then raise exception 'OWNER_REQUIRED' using errcode='42501'; end if;
 perform public.elysium_provider_wallet_ensure_v1(auth.uid());
 return public.elysium_provider_balance_v1(auth.uid());
end; $$;

-- Direct arbitrary confirmed credits and per-service immediate deductions are retired.
create or replace function public.service_provider_wallet_topup_v1(p_provider_id text,p_amount_minor bigint,p_reference text default 'SINPE-MOVIL')
returns jsonb language plpgsql security definer set search_path='' as $$
begin raise exception 'RECONCILED_TOPUP_REQUIRED' using errcode='42501'; end; $$;
create or replace function public.ride_driver_wallet_credit_v1(p_driver_id uuid,p_amount_minor bigint,p_currency text default 'CRC',p_reason text default 'Wallet credit')
returns jsonb language plpgsql security definer set search_path='' as $$
begin raise exception 'RECONCILED_TOPUP_REQUIRED' using errcode='42501'; end; $$;
create or replace function public.service_provider_wallet_deduct_fee_v1(p_provider_id text,p_service_id text,p_gross_amount_minor bigint,p_service_category text default 'GENERAL_SERVICE')
returns jsonb language plpgsql security definer set search_path='' as $$
begin raise exception 'AUTHORITATIVE_SERVICE_TRANSITION_REQUIRED' using errcode='42501'; end; $$;
create or replace function public.universal_service_wallet_transfer_v1(p_transfer_id uuid,p_amount_minor bigint)
returns jsonb language plpgsql security definer set search_path='' as $$
begin raise exception 'UNIFIED_WALLET_TRANSFER_UNNECESSARY' using errcode='42501'; end; $$;

-- Additive authority overrides captured from live project kluumjhzncitjayvvwtj.
-- No historical ledger/grant/settlement rows are rewritten.
CREATE OR REPLACE FUNCTION public.ride_accept_offer_v2(p_request_id uuid, p_offer_id uuid, p_expected_version bigint, p_idempotency_key text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
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
    v_wallet_projection jsonb;
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

    select o.* into v_offer from public.ride_offers o
    where o.request_id=p_request_id and o.id=p_offer_id and o.state='PENDING' for update;

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

    if not found or not v_vehicle.is_active or v_vehicle.verification_status <> 'VERIFIED' then
        return public.ride_command_error('VERIFIED_ACTIVE_VEHICLE_REQUIRED', 'Vehículo activo verificado requerido', false);
    end if;
    perform public.elysium_provider_wallet_ensure_v1(v_offer.driver_id);
    perform 1 from public.ride_wallets where driver_id=v_offer.driver_id for update;
    v_commission := public.elysium_commission_minor_v1(v_offer.fare_minor,500);

    v_wallet_projection:=public.elysium_provider_balance_v1(v_offer.driver_id);
    v_posted:=(v_wallet_projection->>'posted_minor')::bigint;
    v_reserved:=(v_wallet_projection->>'reserved_minor')::bigint;

    if v_posted - v_reserved < v_commission then
        return public.ride_command_error('INSUFFICIENT_PROVIDER_COMMISSION_BALANCE', 'Saldo insuficiente para comisión', false);
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
$function$;

CREATE OR REPLACE FUNCTION public.ride_cancel_trip_v2(p_trip_id uuid, p_expected_version bigint, p_reason_code text, p_detail text, p_idempotency_key text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
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
$function$;

create or replace function public.universal_service_transition_v1(p_request_id uuid,p_action text,p_offer_id uuid default null)
returns public.universal_service_requests language plpgsql security definer set search_path='' as $$
declare a uuid:=auth.uid(); r public.universal_service_requests%rowtype; o public.universal_service_offers%rowtype;
 c public.service_financial_contracts%rowtype; fee bigint; b jsonb; method text;
begin
 if a is null then raise exception 'AUTHENTICATION_REQUIRED' using errcode='42501'; end if;
 select * into r from public.universal_service_requests where id=p_request_id for update;
 if not found then raise exception 'SERVICE_NOT_FOUND' using errcode='P0002'; end if;
 if p_action='ACCEPT' then
  if a<>r.client_id then raise exception 'CLIENT_REQUIRED' using errcode='42501'; end if;
  if r.state='ASSIGNED' and r.accepted_offer_id=p_offer_id then return r; end if;
  if r.state<>'OPEN' then raise exception 'SERVICE_NOT_OPEN'; end if;
  select * into o from public.universal_service_offers where id=p_offer_id and request_id=r.id for update;
  if not found or o.state<>'PENDING' or o.currency<>r.currency or o.provider_id=a
   or not public.universal_service_provider_eligible_v1(o.provider_id) then raise exception 'OFFER_NOT_ELIGIBLE'; end if;
  if r.currency<>'CRC' then raise exception 'WALLET_CURRENCY_UNAVAILABLE'; end if;
  method:=upper(coalesce(nullif(r.intake->>'payment_method',''),'CASH'));
  if method='SINPE' then method:='SINPE_MOVIL'; end if;
  if method not in ('CASH','SINPE_MOVIL') then raise exception 'PAYMENT_RAIL_UNAVAILABLE'; end if;
  perform public.elysium_provider_wallet_ensure_v1(o.provider_id);
  b:=public.elysium_provider_balance_v1(o.provider_id);
  fee:=public.elysium_commission_minor_v1(o.price_minor,500);
  if (b->>'available_minor')::bigint<fee then raise exception 'INSUFFICIENT_PROVIDER_COMMISSION_BALANCE'; end if;
  insert into public.service_financial_contracts(vertical,aggregate_id,customer_id,provider_id,currency,gross_service_minor,commissionable_base_minor,commission_minor,payment_method,state)
  values('UNIVERSAL_SERVICE',r.id,r.client_id,o.provider_id,r.currency,o.price_minor,o.price_minor,fee,method,'RESERVED') returning * into c;
  if fee>0 then
   insert into public.ride_wallet_ledger(driver_id,idempotency_key,entry_type,amount_minor,currency,direction,withdrawable,metadata)
   values(o.provider_id,'service-reserve:'||r.id::text,'COMMISSION_RESERVED',fee,'CRC','DEBIT',false,
    jsonb_build_object('vertical','UNIVERSAL_SERVICE','aggregate_id',r.id,'contract_id',c.contract_id,'commission_policy_version','ELY_PROVIDER_COMMISSION_V1','commission_basis_points',500,'commissionable_base_minor',o.price_minor));
  end if;
  update public.universal_service_offers set state=case when id=o.id then 'ACCEPTED' else 'REJECTED' end,updated_at=now() where request_id=r.id and state='PENDING';
  update public.universal_service_requests set state='ASSIGNED',assigned_provider_id=o.provider_id,accepted_offer_id=o.id,final_price_minor=o.price_minor,version=version+1,updated_at=now() where id=r.id returning * into r;
 elsif p_action='START' then
  if a is distinct from r.assigned_provider_id then raise exception 'PROVIDER_REQUIRED' using errcode='42501'; end if;
  if r.state='IN_PROGRESS' then return r; end if;
  if r.state<>'ASSIGNED' then raise exception 'SERVICE_NOT_ASSIGNED'; end if;
  update public.universal_service_requests set state='IN_PROGRESS',version=version+1,updated_at=now() where id=r.id returning * into r;
 elsif p_action in ('COMPLETE','CANCEL') then
  if a<>r.client_id and (p_action<>'CANCEL' or a is distinct from r.assigned_provider_id) then raise exception 'PARTICIPANT_REQUIRED' using errcode='42501'; end if;
  if (p_action='COMPLETE' and r.state='COMPLETED') or (p_action='CANCEL' and r.state='CANCELLED') then return r; end if;
  if p_action='COMPLETE' and r.state<>'IN_PROGRESS' then raise exception 'SERVICE_NOT_IN_PROGRESS'; end if;
  if p_action='CANCEL' and r.state not in ('DRAFT','OPEN','ASSIGNED','IN_PROGRESS') then raise exception 'TERMINAL_SERVICE'; end if;
  select * into c from public.service_financial_contracts where vertical='UNIVERSAL_SERVICE' and aggregate_id=r.id for update;
  if found and c.state='RESERVED' then
   perform 1 from public.ride_wallets where driver_id=c.provider_id for update;
   update public.service_financial_contracts set state=case when p_action='COMPLETE' then 'CAPTURED' else 'RELEASED' end,settled_at=now() where contract_id=c.contract_id;
   if c.commission_minor>0 then
    insert into public.ride_wallet_ledger(driver_id,idempotency_key,entry_type,amount_minor,currency,direction,withdrawable,metadata)
    values(c.provider_id,'service-'||lower(p_action)||':'||r.id::text,case when p_action='COMPLETE' then 'COMMISSION_CAPTURED' else 'COMMISSION_RELEASED' end,c.commission_minor,'CRC',case when p_action='COMPLETE' then 'DEBIT' else 'CREDIT' end,false,
     jsonb_build_object('vertical','UNIVERSAL_SERVICE','aggregate_id',r.id,'contract_id',c.contract_id,'commission_policy_version',c.policy_code,'commission_basis_points',c.commission_basis_points,'commissionable_base_minor',c.commissionable_base_minor));
   end if;
  elsif r.state in ('ASSIGNED','IN_PROGRESS') then
   -- Historical immediate captures are not captured twice or refunded without reconciliation.
   if not exists(select 1 from public.service_provider_wallet_ledger where provider_id=r.assigned_provider_id::text and reference_id=r.id::text and entry_type='CONSTITUTIONAL_FEE_5_PERCENT') then raise exception 'FINANCIAL_CONTRACT_REQUIRED'; end if;
   if p_action='CANCEL' then raise exception 'LEGACY_CAPTURE_REQUIRES_RECONCILIATION'; end if;
  end if;
  update public.universal_service_requests set state=case when p_action='COMPLETE' then 'COMPLETED' else 'CANCELLED' end,version=version+1,updated_at=now() where id=r.id returning * into r;
 else raise exception 'UNKNOWN_SERVICE_ACTION'; end if;
 return r;
end; $$;

-- Private helpers must not become arbitrary ledger APIs through SECURITY DEFINER.
revoke all on function public.elysium_provider_wallet_ensure_v1(uuid),public.elysium_provider_balance_v1(uuid),public.elysium_provider_eligible_v1(uuid) from public,anon,authenticated,service_role;
revoke all on function public.service_provider_wallet_topup_v1(text,bigint,text),public.service_provider_wallet_deduct_fee_v1(text,text,bigint,text),public.ride_driver_wallet_credit_v1(uuid,bigint,text,text),public.universal_service_wallet_transfer_v1(uuid,bigint) from public,anon,authenticated,service_role;
revoke all on function public.ride_wallet_ensure_starter_credit_v1(),public.ride_wallet_balance_v1(),public.service_provider_wallet_ensure_starter_credit_v1(text,text,text),public.universal_service_transition_v1(uuid,text,uuid),public.ride_accept_offer_v2(uuid,uuid,bigint,text),public.ride_cancel_trip_v2(uuid,bigint,text,text,text) from public,anon;
grant execute on function public.ride_wallet_ensure_starter_credit_v1(),public.ride_wallet_balance_v1(),public.service_provider_wallet_ensure_starter_credit_v1(text,text,text),public.universal_service_transition_v1(uuid,text,uuid),public.ride_accept_offer_v2(uuid,uuid,bigint,text),public.ride_cancel_trip_v2(uuid,bigint,text,text,text) to authenticated;
revoke insert,update,delete on public.ride_wallets,public.ride_wallet_ledger,public.ride_ledger_transactions,public.ride_ledger_postings,public.ride_commission_reservations,public.service_provider_wallets,public.service_provider_wallet_ledger from anon,authenticated;
CREATE OR REPLACE FUNCTION public.ride_submit_offer_v2(p_request_id uuid, p_offer_id uuid, p_vehicle_id uuid, p_fare_minor bigint, p_currency text, p_eta_seconds integer, p_expected_version bigint, p_idempotency_key text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
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
    if not found or not v_vehicle.is_active or v_vehicle.verification_status <> 'VERIFIED' then
        return public.ride_command_error('VERIFIED_ACTIVE_VEHICLE_REQUIRED', 'Vehículo activo verificado requerido', false);
    end if;
    if not public.elysium_provider_eligible_v1(v_user_id) then
        return public.ride_command_error('VERIFIED_PROVIDER_REQUIRED', 'Prestador verificado requerido', false);
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
$function$;

revoke all on function public.ride_submit_offer_v2(uuid,uuid,uuid,bigint,text,integer,bigint,text) from public,anon;
grant execute on function public.ride_submit_offer_v2(uuid,uuid,uuid,bigint,text,integer,bigint,text) to authenticated;

-- Serialize every debit against the principal row and include reserves in every vertical.
create or replace function public.ride_wallet_balance_guard() returns trigger
language plpgsql security definer set search_path='' as $$
declare b jsonb;
begin
 if NEW.direction='DEBIT' then
  b:=public.elysium_provider_balance_v1(NEW.driver_id);
  if NEW.entry_type='COMMISSION_RESERVED' then
   -- Authoritative transition inserted/updated its reservation before this entry.
   if (b->>'posted_minor')::bigint < (b->>'reserved_minor')::bigint then
    raise exception 'INSUFFICIENT_PROVIDER_COMMISSION_BALANCE'; end if;
  elsif (b->>'available_minor')::bigint < NEW.amount_minor then
   raise exception 'INSUFFICIENT_PROVIDER_COMMISSION_BALANCE';
  end if;
 end if;
 return NEW;
end; $$;
revoke all on function public.ride_wallet_balance_guard() from public,anon,authenticated,service_role;
CREATE OR REPLACE FUNCTION public.universal_service_provider_summary_v1(p_provider_id uuid)
 RETURNS jsonb
 LANGUAGE sql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
 select jsonb_build_object(
 'provider_id',p_provider_id,
 'name',(select p.business_name from public.provider_profiles p join public.user_profiles u on u.id=p.user_profile_id
 where u.auth_user_id=p_provider_id and p.is_active and p.is_verified and p.status='active'
 and p.provider_type in ('service_provider','SERVICE_PROVIDER') order by p.id limit 1),
 'completed', (select count(*) from public.universal_service_requests where assigned_provider_id=p_provider_id and state='COMPLETED'),
 'reviews',(select count(*) from public.universal_service_ratings where provider_id=p_provider_id),
 'rating',(select avg(stars) from public.universal_service_ratings where provider_id=p_provider_id),
 'balance_minor',case when p_provider_id=auth.uid() then (public.elysium_provider_balance_v1(p_provider_id)->>'available_minor')::bigint else null end,
 'eligible',public.universal_service_provider_eligible_v1(p_provider_id));
$function$
;
revoke all on function public.universal_service_provider_summary_v1(uuid) from public,anon;
grant execute on function public.universal_service_provider_summary_v1(uuid) to authenticated;
