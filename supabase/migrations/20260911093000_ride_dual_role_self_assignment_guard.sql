-- One authenticated account may operate as passenger and driver concurrently,
-- but it may never supply both parties of the same ride.

-- The convergence trigger generates/synchronizes both identifiers. Defaults on
-- both columns run before a BEFORE trigger and previously produced two UUIDs.
alter table public.ride_requests alter column id drop default;
alter table public.ride_requests alter column ride_request_id drop default;

alter table public.guest_ride_profiles enable row level security;
alter table public.guest_ride_profiles force row level security;
drop policy if exists guest_ride_profiles_requester_read on public.guest_ride_profiles;
create policy guest_ride_profiles_requester_read
on public.guest_ride_profiles for select to authenticated
using (requested_by_rider_id = (select auth.uid()));

alter table public.service_verification_applications
    add column if not exists derived_from_application_id uuid
    references public.service_verification_applications(id) on delete restrict;

create or replace function public.ride_link_passenger_role_from_driver_review()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_passenger public.service_verification_applications%rowtype;
    v_previous_status text;
begin
    if new.service_type <> 'RIDE_DRIVER' or new.status <> 'APPROVED' or
       old.status is not distinct from new.status
    then
        return new;
    end if;

    select status into v_previous_status
      from public.service_verification_applications
     where applicant_user_id = new.applicant_user_id
       and service_type = 'PASSENGER'
       and profile_reference = 'primary';

    insert into public.service_verification_applications(
        applicant_user_id, service_type, profile_reference, display_name,
        phone, evidence_manifest_sha256, status, decision_reason,
        submitted_at, reviewed_at, reviewed_by, updated_at, correlation_id,
        derived_from_application_id
    ) values (
        new.applicant_user_id, 'PASSENGER', 'primary', new.display_name,
        new.phone, new.evidence_manifest_sha256, 'APPROVED',
        'Acceso de pasajero ligado a verificación de chofer aprobada',
        new.submitted_at, new.reviewed_at, new.reviewed_by, now(),
        new.correlation_id, new.id
    )
    on conflict (applicant_user_id, service_type, profile_reference) do update
       set display_name = excluded.display_name,
           phone = excluded.phone,
           evidence_manifest_sha256 = excluded.evidence_manifest_sha256,
           status = 'APPROVED',
           decision_reason = excluded.decision_reason,
           reviewed_at = excluded.reviewed_at,
           reviewed_by = excluded.reviewed_by,
           updated_at = now(),
           correlation_id = excluded.correlation_id,
           derived_from_application_id = excluded.derived_from_application_id
     where public.service_verification_applications.status in ('PENDING', 'APPROVED')
    returning * into v_passenger;

    -- A prior rejection or suspension remains authoritative and is never
    -- silently overridden by linkage.
    if v_passenger.id is not null and new.reviewed_by is not null then
        insert into public.service_verification_audit_events(
            application_id, actor_id, event_type, from_status, to_status,
            reason, correlation_id
        ) values (
            v_passenger.id, new.reviewed_by, 'APPROVED', v_previous_status,
            'APPROVED', 'Ligado a revisión de chofer aprobada', new.correlation_id
        );
    end if;
    return new;
end;
$$;

revoke all on function public.ride_link_passenger_role_from_driver_review() from public;
drop trigger if exists ride_link_passenger_after_driver_review
    on public.service_verification_applications;
create trigger ride_link_passenger_after_driver_review
after update of status on public.service_verification_applications
for each row execute function public.ride_link_passenger_role_from_driver_review();

create or replace function public.ride_reject_self_assignment()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
    if new.assigned_driver_id is not null
       and new.assigned_driver_id = new.passenger_id
    then
        raise exception using errcode = '23514', message = 'SELF_RIDE_ASSIGNMENT_FORBIDDEN';
    end if;
    return new;
end;
$$;

drop trigger if exists ride_requests_reject_self_assignment on public.ride_requests;
create trigger ride_requests_reject_self_assignment
before insert or update of passenger_id, assigned_driver_id on public.ride_requests
for each row execute function public.ride_reject_self_assignment();

create or replace function public.ride_reject_self_offer()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    if exists (
        select 1 from public.ride_requests request
         where request.id = new.request_id
           and request.passenger_id = new.driver_id
    ) then
        raise exception using errcode = '23514', message = 'SELF_RIDE_OFFER_FORBIDDEN';
    end if;
    return new;
end;
$$;

revoke all on function public.ride_reject_self_assignment() from public;
revoke all on function public.ride_reject_self_offer() from public;

