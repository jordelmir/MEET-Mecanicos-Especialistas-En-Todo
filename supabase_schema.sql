-- MEET OBD2 CLOUD INFRASTRUCTURE

CREATE TABLE IF NOT EXISTS oem_pids (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  make TEXT NOT NULL,
  model TEXT,
  year_start INT,
  year_end INT,
  ecu_name TEXT NOT NULL,
  ecu_header TEXT NOT NULL,
  pid_hex TEXT NOT NULL,
  pid_name TEXT NOT NULL,
  description TEXT,
  formula TEXT,
  unit TEXT,
  min_val FLOAT DEFAULT 0,
  max_val FLOAT DEFAULT 100,
  service_mode TEXT DEFAULT '01',
  protocol TEXT DEFAULT 'CAN',
  category TEXT DEFAULT 'sensor',
  is_pro_only BOOLEAN DEFAULT false,
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS dtc_definitions (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  code TEXT NOT NULL,
  description_en TEXT,
  description_es TEXT,
  system TEXT,
  severity TEXT DEFAULT 'low',
  make TEXT,
  possible_causes TEXT,
  suggested_fix TEXT,
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS scan_sessions (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  user_id TEXT NOT NULL,
  vehicle_vin TEXT,
  vehicle_make TEXT,
  vehicle_model TEXT,
  vehicle_year INT,
  vehicle_plate TEXT,
  adapter_type TEXT DEFAULT 'clone',
  scan_type TEXT DEFAULT 'quick',
  dtcs_found JSONB DEFAULT '[]',
  severity TEXT DEFAULT 'low',
  live_data_snapshot JSONB DEFAULT '{}',
  freeze_frame JSONB DEFAULT '{}',
  notes TEXT,
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS cloud_vehicles (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  user_id TEXT NOT NULL,
  vin TEXT,
  make TEXT NOT NULL,
  model TEXT NOT NULL,
  year INT,
  engine TEXT,
  plate TEXT,
  odometer INT DEFAULT 0,
  nickname TEXT,
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS subscriptions (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  user_id TEXT NOT NULL UNIQUE,
  plan TEXT DEFAULT 'free',
  status TEXT DEFAULT 'active',
  started_at TIMESTAMPTZ DEFAULT now(),
  expires_at TIMESTAMPTZ,
  receipt_data TEXT,
  provider TEXT DEFAULT 'revenuecat'
);

CREATE TABLE IF NOT EXISTS service_resets (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  make TEXT NOT NULL,
  model TEXT,
  year_start INT,
  year_end INT,
  reset_name TEXT NOT NULL,
  description TEXT,
  command_hex TEXT NOT NULL,
  ecu_header TEXT,
  requires_pro BOOLEAN DEFAULT true,
  category TEXT DEFAULT 'maintenance',
  created_at TIMESTAMPTZ DEFAULT now()
);

ALTER TABLE oem_pids ENABLE ROW LEVEL SECURITY;
ALTER TABLE dtc_definitions ENABLE ROW LEVEL SECURITY;
ALTER TABLE scan_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE cloud_vehicles ENABLE ROW LEVEL SECURITY;
ALTER TABLE subscriptions ENABLE ROW LEVEL SECURITY;
ALTER TABLE service_resets ENABLE ROW LEVEL SECURITY;

-- RLS Policies (Note: For production, replace "true" with auth.uid() checks and role-based mechanics/admin checks)
CREATE POLICY "Public read oem_pids" ON oem_pids FOR SELECT USING (true);
CREATE POLICY "Public read dtc_definitions" ON dtc_definitions FOR SELECT USING (true);
CREATE POLICY "Public read service_resets" ON service_resets FOR SELECT USING (true);
CREATE POLICY "Users manage own scans and Mechanics view all" ON scan_sessions FOR ALL USING (true);
CREATE POLICY "Users manage own vehicles and Mechanics view all" ON cloud_vehicles FOR ALL USING (true);
CREATE POLICY "Users manage own subs" ON subscriptions FOR ALL USING (true);

CREATE TABLE IF NOT EXISTS trips (
  id UUID PRIMARY KEY,
  user_id TEXT NOT NULL,
  vehicle_id TEXT NOT NULL,
  session_id TEXT NOT NULL,
  started_at BIGINT NOT NULL,
  ended_at BIGINT,
  distance_km FLOAT NOT NULL DEFAULT 0,
  duration_seconds BIGINT NOT NULL DEFAULT 0,
  avg_speed_kmh FLOAT NOT NULL DEFAULT 0,
  max_speed_kmh FLOAT NOT NULL DEFAULT 0,
  max_rpm FLOAT NOT NULL DEFAULT 0,
  avg_rpm FLOAT NOT NULL DEFAULT 0,
  max_temp_c FLOAT NOT NULL DEFAULT 0,
  fuel_efficiency FLOAT,
  eco_score INT NOT NULL DEFAULT 100,
  gps_track_json JSONB,
  created_at TIMESTAMPTZ DEFAULT now()
);

ALTER TABLE trips ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users manage own trips" ON trips FOR ALL USING (true);

-- CORE SHOP MANAGEMENT (Real Data Integration)

CREATE TABLE IF NOT EXISTS shop_settings (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  rules TEXT NOT NULL,
  open_hour INT NOT NULL DEFAULT 8,
  close_hour INT NOT NULL DEFAULT 18,
  time_slice_minutes INT NOT NULL DEFAULT 30,
  free_wash_threshold FLOAT NOT NULL DEFAULT 45000,
  updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS mechanics (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  name TEXT NOT NULL,
  phone TEXT NOT NULL,
  identification TEXT NOT NULL UNIQUE,
  access_code TEXT NOT NULL,
  email TEXT NOT NULL UNIQUE,
  specialty TEXT NOT NULL,
  efficiency_factor FLOAT NOT NULL DEFAULT 1.0,
  avatar TEXT,
  certifications JSONB DEFAULT '[]',
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS services (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  name TEXT NOT NULL,
  category TEXT NOT NULL,
  estimated_minutes INT NOT NULL,
  base_price FLOAT NOT NULL,
  description TEXT,
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS clients (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  name TEXT NOT NULL,
  phone TEXT NOT NULL,
  email TEXT NOT NULL UNIQUE,
  identification TEXT NOT NULL UNIQUE,
  access_code TEXT NOT NULL,
  loyalty_points INT DEFAULT 0,
  join_date TIMESTAMPTZ DEFAULT now(),
  last_visit TIMESTAMPTZ,
  notes TEXT,
  avatar TEXT,
  created_at TIMESTAMPTZ DEFAULT now()
);

-- Note: client vehicles can use cloud_vehicles table, we just link it via user_id -> clients.id

CREATE TABLE IF NOT EXISTS work_orders (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  client_id UUID REFERENCES clients(id) ON DELETE CASCADE,
  client_name TEXT NOT NULL,
  mechanic_id UUID REFERENCES mechanics(id) ON DELETE SET NULL,
  service_id UUID REFERENCES services(id) ON DELETE RESTRICT,
  vehicle_info JSONB NOT NULL,
  start_time TIMESTAMPTZ NOT NULL,
  estimated_end_time TIMESTAMPTZ NOT NULL,
  actual_start_time TIMESTAMPTZ,
  actual_end_time TIMESTAMPTZ,
  status TEXT NOT NULL DEFAULT 'RECEIVED',
  notes TEXT,
  diagnostic_notes TEXT,
  price FLOAT NOT NULL,
  estimated_minutes INT NOT NULL,
  cancellation_reason TEXT,
  cancellation_date TIMESTAMPTZ,
  parts_needed JSONB DEFAULT '[]',
  parts_ready BOOLEAN DEFAULT false,
  created_at TIMESTAMPTZ DEFAULT now(),
  updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS service_history (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  client_id UUID REFERENCES clients(id) ON DELETE CASCADE,
  date TIMESTAMPTZ NOT NULL,
  service_name TEXT NOT NULL,
  mechanic_name TEXT NOT NULL,
  price FLOAT NOT NULL,
  vehicle_info TEXT NOT NULL,
  notes TEXT,
  created_at TIMESTAMPTZ DEFAULT now()
);

-- Enable RLS for all new tables
ALTER TABLE shop_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE mechanics ENABLE ROW LEVEL SECURITY;
ALTER TABLE services ENABLE ROW LEVEL SECURITY;
ALTER TABLE clients ENABLE ROW LEVEL SECURITY;
ALTER TABLE work_orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE service_history ENABLE ROW LEVEL SECURITY;

-- Production RLS Policies (Draft for migration)
CREATE POLICY "Public read shop settings" ON shop_settings FOR SELECT USING (true);
CREATE POLICY "Public read services" ON services FOR SELECT USING (true);

-- Authenticated Users Policies (Requires Supabase Auth UUID to be linked to clients or mechanics)
CREATE POLICY "Clients manage their own data" ON clients FOR ALL USING (true);
CREATE POLICY "Mechanics manage their own data" ON mechanics FOR ALL USING (true);
CREATE POLICY "Work orders are visible to everyone in the shop" ON work_orders FOR ALL USING (true);
CREATE POLICY "Service history is visible" ON service_history FOR ALL USING (true);

-- ═══════════════════════════════════════════════════════════════
// NEW EXTENSION TABLES (MEET PRO & ELITE)
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS live_sessions (
  id TEXT PRIMARY KEY,
  vehicle_id TEXT NOT NULL,
  owner_id TEXT NOT NULL,
  mechanic_id TEXT,
  status TEXT NOT NULL DEFAULT 'PENDING',
  started_at BIGINT NOT NULL,
  ended_at BIGINT,
  permissions TEXT NOT NULL DEFAULT 'READ_ONLY',
  session_code TEXT NOT NULL UNIQUE,
  share_url TEXT NOT NULL,
  duration_minutes INT NOT NULL DEFAULT 30,
  video_call_url TEXT,
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS live_snapshots (
  id TEXT PRIMARY KEY,
  session_id TEXT NOT NULL REFERENCES live_sessions(id) ON DELETE CASCADE,
  timestamp BIGINT NOT NULL,
  pid_values JSONB NOT NULL DEFAULT '{}',
  notes TEXT,
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS mechanic_notes (
  id TEXT PRIMARY KEY,
  session_id TEXT NOT NULL REFERENCES live_sessions(id) ON DELETE CASCADE,
  author_id TEXT NOT NULL,
  content TEXT NOT NULL,
  created_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS repair_photos (
  id TEXT PRIMARY KEY,
  case_id TEXT NOT NULL,
  photo_path TEXT NOT NULL,
  caption TEXT,
  created_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS repair_parts (
  id TEXT PRIMARY KEY,
  case_id TEXT NOT NULL,
  part_number TEXT NOT NULL,
  part_name TEXT NOT NULL,
  price FLOAT NOT NULL DEFAULT 0,
  brand TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS repair_votes (
  id TEXT PRIMARY KEY,
  case_id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  vote_type TEXT NOT NULL, -- 'UP' or 'DOWN'
  UNIQUE(case_id, user_id)
);

CREATE TABLE IF NOT EXISTS repair_comments (
  id TEXT PRIMARY KEY,
  case_id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  user_name TEXT NOT NULL,
  user_reputation TEXT NOT NULL,
  content TEXT NOT NULL,
  created_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS repair_verifications (
  id TEXT PRIMARY KEY,
  case_id TEXT NOT NULL,
  verifier_id TEXT NOT NULL,
  verifier_name TEXT NOT NULL,
  verifier_credential TEXT NOT NULL,
  verified_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS service_requests (
  id TEXT PRIMARY KEY,
  vehicle_id TEXT NOT NULL,
  problem TEXT NOT NULL,
  priority TEXT NOT NULL DEFAULT 'MEDIUM',
  description TEXT NOT NULL,
  location TEXT NOT NULL,
  radius_km FLOAT NOT NULL DEFAULT 10.0,
  status TEXT NOT NULL DEFAULT 'OPEN',
  auto_dtc_code TEXT,
  created_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS service_bids (
  id TEXT PRIMARY KEY,
  request_id TEXT NOT NULL REFERENCES service_requests(id) ON DELETE CASCADE,
  shop_id TEXT NOT NULL,
  shop_name TEXT NOT NULL,
  shop_rating FLOAT NOT NULL DEFAULT 5.0,
  price FLOAT NOT NULL DEFAULT 0,
  estimated_hours FLOAT NOT NULL DEFAULT 1.0,
  warranty_days INT NOT NULL DEFAULT 30,
  message TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'PENDING',
  created_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS evidence_packages (
  id TEXT PRIMARY KEY,
  vehicle_id TEXT NOT NULL,
  event_type TEXT NOT NULL,
  timestamp BIGINT NOT NULL,
  gps_location TEXT NOT NULL,
  video_path TEXT NOT NULL,
  audio_path TEXT,
  pid_snapshot JSONB NOT NULL DEFAULT '{}',
  dtcs TEXT NOT NULL DEFAULT '[]',
  hash_sha256 TEXT NOT NULL,
  signature_version TEXT NOT NULL,
  created_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS vehicle_twin_profiles (
  id TEXT PRIMARY KEY,
  vehicle_id TEXT NOT NULL UNIQUE,
  baseline_json JSONB NOT NULL DEFAULT '{}',
  variance_json JSONB NOT NULL DEFAULT '{}',
  confidence FLOAT NOT NULL DEFAULT 0.0,
  last_training_date BIGINT NOT NULL,
  anomaly_count INT NOT NULL DEFAULT 0,
  health_score INT NOT NULL DEFAULT 100
);

CREATE TABLE IF NOT EXISTS twin_anomalies (
  id TEXT PRIMARY KEY,
  vehicle_id TEXT NOT NULL,
  parameter TEXT NOT NULL,
  expected_value FLOAT NOT NULL,
  actual_value FLOAT NOT NULL,
  deviation FLOAT NOT NULL,
  severity TEXT NOT NULL DEFAULT 'LOW',
  confidence FLOAT NOT NULL DEFAULT 0.0,
  timestamp BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS vanguard_events (
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

CREATE TABLE IF NOT EXISTS marketplace_ledger_entries (
  ledger_entry_id TEXT PRIMARY KEY,
  transaction_id TEXT NOT NULL,
  related_event_id TEXT NOT NULL REFERENCES vanguard_events(event_id) ON DELETE RESTRICT,
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

-- Enable RLS
ALTER TABLE live_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE live_snapshots ENABLE ROW LEVEL SECURITY;
ALTER TABLE mechanic_notes ENABLE ROW LEVEL SECURITY;
ALTER TABLE repair_photos ENABLE ROW LEVEL SECURITY;
ALTER TABLE repair_parts ENABLE ROW LEVEL SECURITY;
ALTER TABLE repair_votes ENABLE ROW LEVEL SECURITY;
ALTER TABLE repair_comments ENABLE ROW LEVEL SECURITY;
ALTER TABLE repair_verifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE service_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE service_bids ENABLE ROW LEVEL SECURITY;
ALTER TABLE evidence_packages ENABLE ROW LEVEL SECURITY;
ALTER TABLE vehicle_twin_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE twin_anomalies ENABLE ROW LEVEL SECURITY;
ALTER TABLE vanguard_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE marketplace_ledger_entries ENABLE ROW LEVEL SECURITY;

-- Select/All policies
CREATE POLICY "Public Select live_sessions" ON live_sessions FOR ALL USING (true);
CREATE POLICY "Public Select live_snapshots" ON live_snapshots FOR ALL USING (true);
CREATE POLICY "Public Select mechanic_notes" ON mechanic_notes FOR ALL USING (true);
CREATE POLICY "Public Select repair_photos" ON repair_photos FOR ALL USING (true);
CREATE POLICY "Public Select repair_parts" ON repair_parts FOR ALL USING (true);
CREATE POLICY "Public Select repair_votes" ON repair_votes FOR ALL USING (true);
CREATE POLICY "Public Select repair_comments" ON repair_comments FOR ALL USING (true);
CREATE POLICY "Public Select repair_verifications" ON repair_verifications FOR ALL USING (true);
CREATE POLICY "Public Select service_requests" ON service_requests FOR ALL USING (true);
CREATE POLICY "Public Select service_bids" ON service_bids FOR ALL USING (true);
CREATE POLICY "Public Select evidence_packages" ON evidence_packages FOR ALL USING (true);
CREATE POLICY "Public Select vehicle_twin_profiles" ON vehicle_twin_profiles FOR ALL USING (true);
CREATE POLICY "Public Select twin_anomalies" ON twin_anomalies FOR ALL USING (true);
CREATE POLICY "Public Select vanguard_events" ON vanguard_events FOR ALL USING (true);
CREATE POLICY "Public Select marketplace_ledger_entries" ON marketplace_ledger_entries FOR ALL USING (true);

-- HIGH SPEED INDICES FOR MILLIONS OF RECORDS (Repair Network & Twin)
CREATE INDEX IF NOT EXISTS idx_repair_cases_vehicleMake_vehicleModel ON repair_cases (vehicleMake, vehicleModel);
CREATE INDEX IF NOT EXISTS idx_repair_cases_dtcCode ON repair_cases (dtcCode);
CREATE INDEX IF NOT EXISTS idx_repair_cases_country ON repair_cases (country);

-- GIN index for PostgreSQL full-text search
CREATE INDEX IF NOT EXISTS idx_repair_cases_full_text ON repair_cases USING gin (to_tsvector('spanish', coalesce(vehicleMake, '') || ' ' || coalesce(vehicleModel, '') || ' ' || coalesce(dtcCode, '') || ' ' || coalesce(symptoms, '') || ' ' || coalesce(solution, '')));

-- Indexes for active telemetry/marketplace
CREATE INDEX IF NOT EXISTS idx_live_snapshots_session ON live_snapshots (session_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_service_bids_request ON service_bids (request_id);
CREATE INDEX IF NOT EXISTS idx_twin_anomalies_vehicle ON twin_anomalies (vehicle_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_vanguard_events_aggregate ON vanguard_events (aggregate_type, aggregate_id, occurred_at_ms);
CREATE INDEX IF NOT EXISTS idx_vanguard_events_event_type ON vanguard_events (event_type, occurred_at_ms);
CREATE INDEX IF NOT EXISTS idx_vanguard_events_correlation ON vanguard_events (correlation_id, occurred_at_ms);
CREATE INDEX IF NOT EXISTS idx_marketplace_ledger_transaction ON marketplace_ledger_entries (transaction_id);
CREATE INDEX IF NOT EXISTS idx_marketplace_ledger_order ON marketplace_ledger_entries (order_type, order_id);
CREATE INDEX IF NOT EXISTS idx_marketplace_ledger_event ON marketplace_ledger_entries (related_event_id);
CREATE INDEX IF NOT EXISTS idx_marketplace_ledger_status ON marketplace_ledger_entries (status, created_at_ms);

-- ─── B2B FLEET & DVIR SUBSYSTEM ──────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS fleet_organizations (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  owner_user_id TEXT NOT NULL,
  name TEXT NOT NULL,
  legal_name TEXT,
  tax_id TEXT,
  phone TEXT NOT NULL,
  email TEXT NOT NULL,
  country TEXT NOT NULL,
  province TEXT,
  address TEXT,
  plan TEXT NOT NULL DEFAULT 'FREE_FLEET',
  status TEXT NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMPTZ DEFAULT now(),
  updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS fleet_branches (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  fleet_id UUID NOT NULL REFERENCES fleet_organizations(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  location TEXT,
  manager_user_id TEXT,
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS fleet_vehicles (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  fleet_id UUID NOT NULL REFERENCES fleet_organizations(id) ON DELETE CASCADE,
  branch_id UUID REFERENCES fleet_branches(id) ON DELETE SET NULL,
  vehicle_profile_id TEXT NOT NULL,
  internal_code TEXT NOT NULL,
  assigned_driver_id UUID,
  status TEXT NOT NULL DEFAULT 'ACTIVE',
  odometer_km INT NOT NULL DEFAULT 0,
  last_dvir_id UUID,
  last_health_score INT DEFAULT 100,
  last_scan_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ DEFAULT now(),
  updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS fleet_drivers (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  fleet_id UUID NOT NULL REFERENCES fleet_organizations(id) ON DELETE CASCADE,
  user_id TEXT,
  full_name TEXT NOT NULL,
  phone TEXT NOT NULL,
  license_number TEXT,
  license_expiration TIMESTAMPTZ,
  assigned_vehicle_id UUID REFERENCES fleet_vehicles(id) ON DELETE SET NULL,
  status TEXT NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMPTZ DEFAULT now()
);

ALTER TABLE fleet_vehicles ADD CONSTRAINT fk_assigned_driver FOREIGN KEY (assigned_driver_id) REFERENCES fleet_drivers(id) ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS dvir_inspections (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  fleet_id UUID NOT NULL REFERENCES fleet_organizations(id) ON DELETE CASCADE,
  vehicle_id UUID NOT NULL REFERENCES fleet_vehicles(id) ON DELETE CASCADE,
  driver_id UUID NOT NULL REFERENCES fleet_drivers(id) ON DELETE CASCADE,
  inspection_type TEXT NOT NULL DEFAULT 'DAILY',
  status TEXT NOT NULL DEFAULT 'SUBMITTED',
  odometer_km INT NOT NULL DEFAULT 0,
  location_lat FLOAT,
  location_lng FLOAT,
  started_at TIMESTAMPTZ NOT NULL,
  completed_at TIMESTAMPTZ,
  signed_at TIMESTAMPTZ,
  overall_result TEXT NOT NULL DEFAULT 'PASS',
  report_id TEXT,
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS dvir_checklist_items (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  inspection_id UUID NOT NULL REFERENCES dvir_inspections(id) ON DELETE CASCADE,
  category TEXT NOT NULL,
  item_key TEXT NOT NULL,
  label TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'OK',
  severity TEXT NOT NULL DEFAULT 'LOW',
  notes TEXT,
  photo_required BOOLEAN DEFAULT false,
  photo_uri TEXT,
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS dvir_obd_snapshots (
  inspection_id UUID PRIMARY KEY REFERENCES dvir_inspections(id) ON DELETE CASCADE,
  connection_state TEXT NOT NULL,
  adapter_quality TEXT NOT NULL,
  dtcs_active TEXT[] DEFAULT '{}',
  dtcs_pending TEXT[] DEFAULT '{}',
  dtcs_permanent TEXT[] DEFAULT '{}',
  readiness JSONB DEFAULT '{}',
  voltage FLOAT NOT NULL,
  rpm FLOAT,
  coolant_temp FLOAT,
  odometer INT,
  raw_hash TEXT NOT NULL,
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS fleet_evidence (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  fleet_id UUID NOT NULL REFERENCES fleet_organizations(id) ON DELETE CASCADE,
  vehicle_id UUID NOT NULL REFERENCES fleet_vehicles(id) ON DELETE CASCADE,
  related_entity_type TEXT NOT NULL,
  related_entity_id UUID NOT NULL,
  evidence_type TEXT NOT NULL,
  uri TEXT NOT NULL,
  hash_sha256 TEXT NOT NULL,
  notes TEXT,
  captured_by_user_id TEXT NOT NULL,
  captured_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS dvir_signatures (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  inspection_id UUID NOT NULL REFERENCES dvir_inspections(id) ON DELETE CASCADE,
  signer_user_id TEXT NOT NULL,
  signer_name TEXT NOT NULL,
  signer_role TEXT NOT NULL,
  signature_uri TEXT NOT NULL,
  signed_at TIMESTAMPTZ DEFAULT now(),
  hash_sha256 TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS fleet_alerts (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  fleet_id UUID NOT NULL REFERENCES fleet_organizations(id) ON DELETE CASCADE,
  vehicle_id UUID NOT NULL REFERENCES fleet_vehicles(id) ON DELETE CASCADE,
  driver_id UUID REFERENCES fleet_drivers(id) ON DELETE SET NULL,
  alert_type TEXT NOT NULL,
  severity TEXT NOT NULL DEFAULT 'LOW',
  title TEXT NOT NULL,
  description TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'OPEN',
  source TEXT NOT NULL DEFAULT 'DVIR',
  created_at TIMESTAMPTZ DEFAULT now(),
  resolved_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS fleet_maintenance_tasks (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  fleet_id UUID NOT NULL REFERENCES fleet_organizations(id) ON DELETE CASCADE,
  vehicle_id UUID NOT NULL REFERENCES fleet_vehicles(id) ON DELETE CASCADE,
  title TEXT NOT NULL,
  description TEXT,
  maintenance_type TEXT NOT NULL,
  due_km INT,
  due_date TIMESTAMPTZ,
  priority TEXT NOT NULL DEFAULT 'NORMAL',
  status TEXT NOT NULL DEFAULT 'OPEN',
  assigned_provider_id TEXT,
  cost_estimate FLOAT,
  report_id TEXT,
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS fleet_work_orders (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  fleet_id UUID NOT NULL REFERENCES fleet_organizations(id) ON DELETE CASCADE,
  vehicle_id UUID NOT NULL REFERENCES fleet_vehicles(id) ON DELETE CASCADE,
  created_by_user_id TEXT NOT NULL,
  assigned_provider_id TEXT,
  title TEXT NOT NULL,
  description TEXT NOT NULL,
  source TEXT NOT NULL DEFAULT 'MANUAL',
  status TEXT NOT NULL DEFAULT 'OPEN',
  priority TEXT NOT NULL DEFAULT 'NORMAL',
  estimated_cost FLOAT,
  final_cost FLOAT,
  started_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  report_id TEXT
);

CREATE TABLE IF NOT EXISTS fleet_cost_entries (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  fleet_id UUID NOT NULL REFERENCES fleet_organizations(id) ON DELETE CASCADE,
  vehicle_id UUID NOT NULL REFERENCES fleet_vehicles(id) ON DELETE CASCADE,
  type TEXT NOT NULL,
  amount FLOAT NOT NULL,
  currency TEXT NOT NULL DEFAULT 'CRC',
  provider_id TEXT,
  description TEXT,
  receipt_uri TEXT,
  related_work_order_id UUID REFERENCES fleet_work_orders(id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS fleet_trips (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  fleet_id UUID NOT NULL REFERENCES fleet_organizations(id) ON DELETE CASCADE,
  vehicle_id UUID NOT NULL REFERENCES fleet_vehicles(id) ON DELETE CASCADE,
  driver_id UUID NOT NULL REFERENCES fleet_drivers(id) ON DELETE CASCADE,
  started_at TIMESTAMPTZ NOT NULL,
  ended_at TIMESTAMPTZ,
  distance_km FLOAT NOT NULL DEFAULT 0,
  eco_score INT NOT NULL DEFAULT 100,
  harsh_brakes INT NOT NULL DEFAULT 0,
  harsh_accels INT NOT NULL DEFAULT 0,
  fuel_used_estimated FLOAT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT now()
);

-- Enable RLS
ALTER TABLE fleet_organizations ENABLE ROW LEVEL SECURITY;
ALTER TABLE fleet_branches ENABLE ROW LEVEL SECURITY;
ALTER TABLE fleet_vehicles ENABLE ROW LEVEL SECURITY;
ALTER TABLE fleet_drivers ENABLE ROW LEVEL SECURITY;
ALTER TABLE dvir_inspections ENABLE ROW LEVEL SECURITY;
ALTER TABLE dvir_checklist_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE dvir_obd_snapshots ENABLE ROW LEVEL SECURITY;
ALTER TABLE fleet_evidence ENABLE ROW LEVEL SECURITY;
ALTER TABLE dvir_signatures ENABLE ROW LEVEL SECURITY;
ALTER TABLE fleet_alerts ENABLE ROW LEVEL SECURITY;
ALTER TABLE fleet_maintenance_tasks ENABLE ROW LEVEL SECURITY;
ALTER TABLE fleet_work_orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE fleet_cost_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE fleet_trips ENABLE ROW LEVEL SECURITY;

-- Select/All policies
CREATE POLICY "Public select fleet_organizations" ON fleet_organizations FOR ALL USING (true);
CREATE POLICY "Public select fleet_branches" ON fleet_branches FOR ALL USING (true);
CREATE POLICY "Public select fleet_vehicles" ON fleet_vehicles FOR ALL USING (true);
CREATE POLICY "Public select fleet_drivers" ON fleet_drivers FOR ALL USING (true);
CREATE POLICY "Public select dvir_inspections" ON dvir_inspections FOR ALL USING (true);
CREATE POLICY "Public select dvir_checklist_items" ON dvir_checklist_items FOR ALL USING (true);
CREATE POLICY "Public select dvir_obd_snapshots" ON dvir_obd_snapshots FOR ALL USING (true);
CREATE POLICY "Public select fleet_evidence" ON fleet_evidence FOR ALL USING (true);
CREATE POLICY "Public select dvir_signatures" ON dvir_signatures FOR ALL USING (true);
CREATE POLICY "Public select fleet_alerts" ON fleet_alerts FOR ALL USING (true);
CREATE POLICY "Public select fleet_maintenance_tasks" ON fleet_maintenance_tasks FOR ALL USING (true);
CREATE POLICY "Public select fleet_work_orders" ON fleet_work_orders FOR ALL USING (true);
CREATE POLICY "Public select fleet_cost_entries" ON fleet_cost_entries FOR ALL USING (true);
CREATE POLICY "Public select fleet_trips" ON fleet_trips FOR ALL USING (true);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_fleet_organizations_owner ON fleet_organizations (owner_user_id);
CREATE INDEX IF NOT EXISTS idx_fleet_branches_fleet ON fleet_branches (fleet_id);
CREATE INDEX IF NOT EXISTS idx_fleet_vehicles_fleet ON fleet_vehicles (fleet_id, status);
CREATE INDEX IF NOT EXISTS idx_fleet_vehicles_driver ON fleet_vehicles (assigned_driver_id);
CREATE INDEX IF NOT EXISTS idx_fleet_drivers_fleet ON fleet_drivers (fleet_id, status);
CREATE INDEX IF NOT EXISTS idx_dvir_inspections_vehicle ON dvir_inspections (vehicle_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_dvir_inspections_driver ON dvir_inspections (driver_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_dvir_checklist_inspection ON dvir_checklist_items (inspection_id);
CREATE INDEX IF NOT EXISTS idx_fleet_evidence_vehicle ON fleet_evidence (vehicle_id, evidence_type);
CREATE INDEX IF NOT EXISTS idx_fleet_alerts_fleet ON fleet_alerts (fleet_id, status, severity);
CREATE INDEX IF NOT EXISTS idx_fleet_maintenance_vehicle ON fleet_maintenance_tasks (vehicle_id, status);
CREATE INDEX IF NOT EXISTS idx_fleet_work_orders_vehicle ON fleet_work_orders (vehicle_id, status);
CREATE INDEX IF NOT EXISTS idx_fleet_cost_entries_vehicle ON fleet_cost_entries (vehicle_id, type);
CREATE INDEX IF NOT EXISTS idx_fleet_trips_driver ON fleet_trips (driver_id, started_at DESC);

