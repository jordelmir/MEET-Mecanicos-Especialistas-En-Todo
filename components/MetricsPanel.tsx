
import React, { useMemo } from 'react';
import { Metrics, WorkOrder, WorkOrderStatus, Service } from '../types';
import { getStatusLabel, getStatusColor } from '../services/timeEngine';
import { Activity, DollarSign, Clock, CheckCircle2, TrendingUp, Gauge, Wrench, AlertTriangle, Zap } from 'lucide-react';

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
      icon: <Gauge size={22} />,
      label: 'Ocupación',
      value: `${metrics.dailyOccupancy}%`,
      sub: 'Capacidad del taller',
      color: metrics.dailyOccupancy > 80 ? '#39ff14' : metrics.dailyOccupancy > 50 ? '#00f0ff' : '#ff3344',
      hoverClass: metrics.dailyOccupancy > 80 ? 'stat-card-green' : '',
      ring: true,
    },
    {
      icon: <DollarSign size={22} />,
      label: 'Facturación',
      value: `₡${metrics.revenue.toLocaleString('es-CR')}`,
      sub: 'Ingresos del día',
      color: '#39ff14',
      hoverClass: 'stat-card-green',
    },
    {
      icon: <CheckCircle2 size={22} />,
      label: 'Completadas',
      value: `${metrics.ordersCompleted} / ${metrics.ordersTotal}`,
      sub: 'Órdenes de trabajo',
      color: '#00f0ff',
      hoverClass: '',
    },
    {
      icon: <Clock size={22} />,
      label: 'Tiempo Muerto',
      value: `${Math.floor(metrics.idleTimeMinutes / 60)}h ${metrics.idleTimeMinutes % 60}m`,
      sub: 'Sin asignar hoy',
      color: metrics.idleTimeMinutes > 120 ? '#ff3344' : '#4d8dff',
      hoverClass: metrics.idleTimeMinutes > 120 ? 'stat-card-red' : '',
    },
  ];

  return (
    <div className="space-y-4">
      {/* Primary KPI Cards — 3D Neon */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 stagger-children">
        {cards.map((card, i) => (
          <div
            key={i}
            className={`stat-card ${card.hoverClass} group cursor-default`}
          >
            {/* Top accent line with card color */}
            <div className="absolute top-0 left-4 right-4 h-px" style={{
              background: `linear-gradient(90deg, transparent, ${card.color}50, transparent)`
            }} />

            {/* Icon + Label */}
            <div className="relative z-10 flex items-center gap-2.5 mb-4">
              <div className="p-2 rounded-lg transition-all duration-300 group-hover:scale-110" style={{
                color: card.color,
                background: `${card.color}12`,
                boxShadow: `0 0 15px ${card.color}15`,
                filter: `drop-shadow(0 0 6px ${card.color}40)`,
              }}>
                {card.icon}
              </div>
              <span className="font-mono text-[10px] tracking-[3px] text-gray-400 uppercase font-bold">
                {card.label}
              </span>
            </div>

            {/* Value */}
            <div className="relative z-10 font-display text-3xl font-black tracking-wider transition-all duration-300" style={{
              color: card.color,
              textShadow: `0 0 15px ${card.color}40, 0 0 30px ${card.color}15`,
            }}>
              {card.value}
            </div>

            {/* Sub label */}
            <div className="relative z-10 font-mono text-[10px] text-gray-500 mt-2 tracking-wide">{card.sub}</div>

            {/* Bottom glow line */}
            <div className="absolute bottom-0 left-0 right-0 h-px" style={{
              background: `linear-gradient(90deg, transparent, ${card.color}20, transparent)`,
            }} />
          </div>
        ))}
      </div>

      {/* Status Distribution */}
      {statusBreakdown.length > 0 && (
        <div className="stat-card cyber-scan-line">
          <div className="relative z-10 flex items-center gap-2.5 mb-4">
            <div className="p-1.5 rounded-lg" style={{
              color: '#00f0ff',
              background: 'rgba(0,240,255,0.08)',
              boxShadow: '0 0 12px rgba(0,240,255,0.1)',
            }}>
              <Activity size={16} />
            </div>
            <span className="font-mono text-[10px] tracking-[3px] text-gray-400 uppercase font-bold">
              Distribución de Estados — Hoy
            </span>
          </div>
          <div className="relative z-10 flex flex-wrap gap-2.5">
            {statusBreakdown.map(({ status, count, label, bg, text, border }) => (
              <div
                key={status}
                className="flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-mono font-bold transition-all duration-300 hover:scale-105 hover:shadow-lg cursor-default"
                style={{
                  background: bg,
                  color: text,
                  border: `1px solid ${border}`,
                  boxShadow: `0 0 10px ${bg}`,
                }}
              >
                <span className="text-sm font-black">{count}</span>
                <span className="tracking-wide">{label}</span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
