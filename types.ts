
// ─── DOMAIN: TALLER MECÁNICO ─────────────────────────────────────────────────

export enum WorkOrderStatus {
  RECEIVED = 'RECEIVED',           // Vehículo recibido
  DIAGNOSED = 'DIAGNOSED',        // Diagnóstico completado
  WAITING_PARTS = 'WAITING_PARTS', // Esperando repuestos
  IN_PROGRESS = 'IN_PROGRESS',    // En reparación
  QUALITY_CHECK = 'QUALITY_CHECK', // Control de calidad
  COMPLETED = 'COMPLETED',        // Completado
  DELIVERED = 'DELIVERED',        // Entregado al cliente
  CANCELLED = 'CANCELLED',        // Cancelado
}

export enum Role {
  CLIENT = 'CLIENT',
  MECHANIC = 'MECHANIC',
  ADMIN = 'ADMIN',
}

export enum ServiceCategory {
  REP = 'rep',   // Reparación
  CAM = 'cam',   // Cambio/Repuesto
  MANT = 'mant', // Mantenimiento
  DIAG = 'diag', // Diagnóstico
}

export interface ServiceHistoryItem {
  id: string;
  date: Date;
  serviceName: string;
  mechanicName: string;
  price: number;
  vehicleInfo: string;
  notes?: string;
}

export interface VehicleInfo {
  plate: string;
  brand: string;
  model: string;
  year: number;
  color: string;
  mileage: number;
  vin?: string;
  fuelType: 'Gasolina' | 'Diésel' | 'Híbrido' | 'Eléctrico' | 'GLP';
}

export interface Client {
  id: string;
  name: string;
  phone: string;
  email: string;
  identification: string;
  accessCode: string;
  vehicles: VehicleInfo[];
  serviceHistory: ServiceHistoryItem[];
  scans?: OBD2ScanResult[]; // Vía APK
  oscilloscopeMeasurements?: OscilloscopeMeasurement[];
  lastVisit?: Date;
  joinDate: Date;
  loyaltyPoints: number;
  notes?: string;
  avatar?: string;
}

export interface OBD2ScanResult {
  id: string;
  date: Date;
  vehiclePlate: string;
  dtcCodes: string[];
  severity: 'high' | 'medium' | 'low' | 'none';
  notes?: string;
}

export interface OscilloscopeMeasurement {
  id: string;
  timestamp: Date;
  signalType: string;
  signalName: string;
  pidCode: string;
  durationMs: number;
  sampleCount: number;
  metrics: {
    frequency: number;
    amplitude: number;
    vpp: number;
    rms: number;
    thd: number;
    dutyCycle: number;
    mean: number;
    min: number;
    max: number;
    stability: number;
    noiseLevel: number;
  };
  diagnosis: string;
  recommendation: string;
  severity: 'normal' | 'warning' | 'critical';
  confidenceScore: number;
  vehiclePlate?: string;
  workOrderId?: string;
  // Compressed waveform data for mini-canvas replay (max 200 points)
  waveformSnapshot?: number[];
}

export interface CatalogItem {
  name: string;
  category: ServiceCategory;
  estimatedMinutes?: number;
  basePrice?: number;
}

export interface CatalogSection {
  id: string;
  icon: string;
  title: string;
  items: CatalogItem[];
}

export interface Service {
  id: string;
  name: string;
  category: ServiceCategory;
  estimatedMinutes: number;
  basePrice: number;
  description?: string;
}

export interface Mechanic {
  id: string;
  name: string;
  phone: string;
  identification: string;
  accessCode: string;
  email: string;
  specialty: 'GENERAL' | 'MOTOR' | 'ELECTRICO' | 'TRANSMISION' | 'SUSPENSION' | 'DIESEL';
  efficiencyFactor: number; // 1.0 = standard, >1 = faster
  avatar: string;
  certifications?: string[];
}

export interface WorkOrder {
  id: string;
  clientId: string;
  clientName: string;
  mechanicId: string;
  serviceId: string;
  vehicleInfo: VehicleInfo;
  startTime: Date;
  estimatedEndTime: Date;
  actualStartTime?: Date;
  actualEndTime?: Date;
  status: WorkOrderStatus;
  notes?: string;
  diagnosticNotes?: string;
  
  price: number;
  estimatedMinutes: number;
  
  // Cancellation Metadata
  cancellationReason?: string;
  cancellationDate?: Date;
  
  // Parts tracking
  partsNeeded?: string[];
  partsReady?: boolean;
  
  // Oscilloscope measurements linked to this work order
  oscilloscopeMeasurements?: OscilloscopeMeasurement[];
}

export interface TimeSlice {
  time: Date;
  isOccupied: boolean;
  workOrderId?: string;
}

export interface Metrics {
  dailyOccupancy: number;
  idleTimeMinutes: number;
  revenue: number;
  ordersCompleted: number;
  ordersTotal: number;
  averageRepairTime?: number;
}

