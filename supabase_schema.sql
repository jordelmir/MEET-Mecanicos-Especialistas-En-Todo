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
  adapter_type TEXT DEFAULT 'clone',
  scan_type TEXT DEFAULT 'quick',
  dtcs_found JSONB DEFAULT '[]',
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

CREATE POLICY "Public read oem_pids" ON oem_pids FOR SELECT USING (true);
CREATE POLICY "Public read dtc_definitions" ON dtc_definitions FOR SELECT USING (true);
CREATE POLICY "Public read service_resets" ON service_resets FOR SELECT USING (true);
CREATE POLICY "Users manage own scans" ON scan_sessions FOR ALL USING (true);
CREATE POLICY "Users manage own vehicles" ON cloud_vehicles FOR ALL USING (true);
CREATE POLICY "Users manage own subs" ON subscriptions FOR ALL USING (true);
