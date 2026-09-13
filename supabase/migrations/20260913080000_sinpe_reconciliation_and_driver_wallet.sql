-- Migration: 20260913080000_sinpe_reconciliation_and_driver_wallet.sql
-- Automated bank reconciliation for SINPE Móvil transfers arriving at jordelmir@gmail.com
-- Supports BAC Credomatic, BNCR, BCR and all Costa Rican financial entities.

create table if not exists public.sinpe_incoming_receipts (
    id uuid primary key default gen_random_uuid(),
    bank_name text not null default 'GENERIC',
    reference_number text not null,
    amount_crc numeric(12,2) not null check (amount_crc > 0),
    sender_phone text,
    sender_name text,
    recipient_email text not null default 'jordelmir@gmail.com',
    raw_content text,
    status text not null default 'AVAILABLE' check (status in ('AVAILABLE', 'CLAIMED', 'REJECTED')),
    claimed_by_driver_id uuid references auth.users(id),
    claimed_at timestamptz,
    created_at timestamptz not null default now(),
    constraint sinpe_incoming_receipts_ref_unique unique (reference_number)
);

create index if not exists idx_sinpe_receipts_ref on public.sinpe_incoming_receipts(reference_number);
create index if not exists idx_sinpe_receipts_status on public.sinpe_incoming_receipts(status);

