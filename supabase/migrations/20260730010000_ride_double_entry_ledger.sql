-- Elysium Vanguard Viajes: append-only double-entry ledger.
-- This migration is additive. The existing ride_wallet_ledger remains the
-- compatibility projection while every posted entry is mirrored atomically.

create table if not exists public.ride_ledger_transactions (
    id uuid primary key default gen_random_uuid(),
    idempotency_key text not null unique check (
        char_length(idempotency_key) between 1 and 256
    ),
    event_type text not null check (
        event_type in (
            'PROMOTIONAL_GRANT', 'TOP_UP_CONFIRMED', 'COMMISSION_RESERVED',
            'COMMISSION_CAPTURED', 'COMMISSION_RELEASED', 'REFUND',
            'ADJUSTMENT', 'DISPUTE_HOLD', 'REVERSAL'
        )
    ),
    trip_id uuid references public.ride_requests(id) on delete restrict,
    currency text not null check (currency ~ '^[A-Z]{3}$'),
    reversal_of uuid references public.ride_ledger_transactions(id) on delete restrict,
    commission_policy_version text check (
        commission_policy_version is null or
        char_length(commission_policy_version) between 1 and 100
    ),
    commission_basis_points integer check (
        commission_basis_points is null or
        commission_basis_points between 0 and 10000
    ),
    commissionable_base_minor bigint check (
        commissionable_base_minor is null or commissionable_base_minor >= 0
    ),
    commission_amount_minor bigint check (
        commission_amount_minor is null or commission_amount_minor >= 0
    ),
    rounding_mode text check (
        rounding_mode is null or rounding_mode in ('HALF_UP', 'FLOOR')
    ),
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    check (reversal_of is null or event_type = 'REVERSAL')
);

create index if not exists ride_ledger_transactions_trip_created_idx
    on public.ride_ledger_transactions(trip_id, created_at, id);

create table if not exists public.ride_ledger_postings (
    id uuid primary key default gen_random_uuid(),
    transaction_id uuid not null
        references public.ride_ledger_transactions(id) on delete restrict,
    entry_sequence smallint not null check (entry_sequence >= 0),
    account_code text not null check (
        account_code in (
            'DRIVER_AVAILABLE', 'DRIVER_RESERVED', 'DRIVER_RECEIVABLE',
            'PLATFORM_COMMISSION_REVENUE', 'PLATFORM_PROMOTION_EXPENSE',
            'TENANT_REVENUE_SHARE', 'COOPERATIVE_REVENUE_SHARE',
            'REFERRAL_PARTNER_REVENUE', 'PROMOTION_POOL',
            'PAYMENT_CLEARING', 'REFUND_CLEARING', 'DISPUTE_HOLD',
            'PROCESSOR_FEE_EXPENSE'
        )
    ),
    account_owner_id uuid,
    direction text not null check (direction in ('DEBIT', 'CREDIT')),
    amount_minor bigint not null check (amount_minor > 0),
    currency text not null check (currency ~ '^[A-Z]{3}$'),
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    unique (transaction_id, entry_sequence),
    check (
        (
            account_code in (
                'DRIVER_AVAILABLE', 'DRIVER_RESERVED', 'DRIVER_RECEIVABLE',
                'TENANT_REVENUE_SHARE', 'COOPERATIVE_REVENUE_SHARE',
                'REFERRAL_PARTNER_REVENUE'
            ) and account_owner_id is not null
        ) or (
            account_code not in (
                'DRIVER_AVAILABLE', 'DRIVER_RESERVED', 'DRIVER_RECEIVABLE',
                'TENANT_REVENUE_SHARE', 'COOPERATIVE_REVENUE_SHARE',
                'REFERRAL_PARTNER_REVENUE'
            )
        )
    )
);

create index if not exists ride_ledger_postings_account_created_idx
    on public.ride_ledger_postings(
        account_code, account_owner_id, currency, created_at, id
    );