// Shop Configuration
export interface ShopConfig {
  rules: string;
  openHour: number;
  closeHour: number;
  timeSliceMinutes: number;
}

// ─── DOMAIN: GARAGE, DIGITAL TWIN & PREDICTIVE HEALTH ───────────────────────

export type FuelType = 'GASOLINE' | 'DIESEL' | 'HYBRID' | 'EV' | 'LPG' | 'UNKNOWN';
export type TransmissionType = 'MANUAL' | 'AUTOMATIC' | 'CVT' | 'DCT' | 'UNKNOWN';
export type DrivingProfile = 'CITY' | 'HIGHWAY' | 'MIXED' | 'AGGRESSIVE' | 'ECO' | 'UNKNOWN';
export type SessionType = 'IDLE' | 'CITY_DRIVE' | 'HIGHWAY_DRIVE' | 'COLD_START' | 'HOT_IDLE' | 'LOAD_TEST';
export type RiskCategory = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type MaintenanceType =
  | 'OIL_CHANGE'
  | 'FILTER_CHANGE'
  | 'SPARK_PLUGS'
  | 'BRAKES'
  | 'ATF'
  | 'COOLANT'
  | 'BATTERY'
  | 'TIRES'
  | 'TIMING_BELT'
  | 'INSPECTION'
  | 'CUSTOM';

export type EventType =
  | 'VEHICLE_CREATED'
  | 'OBD_CONNECTED'
  | 'DTC_DETECTED'
  | 'DTC_CLEARED'
  | 'FREEZE_FRAME_CAPTURED'
  | 'SNAPSHOT_CAPTURED'
  | 'REPORT_GENERATED'
  | 'REPAIR_STARTED'
  | 'REPAIR_COMPLETED'
  | 'PART_REQUESTED'
  | 'PART_PURCHASED'
  | 'MECHANIC_REQUESTED'
  | 'LIVELINK_SESSION'
  | 'MAINTENANCE_CREATED'
  | 'MAINTENANCE_COMPLETED'
  | 'DVIR_CREATED'
  | 'HEALTH_SCORE_CHANGED'
  | 'PREDICTIVE_ALERT'
  | 'ACCIDENT_EVENT'
  | 'HARD_BRAKE'
  | 'OVERHEAT_WARNING'
  | 'LOW_VOLTAGE_WARNING'
  | 'DASHCAM_SESSION_STARTED'
  | 'DASHCAM_SESSION_ENDED'
  | 'INCIDENT_DETECTED'
  | 'CLIP_PROTECTED'
  | 'INCIDENT_REPORT_GENERATED';

// ─── DOMAIN: DASHCAM, BLACK BOX & VEHICLE TESTIGO ─────────────────────────────

export type ClipType =
  | 'MANUAL'
  | 'HARD_BRAKE'
  | 'IMPACT'
  | 'DTC_CRITICAL'
  | 'OVERHEAT'
  | 'LOW_VOLTAGE'
  | 'SPEED_EVENT'
  | 'LIVELINK_CAPTURE'
  | 'FLEET_EVENT';

export interface DashcamSession {
  id: string;
  vehicle_id: string;
  user_id: string;
  started_at: string;
  ended_at_nullable: string | null;
  mode: 'HUD' | 'DASHCAM' | 'BLACK_BOX' | 'FLEET' | 'INCIDENT';
  camera_facing: 'FRONT' | 'BACK' | 'CABIN';
  video_enabled: boolean;
  audio_enabled: boolean;
  gps_enabled: boolean;
  obd_enabled: boolean;
  sensor_fusion_enabled: boolean;
  status: 'ACTIVE' | 'COMPLETED' | 'ABORTED';
  storage_path: string;
  created_at: string;
}

export interface DashcamClip {
  id: string;
  session_id: string;
  vehicle_id: string;
  event_id_nullable: string | null;
  clip_type: ClipType;
  start_time: string;
  end_time: string;
  duration_sec: number;
  video_uri: string;
  thumbnail_uri: string;
  telemetry_overlay_enabled: boolean;
  raw_telemetry_uri_nullable: string | null;
  hash_sha256: string;
  locked: boolean;
  created_at: string;
}

export type DrivingEventType =
  | 'HARD_BRAKE'
  | 'HARD_ACCELERATION'
  | 'IMPACT_DETECTED'
  | 'POSSIBLE_COLLISION'
  | 'SHARP_TURN'
  | 'OVERHEAT'
  | 'LOW_VOLTAGE'
  | 'CRITICAL_DTC'
  | 'MANUAL_MARKER'
  | 'CAMERA_STARTED'
  | 'CAMERA_STOPPED';

