
import { Appointment, Barber, Service, AppointmentStatus } from '../types';
import { TIME_SLICE_MINUTES } from '../constants';

// --- Core Calculations ---

export const calculateEndTime = (startTime: Date, serviceDuration: number, barberSpeed: number): Date => {
  const realDuration = Math.ceil(serviceDuration * barberSpeed);
  return new Date(startTime.getTime() + realDuration * 60000);
};

export const isSlotAvailable = (
  start: Date, 
  end: Date, 
  barberId: string, 
  appointments: Appointment[],
  excludeAppointmentId?: string
): boolean => {
  const barberAppointments = appointments.filter(a => 
    a.barberId === barberId && 
    a.status !== AppointmentStatus.CANCELLED &&
    a.status !== AppointmentStatus.DELAYED &&
    a.id !== excludeAppointmentId
  );

  for (const apt of barberAppointments) {
    // Check intersection
    if (start < apt.expectedEndTime && end > apt.startTime) {
      return false;
    }
  }
  return true;
};

// --- Smart Scheduling Engine ---

export interface ScoredSlot {
  time: Date;
  status: 'AVAILABLE' | 'OCCUPIED' | 'LOCKED'; // LOCKED = Past time or closed
  score: 'STANDARD' | 'OPTIMAL' | 'AI_PERFECT_MATCH'; // AI Scoring
  blockReason?: string;
}

// 45 Minute Rule Logic
export const canClientCancel = (appointmentDate: Date): boolean => {
    const now = new Date();
    const diffMs = appointmentDate.getTime() - now.getTime();
    const diffMins = diffMs / 60000;
    return diffMins >= 45;
};

export const generateSmartGrid = (
  date: Date, 
  barberId: string, 
  serviceDuration: number, 
  barberSpeed: number,
  appointments: Appointment[],
  openHour: number,
  closeHour: number,
  timeSliceMinutes: number = 30 // Default fallback
): ScoredSlot[] => {
  const slots: ScoredSlot[] = [];
  const startOfDay = new Date(date);
  startOfDay.setHours(openHour, 0, 0, 0);
  const endOfDay = new Date(date);
  endOfDay.setHours(closeHour, 0, 0, 0);

  const durationMs = Math.ceil(serviceDuration * barberSpeed) * 60000;
  
  // Filter appointments for this barber and day
  const relevantAppointments = appointments.filter(a => 
    a.barberId === barberId && 
    a.status !== AppointmentStatus.CANCELLED &&
    a.startTime.getDate() === date.getDate()
  ).sort((a,b) => a.startTime.getTime() - b.startTime.getTime());

  let current = startOfDay;
  const now = new Date();

  // Create grid based on dynamic timeSliceMinutes passed from App state
  while (current < endOfDay) {
    const slotTime = new Date(current);
    const proposedEnd = new Date(slotTime.getTime() + durationMs);
    
    // 1. Check if passed time (LOCKED)
    if (date.getDate() === now.getDate() && slotTime < now) {
        slots.push({ time: slotTime, status: 'LOCKED', score: 'STANDARD', blockReason: 'Pasado' });
        current = new Date(current.getTime() + timeSliceMinutes * 60000);
        continue;
    }

    // 2. Check Overlaps (OCCUPIED)
    if (!isSlotAvailable(slotTime, proposedEnd, barberId, appointments)) {
        slots.push({ time: slotTime, status: 'OCCUPIED', score: 'STANDARD', blockReason: 'Ocupado' });
        current = new Date(current.getTime() + timeSliceMinutes * 60000);
        continue;
    }

    // 3. Check Closing Time (LOCKED)
    if (proposedEnd > endOfDay) {
         // Doesn't fit before close
         // We don't push it or push as locked
         current = new Date(current.getTime() + timeSliceMinutes * 60000);
         continue;
    }

    // 4. AI SCORING (GAP FILLING)
    let aiScore: 'STANDARD' | 'OPTIMAL' | 'AI_PERFECT_MATCH' = 'STANDARD';
    
    // Check adjacency to existing appointments
    let touchesPrevious = false;
    let touchesNext = false;

    // Check Start of Day
    if (slotTime.getTime() === startOfDay.getTime()) touchesPrevious = true;

    for (const apt of relevantAppointments) {
        // Starts exactly when another ends
        if (Math.abs(slotTime.getTime() - apt.expectedEndTime.getTime()) < 60000) touchesPrevious = true;
        // Ends exactly when another starts
        if (Math.abs(proposedEnd.getTime() - apt.startTime.getTime()) < 60000) touchesNext = true;
    }

    if (touchesPrevious && touchesNext) {
        // Fills a perfect hole
        aiScore = 'AI_PERFECT_MATCH'; 
    } else if (touchesPrevious || touchesNext) {
        // Adherent to cluster
        aiScore = 'OPTIMAL';
    }

    slots.push({
      time: slotTime,
      status: 'AVAILABLE',
      score: aiScore
    });

    current = new Date(current.getTime() + timeSliceMinutes * 60000);
  }
  return slots;
};

