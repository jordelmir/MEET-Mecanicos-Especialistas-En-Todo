
import { Mechanic, Service, WorkOrder, WorkOrderStatus, Client, ServiceHistoryItem, CatalogSection, ServiceCategory, VehicleInfo } from './types';

// Default Fallbacks
export const DEFAULT_OPEN_HOUR = 7;
export const DEFAULT_CLOSE_HOUR = 18;
export const TIME_SLICE_MINUTES = 30;

// ─── ADMIN USER ──────────────────────────────────────────────────────────────
export const MOCK_ADMIN_USER: Client = {
  id: 'admin_01',
  name: 'Gerente de Taller',
  phone: '',
  email: 'admin@taller.cr',
  identification: '000000000',
  accessCode: '000000',
  vehicles: [],
  serviceHistory: [],
  joinDate: new Date('2020-01-01'),
  loyaltyPoints: 0,
  avatar: 'https://ui-avatars.com/api/?name=Admin&background=e8a020&color=000&bold=true&size=150',
  notes: 'Cuenta Administrador Principal',
};

// ─── FULL SERVICE CATALOG ────────────────────────────────────────────────────
export const SERVICE_CATALOG: CatalogSection[] = [
  {
    id: "motor", icon: "🔧", title: "MOTOR Y COMPONENTES INTERNOS",
    items: [
      { name: "Cambio de aceite de motor", category: ServiceCategory.MANT, estimatedMinutes: 30 },
      { name: "Cambio de filtro de aceite", category: ServiceCategory.CAM, estimatedMinutes: 15 },
      { name: "Cambio de filtro de aire", category: ServiceCategory.CAM, estimatedMinutes: 15 },
      { name: "Cambio de bujías", category: ServiceCategory.CAM, estimatedMinutes: 45 },
      { name: "Cambio de cables de bujías", category: ServiceCategory.CAM, estimatedMinutes: 30 },
      { name: "Cambio de bobinas de encendido", category: ServiceCategory.CAM, estimatedMinutes: 45 },
      { name: "Cambio de correa de distribución", category: ServiceCategory.CAM, estimatedMinutes: 240 },
      { name: "Cambio de cadena de distribución", category: ServiceCategory.CAM, estimatedMinutes: 360 },
      { name: "Cambio de tensor de distribución", category: ServiceCategory.CAM, estimatedMinutes: 120 },
      { name: "Rectificación de culata", category: ServiceCategory.REP, estimatedMinutes: 480 },
      { name: "Cambio de sellos de válvulas", category: ServiceCategory.REP, estimatedMinutes: 360 },
      { name: "Cambio de anillos de pistón", category: ServiceCategory.REP, estimatedMinutes: 600 },
      { name: "Cambio de bomba de aceite", category: ServiceCategory.CAM, estimatedMinutes: 180 },
      { name: "Overhaul completo de motor", category: ServiceCategory.REP, estimatedMinutes: 2400 },
      { name: "Diagnóstico de compresión de motor", category: ServiceCategory.DIAG, estimatedMinutes: 60 },
      { name: "Diagnóstico de ruidos de motor", category: ServiceCategory.DIAG, estimatedMinutes: 45 },
    ]
  },
  {
    id: "enfriamiento", icon: "🌡️", title: "SISTEMA DE ENFRIAMIENTO",
    items: [
      { name: "Cambio de refrigerante / anticongelante", category: ServiceCategory.MANT, estimatedMinutes: 30 },
      { name: "Cambio de termostato", category: ServiceCategory.CAM, estimatedMinutes: 60 },
      { name: "Cambio de radiador", category: ServiceCategory.CAM, estimatedMinutes: 120 },
      { name: "Reparación de radiador", category: ServiceCategory.REP, estimatedMinutes: 90 },
      { name: "Cambio de bomba de agua", category: ServiceCategory.CAM, estimatedMinutes: 180 },
      { name: "Cambio de mangueras de enfriamiento", category: ServiceCategory.CAM, estimatedMinutes: 60 },
      { name: "Cambio de ventilador eléctrico del radiador", category: ServiceCategory.CAM, estimatedMinutes: 90 },
      { name: "Cambio de sensor de temperatura", category: ServiceCategory.CAM, estimatedMinutes: 30 },
      { name: "Diagnóstico de sobrecalentamiento", category: ServiceCategory.DIAG, estimatedMinutes: 45 },
    ]
  },
  {
    id: "combustible", icon: "⛽", title: "SISTEMA DE COMBUSTIBLE",
    items: [
      { name: "Cambio de filtro de combustible", category: ServiceCategory.CAM, estimatedMinutes: 30 },
      { name: "Cambio de bomba de combustible", category: ServiceCategory.CAM, estimatedMinutes: 120 },
      { name: "Limpieza de inyectores", category: ServiceCategory.MANT, estimatedMinutes: 60 },
      { name: "Cambio de inyectores", category: ServiceCategory.CAM, estimatedMinutes: 120 },
      { name: "Cambio de sensor MAP / MAF", category: ServiceCategory.CAM, estimatedMinutes: 30 },
      { name: "Cambio de sensor de oxígeno (O2)", category: ServiceCategory.CAM, estimatedMinutes: 45 },
      { name: "Limpieza de cuerpo de aceleración", category: ServiceCategory.MANT, estimatedMinutes: 45 },
      { name: "Diagnóstico de presión de combustible", category: ServiceCategory.DIAG, estimatedMinutes: 30 },
    ]
  },
  {
    id: "transmision", icon: "⚙️", title: "TRANSMISIÓN Y CAJA DE CAMBIOS",
    items: [
      { name: "Cambio de aceite de transmisión manual", category: ServiceCategory.MANT, estimatedMinutes: 30 },
      { name: "Cambio de aceite de transmisión automática (ATF)", category: ServiceCategory.MANT, estimatedMinutes: 45 },
      { name: "Cambio de embrague (clutch) completo", category: ServiceCategory.CAM, estimatedMinutes: 360 },
      { name: "Cambio de disco de embrague", category: ServiceCategory.CAM, estimatedMinutes: 300 },
      { name: "Cambio de cilindro maestro de embrague", category: ServiceCategory.CAM, estimatedMinutes: 90 },
      { name: "Reparación de transmisión manual", category: ServiceCategory.REP, estimatedMinutes: 600 },
      { name: "Reparación de transmisión automática", category: ServiceCategory.REP, estimatedMinutes: 720 },
      { name: "Cambio de transmisión completa", category: ServiceCategory.CAM, estimatedMinutes: 480 },
      { name: "Diagnóstico de caja de cambios", category: ServiceCategory.DIAG, estimatedMinutes: 60 },
    ]
  },
  {
    id: "frenos", icon: "🛑", title: "SISTEMA DE FRENOS",
    items: [
      { name: "Cambio de pastillas de freno delanteras", category: ServiceCategory.CAM, estimatedMinutes: 45 },
      { name: "Cambio de pastillas de freno traseras", category: ServiceCategory.CAM, estimatedMinutes: 45 },
      { name: "Cambio de discos de freno delanteros", category: ServiceCategory.CAM, estimatedMinutes: 60 },
      { name: "Cambio de discos de freno traseros", category: ServiceCategory.CAM, estimatedMinutes: 60 },
      { name: "Cambio de líquido de frenos", category: ServiceCategory.MANT, estimatedMinutes: 30 },
      { name: "Purga del sistema de frenos", category: ServiceCategory.MANT, estimatedMinutes: 30 },
      { name: "Cambio de cilindro maestro de freno", category: ServiceCategory.CAM, estimatedMinutes: 120 },
      { name: "Cambio de calibrador / mordaza (caliper)", category: ServiceCategory.CAM, estimatedMinutes: 90 },
      { name: "Rectificado de discos de freno", category: ServiceCategory.REP, estimatedMinutes: 60 },
      { name: "Diagnóstico de sistema ABS", category: ServiceCategory.DIAG, estimatedMinutes: 45 },
      { name: "Cambio de sensor ABS de rueda", category: ServiceCategory.CAM, estimatedMinutes: 45 },
    ]
  },
  {
    id: "suspension", icon: "🏎️", title: "SUSPENSIÓN Y DIRECCIÓN",
    items: [
      { name: "Cambio de amortiguadores delanteros", category: ServiceCategory.CAM, estimatedMinutes: 120 },
      { name: "Cambio de amortiguadores traseros", category: ServiceCategory.CAM, estimatedMinutes: 120 },
      { name: "Cambio de resortes (espirales)", category: ServiceCategory.CAM, estimatedMinutes: 120 },
      { name: "Cambio de rótula de dirección", category: ServiceCategory.CAM, estimatedMinutes: 90 },
      { name: "Cambio de terminal de dirección", category: ServiceCategory.CAM, estimatedMinutes: 60 },
      { name: "Cambio de cremallera de dirección", category: ServiceCategory.CAM, estimatedMinutes: 300 },
      { name: "Cambio de bomba de dirección hidráulica", category: ServiceCategory.CAM, estimatedMinutes: 120 },
      { name: "Cambio de rodamiento de rueda", category: ServiceCategory.CAM, estimatedMinutes: 120 },
      { name: "Alineación de dirección al frente", category: ServiceCategory.MANT, estimatedMinutes: 30 },
      { name: "Alineación 4 ruedas", category: ServiceCategory.MANT, estimatedMinutes: 45 },
      { name: "Balanceo de neumáticos", category: ServiceCategory.MANT, estimatedMinutes: 30 },
      { name: "Diagnóstico de ruidos de suspensión", category: ServiceCategory.DIAG, estimatedMinutes: 45 },
    ]
  },
  {
    id: "electrico", icon: "⚡", title: "SISTEMA ELÉCTRICO Y ELECTRÓNICO",
    items: [
      { name: "Cambio de batería", category: ServiceCategory.CAM, estimatedMinutes: 15 },
      { name: "Prueba y diagnóstico de batería", category: ServiceCategory.DIAG, estimatedMinutes: 15 },
      { name: "Cambio de alternador", category: ServiceCategory.CAM, estimatedMinutes: 90 },
      { name: "Cambio de motor de arranque (starter)", category: ServiceCategory.CAM, estimatedMinutes: 90 },
      { name: "Diagnóstico computarizado (OBD-II)", category: ServiceCategory.DIAG, estimatedMinutes: 30 },
      { name: "Borrado de códigos de falla", category: ServiceCategory.MANT, estimatedMinutes: 15 },
      { name: "Reparación de cableado eléctrico", category: ServiceCategory.REP, estimatedMinutes: 120 },
      { name: "Diagnóstico de cortocircuito", category: ServiceCategory.DIAG, estimatedMinutes: 60 },
      { name: "Cambio de luces delanteras (faro)", category: ServiceCategory.CAM, estimatedMinutes: 30 },
      { name: "Cambio de luces traseras", category: ServiceCategory.CAM, estimatedMinutes: 20 },
    ]
  },
  {
    id: "acaire", icon: "❄️", title: "AIRE ACONDICIONADO Y CALEFACCIÓN",
    items: [
      { name: "Recarga de gas refrigerante (A/C)", category: ServiceCategory.MANT, estimatedMinutes: 45 },
      { name: "Diagnóstico del sistema de A/C", category: ServiceCategory.DIAG, estimatedMinutes: 30 },
      { name: "Cambio de compresor de A/C", category: ServiceCategory.CAM, estimatedMinutes: 180 },
      { name: "Cambio de condensador de A/C", category: ServiceCategory.CAM, estimatedMinutes: 120 },
      { name: "Cambio de evaporador de A/C", category: ServiceCategory.CAM, estimatedMinutes: 240 },
      { name: "Cambio de filtro de cabina (polen)", category: ServiceCategory.CAM, estimatedMinutes: 15 },
      { name: "Reparación de fugas en sistema de A/C", category: ServiceCategory.REP, estimatedMinutes: 120 },
    ]
  },
  {
    id: "neumaticos", icon: "🛞", title: "NEUMÁTICOS Y RINES",
    items: [
      { name: "Montaje y desmontaje de neumático", category: ServiceCategory.MANT, estimatedMinutes: 20 },
      { name: "Balanceo de neumático", category: ServiceCategory.MANT, estimatedMinutes: 20 },
      { name: "Reparación de ponchadura (parche)", category: ServiceCategory.REP, estimatedMinutes: 20 },
      { name: "Cambio de neumático completo", category: ServiceCategory.CAM, estimatedMinutes: 15 },
      { name: "Rotación de neumáticos", category: ServiceCategory.MANT, estimatedMinutes: 30 },
      { name: "Reparación de rin (enderezado)", category: ServiceCategory.REP, estimatedMinutes: 60 },
      { name: "Cambio de sensor TPMS", category: ServiceCategory.CAM, estimatedMinutes: 30 },
    ]
  },
  {
    id: "inspeccion", icon: "📋", title: "INSPECCIÓN Y MANTENIMIENTO GENERAL",
    items: [
      { name: "Inspección general (checklist 50 puntos)", category: ServiceCategory.DIAG, estimatedMinutes: 60 },
      { name: "Mantenimiento preventivo 5,000 km", category: ServiceCategory.MANT, estimatedMinutes: 60 },
      { name: "Mantenimiento preventivo 10,000 km", category: ServiceCategory.MANT, estimatedMinutes: 90 },
      { name: "Mantenimiento preventivo 30,000 km", category: ServiceCategory.MANT, estimatedMinutes: 120 },
      { name: "Mantenimiento preventivo 60,000 km", category: ServiceCategory.MANT, estimatedMinutes: 180 },
      { name: "Revisión pre-viaje", category: ServiceCategory.DIAG, estimatedMinutes: 30 },
      { name: "Revisión post-compra (vehículo usado)", category: ServiceCategory.DIAG, estimatedMinutes: 60 },
      { name: "Escaneo eléctrico completo", category: ServiceCategory.DIAG, estimatedMinutes: 45 },
    ]
  },
];

