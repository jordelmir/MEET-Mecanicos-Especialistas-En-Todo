-- Make API privileges match the fail-closed RLS policies exactly. Supabase
-- default grants are intentionally removed from anonymous callers.
revoke all on table
    public.vehicle_access_credentials,
    public.vehicle_access_grants,
    public.vehicle_access_events,
    public.communication_devices,
    public.communication_conversations,
    public.communication_participants,
    public.communication_events,
    public.communication_receipts,
    public.communication_blocks,
    public.communication_call_sessions,
    public.communication_identity_profiles,
    public.communication_identity_aliases,
    public.communication_privacy_settings,
    public.communication_direct_links,
    public.communication_message_requests,
    public.communication_presence_leases,
    public.communication_discovery_attempts
from anon, public;

grant select, insert, update, delete on table
    public.vehicle_access_credentials,
    public.vehicle_access_grants
to authenticated;
grant select on table public.vehicle_access_events to authenticated;

grant select, insert on table public.communication_devices to authenticated;
grant select on table
    public.communication_conversations,
    public.communication_participants,
    public.communication_call_sessions,
    public.communication_identity_aliases,
    public.communication_privacy_settings,
    public.communication_direct_links,
    public.communication_message_requests,
    public.communication_presence_leases
to authenticated;
grant select, insert on table
    public.communication_events,
    public.communication_receipts
to authenticated;
grant select, insert, delete on table public.communication_blocks to authenticated;
grant select, insert, update on table public.communication_identity_profiles to authenticated;

-- Discovery attempts are written only inside the SECURITY DEFINER lookup RPC.
revoke all on table public.communication_discovery_attempts from authenticated;