// Keep old function signature for compatibility if needed, or redirect
export const generateSmartSlots = generateSmartGrid; 

// --- Analytics Transformers ---

export const getHourlyLoad = (appointments: Appointment[], date: Date, openHour: number, closeHour: number) => {
  const distribution = new Array(closeHour - openHour).fill(0);
  
  appointments.forEach(apt => {
    // Only count appointments for the selected date
    if (apt.startTime.getDate() !== date.getDate() || apt.status === AppointmentStatus.CANCELLED) return;

    const startHour = apt.startTime.getHours();
    const endHour = apt.expectedEndTime.getHours();

    for (let h = startHour; h <= endHour; h++) {
        if (h >= openHour && h < closeHour) {
            const index = h - openHour;
            // Simple weight: 1 unit per hour touched
            distribution[index] += 1;
        }
    }
  });

  // Convert to chart format
  return distribution.map((val, idx) => ({
    time: `${idx + openHour}:00`,
    occupancy: val * 20 // Arbitrary scaling for visual demo
  }));
};

export const getServiceBreakdown = (appointments: Appointment[], services: any[]) => {
  const counts: Record<string, number> = {};
  
  appointments.forEach(apt => {
    if (apt.status === AppointmentStatus.CANCELLED) return;
    const s = services.find(srv => srv.id === apt.serviceId);
    // CRITICAL UPDATE: Use full name, do not split
    const name = s ? s.name : 'Unknown'; 
    counts[name] = (counts[name] || 0) + 1;
  });

  // Convert to array and sort by value DESCENDING (Most popular first)
  return Object.entries(counts)
    .map(([name, value]) => ({ name, value }))
    .sort((a, b) => b.value - a.value);
};

// --- Revenue Logic ---

export interface RevenueStats {
    daily: number;
    weekly: number;
    monthly: number;
    yearly: number;
}

export type TimeFrame = 'daily' | 'weekly' | 'monthly' | 'yearly';

export const calculateRevenueStats = (appointments: Appointment[], currentDate: Date): RevenueStats => {
    const startOfDay = new Date(currentDate);
    startOfDay.setHours(0,0,0,0);
    const endOfDay = new Date(currentDate);
    endOfDay.setHours(23,59,59,999);

    const startOfWeek = new Date(startOfDay);
    startOfWeek.setDate(startOfDay.getDate() - startOfDay.getDay());
    startOfWeek.setHours(0,0,0,0);
    const endOfWeek = new Date(startOfWeek);
    endOfWeek.setDate(startOfWeek.getDate() + 6);
    endOfWeek.setHours(23,59,59,999);

    const startOfMonth = new Date(currentDate.getFullYear(), currentDate.getMonth(), 1);
    const endOfMonth = new Date(currentDate.getFullYear(), currentDate.getMonth() + 1, 0, 23, 59, 59);

    const startOfYear = new Date(currentDate.getFullYear(), 0, 1);
    const endOfYear = new Date(currentDate.getFullYear(), 11, 31, 23, 59, 59);

    return appointments.reduce((acc, apt) => {
        if (apt.status === AppointmentStatus.CANCELLED) return acc;
        
        const aptTime = apt.startTime.getTime();
        const price = apt.price;

        if (aptTime >= startOfDay.getTime() && aptTime <= endOfDay.getTime()) acc.daily += price;
        if (aptTime >= startOfWeek.getTime() && aptTime <= endOfWeek.getTime()) acc.weekly += price;
        if (aptTime >= startOfMonth.getTime() && aptTime <= endOfMonth.getTime()) acc.monthly += price;
        if (aptTime >= startOfYear.getTime() && aptTime <= endOfYear.getTime()) acc.yearly += price;

        return acc;
    }, { daily: 0, weekly: 0, monthly: 0, yearly: 0 });
};