// ─── DEFAULT SERVICES (Quick-access) ────────────────────────────────────────
export const SERVICES: Service[] = [
  { id: 's1', name: 'Cambio de Aceite Completo', category: ServiceCategory.MANT, estimatedMinutes: 45, basePrice: 25000, description: 'Aceite + Filtro + Revisión de niveles' },
  { id: 's2', name: 'Diagnóstico Computarizado', category: ServiceCategory.DIAG, estimatedMinutes: 30, basePrice: 15000, description: 'Escaneo OBD-II completo con reporte' },
  { id: 's3', name: 'Frenos Completos (Eje)', category: ServiceCategory.CAM, estimatedMinutes: 90, basePrice: 45000, description: 'Pastillas + discos por eje' },
  { id: 's4', name: 'Alineación y Balanceo', category: ServiceCategory.MANT, estimatedMinutes: 45, basePrice: 20000, description: 'Alineación 4 ruedas + balanceo' },
  { id: 's5', name: 'Servicio Mayor', category: ServiceCategory.MANT, estimatedMinutes: 240, basePrice: 85000, description: 'Aceite, filtros, bujías, revisión completa' },
  { id: 's6', name: 'Recarga de A/C', category: ServiceCategory.MANT, estimatedMinutes: 45, basePrice: 35000, description: 'Recarga de gas + verificación de fugas' },
];

