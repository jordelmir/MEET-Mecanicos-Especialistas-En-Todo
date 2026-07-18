-- ============================================================
-- ELYSIUM VANGUARD / MEET — AI Usage Events Schema
-- Appended to existing Supabase schema
-- ============================================================

-- AI Usage Events table: tracks every AI provider call for analytics,
-- cost monitoring, and abuse detection.
CREATE TABLE IF NOT EXISTS ai_usage_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    session_id TEXT,
    feature TEXT NOT NULL,               -- e.g. DIAGNOSTIC_DTC, AI_COPILOT, LIVE_PID_ANALYSIS
    provider_id TEXT NOT NULL,           -- e.g. minimax, openai, gemini
    model TEXT NOT NULL,                 -- e.g. MiniMax-M1, gpt-4o
    prompt_tokens INT DEFAULT 0,
    completion_tokens INT DEFAULT 0,
    total_tokens INT DEFAULT 0,
    latency_ms BIGINT DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'success',  -- success, error, timeout, rate_limited
    error_message TEXT,                  -- redacted error details
    cost_usd NUMERIC(10, 6) DEFAULT 0,   -- estimated cost
    created_at TIMESTAMPTZ DEFAULT now(),
    metadata JSONB DEFAULT '{}'::jsonb    -- extra context (vehicle_id, dtc_codes, etc.)
);

-- Index for user-based queries and analytics dashboards
CREATE INDEX IF NOT EXISTS idx_ai_usage_user_id ON ai_usage_events(user_id);
CREATE INDEX IF NOT EXISTS idx_ai_usage_created_at ON ai_usage_events(created_at);
CREATE INDEX IF NOT EXISTS idx_ai_usage_provider ON ai_usage_events(provider_id);
CREATE INDEX IF NOT EXISTS idx_ai_usage_feature ON ai_usage_events(feature);

-- Row Level Security: users can only read their own usage events
ALTER TABLE ai_usage_events ENABLE ROW LEVEL SECURITY;

CREATE POLICY ai_usage_select_own ON ai_usage_events
    FOR SELECT USING (auth.uid() = user_id);

CREATE POLICY ai_usage_insert_own ON ai_usage_events
    FOR INSERT WITH CHECK (auth.uid() = user_id);

-- Admin policy (for dashboard analytics)
CREATE POLICY ai_usage_admin_all ON ai_usage_events
    FOR ALL USING (
        EXISTS (
            SELECT 1 FROM auth.users
            WHERE auth.users.id = auth.uid()
            AND auth.users.raw_user_meta_data->>'role' = 'admin'
        )
    );
