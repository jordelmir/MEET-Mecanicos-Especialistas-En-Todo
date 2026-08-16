-- Migration: Vanguard Brand Aliases Table
-- Maps legacy nomenclature (MEET, Repair Network, StackOverflow Mecánico, etc.) to canonical Vanguard names.

CREATE TABLE IF NOT EXISTS public.brand_aliases (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  legacy_name TEXT NOT NULL UNIQUE,
  canonical_name TEXT NOT NULL,
  context TEXT NOT NULL DEFAULT 'general',
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  deprecated_at TIMESTAMPTZ
);

ALTER TABLE public.brand_aliases ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS brand_aliases_read ON public.brand_aliases;

CREATE POLICY brand_aliases_read
ON public.brand_aliases
FOR SELECT
TO anon, authenticated
USING (active = TRUE);

INSERT INTO public.brand_aliases (legacy_name, canonical_name, context)
VALUES
  ('MEET', 'Elysium Vanguard', 'company'),
  ('MEET Mecanicos Especialistas En Todo', 'Elysium Vanguard', 'company'),
  ('MEET Web Portal', 'Vanguard Cloud', 'app_portal'),
  ('MEET ELITE', 'Vanguard Elite', 'plan'),
  ('Repair Network', 'Vanguard Repair', 'module'),
  ('StackOverflow Mecánico', 'Vanguard Community', 'module'),
  ('Pro Hub', 'Vanguard OEM', 'module'),
  ('MEET OBD2 Scanner', 'Vanguard Scan', 'module'),
  ('MEET Scan', 'Vanguard Scan', 'module'),
  ('Client Dashboard', 'Vanguard Garage', 'view'),
  ('Mechanic Dashboard', 'Vanguard Repair', 'view'),
  ('TV Dashboard', 'Vanguard Insights', 'view'),
  ('Service Catalog', 'Vanguard Parts', 'view'),
  ('Oscilloscope', 'Vanguard Studio', 'module'),
  ('Oscilloscope Canvas', 'Vanguard Studio', 'module'),
  ('Shop Settings', 'Vanguard Enterprise', 'view'),
  ('Analytics', 'Vanguard Analytics', 'module')
ON CONFLICT (legacy_name) DO UPDATE
SET canonical_name = EXCLUDED.canonical_name,
    context = EXCLUDED.context,
    active = EXCLUDED.active;
