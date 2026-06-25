-- Production-grade RLS for MEET parts auctions.
-- Android uses camelCase serialized fields, so these columns are quoted intentionally.

CREATE TABLE IF NOT EXISTS public.parts_stores (
  "storeId" TEXT PRIMARY KEY,
  "storeName" TEXT NOT NULL,
  rating DOUBLE PRECISION NOT NULL DEFAULT 0,
  phone TEXT NOT NULL DEFAULT '',
  location TEXT NOT NULL DEFAULT '',
  "deliveryRadiusKm" DOUBLE PRECISION NOT NULL DEFAULT 0,
  "averageEtaMinutes" INTEGER NOT NULL DEFAULT 60,
  verified BOOLEAN NOT NULL DEFAULT false,
  "createdAt" BIGINT NOT NULL DEFAULT (extract(epoch from now()) * 1000)::BIGINT,
  owner_id UUID NOT NULL DEFAULT auth.uid()
);

CREATE TABLE IF NOT EXISTS public.part_requests (
  "requestId" TEXT PRIMARY KEY,
  "serviceRequestId" TEXT,
  "vehicleId" TEXT NOT NULL,
  "dtcCode" TEXT,
  "partName" TEXT NOT NULL,
  "partNumber" TEXT,
  quantity INTEGER NOT NULL DEFAULT 1 CHECK (quantity > 0),
  "oemPreference" TEXT NOT NULL DEFAULT 'ANY' CHECK ("oemPreference" IN ('OEM', 'AFTERMARKET', 'ANY')),
  "deliveryLocation" TEXT NOT NULL,
  "urgencyMinutes" INTEGER NOT NULL DEFAULT 40 CHECK ("urgencyMinutes" >= 0),
  "customerNotes" TEXT NOT NULL DEFAULT '',
  status TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'ACCEPTED', 'DELIVERED', 'CANCELLED')),
  "acceptedOfferId" TEXT,
  "createdAt" BIGINT NOT NULL DEFAULT (extract(epoch from now()) * 1000)::BIGINT,
  customer_id UUID NOT NULL DEFAULT auth.uid()
);

CREATE TABLE IF NOT EXISTS public.part_offers (
  "offerId" TEXT PRIMARY KEY,
  "partRequestId" TEXT NOT NULL REFERENCES public.part_requests("requestId") ON DELETE CASCADE,
  "storeId" TEXT NOT NULL REFERENCES public.parts_stores("storeId") ON DELETE CASCADE,
  "storeName" TEXT NOT NULL,
  brand TEXT NOT NULL DEFAULT 'Por confirmar',
  "partNumber" TEXT NOT NULL DEFAULT 'Por confirmar',
  condition TEXT NOT NULL DEFAULT 'NEW' CHECK (condition IN ('NEW', 'OEM', 'USED_TESTED', 'REMAN')),
  price DOUBLE PRECISION NOT NULL DEFAULT 0 CHECK (price >= 0),
  "deliveryFee" DOUBLE PRECISION NOT NULL DEFAULT 0 CHECK ("deliveryFee" >= 0),
  "etaMinutes" INTEGER NOT NULL DEFAULT 40 CHECK ("etaMinutes" >= 0),
  "warrantyDays" INTEGER NOT NULL DEFAULT 0 CHECK ("warrantyDays" >= 0),
  message TEXT NOT NULL DEFAULT '',
  status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED')),
  "createdAt" BIGINT NOT NULL DEFAULT (extract(epoch from now()) * 1000)::BIGINT,
  store_owner_id UUID NOT NULL DEFAULT auth.uid()
);

ALTER TABLE public.parts_stores
  ADD COLUMN IF NOT EXISTS owner_id UUID NOT NULL DEFAULT auth.uid();

ALTER TABLE public.part_requests
  ADD COLUMN IF NOT EXISTS customer_id UUID NOT NULL DEFAULT auth.uid();

ALTER TABLE public.part_offers
  ADD COLUMN IF NOT EXISTS store_owner_id UUID NOT NULL DEFAULT auth.uid();

CREATE INDEX IF NOT EXISTS idx_parts_stores_owner ON public.parts_stores (owner_id);
CREATE INDEX IF NOT EXISTS idx_part_requests_customer_status ON public.part_requests (customer_id, status, "createdAt" DESC);
CREATE INDEX IF NOT EXISTS idx_part_requests_open_eta ON public.part_requests (status, "urgencyMinutes", "createdAt" DESC);
CREATE INDEX IF NOT EXISTS idx_part_offers_request_eta_price ON public.part_offers ("partRequestId", "etaMinutes", price);
CREATE INDEX IF NOT EXISTS idx_part_offers_owner ON public.part_offers (store_owner_id);

ALTER TABLE public.parts_stores ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.part_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.part_offers ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON public.parts_stores FROM anon;
REVOKE ALL ON public.part_requests FROM anon;
REVOKE ALL ON public.part_offers FROM anon;