// ─── MECHANICS ───────────────────────────────────────────────────────────────
export const MECHANICS: Mechanic[] = [
  {
    id: 'm1',
    name: 'Carlos "El Maestro" Ruiz',
    phone: '8801-1111',
    specialty: 'MOTOR',
    efficiencyFactor: 1.15,
    avatar: 'https://ui-avatars.com/api/?name=Carlos+R&background=e8a020&color=000&bold=true&size=150',
    identification: '101110111',
    accessCode: '111111',
    email: 'carlos@taller.cr',
    certifications: ['ASE Master Tech', 'Toyota Certified'],
  },
  {
    id: 'm2',
    name: 'Diego Hernández',
    phone: '8802-2222',
    specialty: 'ELECTRICO',
    efficiencyFactor: 1.0,
    avatar: 'https://ui-avatars.com/api/?name=Diego+H&background=2563eb&color=fff&bold=true&size=150',
    identification: '202220222',
    accessCode: '222222',
    email: 'diego@taller.cr',
    certifications: ['Bosch Certified'],
  },
  {
    id: 'm3',
    name: 'Marco Solano',
    phone: '8803-3333',
    specialty: 'GENERAL',
    efficiencyFactor: 0.95,
    avatar: 'https://ui-avatars.com/api/?name=Marco+S&background=27ae60&color=fff&bold=true&size=150',
    identification: '303330333',
    accessCode: '333333',
    email: 'marco@taller.cr',
  },
];