create table if not exists public.ride_commission_calculations (
    id uuid primary key default gen_random_uuid(),
    trip_id uuid not null references public.ride_requests(id) on delete restrict,
    calculation_kind text not null check (
        calculation_kind in ('ESTIMATE', 'FINAL', 'REVERSAL')
    ),
    idempotency_key text not null unique,
    commission_policy_version text not null check (
        char_length(commission_policy_version) between 1 and 100
    ),
    commission_basis_points integer not null check (
        commission_basis_points between 0 and 10000
    ),
    commissionable_base_minor bigint not null check (
        commissionable_base_minor >= 0
    ),
    commission_amount_minor bigint not null check (
        commission_amount_minor >= 0
    ),
    rounding_mode text not null check (rounding_mode = 'HALF_UP'),
    currency text not null check (currency ~ '^[A-Z]{3}$'),
    calculated_at timestamptz not null default now(),
    settled_at timestamptz,
    metadata jsonb not null default '{}'::jsonb,
    check (settled_at is null or settled_at >= calculated_at)
);

create index if not exists ride_commission_calculations_trip_idx
    on public.ride_commission_calculations(trip_id, calculated_at, id);

create table if not exists public.ride_revenue_split_rule_sets (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid,
    jurisdiction text not null check (jurisdiction ~ '^[A-Z]{2}$'),
    contract_version text not null check (
        char_length(contract_version) between 1 and 100
    ),
    effective_from timestamptz not null,
    effective_to timestamptz,
    created_at timestamptz not null default now(),
    unique (tenant_id, jurisdiction, contract_version),
    check (effective_to is null or effective_to > effective_from)
);

create table if not exists public.ride_revenue_split_rules (
    id uuid primary key default gen_random_uuid(),
    rule_set_id uuid not null
        references public.ride_revenue_split_rule_sets(id) on delete restrict,
    beneficiary text not null check (
        beneficiary in (
            'PLATFORM', 'TENANT', 'COOPERATIVE',
            'REFERRAL_PARTNER', 'PROMOTION_POOL'
        )
    ),
    beneficiary_owner_id uuid,
    split_basis_points integer not null check (
        split_basis_points between 1 and 500
    ),
    created_at timestamptz not null default now(),
    unique (rule_set_id, beneficiary, beneficiary_owner_id),
    check (
        (
            beneficiary in ('TENANT', 'COOPERATIVE', 'REFERRAL_PARTNER') and
            beneficiary_owner_id is not null
        ) or beneficiary not in ('TENANT', 'COOPERATIVE', 'REFERRAL_PARTNER')
    )
);

create unique index if not exists ride_revenue_split_rules_beneficiary_idx
    on public.ride_revenue_split_rules(
        rule_set_id,
        beneficiary,
        coalesce(
            beneficiary_owner_id,
            '00000000-0000-0000-0000-000000000000'::uuid
        )
    );

-- No tenant/cooperative split is invented. Until a signed contract is entered,
-- the public 5% belongs to the platform rule set.
insert into public.ride_revenue_split_rule_sets(
    id, tenant_id, jurisdiction, contract_version, effective_from
)
values (
    '00000000-0000-0000-0000-000000000500',
    null,
    'CR',
    'cr-platform-default-v1',
    '2026-07-01T00:00:00Z'::timestamptz
)
on conflict (id) do nothing;

insert into public.ride_revenue_split_rules(
    id, rule_set_id, beneficiary, beneficiary_owner_id, split_basis_points
)
values (
    '00000000-0000-0000-0000-000000000501',
    '00000000-0000-0000-0000-000000000500',
    'PLATFORM',
    null,
    500
)
on conflict (id) do nothing;

create or replace function public.ride_assert_journal_balanced()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_transaction_id uuid;
    v_transaction_currency text;
    v_posting_count integer;
    v_currency_count integer;
    v_signed_total numeric;
