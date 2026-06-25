-- 1. Create gauge_listings table
CREATE TABLE IF NOT EXISTS gauge_listings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    creator_id TEXT,
    creator_name TEXT NOT NULL DEFAULT '',
    name TEXT NOT NULL,
    description TEXT,
    config_json TEXT NOT NULL DEFAULT '{}',
    thumbnail_url TEXT,
    price_tier INTEGER NOT NULL DEFAULT 1,
    total_sales INTEGER NOT NULL DEFAULT 0,
    total_revenue_cents INTEGER NOT NULL DEFAULT 0,
    creator_earnings_cents INTEGER NOT NULL DEFAULT 0,
    platform_earnings_cents INTEGER NOT NULL DEFAULT 0,
    avg_rating REAL NOT NULL DEFAULT 0.0,
    review_count INTEGER NOT NULL DEFAULT 0,
    download_count INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT timezone('utc'::text, now())
);

-- 2. Create gauge_purchases table
CREATE TABLE IF NOT EXISTS gauge_purchases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id UUID REFERENCES gauge_listings(id) ON DELETE CASCADE,
    buyer_id TEXT,
    purchase_token TEXT NOT NULL,
    price_cents INTEGER NOT NULL DEFAULT 0,
    creator_share_cents INTEGER NOT NULL DEFAULT 0,
    platform_share_cents INTEGER NOT NULL DEFAULT 0,
    purchased_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT timezone('utc'::text, now())
);

-- 3. Create gauge_reviews table
CREATE TABLE IF NOT EXISTS gauge_reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id UUID REFERENCES gauge_listings(id) ON DELETE CASCADE,
    reviewer_id TEXT,
    rating INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5),
    comment TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT timezone('utc'::text, now())
);

-- 4. Enable Row Level Security (RLS)
ALTER TABLE gauge_listings ENABLE ROW LEVEL SECURITY;
ALTER TABLE gauge_purchases ENABLE ROW LEVEL SECURITY;
ALTER TABLE gauge_reviews ENABLE ROW LEVEL SECURITY;

-- 5. Open access policies for testing & operations
CREATE POLICY "Public Read Access Listings" ON gauge_listings FOR SELECT USING (true);
CREATE POLICY "Public Write Access Listings" ON gauge_listings FOR INSERT WITH CHECK (true);
CREATE POLICY "Public Update Access Listings" ON gauge_listings FOR UPDATE USING (true);

CREATE POLICY "Public Read Access Purchases" ON gauge_purchases FOR SELECT USING (true);
CREATE POLICY "Public Write Access Purchases" ON gauge_purchases FOR INSERT WITH CHECK (true);

CREATE POLICY "Public Read Access Reviews" ON gauge_reviews FOR SELECT USING (true);
CREATE POLICY "Public Write Access Reviews" ON gauge_reviews FOR INSERT WITH CHECK (true);
CREATE POLICY "Public Update Access Reviews" ON gauge_reviews FOR UPDATE USING (true);
CREATE POLICY "Public Delete Access Reviews" ON gauge_reviews FOR DELETE USING (true);
;
