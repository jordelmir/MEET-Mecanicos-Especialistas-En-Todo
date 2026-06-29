
import React, { useMemo, useState } from 'react';
import { WorkOrder, WorkOrderStatus, Mechanic, Service } from '../types';
import { getStatusColor, getStatusLabel, formatDuration } from '../services/timeEngine';
import { ChevronLeft, ChevronRight, Clock, Car, User, Wrench, Calendar, Hammer } from 'lucide-react';

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

  return (
    <div className="p-5">
      {/* Date Navigation HUD */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between mb-6 pb-4 border-b border-white/5 gap-4">
        <div className="flex items-center gap-4">
          <div className="flex items-center gap-1.5 p-1 rounded-xl" style={{ background: 'rgba(0,10,20,0.5)', border: '1px solid rgba(0,240,255,0.15)' }}>
            <button onClick={() => navigateDate(-1)} className="p-2 rounded-lg text-gray-400 hover:text-white hover:bg-white/5 transition-all">
              <ChevronLeft size={16} />
            </button>
            <div className="px-4 text-center min-w-[180px]">
              <div className="font-display font-extrabold text-white text-xs tracking-wider uppercase">
                {currentDate.toLocaleDateString('es-CR', { weekday: 'short', day: 'numeric', month: 'short' })}
              </div>
              {isToday ? (
                <span className="font-mono text-[9px] text-forge-500 tracking-[3px] font-bold block mt-0.5 animate-pulse">SYSTEM LIVE</span>
              ) : (
                <span className="font-mono text-[9px] text-gray-500 tracking-[3px] font-bold block mt-0.5">HISTORIAL</span>
              )}
            </div>
            <button onClick={() => navigateDate(1)} className="p-2 rounded-lg text-gray-400 hover:text-white hover:bg-white/5 transition-all">
              <ChevronRight size={16} />
            </button>
          </div>

          {!isToday && (
            <button
              onClick={() => onDateChange(new Date())}
              className="px-4 py-2 rounded-xl font-mono text-[10px] font-bold tracking-wider transition-all duration-300 transform active:scale-95"
              style={{
                color: '#00f0ff',
                background: 'rgba(0,240,255,0.08)',
                border: '1px solid rgba(0,240,255,0.25)',
                boxShadow: '0 0 15px rgba(0,240,255,0.1)'
              }}
            >
              RETORNAR A HOY
            </button>
          )}
        </div>

        <div className="flex items-center gap-2">
          <div className="p-1.5 rounded-lg text-forge-500" style={{ background: 'rgba(0,240,255,0.08)' }}>
            <Calendar size={14} />
          </div>
          <span className="font-mono text-[10px] text-gray-400 tracking-[2px] uppercase">
            {todaysOrders.filter(o => o.status !== WorkOrderStatus.CANCELLED).length} Órdenes Programadas
          </span>
        </div>
      </div>

      {/* Timeline Grid Wrapper */}
      <div className="overflow-x-auto rounded-xl border border-white/5" style={{ scrollbarWidth: 'thin', background: 'rgba(3,7,12,0.3)' }}>
        <div className="min-w-[900px] table-neon">
          
          {/* Time Header with carbon fiber style */}
          <div className="flex items-stretch" style={{ background: 'linear-gradient(180deg, rgba(10,20,35,0.85) 0%, rgba(5,10,20,0.9) 100%)', borderBottom: '1px solid rgba(0,240,255,0.15)' }}>
            <div className="w-48 flex-shrink-0 px-4 py-3 font-mono text-[10px] text-gray-400 uppercase tracking-[3px] font-black flex items-center gap-2" style={{ borderRight: '1px solid rgba(255,255,255,0.05)' }}>
              <Hammer size={12} className="text-forge-500" />
              Mecánico
            </div>
            <div className="flex-1 flex">
              {timeSlots.map((slot, i) => (
                <div
                  key={i}
                  className="flex-1 text-center font-mono text-[10px] text-gray-400 py-3 border-l border-white/5 font-bold tracking-wider relative group"
                >
                  <div className="absolute inset-0 bg-white/[0.01] opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none" />
                  {slot}
                </div>
              ))}
            </div>
          </div>

          {/* Mechanic Rows */}
          {mechanics.map(mech => {
            const mechOrders = todaysOrders.filter(wo => wo.mechanicId === mech.id && wo.status !== WorkOrderStatus.CANCELLED);

            return (
              <div key={mech.id} className="flex border-b border-white/5 group transition-colors" style={{ background: 'rgba(5,15,30,0.15)' }}>
                
                {/* Mechanic Identity Column */}
                <div className="w-48 flex-shrink-0 px-4 py-4 flex items-center gap-3 relative" style={{ borderRight: '1px solid rgba(255,255,255,0.05)' }}>
                  {/* Neon active indicator bar */}
                  <div className="absolute left-0 top-0 bottom-0 w-0.5 bg-transparent group-hover:bg-forge-500 shadow-[0_0_10px_rgba(0,240,255,0.8)] transition-all" />
                  
                  <div className="relative w-9 h-9 rounded-xl overflow-hidden border border-white/10 flex-shrink-0 group-hover:border-forge-500/50 transition-colors shadow-md">
                    <img src={mech.avatar} alt={mech.name} className="w-full h-full object-cover" />
                  </div>
                  <div className="min-w-0">
                    <div className="text-xs font-extrabold text-white group-hover:text-forge-300 transition-colors truncate">{mech.name}</div>
                    <div className="font-mono text-[8px] text-forge-500 uppercase tracking-widest font-bold mt-0.5">{mech.specialty}</div>
                  </div>
                </div>

                {/* Interactive Grid Lanes */}
                <div className="flex-1 relative" style={{ minHeight: '64px' }}>
                  {/* Subtly lit vertical grid lanes */}
                  <div className="absolute inset-0 flex pointer-events-none">
                    {timeSlots.map((_, i) => (
                      <div key={i} className="flex-1 border-l border-white/5" />
                    ))}
                  </div>

                  {/* Realtime Time indicator */}
                  {isToday && (() => {
                    const now = new Date();
                    const nowMins = now.getHours() * 60 + now.getMinutes();
                    const startMins = openHour * 60;
                    const pct = ((nowMins - startMins) / totalMinutes) * 100;
                    if (pct >= 0 && pct <= 100) {
                      return (
                        <div
                          className="absolute top-0 bottom-0 w-px z-20 pointer-events-none"
                          style={{
                            left: `${pct}%`,
                            background: 'linear-gradient(180deg, #ff3344 0%, rgba(255,51,68,0.3) 100%)',
                            boxShadow: '0 0 10px rgba(255,51,68,0.8)'
                          }}
                        >
                          <div className="absolute -top-0.5 -left-1 w-2.5 h-2.5 rounded-full bg-red-500 shadow-[0_0_10px_#ff3344] animate-ping" />
                          <div className="absolute -top-0.5 -left-1 w-2.5 h-2.5 rounded-full bg-red-500 shadow-[0_0_6px_#ff3344]" />
                        </div>
                      );
                    }
                    return null;
                  })()}

                  {/* High-tech Work Order blocks */}
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
                        className="absolute top-2 bottom-2 rounded-xl cursor-pointer z-10 transition-all duration-300 hover:z-30 hover:scale-[1.02] overflow-hidden flex flex-col justify-center px-3"
                        style={{
                          left: `${Math.max(0, startPct)}%`,
                          width: `${Math.min(widthPct, 100 - startPct)}%`,
                          background: `linear-gradient(135deg, ${statusColor.bg} 0%, rgba(10,20,30,0.9) 100%)`,
                          border: `1.5px solid ${statusColor.border}`,
                          boxShadow: `0 4px 15px rgba(0,0,0,0.35), 0 0 15px ${statusColor.border}15`,
                          minWidth: '70px',
                        }}
                        onClick={() => onEditWorkOrder(order)}
                        onMouseEnter={() => setHoveredOrder(order.id)}
                        onMouseLeave={() => setHoveredOrder(null)}
                      >
                        {/* Shimmer overlay on hover */}
                        <div className="absolute inset-0 bg-gradient-to-r from-transparent via-white/5 to-transparent -translate-x-full hover:animate-shimmer" style={{ animationDuration: '1.5s' }} />
                        
                        <div className="relative z-10 min-w-0">
                          <div className="flex items-center gap-1.5 mb-0.5">
                            <Car size={11} style={{ color: statusColor.text }} className="flex-shrink-0 filter drop-shadow-[0_0_2px_rgba(255,255,255,0.4)]" />
                            <span
                              className="text-[10px] font-black tracking-wider truncate font-mono uppercase"
                              style={{ color: statusColor.text }}
                            >
                              {order.vehicleInfo.plate}
                            </span>
                          </div>
                          <div className="text-[9px] font-bold truncate opacity-85" style={{ color: '#fff' }}>
                            {order.clientName.split(' ')[0]} <span className="opacity-40 font-mono">·</span> {service?.name || ''}
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