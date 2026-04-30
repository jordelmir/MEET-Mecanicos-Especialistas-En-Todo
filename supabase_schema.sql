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
