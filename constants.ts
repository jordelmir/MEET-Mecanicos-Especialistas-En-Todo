
import { Barber, Service, Appointment, AppointmentStatus, Client, BookingHistoryItem, GlobalStyleOptions } from './types';

// Default Fallbacks
export const DEFAULT_OPEN_HOUR = 6; 
export const DEFAULT_CLOSE_HOUR = 24; 
export const TIME_SLICE_MINUTES = 15;

export const DEFAULT_STYLE_OPTIONS: GlobalStyleOptions = [
    {
        id: 'sides',
        label: 'Lados / Degradado',
        items: ['Tijera Clásico', 'Bajo (Low Fade)', 'Medio (Mid Fade)', 'Alto (High Fade)', 'Rapado (Skin)', 'Taper Fade']
    },
    {
        id: 'top',
        label: 'Parte Superior',
        items: ['Solo Puntas', 'Texturizado', 'Largo / Peinado', 'Corto Militar', 'Hacia Atrás', 'Crop Top']
    },
    {
        id: 'beard',
        label: 'Barba & Rostro',
        items: ['Sin Barba', 'Solo Bigote', 'Perfilado', 'Rebajar Volumen', 'Leñador', 'Candado']
    },
    {
        id: 'finish',
        label: 'Acabado Final',
        items: ['Natural (Nada)', 'Cera Mate', 'Gel / Brillo', 'Polvo Textura', 'Pomada Clásica']
    }
];

export const SERVICES: Service[] = [
  { id: 's1', name: 'Corte Clásico', durationMinutes: 45, price: 12000 },
  { id: 's2', name: 'Barba y Perfilado', durationMinutes: 30, price: 8000 },
  { id: 's3', name: 'Servicio Real (Corte + Afeitado)', durationMinutes: 75, price: 25000 },
  { id: 's4', name: 'Retoque Rápido', durationMinutes: 15, price: 5000 },
];

export const BARBERS: Barber[] = [
  { 
      id: 'b1', 
      name: 'Alex "Navaja" K.', 
      phone: '8801-1111',
      tier: 'MASTER', 
      speedFactor: 1.1, 
      avatar: 'https://picsum.photos/100/100?random=1',
      identification: '101110111',
      accessCode: '111111',
      email: 'alex@chronos.barber'
  },
  { 
      id: 'b2', 
      name: 'Sara J.', 
      phone: '8802-2222',
      tier: 'SENIOR', 
      speedFactor: 1.0, 
      avatar: 'https://picsum.photos/100/100?random=2',
      identification: '202220222',
      accessCode: '222222',
      email: 'sara@chronos.barber'
  },
  { 
      id: 'b3', 
      name: 'Maikol T.', 
      phone: '8803-3333',
      tier: 'JUNIOR', 
      speedFactor: 0.9, 
      avatar: 'https://picsum.photos/100/100?random=3',
      identification: '303330333',
      accessCode: '333333',
      email: 'maikol@chronos.barber'
  },
];

// Helper to create past dates
const pastDate = (daysAgo: number) => {
    const d = new Date();
    d.setDate(d.getDate() - daysAgo);
    return d;
};

// Mock history generator
const generateHistory = (count: number): BookingHistoryItem[] => {
    return Array.from({ length: count }).map((_, i) => ({
        id: `h${i}`,
        date: pastDate((i + 1) * 14), 
        serviceName: i % 2 === 0 ? 'Corte Clásico' : 'Barba y Perfilado',
        barberName: i % 3 === 0 ? 'Alex "Navaja" K.' : 'Sara J.',
        price: i % 2 === 0 ? 12000 : 8000,
        notes: i === 0 ? 'Cliente pidió corregir patillas la próxima vez.' : undefined
    }));
};

export const INITIAL_CLIENTS: Client[] = [
  { 
      id: 'c1', 
      name: 'Juan Pérez', 
      phone: '8888-0101', 
      email: 'juan@ejemplo.com',
      identification: '111111111',
      accessCode: '123456',
      bookingHistory: generateHistory(12),
      lastVisit: pastDate(14),
      joinDate: pastDate(365),
      points: 12,
      avatar: 'https://i.pravatar.cc/150?img=11',
      notes: 'Prefiere servicio silencioso.',
      preferences: {
          sides: 'Desvanecido Medio (1.5)',
          top: 'Despuntar con tijera',
          beard: 'Rebajar volumen',
          finish: 'Cera Mate',
          remarks: 'Cuidado con el remolino de la coronilla.'
      }
  },
  { 
      id: 'c2', 
      name: 'Marcos W.', 
      phone: '8888-0102', 
      email: 'marcos@ejemplo.com', 
      identification: '222222222',
      accessCode: '654321',
      bookingHistory: generateHistory(5),
      lastVisit: pastDate(28),
      joinDate: pastDate(120),
      points: 5,
      avatar: 'https://i.pravatar.cc/150?img=13',
      notes: 'Piel sensible.' 
  },
];

// MOCK ADMIN USER
export const MOCK_ADMIN_USER: Client = {
    id: 'admin_01',
    name: 'Gerente General',
    phone: '',
    email: 'admin@chronos.barber',
    identification: '000000000',
    accessCode: '000000', // Master Key
    bookingHistory: [],
    joinDate: new Date('2020-01-01'),
    points: 9999,
    avatar: 'https://ui-avatars.com/api/?name=Admin+Boss&background=f0b429&color=000&bold=true',
    notes: 'Cuenta de Administrador Principal'
};

// Helper to create date relative to today
const todayAt = (hours: number, minutes: number) => {
  const d = new Date();
  d.setHours(hours, minutes, 0, 0);
  return d;
};

export const INITIAL_APPOINTMENTS: Appointment[] = [
  {
    id: 'a1',
    clientId: 'c1',
    clientName: 'Juan Pérez',
    barberId: 'b1',
    serviceId: 's3',
    startTime: todayAt(10, 0),
    expectedEndTime: todayAt(11, 15),
    status: AppointmentStatus.COMPLETED,
    price: 25000,
    durationMinutes: 75
  },
  {
    id: 'a2',
    clientId: 'c2',
    clientName: 'Marcos W.',
    barberId: 'b1',
    serviceId: 's1',
    startTime: todayAt(11, 30),
    expectedEndTime: todayAt(12, 15),
    status: AppointmentStatus.IN_PROGRESS,
    actualStartTime: todayAt(11, 35), 
    price: 12000,
    durationMinutes: 45
  },
];