begin
    if tg_op = 'DELETE' then
        v_transaction_id := old.transaction_id;
    else
        v_transaction_id := new.transaction_id;
    end if;

    select t.currency
      into strict v_transaction_currency
      from public.ride_ledger_transactions t
     where t.id = v_transaction_id;

    select
        count(*)::integer,
        count(distinct p.currency)::integer,
        coalesce(sum(
            case
                when p.direction = 'DEBIT' then p.amount_minor::numeric
                else -p.amount_minor::numeric
            end
        ), 0)
      into v_posting_count, v_currency_count, v_signed_total
      from public.ride_ledger_postings p
     where p.transaction_id = v_transaction_id;

    if v_posting_count < 2 then
        raise exception 'Ledger transaction requires at least two postings';
    end if;
    if v_currency_count <> 1 or exists (
        select 1
          from public.ride_ledger_postings p
         where p.transaction_id = v_transaction_id
           and p.currency <> v_transaction_currency
    ) then
        raise exception 'Ledger transaction currency mismatch';
    end if;
    if v_signed_total <> 0 then
        raise exception 'Ledger transaction is not balanced';
    end if;
    return null;
end;
$$;

drop trigger if exists ride_ledger_postings_balance
    on public.ride_ledger_postings;
create constraint trigger ride_ledger_postings_balance
after insert or update or delete on public.ride_ledger_postings
deferrable initially deferred
for each row execute function public.ride_assert_journal_balanced();

create or replace function public.ride_assert_revenue_split_total()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_rule_set_id uuid;
    v_total integer;
begin
    if tg_op = 'DELETE' then
        v_rule_set_id := old.rule_set_id;
    else
        v_rule_set_id := new.rule_set_id;
    end if;

    select coalesce(sum(r.split_basis_points), 0)::integer
      into v_total
      from public.ride_revenue_split_rules r
     where r.rule_set_id = v_rule_set_id;

    if v_total <> 500 then
        raise exception 'Revenue split rules must total exactly 500 basis points';
    end if;
    return null;
end;
$$;

drop trigger if exists ride_revenue_split_rules_total
    on public.ride_revenue_split_rules;
create constraint trigger ride_revenue_split_rules_total
after insert or update or delete on public.ride_revenue_split_rules
deferrable initially deferred
for each row execute function public.ride_assert_revenue_split_total();

