CREATE OR REPLACE FUNCTION public.ride_mirror_wallet_ledger_entry(p_wallet_entry_id uuid)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
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
$function$
;

create function public.ride_command_error(text,text,boolean,jsonb default null) returns jsonb language sql as $$select jsonb_build_object('ok',false,'code',$1)$$;
create function public.ride_command_success(jsonb) returns jsonb language sql as $$select $1||'{"ok":true}'::jsonb$$;
create function public.ride_command_hash(jsonb) returns text language sql as $$select md5($1::text)$$;
create function public.ride_command_replay(uuid,text,text) returns jsonb language sql as $$select null::jsonb$$;
create function public.ride_record_command_receipt(uuid,uuid,text,text,text,jsonb) returns jsonb language sql as $$select $6$$;
create function test_wallet_mirror() returns trigger language plpgsql as $$begin perform public.ride_mirror_wallet_ledger_entry(NEW.id); return NEW; end$$;
create trigger test_wallet_mirror after insert on ride_wallet_ledger for each row execute function test_wallet_mirror();
create trigger test_wallet_balance before insert on ride_wallet_ledger for each row execute function ride_wallet_balance_guard();
