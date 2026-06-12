
import { Mechanic, Service, WorkOrder, WorkOrderStatus } from '../types';

export interface ScheduleValidationResult {
  valid: boolean;
  errors: string[];
  endTime: Date;
  realDurationMinutes: number;
}

/**
 * Calculate the estimated end time for a work order based on service duration and mechanic efficiency.
 */
export function calculateEndTime(startTime: Date, baseMinutes: number, efficiencyFactor: number): Date {
  const realDuration = Math.ceil(baseMinutes / efficiencyFactor);
  const endTime = new Date(startTime.getTime() + realDuration * 60000);
  return endTime;
}

export function calculateRealDurationMinutes(baseMinutes: number, efficiencyFactor: number): number {
  if (!Number.isFinite(baseMinutes) || baseMinutes <= 0) return 0;
  if (!Number.isFinite(efficiencyFactor) || efficiencyFactor <= 0) return baseMinutes;
  return Math.ceil(baseMinutes / efficiencyFactor);
}

export function minutesSinceMidnight(time: Date): number {
  return time.getHours() * 60 + time.getMinutes();
}

export function isSameBusinessDay(a: Date, b: Date): boolean {
  return (
    a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate()
  );
}

export function validateSchedule(params: {
  mechanic?: Mechanic | null;
  service?: Service | null;
  startTime: Date;
  existingOrders: WorkOrder[];
  openHour: number;
  closeHour: number;
  timeSliceMinutes: number;
  excludeOrderId?: string;
  allowPastStart?: boolean;
  now?: Date;
}): ScheduleValidationResult {
  const {
    mechanic,
    service,
    startTime,
    existingOrders,
    openHour,
    closeHour,
    timeSliceMinutes,
    excludeOrderId,
    allowPastStart = false,
    now = new Date(),
  } = params;
  const errors: string[] = [];

  const realDurationMinutes = service && mechanic
    ? calculateRealDurationMinutes(service.estimatedMinutes, mechanic.efficiencyFactor)
    : 0;
  const endTime = realDurationMinutes > 0
    ? new Date(startTime.getTime() + realDurationMinutes * 60000)
    : new Date(startTime);

  if (!mechanic) errors.push('Seleccione un mecánico válido.');
  if (!service) errors.push('Seleccione un servicio válido.');
  if (!(startTime instanceof Date) || Number.isNaN(startTime.getTime())) {
    errors.push('Seleccione una fecha y hora válidas.');
  }
  if (service && service.estimatedMinutes <= 0) {
    errors.push('La duración del servicio debe ser mayor a cero.');
  }
  if (mechanic && mechanic.efficiencyFactor <= 0) {
    errors.push('La eficiencia del mecánico debe ser mayor a cero.');
  }
  if (openHour >= closeHour) {
    errors.push('El horario de apertura debe ser anterior al cierre.');
  }

  if (errors.length === 0) {
    if (!allowPastStart && startTime.getTime() < now.getTime()) {
      errors.push('No se pueden crear órdenes en fechas u horas pasadas.');
    }

    const startMinutes = minutesSinceMidnight(startTime);
    const endMinutes = minutesSinceMidnight(endTime);
    const openMinutes = openHour * 60;
    const closeMinutes = closeHour * 60;

    if (startMinutes < openMinutes || endMinutes > closeMinutes || !isSameBusinessDay(startTime, endTime)) {
      errors.push('La orden debe iniciar y terminar dentro del horario del taller.');
    }

    if (timeSliceMinutes > 0 && startMinutes % timeSliceMinutes !== 0) {
      errors.push(`La hora debe alinearse a bloques de ${timeSliceMinutes} minutos.`);
    }

    if (mechanic && hasConflict(mechanic.id, startTime, endTime, existingOrders, excludeOrderId)) {
      errors.push('El mecánico ya tiene una orden asignada en ese horario.');
    }
  }

  return { valid: errors.length === 0, errors, endTime, realDurationMinutes };
}

/**
 * Check if a client can cancel a work order.
 * Rules: Can only cancel RECEIVED or DIAGNOSED status orders (not yet started).
 */
export function canClientCancel(order: WorkOrder): boolean {
  return [
    WorkOrderStatus.RECEIVED,
    WorkOrderStatus.DIAGNOSED,
    WorkOrderStatus.WAITING_PARTS,
  ].includes(order.status);
}

const STATUS_TRANSITIONS: Record<WorkOrderStatus, WorkOrderStatus[]> = {
  [WorkOrderStatus.RECEIVED]: [
    WorkOrderStatus.DIAGNOSED,
    WorkOrderStatus.IN_PROGRESS,
    WorkOrderStatus.CANCELLED,
  ],
  [WorkOrderStatus.DIAGNOSED]: [
    WorkOrderStatus.WAITING_PARTS,
    WorkOrderStatus.IN_PROGRESS,
    WorkOrderStatus.CANCELLED,
  ],
  [WorkOrderStatus.WAITING_PARTS]: [
    WorkOrderStatus.IN_PROGRESS,
    WorkOrderStatus.CANCELLED,
  ],
  [WorkOrderStatus.IN_PROGRESS]: [
    WorkOrderStatus.QUALITY_CHECK,
    WorkOrderStatus.COMPLETED,
    WorkOrderStatus.CANCELLED,
  ],
  [WorkOrderStatus.QUALITY_CHECK]: [
    WorkOrderStatus.IN_PROGRESS,
    WorkOrderStatus.COMPLETED,
  ],
  [WorkOrderStatus.COMPLETED]: [
    WorkOrderStatus.DELIVERED,
  ],
  [WorkOrderStatus.DELIVERED]: [],
  [WorkOrderStatus.CANCELLED]: [],
};

