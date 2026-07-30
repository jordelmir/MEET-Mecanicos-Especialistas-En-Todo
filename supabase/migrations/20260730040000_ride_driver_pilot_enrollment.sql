-- Elysium Vanguard Viajes: honest, server-controlled driver pilot enrollment.
--
-- The legacy verification_status='VERIFIED' value remains the dispatch switch
-- consumed by already-deployed command functions. It is NOT exposed as a claim
-- of document authenticity. The separate verification_method and
-- document_review_status columns preserve the actual authority and review state.

alter table public.ride_driver_vehicles
    add column if not exists verification_method text not null
        default 'LEGACY_REVIEW';
alter table public.ride_driver_vehicles
    add column if not exists document_review_status text not null
        default 'UNDER_REVIEW';
alter table public.ride_driver_vehicles
    add column if not exists evidence_manifest_sha256 text;
alter table public.ride_driver_vehicles
    add column if not exists pilot_access_expires_at timestamptz;

alter table public.ride_driver_vehicles
    drop constraint if exists ride_driver_vehicles_verification_method_check;
alter table public.ride_driver_vehicles
    add constraint ride_driver_vehicles_verification_method_check check (
        verification_method in (
            'LEGACY_REVIEW',
            'PILOT_EVIDENCE_ATTESTATION',
            'IDENTITY_PROVIDER',
            'MANUAL_REVIEW'
        )
    );

alter table public.ride_driver_vehicles
    drop constraint if exists ride_driver_vehicles_document_review_status_check;
alter table public.ride_driver_vehicles
    add constraint ride_driver_vehicles_document_review_status_check check (
        document_review_status in (
            'NOT_STARTED', 'DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'APPROVED',
            'REJECTED', 'EXPIRED', 'SUSPENDED'
        )
    );

alter table public.ride_driver_vehicles
    drop constraint if exists ride_driver_vehicles_evidence_hash_check;
alter table public.ride_driver_vehicles
    add constraint ride_driver_vehicles_evidence_hash_check check (
        evidence_manifest_sha256 is null or
        evidence_manifest_sha256 ~ '^[a-f0-9]{64}$'
    );

alter table public.ride_command_receipts
    drop constraint if exists ride_command_receipts_command_type_check;
alter table public.ride_command_receipts
    add constraint ride_command_receipts_command_type_check check (
        command_type in (
            'CREATE_REQUEST', 'SUBMIT_OFFER', 'ACCEPT_OFFER',
            'CLAIM', 'DRIVER_EN_ROUTE', 'DRIVER_ARRIVED', 'START',
            'CANCEL', 'COMPLETE', 'ISSUE_BOARDING_PIN',
            'VERIFY_BOARDING_PIN', 'ENROLL_DRIVER_PILOT'
        )
    );

create or replace function public.ride_vehicle_dispatch_eligible(
    p_vehicle_id uuid,
    p_driver_id uuid
)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
          from public.ride_driver_vehicles v
         where v.id = p_vehicle_id
           and v.driver_id = p_driver_id
           and v.is_active
           and v.verification_status = 'VERIFIED'
           and (
               v.verification_method <> 'PILOT_EVIDENCE_ATTESTATION' or
               (
                   v.document_review_status in ('SUBMITTED', 'UNDER_REVIEW') and
                   v.pilot_access_expires_at > now()
               )
           )
    );
$$;

create or replace function public.ride_guard_dispatch_vehicle()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_vehicle_id uuid;
    v_driver_id uuid;
begin
    if tg_table_name = 'ride_offers' then
        if tg_op = 'UPDATE' and new.state <> 'PENDING' then
            return new;
        end if;
        v_vehicle_id := new.vehicle_id;
        v_driver_id := new.driver_id;
    else
        if tg_op = 'UPDATE' and
           old.assigned_vehicle_id is not distinct from new.assigned_vehicle_id and
           old.assigned_driver_id is not distinct from new.assigned_driver_id
        then
            return new;
        end if;
        v_vehicle_id := new.assigned_vehicle_id;
        v_driver_id := new.assigned_driver_id;
    end if;

    if v_vehicle_id is not null and not public.ride_vehicle_dispatch_eligible(
        v_vehicle_id,
        v_driver_id
    ) then
        raise exception using
            errcode = '23514',
            message = 'RIDE_VEHICLE_NOT_DISPATCH_ELIGIBLE';
    end if;
    return new;
end;
$$;

drop trigger if exists ride_offer_dispatch_vehicle_guard on public.ride_offers;
create trigger ride_offer_dispatch_vehicle_guard
before insert or update
on public.ride_offers
for each row execute function public.ride_guard_dispatch_vehicle();

drop trigger if exists ride_request_dispatch_vehicle_guard
on public.ride_requests;
create trigger ride_request_dispatch_vehicle_guard
before insert or update of assigned_vehicle_id, assigned_driver_id, state
on public.ride_requests
for each row execute function public.ride_guard_dispatch_vehicle();

drop policy if exists ride_requests_participant_select
on public.ride_requests;
create policy ride_requests_participant_select on public.ride_requests
for select to authenticated
using (
    passenger_id = (select auth.uid()) or
    assigned_driver_id = (select auth.uid()) or
    (
        state in ('SEARCHING', 'OFFERED') and
        exists (
            select 1
              from public.ride_driver_vehicles v
             where v.driver_id = (select auth.uid())
               and public.ride_vehicle_dispatch_eligible(
                   v.id,
                   (select auth.uid())
               )
        )
    )
);

