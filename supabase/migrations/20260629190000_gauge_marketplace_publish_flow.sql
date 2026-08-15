-- Gauge marketplace publish flow metadata.
-- Keeps existing listings compatible while adding the fields required by the APK sell form.

ALTER TABLE public.gauge_listings
ADD COLUMN IF NOT EXISTS product_id TEXT NOT NULL DEFAULT 'gauge_tier_1';

ALTER TABLE public.gauge_listings
ADD COLUMN IF NOT EXISTS sale_category TEXT NOT NULL DEFAULT 'performance';

ALTER TABLE public.gauge_listings
ADD COLUMN IF NOT EXISTS tags TEXT NOT NULL DEFAULT '';

ALTER TABLE public.gauge_listings
ADD COLUMN IF NOT EXISTS currency TEXT NOT NULL DEFAULT 'USD';

ALTER TABLE public.gauge_listings
ADD COLUMN IF NOT EXISTS seller_terms_accepted BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE public.gauge_listings
ADD COLUMN IF NOT EXISTS published_from_saved_gauge_id TEXT;

CREATE INDEX IF NOT EXISTS idx_gauge_listings_creator_active_updated
ON public.gauge_listings (creator_id, is_active, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_gauge_listings_category_active_sales
ON public.gauge_listings (sale_category, is_active, total_sales DESC);

UPDATE public.gauge_listings
SET product_id = 'gauge_tier_' || LEAST(GREATEST(price_tier, 1), 10)::TEXT
WHERE product_id = 'gauge_tier_1'
  AND price_tier IS NOT NULL;
