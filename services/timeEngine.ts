
import { WorkOrder, WorkOrderStatus } from '../types';

/**
 * Calculate the estimated end time for a work order based on service duration and mechanic efficiency.
 */
export function calculateEndTime(startTime: Date, baseMinutes: number, efficiencyFactor: number): Date {
  const realDuration = Math.ceil(baseMinutes / efficiencyFactor);
  const endTime = new Date(startTime.getTime() + realDuration * 60000);
  return endTime;
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