drop trigger if exists ride_offers_reject_self_offer on public.ride_offers;
create trigger ride_offers_reject_self_offer
before insert or update of request_id, driver_id on public.ride_offers
for each row execute function public.ride_reject_self_offer();

comment on function public.ride_reject_self_assignment() is
    'Prevents one principal from being passenger and assigned driver on the same ride.';
comment on function public.ride_reject_self_offer() is
    'Prevents a driver from offering on a ride requested by the same principal.';

-- Durable outbox-compatible guest request. The caller remains the accountable
-- passenger while the actual rider identity is bound atomically to the request.
create or replace function public.ride_create_guest_request_v1(
    p_request_id uuid,
    p_display_name text,
    p_guest_name text,
    p_guest_phone_e164 text,
    p_country_code text,
    p_pickup_latitude double precision,
    p_pickup_longitude double precision,
    p_pickup_address text,
    p_destination_latitude double precision,
    p_destination_longitude double precision,
    p_destination_address text,
    p_offered_fare_minor bigint,
    p_currency text,
    p_payment_method text,
    p_stops jsonb,
    p_fare_mode text,
    p_distance_rate_minor_per_km bigint,
    p_time_rate_minor_per_minute bigint,
    p_estimated_distance_meters bigint,
    p_estimated_duration_seconds bigint,
    p_fare_rate_card_version bigint,
    p_allows_in_trip_stops boolean,
    p_idempotency_key text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_actor uuid := (select auth.uid());
    v_base jsonb;
    v_profile public.guest_ride_profiles%rowtype;
    v_tracking_token text;
begin
    if v_actor is null then
        return public.ride_command_error('UNAUTHENTICATED', 'Autenticación requerida', false);
    end if;
    if char_length(trim(coalesce(p_guest_name, ''))) not between 2 and 120 or
       coalesce(p_guest_phone_e164, '') !~ '^\+[1-9][0-9]{7,14}$'
    then
        return public.ride_command_error(
            'INVALID_GUEST_IDENTITY',
            'Nombre y teléfono internacional del pasajero requeridos',
            false
        );
    end if;

    v_base := public.ride_create_request_v3(
        p_request_id, p_display_name, p_country_code,
        p_pickup_latitude, p_pickup_longitude, p_pickup_address,
        p_destination_latitude, p_destination_longitude, p_destination_address,
        p_offered_fare_minor, p_currency, p_payment_method, p_stops,
        p_fare_mode, p_distance_rate_minor_per_km,
        p_time_rate_minor_per_minute, p_estimated_distance_meters,
        p_estimated_duration_seconds, p_fare_rate_card_version,
        p_allows_in_trip_stops, 'guest-base:' || p_idempotency_key
    );
    if not coalesce((v_base ->> 'ok')::boolean, false) then
        return v_base;
    end if;

    v_tracking_token := encode(
        extensions.digest(
            p_request_id::text || ':' || v_actor::text || ':' || p_idempotency_key,
            'sha256'
        ),
        'hex'
    );
    insert into public.guest_ride_profiles(
        ride_request_id, requested_by_rider_id, guest_name,
        guest_phone_e164, sms_notifications_enabled, tracking_token
    ) values (
        p_request_id, v_actor, trim(p_guest_name),
        p_guest_phone_e164, true, v_tracking_token
    )
    on conflict (ride_request_id) do nothing
    returning * into v_profile;

    if v_profile.guest_ride_id is null then
        select * into v_profile
          from public.guest_ride_profiles
         where ride_request_id = p_request_id
           and requested_by_rider_id = v_actor
           and guest_name = trim(p_guest_name)
           and guest_phone_e164 = p_guest_phone_e164;
        if not found then
            return public.ride_command_error(
                'GUEST_REQUEST_CONFLICT',
                'La identidad del pasajero no coincide con la solicitud existente',
                false
            );
        end if;
    end if;

    return v_base || jsonb_build_object(
        'guest_ride_id', v_profile.guest_ride_id,
        'guest_name', v_profile.guest_name
    );
end;
$$;

revoke all on function public.ride_create_guest_request_v1(
    uuid,text,text,text,text,double precision,double precision,text,
    double precision,double precision,text,bigint,text,text,jsonb,text,
    bigint,bigint,bigint,bigint,bigint,boolean,text
) from public, anon;
grant execute on function public.ride_create_guest_request_v1(
    uuid,text,text,text,text,double precision,double precision,text,
    double precision,double precision,text,bigint,text,text,jsonb,text,
    bigint,bigint,bigint,bigint,bigint,boolean,text
) to authenticated;