// Helper to create past dates
const pastDate = (daysAgo: number) => {
  const d = new Date();
  d.setDate(d.getDate() - daysAgo);
  return d;
};

// Mock service history
const generateServiceHistory = (count: number): ServiceHistoryItem[] => {
  const serviceNames = ['Cambio de Aceite', 'Diagnóstico OBD-II', 'Cambio de Pastillas', 'Alineación', 'Cambio de Bujías'];
  const mechanics = ['Carlos "El Maestro" Ruiz', 'Diego Hernández', 'Marco Solano'];
  return Array.from({ length: count }).map((_, i) => ({
    id: `h${i}`,
    date: pastDate((i + 1) * 21),
    serviceName: serviceNames[i % serviceNames.length],
    mechanicName: mechanics[i % mechanics.length],
    price: [25000, 15000, 45000, 20000, 12000][i % 5],
    vehicleInfo: 'Toyota Corolla 2019',
    notes: i === 0 ? 'Requiere seguimiento en 5,000 km.' : undefined
  }));
};

// ─── CLIENTS ─────────────────────────────────────────────────────────────────
export const INITIAL_CLIENTS: Client[] = [
  {
    id: 'c1',
    name: 'Juan Pérez',
    phone: '8888-0101',
    email: 'juan@ejemplo.com',
    identification: '111111111',
    accessCode: '123456',
    vehicles: [
      { plate: 'ABC-123', brand: 'Toyota', model: 'Corolla', year: 2019, color: 'Gris', mileage: 65000, fuelType: 'Gasolina' },
      { plate: 'XYZ-789', brand: 'Hyundai', model: 'Tucson', year: 2021, color: 'Blanco', mileage: 32000, fuelType: 'Gasolina' },
    ],
    serviceHistory: generateServiceHistory(8),
    lastVisit: pastDate(14),
    joinDate: pastDate(730),
    loyaltyPoints: 24,
    avatar: 'https://ui-avatars.com/api/?name=Juan+P&background=1a1a1a&color=e8a020&bold=true&size=150',
    notes: 'Cliente frecuente. Prefiere trabajos en la mañana.',
  },
  {
    id: 'c2',
    name: 'María Rodríguez',
    phone: '8888-0102',
    email: 'maria@ejemplo.com',
    identification: '222222222',
    accessCode: '654321',
    vehicles: [
      { plate: 'DEF-456', brand: 'Honda', model: 'CR-V', year: 2020, color: 'Rojo', mileage: 48000, fuelType: 'Gasolina' },
    ],
    serviceHistory: generateServiceHistory(4),
    lastVisit: pastDate(45),
    joinDate: pastDate(365),
    loyaltyPoints: 8,
    avatar: 'https://ui-avatars.com/api/?name=Maria+R&background=1a1a1a&color=60a5fa&bold=true&size=150',
    notes: 'Siempre pide factura electrónica.',
  },
];