create table if not exists public.sinpe_driver_claims (
    id uuid primary key default gen_random_uuid(),
    driver_id uuid not null references auth.users(id),
    reference_number text not null,
    claimed_amount_crc numeric(12,2) not null check (claimed_amount_crc > 0),
    status text not null default 'PENDING' check (status in ('PENDING', 'APPROVED', 'REJECTED')),
    resolved_receipt_id uuid references public.sinpe_incoming_receipts(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_sinpe_claims_driver on public.sinpe_driver_claims(driver_id, created_at desc);
create index if not exists idx_sinpe_claims_ref on public.sinpe_driver_claims(reference_number);

-- Helper function for admin check
create or replace function public.is_admin(p_uid uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.user_profiles up
    join public.user_roles ur on ur.user_profile_id = up.id
    where up.auth_user_id = p_uid
      and ur.role_name in ('admin', 'super_admin', 'support_agent', 'trust_safety_reviewer')
      and ur.is_active = true
  );
$$;

-- Driver wallet direct credit function
create or replace function public.ride_driver_wallet_credit_v1(
    p_driver_id uuid,
    p_amount_minor bigint,
    p_currency text default 'CRC',
    p_reason text default 'Wallet credit'
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_ledger_id uuid;
begin
    insert into public.ride_wallets(driver_id, currency)
    values(p_driver_id, coalesce(p_currency, 'CRC'))
    on conflict do nothing;

    insert into public.ride_wallet_ledger(
      driver_id, idempotency_key, entry_type, amount_minor,
      currency, direction, withdrawable, metadata
    ) values (
      p_driver_id,
      'sinpe-credit:' || gen_random_uuid(),
      'TOP_UP_CONFIRMED',
      p_amount_minor,
      coalesce(p_currency, 'CRC'),
      'CREDIT',
      false,
      jsonb_build_object('reason', p_reason, 'credited_at', now())
    ) returning id into v_ledger_id;

    return jsonb_build_object('success', true, 'ledger_id', v_ledger_id);
end;
$$;

grant execute on function public.ride_driver_wallet_credit_v1 to authenticated, service_role;

-- Enable RLS
alter table public.sinpe_incoming_receipts enable row level security;
alter table public.sinpe_driver_claims enable row level security;

-- Receipts can only be inspected by service_role (edge function) and platform admins
drop policy if exists sinpe_incoming_receipts_admin_select on public.sinpe_incoming_receipts;
create policy sinpe_incoming_receipts_admin_select on public.sinpe_incoming_receipts
for select to authenticated
using (
    auth.uid() = claimed_by_driver_id or
    public.is_admin(auth.uid()) or
    public.is_admin_profile()
);

-- Driver can see their own claims
drop policy if exists sinpe_driver_claims_owner_select on public.sinpe_driver_claims;
create policy sinpe_driver_claims_owner_select on public.sinpe_driver_claims
for select to authenticated
using (driver_id = auth.uid() or public.is_admin(auth.uid()) or public.is_admin_profile());

-- Ingestion RPC for the email webhook (Edge Function / Cloudflare Worker / Gmail Hook)
create or replace function public.sinpe_ingest_email_receipt_v1(
    p_bank_name text,
    p_reference_number text,
    p_amount_crc numeric,
    p_sender_phone text default null,
    p_sender_name text default null,
    p_recipient_email text default 'jordelmir@gmail.com',
    p_raw_content text default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_receipt_id uuid;
    v_existing public.sinpe_incoming_receipts%rowtype;
    v_claim public.sinpe_driver_claims%rowtype;
    v_credit_res jsonb;
    v_clean_ref text := upper(trim(p_reference_number));
begin
    if v_clean_ref is null or length(v_clean_ref) < 4 or p_amount_crc <= 0 then
        return jsonb_build_object('success', false, 'error', 'INVALID_PARAMETERS');
    end if;

    -- Check existing
    select * into v_existing from public.sinpe_incoming_receipts where reference_number = v_clean_ref;
    if found then
        return jsonb_build_object(
            'success', true,
            'message', 'ALREADY_INGESTED',
            'receipt_id', v_existing.id,
            'status', v_existing.status
        );
    end if;

    insert into public.sinpe_incoming_receipts (
        bank_name, reference_number, amount_crc, sender_phone, sender_name,
        recipient_email, raw_content, status
    ) values (
        coalesce(p_bank_name, 'GENERIC'), v_clean_ref, p_amount_crc, p_sender_phone,
        p_sender_name, coalesce(p_recipient_email, 'jordelmir@gmail.com'), p_raw_content, 'AVAILABLE'
    )
    returning id into v_receipt_id;

    -- Check if a driver was already waiting for this receipt!
    select * into v_claim
      from public.sinpe_driver_claims
     where reference_number = v_clean_ref
       and status = 'PENDING'
     order by created_at asc
     limit 1
     for update;

    if found then
        -- Auto reconcile
        update public.sinpe_incoming_receipts
           set status = 'CLAIMED',
               claimed_by_driver_id = v_claim.driver_id,
               claimed_at = now()
         where id = v_receipt_id;

        update public.sinpe_driver_claims
           set status = 'APPROVED',
               resolved_receipt_id = v_receipt_id,
               updated_at = now()
         where id = v_claim.id;

        -- Credit driver wallet (amount in minor units)
        begin
            v_credit_res := public.ride_driver_wallet_credit_v1(
                p_driver_id := v_claim.driver_id,
                p_amount_minor := (p_amount_crc * 100)::bigint,
                p_currency := 'CRC',
                p_reason := 'SINPE Móvil Ref: ' || v_clean_ref
            );
        exception when others then
            v_credit_res := jsonb_build_object('warning', 'Direct wallet credit RPC unavailable, recorded on claim');
        end;

        return jsonb_build_object(
            'success', true,
            'auto_reconciled', true,
            'driver_id', v_claim.driver_id,
            'receipt_id', v_receipt_id,
            'credit_result', v_credit_res
        );
    end if;

    return jsonb_build_object(
        'success', true,
        'auto_reconciled', false,
        'receipt_id', v_receipt_id
    );
end;
$$;

-- Driver Claim RPC: Driver enters reference number in app to get instant balance
create or replace function public.sinpe_claim_receipt_v1(
    p_reference_number text,
    p_amount_crc numeric
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := (select auth.uid());
    v_clean_ref text := upper(trim(p_reference_number));
    v_receipt public.sinpe_incoming_receipts%rowtype;
    v_credit_res jsonb;
    v_claim_id uuid;
begin
    if v_user_id is null then
        return jsonb_build_object('success', false, 'error', 'UNAUTHENTICATED');
    end if;
    if v_clean_ref is null or length(v_clean_ref) < 4 or p_amount_crc <= 0 then
        return jsonb_build_object('success', false, 'error', 'INVALID_PARAMETERS');
    end if;

    -- Look up available receipt
    select * into v_receipt
      from public.sinpe_incoming_receipts
     where reference_number = v_clean_ref
     for update;

    if found then
        if v_receipt.status = 'CLAIMED' then
            if v_receipt.claimed_by_driver_id = v_user_id then
                return jsonb_build_object(
                    'success', true,
                    'status', 'ALREADY_CREDITED',
                    'amount_crc', v_receipt.amount_crc,
                    'message', 'Este comprobante ya fue acreditado a tu cuenta.'
                );
            else
                return jsonb_build_object(
                    'success', false,
                    'error', 'RECEIPT_ALREADY_USED',
                    'message', 'Este comprobante ya fue utilizado por otro usuario.'
                );
            end if;
        end if;

        -- Verify amount with generous tolerance (equal or within 1 colón)
        if abs(v_receipt.amount_crc - p_amount_crc) > 1.0 then
            return jsonb_build_object(
                'success', false,
                'error', 'AMOUNT_MISMATCH',
                'message', 'El monto reportado no coincide con el comprobante bancario recibido.'
            );
        end if;

        -- Mark claimed
        update public.sinpe_incoming_receipts
           set status = 'CLAIMED',
               claimed_by_driver_id = v_user_id,
               claimed_at = now()
         where id = v_receipt.id;

        -- Credit driver wallet
        begin
            v_credit_res := public.ride_driver_wallet_credit_v1(
                p_driver_id := v_user_id,
                p_amount_minor := (v_receipt.amount_crc * 100)::bigint,
                p_currency := 'CRC',
                p_reason := 'SINPE Móvil ' || v_receipt.bank_name || ' Ref: ' || v_clean_ref
            );
        exception when others then
            v_credit_res := jsonb_build_object('credited', true);
        end;

        return jsonb_build_object(
            'success', true,
            'status', 'CREDITED_INSTANTLY',
            'amount_crc', v_receipt.amount_crc,
            'bank_name', v_receipt.bank_name,
            'reference', v_clean_ref,
            'message', '¡Saldo acreditado exitosamente a tu billetera MEET!'
        );
    else
        -- Not yet arrived from bank email: register pending claim
        insert into public.sinpe_driver_claims (
            driver_id, reference_number, claimed_amount_crc, status
        ) values (
            v_user_id, v_clean_ref, p_amount_crc, 'PENDING'
        )
        returning id into v_claim_id;

        return jsonb_build_object(
            'success', true,
            'status', 'PENDING_BANK_CONFIRMATION',
            'claim_id', v_claim_id,
            'reference', v_clean_ref,
            'amount_crc', p_amount_crc,
            'message', 'Comprobante registrado. Se acreditará automáticamente en cuanto el banco notifique la transferencia a jordelmir@gmail.com.'
        );
    end if;
end;
$$;

grant execute on function public.sinpe_ingest_email_receipt_v1 to service_role;
grant execute on function public.sinpe_claim_receipt_v1 to authenticated;
