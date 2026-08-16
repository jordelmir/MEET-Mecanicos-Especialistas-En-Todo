-- Vanguard commerce event stream and ledger.
-- The mobile app writes these through its durable local outbox; idempotency keys
-- make retries safe after network loss, double taps, and reconnects.

CREATE TABLE IF NOT EXISTS public.vanguard_events (
  event_id TEXT PRIMARY KEY,
  aggregate_type TEXT NOT NULL,
  aggregate_id TEXT NOT NULL,
  event_type TEXT NOT NULL,
  actor_id TEXT,
  actor_role TEXT,
  source TEXT NOT NULL,
  correlation_id TEXT,
  causation_id TEXT,
  idempotency_key TEXT NOT NULL UNIQUE,
  payload_json TEXT NOT NULL,
  schema_version INT NOT NULL DEFAULT 1,
  occurred_at_ms BIGINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.marketplace_ledger_entries (
  ledger_entry_id TEXT PRIMARY KEY,
  transaction_id TEXT NOT NULL,
  related_event_id TEXT NOT NULL REFERENCES public.vanguard_events(event_id) ON DELETE RESTRICT,
  order_type TEXT NOT NULL,
  order_id TEXT NOT NULL,
  participant_id TEXT,
  participant_role TEXT NOT NULL,
  entry_type TEXT NOT NULL,
  direction TEXT NOT NULL,
  amount_cents BIGINT NOT NULL CHECK (amount_cents >= 0),
  currency TEXT NOT NULL DEFAULT 'USD',
  status TEXT NOT NULL,
  metadata_json TEXT NOT NULL,
  created_at_ms BIGINT NOT NULL,
  settled_at_ms BIGINT,
  idempotency_key TEXT NOT NULL UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_vanguard_events_aggregate ON public.vanguard_events (aggregate_type, aggregate_id, occurred_at_ms);

CREATE INDEX IF NOT EXISTS idx_vanguard_events_event_type ON public.vanguard_events (event_type, occurred_at_ms);

CREATE INDEX IF NOT EXISTS idx_vanguard_events_correlation ON public.vanguard_events (correlation_id, occurred_at_ms);

CREATE INDEX IF NOT EXISTS idx_marketplace_ledger_transaction ON public.marketplace_ledger_entries (transaction_id);

CREATE INDEX IF NOT EXISTS idx_marketplace_ledger_order ON public.marketplace_ledger_entries (order_type, order_id);

CREATE INDEX IF NOT EXISTS idx_marketplace_ledger_event ON public.marketplace_ledger_entries (related_event_id);

CREATE INDEX IF NOT EXISTS idx_marketplace_ledger_status ON public.marketplace_ledger_entries (status, created_at_ms);

ALTER TABLE public.vanguard_events ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.marketplace_ledger_entries ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS vanguard_events_sync_all ON public.vanguard_events;

DROP POLICY IF EXISTS marketplace_ledger_entries_sync_all ON public.marketplace_ledger_entries;

CREATE POLICY vanguard_events_sync_all
ON public.vanguard_events
FOR ALL
USING (true)
WITH CHECK (true);
CREATE POLICY marketplace_ledger_entries_sync_all
ON public.marketplace_ledger_entries
FOR ALL
USING (true)
WITH CHECK (true);