// Helper to create times for today
const todayAt = (hours: number, minutes: number) => {
  const d = new Date();
  d.setHours(hours, minutes, 0, 0);
  return d;
};

// ─── INITIAL WORK ORDERS ─────────────────────────────────────────────────────
export const INITIAL_WORK_ORDERS: WorkOrder[] = [
  {
    id: 'wo1',
    clientId: 'c1',
    clientName: 'Juan Pérez',
    mechanicId: 'm1',
    serviceId: 's5',
    vehicleInfo: { plate: 'ABC-123', brand: 'Toyota', model: 'Corolla', year: 2019, color: 'Gris', mileage: 65000, fuelType: 'Gasolina' },
    startTime: todayAt(8, 0),
    estimatedEndTime: todayAt(12, 0),
    status: WorkOrderStatus.COMPLETED,
    price: 85000,
    estimatedMinutes: 240,
    notes: 'Servicio mayor completo',
    diagnosticNotes: 'Correa de distribución con desgaste. Recomendar cambio en próximo servicio.',
  },
  {
    id: 'wo2',
    clientId: 'c2',
    clientName: 'María Rodríguez',
    mechanicId: 'm2',
    serviceId: 's2',
    vehicleInfo: { plate: 'DEF-456', brand: 'Honda', model: 'CR-V', year: 2020, color: 'Rojo', mileage: 48000, fuelType: 'Gasolina' },
    startTime: todayAt(10, 0),
    estimatedEndTime: todayAt(10, 30),
    status: WorkOrderStatus.IN_PROGRESS,
    actualStartTime: todayAt(10, 5),
    price: 15000,
    estimatedMinutes: 30,
    notes: 'Luz de check engine encendida',
  },
  {
    id: 'wo3',
    clientId: 'c1',
    clientName: 'Juan Pérez',
    mechanicId: 'm3',
    serviceId: 's4',
    vehicleInfo: { plate: 'XYZ-789', brand: 'Hyundai', model: 'Tucson', year: 2021, color: 'Blanco', mileage: 32000, fuelType: 'Gasolina' },
    startTime: todayAt(13, 0),
    estimatedEndTime: todayAt(13, 45),
    status: WorkOrderStatus.RECEIVED,
    price: 20000,
    estimatedMinutes: 45,
    notes: 'Vibración al frenar',
  },
];