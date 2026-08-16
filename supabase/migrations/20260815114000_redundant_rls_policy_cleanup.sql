-- Remove semantically redundant permissive policies reported by the Supabase
-- advisor. A permissive policy with USING false can never grant access, and an
-- identical SELECT policy adds nothing when an ALL policy already grants the
-- same owner predicate. Removing them does not broaden access.

drop policy if exists feature_flags_no_client_write
    on public.feature_flags;
drop policy if exists "receipts no client write"
    on public.google_play_purchase_receipts;
drop policy if exists provider_plan_assignments_no_client_write
    on public.provider_plan_assignments;
drop policy if exists provider_rep_no_client_write
    on public.provider_reputation_scores;
drop policy if exists provider_verif_no_client_write
    on public.provider_verifications;
drop policy if exists repair_reports_no_client_write
    on public.repair_reports;
drop policy if exists repair_status_no_client_write
    on public.repair_status_events;
drop policy if exists repair_warranties_no_write
    on public.repair_warranties;
drop policy if exists repair_wo_no_client_write
    on public.repair_work_orders;
drop policy if exists "entitlements no client write"
    on public.user_entitlements;
drop policy if exists user_roles_no_client_write
    on public.user_roles;
drop policy if exists vehicle_timeline_no_client_write
    on public.vehicle_timeline_events;
drop policy if exists verified_badges_no_client_write
    on public.verified_badges;

drop policy if exists certified_reports_owner_read
    on public.certified_reports;
drop policy if exists diagnostic_snapshots_owner_read
    on public.diagnostic_snapshots;
drop policy if exists repair_actions_owner_read
    on public.repair_actions;
drop policy if exists report_evidence_owner_read
    on public.report_evidence;
