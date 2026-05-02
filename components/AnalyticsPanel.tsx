
import React, { useMemo } from 'react';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, PieChart, Pie, Cell, AreaChart, Area } from 'recharts';
import { WorkOrder, WorkOrderStatus, Mechanic, Service, ServiceCategory } from '../types';
import { formatDuration, getStatusLabel } from '../services/timeEngine';
import { TrendingUp, Users, Wrench, PieChart as PieChartIcon } from 'lucide-react';

interface AnalyticsPanelProps {
  workOrders: WorkOrder[];
  mechanics: Mechanic[];
  services: Service[];
}

const COLORS = {
  forge: '#00f0ff',
  green: '#4ade80',
  blue: '#60a5fa',
  red: '#f87171',
  purple: '#c084fc',
  cyan: '#22d3ee',
  orange: '#fb923c',
  gray: '#6b7280',
};

const PIE_COLORS = [COLORS.forge, COLORS.green, COLORS.blue, COLORS.red, COLORS.purple, COLORS.cyan, COLORS.orange];

export function AnalyticsPanel({ workOrders, mechanics, services }: AnalyticsPanelProps) {
  // Revenue by day (last 7 days)
  const revenueByDay = useMemo(() => {
    const days: { day: string; revenue: number; orders: number }[] = [];
    for (let i = 6; i >= 0; i--) {
      const d = new Date();
      d.setDate(d.getDate() - i);
      const dayStr = d.toLocaleDateString('es-CR', { weekday: 'short', day: 'numeric' });
      const dayOrders = workOrders.filter(wo =>
        wo.startTime.getFullYear() === d.getFullYear() &&
        wo.startTime.getDate() === d.getDate() &&
        wo.startTime.getMonth() === d.getMonth() &&
        wo.status !== WorkOrderStatus.CANCELLED
      );
      days.push({
        day: dayStr,
        revenue: dayOrders.reduce((sum, wo) => sum + wo.price, 0),
        orders: dayOrders.length,
      });
    }
    return days;
  }, [workOrders]);

  // Mechanic performance
  const mechanicPerformance = useMemo(() => {
    return mechanics.map(mech => {
      const mechOrders = workOrders.filter(wo => wo.mechanicId === mech.id && wo.status !== WorkOrderStatus.CANCELLED);
      const completed = mechOrders.filter(wo => wo.status === WorkOrderStatus.COMPLETED || wo.status === WorkOrderStatus.DELIVERED);
      const revenue = completed.reduce((sum, wo) => sum + wo.price, 0);
      return {
        name: mech.name.split(' ')[0],
        ordenes: mechOrders.length,
        completadas: completed.length,
        revenue,
      };
    });
  }, [workOrders, mechanics]);

  // Service category distribution
  const categoryDistribution = useMemo(() => {
    const counts: Record<string, number> = { rep: 0, cam: 0, mant: 0, diag: 0 };
    workOrders.forEach(wo => {
      const svc = services.find(s => s.id === wo.serviceId);
      if (svc) counts[svc.category] = (counts[svc.category] || 0) + 1;
    });
    return [
      { name: 'Reparación', value: counts.rep, color: COLORS.red },
      { name: 'Cambio', value: counts.cam, color: COLORS.blue },
      { name: 'Mantenimiento', value: counts.mant, color: COLORS.green },
      { name: 'Diagnóstico', value: counts.diag, color: COLORS.purple },
    ].filter(d => d.value > 0);
  }, [workOrders, services]);

  // Status pipeline
  const statusPipeline = useMemo(() => {
    const statuses = [
      WorkOrderStatus.RECEIVED, WorkOrderStatus.DIAGNOSED, WorkOrderStatus.WAITING_PARTS,
      WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.QUALITY_CHECK, WorkOrderStatus.COMPLETED,
      WorkOrderStatus.DELIVERED,
    ];
    return statuses.map(status => ({
      name: getStatusLabel(status).substring(0, 8),
      count: workOrders.filter(wo => wo.status === status).length,
    }));
  }, [workOrders]);

  const totalRevenue = workOrders
    .filter(wo => wo.status !== WorkOrderStatus.CANCELLED)
    .reduce((sum, wo) => sum + wo.price, 0);

  const customTooltip = ({ active, payload, label }: any) => {
    if (!active || !payload?.length) return null;
    return (
      <div className="glass rounded-lg px-3 py-2 shadow-xl border border-white/10">
        <p className="text-xs font-bold text-white">{label}</p>
        {payload.map((entry: any, i: number) => (
          <p key={i} className="text-[10px] font-mono" style={{ color: entry.color }}>
            {entry.name}: {typeof entry.value === 'number' && entry.value > 1000 
              ? `₡${entry.value.toLocaleString()}` 
              : entry.value}
          </p>
        ))}
      </div>
    );
  };

  return (
    <div className="space-y-4 animate-slide-up">
      {/* Revenue Overview */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* Revenue Chart */}
        <div className="glass rounded-xl p-4">
          <div className="flex items-center gap-2 mb-4">
            <TrendingUp size={16} className="text-forge-500" />
            <span className="font-mono text-[10px] tracking-widest text-steel-200 uppercase">
              Ingresos — Últimos 7 Días
            </span>
            <span className="ml-auto font-mono text-sm font-bold text-forge-500">
              ₡{totalRevenue.toLocaleString('es-CR')}
            </span>
          </div>
          <ResponsiveContainer width="100%" height={200}>
            <AreaChart data={revenueByDay}>
              <defs>
                <linearGradient id="revenueGradient" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor={COLORS.forge} stopOpacity={0.3} />
                  <stop offset="95%" stopColor={COLORS.forge} stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
              <XAxis dataKey="day" tick={{ fill: '#888', fontSize: 10, fontFamily: 'IBM Plex Mono' }} axisLine={false} tickLine={false} />
              <YAxis tick={{ fill: '#888', fontSize: 10, fontFamily: 'IBM Plex Mono' }} axisLine={false} tickLine={false} tickFormatter={(v) => `₡${(v/1000).toFixed(0)}k`} />
              <Tooltip content={customTooltip} />
              <Area type="monotone" dataKey="revenue" stroke={COLORS.forge} strokeWidth={2} fill="url(#revenueGradient)" name="Ingresos" />
            </AreaChart>
          </ResponsiveContainer>
        </div>

        {/* Mechanic Performance */}
        <div className="glass rounded-xl p-4">
          <div className="flex items-center gap-2 mb-4">
            <Users size={16} className="text-blue-400" />
            <span className="font-mono text-[10px] tracking-widest text-steel-200 uppercase">
              Rendimiento por Mecánico
            </span>
          </div>
          <ResponsiveContainer width="100%" height={200}>
            <BarChart data={mechanicPerformance} barGap={2}>
              <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
              <XAxis dataKey="name" tick={{ fill: '#888', fontSize: 10, fontFamily: 'IBM Plex Mono' }} axisLine={false} tickLine={false} />
              <YAxis tick={{ fill: '#888', fontSize: 10, fontFamily: 'IBM Plex Mono' }} axisLine={false} tickLine={false} />
              <Tooltip content={customTooltip} />
              <Bar dataKey="ordenes" fill={COLORS.forge} name="Órdenes" radius={[4, 4, 0, 0]} />
              <Bar dataKey="completadas" fill={COLORS.green} name="Completadas" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>

      {/* Second Row */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* Category Distribution */}
        <div className="glass rounded-xl p-4">
          <div className="flex items-center gap-2 mb-4">
            <PieChartIcon size={16} className="text-purple-400" />
            <span className="font-mono text-[10px] tracking-widest text-steel-200 uppercase">
              Distribución por Tipo de Servicio
            </span>
          </div>
          <div className="flex items-center gap-4">
            <ResponsiveContainer width="50%" height={160}>
              <PieChart>
                <Pie
                  data={categoryDistribution}
                  cx="50%"
                  cy="50%"
                  innerRadius={35}
                  outerRadius={65}
                  paddingAngle={3}
                  dataKey="value"
                  strokeWidth={0}
                >
                  {categoryDistribution.map((entry, i) => (
                    <Cell key={i} fill={entry.color} />
                  ))}
                </Pie>
                <Tooltip content={customTooltip} />
              </PieChart>
            </ResponsiveContainer>
            <div className="flex-1 space-y-2">
              {categoryDistribution.map((entry, i) => (
                <div key={i} className="flex items-center gap-2">
                  <div className="w-3 h-3 rounded-sm flex-shrink-0" style={{ background: entry.color }} />
                  <span className="text-xs text-steel-200 flex-1">{entry.name}</span>
                  <span className="font-mono text-xs font-bold text-white">{entry.value}</span>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Status Pipeline */}
        <div className="glass rounded-xl p-4">
          <div className="flex items-center gap-2 mb-4">
            <Wrench size={16} className="text-green-400" />
            <span className="font-mono text-[10px] tracking-widest text-steel-200 uppercase">
              Pipeline de Estados
            </span>
          </div>
          <ResponsiveContainer width="100%" height={160}>
            <BarChart data={statusPipeline} layout="vertical" barSize={16}>
              <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" horizontal={false} />
              <XAxis type="number" tick={{ fill: '#888', fontSize: 10, fontFamily: 'IBM Plex Mono' }} axisLine={false} tickLine={false} />
              <YAxis type="category" dataKey="name" tick={{ fill: '#888', fontSize: 9, fontFamily: 'IBM Plex Mono' }} axisLine={false} tickLine={false} width={70} />
              <Tooltip content={customTooltip} />
              <Bar dataKey="count" name="Cantidad" radius={[0, 4, 4, 0]}>
                {statusPipeline.map((_, i) => (
                  <Cell key={i} fill={PIE_COLORS[i % PIE_COLORS.length]} />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>
    </div>
  );
}
