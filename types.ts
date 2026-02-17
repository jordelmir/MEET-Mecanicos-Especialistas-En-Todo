
export enum AppointmentStatus {
  SCHEDULED = 'SCHEDULED',
  CONFIRMED = 'CONFIRMED',
  CHECKED_IN = 'CHECKED_IN',
  IN_PROGRESS = 'IN_PROGRESS',
  COMPLETED = 'COMPLETED',
  CANCELLED = 'CANCELLED',
  DELAYED = 'DELAYED',
}

export enum Role {
  CLIENT = 'CLIENT',
  BARBER = 'BARBER',
  ADMIN = 'ADMIN',
}

export interface BookingHistoryItem {
  id: string;
  date: Date;
  serviceName: string;
  barberName: string;
  price: number;
  notes?: string; // Historical comments/feedback
}

export interface CutPreferences {
  remarks: string;  // Free text always exists
  [categoryId: string]: string; // Dynamic keys for any category (sides, top, color, eyebrows...)
}

export interface StyleCategory {
  id: string;
  label: string;
  items: string[];
}

export type GlobalStyleOptions = StyleCategory[];

export interface Client {
  id: string;
  name: string;
  phone: string;
  email: string; // Used for login
  identification: string; // Used for login (unique)
  accessCode: string; // 6-digit login code
  bookingHistory: BookingHistoryItem[]; 
  lastVisit?: Date;
  joinDate: Date; 
  points: number; 
  notes?: string;
  avatar?: string; 
  preferences?: CutPreferences; 
}

export interface Service {
  id: string;
  name: string;
  durationMinutes: number; 
  price: number;
}

export interface Barber {
  id: string;
  name: string;
  phone: string; // Added phone number
  identification: string; // Used for login
  accessCode: string; // 6-digit login code
  email: string; // Contact email
  tier: 'JUNIOR' | 'SENIOR' | 'MASTER';
  speedFactor: number; 
  avatar: string;
}

export interface Appointment {
  id: string;
  clientId: string; 
  clientName: string; 
  barberId: string;
  serviceId: string;
  startTime: Date;
  expectedEndTime: Date;
  actualStartTime?: Date;
  actualEndTime?: Date;
  status: AppointmentStatus;
  notes?: string;
  isShadow?: boolean; 
  
  price: number; 
  durationMinutes: number;
  
  // Cancellation Metadata
  cancellationReason?: string;
  cancellationDate?: Date;
}

export interface TimeSlice {
  time: Date;
  isOccupied: boolean;
  appointmentId?: string;
}

export interface Metrics {
  dailyOccupancy: number; 
  deadTimeMinutes: number;
  revenue: number;
  appointmentsCompleted: number;
  appointmentsTotal: number;
}