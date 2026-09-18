-- Chat evidence is append-only. Idempotent retries may upsert the same values,
-- but a sender must not rewrite the trip, content, media or timestamp later.
create or replace function public.ride_message_reject_mutation()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    if new is distinct from old then
        raise exception 'RIDE_MESSAGE_IMMUTABLE' using errcode = '23514';
    end if;
    return new;
end;
$$;

revoke all on function public.ride_message_reject_mutation() from public, anon, authenticated;

drop trigger if exists ride_message_immutable on public.ride_messages;
create trigger ride_message_immutable
before update on public.ride_messages
for each row execute function public.ride_message_reject_mutation();