export interface DrivingEvent {
  id: string;
  vehicle_id: string;
  session_id: string;
  event_type: DrivingEventType;
  severity: 'low' | 'medium' | 'high' | 'critical';
  timestamp: string;
  speed_kmh_nullable: number | null;
  rpm_nullable: number | null;
  gps_lat_nullable: number | null;
  gps_lng_nullable: number | null;
  g_force_x: number;
  g_force_y: number;
  g_force_z: number;
  obd_snapshot_id_nullable: string | null;
  clip_id_nullable: string | null;
  report_id_nullable: string | null;
  created_at: string;
}

export type TelemetrySource =
  | 'REAL_OBD'
  | 'GPS'
  | 'ACCELEROMETER'
  | 'GYROSCOPE'
  | 'MANUAL'
  | 'SIMULATED';

export interface TelemetrySample {
  rpm: number | null;
  speed: number | null;
  temp: number | null;
  voltage: number | null;
  gForceX: number;
  gForceY: number;
  gForceZ: number;
  lat: number | null;
  lng: number | null;
  timestamp: number;
  quality: 'GOOD' | 'STALE' | 'POOR' | 'UNAVAILABLE';
  source: TelemetrySource;
}

export interface OverlayData {
  value: string | number;
  unit: string;
  source: TelemetrySource;
  quality: 'GOOD' | 'STALE' | 'POOR' | 'UNAVAILABLE';
  timestamp: number;
}

export interface VehicleProfile {
  id: string;
  owner_user_id: string;
  nickname: string;
  make: string;
  model: string;
  year: number;
  trim_nullable: string | null;
  engine: string;
  engine_code_nullable: string | null;
  transmission: TransmissionType;
  fuel_type: FuelType;
  vin_nullable: string | null;
  plate_nullable: string | null;
  odometer_km: number;
  country: string;
  province_nullable: string | null;
  color_nullable: string | null;
  photo_uri_nullable: string | null;
  created_at: Date | string;
  updated_at: Date | string;
  
  // Backwards compatibility properties mapping to VehicleInfo
  plate: string;
  brand: string;
  color: string;
  mileage: number;
  vin?: string;
  fuelType?: 'Gasolina' | 'Diésel' | 'Híbrido' | 'Eléctrico' | 'GLP';
}

export interface VehicleDigitalTwin {
  vehicle_id: string;
  baseline_created_at: Date | string | null;
  baseline_confidence: number; // 0 to 100
  normal_idle_rpm_min: number;
  normal_idle_rpm_max: number;
  normal_voltage_min: number;
  normal_voltage_max: number;
  normal_ect_min: number;
  normal_ect_max: number;
  normal_fuel_trim_min: number;
  normal_fuel_trim_max: number;
  normal_maf_min: number;
  normal_maf_max: number;
  normal_map_min: number;
  normal_map_max: number;
  driving_profile: DrivingProfile;
  health_score: number; // 0 to 100
  risk_score: number; // 0 to 100
  last_updated_at: Date | string;
}

export interface BaselineSession {
  id: string;
  vehicle_id: string;
  session_type: SessionType;
  duration_sec: number;
  samples_count: number;
  confidence: number; // 0 to 100
  created_at: Date | string;
}

export interface VehicleTimelineEvent {
  id: string;
  vehicle_id: string;
  event_type: EventType;
  title: string;
  description: string;
  severity: 'low' | 'medium' | 'high' | 'critical';
  source: string; // e.g. "OBD", "Manual", "Taller", "Repuestera"
  payload_json?: string; // Additional payload data
  related_report_id_nullable: string | null;
  related_work_order_id_nullable: string | null;
  related_part_request_id_nullable: string | null;
  related_livelink_id_nullable: string | null;
  created_at: Date | string;
}

export interface VehicleHealthScore {
  vehicle_id: string;
  overall_score: number; // 0 to 100
  engine_score: number;
  transmission_score: number;
  electrical_score: number;
  emissions_score: number;
  brake_score: number;
  suspension_score: number;
  cooling_score: number;
  battery_score: number;
  confidence: 'LOW' | 'MEDIUM' | 'HIGH';
  calculated_at: Date | string;
}

export interface PredictiveMaintenanceAlert {
  id: string;
  vehicle_id: string;
  component: string;
  risk_level: RiskCategory;
  predicted_issue: string;
  evidence: string[]; // List of metrics/deviations supporting this
  recommended_action: string;
  due_in_km_nullable: number | null;
  due_in_days_nullable: number | null;
  confidence: number; // percentage 0 to 100
  status: 'active' | 'resolved' | 'ignored';
  created_at: Date | string;
}

export interface MaintenanceRecord {
  id: string;
  vehicle_id: string;
  type: MaintenanceType;
  title: string;
  odometer_km: number;
  date: Date | string;
  provider_id_nullable: string | null;
  provider_name?: string | null;
  cost_nullable: number | null;
  currency: string;
  parts_used: string[];
  notes: string;
  photos: string[];
  report_id_nullable: string | null;
  created_at: Date | string;
}