create or replace function public.ride_mirror_wallet_ledger_entry(
    p_wallet_entry_id uuid
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_entry public.ride_wallet_ledger%rowtype;
    v_transaction_id uuid;
    v_event_type text;
    v_debit_account text;
    v_debit_owner uuid;
    v_credit_account text;
    v_credit_owner uuid;
    v_transaction_key text;
begin
    select l.*
      into strict v_entry
      from public.ride_wallet_ledger l
     where l.id = p_wallet_entry_id;

    if v_entry.entry_type = 'TOP_UP_PENDING' or v_entry.amount_minor = 0 then
        return null;
    end if;

    -- Source entry UUID keeps the mirror key bounded even when a historical
    -- idempotency key was created before length validation existed.
    v_transaction_key := 'wallet-entry:' || v_entry.id::text;

    select t.id
      into v_transaction_id
      from public.ride_ledger_transactions t
     where t.idempotency_key = v_transaction_key;

    if found then
        return v_transaction_id;
    end if;

    case v_entry.entry_type
        when 'PROMOTIONAL_GRANT' then
            v_event_type := 'PROMOTIONAL_GRANT';
            v_debit_account := 'PLATFORM_PROMOTION_EXPENSE';
            v_credit_account := 'DRIVER_AVAILABLE';
            v_credit_owner := v_entry.driver_id;
        when 'TOP_UP_CONFIRMED' then
            v_event_type := 'TOP_UP_CONFIRMED';
            v_debit_account := 'PAYMENT_CLEARING';
            v_credit_account := 'DRIVER_AVAILABLE';
            v_credit_owner := v_entry.driver_id;
        when 'COMMISSION_RESERVED' then
            v_event_type := 'COMMISSION_RESERVED';
            v_debit_account := 'DRIVER_AVAILABLE';
            v_debit_owner := v_entry.driver_id;
            v_credit_account := 'DRIVER_RESERVED';
            v_credit_owner := v_entry.driver_id;
        when 'COMMISSION_CAPTURED' then
            v_event_type := 'COMMISSION_CAPTURED';
            v_debit_account := 'DRIVER_RESERVED';
            v_debit_owner := v_entry.driver_id;
            v_credit_account := 'PLATFORM_COMMISSION_REVENUE';
        when 'COMMISSION_RELEASED' then
            v_event_type := 'COMMISSION_RELEASED';
            v_debit_account := 'DRIVER_RESERVED';
            v_debit_owner := v_entry.driver_id;
            v_credit_account := 'DRIVER_AVAILABLE';
            v_credit_owner := v_entry.driver_id;
        when 'REFUND' then
            v_event_type := 'REFUND';
            v_debit_account := 'REFUND_CLEARING';
            v_credit_account := 'DRIVER_AVAILABLE';
            v_credit_owner := v_entry.driver_id;
        when 'ADJUSTMENT' then
            v_event_type := 'ADJUSTMENT';
            if v_entry.direction = 'CREDIT' then
                v_debit_account := 'PAYMENT_CLEARING';
                v_credit_account := 'DRIVER_AVAILABLE';
                v_credit_owner := v_entry.driver_id;
            else
                v_debit_account := 'DRIVER_AVAILABLE';
                v_debit_owner := v_entry.driver_id;
                v_credit_account := 'PAYMENT_CLEARING';
            end if;
        else
            raise exception 'Unsupported wallet entry type %', v_entry.entry_type;
    end case;

    insert into public.ride_ledger_transactions(
        idempotency_key,
        event_type,
        trip_id,
        currency,
        commission_policy_version,
        commission_basis_points,
        commissionable_base_minor,
        commission_amount_minor,
        rounding_mode,
        metadata
    )
    values (
        v_transaction_key,
        v_event_type,
        v_entry.trip_id,
        v_entry.currency,
        case
            when v_entry.entry_type like 'COMMISSION_%'
                then coalesce(
                    case
                        when char_length(
                            v_entry.metadata ->> 'commission_policy_version'
                        ) between 1 and 100
                        then v_entry.metadata ->> 'commission_policy_version'
                        else null
                    end,
                    'legacy-flat-fare-v0'
                )
            else null
        end,
        case
            when v_entry.entry_type like 'COMMISSION_%' then
                case
                    when coalesce(
                        v_entry.metadata ->> 'commission_basis_points',
                        ''
                    ) ~ '^[0-9]{1,5}$'
                    and (
                        v_entry.metadata ->> 'commission_basis_points'
                    )::integer between 0 and 10000
                    then (
                        v_entry.metadata ->> 'commission_basis_points'
                    )::integer
                    else 500
                end
            else null
        end,
        case
            when v_entry.entry_type like 'COMMISSION_%'
                 and coalesce(
                     v_entry.metadata ->> 'commissionable_base_minor',
                     ''
                 ) ~ '^[0-9]{1,19}$'
                then case
                    when (
                        v_entry.metadata ->> 'commissionable_base_minor'
                    )::numeric <= 9223372036854775807::numeric
                    then (
                        v_entry.metadata ->> 'commissionable_base_minor'
                    )::bigint
                    else null
                end
            else null
        end,
        case
            when v_entry.entry_type like 'COMMISSION_%'
                then v_entry.amount_minor
            else null
        end,
        case
            when v_entry.entry_type like 'COMMISSION_%'
                 and v_entry.metadata ->> 'rounding_mode' in (
                     'HALF_UP', 'FLOOR'
                 )
                then v_entry.metadata ->> 'rounding_mode'
            when v_entry.entry_type like 'COMMISSION_%' then 'HALF_UP'
            else null
        end,
        v_entry.metadata || jsonb_build_object(
            'source', 'ride_wallet_ledger',
            'source_entry_id', v_entry.id,
            'source_entry_type', v_entry.entry_type,
            'source_direction', v_entry.direction
        )
    )
    returning id into v_transaction_id;

    insert into public.ride_ledger_postings(
        transaction_id, entry_sequence, account_code, account_owner_id,
        direction, amount_minor, currency, metadata
    )
    values
    (
        v_transaction_id, 0, v_debit_account, v_debit_owner,
        'DEBIT', v_entry.amount_minor, v_entry.currency,
        jsonb_build_object('source_entry_id', v_entry.id)
    ),
    (
        v_transaction_id, 1, v_credit_account, v_credit_owner,
        'CREDIT', v_entry.amount_minor, v_entry.currency,
        jsonb_build_object('source_entry_id', v_entry.id)
    );

    return v_transaction_id;
end;
$$;

create or replace function public.ride_mirror_wallet_ledger_trigger()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    perform public.ride_mirror_wallet_ledger_entry(new.id);
    return new;
end;
$$;

drop trigger if exists ride_wallet_ledger_double_entry_mirror
    on public.ride_wallet_ledger;
create trigger ride_wallet_ledger_double_entry_mirror
after insert on public.ride_wallet_ledger
for each row execute function public.ride_mirror_wallet_ledger_trigger();

-- Backfill is idempotent and preserves every legacy source identifier.
do $$
declare
    v_entry record;
begin
    for v_entry in
        select l.id
          from public.ride_wallet_ledger l
         order by l.created_at, l.id
    loop
        perform public.ride_mirror_wallet_ledger_entry(v_entry.id);
    end loop;
end;
$$;

drop trigger if exists ride_ledger_transactions_immutable
    on public.ride_ledger_transactions;
create trigger ride_ledger_transactions_immutable
before update or delete on public.ride_ledger_transactions
for each row execute function public.ride_reject_immutable_change();

drop trigger if exists ride_ledger_postings_immutable
    on public.ride_ledger_postings;
create trigger ride_ledger_postings_immutable
before update or delete on public.ride_ledger_postings
for each row execute function public.ride_reject_immutable_change();

drop trigger if exists ride_commission_calculations_immutable
    on public.ride_commission_calculations;
create trigger ride_commission_calculations_immutable
before update or delete on public.ride_commission_calculations
for each row execute function public.ride_reject_immutable_change();

drop trigger if exists ride_revenue_split_rule_sets_immutable
    on public.ride_revenue_split_rule_sets;
create trigger ride_revenue_split_rule_sets_immutable
before update or delete on public.ride_revenue_split_rule_sets
for each row execute function public.ride_reject_immutable_change();

drop trigger if exists ride_revenue_split_rules_immutable
    on public.ride_revenue_split_rules;
create trigger ride_revenue_split_rules_immutable
before update or delete on public.ride_revenue_split_rules
for each row execute function public.ride_reject_immutable_change();

alter table public.ride_ledger_transactions enable row level security;
alter table public.ride_ledger_postings enable row level security;
alter table public.ride_commission_calculations enable row level security;
alter table public.ride_revenue_split_rule_sets enable row level security;
alter table public.ride_revenue_split_rules enable row level security;

revoke all on public.ride_ledger_transactions from anon, authenticated;
revoke all on public.ride_ledger_postings from anon, authenticated;
revoke all on public.ride_commission_calculations from anon, authenticated;
revoke all on public.ride_revenue_split_rule_sets from anon, authenticated;
revoke all on public.ride_revenue_split_rules from anon, authenticated;

revoke all on function public.ride_assert_journal_balanced() from public;
revoke all on function public.ride_assert_revenue_split_total() from public;
revoke all on function public.ride_mirror_wallet_ledger_entry(uuid) from public;
revoke all on function public.ride_mirror_wallet_ledger_trigger() from public;