create or replace function public.ride_enroll_driver_pilot_v2(
    p_driver_display_name text,
    p_country_code text,
    p_currency text,
    p_vehicle_reference text,
    p_vehicle_display_name text,
    p_seats integer,
    p_evidence_manifest_sha256 text,
    p_idempotency_key text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := (select auth.uid());
    v_vehicle public.ride_driver_vehicles%rowtype;
    v_request_hash text;
    v_replay jsonb;
    v_response jsonb;
    v_expires_at timestamptz := now() + interval '30 days';
begin
    if v_user_id is null then
        return public.ride_command_error(
            'UNAUTHENTICATED', 'Autenticación requerida', false
        );
    end if;
    if char_length(trim(coalesce(p_driver_display_name, ''))) not between 3 and 120 or
       coalesce(p_country_code, '') !~ '^[A-Z]{2}$' or
       coalesce(p_currency, '') !~ '^[A-Z]{3}$' or
       char_length(trim(coalesce(p_vehicle_reference, ''))) not between 16 and 160 or
       char_length(trim(coalesce(p_vehicle_display_name, ''))) not between 3 and 160 or
       coalesce(p_seats, 0) not between 1 and 16 or
       coalesce(p_evidence_manifest_sha256, '') !~ '^[a-f0-9]{64}$' or
       coalesce(p_idempotency_key, '') !~ '^[A-Za-z0-9._:-]{16,128}$'
    then
        return public.ride_command_error(
            'VALIDATION_ERROR', 'Datos de alta piloto inválidos', false
        );
    end if;

    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(
            v_user_id::text || ':' || p_idempotency_key,
            0
        )
    );
    v_request_hash := public.ride_command_hash(jsonb_build_object(
        'command', 'ENROLL_DRIVER_PILOT',
        'driver_display_name', trim(p_driver_display_name),
        'country_code', p_country_code,
        'currency', p_currency,
        'vehicle_reference', trim(p_vehicle_reference),
        'vehicle_display_name', trim(p_vehicle_display_name),
        'seats', p_seats,
        'evidence_manifest_sha256', p_evidence_manifest_sha256
    ));
    v_replay := public.ride_command_replay(
        v_user_id, p_idempotency_key, v_request_hash
    );
    if v_replay is not null then
        return v_replay;
    end if;

    insert into public.ride_profiles(
        user_id, mobility_role, country_code, preferred_currency,
        display_name, updated_at
    )
    values (
        v_user_id, 'DRIVER', p_country_code, p_currency,
        trim(p_driver_display_name), now()
    )
    on conflict (user_id) do update
       set mobility_role = case
               when public.ride_profiles.mobility_role = 'PASSENGER' then 'BOTH'
               else public.ride_profiles.mobility_role
           end,
           country_code = excluded.country_code,
           preferred_currency = excluded.preferred_currency,
           display_name = excluded.display_name,
           updated_at = now();

    update public.ride_driver_vehicles
       set is_active = false,
           updated_at = now()
     where driver_id = v_user_id
       and vehicle_id <> trim(p_vehicle_reference)
       and is_active;

    insert into public.ride_driver_vehicles(
        driver_id, vehicle_id, display_name, seats,
        verification_status, verification_method,
        document_review_status, evidence_manifest_sha256,
        pilot_access_expires_at, is_active, updated_at
    )
    values (
        v_user_id, trim(p_vehicle_reference), trim(p_vehicle_display_name),
        p_seats, 'VERIFIED', 'PILOT_EVIDENCE_ATTESTATION',
        'UNDER_REVIEW', p_evidence_manifest_sha256,
        v_expires_at, true, now()
    )
    on conflict (driver_id, vehicle_id) do update
       set display_name = excluded.display_name,
           seats = excluded.seats,
           verification_status = 'VERIFIED',
           verification_method = 'PILOT_EVIDENCE_ATTESTATION',
           document_review_status = 'UNDER_REVIEW',
           evidence_manifest_sha256 = excluded.evidence_manifest_sha256,
           pilot_access_expires_at = excluded.pilot_access_expires_at,
           is_active = true,
           updated_at = now()
    returning * into v_vehicle;

    v_response := public.ride_command_success(jsonb_build_object(
        'status', 'PILOT_ATTESTED',
        'document_review_status', v_vehicle.document_review_status,
        'vehicle_id', v_vehicle.id,
        'pilot_access_expires_at', v_vehicle.pilot_access_expires_at
    ));
    return public.ride_record_command_receipt(
        v_user_id, null, 'ENROLL_DRIVER_PILOT', p_idempotency_key,
        v_request_hash, v_response
    );
end;
$$;

revoke all on function public.ride_vehicle_dispatch_eligible(uuid, uuid)
    from public;
revoke all on function public.ride_enroll_driver_pilot_v2(
    text, text, text, text, text, integer, text, text
) from public;

grant execute on function public.ride_vehicle_dispatch_eligible(uuid, uuid)
    to authenticated;
grant execute on function public.ride_enroll_driver_pilot_v2(
    text, text, text, text, text, integer, text, text
) to authenticated;

comment on column public.ride_driver_vehicles.verification_status is
    'Legacy dispatch switch. Never present this field alone as document verification.';
comment on column public.ride_driver_vehicles.verification_method is
    'Authority/method behind dispatch eligibility; PILOT_EVIDENCE_ATTESTATION is not document authenticity review.';
comment on column public.ride_driver_vehicles.document_review_status is
    'Honest review lifecycle shown to the driver; pilot enrollment remains UNDER_REVIEW.';