// ═══════════════════════════════════════════════════════════════
// FEATURE: 3D Engine & Electrical Topology & Fuse Box & Wiring
// ═══════════════════════════════════════════════════════════════

export type TemplateType =
  | 'OEM_SPECIFIC'
  | 'ENGINE_FAMILY'
  | 'GENERIC_FWD_INLINE4'
  | 'GENERIC_RWD'
  | 'GENERIC_DIESEL'
  | 'GENERIC_EV'
  | 'UNKNOWN';

export type LocationConfidence =
  | 'EXACT'
  | 'HIGH'
  | 'MEDIUM'
  | 'LOW'
  | 'GENERIC';

export type ComponentStatus =
  | 'NORMAL'
  | 'RELATED_TO_DTC'
  | 'SUSPECT'
  | 'TEST_REQUIRED'
  | 'TEST_FAILED'
  | 'TEST_PASSED'
  | 'CONFIRMED_FAULT'
  | 'REPLACED'
  | 'UNKNOWN';

export type SlotStatus =
  | 'UNKNOWN'
  | 'NORMAL'
  | 'RELATED_TO_DTC'
  | 'CHECK_REQUIRED'
  | 'FAILED'
  | 'REPLACED';

export type MeasurementType =
  | 'VOLTAGE'
  | 'RESISTANCE'
  | 'CONTINUITY'
  | 'PRESSURE'
  | 'TEMPERATURE'
  | 'SIGNAL'
  | 'VISUAL_INSPECTION'
  | 'SOUND'
  | 'MECHANICAL_MOVEMENT';

export interface Vehicle3DProfile {
  id: string;
  vehicle_id: string;
  make: string;
  model: string;
  year: number;
  engine: string;
  transmission: string;
  template_type: TemplateType;
  confidence: number; // 0 to 100
  created_at: string;
}

export interface Component3D {
  id: string;
  vehicle_3d_profile_id: string;
  component_key: string; // key matching mapping (eg. 'fuel_pump')
  name: string;
  system: string; // Eg. 'Combustible', 'Eléctrico'
  subsystem: string; // Eg. 'Fusibles', 'Sensores'
  description: string;
  mesh_uri_nullable: string | null;
  icon_uri_nullable: string | null;
  position_x: number;
  position_y: number;
  position_z: number;
  rotation_x: number;
  rotation_y: number;
  rotation_z: number;
  scale: number;
  location_confidence: LocationConfidence;
  related_dtcs: string[];
  related_symptoms: string[];
  related_pids: string[];
  related_tests: string[];
  related_parts: string[];
  safety_notes: string[];
  created_at: string;
}

export interface DtcComponentMap {
  dtc_code: string;
  system: string;
  primary_components: string[];
  secondary_components: string[];
  circuits: string[];
  required_tests: string[];
  caution_notes: string[];
}

export interface FuseRelayBox {
  id: string;
  vehicle_id: string;
  location: 'ENGINE_BAY' | 'UNDER_DASH' | 'TRUNK';
  label: string;
  layout_template: string;
  confidence: number;
}

export interface FuseRelaySlot {
  id: string;
  box_id: string;
  slot_code: string;
  label: string;
  amperage_nullable: number | null;
  component_protected: string;
  related_dtcs: string[];
  position_row: number;
  position_col: number;
  status: SlotStatus;
}

export interface CircuitNode {
  id: string;
  type: 'ECU_PIN' | 'FUSE' | 'RELAY' | 'CONNECTOR' | 'SENSOR' | 'ACTUATOR' | 'GROUND' | 'POWER_SUPPLY';
  label: string;
  pin_nullable: string | null;
  expected_voltage_nullable: number | null;
  expected_resistance_nullable: number | null;
  test_point: boolean;
}

export interface CircuitEdge {
  from_node: string;
  to_node: string;
  wire_color_nullable: string | null;
  expected_signal: string;
  status: 'NORMAL' | 'OPEN' | 'SHORT' | 'CORRODED';
}

export interface WiringCircuit {
  id: string;
  vehicle_id: string;
  circuit_name: string;
  related_dtcs: string[];
  nodes: CircuitNode[];
  edges: CircuitEdge[];
  confidence: number;
}

export interface ComponentTest {
  id: string;
  component_key: string;
  name: string;
  required_tools: string[];
  safety_level: 'SAFE' | 'CAUTION' | 'DANGER';
  steps: string[];
  expected_result: string;
  pass_action: string;
  fail_action: string;
}

export interface ComponentMeasurement {
  id: string;
  vehicle_id: string;
  component_id: string;
  test_id: string;
  measurement_type: MeasurementType;
  value: number;
  unit: string;
  expected_min_nullable: number | null;
  expected_max_nullable: number | null;
  result: 'PASS' | 'FAIL' | 'INCONCLUSIVE' | 'NOT_TESTED';
  notes: string;
  photo_uri_nullable: string | null;
  created_at: string;
}

