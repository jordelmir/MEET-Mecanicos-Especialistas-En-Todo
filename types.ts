
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