GRANT SELECT ON public.parts_stores TO anon;
GRANT SELECT, INSERT, UPDATE ON public.parts_stores TO authenticated;
GRANT SELECT, INSERT, UPDATE ON public.part_requests TO authenticated;
GRANT SELECT, INSERT, UPDATE ON public.part_offers TO authenticated;

DROP POLICY IF EXISTS parts_stores_anon_read_verified ON public.parts_stores;
DROP POLICY IF EXISTS parts_stores_authenticated_read ON public.parts_stores;
DROP POLICY IF EXISTS parts_stores_insert_own ON public.parts_stores;
DROP POLICY IF EXISTS parts_stores_update_own ON public.parts_stores;
DROP POLICY IF EXISTS part_requests_read_marketplace ON public.part_requests;
DROP POLICY IF EXISTS part_requests_insert_own ON public.part_requests;
DROP POLICY IF EXISTS part_requests_update_own ON public.part_requests;
DROP POLICY IF EXISTS part_offers_read_participants ON public.part_offers;
DROP POLICY IF EXISTS part_offers_insert_store_owner ON public.part_offers;
DROP POLICY IF EXISTS part_offers_update_store_owner_limited ON public.part_offers;
DROP POLICY IF EXISTS part_offers_update_request_customer ON public.part_offers;

CREATE POLICY parts_stores_anon_read_verified
ON public.parts_stores
FOR SELECT
TO anon
USING (verified = true);

CREATE POLICY parts_stores_authenticated_read
ON public.parts_stores
FOR SELECT
TO authenticated
USING (verified = true OR owner_id = auth.uid());

CREATE POLICY parts_stores_insert_own
ON public.parts_stores
FOR INSERT
TO authenticated
WITH CHECK (owner_id = auth.uid());

CREATE POLICY parts_stores_update_own
ON public.parts_stores
FOR UPDATE
TO authenticated
USING (owner_id = auth.uid())
WITH CHECK (owner_id = auth.uid());

CREATE POLICY part_requests_read_marketplace
ON public.part_requests
FOR SELECT
TO authenticated
USING (
  status = 'OPEN'
  OR customer_id = auth.uid()
  OR EXISTS (
    SELECT 1
    FROM public.part_offers po
    WHERE po."partRequestId" = part_requests."requestId"
      AND po.store_owner_id = auth.uid()
  )
);

CREATE POLICY part_requests_insert_own
ON public.part_requests
FOR INSERT
TO authenticated
WITH CHECK (customer_id = auth.uid());

CREATE POLICY part_requests_update_own
ON public.part_requests
FOR UPDATE
TO authenticated
USING (customer_id = auth.uid())
WITH CHECK (customer_id = auth.uid());

CREATE POLICY part_offers_read_participants
ON public.part_offers
FOR SELECT
TO authenticated
USING (
  store_owner_id = auth.uid()
  OR EXISTS (
    SELECT 1
    FROM public.part_requests pr
    WHERE pr."requestId" = part_offers."partRequestId"
      AND pr.customer_id = auth.uid()
  )
);

CREATE POLICY part_offers_insert_store_owner
ON public.part_offers
FOR INSERT
TO authenticated
WITH CHECK (
  store_owner_id = auth.uid()
  AND EXISTS (
    SELECT 1
    FROM public.part_requests pr
    WHERE pr."requestId" = part_offers."partRequestId"
      AND pr.status = 'OPEN'
  )
);

CREATE POLICY part_offers_update_store_owner_limited
ON public.part_offers
FOR UPDATE
TO authenticated
USING (store_owner_id = auth.uid())
WITH CHECK (store_owner_id = auth.uid() AND status IN ('PENDING', 'CANCELLED'));

CREATE POLICY part_offers_update_request_customer
ON public.part_offers
FOR UPDATE
TO authenticated
USING (
  EXISTS (
    SELECT 1
    FROM public.part_requests pr
    WHERE pr."requestId" = part_offers."partRequestId"
      AND pr.customer_id = auth.uid()
  )
)
WITH CHECK (
  EXISTS (
    SELECT 1
    FROM public.part_requests pr
    WHERE pr."requestId" = part_offers."partRequestId"
      AND pr.customer_id = auth.uid()
  )
);

CREATE OR REPLACE FUNCTION public.sync_accepted_part_offer()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF NEW."acceptedOfferId" IS NOT NULL
     AND (OLD."acceptedOfferId" IS DISTINCT FROM NEW."acceptedOfferId" OR OLD.status IS DISTINCT FROM NEW.status) THEN
    UPDATE public.part_offers
    SET status = CASE
      WHEN "offerId" = NEW."acceptedOfferId" THEN 'ACCEPTED'
      WHEN status = 'PENDING' THEN 'REJECTED'
      ELSE status
    END
    WHERE "partRequestId" = NEW."requestId";
  END IF;

  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_sync_accepted_part_offer ON public.part_requests;
CREATE TRIGGER trg_sync_accepted_part_offer
AFTER UPDATE OF status, "acceptedOfferId" ON public.part_requests
FOR EACH ROW
WHEN (NEW.status = 'ACCEPTED')
EXECUTE FUNCTION public.sync_accepted_part_offer();