// ─── DOMAIN: B2B FLEET, DVIR & VEHICLE OPERATIONS ───────────────────────────

export type FleetPlan = 'FREE_FLEET' | 'FLEET_STARTER' | 'FLEET_PRO' | 'FLEET_ENTERPRISE';
export type FleetRole = 'OWNER' | 'ADMIN' | 'MANAGER' | 'MECHANIC' | 'DRIVER' | 'VIEWER' | 'AUDITOR';
export type FleetVehicleStatus = 'ACTIVE' | 'IN_MAINTENANCE' | 'OUT_OF_SERVICE' | 'SOLD' | 'INACTIVE';
export type FleetDriverStatus = 'ACTIVE' | 'SUSPENDED' | 'INACTIVE';
export type InspectionType = 'PRE_TRIP' | 'POST_TRIP' | 'DAILY' | 'WEEKLY' | 'INCIDENT' | 'MAINTENANCE_RETURN' | 'CUSTOM';
export type DvirStatus = 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'REQUIRES_REPAIR' | 'LOCKED';
export type OverallResult = 'PASS' | 'PASS_WITH_OBSERVATIONS' | 'FAIL_MINOR' | 'FAIL_MAJOR' | 'OUT_OF_SERVICE';
export type ItemStatus = 'OK' | 'WARNING' | 'FAILED' | 'NOT_APPLICABLE' | 'NOT_CHECKED';
export type ChecklistSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type FleetAlertType =
  | 'DTC_CRITICAL'
  | 'MAINTENANCE_DUE'
  | 'DVIR_FAILED'
  | 'VEHICLE_OUT_OF_SERVICE'
  | 'LOW_BATTERY'
  | 'OVERHEAT'
  | 'HIGH_FUEL_CONSUMPTION'
  | 'HARSH_BRAKING'
  | 'ACCIDENT_EVENT'
  | 'REPAIR_OVERDUE'
  | 'DOCUMENT_EXPIRING'
  | 'DRIVER_LICENSE_EXPIRING';
export type FleetAlertStatus = 'OPEN' | 'ACKNOWLEDGED' | 'ASSIGNED' | 'RESOLVED' | 'DISMISSED';
export type FleetMaintenancePriority = 'LOW' | 'NORMAL' | 'HIGH' | 'CRITICAL';
export type FleetMaintenanceStatus = 'OPEN' | 'SCHEDULED' | 'IN_PROGRESS' | 'WAITING_PARTS' | 'COMPLETED' | 'CANCELLED';
export type FleetCostType = 'MAINTENANCE' | 'REPAIR' | 'PARTS' | 'TOWING' | 'FUEL' | 'INSPECTION' | 'INSURANCE' | 'OTHER';

export interface FleetOrganization {
  id: string;
  owner_user_id: string;
  name: string;
  legal_name_nullable: string | null;
  tax_id_nullable: string | null;
  phone: string;
  email: string;
  country: string;
  province: string | null;
  address_nullable: string | null;
  plan: FleetPlan;
  status: 'ACTIVE' | 'SUSPENDED' | 'INACTIVE';
  created_at: string;
  updated_at: string;
}

export interface FleetBranch {
  id: string;
  fleet_id: string;
  name: string;
  location: string | null;
  manager_user_id_nullable: string | null;
  created_at: string;
}

export interface FleetVehicle {
  id: string;
  fleet_id: string;
  branch_id_nullable: string | null;
  vehicle_profile_id: string; // references VehicleProfile.id
  internal_code: string;
  assigned_driver_id_nullable: string | null;
  status: FleetVehicleStatus;
  odometer_km: number;
  last_dvir_id_nullable: string | null;
  last_health_score: number;
  last_scan_at_nullable: string | null;
  created_at: string;
  updated_at: string;
}

export interface FleetDriver {
  id: string;
  fleet_id: string;
  user_id_nullable: string | null; // matches Client.id if registered
  full_name: string;
  phone: string;
  license_number_nullable: string | null;
  license_expiration_nullable: string | null;
  assigned_vehicle_id_nullable: string | null;
  status: FleetDriverStatus;
  created_at: string;
}

export interface DvirInspection {
  id: string;
  fleet_id: string;
  vehicle_id: string;
  driver_id: string;
  inspection_type: InspectionType;
  status: DvirStatus;
  odometer_km: number;
  location_lat_nullable: number | null;
  location_lng_nullable: number | null;
  started_at: string;
  completed_at_nullable: string | null;
  signed_at_nullable: string | null;
  overall_result: OverallResult;
  report_id_nullable: string | null;
  created_at: string;
}