export const getRevenueTrend = (
    appointments: Appointment[], 
    view: TimeFrame, 
    currentDate: Date,
    openHour: number,
    closeHour: number
) => {
    const data: Record<string, number> = {};
    let labels: string[] = [];

    if (view === 'daily') {
        // Initialize hours based on dynamic config
        for (let i = openHour; i < closeHour; i++) {
            data[`${i}:00`] = 0;
            labels.push(`${i}:00`);
        }

        const startOfDay = new Date(currentDate);
        startOfDay.setHours(0,0,0,0);
        const endOfDay = new Date(currentDate);
        endOfDay.setHours(23,59,59,999);

        appointments.forEach(apt => {
            if (apt.status === AppointmentStatus.CANCELLED) return;
            if (apt.startTime >= startOfDay && apt.startTime <= endOfDay) {
                const hour = apt.startTime.getHours();
                if (hour >= openHour && hour < closeHour) {
                    data[`${hour}:00`] += apt.price;
                }
            }
        });

    } else if (view === 'weekly') {
        const days = ['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb'];
        labels = days;
        days.forEach(d => data[d] = 0);

        const startOfWeek = new Date(currentDate);
        startOfWeek.setDate(currentDate.getDate() - currentDate.getDay());
        startOfWeek.setHours(0,0,0,0);
        const endOfWeek = new Date(startOfWeek);
        endOfWeek.setDate(startOfWeek.getDate() + 6);
        endOfWeek.setHours(23,59,59,999);

        appointments.forEach(apt => {
            if (apt.status === AppointmentStatus.CANCELLED) return;
            if (apt.startTime >= startOfWeek && apt.startTime <= endOfWeek) {
                const dayName = days[apt.startTime.getDay()];
                data[dayName] += apt.price;
            }
        });

    } else if (view === 'monthly') {
        const daysInMonth = new Date(currentDate.getFullYear(), currentDate.getMonth() + 1, 0).getDate();
        for(let i=1; i<=daysInMonth; i++) {
            data[i.toString()] = 0;
            labels.push(i.toString());
        }

        const startOfMonth = new Date(currentDate.getFullYear(), currentDate.getMonth(), 1);
        const endOfMonth = new Date(currentDate.getFullYear(), currentDate.getMonth() + 1, 0, 23, 59, 59);

        appointments.forEach(apt => {
            if (apt.status === AppointmentStatus.CANCELLED) return;
            if (apt.startTime >= startOfMonth && apt.startTime <= endOfMonth) {
                data[apt.startTime.getDate().toString()] += apt.price;
            }
        });

    } else if (view === 'yearly') {
        const months = ['Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun', 'Jul', 'Ago', 'Sep', 'Oct', 'Nov', 'Dic'];
        labels = months;
        months.forEach(m => data[m] = 0);

        const startOfYear = new Date(currentDate.getFullYear(), 0, 1);
        const endOfYear = new Date(currentDate.getFullYear(), 11, 31, 23, 59, 59);

        appointments.forEach(apt => {
            if (apt.status === AppointmentStatus.CANCELLED) return;
            if (apt.startTime >= startOfYear && apt.startTime <= endOfYear) {
                const monthName = months[apt.startTime.getMonth()];
                data[monthName] += apt.price;
            }
        });
    }

    return labels.map(label => ({
        name: label,
        value: data[label] || 0
    }));
};


// --- Utilities ---

export const formatTime = (date: Date): string => {
  return date.toLocaleTimeString('es-CR', { hour: '2-digit', minute: '2-digit', hour12: true });
};

export const formatDate = (date: Date): string => {
  return date.toLocaleDateString('es-CR', { weekday: 'long', month: 'long', day: 'numeric' });
};

export const getStatusColor = (status: AppointmentStatus): string => {
  switch (status) {
    case AppointmentStatus.SCHEDULED: return 'bg-blue-600/20 border-blue-500 text-blue-100';
    case AppointmentStatus.CONFIRMED: return 'bg-indigo-600/20 border-indigo-500 text-indigo-100';
    case AppointmentStatus.CHECKED_IN: return 'bg-purple-600/20 border-purple-500 text-purple-100';
    case AppointmentStatus.IN_PROGRESS: return 'bg-brand-500/20 border-brand-500 text-brand-100 animate-pulse-slow';
    case AppointmentStatus.COMPLETED: return 'bg-emerald-600/20 border-emerald-500 text-emerald-100';
    case AppointmentStatus.DELAYED: return 'bg-red-600/20 border-red-500 text-red-100';
    case AppointmentStatus.CANCELLED: return 'bg-gray-600/20 border-gray-500 text-gray-400 opacity-50';
    default: return 'bg-gray-700 border-gray-600';
  }
};