export function canTransitionStatus(currentStatus: WorkOrderStatus, nextStatus: WorkOrderStatus): boolean {
  if (currentStatus === nextStatus) return true;
  return STATUS_TRANSITIONS[currentStatus]?.includes(nextStatus) ?? false;
}

/**
 * Generate time slots for the timeline grid.
 */
export function generateTimeSlots(openHour: number, closeHour: number, sliceMinutes: number): Date[] {
  const slots: Date[] = [];
  const today = new Date();
  today.setSeconds(0, 0);

  for (let h = openHour; h < closeHour; h++) {
    for (let m = 0; m < 60; m += sliceMinutes) {
      const slot = new Date(today);
      slot.setHours(h, m, 0, 0);
      slots.push(slot);
    }
  }
  return slots;
}

/**
 * Check for scheduling conflicts.
 */
export function hasConflict(
  mechanicId: string,
  startTime: Date,
  endTime: Date,
  existingOrders: WorkOrder[],
  excludeOrderId?: string
): boolean {
  return existingOrders.some(order => {
    if (order.id === excludeOrderId) return false;
    if (order.mechanicId !== mechanicId) return false;
    if (order.status === WorkOrderStatus.CANCELLED || order.status === WorkOrderStatus.DELIVERED) return false;

    const orderStart = order.startTime.getTime();
    const orderEnd = order.estimatedEndTime.getTime();
    const newStart = startTime.getTime();
    const newEnd = endTime.getTime();

    return newStart < orderEnd && newEnd > orderStart;
  });
}

/**
 * Format duration as human-readable text.
 */
export function formatDuration(minutes: number): string {
  if (minutes < 60) return `${minutes} min`;
  const hours = Math.floor(minutes / 60);
  const remaining = minutes % 60;
  if (remaining === 0) return `${hours}h`;
  return `${hours}h ${remaining}m`;
}

/**
 * Get a status-based color theme.
 */
export function getStatusColor(status: WorkOrderStatus): { bg: string; text: string; border: string } {
  const map: Record<WorkOrderStatus, { bg: string; text: string; border: string }> = {
    [WorkOrderStatus.RECEIVED]:      { bg: 'rgba(59,130,246,0.15)', text: '#60a5fa', border: '#2563eb' },
    [WorkOrderStatus.DIAGNOSED]:     { bg: 'rgba(139,92,246,0.15)', text: '#a78bfa', border: '#7c3aed' },
    [WorkOrderStatus.WAITING_PARTS]: { bg: 'rgba(245,158,11,0.15)', text: '#fbbf24', border: '#d97706' },
    [WorkOrderStatus.IN_PROGRESS]:   { bg: 'rgba(0, 240, 255,0.15)', text: '#00f0ff', border: '#00c2cf' },
    [WorkOrderStatus.QUALITY_CHECK]: { bg: 'rgba(16,185,129,0.15)', text: '#34d399', border: '#059669' },
    [WorkOrderStatus.COMPLETED]:     { bg: 'rgba(34,197,94,0.15)', text: '#4ade80', border: '#16a34a' },
    [WorkOrderStatus.DELIVERED]:     { bg: 'rgba(107,114,128,0.15)', text: '#9ca3af', border: '#6b7280' },
    [WorkOrderStatus.CANCELLED]:     { bg: 'rgba(239,68,68,0.15)', text: '#f87171', border: '#dc2626' },
  };
  return map[status] || map[WorkOrderStatus.RECEIVED];
}

/**
 * Get status label in Spanish.
 */
export function getStatusLabel(status: WorkOrderStatus): string {
  const map: Record<WorkOrderStatus, string> = {
    [WorkOrderStatus.RECEIVED]:      'Recibido',
    [WorkOrderStatus.DIAGNOSED]:     'Diagnosticado',
    [WorkOrderStatus.WAITING_PARTS]: 'Esperando Repuestos',
    [WorkOrderStatus.IN_PROGRESS]:   'En Reparación',
    [WorkOrderStatus.QUALITY_CHECK]: 'Control de Calidad',
    [WorkOrderStatus.COMPLETED]:     'Completado',
    [WorkOrderStatus.DELIVERED]:     'Entregado',
    [WorkOrderStatus.CANCELLED]:     'Cancelado',
  };
  return map[status] || status;
}

/**
 * Get category badge info.
 */
export function getCategoryBadge(category: string): { className: string; label: string } {
  const map: Record<string, { className: string; label: string }> = {
    rep:  { className: 'badge-rep',  label: 'Reparación' },
    cam:  { className: 'badge-cam',  label: 'Cambio' },
    mant: { className: 'badge-mant', label: 'Mantenimiento' },
    diag: { className: 'badge-diag', label: 'Diagnóstico' },
  };
  return map[category] || { className: 'badge-rep', label: '?' };
}