export interface DvirChecklistItem {
  id: string;
  inspection_id: string;
  category:
    | 'MOTOR'
    | 'TRANSMISSION'
    | 'BRAKES'
    | 'TIRES'
    | 'LIGHTS'
    | 'STEERING'
    | 'SUSPENSION'
    | 'FLUIDS'
    | 'BATTERY'
    | 'SAFETY_EQUIPMENT'
    | 'BODY'
    | 'INTERIOR'
    | 'OBD'
    | 'DOCUMENTS';
  item_key: string;
  label: string;
  status: ItemStatus;
  severity: ChecklistSeverity;
  notes: string;
  photo_required: boolean;
  photo_uri_nullable: string | null;
  created_at: string;
}

export interface DvirObdSnapshot {
  inspection_id: string;
  connection_state: 'CONNECTED' | 'DISCONNECTED' | 'NO_ADAPTER';
  adapter_quality: 'EXCELLENT' | 'GOOD' | 'POOR' | 'CLONE_RISK' | 'UNKNOWN';
  dtcs_active: string[];
  dtcs_pending: string[];
  dtcs_permanent: string[];
  readiness: Record<string, boolean>;
  voltage: number;
  rpm_nullable: number | null;
  coolant_temp_nullable: number | null;
  odometer_nullable: number | null;
  raw_hash: string;
  created_at: string;
}

export interface FleetEvidence {
  id: string;
  fleet_id: string;
  vehicle_id: string;
  related_entity_type: 'DVIR' | 'MAINTENANCE' | 'WORK_ORDER' | 'TRIP' | 'VEHICLE';
  related_entity_id: string;
  evidence_type: 'PHOTO' | 'VIDEO' | 'OBD_SNAPSHOT' | 'PDF_REPORT' | 'SIGNATURE' | 'RECEIPT' | 'MEASUREMENT' | 'PART_INVOICE';
  uri: string;
  hash_sha256: string;
  notes: string;
  captured_by_user_id: string;
  captured_at: string;
}

export interface DvirSignature {
  id: string;
  inspection_id: string;
  signer_user_id: string;
  signer_name: string;
  signer_role: FleetRole;
  signature_uri: string;
  signed_at: string;
  hash_sha256: string;
}

export interface FleetAlert {
  id: string;
  fleet_id: string;
  vehicle_id: string;
  driver_id_nullable: string | null;
  alert_type: FleetAlertType;
  severity: ChecklistSeverity;
  title: string;
  description: string;
  status: FleetAlertStatus;
  source: 'DVIR' | 'OBD' | 'MAINTENANCE' | 'TELEMETRY' | 'SYSTEM';
  created_at: string;
  resolved_at_nullable: string | null;
}

export interface FleetMaintenanceTask {
  id: string;
  fleet_id: string;
  vehicle_id: string;
  title: string;
  description: string;
  maintenance_type: string;
  due_km_nullable: number | null;
  due_date_nullable: string | null;
  priority: FleetMaintenancePriority;
  status: FleetMaintenanceStatus;
  assigned_provider_id_nullable: string | null;
  cost_estimate_nullable: number | null;
  report_id_nullable: string | null;
  created_at: string;
}

export interface FleetWorkOrder {
  id: string;
  fleet_id: string;
  vehicle_id: string;
  created_by_user_id: string;
  assigned_provider_id_nullable: string | null;
  title: string;
  description: string;
  source: 'DVIR' | 'DTC' | 'MAINTENANCE' | 'MANUAL' | 'LIVELINK' | 'PREDICTIVE_ALERT';
  status: 'OPEN' | 'SCHEDULED' | 'IN_PROGRESS' | 'WAITING_PARTS' | 'COMPLETED' | 'CANCELLED';
  priority: FleetMaintenancePriority;
  estimated_cost_nullable: number | null;
  final_cost_nullable: number | null;
  started_at_nullable: string | null;
  completed_at_nullable: string | null;
  report_id_nullable: string | null;
}

export interface FleetCostEntry {
  id: string;
  fleet_id: string;
  vehicle_id: string;
  type: FleetCostType;
  amount: number;
  currency: string;
  provider_id_nullable: string | null;
  description: string;
  receipt_uri_nullable: string | null;
  related_work_order_id_nullable: string | null;
  created_at: string;
}

export interface FleetTrip {
  id: string;
  fleet_id: string;
  vehicle_id: string;
  driver_id: string;
  started_at: string;
  ended_at: string;
  distance_km: number;
  eco_score: number;
  harsh_brakes: number;
  harsh_accels: number;
  fuel_used_estimated: number;
  created_at: string;
}

export interface FleetSyncQueueItem {
  id: string;
  idempotency_key: string;
  action: 'CREATE' | 'UPDATE' | 'DELETE';
  table: string;
  payload: any;
  retry_count: number;
  created_at: string;
}

