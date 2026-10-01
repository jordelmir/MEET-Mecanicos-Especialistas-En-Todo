do $$ begin
 if encode(extensions.digest(convert_to(concat_ws(chr(31),'MEET-SAFETY-CUSTODY-V2','11111111-1111-4111-8111-111111111111','22222222-2222-4222-8222-222222222222','','SERVER_VERIFIED','SERVER','BYTE_MATCH','aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',to_char('2026-09-30T21:22:23.123456Z'::timestamptz at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"')),'UTF8'),'sha256'),'hex') <> '8e688cf3ade34b33165cf5583443ec482781e285ccae67cbfc617499315b3917' then raise exception 'Safety custody PostgreSQL parity failed'; end if;
end $$;
