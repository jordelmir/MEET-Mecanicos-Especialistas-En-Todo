
import React, { useMemo, useState } from 'react';
import { WorkOrder, WorkOrderStatus, Mechanic, Service } from '../types';
import { getStatusColor, getStatusLabel, formatDuration } from '../services/timeEngine';
import { Car, Clock, User, ArrowRight, CheckCircle, AlertTriangle, Wrench, Play, PauseCircle, CheckCircle2, Package } from 'lucide-react';

interface MechanicDashboardProps {
  mechanicId: string;
  currentMechanic?: Mechanic;
  mechanics: Mechanic[];
  workOrders: WorkOrder[];
  services: Service[];
  onStatusChange: (id: string, status: WorkOrderStatus) => void;
  onUpdateMechanic: (mech: Mechanic) => void;
  openHour: number;
  closeHour: number;
}

const STATUS_FLOW: WorkOrderStatus[] = [
  WorkOrderStatus.RECEIVED,
  WorkOrderStatus.DIAGNOSED,
  WorkOrderStatus.WAITING_PARTS,
  WorkOrderStatus.IN_PROGRESS,
  WorkOrderStatus.QUALITY_CHECK,
  WorkOrderStatus.COMPLETED,
  WorkOrderStatus.DELIVERED,
];

function getNextStatus(current: WorkOrderStatus): WorkOrderStatus | null {
  const idx = STATUS_FLOW.indexOf(current);
  if (idx === -1 || idx >= STATUS_FLOW.length - 1) return null;
  return STATUS_FLOW[idx + 1];
}

function getStatusIcon(status: WorkOrderStatus) {
  switch (status) {
    case WorkOrderStatus.RECEIVED: return <Car size={14} />;
    case WorkOrderStatus.DIAGNOSED: return <AlertTriangle size={14} />;
    case WorkOrderStatus.WAITING_PARTS: return <Package size={14} />;
    case WorkOrderStatus.IN_PROGRESS: return <Wrench size={14} />;
    case WorkOrderStatus.QUALITY_CHECK: return <CheckCircle size={14} />;
    case WorkOrderStatus.COMPLETED: return <CheckCircle2 size={14} />;
    case WorkOrderStatus.DELIVERED: return <CheckCircle2 size={14} />;
    default: return <Clock size={14} />;
  }
}