// ═══════════════════════════════════════════════════════════════
// FEATURE: Technical Manuals Center & Offline RAG Engine
// ═══════════════════════════════════════════════════════════════

export enum DocumentType {
  REPAIR_MANUAL = 'REPAIR_MANUAL',
  WIRING_DIAGRAM = 'WIRING_DIAGRAM',
  SERVICE_SPECIFICATION = 'SERVICE_SPECIFICATION',
  TORQUE_SPEC = 'TORQUE_SPEC',
  MAINTENANCE_SCHEDULE = 'MAINTENANCE_SCHEDULE',
  PARTS_CATALOG = 'PARTS_CATALOG',
  OWNER_MANUAL = 'OWNER_MANUAL',
  TSB = 'TSB',
  DIAGNOSTIC_PROCEDURE = 'DIAGNOSTIC_PROCEDURE',
  COMMUNITY_NOTE = 'COMMUNITY_NOTE',
  PDF_REPORT = 'PDF_REPORT',
  UNKNOWN = 'UNKNOWN'
}

export enum SourceType {
  OFFICIAL_SOURCE = 'OFFICIAL_SOURCE',
  USER_UPLOADED = 'USER_UPLOADED',
  OPEN_SOURCE = 'OPEN_SOURCE',
  COMMUNITY_NOTE = 'COMMUNITY_NOTE',
  GENERATED_SUMMARY = 'GENERATED_SUMMARY',
  UNKNOWN = 'UNKNOWN'
}

export enum ExtractionStatus {
  PENDING = 'PENDING',
  PROCESSING = 'PROCESSING',
  READY = 'READY',
  FAILED = 'FAILED',
  UNSUPPORTED = 'UNSUPPORTED',
  ENCRYPTED = 'ENCRYPTED',
  CORRUPTED = 'CORRUPTED'
}

export interface KnowledgeDocument {
  id: string;
  owner_user_id: string;
  vehicle_id_nullable: string | null;
  title: string;
  source_type: SourceType;
  document_type: DocumentType;
  file_uri: string;
  file_hash_sha256: string;
  mime_type: string;
  size_bytes: number;
  language: string;
  make_nullable: string | null;
  model_nullable: string | null;
  year_from_nullable: number | null;
  year_to_nullable: number | null;
  engine_nullable: string | null;
  transmission_nullable: string | null;
  market_region_nullable: string | null;
  source_url_nullable: string | null;
  license_note_nullable: string | null;
  is_offline_available: boolean;
  extraction_status: ExtractionStatus;
  created_at: Date | string;
  updated_at: Date | string;
}

export interface KnowledgeChunk {
  id: string;
  document_id: string;
  vehicle_id_nullable: string | null;
  section_title_nullable: string | null;
  page_start_nullable: number | null;
  page_end_nullable: number | null;
  chunk_index: number;
  text: string;
  token_count: number;
  embedding_vector_nullable: number[] | null;
  content_hash: string;
  created_at: Date | string;
}

export interface KnowledgeCitation {
  id: string;
  chunk_id: string;
  document_id: string;
  page_start: number | null;
  page_end: number | null;
  quoted_text_short: string;
  confidence: 'LOW' | 'MEDIUM' | 'HIGH';
  applicability_note?: string;
}

export interface VehicleApplicability {
  id: string;
  document_id: string;
  make: string;
  model: string;
  year_from: number;
  year_to: number;
  engine_nullable: string | null;
  transmission_nullable: string | null;
  region_nullable: string | null;
  confidence: 'LOW' | 'MEDIUM' | 'HIGH';
}

// Structured Technical Cards
export interface TorqueSpecCard {
  id: string;
  vehicle_id: string | null;
  component: string;
  fastener: string;
  torque_value: number;
  unit: 'Nm' | 'Lb-Ft' | 'Lb-In' | 'Kg-m';
  angle_nullable: number | null;
  sequence_notes: string;
  source_document_id: string | null;
  page_nullable: number | null;
  confidence: 'LOW' | 'MEDIUM' | 'HIGH';
}

export interface FluidSpecCard {
  id: string;
  vehicle_id: string | null;
  system: string;
  fluid_type: string;
  capacity: number;
  unit: 'Liters' | 'Quarts' | 'Gallons' | 'Milliliters';
  specification: string;
  source_document_id: string | null;
  confidence: 'LOW' | 'MEDIUM' | 'HIGH';
}

export interface DiagnosticProcedureCard {
  id: string;
  vehicle_id_nullable: string | null;
  dtc_code_nullable: string | null;
  symptom_nullable: string | null;
  system: string;
  title: string;
  tools_required: string[];
  steps: string[];
  expected_results: string[];
  safety_notes: string[];
  source_document_id: string | null;
  confidence: 'LOW' | 'MEDIUM' | 'HIGH';
}

