begin;

-- Truncate safety public projections and maps
truncate table public.safety_public_points cascade;
truncate table public.safety_public_accountability_projection cascade;
truncate table public.safety_public_case_claim_projection cascade;
truncate table public.safety_public_case_timeline_projection cascade;
truncate table public.safety_public_case_projection cascade;

-- Truncate safety reports and all cascade-dependent private/public tables
truncate table public.safety_reports cascade;

-- Clean up any residual safety claims or sources if not cascade-deleted
truncate table public.safety_claims cascade;
truncate table public.safety_sources cascade;

-- Clean up command dedup for safety commands
delete from public.safety_command_dedup where command_type in ('WITHDRAW_REPORT', 'CREATE_REPORT', 'SUBMIT_REPORT', 'SAFETY_REPORT');

commit;
