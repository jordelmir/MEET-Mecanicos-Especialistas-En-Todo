
import React, { useMemo } from 'react';
import { Metrics, WorkOrder, WorkOrderStatus, Service } from '../types';
import { getStatusLabel, getStatusColor } from '../services/timeEngine';
import { Activity, DollarSign, Clock, CheckCircle2, TrendingUp, Gauge, Wrench, AlertTriangle } from 'lucide-react';

interface MetricsPanelProps {
  metrics: Metrics;
  workOrders: WorkOrder[];
  currentDate: Date;
  services: Service[];
  openHour: number;
  closeHour: number;
}

export function MetricsPanel({ metrics, workOrders, currentDate, services, openHour, closeHour }: MetricsPanelProps) {
  
  const todaysOrders = useMemo(() => {
    return workOrders.filter(wo =>
      wo.startTime.getDate() === currentDate.getDate() &&
      wo.startTime.getMonth() === currentDate.getMonth() &&
      wo.status !== WorkOrderStatus.CANCELLED
    );
  }, [workOrders, currentDate]);

  const statusBreakdown = useMemo(() => {
    const counts: Record<string, number> = {};
    todaysOrders.forEach(wo => {
      counts[wo.status] = (counts[wo.status] || 0) + 1;
    });
    return Object.entries(counts).map(([status, count]) => ({
      status: status as WorkOrderStatus,
      count,
      ...getStatusColor(status as WorkOrderStatus),
      label: getStatusLabel(status as WorkOrderStatus),
    }));
  }, [todaysOrders]);

  const cards = [
    {
      icon: <Gauge size={20} />,
      label: 'Ocupación',
      value: `${metrics.dailyOccupancy}%`,
      sub: 'Capacidad del taller',
      color: metrics.dailyOccupancy > 80 ? 'text-green-400' : metrics.dailyOccupancy > 50 ? 'text-forge-500' : 'text-red-400',
      ring: true,
    },
    {
      icon: <DollarSign size={20} />,
      label: 'Facturación',
      value: `₡${metrics.revenue.toLocaleString('es-CR')}`,
      sub: 'Ingresos del día',
      color: 'text-green-400',
    },
    {
      icon: <CheckCircle2 size={20} />,
      label: 'Completadas',
      value: `${metrics.ordersCompleted} / ${metrics.ordersTotal}`,
      sub: 'Órdenes de trabajo',
      color: 'text-forge-500',
    },
    {
      icon: <Clock size={20} />,
      label: 'Tiempo Muerto',
      value: `${Math.floor(metrics.idleTimeMinutes / 60)}h ${metrics.idleTimeMinutes % 60}m`,
      sub: 'Sin asignar hoy',
      color: metrics.idleTimeMinutes > 120 ? 'text-red-400' : 'text-blue-400',
    },
  ];

  return (
    <div className="space-y-4 animate-slide-up">
      {/* Primary KPI Cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        {cards.map((card, i) => (
          <div
            key={i}
            className="glass rounded-xl p-4 hover:border-forge-500/20 transition-all group"
          >
            <div className="flex items-center gap-2 mb-3">
              <div className={`${card.color} opacity-60 group-hover:opacity-100 transition-opacity`}>
                {card.icon}
              </div>
              <span className="font-mono text-[10px] tracking-widest text-steel-200 uppercase">
                {card.label}
              </span>
            </div>
            <div className={`font-bold text-2xl ${card.color} font-mono`}>
              {card.value}
            </div>
            <div className="font-mono text-[10px] text-steel-300 mt-1">{card.sub}</div>
          </div>
        ))}
      </div>

      {/* Status Distribution */}
      {statusBreakdown.length > 0 && (
        <div className="glass rounded-xl p-4">
          <div className="flex items-center gap-2 mb-3">
            <Activity size={16} className="text-forge-500" />
            <span className="font-mono text-[10px] tracking-widest text-steel-200 uppercase">
              Distribución de Estados — Hoy
            </span>
          </div>
          <div className="flex flex-wrap gap-2">
            {statusBreakdown.map(({ status, count, label, bg, text, border }) => (
              <div
                key={status}
                className="flex items-center gap-2 px-3 py-1.5 rounded-lg text-xs font-mono font-medium"
                style={{ background: bg, color: text, border: `1px solid ${border}` }}
              >
                <span className="font-bold">{count}</span>
                <span>{label}</span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