export interface WiringReferenceCard {
  id: string;
  vehicle_id_nullable: string | null;
  circuit_name: string;
  related_dtcs: string[];
  connectors: string[];
  pins: string[];
  wire_colors: string[];
  expected_voltages: string[];
  grounds: string[];
  source_document_id: string | null;
  confidence: 'LOW' | 'MEDIUM' | 'HIGH';
}

export interface MaintenanceIntervalCard {
  id: string;
  vehicle_id: string | null;
  service_item: string;
  interval_km_nullable: number | null;
  interval_months_nullable: number | null;
  severe_service_interval_nullable: string | null;
  source_document_id: string | null;
  confidence: 'LOW' | 'MEDIUM' | 'HIGH';
}

// Guided Procedure Flow Step
export interface ProcedureStep {
  id: string;
  procedure_id: string;
  order: number;
  title: string;
  instruction: string;
  required_tool_nullable: string | null;
  expected_result_nullable: string | null;
  safety_warning_nullable: string | null;
  evidence_required: boolean;
  source_chunk_id_nullable: string | null;
}

export enum KnowledgeAnswerQuality {
  EXACT_VEHICLE_SOURCE = 'EXACT_VEHICLE_SOURCE',
  SAME_ENGINE_FAMILY = 'SAME_ENGINE_FAMILY',
  SAME_MODEL_DIFFERENT_YEAR = 'SAME_MODEL_DIFFERENT_YEAR',
  GENERIC_SYSTEM_KNOWLEDGE = 'GENERIC_SYSTEM_KNOWLEDGE',
  UNSOURCED = 'UNSOURCED',
  CONTRADICTORY_SOURCES = 'CONTRADICTORY_SOURCES'
}

export interface AiKnowledgeContext {
  relevant_chunks: KnowledgeChunk[];
  citations: KnowledgeCitation[];
  vehicle_applicability: string;
  confidence: 'LOW' | 'MEDIUM' | 'HIGH';
  missing_sources: string[];
}

// Detailed Parts and Guided Repairs for V2 Parts Catalog and 3D Visualizer
export interface PartSpecification {
  oem_number: string;
  equivalent_numbers: string[];
  dimensions: string;
  material: string;
  weight_kg?: number;
  torque_nm?: string;
  lubricants?: string;
  pinout?: Record<string, string>;
}

export interface DetailedPart {
  id: string;
  name: string;
  aliases: string[];
  category: string;
  system: string;
  subsystem: string;
  assembly: string;
  subassembly?: string;
  description: string;
  position: 'LEFT' | 'RIGHT' | 'FRONT' | 'REAR' | 'CENTER';
  specification: PartSpecification;
  symptoms: string[];
  related_dtcs: string[];
  confidence_level: 'CONFIRMED' | 'PROBABLE' | 'UNCONFIRMED';
  publication_state?: 'REVIEW_REQUIRED' | 'PUBLISHED' | 'REJECTED';
  compatibility_state?: 'REQUIRES_VERIFICATION' | 'PROBABLE' | 'EXACT';
  compatibility_message?: string;
  required_compatibility_evidence?: string[];
  visual_authority?: 'GENERIC_SCHEMATIC' | 'VALIDATED_MODEL' | 'OEM_MODEL';
  source_refs?: Array<{
    source_file_name: string;
    source_document_sha256: string;
    source_block_id: string;
    source_text_hash: string;
    section_path: string[];
    review_status: string;
  }>;
}

export interface RepairStep3D {
  id: string;
  order: number;
  title: string;
  description: string;
  type: 'DISASSEMBLE' | 'ASSEMBLE' | 'TORQUE' | 'INSPECT' | 'ALIGN';
  target_node_id: string;
  animation_action: 'TRANSLATE_X' | 'TRANSLATE_Y' | 'TRANSLATE_Z' | 'ROTATE_X' | 'EXPLODE' | 'NONE';
  required_tools: string[];
  torque_spec?: string;
  warning_notes?: string;
  expected_measurement?: string;
  completion_gate?: 'MANUAL_CONFIRMATION' | 'COMPATIBILITY_EVIDENCE_REQUIRED' | 'VERIFIED_TORQUE_REQUIRED' | 'ALIGNMENT_EVIDENCE_REQUIRED';
  required_evidence?: string[];
  technical_value_message?: string;
}

export interface GuidedRepairProcedure {
  id: string;
  title: string;
  vehicle_applicability: string;
  estimated_duration_min: number;
  difficulty: 'EASY' | 'MEDIUM' | 'HARD';
  safety_level: 'SAFE' | 'CAUTION' | 'DANGER';
  prerequisites: string[];
  steps: RepairStep3D[];
  final_verification: string[];
  publication_state?: 'REVIEW_REQUIRED' | 'PUBLISHED';
  execution_policy?: 'TRAINING_ONLY_REVIEW_REQUIRED' | 'APPROVED_SERVICE_PROCEDURE';
}
