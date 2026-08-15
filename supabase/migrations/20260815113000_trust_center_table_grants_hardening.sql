-- Defense in depth for the Trust Center Data API surface. RLS already denies
-- anonymous rows; these grants ensure anonymous clients cannot attempt table
-- operations at all and authenticated mutations remain RPC-only.

revoke all on table public.platform_authorities
    from anon, authenticated;

revoke all on table public.service_verification_applications
    from anon;
revoke insert, update, delete, truncate, references, trigger
    on table public.service_verification_applications
    from authenticated;
grant select on table public.service_verification_applications
    to authenticated;

revoke all on table public.service_verification_audit_events
    from anon;
revoke insert, update, delete, truncate, references, trigger
    on table public.service_verification_audit_events
    from authenticated;
grant select on table public.service_verification_audit_events
    to authenticated;

revoke all on sequence public.service_verification_audit_events_id_seq
    from anon, authenticated;
