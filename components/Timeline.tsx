
import React, { useMemo, useState } from 'react';
import { WorkOrder, WorkOrderStatus, Mechanic, Service } from '../types';
import { getStatusColor, getStatusLabel, formatDuration } from '../services/timeEngine';
import { ChevronLeft, ChevronRight, Clock, Car, User, Wrench } from 'lucide-react';

interface TimelineProps {
  mechanics: Mechanic[];
  workOrders: WorkOrder[];
  services: Service[];
  currentDate: Date;
  openHour: number;
  closeHour: number;
  timeSliceMinutes: number;
  onStatusChange: (id: string, status: WorkOrderStatus) => void;
  onDateChange: (date: Date) => void;
  onEditWorkOrder: (order: WorkOrder) => void;
}

export function Timeline({
  mechanics,
  workOrders,
  services,
  currentDate,
  openHour,
  closeHour,
  timeSliceMinutes,
  onStatusChange,
  onDateChange,
  onEditWorkOrder,
}: TimelineProps) {
  const [hoveredOrder, setHoveredOrder] = useState<string | null>(null);

  const timeSlots = useMemo(() => {
    const slots: string[] = [];
    for (let h = openHour; h < closeHour; h++) {
      for (let m = 0; m < 60; m += timeSliceMinutes) {
        slots.push(`${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`);
      }
    }
    return slots;
  }, [openHour, closeHour, timeSliceMinutes]);

  const todaysOrders = useMemo(() => {
    return workOrders.filter(wo =>
      wo.startTime.getFullYear() === currentDate.getFullYear() &&
      wo.startTime.getDate() === currentDate.getDate() &&
      wo.startTime.getMonth() === currentDate.getMonth()
    );
  }, [workOrders, currentDate]);

  const navigateDate = (delta: number) => {
    const newDate = new Date(currentDate);
    newDate.setDate(newDate.getDate() + delta);
    onDateChange(newDate);
  };

  const isToday = currentDate.toDateString() === new Date().toDateString();

  // Calculate pixel width per minute for positioning
  const totalMinutes = (closeHour - openHour) * 60;
  const colWidth = 100 / timeSlots.length;

  return (
    <div className="p-4">
      {/* Date Navigation */}
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-3">
          <button onClick={() => navigateDate(-1)} className="p-2 rounded-lg glass-inner glass-hover transition-all text-steel-200 hover:text-white">
            <ChevronLeft size={16} />
          </button>
          <div className="text-center">
            <div className="font-bold text-white text-sm">
              {currentDate.toLocaleDateString('es-CR', { weekday: 'long', day: 'numeric', month: 'long' })}
            </div>
            {isToday && <span className="font-mono text-[10px] text-forge-500 tracking-wider">HOY</span>}
          </div>
          <button onClick={() => navigateDate(1)} className="p-2 rounded-lg glass-inner glass-hover transition-all text-steel-200 hover:text-white">
            <ChevronRight size={16} />
          </button>
          {!isToday && (
            <button
              onClick={() => onDateChange(new Date())}
              className="ml-2 px-3 py-1 rounded-full font-mono text-[10px] bg-forge-500/10 text-forge-500 border border-forge-500/20 hover:bg-forge-500/20 transition-all"
            >
              Ir a Hoy
            </button>
          )}
        </div>
        <div className="font-mono text-[10px] text-steel-300">
          {todaysOrders.filter(o => o.status !== WorkOrderStatus.CANCELLED).length} órdenes activas
        </div>
      </div>

      {/* Timeline Grid */}
      <div className="overflow-x-auto" style={{ scrollbarWidth: 'thin' }}>
        <div className="min-w-[800px]">
          {/* Time Header */}
          <div className="flex border-b border-steel-500/50 mb-1">
            <div className="w-44 flex-shrink-0 px-3 py-2 font-mono text-[10px] text-steel-300 uppercase tracking-wider">
              Mecánico
            </div>
            <div className="flex-1 flex">
              {timeSlots.map((slot, i) => (
                <div
                  key={i}
                  className="flex-1 text-center font-mono text-[10px] text-steel-300 py-2 border-l border-steel-600/30"
                >
                  {slot}
                </div>
              ))}
            </div>
          </div>

          {/* Mechanic Rows */}
          {mechanics.map(mech => {
            const mechOrders = todaysOrders.filter(wo => wo.mechanicId === mech.id && wo.status !== WorkOrderStatus.CANCELLED);

            return (
              <div key={mech.id} className="flex border-b border-steel-600/20 group hover:bg-white/[0.01] transition-colors">
                {/* Mechanic Info */}
                <div className="w-44 flex-shrink-0 px-3 py-3 flex items-center gap-2">
                  <div className="w-8 h-8 rounded-full overflow-hidden border border-steel-500 flex-shrink-0">
                    <img src={mech.avatar} alt={mech.name} className="w-full h-full object-cover" />
                  </div>
                  <div className="min-w-0">
                    <div className="text-xs font-bold text-white truncate">{mech.name.split(' ')[0]}</div>
                    <div className="font-mono text-[9px] text-forge-500 uppercase">{mech.specialty}</div>
                  </div>
                </div>

                {/* Schedule Area */}
                <div className="flex-1 relative" style={{ minHeight: '52px' }}>
                  {/* Grid lines */}
                  <div className="absolute inset-0 flex">
                    {timeSlots.map((_, i) => (
                      <div key={i} className="flex-1 border-l border-steel-600/15" />
                    ))}
                  </div>

                  {/* Current time indicator */}
                  {isToday && (() => {
                    const now = new Date();
                    const nowMins = now.getHours() * 60 + now.getMinutes();
                    const startMins = openHour * 60;
                    const pct = ((nowMins - startMins) / totalMinutes) * 100;
                    if (pct >= 0 && pct <= 100) {
                      return (
                        <div
                          className="absolute top-0 bottom-0 w-0.5 bg-red-500/60 z-20"
                          style={{ left: `${pct}%` }}
                        >
                          <div className="absolute -top-1 -left-1 w-2 h-2 rounded-full bg-red-500" />
                        </div>
                      );
                    }
                    return null;
                  })()}

                  {/* Work Order Blocks */}
                  {mechOrders.map(order => {
                    const orderStartMins = order.startTime.getHours() * 60 + order.startTime.getMinutes();
                    const orderEndMins = order.estimatedEndTime.getHours() * 60 + order.estimatedEndTime.getMinutes();
                    const startPct = ((orderStartMins - openHour * 60) / totalMinutes) * 100;
                    const widthPct = ((orderEndMins - orderStartMins) / totalMinutes) * 100;
                    const statusColor = getStatusColor(order.status);
                    const service = services.find(s => s.id === order.serviceId);

                    return (
                      <div
                        key={order.id}
                        className="absolute top-1 bottom-1 rounded-md cursor-pointer z-10 transition-all hover:z-30 hover:shadow-lg overflow-hidden group/block"
                        style={{
                          left: `${Math.max(0, startPct)}%`,
                          width: `${Math.min(widthPct, 100 - startPct)}%`,
                          background: statusColor.bg,
                          border: `1px solid ${statusColor.border}`,
                          minWidth: '40px',
                        }}
                        onClick={() => onEditWorkOrder(order)}
                        onMouseEnter={() => setHoveredOrder(order.id)}
                        onMouseLeave={() => setHoveredOrder(null)}
                        title={`${order.clientName} — ${service?.name || 'Servicio'} | ${getStatusLabel(order.status)}`}
                      >
                        <div className="px-2 py-1 h-full flex flex-col justify-center overflow-hidden">
                          <div className="flex items-center gap-1">
                            <Car size={10} style={{ color: statusColor.text }} className="flex-shrink-0" />
                            <span
                              className="text-[10px] font-bold truncate"
                              style={{ color: statusColor.text }}
                            >
                              {order.vehicleInfo.plate}
                            </span>
                          </div>
                          <div className="text-[9px] truncate opacity-70" style={{ color: statusColor.text }}>
                            {order.clientName.split(' ')[0]} · {service?.name.substring(0, 20) || ''}
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}