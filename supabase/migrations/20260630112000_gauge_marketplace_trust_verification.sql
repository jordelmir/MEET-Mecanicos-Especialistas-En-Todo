-- Gauge Marketplace trust and seller verification flow.
-- Public creator publishing remains paid-only in the APK. These columns keep
-- seller identity explicit without marking anyone as Verified before review.

ALTER TABLE public.gauge_listings
ADD COLUMN IF NOT EXISTS seller_claim_type TEXT NOT NULL DEFAULT 'independent_creator';

ALTER TABLE public.gauge_listings
ADD COLUMN IF NOT EXISTS seller_claim_name TEXT;

ALTER TABLE public.gauge_listings
ADD COLUMN IF NOT EXISTS seller_contact_email TEXT;

ALTER TABLE public.gauge_listings
ADD COLUMN IF NOT EXISTS seller_website TEXT;

ALTER TABLE public.gauge_listings
ADD COLUMN IF NOT EXISTS seller_verification_status TEXT NOT NULL DEFAULT 'unverified';

ALTER TABLE public.gauge_listings
ADD COLUMN IF NOT EXISTS seller_verified_badge TEXT;

CREATE INDEX IF NOT EXISTS idx_gauge_listings_paid_active_recent
ON public.gauge_listings (is_active, price_tier, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_gauge_listings_seller_verification
ON public.gauge_listings (seller_verification_status, seller_claim_type);

CREATE TABLE IF NOT EXISTS public.gauge_seller_verification_requests (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  listing_id UUID NOT NULL REFERENCES public.gauge_listings(id) ON DELETE CASCADE,
  creator_id TEXT NOT NULL,
  claim_type TEXT NOT NULL DEFAULT 'independent_creator'
    CHECK (claim_type IN ('independent_creator', 'workshop', 'brand', 'oem', 'manufacturer', 'parts_store')),
  claim_name TEXT NOT NULL,
  contact_email TEXT,
  website TEXT,
  evidence_summary TEXT NOT NULL DEFAULT '',
  requested_badge TEXT NOT NULL DEFAULT 'verified_creator',
  status TEXT NOT NULL DEFAULT 'pending'
    CHECK (status IN ('pending', 'needs_more_info', 'approved', 'rejected', 'expired', 'revoked')),
  reviewer_id UUID,
  reviewer_notes TEXT,
  reviewed_at TIMESTAMPTZ,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_gauge_seller_verif_listing_status
ON public.gauge_seller_verification_requests (listing_id, status);

CREATE INDEX IF NOT EXISTS idx_gauge_seller_verif_creator_status
ON public.gauge_seller_verification_requests (creator_id, status);

ALTER TABLE public.gauge_seller_verification_requests ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS gauge_seller_verification_public_read ON public.gauge_seller_verification_requests;

CREATE POLICY gauge_seller_verification_public_read
ON public.gauge_seller_verification_requests
FOR SELECT
TO anon, authenticated
USING (true);

DROP POLICY IF EXISTS gauge_seller_verification_public_insert ON public.gauge_seller_verification_requests;

CREATE POLICY gauge_seller_verification_public_insert
ON public.gauge_seller_verification_requests
FOR INSERT
TO anon, authenticated
WITH CHECK (true);

DROP POLICY IF EXISTS gauge_seller_verification_public_update ON public.gauge_seller_verification_requests;

CREATE POLICY gauge_seller_verification_public_update
ON public.gauge_seller_verification_requests
FOR UPDATE
TO anon, authenticated
USING (true)
WITH CHECK (true);

UPDATE public.gauge_listings
SET seller_claim_name = COALESCE(NULLIF(seller_claim_name, ''), creator_name),
    seller_verification_status = COALESCE(NULLIF(seller_verification_status, ''), 'unverified')
WHERE seller_claim_name IS NULL
   OR seller_verification_status IS NULL
   OR seller_verification_status = '';
