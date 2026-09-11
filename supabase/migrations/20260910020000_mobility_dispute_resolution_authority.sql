-- Participants may file a case, but may not manufacture an operator resolution.
-- Preserve existing evidence. This migration does not rewrite historical cases.
BEGIN;

CREATE OR REPLACE FUNCTION public.mobility_guard_dispute_authority()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, public
AS $$
BEGIN
    IF current_user NOT IN ('service_role', 'supabase_admin')
       AND NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = current_user AND rolsuper) THEN
        IF TG_OP <> 'INSERT' THEN
            RAISE EXCEPTION 'DISPUTE_RESOLUTION_REQUIRES_PRIVILEGED_ACTOR' USING ERRCODE = '42501';
        END IF;
        IF NEW.state <> 'OPEN' OR NEW.resolution_notes IS NOT NULL OR NEW.resolved_at IS NOT NULL THEN
            RAISE EXCEPTION 'DISPUTE_MUST_OPEN_WITHOUT_RESOLUTION' USING ERRCODE = '42501';
        END IF;
        -- Evidence timestamps come from the database, not the participant clock.
        NEW.created_at := clock_timestamp();
        NEW.updated_at := NEW.created_at;
    END IF;
    RETURN NEW;
END;
$$;

REVOKE ALL ON FUNCTION public.mobility_guard_dispute_authority() FROM PUBLIC, anon, authenticated;
DROP TRIGGER IF EXISTS mobility_dispute_authority_guard ON public.mobility_trip_disputes;
CREATE TRIGGER mobility_dispute_authority_guard
BEFORE INSERT OR UPDATE ON public.mobility_trip_disputes
FOR EACH ROW EXECUTE FUNCTION public.mobility_guard_dispute_authority();

REVOKE UPDATE, DELETE ON public.mobility_trip_disputes FROM anon, authenticated;
DROP POLICY IF EXISTS p_disputes_participant_insert ON public.mobility_trip_disputes;
CREATE POLICY p_disputes_participant_insert ON public.mobility_trip_disputes
FOR INSERT TO authenticated
WITH CHECK (
    opened_by = auth.uid()
    AND state = 'OPEN'
    AND resolution_notes IS NULL
    AND resolved_at IS NULL
    AND EXISTS (
        SELECT 1 FROM public.trips t
        WHERE t.trip_id = mobility_trip_disputes.trip_id
          AND (t.rider_id = auth.uid() OR t.driver_id = auth.uid())
    )
);
COMMIT;