export function MechanicDashboard({
  mechanicId,
  currentMechanic,
  mechanics,
  workOrders,
  services,
  onStatusChange,
  onUpdateMechanic,
  openHour,
  closeHour,
}: MechanicDashboardProps) {
  const [selectedOrder, setSelectedOrder] = useState<string | null>(null);
  const [ticker, setTicker] = useState(0);

  // Live timer tick every minute for active orders
  React.useEffect(() => {
    const interval = setInterval(() => setTicker(t => t + 1), 60000);
    return () => clearInterval(interval);
  }, []);

  const myOrders = useMemo(() => {
    const today = new Date();
    return workOrders
      .filter(wo =>
        wo.mechanicId === mechanicId &&
        wo.startTime.getDate() === today.getDate() &&
        wo.status !== WorkOrderStatus.CANCELLED &&
        wo.status !== WorkOrderStatus.DELIVERED
      )
      .sort((a, b) => a.startTime.getTime() - b.startTime.getTime());
  }, [workOrders, mechanicId]);

  const completedToday = useMemo(() => {
    const today = new Date();
    return workOrders.filter(wo =>
      wo.mechanicId === mechanicId &&
      wo.startTime.getDate() === today.getDate() &&
      (wo.status === WorkOrderStatus.COMPLETED || wo.status === WorkOrderStatus.DELIVERED)
    ).length;
  }, [workOrders, mechanicId]);

  const currentActive = myOrders.find(o => o.status === WorkOrderStatus.IN_PROGRESS);

  return (
    <div className="space-y-4 animate-slide-up">
      {/* Mechanic Summary */}
      {currentMechanic && (
        <div className="glass rounded-xl p-4 flex items-center gap-4">
          <div className="w-14 h-14 rounded-xl overflow-hidden border-2 border-forge-500/30 flex-shrink-0">
            <img src={currentMechanic.avatar} alt={currentMechanic.name} className="w-full h-full object-cover" />
          </div>
          <div className="flex-1 min-w-0">
            <div className="font-bold text-white text-lg">{currentMechanic.name}</div>
            <div className="font-mono text-[10px] text-forge-500 uppercase tracking-wider">
              {currentMechanic.specialty} · Eficiencia: {Math.round(currentMechanic.efficiencyFactor * 100)}%
            </div>
          </div>
          <div className="text-right hidden sm:block">
            <div className="font-mono text-2xl font-bold text-forge-500">{myOrders.length}</div>
            <div className="font-mono text-[10px] text-steel-300">Pendientes</div>
          </div>
          <div className="text-right hidden sm:block">
            <div className="font-mono text-2xl font-bold text-green-400">{completedToday}</div>
            <div className="font-mono text-[10px] text-steel-300">Completadas</div>
          </div>
        </div>
      )}

      {/* Active Work Order (Hero) */}
      {currentActive && (() => {
        const svc = services.find(s => s.id === currentActive.serviceId);
        const colors = getStatusColor(currentActive.status);
        const elapsed = currentActive.actualStartTime
          ? Math.round((Date.now() - currentActive.actualStartTime.getTime()) / 60000)
          : 0;

        return (
          <div className="glass rounded-xl p-5 border-l-4" style={{ borderLeftColor: colors.border }}>
            <div className="flex items-center gap-2 mb-3">
              <div className="animate-pulse">
                <Wrench size={18} style={{ color: colors.text }} />
              </div>
              <span className="font-mono text-xs tracking-wider uppercase font-bold" style={{ color: colors.text }}>
                Trabajo Activo — {formatDuration(elapsed)} transcurridos
              </span>
            </div>
            <div className="flex items-start gap-4">
              <div className="flex-1">
                <h3 className="text-lg font-bold text-white">{svc?.name || 'Servicio'}</h3>
                <div className="flex items-center gap-3 mt-2 text-sm text-steel-200">
                  <span className="flex items-center gap-1"><User size={12} />{currentActive.clientName}</span>
                  <span className="flex items-center gap-1"><Car size={12} />{currentActive.vehicleInfo.plate} — {currentActive.vehicleInfo.brand} {currentActive.vehicleInfo.model}</span>
                </div>
                {currentActive.notes && (
                  <p className="text-xs text-steel-300 mt-2 italic">"{currentActive.notes}"</p>
                )}
              </div>
              <div className="flex gap-2">
                {getNextStatus(currentActive.status) && (
                  <button
                    onClick={() => onStatusChange(currentActive.id, getNextStatus(currentActive.status)!)}
                    className="flex items-center gap-2 px-4 py-2 rounded-lg bg-forge-500 text-black font-bold font-mono text-xs hover:bg-forge-400 transition-all forge-glow"
                  >
                    <ArrowRight size={14} />
                    {getStatusLabel(getNextStatus(currentActive.status)!)}
                  </button>
                )}
              </div>
            </div>
          </div>
        );
      })()}

      {/* Queue */}
      <div className="space-y-2">
        <h3 className="font-mono text-[10px] text-steel-300 uppercase tracking-wider px-1">
          Cola de Trabajo ({myOrders.filter(o => o.status !== WorkOrderStatus.IN_PROGRESS).length})
        </h3>
        {myOrders.filter(o => o.status !== WorkOrderStatus.IN_PROGRESS).length === 0 && (
          <div className="glass-inner rounded-xl p-8 text-center">
            <CheckCircle2 size={32} className="text-green-400 mx-auto mb-2 opacity-50" />
            <p className="text-sm text-steel-300">Sin trabajos pendientes</p>
          </div>
        )}
        {myOrders.filter(o => o.status !== WorkOrderStatus.IN_PROGRESS).map(order => {
          const svc = services.find(s => s.id === order.serviceId);
          const colors = getStatusColor(order.status);
          const next = getNextStatus(order.status);

          return (
            <div
              key={order.id}
              className="glass-inner rounded-xl p-4 flex items-center gap-3 hover:bg-white/[0.03] transition-all cursor-pointer group"
              onClick={() => setSelectedOrder(selectedOrder === order.id ? null : order.id)}
            >
              <div className="w-10 h-10 rounded-lg flex items-center justify-center flex-shrink-0"
                style={{ background: colors.bg, border: `1px solid ${colors.border}` }}>
                <span style={{ color: colors.text }}>{getStatusIcon(order.status)}</span>
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-bold text-white truncate">{svc?.name || 'Servicio'}</span>
                  <span className="font-mono text-[9px] px-2 py-0.5 rounded-full" style={{ background: colors.bg, color: colors.text, border: `1px solid ${colors.border}` }}>
                    {getStatusLabel(order.status)}
                  </span>
                </div>
                <div className="flex items-center gap-3 mt-1 text-xs text-steel-300">
                  <span>{order.clientName}</span>
                  <span className="text-forge-500">{order.vehicleInfo.plate}</span>
                  <span>{order.startTime.toLocaleTimeString('es-CR', { hour: '2-digit', minute: '2-digit' })}</span>
                </div>
              </div>
              <div className="text-right hidden sm:block">
                <div className="font-mono text-sm font-bold text-forge-500">
                  ₡{order.price.toLocaleString('es-CR')}
                </div>
                <div className="font-mono text-[10px] text-steel-300">{formatDuration(order.estimatedMinutes)}</div>
              </div>
              {next && (
                <button
                  onClick={(e) => { e.stopPropagation(); onStatusChange(order.id, next); }}
                  className="px-3 py-1.5 rounded-lg text-[10px] font-mono font-bold transition-all opacity-0 group-hover:opacity-100"
                  style={{ background: colors.bg, color: colors.text, border: `1px solid ${colors.border}` }}
                >
                  → {getStatusLabel(next)}
                </button>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
